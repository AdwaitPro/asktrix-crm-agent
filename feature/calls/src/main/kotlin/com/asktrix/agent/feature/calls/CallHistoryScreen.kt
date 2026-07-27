package com.asktrix.agent.feature.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asktrix.agent.core.data.model.CallDirection
import com.asktrix.agent.core.data.model.CallRecord
import com.asktrix.agent.core.data.model.CallState
import com.asktrix.agent.core.designsystem.component.AsktrixEmptyState
import com.asktrix.agent.core.designsystem.component.absoluteLabel
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import java.time.Instant

@Composable
fun CallHistoryRoute(viewModel: CallHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CallHistoryScreen(state)
}

@Composable
fun CallHistoryScreen(state: CallHistoryUiState) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "Calls",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = AsktrixTheme.spacing.screenEdge,
                    vertical = AsktrixTheme.spacing.lg,
                ),
            )

            when {
                state.isLoading && state.records.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.records.isEmpty() ->
                    AsktrixEmptyState(
                        icon = Icons.Outlined.Phone,
                        title = "No calls yet",
                        body = "Calls you place through the CRM appear here with their outcome " +
                            "and duration.",
                    )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = AsktrixTheme.spacing.screenEdge,
                        vertical = AsktrixTheme.spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.sm),
                ) {
                    items(state.records, key = { it.callRecordId }) { CallRow(it) }
                }
            }
        }
    }
}

@Composable
private fun CallRow(record: CallRecord) {
    val connected = record.state == CallState.COMPLETED
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(AsktrixTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (connected) {
                            AsktrixTheme.statusColors.positive.copy(alpha = 0.14f)
                        } else {
                            AsktrixTheme.statusColors.warning.copy(alpha = 0.14f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (record.direction) {
                        CallDirection.MISSED -> Icons.Outlined.CallMissed
                        else -> Icons.AutoMirrored.Outlined.CallMade
                    },
                    contentDescription = null,
                    tint = if (connected) {
                        AsktrixTheme.statusColors.positive
                    } else {
                        AsktrixTheme.statusColors.warning
                    },
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(AsktrixTheme.spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = record.clientName ?: record.clientId,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(record.state.label)
                        if (record.durationSeconds > 0) {
                            append(" · ")
                            append(record.durationSeconds / SECONDS_PER_MINUTE)
                            append("m ")
                            append(record.durationSeconds % SECONDS_PER_MINUTE)
                            append("s")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.startedAt.absoluteLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.recordingAvailable) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FiberManualRecord,
                            contentDescription = null,
                            tint = AsktrixTheme.statusColors.negative,
                            modifier = Modifier.size(8.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        // The device is told a recording exists; it never receives the audio (§6).
                        Text(
                            text = "Recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

@Preview(showBackground = true)
@Composable
private fun CallHistoryPreview() {
    AsktrixTheme {
        CallHistoryScreen(
            CallHistoryUiState(
                isLoading = false,
                records = listOf(
                    CallRecord("c1", "CLI-10240", "Sivakumar Ramanathan", CallDirection.OUTBOUND,
                        CallState.COMPLETED, Instant.now(), 119, true),
                    CallRecord("c2", "CLI-10244", "Mohammed Irfan", CallDirection.OUTBOUND,
                        CallState.NO_ANSWER, Instant.now(), 0, false),
                ),
            ),
        )
    }
}
