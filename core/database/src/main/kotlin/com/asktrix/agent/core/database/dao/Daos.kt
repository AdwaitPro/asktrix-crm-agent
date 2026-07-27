package com.asktrix.agent.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.asktrix.agent.core.database.entity.CachedCallRecordEntity
import com.asktrix.agent.core.database.entity.CachedClientEntity
import com.asktrix.agent.core.database.entity.CachedTimelineEntity
import com.asktrix.agent.core.database.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    /**
     * Expired rows are filtered in the query rather than by the caller, so there is no code path
     * that can accidentally serve stale customer data (§3).
     */
    @Query("SELECT * FROM cached_clients WHERE expiresAtMillis > :now ORDER BY COALESCE(followUpAtMillis, lastInteractionAtMillis, cachedAtMillis) DESC")
    fun observeAll(now: Long): Flow<List<CachedClientEntity>>

    @Query("SELECT * FROM cached_clients WHERE clientId = :clientId AND expiresAtMillis > :now")
    suspend fun find(clientId: String, now: Long): CachedClientEntity?

    @Query("SELECT * FROM cached_clients WHERE clientId = :clientId AND expiresAtMillis > :now")
    fun observe(clientId: String, now: Long): Flow<CachedClientEntity?>

    @Upsert
    suspend fun upsertAll(clients: List<CachedClientEntity>)

    @Query("DELETE FROM cached_clients WHERE expiresAtMillis <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM cached_clients")
    suspend fun deleteAll()
}

@Dao
interface TimelineDao {

    @Query("SELECT * FROM cached_timeline WHERE clientId = :clientId AND expiresAtMillis > :now ORDER BY occurredAtMillis DESC")
    fun observeFor(clientId: String, now: Long): Flow<List<CachedTimelineEntity>>

    @Upsert
    suspend fun upsertAll(entries: List<CachedTimelineEntity>)

    @Query("DELETE FROM cached_timeline WHERE expiresAtMillis <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM cached_timeline")
    suspend fun deleteAll()
}

@Dao
interface CallRecordDao {

    @Query("SELECT * FROM cached_call_records WHERE expiresAtMillis > :now ORDER BY startedAtMillis DESC")
    fun observeAll(now: Long): Flow<List<CachedCallRecordEntity>>

    @Query("SELECT * FROM cached_call_records WHERE clientId = :clientId AND expiresAtMillis > :now ORDER BY startedAtMillis DESC")
    fun observeFor(clientId: String, now: Long): Flow<List<CachedCallRecordEntity>>

    @Upsert
    suspend fun upsertAll(records: List<CachedCallRecordEntity>)

    @Query("DELETE FROM cached_call_records")
    suspend fun deleteAll()
}

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(item: OutboxEntity)

    /** Work that is due now, oldest first — FIFO preserves the order the employee acted in. */
    @Query("SELECT * FROM outbox WHERE state = 'PENDING' AND nextAttemptAtMillis <= :now ORDER BY createdAtMillis ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 25): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE state IN ('PENDING','IN_FLIGHT') ORDER BY createdAtMillis ASC")
    fun observePending(): Flow<List<OutboxEntity>>

    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING','IN_FLIGHT')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE targetId = :targetId AND state IN ('PENDING','IN_FLIGHT')")
    fun observePendingFor(targetId: String): Flow<List<OutboxEntity>>

    @Query("UPDATE outbox SET state = :state, attempts = :attempts, nextAttemptAtMillis = :nextAttemptAtMillis, lastError = :lastError WHERE id = :id")
    suspend fun update(id: String, state: String, attempts: Int, nextAttemptAtMillis: Long, lastError: String?)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Recovers items stranded IN_FLIGHT by process death. Safe because every request carries a
     * stable idempotency key, so re-sending cannot duplicate the action.
     */
    @Query("UPDATE outbox SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun requeueInFlight()

    @Query("DELETE FROM outbox WHERE state = 'SENT'")
    suspend fun purgeSent()

    @Query("DELETE FROM outbox")
    suspend fun deleteAll()
}
