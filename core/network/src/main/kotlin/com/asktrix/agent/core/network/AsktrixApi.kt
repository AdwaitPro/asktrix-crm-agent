package com.asktrix.agent.core.network

import com.asktrix.agent.core.network.dto.AttendanceRecordDto
import com.asktrix.agent.core.network.dto.AttendanceRequestDto
import com.asktrix.agent.core.network.dto.AttendanceTodayDto
import com.asktrix.agent.core.network.dto.AuthSessionDto
import com.asktrix.agent.core.network.dto.CallRecordPageDto
import com.asktrix.agent.core.network.dto.CallRequestDto
import com.asktrix.agent.core.network.dto.CallSessionDto
import com.asktrix.agent.core.network.dto.ClientDetailDto
import com.asktrix.agent.core.network.dto.ClientPageDto
import com.asktrix.agent.core.network.dto.ComplianceReportDto
import com.asktrix.agent.core.network.dto.ComplianceVerdictDto
import com.asktrix.agent.core.network.dto.EmployeeDto
import com.asktrix.agent.core.network.dto.LocationPingAckDto
import com.asktrix.agent.core.network.dto.LocationPingBatchDto
import com.asktrix.agent.core.network.dto.LocationPolicyDto
import com.asktrix.agent.core.network.dto.LoginRequestDto
import com.asktrix.agent.core.network.dto.PushTokenRequestDto
import com.asktrix.agent.core.network.dto.RefreshRequestDto
import com.asktrix.agent.core.network.dto.RemarkRequestDto
import com.asktrix.agent.core.network.dto.StatusUpdateRequestDto
import com.asktrix.agent.core.network.dto.TimelineEntryDto
import com.asktrix.agent.core.network.dto.TimelinePageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The CRM API surface, mirroring `api/openapi.yaml`.
 *
 * Every call returns `Response<T>` rather than a bare body so the repository layer can map HTTP
 * status to [com.asktrix.agent.core.common.result.AsktrixError] explicitly. Exceptions are reserved
 * for genuinely exceptional conditions; a 403 is an ordinary outcome this app must handle.
 *
 * `Idempotency-Key` is a required parameter on every mutating call - not optional, so it cannot be
 * forgotten at a call site (§9, §23).
 */
interface AsktrixApi {

    // --- Auth ---
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthSessionDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<AuthSessionDto>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/session")
    suspend fun session(): Response<EmployeeDto>

    // --- Clients (§3, §4, §12) ---
    @GET("clients")
    suspend fun clients(
        @Query("status") status: String? = null,
        @Query("needsFollowUp") needsFollowUp: Boolean? = null,
        @Query("query") query: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ClientPageDto>

    @GET("clients/{clientId}")
    suspend fun client(@Path("clientId") clientId: String): Response<ClientDetailDto>

    // --- Status and remarks (§13) ---
    @POST("clients/{clientId}/status")
    suspend fun updateStatus(
        @Path("clientId") clientId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: StatusUpdateRequestDto,
    ): Response<TimelineEntryDto>

    @POST("clients/{clientId}/remarks")
    suspend fun addRemark(
        @Path("clientId") clientId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RemarkRequestDto,
    ): Response<TimelineEntryDto>

    // --- Timeline (§8) ---
    @GET("clients/{clientId}/timeline")
    suspend fun timeline(
        @Path("clientId") clientId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<TimelinePageDto>

    // --- Calls (§5, §7) ---
    @POST("calls")
    suspend fun placeCall(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CallRequestDto,
    ): Response<CallSessionDto>

    @GET("calls/{callSessionId}")
    suspend fun callSession(@Path("callSessionId") callSessionId: String): Response<CallSessionDto>

    @GET("calls/history")
    suspend fun callHistory(
        @Query("clientId") clientId: String? = null,
        @Query("since") since: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<CallRecordPageDto>

    // --- Attendance (§11) ---
    @POST("attendance")
    suspend fun attendance(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: AttendanceRequestDto,
    ): Response<AttendanceRecordDto>

    @PUT("attendance/{attendanceId}/photo")
    suspend fun attendancePhoto(
        @Path("attendanceId") attendanceId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: okhttp3.RequestBody,
    ): Response<Unit>

    @GET("attendance/today")
    suspend fun attendanceToday(): Response<AttendanceTodayDto>

    // --- Location (§10) ---
    @POST("location/pings")
    suspend fun uploadPings(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: LocationPingBatchDto,
    ): Response<LocationPingAckDto>

    @GET("location/policy")
    suspend fun locationPolicy(): Response<LocationPolicyDto>

    // --- Device (§24, §14-§20) ---
    @PUT("device/push-token")
    suspend fun pushToken(@Body body: PushTokenRequestDto): Response<Unit>

    @POST("device/compliance")
    suspend fun compliance(@Body body: ComplianceReportDto): Response<ComplianceVerdictDto>
}
