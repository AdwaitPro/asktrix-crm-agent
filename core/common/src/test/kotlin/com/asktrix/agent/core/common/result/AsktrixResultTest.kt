package com.asktrix.agent.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsktrixResultTest {

    @Test
    fun `map transforms a success value`() {
        val result: AsktrixResult<Int> = 21.asSuccess()

        val mapped = result.map { it * 2 }

        assertEquals(AsktrixResult.Success(42), mapped)
    }

    @Test
    fun `map leaves a failure untouched and does not invoke the transform`() {
        var invoked = false
        val failure: AsktrixResult<Int> = AsktrixError.Offline().asFailure()

        val mapped = failure.map { invoked = true; it * 2 }

        assertFalse("transform must not run on a failure", invoked)
        assertEquals(failure, mapped)
    }

    @Test
    fun `flatMap chains successes and short-circuits on the first failure`() {
        val offline = AsktrixError.Offline("no network")

        val chained = 1.asSuccess()
            .flatMap { (it + 1).asSuccess() }
            .flatMap { offline.asFailure() }
            .flatMap<Int, Int> { error("must not be reached after a failure") }

        assertEquals(AsktrixResult.Failure(offline), chained)
    }

    @Test
    fun `getOrNull and getOrDefault behave per branch`() {
        assertEquals("v", "v".asSuccess().getOrNull())
        assertNull(AsktrixError.Timeout().asFailure().getOrNull())
        assertEquals("fallback", AsktrixError.Timeout().asFailure().getOrDefault("fallback"))
    }

    @Test
    fun `onSuccess and onFailure fire only on their own branch`() {
        var successes = 0
        var failures = 0

        "ok".asSuccess().onSuccess { successes++ }.onFailure { failures++ }
        AsktrixError.NotFound().asFailure().onSuccess { successes++ }.onFailure { failures++ }

        assertEquals(1, successes)
        assertEquals(1, failures)
    }

    @Test
    fun `isSuccess reflects the branch`() {
        assertTrue(Unit.asSuccess().isSuccess)
        assertFalse(AsktrixError.Unexpected().asFailure().isSuccess)
    }

    /**
     * The sync engine (§9, §23) decides retry behaviour purely from this classification, so it must
     * hold for every error type. A miscategorised error either loses an employee's work or retries a
     * permanently-rejected request forever.
     */
    @Test
    fun `errors are classified as retryable or permanent, never both, never neither`() {
        val allErrors: List<AsktrixError> = listOf(
            AsktrixError.Offline(),
            AsktrixError.Timeout(),
            AsktrixError.ServerUnavailable(statusCode = 503),
            AsktrixError.Unauthenticated(),
            AsktrixError.Forbidden(),
            AsktrixError.DeviceNotBound(),
            AsktrixError.MalformedResponse(),
            AsktrixError.Validation(fieldErrors = mapOf("status" to "unknown value")),
            AsktrixError.NotFound(),
            AsktrixError.StorageFailure(),
            AsktrixError.IntegrityFailure(IntegrityFailureReason.ROOTED),
            AsktrixError.PermissionDenied("android.permission.CAMERA", permanentlyDenied = false),
            AsktrixError.CallNotPlaced(providerReason = "busy"),
            AsktrixError.Unexpected(),
        )

        allErrors.forEach { error ->
            val retryable = error is AsktrixError.Retryable
            val permanent = error is AsktrixError.Permanent
            assertTrue(
                "$error is neither Retryable nor Permanent - the sync engine cannot classify it",
                retryable || permanent,
            )
            assertFalse(
                "$error is both Retryable and Permanent - retry behaviour would be ambiguous",
                retryable && permanent,
            )
        }
    }

    @Test
    fun `connectivity and server failures are retryable, contract failures are not`() {
        assertTrue(AsktrixError.Offline() is AsktrixError.Retryable)
        assertTrue(AsktrixError.Timeout() is AsktrixError.Retryable)
        assertTrue(AsktrixError.ServerUnavailable(500) is AsktrixError.Retryable)

        assertTrue(AsktrixError.MalformedResponse() is AsktrixError.Permanent)
        assertTrue(AsktrixError.Forbidden() is AsktrixError.Permanent)
        assertTrue(AsktrixError.IntegrityFailure(IntegrityFailureReason.EMULATOR) is AsktrixError.Permanent)
    }
}
