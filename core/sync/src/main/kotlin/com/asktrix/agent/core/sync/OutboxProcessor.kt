package com.asktrix.agent.core.sync

import com.asktrix.agent.core.common.log.AsktrixLog
import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.database.entity.OutboxEntity
import com.asktrix.agent.core.database.entity.OutboxKind
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.apiCall
import com.asktrix.agent.core.network.dto.AttendanceRequestDto
import com.asktrix.agent.core.network.dto.CallRequestDto
import com.asktrix.agent.core.network.dto.LocationPingBatchDto
import com.asktrix.agent.core.network.dto.RemarkRequestDto
import com.asktrix.agent.core.network.dto.StatusUpdateRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Drains the outbox, one item at a time, in the order the employee acted.
 *
 * Order matters and is deliberate: a remark added before a status change should reach the CRM in
 * that order, and processing serially is what guarantees it. Throughput is irrelevant here - a field
 * agent generates a handful of actions per hour, not thousands.
 */
@Singleton
class OutboxProcessor @Inject constructor(
    private val outbox: Outbox,
    private val api: AsktrixApi,
    private val json: Json,
) {

    /** Returns true when every due item was delivered, so the worker knows whether to reschedule. */
    suspend fun drain(): Boolean {
        outbox.recoverStranded()
        var allSucceeded = true

        for (item in outbox.due()) {
            outbox.markInFlight(item)
            when (val result = send(item)) {
                is AsktrixResult.Success -> outbox.markSent(item)
                is AsktrixResult.Failure -> {
                    outbox.markFailed(item, result.error)
                    allSucceeded = false
                    // Stop on a connectivity failure: the next items would fail identically, and
                    // burning their retry budget against a network that is simply absent is waste.
                    if (result.error is AsktrixError.Offline) break
                }
            }
        }
        return allSucceeded
    }

    private suspend fun send(item: OutboxEntity): AsktrixResult<*> = when (item.kind) {
        OutboxKind.STATUS_UPDATE -> apiCall(json) {
            api.updateStatus(
                clientId = requireNotNull(item.targetId) { "STATUS_UPDATE requires a clientId" },
                idempotencyKey = item.idempotencyKey,
                body = json.decodeFromString(StatusUpdateRequestDto.serializer(), item.payload),
            )
        }

        OutboxKind.REMARK -> apiCall(json) {
            api.addRemark(
                clientId = requireNotNull(item.targetId) { "REMARK requires a clientId" },
                idempotencyKey = item.idempotencyKey,
                body = json.decodeFromString(RemarkRequestDto.serializer(), item.payload),
            )
        }

        OutboxKind.ATTENDANCE -> apiCall(json) {
            api.attendance(
                idempotencyKey = item.idempotencyKey,
                body = json.decodeFromString(AttendanceRequestDto.serializer(), item.payload),
            )
        }

        OutboxKind.LOCATION_BATCH -> apiCall(json) {
            api.uploadPings(
                idempotencyKey = item.idempotencyKey,
                body = json.decodeFromString(LocationPingBatchDto.serializer(), item.payload),
            )
        }

        OutboxKind.CALL_REQUEST -> apiCall(json) {
            api.placeCall(
                idempotencyKey = item.idempotencyKey,
                body = json.decodeFromString(CallRequestDto.serializer(), item.payload),
            )
        }

        else -> {
            AsktrixLog.e(TAG, "Unknown outbox kind '${item.kind}' - dropping to avoid a retry loop")
            AsktrixResult.Failure(AsktrixError.Unexpected("unknown outbox kind"))
        }
    }

    private companion object {
        const val TAG = "OutboxProcessor"
    }
}
