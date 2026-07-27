package com.asktrix.agent.feature.auth

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.common.result.map
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.common.session.SessionTokens
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.apiCall
import com.asktrix.agent.core.network.dto.DeviceBindingDto
import com.asktrix.agent.core.network.dto.EmployeeDto
import com.asktrix.agent.core.network.dto.LoginRequestDto
import com.asktrix.agent.core.security.DeviceIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Sign-in, sign-out, and session restore.
 *
 * Sign-out clears tokens **and** the device identity, because the requirement is that no customer
 * data survives on the device after logout (§3). A subsequent sign-in is a fresh device binding.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AsktrixApi,
    private val tokens: SessionTokenStore,
    private val deviceIdentity: DeviceIdentity,
    private val json: Json,
    private val appVersion: AppVersion,
    private val pushRegistration: PushRegistration,
) {

    suspend fun login(employeeCode: String, password: String): AsktrixResult<EmployeeDto> {
        val deviceId = deviceIdentity.deviceId()
        val request = LoginRequestDto(
            employeeCode = employeeCode.trim(),
            password = password,
            device = DeviceBindingDto(
                deviceId = deviceId,
                manufacturer = deviceIdentity.manufacturer,
                model = deviceIdentity.model,
                osVersion = deviceIdentity.osVersion,
                appVersion = appVersion.name,
            ),
        )

        return apiCall(json) { api.login(request) }
            .also { result ->
                if (result is AsktrixResult.Success) {
                    tokens.save(
                        SessionTokens(
                            accessToken = result.data.accessToken,
                            refreshToken = result.data.refreshToken,
                            deviceId = deviceId,
                        ),
                    )
                    // Best-effort and deliberately after the token is stored, since the call itself
                    // needs authentication. A failure here never fails sign-in.
                    pushRegistration.register()
                }
            }
            .map { it.employee }
    }

    /** Best-effort server-side revoke, then unconditional local teardown. */
    suspend fun logout() {
        runCatching { api.logout() }
        tokens.clear()
        deviceIdentity.clear()
    }

    suspend fun hasSession(): Boolean = tokens.current() != null

    suspend fun currentEmployee(): AsktrixResult<EmployeeDto> = apiCall(json) { api.session() }
}

/** Supplied by the app module so feature code never reaches into `BuildConfig` directly. */
data class AppVersion(val name: String)

/**
 * Registers this device for push after sign-in.
 *
 * An interface so `:feature:auth` never depends on Firebase — the app module supplies the real
 * implementation, and tests supply a no-op.
 */
fun interface PushRegistration {
    suspend fun register(): Boolean
}

/**
 * Maps an error onto copy a field agent can act on. Never surfaces technical detail.
 *
 * Split by category so neither half becomes an unreadable wall of branches.
 */
fun AsktrixError.toUserMessage(): String =
    connectivityMessage() ?: accessMessage() ?: "Something went wrong. Try again."

private fun AsktrixError.connectivityMessage(): String? = when (this) {
    is AsktrixError.Offline -> "No internet connection. Check your network and try again."
    is AsktrixError.Timeout -> "The server took too long to respond. Try again."
    is AsktrixError.ServerUnavailable -> "The CRM is unavailable right now. Try again shortly."
    else -> null
}

private fun AsktrixError.accessMessage(): String? = when (this) {
    is AsktrixError.Unauthenticated -> "Incorrect employee code or password."
    is AsktrixError.Forbidden -> "Your account does not have access to this."
    is AsktrixError.DeviceNotBound -> "This device is not registered. Contact your administrator."
    is AsktrixError.Validation ->
        fieldErrors.values.firstOrNull() ?: "Please check the details you entered."
    is AsktrixError.NotFound -> "That record is no longer available."
    is AsktrixError.IntegrityFailure ->
        "This device failed a security check. Contact your administrator."
    is AsktrixError.PermissionDenied -> "A required permission is not granted."
    is AsktrixError.StorageFailure -> "Secure storage is unavailable on this device."
    is AsktrixError.CallNotPlaced -> providerReason ?: "The call could not be placed."
    is AsktrixError.MalformedResponse -> "The server sent an unexpected response."
    else -> null
}
