package com.safemesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.safemesh.protocol.PacketFramer
import com.safemesh.protocol.SafeMeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class BleGattServer(
    private val context: Context,
    private val localNodeId: String,
    private val localPublicKeyBase64: String,
    private val onPacketReceived: (packet: SafeMeshPacket, peerAddress: String) -> Unit
) {
    private val tag = "BleGattServer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothGattServer: BluetoothGattServer? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError: StateFlow<String?> = _serverError.asStateFlow()

    // Transfer ID -> (Chunk Index -> Chunk Data)
    private val pendingTransfers = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    // Transfer ID -> Expected Total Chunks
    private val expectedChunks = ConcurrentHashMap<String, Int>()
    // Transfer ID -> Start Timestamp for cleanup
    private val transferTimestamps = ConcurrentHashMap<String, Long>()

    // Connected client devices for notification delivery
    private val connectedClients = ConcurrentHashMap<String, BluetoothDevice>()

    private var ackCharacteristic: BluetoothGattCharacteristic? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(tag, "Client connection state: ${device.address}, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedClients[device.address] = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedClients.remove(device.address)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                BluetoothConstants.CHAR_NODE_INFO_UUID -> {
                    val info = "$localNodeId|$localPublicKeyBase64".toByteArray(StandardCharsets.UTF_8)
                    val responseBytes = if (offset < info.size) {
                        info.copyOfRange(offset, info.size)
                    } else {
                        ByteArray(0)
                    }
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseBytes)
                }
                else -> {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            if (characteristic.uuid == BluetoothConstants.CHAR_WRITE_CHUNK_UUID && value != null) {
                scope.launch {
                    handleIncomingChunk(device, value)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG) {
                descriptor.value = value
            }
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleIncomingChunk(device: BluetoothDevice, frameBytes: ByteArray) {
        val chunk = PacketFramer.decodeFrameToChunk(frameBytes) ?: run {
            Log.w(tag, "Received invalid or corrupted chunk frame from ${device.address}")
            return
        }

        val transferId = chunk.transferId
        val chunkIndex = chunk.chunkIndex
        val totalChunks = chunk.totalChunks

        Log.d(tag, "Received chunk $chunkIndex/$totalChunks for transfer $transferId from ${device.address}")

        val chunkMap = pendingTransfers.computeIfAbsent(transferId) { ConcurrentHashMap() }
        expectedChunks[transferId] = totalChunks
        transferTimestamps.putIfAbsent(transferId, System.currentTimeMillis())
        chunkMap[chunkIndex] = chunk.data

        // Send Ack notification back to client
        sendChunkAck(device, transferId, chunkIndex, true)

        // Check if all chunks received
        if (chunkMap.size == totalChunks) {
            val assembledBytes = PacketFramer.reassembleChunks(chunkMap, totalChunks)
            pendingTransfers.remove(transferId)
            expectedChunks.remove(transferId)
            transferTimestamps.remove(transferId)

            if (assembledBytes != null) {
                try {
                    val packet = PacketFramer.parsePacket(assembledBytes)
                    Log.i(tag, "Successfully reassembled SafeMesh packet ${packet.messageId} from ${device.address}")
                    onPacketReceived(packet, device.address)
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing reassembled packet: ${e.message}", e)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendChunkAck(device: BluetoothDevice, transferId: String, chunkIndex: Int, isSuccess: Boolean) {
        val ackChar = ackCharacteristic ?: return
        val ackPayload = "$transferId:$chunkIndex:${if (isSuccess) 1 else 0}".toByteArray(StandardCharsets.US_ASCII)
        ackChar.value = ackPayload
        try {
            bluetoothGattServer?.notifyCharacteristicChanged(device, ackChar, false)
        } catch (e: Exception) {
            Log.w(tag, "Failed to send chunk ack notification: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        if (bluetoothManager == null) {
            _serverError.value = "BluetoothManager unavailable"
            return false
        }

        try {
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            if (bluetoothGattServer == null) {
                _serverError.value = "Failed to open GATT Server"
                return false
            }

            val service = BluetoothGattService(
                BluetoothConstants.SAFEMESH_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val writeChar = BluetoothGattCharacteristic(
                BluetoothConstants.CHAR_WRITE_CHUNK_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            val ackChar = BluetoothGattCharacteristic(
                BluetoothConstants.CHAR_ACK_NOTIFY_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            val cccd = BluetoothGattDescriptor(
                BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
            ackChar.addDescriptor(cccd)
            this.ackCharacteristic = ackChar

            val nodeInfoChar = BluetoothGattCharacteristic(
                BluetoothConstants.CHAR_NODE_INFO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            service.addCharacteristic(writeChar)
            service.addCharacteristic(ackChar)
            service.addCharacteristic(nodeInfoChar)

            bluetoothGattServer?.addService(service)
            _isServerRunning.value = true
            _serverError.value = null
            Log.d(tag, "SafeMesh GATT Server started successfully")
            return true
        } catch (e: SecurityException) {
            _serverError.value = "Bluetooth connect permission denied for GATT Server"
            Log.e(tag, "SecurityException opening GATT server", e)
            return false
        } catch (e: Exception) {
            _serverError.value = "Error starting GATT Server: ${e.message}"
            Log.e(tag, "Exception opening GATT server", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            bluetoothGattServer?.clearServices()
            bluetoothGattServer?.close()
            connectedClients.clear()
            pendingTransfers.clear()
            expectedChunks.clear()
            transferTimestamps.clear()
        } catch (e: Exception) {
            Log.e(tag, "Error closing GATT server", e)
        } finally {
            bluetoothGattServer = null
            _isServerRunning.value = false
        }
    }
}
