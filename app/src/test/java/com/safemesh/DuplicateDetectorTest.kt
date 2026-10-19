package com.safemesh

import com.safemesh.data.database.DuplicateCacheDao
import com.safemesh.data.database.DuplicateCacheEntity
import com.safemesh.routing.DuplicateDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class FakeDuplicateCacheDao : DuplicateCacheDao {
    private val db = ConcurrentHashMap<String, DuplicateCacheEntity>()

    override suspend fun hasSeen(messageId: String): Boolean {
        return db.containsKey(messageId)
    }

    override suspend fun recordMessageId(entry: DuplicateCacheEntity): Long {
        db[entry.messageId] = entry
        return 1L
    }

    override suspend fun cleanExpired(now: Long): Int {
        var removed = 0
        val it = db.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.expiresAt <= now) {
                it.remove()
                removed++
            }
        }
        return removed
    }

    override suspend fun deleteAll() {
        db.clear()
    }
}

class DuplicateDetectorTest {

    private lateinit var fakeDao: FakeDuplicateCacheDao
    private lateinit var detector: DuplicateDetector

    @Before
    fun setup() {
        fakeDao = FakeDuplicateCacheDao()
        detector = DuplicateDetector(fakeDao)
    }

    @Test
    fun testDuplicateDetectionFlow() = runBlocking {
        val messageId = "packet-unique-uuid-999"
        val now = System.currentTimeMillis()
        val expiresAt = now + 60000L

        // First check: should not be duplicate
        assertFalse(detector.isDuplicate(messageId))

        // Mark seen
        detector.markSeen(messageId, expiresAt)

        // Second check: must be recognized as duplicate immediately
        assertTrue(detector.isDuplicate(messageId))
    }

    @Test
    fun testPurgeExpiredFromMemoryCache() = runBlocking {
        val messageId = "packet-to-expire"
        val pastExpiry = System.currentTimeMillis() - 1000L

        detector.markSeen(messageId, pastExpiry)
        detector.purgeMemoryCache(System.currentTimeMillis())

        // Memory cache should be purged
        // (FakeDao still holds it until cleanExpired is called)
        fakeDao.cleanExpired(System.currentTimeMillis())
        assertFalse(detector.isDuplicate(messageId))
    }
}
