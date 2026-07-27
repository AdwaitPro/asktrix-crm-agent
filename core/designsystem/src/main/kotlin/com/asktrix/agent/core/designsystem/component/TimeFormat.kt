package com.asktrix.agent.core.designsystem.component

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Human-readable relative time.
 *
 * Field agents scan these while walking; "Overdue 2d" is read faster and more accurately than a
 * timestamp. Anything beyond a week falls back to an absolute date, because "in 43 days" stops being
 * meaningful.
 *
 * Rendered in IST, matching the timezone the CRM and the call records use.
 */
fun Instant.relativeLabel(now: Instant = Instant.now()): String {
    val duration = Duration.between(now, this)
    val minutes = duration.toMinutes()
    val overdue = minutes < 0
    val absMinutes = kotlin.math.abs(minutes)

    return when {
        absMinutes < MINUTES_PER_HOUR ->
            if (overdue) "Overdue ${absMinutes}m" else "In ${absMinutes}m"

        absMinutes < MINUTES_PER_DAY -> {
            val hours = absMinutes / MINUTES_PER_HOUR
            if (overdue) "Overdue ${hours}h" else "In ${hours}h"
        }

        absMinutes < MINUTES_PER_WEEK -> {
            val days = absMinutes / MINUTES_PER_DAY
            if (overdue) "Overdue ${days}d" else "In ${days}d"
        }

        else -> DATE_FORMAT.format(atZone(REPORTING_ZONE))
    }
}

/** Absolute date and time in IST, for timeline entries and call records. */
fun Instant.absoluteLabel(): String = DATE_TIME_FORMAT.format(atZone(REPORTING_ZONE))

private val REPORTING_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 1440L
private const val MINUTES_PER_WEEK = 10_080L
