package com.safemesh

import com.safemesh.protocol.MeshChunk
import com.safemesh.protocol.PacketFramer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PacketFramerTest {

    @Test
    fun testChunkSplittingAndReassembly() {
        val originalData = "A".repeat(500).toByteArray(StandardCharsets.UTF_8)
        val transferId = "trans-12345678"
        val chunkSize = 120

        val chunks = PacketFramer.splitIntoChunks(originalData, transferId, chunkSize)
        assertEquals(5, chunks.size) // 500 / 120 = 4 chunks of 120 + 1 chunk of 20

        val chunkMap = mutableMapOf<Int, ByteArray>()
        chunks.forEach { chunkMap[it.chunkIndex] = it.data }

        val reassembled = PacketFramer.reassembleChunks(chunkMap, chunks[0].totalChunks)
        assertNotNull(reassembled)
        assertArrayEquals(originalData, reassembled)
    }

    @Test
    fun testChunkFrameEncodingAndDecoding() {
        val payload = "CHUNK_PAYLOAD_TEST_DATA".toByteArray(StandardCharsets.UTF_8)
        val chunk = MeshChunk("transfer-9999", 2, 5, payload)

        val frameBytes = PacketFramer.encodeChunkToFrame(chunk)
        assertTrue(frameBytes.size > payload.size)

        val decodedChunk = PacketFramer.decodeFrameToChunk(frameBytes)
        assertNotNull(decodedChunk)
        assertEquals(chunk.transferId, decodedChunk!!.transferId)
        assertEquals(chunk.chunkIndex, decodedChunk.chunkIndex)
        assertEquals(chunk.totalChunks, decodedChunk.totalChunks)
        assertArrayEquals(chunk.data, decodedChunk.data)
    }

    @Test
    fun testCorruptedChunkFrameIsRejectedByCrc() {
        val payload = "VALID_DATA_BEFORE_TAMPER".toByteArray(StandardCharsets.UTF_8)
        val chunk = MeshChunk("transfer-tamper", 0, 1, payload)

        val frameBytes = PacketFramer.encodeChunkToFrame(chunk)

        // Corrupt one payload byte in the frame
        frameBytes[25] = (frameBytes[25].toInt() xor 0x5A).toByte()

        val decodedChunk = PacketFramer.decodeFrameToChunk(frameBytes)
        assertNull("Corrupted chunk with invalid CRC32 must return null", decodedChunk)
    }

    @Test
    fun testInvalidMagicBytesRejected() {
        val payload = "DATA".toByteArray(StandardCharsets.UTF_8)
        val chunk = MeshChunk("trans-magic", 0, 1, payload)
        val frameBytes = PacketFramer.encodeChunkToFrame(chunk)

        // Corrupt magic bytes
        frameBytes[0] = 0x00
        frameBytes[1] = 0x00

        val decodedChunk = PacketFramer.decodeFrameToChunk(frameBytes)
        assertNull("Chunk with invalid magic bytes must be rejected", decodedChunk)
    }
}
