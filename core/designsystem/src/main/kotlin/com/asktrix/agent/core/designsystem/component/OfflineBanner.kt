package com.asktrix.agent.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme

/**
 * Shown when the device has no validated internet connection (§9).
 *
 * Worded as reassurance rather than as an error, because being offline is the expected condition for
 * a field agent, and their work is genuinely safe — it is queued in the outbox and will sync. An
 * alarming message here would train people to distrust a system that is working correctly.
 */
@Composable
fun OfflineBanner(
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AsktrixTheme.statusColors.offline.copy(alpha = 0.14f))
            .padding(AsktrixTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsktrixTheme.spacing.sm))
        Text(
            text = if (pendingCount > 0) {
                "Offline · $pendingCount change${if (pendingCount == 1) "" else "s"} will sync automatically"
            } else {
                "Offline · showing saved data"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
