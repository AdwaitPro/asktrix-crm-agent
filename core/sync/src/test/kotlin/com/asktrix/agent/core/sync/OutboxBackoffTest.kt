package com.asktrix.agent.core.sync

import com.asktrix.agent.core.common.time.TimeSource
import com.asktrix.agent.core.database.dao.OutboxDao
import io.mockk.mockk
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backoff is where an offline-first app quietly goes wrong: too aggressive and a fleet of devices
 * stampedes the CRM the moment a network returns; too slow and an employee's work sits unsent for
 * hours. These assertions pin both ends.
 */
class OutboxBackoffTest {

    private val time = object : TimeSource {
        override fun now(): Instant = Instant.parse("2026-07-27T04:00:00Z")
        override fun elapsedRealtimeMillis(): Long = 0
        override val serverSkewMillis: Long = 0
    }

    private val outbox = Outbox(mockk<OutboxDao>(relaxed = true), time)

    @Test
    fun `backoff grows with each attempt`() {
        // Sampled because each value is jittered; the medians must still increase.
        val first = (1..200).map { outbox.backoffMillis(1) }.average()
        val third = (1..200).map { outbox.backoffMillis(3) }.average()
        val sixth = (1..200).map { outbox.backoffMillis(6) }.average()

        assertTrue("attempt 3 must back off further than attempt 1", third > first)
        assertTrue("attempt 6 must back off further than attempt 3", sixth > third)
    }

    @Test
    fun `backoff is capped so a long outage does not schedule a retry days away`() {
        val maxObserved = (1..500).maxOf { outbox.backoffMillis(40) }

        assertTrue(
            "backoff must stay at or below 30 minutes, saw ${maxObserved}ms",
            maxObserved <= 30 * 60 * 1000L,
        )
    }

    @Test
    fun `backoff is jittered so devices that reconnect together do not retry in lockstep`() {
        val samples = (1..300).map { outbox.backoffMillis(5) }.toSet()

        assertTrue(
            "expected a spread of delays, got ${samples.size} distinct values",
            samples.size > 50,
        )
    }

    @Test
    fun `backoff is never zero or negative`() {
        (1..20).forEach { attempt ->
            repeat(50) {
                val delay = outbox.backoffMillis(attempt)
                assertTrue("attempt $attempt produced $delay", delay > 0)
            }
        }
    }

    @Test
    fun `retry ceiling is bounded so a permanently broken item cannot retry forever`() {
        assertEquals(12, Outbox.MAX_ATTEMPTS)
    }
}
