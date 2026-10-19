package com.safemesh

import android.app.Application
import com.safemesh.bluetooth.BluetoothMeshManager
import com.safemesh.crypto.CryptoManager
import com.safemesh.data.database.SafeMeshDatabase
import com.safemesh.data.repository.MessageRepository
import com.safemesh.data.repository.PeerRepository
import com.safemesh.location.SafeMeshLocationProvider
import com.safemesh.routing.DuplicateDetector
import com.safemesh.routing.StoreCarryForwardEngine
import com.safemesh.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SafeMeshApplication : Application() {

    lateinit var database: SafeMeshDatabase private set
    lateinit var messageRepository: MessageRepository private set
    lateinit var peerRepository: PeerRepository private set
    lateinit var duplicateDetector: DuplicateDetector private set
    lateinit var storeCarryForwardEngine: StoreCarryForwardEngine private set
    lateinit var bluetoothMeshManager: BluetoothMeshManager private set
    lateinit var locationProvider: SafeMeshLocationProvider private set
    lateinit var notificationHelper: NotificationHelper private set

    private val _latestEmergencyPacket = MutableStateFlow<com.safemesh.protocol.SafeMeshPacket?>(null)
    val latestEmergencyPacket: StateFlow<com.safemesh.protocol.SafeMeshPacket?> = _latestEmergencyPacket.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize persistent local Room database
        database = SafeMeshDatabase.getDatabase(this)

        // 2. Generate or retrieve stable local node ID
        val prefs = getSharedPreferences("safemesh_prefs", MODE_PRIVATE)
        var nodeId = prefs.getString("node_id", null)
        if (nodeId == null) {
            nodeId = CryptoManager.generateNodeId()
            prefs.edit().putString("node_id", nodeId).apply()
        }

        // 3. Initialize Repositories and Engines
        messageRepository = MessageRepository(
            messageDao = database.messageDao(),
            duplicateCacheDao = database.duplicateCacheDao(),
            transferRecordDao = database.transferRecordDao(),
            localNodeId = nodeId
        )

        peerRepository = PeerRepository(database.peerDao())
        duplicateDetector = DuplicateDetector(database.duplicateCacheDao())
        storeCarryForwardEngine = StoreCarryForwardEngine(messageRepository, duplicateDetector)
        locationProvider = SafeMeshLocationProvider(this)
        notificationHelper = NotificationHelper(this)

        // 4. Initialize Bluetooth Mesh Manager
        bluetoothMeshManager = BluetoothMeshManager(
            context = this,
            messageRepository = messageRepository,
            peerRepository = peerRepository,
            onEmergencyPacketReceived = { packet ->
                _latestEmergencyPacket.value = packet
                notificationHelper.showEmergencyNotification(packet)
            }
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        bluetoothMeshManager.destroy()
        storeCarryForwardEngine.stop()
    }
}
