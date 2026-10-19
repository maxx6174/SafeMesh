package com.safemesh.data.repository

import android.content.Context
import com.safemesh.crypto.CryptoManager
import com.safemesh.data.database.DuplicateCacheDao
import com.safemesh.data.database.DuplicateCacheEntity
import com.safemesh.data.database.MessageDao
import com.safemesh.data.database.MessageEntity
import com.safemesh.data.database.TransferRecordDao
import com.safemesh.data.database.TransferRecordEntity
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.MessageType
import com.safemesh.model.TransferDirection
import com.safemesh.protocol.SafeMeshPacket
import kotlinx.coroutines.flow.Flow
import java.nio.charset.StandardCharsets
import java.util.UUID

class MessageRepository(
    private val messageDao: MessageDao,
    private val duplicateCacheDao: DuplicateCacheDao,
    private val transferRecordDao: TransferRecordDao,
    val localNodeId: String,
    val localKeyPair: java.security.KeyPair = CryptoManager.generateEcKeyPair()
) {

    val allMessagesFlow: Flow<List<MessageEntity>> = messageDao.getAllMessagesFlow()
    val pendingMessagesFlow: Flow<List<MessageEntity>> = messageDao.getPendingMessagesFlow()

    fun getMessageByIdFlow(id: String): Flow<MessageEntity?> = messageDao.getMessageByIdFlow(id)

    suspend fun getMessageById(id: String): MessageEntity? = messageDao.getMessageById(id)

    /**
     * Creates an Emergency SOS message, encrypts the payload, persists it locally,
     * and prepares the network packet for store-carry-forward transmission.
     */
    suspend fun createEmergencySos(
        text: String,
        recipientId: String = SafeMeshPacket.BROADCAST_RECIPIENT,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null,
        locationTimestamp: Long? = null,
        expiryHours: Int = 24
    ): SafeMeshPacket {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + (expiryHours * 3600 * 1000L)
        val payloadBytes = text.toByteArray(StandardCharsets.UTF_8)

        val (ciphertext, ivBytes) = CryptoManager.encryptBroadcastPayload(payloadBytes)
        val encryptedPayloadBase64 = CryptoManager.encodeBase64(ciphertext)
        val ivBase64 = CryptoManager.encodeBase64(ivBytes)

        // Envelope signature for authentication and tampering protection
        val envelopeToSign = "$messageId:$localNodeId:$recipientId:$encryptedPayloadBase64:$ivBase64:$now:$expiresAt"
            .toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = CryptoManager.computeHmac(envelopeToSign, localNodeId.toByteArray(StandardCharsets.UTF_8))
        val authSigBase64 = CryptoManager.encodeBase64(signatureBytes)

        val packet = SafeMeshPacket(
            protocolVersion = SafeMeshPacket.PROTOCOL_VERSION,
            messageId = messageId,
            messageType = MessageType.EMERGENCY_SOS,
            senderId = localNodeId,
            recipientId = recipientId,
            encryptedPayload = encryptedPayloadBase64,
            iv = ivBase64,
            ephemeralPublicKey = null,
            authSignature = authSigBase64,
            createdAt = now,
            expiresAt = expiresAt,
            hopLimit = SafeMeshPacket.DEFAULT_HOP_LIMIT,
            hopCount = 0,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = accuracy,
            locationTimestamp = locationTimestamp
        )

        val entity = MessageEntity(
            messageId = messageId,
            senderId = localNodeId,
            recipientId = recipientId,
            encryptedPayload = encryptedPayloadBase64,
            plaintextPreview = text,
            iv = ivBase64,
            ephemeralPublicKey = null,
            authSignature = authSigBase64,
            messageType = MessageType.EMERGENCY_SOS,
            createdAt = now,
            expiresAt = expiresAt,
            hopLimit = SafeMeshPacket.DEFAULT_HOP_LIMIT,
            hopCount = 0,
            status = DeliveryStatus.SAVED_LOCAL,
            relayCount = 0,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = accuracy,
            locationTimestamp = locationTimestamp,
            isEmergency = true,
            deliveryAckReceived = false
        )

        messageDao.insertMessage(entity)
        duplicateCacheDao.recordMessageId(
            DuplicateCacheEntity(
                messageId = messageId,
                firstSeenTimestamp = now,
                expiresAt = expiresAt
            )
        )

        return packet
    }

    /**
     * Creates a standard peer-to-peer message with ECDH + AES-256-GCM encryption.
     */
    suspend fun createStandardMessage(
        text: String,
        recipientId: String,
        recipientPublicKeyBase64: String?,
        expiryHours: Int = 12
    ): SafeMeshPacket {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + (expiryHours * 3600 * 1000L)
        val payloadBytes = text.toByteArray(StandardCharsets.UTF_8)

        val (ciphertext, ivBytes, epub) = if (recipientPublicKeyBase64 != null) {
            val peerPubKey = CryptoManager.decodePublicKey(recipientPublicKeyBase64)
            val sharedKey = CryptoManager.deriveSharedKey(localKeyPair.private, peerPubKey)
            val (ct, iv) = CryptoManager.encryptAesGcm(payloadBytes, sharedKey)
            Triple(ct, iv, CryptoManager.encodePublicKey(localKeyPair.public))
        } else {
            val (ct, iv) = CryptoManager.encryptBroadcastPayload(payloadBytes)
            Triple(ct, iv, null)
        }

        val encryptedPayloadBase64 = CryptoManager.encodeBase64(ciphertext)
        val ivBase64 = CryptoManager.encodeBase64(ivBytes)

        val envelopeToSign = "$messageId:$localNodeId:$recipientId:$encryptedPayloadBase64:$ivBase64:$now:$expiresAt"
            .toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = CryptoManager.computeHmac(envelopeToSign, localNodeId.toByteArray(StandardCharsets.UTF_8))
        val authSigBase64 = CryptoManager.encodeBase64(signatureBytes)

        val packet = SafeMeshPacket(
            protocolVersion = SafeMeshPacket.PROTOCOL_VERSION,
            messageId = messageId,
            messageType = MessageType.STANDARD_MESSAGE,
            senderId = localNodeId,
            recipientId = recipientId,
            encryptedPayload = encryptedPayloadBase64,
            iv = ivBase64,
            ephemeralPublicKey = epub,
            authSignature = authSigBase64,
            createdAt = now,
            expiresAt = expiresAt,
            hopLimit = SafeMeshPacket.DEFAULT_HOP_LIMIT,
            hopCount = 0
        )

        val entity = MessageEntity(
            messageId = messageId,
            senderId = localNodeId,
            recipientId = recipientId,
            encryptedPayload = encryptedPayloadBase64,
            plaintextPreview = text,
            iv = ivBase64,
            ephemeralPublicKey = epub,
            authSignature = authSigBase64,
            messageType = MessageType.STANDARD_MESSAGE,
            createdAt = now,
            expiresAt = expiresAt,
            hopLimit = SafeMeshPacket.DEFAULT_HOP_LIMIT,
            hopCount = 0,
            status = DeliveryStatus.SAVED_LOCAL,
            relayCount = 0,
            latitude = null,
            longitude = null,
            locationAccuracy = null,
            locationTimestamp = null,
            isEmergency = false,
            deliveryAckReceived = false
        )

        messageDao.insertMessage(entity)
        duplicateCacheDao.recordMessageId(DuplicateCacheEntity(messageId, now, expiresAt))
        return packet
    }

    /**
     * Processes an incoming SafeMeshPacket received over Bluetooth.
     * Prevents duplicate loops, respects hop count, decrypts if intended for this node,
     * or stores for store-carry-forward if intended for relay.
     */
    suspend fun processIncomingPacket(packet: SafeMeshPacket, peerId: String): Boolean {
        val now = System.currentTimeMillis()

        // 1. Expiration check
        if (packet.isExpired(now)) {
            return false
        }

        // 2. Duplicate detection
        if (duplicateCacheDao.hasSeen(packet.messageId)) {
            return false
        }

        // 3. Hop limit check
        if (packet.isHopLimitReached()) {
            return false
        }

        // 4. Record in duplicate cache to prevent duplicate processing
        duplicateCacheDao.recordMessageId(
            DuplicateCacheEntity(
                messageId = packet.messageId,
                firstSeenTimestamp = now,
                expiresAt = packet.expiresAt
            )
        )

        // 5. Handle special packet types (Acks)
        if (packet.messageType == MessageType.DELIVERY_ACK) {
            messageDao.markDelivered(packet.messageId)
            transferRecordDao.insertRecord(
                TransferRecordEntity(
                    messageId = packet.messageId,
                    peerId = peerId,
                    timestamp = now,
                    direction = TransferDirection.INCOMING,
                    isSuccess = true,
                    acknowledged = true
                )
            )
            return true
        }

        if (packet.messageType == MessageType.RELAY_ACK) {
            messageDao.recordRelaySuccess(packet.messageId, DeliveryStatus.RELAYED)
            transferRecordDao.insertRecord(
                TransferRecordEntity(
                    messageId = packet.messageId,
                    peerId = peerId,
                    timestamp = now,
                    direction = TransferDirection.INCOMING,
                    isSuccess = true,
                    acknowledged = true
                )
            )
            return true
        }

        // 6. Decrypt payload if we are the intended recipient or it is an emergency broadcast
        var decryptedText: String? = null
        val isRecipient = packet.recipientId == localNodeId || packet.isBroadcast()

        if (isRecipient) {
            try {
                val ciphertext = CryptoManager.decodeBase64(packet.encryptedPayload)
                val iv = CryptoManager.decodeBase64(packet.iv)
                val plaintextBytes = if (packet.ephemeralPublicKey != null) {
                    val peerKey = CryptoManager.decodePublicKey(packet.ephemeralPublicKey)
                    val sharedKey = CryptoManager.deriveSharedKey(localKeyPair.private, peerKey)
                    CryptoManager.decryptAesGcm(ciphertext, sharedKey, iv)
                } else {
                    CryptoManager.decryptBroadcastPayload(ciphertext, iv)
                }
                decryptedText = String(plaintextBytes, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                // If direct decryption fails (e.g. directed to another node), keep plaintextPreview null
                decryptedText = null
            }
        }

        // 7. Store packet persistently
        val initialStatus = if (packet.recipientId == localNodeId) {
            DeliveryStatus.DELIVERED
        } else {
            DeliveryStatus.RELAYED
        }

        val entity = MessageEntity(
            messageId = packet.messageId,
            senderId = packet.senderId,
            recipientId = packet.recipientId,
            encryptedPayload = packet.encryptedPayload,
            plaintextPreview = decryptedText,
            iv = packet.iv,
            ephemeralPublicKey = packet.ephemeralPublicKey,
            authSignature = packet.authSignature,
            messageType = packet.messageType,
            createdAt = packet.createdAt,
            expiresAt = packet.expiresAt,
            hopLimit = packet.hopLimit,
            hopCount = packet.hopCount + 1, // increment hop count
            status = initialStatus,
            relayCount = 1,
            latitude = packet.latitude,
            longitude = packet.longitude,
            locationAccuracy = packet.locationAccuracy,
            locationTimestamp = packet.locationTimestamp,
            isEmergency = packet.isEmergency(),
            deliveryAckReceived = packet.recipientId == localNodeId
        )

        messageDao.insertMessage(entity)

        transferRecordDao.insertRecord(
            TransferRecordEntity(
                messageId = packet.messageId,
                peerId = peerId,
                timestamp = now,
                direction = TransferDirection.INCOMING,
                isSuccess = true,
                acknowledged = true
            )
        )

        return true
    }

    /**
     * Fetches all pending messages eligible for opportunistic store-carry-forward.
     * Prioritizes emergency messages first.
     */
    suspend fun getEligibleForwardPackets(targetPeerId: String): List<SafeMeshPacket> {
        val now = System.currentTimeMillis()
        val pendingEntities = messageDao.getPendingForwardableMessages(now)

        val eligiblePackets = mutableListOf<SafeMeshPacket>()
        for (entity in pendingEntities) {
            // Check if already transferred to this peer to prevent duplicate forwarding loops
            val alreadySent = transferRecordDao.hasAlreadyTransferredTo(entity.messageId, targetPeerId)
            if (!alreadySent && entity.senderId != targetPeerId && entity.hopCount < entity.hopLimit) {
                eligiblePackets.add(
                    SafeMeshPacket(
                        protocolVersion = SafeMeshPacket.PROTOCOL_VERSION,
                        messageId = entity.messageId,
                        messageType = entity.messageType,
                        senderId = entity.senderId,
                        recipientId = entity.recipientId,
                        encryptedPayload = entity.encryptedPayload,
                        iv = entity.iv,
                        ephemeralPublicKey = entity.ephemeralPublicKey,
                        authSignature = entity.authSignature,
                        createdAt = entity.createdAt,
                        expiresAt = entity.expiresAt,
                        hopLimit = entity.hopLimit,
                        hopCount = entity.hopCount,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        locationAccuracy = entity.locationAccuracy,
                        locationTimestamp = entity.locationTimestamp
                    )
                )
            }
        }

        return eligiblePackets
    }

    suspend fun recordSuccessfulRelay(messageId: String, peerId: String) {
        val now = System.currentTimeMillis()
        messageDao.recordRelaySuccess(messageId, DeliveryStatus.RELAYED)
        transferRecordDao.insertRecord(
            TransferRecordEntity(
                messageId = messageId,
                peerId = peerId,
                timestamp = now,
                direction = TransferDirection.OUTGOING,
                isSuccess = true,
                acknowledged = true
            )
        )
    }

    suspend fun recordDeliveredAck(messageId: String, peerId: String) {
        val now = System.currentTimeMillis()
        messageDao.markDelivered(messageId)
        transferRecordDao.insertRecord(
            TransferRecordEntity(
                messageId = messageId,
                peerId = peerId,
                timestamp = now,
                direction = TransferDirection.OUTGOING,
                isSuccess = true,
                acknowledged = true
            )
        )
    }

    suspend fun updateStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateStatus(messageId, status)
    }

    suspend fun cleanExpired(): Int {
        val now = System.currentTimeMillis()
        duplicateCacheDao.cleanExpired(now)
        return messageDao.markExpiredMessages(now)
    }

    suspend fun clearAllData() {
        messageDao.deleteAll()
        duplicateCacheDao.deleteAll()
        transferRecordDao.deleteAll()
    }
}
