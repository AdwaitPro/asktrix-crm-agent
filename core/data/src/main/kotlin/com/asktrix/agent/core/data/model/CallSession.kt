package com.asktrix.agent.core.data.model

import java.time.Instant

/**
 * A click-to-call session (§5).
 *
 * Carries no phone number for either leg, because none is ever sent to the device. The employee sees
 * the client's name and the call's progress; the telephony provider owns the audio path entirely
 * (ADR-0002).
 */
data class CallSession(
    val callSessionId: String,
    val clientId: String,
    val state: CallState,
    val requestedAt: Instant,
    val connectedAt: Instant? = null,
    val endedAt: Instant? = null,
    val durationSeconds: Int? = null,
    val failureReason: String? = null,
)

enum class CallState(val label: String) {
    REQUESTED("Connecting…"),
    RINGING_AGENT("Calling your phone…"),
    RINGING_CUSTOMER("Ringing client…"),
    BRIDGED("Connected"),
    COMPLETED("Call ended"),
    BUSY("Line busy"),
    NO_ANSWER("No answer"),
    FAILED("Call failed"),
    CANCELLED("Cancelled"),
    ;

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, BUSY, NO_ANSWER, FAILED, CANCELLED)

    val isActive: Boolean get() = !isTerminal

    companion object {
        fun from(raw: String): CallState = entries.firstOrNull { it.name == raw } ?: FAILED
    }
}

data class CallRecord(
    val callRecordId: String,
    val clientId: String,
    val clientName: String?,
    val direction: CallDirection,
    val state: CallState,
    val startedAt: Instant,
    val durationSeconds: Int,
    /** Whether the CRM holds a recording. The device never receives the audio or a link to it (§6). */
    val recordingAvailable: Boolean,
)

enum class CallDirection { OUTBOUND, INBOUND, MISSED;
    companion object {
        fun from(raw: String): CallDirection = entries.firstOrNull { it.name == raw } ?: OUTBOUND
    }
}
