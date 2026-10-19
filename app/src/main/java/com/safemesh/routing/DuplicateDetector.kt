package com.safemesh.routing

import com.safemesh.data.database.DuplicateCacheDao
import com.safemesh.data.database.DuplicateCacheEntity
import java.util.concurrent.ConcurrentHashMap

class DuplicateDetector(
    private val duplicateCacheDao: DuplicateCacheDao
) {
    // In-memory fast cache for high-throughput packet deduplication
    private val memoryCache = ConcurrentHashMap<String, Long>()

    suspend fun isDuplicate(messageId: String): Boolean {
        if (memoryCache.containsKey(messageId)) {
            return true
        }
        val inDb = duplicateCacheDao.hasSeen(messageId)
        if (inDb) {
            memoryCache[messageId] = System.currentTimeMillis()
            return true
        }
        return false
    }

    suspend fun markSeen(messageId: String, expiresAt: Long) {
        val now = System.currentTimeMillis()
        memoryCache[messageId] = expiresAt
        duplicateCacheDao.recordMessageId(
            DuplicateCacheEntity(
                messageId = messageId,
                firstSeenTimestamp = now,
                expiresAt = expiresAt
            )
        )
    }

    fun purgeMemoryCache(currentTime: Long = System.currentTimeMillis()) {
        val iterator = memoryCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= currentTime) {
                iterator.remove()
            }
        }
    }
}
