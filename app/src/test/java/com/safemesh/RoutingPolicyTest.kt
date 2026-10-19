package com.safemesh

import com.safemesh.model.MessageType
import com.safemesh.protocol.SafeMeshPacket
import com.safemesh.routing.RoutingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyTest {

    @Test
    fun testEmergencyMessagesPrioritizedOverStandardMessages() {
        val now = System.currentTimeMillis()

        val standardMsg = SafeMeshPacket(
            messageId = "standard-001",
            messageType = MessageType.STANDARD_MESSAGE,
            senderId = "node_a",
            recipientId = "node_b",
            encryptedPayload = "payload1",
            iv = "iv1",
            authSignature = "sig1",
            createdAt = now - 5000L,
            expiresAt = now + 3600000L,
            hopCount = 0
        )

        val emergencySos = SafeMeshPacket(
            messageId = "emergency-001",
            messageType = MessageType.EMERGENCY_SOS,
            senderId = "node_c",
            recipientId = "BROADCAST",
            encryptedPayload = "payload2",
            iv = "iv2",
            authSignature = "sig2",
            createdAt = now,
            expiresAt = now + 7200000L,
            hopCount = 1
        )

        val list = listOf(standardMsg, emergencySos)
        val prioritized = RoutingPolicy.prioritizePackets(list)

        // Emergency SOS must be first
        assertEquals("emergency-001", prioritized[0].messageId)
        assertEquals("standard-001", prioritized[1].messageId)
    }

    @Test
    fun testForwardingEligibilityRules() {
        val now = System.currentTimeMillis()

        val validPacket = SafeMeshPacket(
            messageId = "valid-001",
            messageType = MessageType.EMERGENCY_SOS,
            senderId = "node_sender",
            recipientId = "BROADCAST",
            encryptedPayload = "payload",
            iv = "iv",
            authSignature = "sig",
            createdAt = now,
            expiresAt = now + 3600000L,
            hopLimit = 5,
            hopCount = 2
        )

        // 1. Valid transfer to a third party
        assertTrue(RoutingPolicy.isEligibleForForwarding(validPacket, "node_third_party", alreadyTransferred = false, currentTime = now))

        // 2. Reject sending back to original sender (loop prevention)
        assertFalse(RoutingPolicy.isEligibleForForwarding(validPacket, "node_sender", alreadyTransferred = false, currentTime = now))

        // 3. Reject if already transferred to this specific peer
        assertFalse(RoutingPolicy.isEligibleForForwarding(validPacket, "node_third_party", alreadyTransferred = true, currentTime = now))

        // 4. Reject if packet expired
        val expiredPacket = validPacket.copy(expiresAt = now - 1000L)
        assertFalse(RoutingPolicy.isEligibleForForwarding(expiredPacket, "node_third_party", alreadyTransferred = false, currentTime = now))

        // 5. Reject if hop limit reached
        val maxHopPacket = validPacket.copy(hopCount = 5, hopLimit = 5)
        assertFalse(RoutingPolicy.isEligibleForForwarding(maxHopPacket, "node_third_party", alreadyTransferred = false, currentTime = now))
    }
}
