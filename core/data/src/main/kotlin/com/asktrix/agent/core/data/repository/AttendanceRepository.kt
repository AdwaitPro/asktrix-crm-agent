package com.asktrix.agent.core.data.repository

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.common.result.map
import com.asktrix.agent.core.common.time.TimeSource
import com.asktrix.agent.core.database.entity.OutboxKind
import com.asktrix.agent.core.location.LocationSample
import com.asktrix.agent.core.location.LocationSampler
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.apiCall
import com.asktrix.agent.core.network.dto.AttendanceRequestDto
import com.asktrix.agent.core.network.dto.GeoPointDto
import com.asktrix.agent.core.sync.Outbox
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class AttendanceToday(
    val checkedIn: Boolean,
    val checkInAt: Instant? = null,
    val checkOutAt: Instant? = null,
    val workedSeconds: Long? = null,
)

/**
 * Check-in and check-out (§11).
 *
 * A location fix is required, because attendance without a place is not attendance. The action then
 * goes through the outbox so an agent checking in from a basement or a village with no coverage is
 * still recorded — the timestamp travels with the request, and the server keeps both the reported
 * time and its own receipt time.
 */
@Singleton
class AttendanceRepository @Inject constructor(
    private val api: AsktrixApi,
    private val sampler: LocationSampler,
    private val outbox: Outbox,
    private val time: TimeSource,
    private val json: Json,
) {

    suspend fun today(): AsktrixResult<AttendanceToday> =
        apiCall(json) { api.attendanceToday() }.map { dto ->
            AttendanceToday(
                checkedIn = dto.checkedIn,
                checkInAt = dto.checkInAt?.let(Instant::parse),
                checkOutAt = dto.checkOutAt?.let(Instant::parse),
                workedSeconds = dto.workedSeconds,
            )
        }

    /**
     * Records attendance. Returns the sample taken, so the UI can show where the employee was
     * recorded rather than asking them to trust it silently.
     */
    suspend fun record(checkIn: Boolean): AsktrixResult<LocationSample> {
        val sample = when (val result = sampler.sample()) {
            is AsktrixResult.Success -> result.data
            is AsktrixResult.Failure -> return AsktrixResult.Failure(
                when (result.error) {
                    is AsktrixError.PermissionDenied -> result.error
                    else -> AsktrixError.Unexpected("Could not get a location fix.")
                },
            )
        }

        val payload = json.encodeToString(
            AttendanceRequestDto.serializer(),
            AttendanceRequestDto(
                kind = if (checkIn) "CHECK_IN" else "CHECK_OUT",
                occurredAt = time.now().toString(),
                location = GeoPointDto(
                    latitude = sample.latitudeE7,
                    longitude = sample.longitudeE7,
                    accuracyMetres = sample.accuracyMetres,
                ),
                hasPhoto = false,
            ),
        )
        outbox.enqueue(OutboxKind.ATTENDANCE, targetId = null, payload = payload)
        return AsktrixResult.Success(sample)
    }
}
