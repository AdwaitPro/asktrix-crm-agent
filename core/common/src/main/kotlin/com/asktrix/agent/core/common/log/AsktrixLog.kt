@file:Suppress("ForbiddenImport")

package com.asktrix.agent.core.common.log

import android.util.Log

/**
 * The only sanctioned logging entry point.
 *
 * Invariant 4 (CLAUDE.md): nothing sensitive is ever written to logcat. Two mechanisms enforce it:
 *  1. Detekt's `ForbiddenImport` rule fails the build on any other `android.util.Log` import — this
 *     file is the single suppressed exception.
 *  2. R8 strips every `android.util.Log` call in release via `-assumenosideeffects`, so release
 *     builds cannot emit logs at all, whatever a caller passes.
 *
 * [redact] is provided for the cases where a value must appear in a diagnostic at all: it keeps only
 * enough of the value to correlate, never enough to identify. Anything resembling a phone number or
 * an email address must go through it.
 */
object AsktrixLog {

    private const val MAX_TAG_LENGTH = 23
    private const val REDACTED = "[redacted]"
    private const val VISIBLE_SUFFIX = 2

    fun v(tag: String, message: String) = Log.v(tag.trim(), message)

    fun d(tag: String, message: String) = Log.d(tag.trim(), message)

    fun i(tag: String, message: String) = Log.i(tag.trim(), message)

    fun w(tag: String, message: String, cause: Throwable? = null) =
        Log.w(tag.trim(), message, cause)

    fun e(tag: String, message: String, cause: Throwable? = null) =
        Log.e(tag.trim(), message, cause)

    /**
     * Reduces a value to a correlation-only form: `"98XXXXXX12"` becomes `"…12"`.
     *
     * Returns [REDACTED] for anything too short to redact safely, so a 3-character secret is not
     * echoed verbatim.
     */
    fun redact(value: String?): String {
        if (value == null) return REDACTED
        val trimmed = value.trim()
        if (trimmed.length <= VISIBLE_SUFFIX * 2) return REDACTED
        return "…" + trimmed.takeLast(VISIBLE_SUFFIX)
    }

    /** Trims a tag to logcat's historical 23-character limit so tags stay greppable. */
    fun tagOf(source: Any): String =
        source::class.simpleName.orEmpty().take(MAX_TAG_LENGTH).ifEmpty { "Asktrix" }
}
