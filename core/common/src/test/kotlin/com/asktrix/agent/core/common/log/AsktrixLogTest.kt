package com.asktrix.agent.core.common.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Privacy regression tests for the redaction helper.
 *
 * §4 requires that employees never see full customer contact details, and invariant 4 (CLAUDE.md)
 * requires that nothing sensitive reaches logcat. These assertions are the guard: if someone widens
 * [AsktrixLog.redact] so that it leaks more of a value, these fail.
 */
class AsktrixLogTest {

    @Test
    fun `redact keeps only the last two characters`() {
        assertEquals("…12", AsktrixLog.redact("9876543212"))
    }

    @Test
    fun `redact never echoes a value short enough to be guessed`() {
        assertEquals("[redacted]", AsktrixLog.redact("1234"))
        assertEquals("[redacted]", AsktrixLog.redact("abc"))
        assertEquals("[redacted]", AsktrixLog.redact(""))
    }

    @Test
    fun `redact handles null without leaking a literal null`() {
        assertEquals("[redacted]", AsktrixLog.redact(null))
    }

    @Test
    fun `a redacted phone number does not contain the subscriber digits`() {
        val phone = "9876543212"

        val redacted = AsktrixLog.redact(phone)

        assertFalse("full number leaked", redacted.contains(phone))
        assertFalse("leaked the operator prefix", redacted.contains("98765"))
        // "…12": one ellipsis character plus the two retained digits.
        assertEquals(3, redacted.length)
    }

    @Test
    fun `a redacted email does not contain the local part or domain`() {
        val email = "sivakumar@gmail.com"

        val redacted = AsktrixLog.redact(email)

        assertFalse("local part leaked", redacted.contains("sivakumar"))
        assertFalse("domain leaked", redacted.contains("gmail"))
    }

    @Test
    fun `redact trims surrounding whitespace before redacting`() {
        assertEquals("…12", AsktrixLog.redact("  9876543212  "))
    }

    @Test
    fun `tagOf produces a non-empty tag within logcat's length limit`() {
        val tag = AsktrixLog.tagOf(this)

        assertEquals("AsktrixLogTest", tag)
        assertFalse(tag.isEmpty())
        assert(tag.length <= 23)
    }
}
