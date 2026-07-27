package com.asktrix.agent.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * A 1.200 (minor-third) type scale with tuned line heights and tracking.
 *
 * Uses the platform default family deliberately: a bundled font would add APK weight for a
 * self-hosted enterprise app whose users never compare it to anything, and the platform family has
 * the best Devanagari coverage on Indian devices for free. Weight and spacing carry the hierarchy
 * instead.
 *
 * `LineHeightStyle` with `Trim.None` keeps multi-line list items optically even - the default trims
 * the first line's ascent and makes dense lists look misaligned.
 */
private val Default = FontFamily.Default

private val EvenLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = EvenLineHeight,
)

val AsktrixTypography = Typography(
    displaySmall = style(36, 44, FontWeight.SemiBold, -0.6),

    headlineLarge = style(30, 38, FontWeight.SemiBold, -0.4),
    headlineMedium = style(25, 32, FontWeight.SemiBold, -0.3),
    headlineSmall = style(21, 28, FontWeight.SemiBold, -0.2),

    titleLarge = style(18, 26, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 24, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),

    bodyLarge = style(16, 25, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Normal, 0.1),

    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.3),
    // Used for masked-field captions and status chips: small, but tracked out to stay legible.
    labelSmall = style(11, 15, FontWeight.SemiBold, 0.5),
)
