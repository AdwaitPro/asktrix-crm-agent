package com.asktrix.agent.core.network

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.network.dto.ApiErrorDto
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Turns a Retrofit call into an [AsktrixResult], mapping every failure mode to a specific
 * [AsktrixError] so the sync engine can decide retry behaviour from the type alone (§9, §23).
 *
 * Nothing here throws. A network call failing is ordinary, not exceptional, in an app designed to
 * spend much of its life offline.
 */
suspend fun <T : Any> apiCall(
    json: Json,
    block: suspend () -> Response<T>,
): AsktrixResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        when {
            body != null -> AsktrixResult.Success(body)
            // 204 on a Unit-returning endpoint is a legitimate empty success.
            response.code() == HTTP_NO_CONTENT -> @Suppress("UNCHECKED_CAST")
            AsktrixResult.Success(Unit as T)
            else -> AsktrixResult.Failure(
                AsktrixError.MalformedResponse("empty body on ${response.code()}"),
            )
        }
    } else {
        AsktrixResult.Failure(response.toError(json))
    }
} catch (e: SocketTimeoutException) {
    AsktrixResult.Failure(AsktrixError.Timeout(e.message))
} catch (e: UnknownHostException) {
    // DNS failure is indistinguishable from "no network" from the app's point of view.
    AsktrixResult.Failure(AsktrixError.Offline(e.message))
} catch (e: IOException) {
    AsktrixResult.Failure(AsktrixError.Offline(e.message))
}

private const val HTTP_NO_CONTENT = 204
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_UNPROCESSABLE = 422
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500

private fun <T> Response<T>.toError(json: Json): AsktrixError {
    val parsed = runCatching {
        errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(ApiErrorDto.serializer(), it) }
    }.getOrNull()

    // Prefer the server's own error code; fall back to the status when the body is unparseable.
    return parsed?.let(::fromServerCode) ?: fromHttpStatus(code(), parsed)
}

/** Maps the CRM's own error code, which is more specific than the HTTP status. */
private fun fromServerCode(parsed: ApiErrorDto): AsktrixError? =
    when (parsed.code) {
        "UNAUTHENTICATED" -> AsktrixError.Unauthenticated(parsed.message)
        "FORBIDDEN" -> AsktrixError.Forbidden(parsed.message)
        "DEVICE_NOT_BOUND" -> AsktrixError.DeviceNotBound(parsed.message)
        "NOT_FOUND" -> AsktrixError.NotFound(parsed.message)
        "VALIDATION_FAILED" -> AsktrixError.Validation(parsed.fieldErrors, parsed.message)
        "CALL_NOT_PLACED" -> AsktrixError.CallNotPlaced(parsed.message)
        // A conflict carries a real explanation from the server; surface it verbatim
        // rather than flattening it into a validation message.
        "CONFLICT" -> AsktrixError.Conflict(parsed.message)
        "INTEGRITY_FAILURE" -> AsktrixError.IntegrityFailure(
            com.asktrix.agent.core.common.result.IntegrityFailureReason.ATTESTATION_REJECTED,
            parsed.message,
        )
        else -> null
    }

/** Fallback when the error body is missing or carries a code we do not recognise. */
private fun fromHttpStatus(code: Int, parsed: ApiErrorDto?): AsktrixError =
    when (code) {
        HTTP_UNAUTHORIZED -> AsktrixError.Unauthenticated(parsed?.message)
        HTTP_FORBIDDEN -> AsktrixError.Forbidden(parsed?.message)
        HTTP_NOT_FOUND -> AsktrixError.NotFound(parsed?.message)
        HTTP_CONFLICT -> AsktrixError.Conflict(parsed?.message)
        HTTP_UNPROCESSABLE -> AsktrixError.Validation(parsed?.fieldErrors.orEmpty(), parsed?.message)
        // Rate limiting and 5xx are transient: the outbox should back off and retry.
        HTTP_TOO_MANY_REQUESTS -> AsktrixError.ServerUnavailable(code, parsed?.message)
        in HTTP_SERVER_ERROR_MIN..MAX_STATUS -> AsktrixError.ServerUnavailable(code, parsed?.message)
        else -> AsktrixError.Unexpected("HTTP $code")
    }

private const val MAX_STATUS = 599
