package com.asktrix.agent.core.network

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Error mapping decides retry behaviour for the whole offline engine (§9, §23).
 *
 * If a 503 were classified permanent, an employee's work would be dropped after a brief CRM outage.
 * If a 403 were classified retryable, the device would hammer the server forever over a request it
 * is never allowed to make. These assertions pin the boundary.
 */
class ApiCallTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    private fun errorBody(body: String) = body.toResponseBody(jsonMedia)

    @Test
    fun `successful response returns the body`() = runTest {
        val result = apiCall(json) { Response.success("ok") }

        assertEquals(AsktrixResult.Success("ok"), result)
    }

    @Test
    fun `timeout maps to a retryable Timeout`() = runTest {
        val result = apiCall<String>(json) { throw SocketTimeoutException("timed out") }

        val error = (result as AsktrixResult.Failure).error
        assertTrue("expected Timeout, got $error", error is AsktrixError.Timeout)
        assertTrue("Timeout must be retryable", error is AsktrixError.Retryable)
    }

    @Test
    fun `no network maps to a retryable Offline`() = runTest {
        val result = apiCall<String>(json) { throw java.net.UnknownHostException("no dns") }

        val error = (result as AsktrixResult.Failure).error
        assertTrue("expected Offline, got $error", error is AsktrixError.Offline)
        assertTrue("Offline must be retryable so the outbox holds the work", error is AsktrixError.Retryable)
    }

    @Test
    fun `server error is retryable so a brief CRM outage does not drop work`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(503, errorBody("""{"code":"SERVER_ERROR","message":"down"}"""))
        }

        val error = (result as AsktrixResult.Failure).error
        assertTrue("expected ServerUnavailable, got $error", error is AsktrixError.ServerUnavailable)
        assertTrue(error is AsktrixError.Retryable)
    }

    @Test
    fun `rate limiting is retryable, not a permanent failure`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(429, errorBody("""{"code":"RATE_LIMITED","message":"slow down"}"""))
        }

        assertTrue((result as AsktrixResult.Failure).error is AsktrixError.Retryable)
    }

    @Test
    fun `forbidden is permanent so the device does not retry a request it may never make`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(403, errorBody("""{"code":"FORBIDDEN","message":"not yours"}"""))
        }

        val error = (result as AsktrixResult.Failure).error
        assertTrue("expected Forbidden, got $error", error is AsktrixError.Forbidden)
        assertTrue("Forbidden must be permanent", error is AsktrixError.Permanent)
    }

    @Test
    fun `unauthenticated is permanent — refresh is the Authenticator's job, not the outbox's`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(401, errorBody("""{"code":"UNAUTHENTICATED","message":"expired"}"""))
        }

        val error = (result as AsktrixResult.Failure).error
        assertTrue(error is AsktrixError.Unauthenticated)
        assertTrue(error is AsktrixError.Permanent)
    }

    @Test
    fun `validation failure carries the field errors through to the UI`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(
                422,
                errorBody(
                    """{"code":"VALIDATION_FAILED","message":"bad","fieldErrors":{"status":"unknown value"}}""",
                ),
            )
        }

        val error = (result as AsktrixResult.Failure).error as AsktrixError.Validation
        assertEquals("unknown value", error.fieldErrors["status"])
    }

    @Test
    fun `an unparseable error body still maps by HTTP status rather than crashing`() = runTest {
        val result = apiCall<String>(json) { Response.error(500, errorBody("<html>gateway</html>")) }

        val error = (result as AsktrixResult.Failure).error
        assertTrue("expected ServerUnavailable, got $error", error is AsktrixError.ServerUnavailable)
    }

    @Test
    fun `an unrecognised server code falls back to the HTTP status`() = runTest {
        val result = apiCall<String>(json) {
            Response.error(403, errorBody("""{"code":"SOMETHING_NEW","message":"future code"}"""))
        }

        assertTrue((result as AsktrixResult.Failure).error is AsktrixError.Forbidden)
    }
}
