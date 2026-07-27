package com.asktrix.agent.feature.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asktrix.agent.core.data.model.CallState
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.ClientDocument
import com.asktrix.agent.core.data.model.DocumentStatus
import com.asktrix.agent.core.data.model.GovernmentStatus
import com.asktrix.agent.core.data.model.MaskedContact
import com.asktrix.agent.core.data.model.PaymentStatus
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.data.model.TimelineEntry
import com.asktrix.agent.core.data.model.TimelineKind
import com.asktrix.agent.core.designsystem.component.OfflineBanner
import com.asktrix.agent.core.designsystem.component.StatusChip
import com.asktrix.agent.core.designsystem.component.absoluteLabel
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import java.time.Instant

@Composable
fun ClientDetailRoute(
    onBack: () -> Unit,
    viewModel: ClientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClientDetailScreen(
        state = state,
        onBack = onBack,
        onCall = viewModel::startCall,
        onStatus = { viewModel.applyStatus(it) },
        onDismissCall = viewModel::dismissCall,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    state: ClientDetailUiState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onStatus: (ProcessStatus) -> Unit,
    onDismissCall: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.errorMessage) {
        val text = state.errorMessage ?: state.message
        if (text != null) {
            snackbarHost.showSnackbar(text)
            onConsumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.client?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->

        val client = state.client
        if (client == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = AsktrixTheme.spacing.screenEdge,
                end = AsktrixTheme.spacing.screenEdge,
                bottom = AsktrixTheme.spacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.lg),
        ) {
            item {
                AnimatedVisibility(visible = !state.isOnline, enter = fadeIn(), exit = fadeOut()) {
                    OfflineBanner(pendingCount = if (client.hasPendingChanges) 1 else 0)
                }
            }

            item { SummaryCard(client) }

            item {
                MaskedContactCard(
                    contact = client.contact,
                    activeCallLabel = state.activeCall
                        ?.takeIf { it.state.isActive }
                        ?.state
                        ?.label,
                    onCall = onCall,
                )
            }

            if (state.activeCall != null && state.activeCall.state.isTerminal) {
                item { CallOutcomeCard(state.activeCall.state, state.activeCall.durationSeconds, onDismissCall) }
            }

            item { QuickStatusSection(current = client.processStatus, onStatus = onStatus) }

            if (client.documents.isNotEmpty()) {
                item { DocumentsSection(client.documents) }
            }

            item {
                Text(
                    text = "Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (state.timeline.isEmpty()) {
                item {
                    Text(
                        text = "No interactions recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.timeline, key = { it.entryId }) { TimelineRow(it) }
            }
        }
    }
}

@Composable
private fun SummaryCard(client: Client) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(AsktrixTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(client.clientId, client.serviceId).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(label = client.processStatus.label, tone = client.processStatus.tone())
            }
            Spacer(Modifier.height(AsktrixTheme.spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(AsktrixTheme.spacing.md))
            DetailRow("Payment", client.paymentStatus.label)
            DetailRow("Government", client.governmentStatus.label)
            DetailRow("Documents pending", client.documentsPending.toString())
            client.followUpAt?.let { DetailRow("Follow-up", it.absoluteLabel()) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsktrixTheme.spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Masked contact details (§4).
 *
 * There is no copy affordance, no long-press selection, and no tap-to-dial: the values shown here
 * are the *only* form the device ever receives, and calling goes through the CRM by client id
 * (§5). The lock note tells the employee this is deliberate, so a masked number does not read as a
 * bug they should work around.
 */
@Composable
private fun MaskedContactCard(
    contact: MaskedContact,
    activeCallLabel: String?,
    onCall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(AsktrixTheme.spacing.lg)) {

            ContactLine(Icons.Outlined.Phone, contact.phoneMasked.ifBlank { "Not available" })
            Spacer(Modifier.height(AsktrixTheme.spacing.sm))
            ContactLine(Icons.Outlined.MailOutline, contact.emailMasked.ifBlank { "Not available" })

            Spacer(Modifier.height(AsktrixTheme.spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(AsktrixTheme.spacing.xs))
                Text(
                    text = "Contact details are masked by the CRM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(AsktrixTheme.spacing.lg))

            Button(
                onClick = onCall,
                enabled = contact.callable && activeCallLabel == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AsktrixTheme.spacing.minTouchTarget),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (activeCallLabel != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(AsktrixTheme.spacing.sm))
                    Text(activeCallLabel, style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AsktrixTheme.spacing.sm))
                    Text("Call through CRM", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ContactLine(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsktrixTheme.spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            // Announced as masked so a screen-reader user is not left guessing at the X characters.
            modifier = Modifier.semantics { contentDescription = "Masked contact: $value" },
        )
    }
}

@Composable
private fun CallOutcomeCard(state: CallState, durationSeconds: Int?, onDismiss: () -> Unit) {
    val tone = if (state == CallState.COMPLETED) {
        AsktrixTheme.statusColors.positive
    } else {
        AsktrixTheme.statusColors.warning
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tone.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(AsktrixTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(state.label, style = MaterialTheme.typography.titleSmall)
                if (durationSeconds != null && durationSeconds > 0) {
                    Text(
                        text = "${durationSeconds / SECONDS_PER_MINUTE}m ${durationSeconds % SECONDS_PER_MINUTE}s · recorded by the CRM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** The six §13 quick actions. */
@Composable
private fun QuickStatusSection(current: ProcessStatus, onStatus: (ProcessStatus) -> Unit) {
    Column {
        Text(
            text = "Update status",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(AsktrixTheme.spacing.md))

        QUICK_STATUSES.chunked(STATUS_COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = AsktrixTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsktrixTheme.spacing.sm),
            ) {
                row.forEach { status ->
                    val selected = status == current
                    OutlinedButton(
                        onClick = { onStatus(status) },
                        enabled = !selected,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size < STATUS_COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DocumentsSection(documents: List<ClientDocument>) {
    Column {
        Text(
            text = "Documents",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(AsktrixTheme.spacing.sm))
        documents.forEach { document ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = AsktrixTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(AsktrixTheme.spacing.sm))
                Text(
                    text = document.kind.replace('_', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = document.status.label,
                    tone = when (document.status) {
                        DocumentStatus.VERIFIED, DocumentStatus.RECEIVED ->
                            com.asktrix.agent.core.designsystem.component.StatusTone.POSITIVE
                        DocumentStatus.REJECTED ->
                            com.asktrix.agent.core.designsystem.component.StatusTone.NEGATIVE
                        DocumentStatus.PENDING ->
                            com.asktrix.agent.core.designsystem.component.StatusTone.WARNING
                    },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when (entry.kind) {
                        TimelineKind.CALL -> AsktrixTheme.statusColors.positive
                        TimelineKind.PAYMENT -> AsktrixTheme.statusColors.positive
                        TimelineKind.STATUS_CHANGE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
        )
        Spacer(Modifier.width(AsktrixTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(entry.actorName, entry.occurredAt.absoluteLabel())
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val STATUS_COLUMNS = 2
private const val SECONDS_PER_MINUTE = 60

@Preview(name = "Client detail", showBackground = true, heightDp = 1200)
@Composable
private fun ClientDetailPreview() {
    AsktrixTheme {
        ClientDetailScreen(
            state = ClientDetailUiState(
                isLoading = false,
                client = Client(
                    clientId = "CLI-10240",
                    name = "Sivakumar Ramanathan",
                    serviceId = "SVC-GST-2291",
                    processStatus = ProcessStatus.DOCUMENTS_PENDING,
                    paymentStatus = PaymentStatus.PENDING,
                    governmentStatus = GovernmentStatus.NOT_SUBMITTED,
                    documentsPending = 3,
                    followUpAt = Instant.now(),
                    lastInteractionAt = Instant.now(),
                    version = 1,
                    contact = MaskedContact("98XXXXXX12", "siv****@gmail.com", true),
                    documents = listOf(
                        ClientDocument("d1", "PAN", DocumentStatus.VERIFIED, Instant.now()),
                        ClientDocument("d2", "AADHAAR", DocumentStatus.PENDING, null),
                    ),
                ),
                timeline = listOf(
                    TimelineEntry("t1", TimelineKind.CALL, "Call completed — 1m 59s (recorded)", "Aarav Sharma", "c1", Instant.now()),
                    TimelineEntry("t2", TimelineKind.STATUS_CHANGE, "Case opened — SVC-GST-2291", "Aarav Sharma", null, Instant.now()),
                ),
            ),
            onBack = {},
            onCall = {},
            onStatus = {},
            onDismissCall = {},
            onConsumeMessage = {},
        )
    }
}
