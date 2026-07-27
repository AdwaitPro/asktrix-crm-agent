package com.asktrix.agent.core.data.repository

import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.common.result.map
import com.asktrix.agent.core.data.mapper.toInstantOrNull
import com.asktrix.agent.core.data.model.CallDirection
import com.asktrix.agent.core.data.model.CallRecord
import com.asktrix.agent.core.data.model.CallSession
import com.asktrix.agent.core.data.model.CallState
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.apiCall
import com.asktrix.agent.core.network.dto.CallRequestDto
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Click-to-call (§5) and call history (§7).
 *
 * The device sends a `clientId` and nothing else. It never dials, never holds a number, and never
 * reads the system call log — which is why the manifest declares no telephony permissions at all
 * (ADR-0002).
 *
 * When the real CPaaS integration lands it replaces the CRM's provider adapter, not this class: the
 * app's view of a call is already "ask, then observe".
 */
@Singleton
class CallRepository @Inject constructor(
    private val api: AsktrixApi,
    private val json: Json,
) {

    /**
     * Requests a call.
     *
     * Sent directly rather than through the outbox: a call is only meaningful right now. Queuing one
     * to be placed hours later, when the employee has moved on, would be worse than failing.
     */
    suspend fun placeCall(clientId: String, reason: String? = null): AsktrixResult<CallSession> =
        apiCall(json) {
            api.placeCall(
                idempotencyKey = UUID.randomUUID().toString(),
                body = CallRequestDto(clientId = clientId, reason = reason),
            )
        }.map { it.toDomain() }

    /**
     * Polls a session until it reaches a terminal state.
     *
     * Polling is the fallback path; FCM delivers the outcome faster when it is configured. The
     * interval is short because a person is watching the screen waiting for the call to connect, and
     * it stops as soon as the call is over so it cannot become a background drain.
     */
    fun observeCall(callSessionId: String): Flow<CallSession> = flow {
        var elapsed = 0L
        while (elapsed < MAX_POLL_MILLIS) {
            when (val result = apiCall(json) { api.callSession(callSessionId) }) {
                is AsktrixResult.Success -> {
                    val session = result.data.toDomain()
                    emit(session)
                    if (session.state.isTerminal) return@flow
                }
                // A transient failure must not end the call UI; keep polling until the deadline.
                is AsktrixResult.Failure -> Unit
            }
            delay(POLL_INTERVAL_MILLIS)
            elapsed += POLL_INTERVAL_MILLIS
        }
    }

    suspend fun history(clientId: String? = null): AsktrixResult<List<CallRecord>> =
        apiCall(json) { api.callHistory(clientId = clientId, limit = HISTORY_LIMIT) }
            .map { page ->
                page.items.map { dto ->
                    CallRecord(
                        callRecordId = dto.callRecordId,
                        clientId = dto.clientId,
                        clientName = dto.clientName,
                        direction = CallDirection.from(dto.direction),
                        state = CallState.from(dto.state),
                        startedAt = dto.startedAt.toInstantOrNull() ?: Instant.EPOCH,
                        durationSeconds = dto.durationSeconds,
                        recordingAvailable = dto.recordingAvailable,
                    )
                }
            }

    private fun com.asktrix.agent.core.network.dto.CallSessionDto.toDomain(): CallSession =
        CallSession(
            callSessionId = callSessionId,
            clientId = clientId,
            state = CallState.from(state),
            requestedAt = requestedAt.toInstantOrNull() ?: Instant.now(),
            connectedAt = connectedAt.toInstantOrNull(),
            endedAt = endedAt.toInstantOrNull(),
            durationSeconds = durationSeconds,
            failureReason = failureReason,
        )

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_500L
        const val MAX_POLL_MILLIS = 3 * 60 * 1000L
        const val HISTORY_LIMIT = 50
    }
}
