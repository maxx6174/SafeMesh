package com.safemesh.routing

import android.util.Log
import com.safemesh.data.repository.MessageRepository
import com.safemesh.model.DeliveryStatus
import com.safemesh.protocol.SafeMeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StoreCarryForwardEngine(
    private val messageRepository: MessageRepository,
    private val duplicateDetector: DuplicateDetector,
    private val config: RoutingConfig = RoutingConfig()
) {
    private val tag = "StoreCarryForward"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var maintenanceJob: Job? = null

    private val _pendingQueueCount = MutableStateFlow(0)
    val pendingQueueCount: StateFlow<Int> = _pendingQueueCount.asStateFlow()

    init {
        startMaintenanceLoop()
    }

    private fun startMaintenanceLoop() {
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    // 1. Purge expired packets in memory and database
                    duplicateDetector.purgeMemoryCache(now)
                    val expiredCount = messageRepository.cleanExpired()
                    if (expiredCount > 0) {
                        Log.i(tag, "Marked $expiredCount expired message(s)")
                    }

                    // 2. Update queue size metric
                    val pending = messageRepository.getEligibleForwardPackets("")
                    _pendingQueueCount.value = pending.size

                } catch (e: Exception) {
                    Log.e(tag, "Error in store-carry-forward maintenance loop: ${e.message}", e)
                }
                delay(30_000L) // Runs every 30 seconds
            }
        }
    }

    /**
     * Resolves pending packets for a discovered target peer, sorted by emergency priority.
     */
    suspend fun getOrderedForwardQueue(targetPeerId: String): List<SafeMeshPacket> {
        val eligible = messageRepository.getEligibleForwardPackets(targetPeerId)
        return RoutingPolicy.prioritizePackets(eligible)
    }

    fun stop() {
        maintenanceJob?.cancel()
    }
}
