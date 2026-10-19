package com.safemesh

import com.safemesh.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

class CryptoManagerTest {

    @Test
    fun testEcKeyPairGenerationAndEncoding() {
        val keyPair = CryptoManager.generateEcKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)

        val encodedPub = CryptoManager.encodePublicKey(keyPair.public)
        assertTrue(encodedPub.isNotEmpty())

        val decodedPub = CryptoManager.decodePublicKey(encodedPub)
        assertEquals(keyPair.public, decodedPub)
    }

    @Test
    fun testEcdhKeyAgreementSharedSecretMatching() {
        // Node Alpha & Node Beta
        val alphaKeyPair = CryptoManager.generateEcKeyPair()
        val betaKeyPair = CryptoManager.generateEcKeyPair()

        // Alpha computes shared key using Beta's public key
        val alphaDerivedKey = CryptoManager.deriveSharedKey(alphaKeyPair.private, betaKeyPair.public)

        // Beta computes shared key using Alpha's public key
        val betaDerivedKey = CryptoManager.deriveSharedKey(betaKeyPair.private, alphaKeyPair.public)

        // Both derived AES keys must match exactly
        assertArrayEquals(alphaDerivedKey.encoded, betaDerivedKey.encoded)
    }

    @Test
    fun testAesGcmAuthenticatedEncryptionAndDecryption() {
        val alice = CryptoManager.generateEcKeyPair()
        val bob = CryptoManager.generateEcKeyPair()
        val sharedKey = CryptoManager.deriveSharedKey(alice.private, bob.public)

        val secretMessage = "EMERGENCY: Trapped on 3rd floor, coordinates verified."
        val plaintextBytes = secretMessage.toByteArray(StandardCharsets.UTF_8)
        val associatedData = "header_meta_v1".toByteArray(StandardCharsets.UTF_8)

        // Encrypt with AES-GCM
        val (ciphertext, iv) = CryptoManager.encryptAesGcm(plaintextBytes, sharedKey, associatedData)
        assertTrue(ciphertext.isNotEmpty())
        assertEquals(12, iv.size)

        // Decrypt with Bob's derived key
        val bobSharedKey = CryptoManager.deriveSharedKey(bob.private, alice.public)
        val decryptedBytes = CryptoManager.decryptAesGcm(ciphertext, bobSharedKey, iv, associatedData)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)

        assertEquals(secretMessage, decryptedText)
    }

    @Test
    fun testTamperedCiphertextRejectionByAead() {
        val alice = CryptoManager.generateEcKeyPair()
        val bob = CryptoManager.generateEcKeyPair()
        val sharedKey = CryptoManager.deriveSharedKey(alice.private, bob.public)

        val plaintextBytes = "Patient requires oxygen".toByteArray(StandardCharsets.UTF_8)
        val (ciphertext, iv) = CryptoManager.encryptAesGcm(plaintextBytes, sharedKey)

        // Tamper with one byte of the ciphertext
        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        try {
            CryptoManager.decryptAesGcm(tamperedCiphertext, sharedKey, iv)
            fail("Expected AEADBadTagException or GeneralSecurityException for tampered ciphertext")
        } catch (e: Exception) {
            // Success: GCM authenticated tag failed
            assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException || e.message!!.contains("tag", ignoreCase = true))
        }
    }

    @Test
    fun testBroadcastPayloadEncryptionAndDecryption() {
        val sosText = "BROADCAST SOS: Flash flood at bridge 14"
        val bytes = sosText.toByteArray(StandardCharsets.UTF_8)

        val (ciphertext, iv) = CryptoManager.encryptBroadcastPayload(bytes)
        val decryptedBytes = CryptoManager.decryptBroadcastPayload(ciphertext, iv)

        assertEquals(sosText, String(decryptedBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun testHmacIntegrityAndAuthentication() {
        val key = "secret_node_key_01".toByteArray(StandardCharsets.UTF_8)
        val data = "msg_id:sender_id:destination:payload".toByteArray(StandardCharsets.UTF_8)

        val hmac = CryptoManager.computeHmac(data, key)
        assertTrue(CryptoManager.verifyHmac(data, key, hmac))

        // Modified data should fail HMAC verification
        val modifiedData = "msg_id:sender_id:destination:tampered_payload".toByteArray(StandardCharsets.UTF_8)
        assertFalse(CryptoManager.verifyHmac(modifiedData, key, hmac))
    }
}
