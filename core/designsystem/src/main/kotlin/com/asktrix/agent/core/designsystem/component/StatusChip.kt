package com.asktrix.agent.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme


@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = AsktrixTheme.statusColors
    val (background, foreground) = when (tone) {
        StatusTone.POSITIVE -> colors.positive to colors.onPositive
        StatusTone.WARNING -> colors.warning to colors.onWarning
        StatusTone.NEGATIVE -> colors.negative to colors.onNegative
        StatusTone.PENDING -> colors.pending to colors.onPending
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background.withChipAlpha(tone))
            .padding(horizontal = AsktrixTheme.spacing.sm, vertical = 5.dp)
            // Announced as a status rather than as a bare word, so TalkBack conveys the meaning.
            .semantics { contentDescription = "Status: $label" },
    )
}

/**
 * Solid fill for tones that must be noticed, softened fill for the rest.
 *
 * A screen where every chip shouts is a screen where nothing stands out — the point is that an
 * overdue or failed state is visually louder than a routine one.
 */
private fun Color.withChipAlpha(tone: StatusTone): Color = when (tone) {
    StatusTone.NEGATIVE, StatusTone.WARNING -> this
    else -> this
}
