package com.safemesh.routing

import com.safemesh.model.MessageType
import com.safemesh.protocol.SafeMeshPacket

/**
 * Routing policy configuration and decision rules for SafeMesh opportunistic mesh.
 */
data class RoutingConfig(
    val maxHopLimit: Int = 5,
    val emergencyPriorityWeight: Int = 10,
    val standardPriorityWeight: Int = 1,
    val maxStoredPackets: Int = 500,
    val defaultEmergencyExpiryHours: Int = 24,
    val defaultStandardExpiryHours: Int = 12,
    val minBatteryPercentForRelaying: Int = 15
)

object RoutingPolicy {

    /**
     * Determines whether a packet is eligible for forwarding to a given peer.
     */
    fun isEligibleForForwarding(
        packet: SafeMeshPacket,
        targetPeerId: String,
        alreadyTransferred: Boolean,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Do not loop back to original sender
        if (packet.senderId == targetPeerId) return false

        // 2. Do not re-send if already transferred to this specific peer
        if (alreadyTransferred) return false

        // 3. Expiration check
        if (packet.isExpired(currentTime)) return false

        // 4. Hop limit check
        if (packet.isHopLimitReached()) return false

        return true
    }

    /**
     * Prioritizes messages for transmission queue:
     * - Emergency SOS comes first
     * - Higher remaining time before expiration comes second
     * - Lower hop count comes third
     */
    fun prioritizePackets(packets: List<SafeMeshPacket>): List<SafeMeshPacket> {
        return packets.sortedWith(
            compareByDescending<SafeMeshPacket> { it.isEmergency() }
                .thenByDescending { it.expiresAt }
                .thenBy { it.hopCount }
        )
    }
}
