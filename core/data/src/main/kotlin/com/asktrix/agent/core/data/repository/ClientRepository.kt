package com.asktrix.agent.core.data.repository

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.common.time.TimeSource
import com.asktrix.agent.core.data.mapper.toDomain
import com.asktrix.agent.core.data.mapper.toEntity
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.data.model.TimelineEntry
import com.asktrix.agent.core.database.dao.ClientDao
import com.asktrix.agent.core.database.dao.TimelineDao
import com.asktrix.agent.core.database.entity.OutboxKind
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.apiCall
import com.asktrix.agent.core.network.dto.StatusUpdateRequestDto
import com.asktrix.agent.core.sync.Outbox
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Offline-first access to clients.
 *
 * The encrypted cache is the single source of truth the UI observes. The network refreshes it; a
 * failed refresh never blanks the screen, because a field agent standing in front of a customer with
 * no signal still needs to see the case.
 *
 * Writes go through the outbox, never straight to the network, so an action taken underground or on
 * a dead network is preserved and delivered later exactly once (§9, §23).
 */
@Singleton
class ClientRepository @Inject constructor(
    private val api: AsktrixApi,
    private val clientDao: ClientDao,
    private val timelineDao: TimelineDao,
    private val outbox: Outbox,
    private val time: TimeSource,
    private val json: Json,
) {

    /**
     * Assigned clients (§12), merged with pending outbox state so the UI can show that an edit is
     * still queued.
     */
    fun observeClients(): Flow<List<Client>> =
        combine(
            clientDao.observeAll(time.now().toEpochMilli()),
            outbox.observePendingCount(),
        ) { cached, _ ->
            cached.map { it.toDomain() }
        }

    fun observeClient(clientId: String): Flow<Client?> =
        combine(
            clientDao.observe(clientId, time.now().toEpochMilli()),
            outbox.observePendingFor(clientId),
        ) { cached, pending ->
            cached?.toDomain(hasPendingChanges = pending.isNotEmpty())
        }

    fun observeTimeline(clientId: String): Flow<List<TimelineEntry>> =
        timelineDao.observeFor(clientId, time.now().toEpochMilli())
            .map { entries -> entries.map { it.toDomain() } }

    /**
     * Refreshes the assigned-client list.
     *
     * Returns the error on failure so the UI can show an offline banner, but the cached list stays
     * on screen either way.
     */
    suspend fun refreshClients(): AsktrixResult<Unit> {
        val now = time.now().toEpochMilli()
        return when (val result = apiCall(json) { api.clients(limit = PAGE_SIZE) }) {
            is AsktrixResult.Success -> {
                clientDao.deleteExpired(now)
                clientDao.upsertAll(
                    result.data.items.map { it.toEntity(now, LIST_TTL_SECONDS) },
                )
                AsktrixResult.Success(Unit)
            }

            is AsktrixResult.Failure -> result
        }
    }

    /** Fetches full detail, including the masked contact block that the list response omits. */
    suspend fun refreshClient(clientId: String): AsktrixResult<Client> {
        val now = time.now().toEpochMilli()
        return when (val result = apiCall(json) { api.client(clientId) }) {
            is AsktrixResult.Success -> {
                clientDao.upsertAll(listOf(result.data.toEntity(now)))
                AsktrixResult.Success(result.data.toDomain())
            }

            is AsktrixResult.Failure -> result
        }
    }

    suspend fun refreshTimeline(clientId: String): AsktrixResult<Unit> {
        val now = time.now().toEpochMilli()
        return when (val result = apiCall(json) { api.timeline(clientId, limit = PAGE_SIZE) }) {
            is AsktrixResult.Success -> {
                timelineDao.upsertAll(
                    result.data.items.map { dto ->
                        dto.toEntity(now).copy(clientId = clientId)
                    },
                )
                AsktrixResult.Success(Unit)
            }

            is AsktrixResult.Failure -> result
        }
    }

    /**
     * Applies a §13 quick status update.
     *
     * Enqueued in the outbox first, so the action survives being offline, and `expectedVersion` is
     * carried so a concurrent change on another device produces a 409 the sync engine can resolve
     * rather than a silent overwrite.
     */
    suspend fun updateStatus(
        clientId: String,
        status: ProcessStatus,
        note: String? = null,
        followUpAt: Instant? = null,
        expectedVersion: Int? = null,
    ): AsktrixResult<Unit> {
        if (status == ProcessStatus.CALLBACK_SCHEDULED && followUpAt == null) {
            return AsktrixResult.Failure(
                AsktrixError.Validation(mapOf("followUpAt" to "A callback needs a date and time.")),
            )
        }

        val payload = json.encodeToString(
            StatusUpdateRequestDto.serializer(),
            StatusUpdateRequestDto(
                status = status.name,
                note = note?.takeIf { it.isNotBlank() },
                followUpAt = followUpAt?.toString(),
                occurredAt = time.now().toString(),
                expectedVersion = expectedVersion,
            ),
        )
        outbox.enqueue(OutboxKind.STATUS_UPDATE, clientId, payload)
        return AsktrixResult.Success(Unit)
    }

    /** Clears every cached record. Called on logout, integrity failure and remote wipe (§3). */
    suspend fun clearCache() {
        clientDao.deleteAll()
        timelineDao.deleteAll()
        outbox.clear()
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val LIST_TTL_SECONDS = 900
    }
}
