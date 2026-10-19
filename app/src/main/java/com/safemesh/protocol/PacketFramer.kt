package com.safemesh.protocol

import com.safemesh.model.MessageType
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

object PacketFramer {

    private val CHUNK_MAGIC = byteArrayOf(0x53, 0x4D) // 'S', 'M'
    const val DEFAULT_MAX_CHUNK_SIZE = 180 // Safe under default and negotiated BLE MTUs

    /**
     * Serializes a SafeMeshPacket to UTF-8 bytes.
     */
    fun serializePacket(packet: SafeMeshPacket): ByteArray {
        val jsonString = packet.toJson()
        return jsonString.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Parses and strictly validates a SafeMeshPacket from bytes.
     * Throws IllegalArgumentException on invalid or malicious packets.
     */
    fun parsePacket(bytes: ByteArray, currentTime: Long = System.currentTimeMillis()): SafeMeshPacket {
        if (bytes.size > SafeMeshPacket.MAX_PACKET_SIZE_BYTES) {
            throw IllegalArgumentException("Packet exceeds maximum allowed size: ${bytes.size} bytes")
        }

        val jsonString = String(bytes, StandardCharsets.UTF_8)
        val json = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed JSON packet payload", e)
        }

        val version = json.optInt("ver", -1)
        if (version != SafeMeshPacket.PROTOCOL_VERSION) {
            throw IllegalArgumentException("Unsupported protocol version: $version")
        }

        val messageId = json.optString("id", "")
        if (messageId.isBlank()) {
            throw IllegalArgumentException("Missing or empty message ID")
        }

        val typeStr = json.optString("type", "")
        val messageType = try {
            MessageType.valueOf(typeStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unknown message type: $typeStr")
        }

        val senderId = json.optString("src", "")
        val recipientId = json.optString("dst", "")
        val encryptedPayload = json.optString("payload", "")
        val iv = json.optString("iv", "")
        val authSignature = json.optString("sig", "")
        val createdAt = json.optLong("ts", 0L)
        val expiresAt = json.optLong("exp", 0L)
        val hopLimit = json.optInt("hl", SafeMeshPacket.DEFAULT_HOP_LIMIT)
        val hopCount = json.optInt("hc", 0)

        if (senderId.isBlank() || recipientId.isBlank() || encryptedPayload.isBlank() || iv.isBlank()) {
            throw IllegalArgumentException("Missing required transport envelope fields")
        }

        if (expiresAt <= 0 || expiresAt <= currentTime) {
            throw IllegalArgumentException("Packet has expired or invalid expiration timestamp: $expiresAt")
        }

        if (hopCount > hopLimit) {
            throw IllegalArgumentException("Hop limit exceeded: hopCount=$hopCount > hopLimit=$hopLimit")
        }

        val ephemeralPublicKey = if (json.has("epub")) json.getString("epub") else null
        val latitude = if (json.has("lat")) json.getDouble("lat") else null
        val longitude = if (json.has("lon")) json.getDouble("lon") else null
        val locationAccuracy = if (json.has("acc")) json.getDouble("acc").toFloat() else null
        val locationTimestamp = if (json.has("lts")) json.getLong("lts") else null

        return SafeMeshPacket(
            protocolVersion = version,
            messageId = messageId,
            messageType = messageType,
            senderId = senderId,
            recipientId = recipientId,
            encryptedPayload = encryptedPayload,
            iv = iv,
            ephemeralPublicKey = ephemeralPublicKey,
            authSignature = authSignature,
            createdAt = createdAt,
            expiresAt = expiresAt,
            hopLimit = hopLimit,
            hopCount = hopCount,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = locationAccuracy,
            locationTimestamp = locationTimestamp
        )
    }

    /**
     * Splits serialized packet bytes into framed BLE chunks.
     */
    fun splitIntoChunks(
        packetBytes: ByteArray,
        transferId: String,
        chunkSize: Int = DEFAULT_MAX_CHUNK_SIZE
    ): List<MeshChunk> {
        val chunks = mutableListOf<MeshChunk>()
        val totalChunks = (packetBytes.size + chunkSize - 1) / chunkSize

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = kotlin.math.min(start + chunkSize, packetBytes.size)
            val chunkData = packetBytes.copyOfRange(start, end)
            chunks.add(MeshChunk(transferId, i, totalChunks, chunkData))
        }

        return chunks
    }

    /**
     * Encodes a MeshChunk into a binary frame for BLE GATT characteristic transmission:
     * [0..1] MAGIC (0x53, 0x4D)
     * [2..17] transferId (16 bytes UTF-8 ASCII)
     * [18..19] chunkIndex (Short, 2 bytes)
     * [20..21] totalChunks (Short, 2 bytes)
     * [22..23] payloadLength (Short, 2 bytes)
     * [24..24+payloadLength-1] payload bytes
     * [last 4 bytes] CRC32 checksum
     */
    fun encodeChunkToFrame(chunk: MeshChunk): ByteArray {
        val transferIdBytes = chunk.transferId.padEnd(16, ' ').take(16).toByteArray(StandardCharsets.US_ASCII)
        val payloadLen = chunk.data.size
        val totalFrameSize = 2 + 16 + 2 + 2 + 2 + payloadLen + 4

        val buffer = ByteBuffer.allocate(totalFrameSize)
        buffer.put(CHUNK_MAGIC)
        buffer.put(transferIdBytes)
        buffer.putShort(chunk.chunkIndex.toShort())
        buffer.putShort(chunk.totalChunks.toShort())
        buffer.putShort(payloadLen.toShort())
        buffer.put(chunk.data)

        // CRC32 over the frame content before CRC
        val crc = CRC32()
        crc.update(buffer.array(), 0, totalFrameSize - 4)
        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }

    /**
     * Decodes a binary frame received over BLE GATT into a MeshChunk.
     * Validates magic bytes and CRC32 checksum.
     */
    fun decodeFrameToChunk(frameBytes: ByteArray): MeshChunk? {
        if (frameBytes.size < 28) return null // Minimum frame size with empty payload

        if (frameBytes[0] != CHUNK_MAGIC[0] || frameBytes[1] != CHUNK_MAGIC[1]) {
            return null
        }

        val buffer = ByteBuffer.wrap(frameBytes)
        buffer.position(2) // Skip magic

        val transferIdBytes = ByteArray(16)
        buffer.get(transferIdBytes)
        val transferId = String(transferIdBytes, StandardCharsets.US_ASCII).trim()

        val chunkIndex = buffer.short.toInt() and 0xFFFF
        val totalChunks = buffer.short.toInt() and 0xFFFF
        val payloadLen = buffer.short.toInt() and 0xFFFF

        if (frameBytes.size < 24 + payloadLen + 4) return null

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val receivedCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val crc = CRC32()
        crc.update(frameBytes, 0, frameBytes.size - 4)
        if (crc.value != receivedCrc) {
            return null // CRC mismatch / corrupted chunk
        }

        return MeshChunk(transferId, chunkIndex, totalChunks, payload)
    }

    /**
     * Reassembles chunks into original packet bytes once all chunks have been received.
     */
    fun reassembleChunks(chunks: Map<Int, ByteArray>, totalChunks: Int): ByteArray? {
        if (chunks.size < totalChunks) return null

        var totalSize = 0
        for (i in 0 until totalChunks) {
            val chunk = chunks[i] ?: return null
            totalSize += chunk.size
        }

        val result = ByteArray(totalSize)
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunk = chunks[i] ?: return null
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }

        return result
    }

    /**
     * Computes CRC32 checksum of bytes.
     */
    fun computeChecksum(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }
}
