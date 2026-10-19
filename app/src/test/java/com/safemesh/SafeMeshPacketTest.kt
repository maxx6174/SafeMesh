package com.safemesh

import com.safemesh.model.MessageType
import com.safemesh.protocol.PacketFramer
import com.safemesh.protocol.SafeMeshPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets

class SafeMeshPacketTest {

    @Test
    fun testPacketSerializationAndParsingRoundtrip() {
        val now = System.currentTimeMillis()
        val expiresAt = now + 86400000L

        val originalPacket = SafeMeshPacket(
            protocolVersion = 1,
            messageId = "test-msg-uuid-001",
            messageType = MessageType.EMERGENCY_SOS,
            senderId = "node_sender_alpha",
            recipientId = SafeMeshPacket.BROADCAST_RECIPIENT,
            encryptedPayload = "ZW5jcnlwdGVkX3BheWxvYWRfdGVzdA==",
            iv = "aXZfc2VjdXJlXzEyYg==",
            ephemeralPublicKey = null,
            authSignature = "aG1hY19zaWduYXR1cmVfYmFzZTY0",
            createdAt = now,
            expiresAt = expiresAt,
            hopLimit = 5,
            hopCount = 1,
            latitude = 37.7749,
            longitude = -122.4194,
            locationAccuracy = 4.5f,
            locationTimestamp = now
        )

        val bytes = PacketFramer.serializePacket(originalPacket)
        val parsedPacket = PacketFramer.parsePacket(bytes, currentTime = now)

        assertEquals(originalPacket.protocolVersion, parsedPacket.protocolVersion)
        assertEquals(originalPacket.messageId, parsedPacket.messageId)
        assertEquals(originalPacket.messageType, parsedPacket.messageType)
        assertEquals(originalPacket.senderId, parsedPacket.senderId)
        assertEquals(originalPacket.recipientId, parsedPacket.recipientId)
        assertEquals(originalPacket.encryptedPayload, parsedPacket.encryptedPayload)
        assertEquals(originalPacket.iv, parsedPacket.iv)
        assertEquals(originalPacket.authSignature, parsedPacket.authSignature)
        assertEquals(originalPacket.createdAt, parsedPacket.createdAt)
        assertEquals(originalPacket.expiresAt, parsedPacket.expiresAt)
        assertEquals(originalPacket.hopLimit, parsedPacket.hopLimit)
        assertEquals(originalPacket.hopCount, parsedPacket.hopCount)
        assertEquals(37.7749, parsedPacket.latitude!!, 0.0001)
        assertEquals(-122.4194, parsedPacket.longitude!!, 0.0001)
        assertEquals(4.5f, parsedPacket.locationAccuracy!!, 0.1f)
    }

    @Test
    fun testExpiredPacketIsRejected() {
        val now = System.currentTimeMillis()
        val expiredTime = now - 1000L

        val expiredPacket = SafeMeshPacket(
            protocolVersion = 1,
            messageId = "expired-msg-001",
            messageType = MessageType.EMERGENCY_SOS,
            senderId = "node_sender",
            recipientId = "BROADCAST",
            encryptedPayload = "cGF5bG9hZA==",
            iv = "aXYxMjM0NQ==",
            authSignature = "c2lnbmF0dXJl",
            createdAt = now - 10000L,
            expiresAt = expiredTime
        )

        val bytes = PacketFramer.serializePacket(expiredPacket)
        try {
            PacketFramer.parsePacket(bytes, currentTime = now)
            fail("Expected parsePacket to throw IllegalArgumentException for expired packet")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("expired", ignoreCase = true))
        }
    }

    @Test
    fun testHopLimitEnforcement() {
        val now = System.currentTimeMillis()
        val packet = SafeMeshPacket(
            protocolVersion = 1,
            messageId = "hop-exceeded-001",
            messageType = MessageType.EMERGENCY_SOS,
            senderId = "node_sender",
            recipientId = "BROADCAST",
            encryptedPayload = "cGF5bG9hZA==",
            iv = "aXYxMjM0NQ==",
            authSignature = "c2lnbmF0dXJl",
            createdAt = now,
            expiresAt = now + 3600000L,
            hopLimit = 3,
            hopCount = 4 // Exceeds hop limit!
        )

        val bytes = PacketFramer.serializePacket(packet)
        try {
            PacketFramer.parsePacket(bytes, currentTime = now)
            fail("Expected parsePacket to throw IllegalArgumentException when hopCount > hopLimit")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Hop limit", ignoreCase = true))
        }
    }

    @Test
    fun testPacketOversizedRejection() {
        val hugeBytes = ByteArray(SafeMeshPacket.MAX_PACKET_SIZE_BYTES + 10)
        try {
            PacketFramer.parsePacket(hugeBytes)
            fail("Expected parsePacket to reject oversized byte array")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds maximum allowed size", ignoreCase = true))
        }
    }

    @Test
    fun testMalformedJsonRejection() {
        val corruptBytes = "{ malformed json :::".toByteArray(StandardCharsets.UTF_8)
        try {
            PacketFramer.parsePacket(corruptBytes)
            fail("Expected parsePacket to throw IllegalArgumentException for malformed JSON")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Malformed JSON", ignoreCase = true))
        }
    }
}
