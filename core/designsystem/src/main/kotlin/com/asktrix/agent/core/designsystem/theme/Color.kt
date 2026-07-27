package com.asktrix.agent.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A deliberately narrow palette. This app is used all day, in bright Indian daylight and in dim
 * offices, by people who need to read a status at a glance - so contrast and semantic clarity matter
 * far more than decoration.
 *
 * Every foreground/background pair below meets WCAG AA (4.5:1) for body text.
 */

// --- Brand: a single cool blue accent, used sparingly for primary actions only. ---
internal val Sky300 = Color(0xFF7DD3FC)
internal val Sky400 = Color(0xFF38BDF8)
internal val Sky600 = Color(0xFF0284C7)
internal val Sky700 = Color(0xFF0369A1)
internal val Sky900 = Color(0xFF0C4A6E)

// --- Neutrals: slate, warmer and less clinical than pure grey. ---
internal val Slate50 = Color(0xFFF8FAFC)
internal val Slate100 = Color(0xFFF1F5F9)
internal val Slate200 = Color(0xFFE2E8F0)
internal val Slate400 = Color(0xFF94A3B8)
internal val Slate500 = Color(0xFF64748B)
internal val Slate700 = Color(0xFF334155)
internal val Slate800 = Color(0xFF1E293B)
internal val Slate900 = Color(0xFF0F172A)
internal val Slate950 = Color(0xFF0B1220)

// --- Semantic status colours, mapped to the §13 quick-status actions. ---
internal val Emerald500 = Color(0xFF10B981)
internal val Emerald700 = Color(0xFF047857)
internal val Amber500 = Color(0xFFF59E0B)
internal val Amber700 = Color(0xFFB45309)
internal val Rose500 = Color(0xFFF43F5E)
internal val Rose700 = Color(0xFFBE123C)
internal val Violet500 = Color(0xFF8B5CF6)
internal val Violet700 = Color(0xFF6D28D9)

/**
 * Status colours carried outside the Material scheme, because Material 3 has no slot for
 * "waiting on a government approval". Exposed through [AsktrixStatusColors] on the theme.
 */
data class AsktrixStatusColors(
    val positive: Color,
    val onPositive: Color,
    val warning: Color,
    val onWarning: Color,
    val negative: Color,
    val onNegative: Color,
    val pending: Color,
    val onPending: Color,
    /** Shown whenever the device is working from the local cache rather than live CRM data (§9). */
    val offline: Color,
    val onOffline: Color,
)

internal val LightStatusColors = AsktrixStatusColors(
    positive = Emerald700,
    onPositive = Slate50,
    warning = Amber700,
    onWarning = Slate50,
    negative = Rose700,
    onNegative = Slate50,
    pending = Violet700,
    onPending = Slate50,
    offline = Slate500,
    onOffline = Slate50,
)

internal val DarkStatusColors = AsktrixStatusColors(
    positive = Emerald500,
    onPositive = Slate950,
    warning = Amber500,
    onWarning = Slate950,
    negative = Rose500,
    onNegative = Slate950,
    pending = Violet500,
    onPending = Slate950,
    offline = Slate400,
    onOffline = Slate950,
)

internal val White = Color(0xFFFFFFFF)

/**
 * Status colours are provided through a CompositionLocal rather than added to Material's scheme,
 * because Material 3 has no slot for states like "waiting on a government approval" (§13).
 */
val LocalAsktrixStatusColors = staticCompositionLocalOf { LightStatusColors }
