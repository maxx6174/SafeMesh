package com.safemesh.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.MessageType
import com.safemesh.model.PeerConnectionState
import com.safemesh.model.TransferDirection

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["status"]),
        Index(value = ["expiresAt"]),
        Index(value = ["isEmergency"]),
        Index(value = ["createdAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val encryptedPayload: String,
    val plaintextPreview: String?,
    val iv: String,
    val ephemeralPublicKey: String?,
    val authSignature: String,
    val messageType: MessageType,
    val createdAt: Long,
    val expiresAt: Long,
    val hopLimit: Int,
    val hopCount: Int,
    val status: DeliveryStatus,
    val relayCount: Int,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracy: Float?,
    val locationTimestamp: Long?,
    val isEmergency: Boolean,
    val deliveryAckReceived: Boolean
)

@Entity(
    tableName = "peers",
    indices = [
        Index(value = ["lastSeen"])
    ]
)
data class PeerEntity(
    @PrimaryKey
    val peerId: String,
    val deviceName: String?,
    val bluetoothAddress: String?,
    val lastSeen: Long,
    val connectionState: PeerConnectionState,
    val rssi: Int,
    val transferCount: Int,
    val lastTransferStatus: String?,
    val isDemonstration: Boolean = false
)

@Entity(
    tableName = "transfer_records",
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["peerId"]),
        Index(value = ["timestamp"])
    ]
)
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val peerId: String,
    val timestamp: Long,
    val direction: TransferDirection,
    val isSuccess: Boolean,
    val acknowledged: Boolean
)

@Entity(
    tableName = "duplicate_cache",
    indices = [
        Index(value = ["expiresAt"])
    ]
)
data class DuplicateCacheEntity(
    @PrimaryKey
    val messageId: String,
    val firstSeenTimestamp: Long,
    val expiresAt: Long
)
