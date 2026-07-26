package com.asktrix.agent.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 4dp-based spacing scale. Using named steps rather than raw dp values is what keeps rhythm
 * consistent across six feature modules written at different times.
 */
data class AsktrixSpacing(
    val hairline: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Standard screen edge inset. */
    val screenEdge: Dp = 20.dp,
    /** Minimum interactive target. Material's floor, and an accessibility requirement. */
    val minTouchTarget: Dp = 48.dp,
)

val LocalAsktrixSpacing = staticCompositionLocalOf { AsktrixSpacing() }
