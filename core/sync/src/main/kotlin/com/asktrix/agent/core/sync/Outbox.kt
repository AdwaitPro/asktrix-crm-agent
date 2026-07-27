package com.asktrix.agent.core.sync

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.time.TimeSource
import com.asktrix.agent.core.database.dao.OutboxDao
import com.asktrix.agent.core.database.entity.OutboxEntity
import com.asktrix.agent.core.database.entity.OutboxState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow

/**
 * The outbox (§9, §23).
 *
 * Every write the employee makes is enqueued here **before** any network call, and the UI reflects
 * it immediately. Nothing is lost when the device is offline, and nothing is duplicated on retry
 * because the idempotency key is generated once at enqueue time and reused on every attempt.
 */
@Singleton
class Outbox @Inject constructor(
    private val dao: OutboxDao,
    private val time: TimeSource,
) {

    /**
     * Enqueues an action. Returns the idempotency key, which the caller can use to correlate the
     * pending action with its eventual result.
     */
    suspend fun enqueue(kind: String, targetId: String?, payload: String): String {
        val key = UUID.randomUUID().toString()
        dao.enqueue(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = key,
                kind = kind,
                targetId = targetId,
                payload = payload,
                state = OutboxState.PENDING,
                attempts = 0,
                createdAtMillis = time.now().toEpochMilli(),
                nextAttemptAtMillis = time.now().toEpochMilli(),
                lastError = null,
            ),
        )
        return key
    }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    fun observePendingFor(targetId: String): Flow<List<OutboxEntity>> = dao.observePendingFor(targetId)

    suspend fun due(limit: Int = DEFAULT_BATCH): List<OutboxEntity> =
        dao.due(time.now().toEpochMilli(), limit)

    suspend fun markInFlight(item: OutboxEntity) =
        dao.update(item.id, OutboxState.IN_FLIGHT, item.attempts, item.nextAttemptAtMillis, null)

    suspend fun markSent(item: OutboxEntity) = dao.delete(item.id)

    /**
     * Records a failure and decides what happens next.
     *
     * The decision comes from the error *type*, not from a string match: [AsktrixError.Retryable]
     * backs off and tries again, [AsktrixError.Permanent] stops and surfaces to the user. Retrying a
     * permanently-rejected request forever would burn battery and hide a real problem.
     *
     * Retries are also capped, because "retryable" does not mean "retry indefinitely" — a server
     * that has been down for a day should surface, not spin.
     */
    suspend fun markFailed(item: OutboxEntity, error: AsktrixError) {
        val attempts = item.attempts + 1
        val retryable = error is AsktrixError.Retryable && attempts < MAX_ATTEMPTS

        if (!retryable) {
            dao.update(
                item.id,
                OutboxState.FAILED_PERMANENT,
                attempts,
                item.nextAttemptAtMillis,
                error::class.simpleName,
            )
            return
        }
        dao.update(
            item.id,
            OutboxState.PENDING,
            attempts,
            time.now().toEpochMilli() + backoffMillis(attempts),
            error::class.simpleName,
        )
    }

    /**
     * Exponential backoff with full jitter, capped.
     *
     * The jitter matters more than it looks: without it, a fleet of devices that all lost
     * connectivity at the same moment would retry in lockstep and hammer the CRM the instant the
     * network returns.
     */
    fun backoffMillis(attempts: Int): Long {
        val exponential = BASE_DELAY_MILLIS shl min(attempts - 1, MAX_SHIFT)
        val capped = min(exponential, MAX_DELAY_MILLIS)
        return Random.nextLong(capped / 2, capped + 1)
    }

    /** Called at startup: items stranded IN_FLIGHT by process death are safe to retry. */
    suspend fun recoverStranded() = dao.requeueInFlight()

    suspend fun clear() = dao.deleteAll()

    companion object {
        const val MAX_ATTEMPTS = 12
        private const val DEFAULT_BATCH = 25
        private const val BASE_DELAY_MILLIS = 5_000L
        private const val MAX_DELAY_MILLIS = 30 * 60 * 1000L
        private const val MAX_SHIFT = 10
    }
}
