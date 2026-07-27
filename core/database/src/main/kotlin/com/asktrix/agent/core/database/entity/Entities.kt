package com.asktrix.agent.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The local store.
 *
 * §3 forbids a permanent customer database on the device: this is an **ephemeral, encrypted, TTL'd
 * cache**, purged on logout, on integrity failure, and on remote wipe. Two properties enforce that:
 *
 *  1. Every cached row carries [CachedClientEntity.expiresAtMillis]. Expired rows are not served.
 *  2. No entity has a field for an unmasked phone number or email. The cache cannot hold what the
 *     API never sends (§4, ADR-0003).
 */

@Entity(
    tableName = "cached_clients",
    indices = [Index("processStatus"), Index("followUpAtMillis")],
)
data class CachedClientEntity(
    @PrimaryKey val clientId: String,
    val name: String,
    val serviceId: String?,
    val processStatus: String,
    val paymentStatus: String,
    val governmentStatus: String,
    val documentsPending: Int,
    val followUpAtMillis: Long?,
    val lastInteractionAtMillis: Long?,
    val version: Int,
    // Pre-masked, exactly as received. There is deliberately no unmasked counterpart.
    val phoneMasked: String,
    val emailMasked: String,
    val callable: Boolean,
    val cachedAtMillis: Long,
    /** After this instant the row is stale and must be refreshed or discarded (§3). */
    val expiresAtMillis: Long,
)

@Entity(
    tableName = "cached_timeline",
    indices = [Index("clientId"), Index("occurredAtMillis")],
)
data class CachedTimelineEntity(
    @PrimaryKey val entryId: String,
    val clientId: String,
    val kind: String,
    val summary: String,
    val actorName: String?,
    val callRecordId: String?,
    val occurredAtMillis: Long,
    val expiresAtMillis: Long,
)

@Entity(tableName = "cached_call_records", indices = [Index("clientId"), Index("startedAtMillis")])
data class CachedCallRecordEntity(
    @PrimaryKey val callRecordId: String,
    val callSessionId: String?,
    val clientId: String,
    val clientName: String?,
    val direction: String,
    val state: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val recordingAvailable: Boolean,
    val expiresAtMillis: Long,
)

/**
 * The outbox (§9, §23).
 *
 * Every write the employee makes is enqueued here **before** any network attempt, so nothing is lost
 * when the device is offline and nothing is duplicated on retry. [idempotencyKey] is generated once,
 * at enqueue time, and reused on every attempt - that is what makes a retry safe.
 */
@Entity(tableName = "outbox", indices = [Index("state"), Index("nextAttemptAtMillis")])
data class OutboxEntity(
    @PrimaryKey val id: String,
    /** Generated once and never regenerated; the server dedupes on it. */
    val idempotencyKey: String,
    val kind: String,
    /** The target entity, e.g. a clientId - used to show pending state in the UI. */
    val targetId: String?,
    /** JSON request body, serialised at enqueue time. */
    val payload: String,
    val state: String,
    val attempts: Int,
    val createdAtMillis: Long,
    val nextAttemptAtMillis: Long,
    /** Non-sensitive reason shown to the user when [state] is FAILED_PERMANENT. */
    val lastError: String?,
)

object OutboxKind {
    const val STATUS_UPDATE = "STATUS_UPDATE"
    const val REMARK = "REMARK"
    const val ATTENDANCE = "ATTENDANCE"
    const val LOCATION_BATCH = "LOCATION_BATCH"
    const val CALL_REQUEST = "CALL_REQUEST"
}

object OutboxState {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val SENT = "SENT"
    /** Terminal. Surfaced to the user rather than retried forever. */
    const val FAILED_PERMANENT = "FAILED_PERMANENT"
}
