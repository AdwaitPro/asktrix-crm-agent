package com.asktrix.agent.core.common.time

import java.time.Instant
import java.time.ZoneId

/**
 * All time comes from here, never from `Instant.now()` at a call site.
 *
 * Three reasons this abstraction pays for itself in this app specifically:
 *  1. **The device clock is user-influenced.** Working-hours gating (§10) and attendance (§11) are
 *     compliance-relevant, so the authoritative decision is made server-side; the client clock is a
 *     hint. [serverSkewMillis] records the observed offset so the client can detect tampering.
 *  2. **OSP logging obligations require IST-synchronised timestamps** on call records, so the
 *     reporting timezone is explicit rather than the device default.
 *  3. Deterministic tests.
 */
interface TimeSource {

    /** Wall-clock time, subject to device clock changes. Use for display. */
    fun now(): Instant

    /**
     * Monotonic elapsed time in milliseconds since boot. Never goes backwards and is unaffected by
     * clock changes, so it is the only safe basis for measuring durations and backoff.
     */
    fun elapsedRealtimeMillis(): Long

    /** Observed difference between the server clock and the device clock, if known. */
    val serverSkewMillis: Long

    companion object {
        /**
         * India Standard Time. Call records and attendance are reported in IST because the OSP
         * security conditions require timestamps synchronised with Indian Standard Time.
         */
        val REPORTING_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
