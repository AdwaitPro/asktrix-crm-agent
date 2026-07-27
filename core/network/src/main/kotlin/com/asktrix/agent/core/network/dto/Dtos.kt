package com.asktrix.agent.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Asktrix CRM API (`api/openapi.yaml`).
 *
 * ARCHITECTURAL INVARIANT - read before adding a field.
 *
 * There is no `phone` or `email` property anywhere in this file, and none may be added. Customer
 * contact details exist only as the pre-masked strings in [MaskedContactDto]. The server never emits
 * an unmasked value, so the device cannot leak one (§4, ADR-0003).
 *
 * A privacy test in `core:network` reflects over these classes and fails the build if a property
 * name suggests an unmasked contact field.
 */

@Serializable
data class LoginRequestDto(
    val employeeCode: String,
    val password: String,
    val device: DeviceBindingDto,
)

@Serializable
data class DeviceBindingDto(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val attestationStatement: String? = null,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
    val deviceId: String,
)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val employee: EmployeeDto,
)

@Serializable
data class EmployeeDto(
    val employeeId: String,
    val employeeCode: String,
    val displayName: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    /** The §13 quick actions this role is allowed to apply. Server-issued (§2). */
    val allowedStatuses: List<String> = emptyList(),
)

@Serializable
data class MaskedContactDto(
    /** Display-only, e.g. `98XXXXXX12`. Never dialable. */
    val phoneMasked: String,
    /** Display-only, e.g. `siv****@gmail.com`. Never sendable. */
    val emailMasked: String,
    val callable: Boolean,
)

@Serializable
data class ClientSummaryDto(
    val clientId: String,
    val name: String,
    val serviceId: String? = null,
    val processStatus: String,
    val paymentStatus: String,
    val documentsPending: Int,
    val followUpAt: String? = null,
    val lastInteractionAt: String? = null,
    val version: Int,
)

@Serializable
data class ClientDetailDto(
    val clientId: String,
    val name: String,
    val serviceId: String? = null,
    val processStatus: String,
    val paymentStatus: String,
    val governmentStatus: String,
    val documentsPending: Int,
    val followUpAt: String? = null,
    val lastInteractionAt: String? = null,
    val version: Int,
    val contact: MaskedContactDto,
    val assignedEmployeeId: String? = null,
    val internalRemarks: List<RemarkDto> = emptyList(),
    val documents: List<DocumentRefDto> = emptyList(),
    /** How long this record may stay in the encrypted cache before it must be refreshed (§3). */
    val cacheTtlSeconds: Int = 3600,
)

@Serializable
data class RemarkDto(
    val remarkId: String,
    val body: String,
    val authorName: String,
    val createdAt: String,
)

@Serializable
data class DocumentRefDto(
    val documentId: String,
    val kind: String,
    val status: String,
    val receivedAt: String? = null,
)

@Serializable
data class ClientPageDto(
    val items: List<ClientSummaryDto> = emptyList(),
    val nextCursor: String? = null,
    /** Authoritative server clock; the client measures its own skew against this. */
    val serverTime: String? = null,
)

@Serializable
data class StatusUpdateRequestDto(
    val status: String,
    val note: String? = null,
    val followUpAt: String? = null,
    val occurredAt: String? = null,
    val expectedVersion: Int? = null,
)

@Serializable
data class RemarkRequestDto(
    val body: String,
    val recordedAt: String? = null,
)

@Serializable
data class TimelineEntryDto(
    val entryId: String,
    val kind: String,
    val occurredAt: String,
    val summary: String,
    val actorName: String? = null,
    val callRecordId: String? = null,
)

@Serializable
data class TimelinePageDto(
    val items: List<TimelineEntryDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CallRequestDto(
    val clientId: String,
    val serviceId: String? = null,
    val reason: String? = null,
)

@Serializable
data class CallSessionDto(
    val callSessionId: String,
    val clientId: String,
    val state: String,
    val requestedAt: String,
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Int? = null,
    val failureReason: String? = null,
    /** Present when the CRM places the call over data rather than through a carrier (ADR-0006). */
    val rtc: RtcSessionDto? = null,
)

@Serializable
data class RtcSessionDto(
    val roomId: String,
    /** The page this device opens to take part in the call. */
    val agentUrl: String,
    /** The one-time link sent to the customer. They install nothing. */
    val customerUrl: String,
)

@Serializable
data class CallRecordDto(
    val callRecordId: String,
    val callSessionId: String? = null,
    val clientId: String,
    val clientName: String? = null,
    val direction: String,
    val state: String,
    val startedAt: String,
    val durationSeconds: Int,
    /** Whether a recording exists server-side. The device never receives a link to the audio (§6). */
    val recordingAvailable: Boolean,
)

@Serializable
data class CallRecordPageDto(
    val items: List<CallRecordDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class GeoPointDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
    val altitudeMetres: Float? = null,
)

@Serializable
data class AttendanceRequestDto(
    val kind: String,
    val occurredAt: String,
    val location: GeoPointDto,
    val hasPhoto: Boolean = false,
)

@Serializable
data class AttendanceRecordDto(
    val attendanceId: String,
    val kind: String,
    val occurredAt: String,
    val recordedAt: String,
    val location: GeoPointDto? = null,
    val photoUploaded: Boolean = false,
)

@Serializable
data class AttendanceTodayDto(
    val checkedIn: Boolean,
    val checkInAt: String? = null,
    val checkOutAt: String? = null,
    val workedSeconds: Long? = null,
)

@Serializable
data class LocationPingDto(
    val sampledAt: String,
    val location: GeoPointDto,
    val isMocked: Boolean = false,
    val batteryPercent: Int? = null,
)

@Serializable
data class LocationPingBatchDto(val pings: List<LocationPingDto>)

@Serializable
data class LocationPingAckDto(
    val accepted: Int,
    val rejectedOutsideWorkingHours: Int,
)

@Serializable
data class WorkingHoursDto(
    val dayOfWeek: String,
    val startLocalTime: String,
    val endLocalTime: String,
)

@Serializable
data class LocationPolicyDto(
    val enabled: Boolean,
    val sampleIntervalSeconds: Int,
    val workingHours: List<WorkingHoursDto> = emptyList(),
    val timezone: String,
)

@Serializable
data class PushTokenRequestDto(val token: String)

@Serializable
data class ComplianceChecksDto(
    val rootIndicators: Boolean = false,
    val emulatorIndicators: Boolean = false,
    val debuggerAttached: Boolean = false,
    val developerOptionsEnabled: Boolean = false,
    val screenLockSet: Boolean = true,
    val isDeviceOwnerManaged: Boolean = false,
    /** `ActivityManager.isBackgroundRestricted()` - reveals OEM battery restriction in the field. */
    val backgroundRestricted: Boolean = false,
    val appStandbyBucket: Int? = null,
)

@Serializable
data class ComplianceReportDto(
    val deviceId: String,
    val attestationStatement: String? = null,
    val integrityToken: String? = null,
    val checks: ComplianceChecksDto,
)

@Serializable
data class ComplianceVerdictDto(
    val compliant: Boolean,
    val action: String,
    val reason: String? = null,
)

/** The API's error envelope. `message` is safe to display and never carries PII. */
@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Int? = null,
    @SerialName("current") val currentClient: ClientDetailDto? = null,
)
