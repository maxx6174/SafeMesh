package com.safemesh.data.repository

import com.safemesh.data.database.PeerDao
import com.safemesh.data.database.PeerEntity
import com.safemesh.model.PeerConnectionState
import kotlinx.coroutines.flow.Flow

class PeerRepository(private val peerDao: PeerDao) {

    val allPeersFlow: Flow<List<PeerEntity>> = peerDao.getAllPeersFlow()

    suspend fun upsertDiscoveredPeer(
        peerId: String,
        deviceName: String?,
        bluetoothAddress: String?,
        rssi: Int,
        isDemo: Boolean = false
    ) {
        val existing = peerDao.getPeer(peerId)
        val peer = PeerEntity(
            peerId = peerId,
            deviceName = deviceName ?: existing?.deviceName ?: "SafeMesh Node ${peerId.take(4)}",
            bluetoothAddress = bluetoothAddress ?: existing?.bluetoothAddress,
            lastSeen = System.currentTimeMillis(),
            connectionState = existing?.connectionState ?: PeerConnectionState.DISCONNECTED,
            rssi = rssi,
            transferCount = existing?.transferCount ?: 0,
            lastTransferStatus = existing?.lastTransferStatus,
            isDemonstration = isDemo
        )
        peerDao.upsertPeer(peer)
    }

    suspend fun updateConnectionState(peerId: String, state: PeerConnectionState) {
        peerDao.updateConnectionState(peerId, state)
    }

    suspend fun recordTransfer(peerId: String, status: String) {
        peerDao.recordTransfer(peerId, status)
    }

    suspend fun getPeer(peerId: String): PeerEntity? {
        return peerDao.getPeer(peerId)
    }

    suspend fun purgeStalePeers(olderThanMillis: Long = 10 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        peerDao.deleteStalePeers(cutoff)
    }

    suspend fun clearAll() {
        peerDao.deleteAll()
    }

    suspend fun insertDemoPeers() {
        val now = System.currentTimeMillis()
        val demoPeers = listOf(
            PeerEntity(
                peerId = "node_relay_alpha",
                deviceName = "SafeMesh Relay A",
                bluetoothAddress = "AA:BB:CC:11:22:33",
                lastSeen = now - 15000,
                connectionState = PeerConnectionState.CONNECTED,
                rssi = -64,
                transferCount = 3,
                lastTransferStatus = "Relayed 2 SOS packets",
                isDemonstration = true
            ),
            PeerEntity(
                peerId = "node_responder_beta",
                deviceName = "Search & Rescue Unit 4",
                bluetoothAddress = "AA:BB:CC:44:55:66",
                lastSeen = now - 45000,
                connectionState = PeerConnectionState.DISCONNECTED,
                rssi = -82,
                transferCount = 1,
                lastTransferStatus = "Delivered SOS packet",
                isDemonstration = true
            )
        )
        for (p in demoPeers) {
            peerDao.upsertPeer(p)
        }
    }
}
