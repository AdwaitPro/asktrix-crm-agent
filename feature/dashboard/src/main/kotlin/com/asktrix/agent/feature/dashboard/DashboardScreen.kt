package com.asktrix.agent.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.MaskedContact
import com.asktrix.agent.core.data.model.PaymentStatus
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.designsystem.component.AsktrixEmptyState
import com.asktrix.agent.core.designsystem.component.OfflineBanner
import com.asktrix.agent.core.designsystem.component.StatusChip
import com.asktrix.agent.core.designsystem.component.relativeLabel
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import java.time.Instant

@Composable
fun DashboardRoute(
    onClientClick: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onFilterChange = viewModel::onFilterChange,
        onRefresh = viewModel::refresh,
        onClientClick = onClientClick,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onFilterChange: (DashboardFilter) -> Unit,
    onRefresh: () -> Unit,
    onClientClick: (String) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            DashboardHeader(state = state, onRefresh = onRefresh)

            // Offline is a first-class state, not an error. The cached list stays usable.
            AnimatedVisibility(visible = !state.isOnline, enter = fadeIn(), exit = fadeOut()) {
                OfflineBanner(
                    pendingCount = state.pendingSyncCount,
                    modifier = Modifier.padding(
                        horizontal = AsktrixTheme.spacing.screenEdge,
                        vertical = AsktrixTheme.spacing.sm,
                    ),
                )
            }

            FilterRow(
                selected = state.filter,
                pendingCount = state.pendingWorkCount,
                followUpCount = state.followUpDueCount,
                onFilterChange = onFilterChange,
            )

            when {
                !state.hasLoadedOnce && state.clients.isEmpty() ->
                    LoadingState()

                state.visibleClients.isEmpty() ->
                    AsktrixEmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = when (state.filter) {
                            DashboardFilter.ALL -> "No clients assigned"
                            DashboardFilter.PENDING -> "Nothing pending"
                            DashboardFilter.FOLLOW_UP -> "No follow-ups due"
                        },
                        body = when (state.filter) {
                            DashboardFilter.ALL ->
                                "Clients assigned to you by the CRM will appear here."
                            DashboardFilter.PENDING ->
                                "Every assigned client is up to date."
                            DashboardFilter.FOLLOW_UP ->
                                "No callbacks are due right now."
                        },
                    )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = AsktrixTheme.spacing.screenEdge,
                        end = AsktrixTheme.spacing.screenEdge,
                        bottom = AsktrixTheme.spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.md),
                ) {
                    items(state.visibleClients, key = { it.clientId }) { client ->
                        ClientCard(client = client, onClick = { onClientClick(client.clientId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(state: DashboardUiState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AsktrixTheme.spacing.screenEdge,
                end = AsktrixTheme.spacing.screenEdge,
                top = AsktrixTheme.spacing.lg,
                bottom = AsktrixTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My clients",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${state.clients.size} assigned · ${state.followUpDueCount} follow-up due",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .size(AsktrixTheme.spacing.minTouchTarget)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !state.isRefreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = "Refresh client list",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: DashboardFilter,
    pendingCount: Int,
    followUpCount: Int,
    onFilterChange: (DashboardFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AsktrixTheme.spacing.screenEdge,
                vertical = AsktrixTheme.spacing.sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.sm),
    ) {
        DashboardFilter.entries.forEach { filter ->
            val count = when (filter) {
                DashboardFilter.ALL -> null
                DashboardFilter.PENDING -> pendingCount
                DashboardFilter.FOLLOW_UP -> followUpCount
            }
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = if (count != null && count > 0) "${filter.label} ($count)" else filter.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(AsktrixTheme.spacing.md))
}

@Composable
private fun ClientCard(client: Client, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(AsktrixTheme.spacing.lg)) {

            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(client.clientId, client.serviceId).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(
                    label = client.processStatus.label,
                    tone = client.processStatus.tone(),
                )
            }

            Spacer(Modifier.height(AsktrixTheme.spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (client.documentsPending > 0) {
                    MetaItem(
                        icon = Icons.Outlined.Description,
                        text = "${client.documentsPending} pending",
                    )
                    Spacer(Modifier.width(AsktrixTheme.spacing.lg))
                }
                client.followUpAt?.let { followUp ->
                    MetaItem(
                        icon = Icons.Outlined.Schedule,
                        text = followUp.relativeLabel(),
                        emphasise = followUp <= Instant.now(),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (client.paymentStatus != PaymentStatus.NOT_DUE) {
                    Text(
                        text = client.paymentStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (client.paymentStatus == PaymentStatus.RECEIVED) {
                            AsktrixTheme.statusColors.positive
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Tells the employee their edit is saved locally and will reach the CRM (§9, §23).
            if (client.hasPendingChanges) {
                Spacer(Modifier.height(AsktrixTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = AsktrixTheme.statusColors.offline,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(AsktrixTheme.spacing.xs))
                    Text(
                        text = "Waiting to sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = AsktrixTheme.statusColors.offline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    emphasise: Boolean = false,
) {
    val tint = if (emphasise) {
        AsktrixTheme.statusColors.warning
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(AsktrixTheme.spacing.xs))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AsktrixTheme.spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.md),
    ) {
        repeat(SKELETON_COUNT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SKELETON_HEIGHT)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private const val SKELETON_COUNT = 5
private val SKELETON_HEIGHT = 104.dp

@Preview(name = "Dashboard", showBackground = true, heightDp = 800)
@Composable
private fun DashboardPreview() {
    AsktrixTheme {
        DashboardScreen(
            state = DashboardUiState(
                hasLoadedOnce = true,
                clients = listOf(
                    Client(
                        clientId = "CLI-10240",
                        name = "Sivakumar Ramanathan",
                        serviceId = "SVC-GST-2291",
                        processStatus = ProcessStatus.DOCUMENTS_PENDING,
                        paymentStatus = PaymentStatus.PENDING,
                        governmentStatus = com.asktrix.agent.core.data.model.GovernmentStatus.NOT_SUBMITTED,
                        documentsPending = 3,
                        followUpAt = Instant.now().minusSeconds(3600),
                        lastInteractionAt = Instant.now(),
                        version = 1,
                        contact = MaskedContact("98XXXXXX12", "siv****@gmail.com", true),
                    ),
                    Client(
                        clientId = "CLI-10242",
                        name = "Rajesh Kumar Gupta",
                        serviceId = "SVC-GST-2310",
                        processStatus = ProcessStatus.WAITING_GOVERNMENT_APPROVAL,
                        paymentStatus = PaymentStatus.RECEIVED,
                        governmentStatus = com.asktrix.agent.core.data.model.GovernmentStatus.UNDER_REVIEW,
                        documentsPending = 0,
                        followUpAt = Instant.now().plusSeconds(86_400 * 4),
                        lastInteractionAt = Instant.now(),
                        version = 3,
                        contact = MaskedContact("98XXXXXX78", "raj****@yahoo.in", true),
                        hasPendingChanges = true,
                    ),
                ),
            ),
            onFilterChange = {},
            onRefresh = {},
            onClientClick = {},
        )
    }
}
