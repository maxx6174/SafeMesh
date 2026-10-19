package com.safemesh.protocol

import com.safemesh.model.MessageType
import org.json.JSONObject

/**
 * Versioned, structured network packet for SafeMesh.
 * Separates transport envelope metadata from the encrypted payload.
 */
data class SafeMeshPacket(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val messageId: String,
    val messageType: MessageType,
    val senderId: String,
    val recipientId: String, // "BROADCAST" or specific node ID
    val encryptedPayload: String, // Base64 encoded ciphertext
    val iv: String, // Base64 encoded 12-byte GCM IV
    val ephemeralPublicKey: String? = null, // Base64 encoded EC public key if directed
    val authSignature: String, // Base64 HMAC/Signature for envelope integrity
    val createdAt: Long,
    val expiresAt: Long,
    val hopLimit: Int = DEFAULT_HOP_LIMIT,
    val hopCount: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationTimestamp: Long? = null
) {
    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_HOP_LIMIT = 5
        const val BROADCAST_RECIPIENT = "BROADCAST"
        const val MAX_PACKET_SIZE_BYTES = 65536 // 64 KB safety limit
    }

    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime >= expiresAt
    }

    fun isHopLimitReached(): Boolean {
        return hopCount >= hopLimit
    }

    fun isEmergency(): Boolean {
        return messageType == MessageType.EMERGENCY_SOS
    }

    fun isBroadcast(): Boolean {
        return recipientId == BROADCAST_RECIPIENT
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("ver", protocolVersion)
        json.put("id", messageId)
        json.put("type", messageType.name)
        json.put("src", senderId)
        json.put("dst", recipientId)
        json.put("payload", encryptedPayload)
        json.put("iv", iv)
        if (ephemeralPublicKey != null) {
            json.put("epub", ephemeralPublicKey)
        }
        json.put("sig", authSignature)
        json.put("ts", createdAt)
        json.put("exp", expiresAt)
        json.put("hl", hopLimit)
        json.put("hc", hopCount)
        if (latitude != null && longitude != null) {
            json.put("lat", latitude)
            json.put("lon", longitude)
            if (locationAccuracy != null) json.put("acc", locationAccuracy.toDouble())
            if (locationTimestamp != null) json.put("lts", locationTimestamp)
        }
        return json.toString()
    }
}

data class MeshChunk(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshChunk
        if (transferId != other.transferId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class ChunkAck(
    val transferId: String,
    val chunkIndex: Int,
    val isSuccess: Boolean
)
