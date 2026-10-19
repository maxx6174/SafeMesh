package com.safemesh.crypto

import java.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles cryptographic operations for SafeMesh:
 * - NIST P-256 (secp256r1) Elliptic Curve Key Pair Generation
 * - ECDH (Elliptic Curve Diffie-Hellman) Key Agreement
 * - AES-256-GCM Authenticated Encryption with Associated Data (AEAD)
 * - HMAC-SHA256 for envelope authentication and integrity
 * - Ephemeral key exchange for Forward Secrecy
 */
object CryptoManager {

    private const val EC_CURVE = "secp256r1"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val HMAC_ALGORITHM = "HmacSHA256"

    private val secureRandom = SecureRandom()

    // Well-known public emergency broadcast salt/key derived for decentralized SOS mesh
    private val BROADCAST_EMERGENCY_KEY: ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("SafeMesh-Universal-Emergency-Broadcast-Channel-v1".toByteArray(Charsets.UTF_8))

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decodeBase64(str: String): ByteArray = Base64.getDecoder().decode(str)

    /**
     * Generates a persistent or ephemeral EC KeyPair using secp256r1.
     */
    fun generateEcKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE)
        keyPairGenerator.initialize(ecSpec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encodes a Public Key to a compact Base64 string for transmission/storage.
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return encodeBase64(publicKey.encoded)
    }

    /**
     * Decodes a Base64 string back to an EC Public Key.
     */
    fun decodePublicKey(encodedKey: String): PublicKey {
        val keyBytes = decodeBase64(encodedKey)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Generates a cryptographically secure random 12-byte IV for AES-GCM.
     */
    fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Derives an AES-256 key from an ECDH shared secret using SHA-256 KDF.
     */
    fun deriveSharedKey(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val derivedKey = digest.digest(sharedSecret)
        return SecretKeySpec(derivedKey, "AES")
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Returns a pair of (ciphertext, iv).
     */
    fun encryptAesGcm(
        plaintext: ByteArray,
        secretKey: SecretKeySpec,
        associatedData: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val iv = generateIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(ciphertext, iv)
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM.
     */
    fun decryptAesGcm(
        ciphertext: ByteArray,
        secretKey: SecretKeySpec,
        iv: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts broadcast emergency SOS payload using the standard SafeMesh emergency broadcast key.
     */
    fun encryptBroadcastPayload(plaintext: ByteArray, associatedData: ByteArray? = null): Pair<ByteArray, ByteArray> {
        val secretKey = SecretKeySpec(BROADCAST_EMERGENCY_KEY, "AES")
        return encryptAesGcm(plaintext, secretKey, associatedData)
    }

    /**
     * Decrypts broadcast emergency SOS payload using the standard SafeMesh emergency broadcast key.
     */
    fun decryptBroadcastPayload(ciphertext: ByteArray, iv: ByteArray, associatedData: ByteArray? = null): ByteArray {
        val secretKey = SecretKeySpec(BROADCAST_EMERGENCY_KEY, "AES")
        return decryptAesGcm(ciphertext, secretKey, iv, associatedData)
    }

    /**
     * Computes HMAC-SHA256 for envelope integrity and authentication.
     */
    fun computeHmac(data: ByteArray, keyBytes: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val key = SecretKeySpec(keyBytes, HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(data)
    }

    /**
     * Verifies HMAC in constant time to prevent timing attacks.
     */
    fun verifyHmac(data: ByteArray, keyBytes: ByteArray, expectedHmac: ByteArray): Boolean {
        val computed = computeHmac(data, keyBytes)
        return MessageDigest.isEqual(computed, expectedHmac)
    }

    /**
     * Generates a unique 16-byte random node/device temporary ID formatted as hex.
     */
    fun generateNodeId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
