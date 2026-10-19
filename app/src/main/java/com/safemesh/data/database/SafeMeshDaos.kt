package com.safemesh.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.PeerConnectionState
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY isEmergency DESC, createdAt DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE messageId = :id")
    fun getMessageByIdFlow(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE status IN ('SAVED_LOCAL', 'SEARCHING', 'TRANSFERRING', 'RELAYED', 'FORWARDED') AND expiresAt > :now ORDER BY isEmergency DESC, createdAt ASC")
    suspend fun getPendingForwardableMessages(now: Long = System.currentTimeMillis()): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status IN ('SAVED_LOCAL', 'SEARCHING', 'TRANSFERRING', 'RELAYED', 'FORWARDED') AND expiresAt > :now")
    fun getPendingMessagesFlow(now: Long = System.currentTimeMillis()): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: DeliveryStatus)

    @Query("UPDATE messages SET relayCount = relayCount + 1, status = :status WHERE messageId = :id")
    suspend fun recordRelaySuccess(id: String, status: DeliveryStatus = DeliveryStatus.RELAYED)

    @Query("UPDATE messages SET deliveryAckReceived = 1, status = :status WHERE messageId = :id")
    suspend fun markDelivered(id: String, status: DeliveryStatus = DeliveryStatus.DELIVERED)

    @Query("UPDATE messages SET status = 'EXPIRED' WHERE expiresAt <= :now AND status != 'DELIVERED'")
    suspend fun markExpiredMessages(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM messages WHERE expiresAt <= :cutoff AND status = 'EXPIRED'")
    suspend fun purgeOldExpired(cutoff: Long): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeersFlow(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :peerId")
    suspend fun getPeer(peerId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)

    @Query("UPDATE peers SET connectionState = :state WHERE peerId = :peerId")
    suspend fun updateConnectionState(peerId: String, state: PeerConnectionState)

    @Query("UPDATE peers SET transferCount = transferCount + 1, lastTransferStatus = :status WHERE peerId = :peerId")
    suspend fun recordTransfer(peerId: String, status: String)

    @Query("DELETE FROM peers WHERE lastSeen < :cutoff AND isDemonstration = 0")
    suspend fun deleteStalePeers(cutoff: Long): Int

    @Query("DELETE FROM peers")
    suspend fun deleteAll()
}

@Dao
interface TransferRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TransferRecordEntity): Long

    @Query("SELECT * FROM transfer_records WHERE messageId = :messageId ORDER BY timestamp DESC")
    fun getRecordsForMessageFlow(messageId: String): Flow<List<TransferRecordEntity>>

    @Query("SELECT COUNT(*) > 0 FROM transfer_records WHERE messageId = :messageId AND peerId = :peerId AND isSuccess = 1")
    suspend fun hasAlreadyTransferredTo(messageId: String, peerId: String): Boolean

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}

@Dao
interface DuplicateCacheDao {
    @Query("SELECT COUNT(*) > 0 FROM duplicate_cache WHERE messageId = :messageId")
    suspend fun hasSeen(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordMessageId(entry: DuplicateCacheEntity): Long

    @Query("DELETE FROM duplicate_cache WHERE expiresAt <= :now")
    suspend fun cleanExpired(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM duplicate_cache")
    suspend fun deleteAll()
}
