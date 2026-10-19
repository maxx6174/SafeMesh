package com.safemesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.safemesh.data.repository.MessageRepository
import com.safemesh.data.repository.PeerRepository
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.PeerConnectionState
import com.safemesh.protocol.SafeMeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothMeshManager(
    private val context: Context,
    private val messageRepository: MessageRepository,
    private val peerRepository: PeerRepository,
    private val onEmergencyPacketReceived: (SafeMeshPacket) -> Unit
) {
    private val tag = "BluetoothMeshManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val advertiser = BleAdvertiser(bluetoothAdapter, messageRepository.localNodeId)
    private val gattClient = BleGattClient(context)

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("SafeMesh Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Track active transfer in progress per device address to prevent concurrent collisions
    private val activeDeviceTransfers = ConcurrentHashMap<String, Boolean>()
    private val isTransferringAny = AtomicBoolean(false)

    private val gattServer = BleGattServer(
        context = context,
        localNodeId = messageRepository.localNodeId,
        localPublicKeyBase64 = com.safemesh.crypto.CryptoManager.encodePublicKey(messageRepository.localKeyPair.public),
        onPacketReceived = { packet, peerAddress ->
            scope.launch {
                handleReceivedPacket(packet, peerAddress)
            }
        }
    )

    private val scanner = BleScanner(bluetoothAdapter) { device, nodeId, rssi ->
        scope.launch {
            handleDiscoveredPeer(device, nodeId, rssi)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                _isBluetoothEnabled.value = enabled
                if (!enabled) {
                    stopMesh()
                    _statusMessage.value = "Bluetooth turned off"
                } else {
                    _statusMessage.value = "Bluetooth enabled"
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun startMesh(lowPowerMode: Boolean = false) {
        if (bluetoothAdapter == null) {
            _statusMessage.value = "Bluetooth not supported on this hardware"
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            _statusMessage.value = "Please enable Bluetooth to start SafeMesh"
            return
        }

        val serverStarted = gattServer.startServer()
        if (serverStarted) {
            advertiser.startAdvertising(lowPowerMode)
            scanner.startScanning(lowPowerMode)
            _isMeshActive.value = true
            _statusMessage.value = "Active: Scanning & Advertising"
            Log.i(tag, "SafeMesh communication layer started")
        } else {
            _statusMessage.value = "Failed to start SafeMesh GATT Server"
        }
    }

    fun stopMesh() {
        advertiser.stopAdvertising()
        scanner.stopScanning()
        gattServer.stopServer()
        _isMeshActive.value = false
        _statusMessage.value = "SafeMesh Stopped"
        Log.i(tag, "SafeMesh communication layer stopped")
    }

    /**
     * Called whenever a nearby node is discovered by BleScanner.
     */
    private suspend fun handleDiscoveredPeer(device: BluetoothDevice, nodeId: String, rssi: Int) {
        peerRepository.upsertDiscoveredPeer(
            peerId = nodeId,
            deviceName = try { device.name } catch (e: SecurityException) { null },
            bluetoothAddress = device.address,
            rssi = rssi
        )

        // Opportunistic Store-Carry-Forward transfer check
        triggerOpportunisticForwarding(device, nodeId)
    }

    /**
     * Evaluates pending forwardable messages and transfers them to the discovered device.
     */
    private suspend fun triggerOpportunisticForwarding(device: BluetoothDevice, peerId: String) {
        if (activeDeviceTransfers.putIfAbsent(device.address, true) != null) {
            // Transfer already in progress for this device
            return
        }

        try {
            val eligiblePackets = messageRepository.getEligibleForwardPackets(peerId)
            if (eligiblePackets.isEmpty()) return

            Log.i(tag, "Found ${eligiblePackets.size} eligible packets to forward to peer $peerId (${device.address})")
            peerRepository.updateConnectionState(peerId, PeerConnectionState.CONNECTING)
            _statusMessage.value = "Transferring ${eligiblePackets.size} message(s) to $peerId"

            for (packet in eligiblePackets) {
                messageRepository.updateStatus(packet.messageId, DeliveryStatus.TRANSFERRING)
                val success = gattClient.sendPacket(device, packet)

                if (success) {
                    messageRepository.recordSuccessfulRelay(packet.messageId, peerId)
                    peerRepository.recordTransfer(peerId, "Transferred packet ${packet.messageId.take(8)}")
                    _statusMessage.value = "Successfully relayed packet to $peerId"
                    Log.i(tag, "Successfully forwarded packet ${packet.messageId} to $peerId")
                } else {
                    messageRepository.updateStatus(packet.messageId, DeliveryStatus.SAVED_LOCAL)
                    Log.w(tag, "Failed to transfer packet ${packet.messageId} to $peerId, reverted to SAVED_LOCAL")
                    break // Stop attempting further packets to this unreachable peer
                }
                delay(300) // Pacing between consecutive packets
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during opportunistic transfer: ${e.message}", e)
        } finally {
            peerRepository.updateConnectionState(peerId, PeerConnectionState.DISCONNECTED)
            activeDeviceTransfers.remove(device.address)
        }
    }

    /**
     * Handles an incoming packet reassembled by BleGattServer.
     */
    private suspend fun handleReceivedPacket(packet: SafeMeshPacket, peerAddress: String) {
        Log.i(tag, "Processing received packet ${packet.messageId} from $peerAddress")
        val isNew = messageRepository.processIncomingPacket(packet, peerAddress)

        if (isNew && packet.isEmergency()) {
            onEmergencyPacketReceived(packet)
            _statusMessage.value = "Emergency SOS Received: ${packet.messageId.take(8)}"
        }
    }

    fun destroy() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        stopMesh()
    }
}
