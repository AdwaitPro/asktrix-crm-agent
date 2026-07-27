package com.asktrix.agent.feature.attendance

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asktrix.agent.core.data.repository.AttendanceToday
import com.asktrix.agent.core.designsystem.component.absoluteLabel
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import java.time.Instant

@Composable
fun AttendanceRoute(viewModel: AttendanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }

    AttendanceScreen(
        state = state,
        onToggle = viewModel::toggle,
        onRequestPermission = {
            // Foreground location only. ACCESS_BACKGROUND_LOCATION must be requested separately,
            // after this is granted — Android refuses to show both in one dialog.
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        },
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AsktrixTheme.spacing.screenEdge),
        ) {
            Spacer(Modifier.height(AsktrixTheme.spacing.lg))
            Text(
                text = "Attendance",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(AsktrixTheme.spacing.xl))

            StatusRing(checkedIn = state.today.checkedIn)

            Spacer(Modifier.height(AsktrixTheme.spacing.xl))

            TodaySummary(state.today)

            AnimatedVisibility(visible = state.needsLocationPermission) {
                PermissionPrompt(onRequestPermission)
            }

            Spacer(Modifier.weight(1f))

            state.lastFixAccuracy?.let { accuracy ->
                Row(
                    modifier = Modifier.padding(bottom = AsktrixTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(AsktrixTheme.spacing.xs))
                    Text(
                        text = "Recorded within ${accuracy.toInt()} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onToggle,
                enabled = !state.isBusy && !state.needsLocationPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.today.checkedIn) {
                        AsktrixTheme.statusColors.negative
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = if (state.today.checkedIn) "Check out" else "Check in",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(AsktrixTheme.spacing.sm))
            Text(
                text = "Your location is recorded every 10 minutes while you are checked in, and " +
                    "not at any other time.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsktrixTheme.spacing.xl))
        }
    }
}

@Composable
private fun StatusRing(checkedIn: Boolean) {
    val tone = if (checkedIn) {
        AsktrixTheme.statusColors.positive
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tone.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (checkedIn) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(AsktrixTheme.spacing.sm))
            Text(
                text = if (checkedIn) "Checked in" else "Not checked in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun TodaySummary(today: AttendanceToday) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(AsktrixTheme.spacing.lg)) {
            SummaryRow("Checked in", today.checkInAt?.absoluteLabel() ?: "—")
            SummaryRow("Checked out", today.checkOutAt?.absoluteLabel() ?: "—")
            SummaryRow(
                "Worked",
                today.workedSeconds?.let { seconds ->
                    "${seconds / SECONDS_PER_HOUR}h ${(seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE}m"
                } ?: "—",
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = AsktrixTheme.spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AsktrixTheme.statusColors.warning.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsktrixTheme.spacing.lg),
    ) {
        Row(
            Modifier.padding(AsktrixTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = AsktrixTheme.statusColors.warning,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsktrixTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Location needed", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Attendance is recorded with your location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRequest, shape = RoundedCornerShape(10.dp)) { Text("Allow") }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

@Preview(name = "Attendance — checked in", showBackground = true, heightDp = 800)
@Composable
private fun AttendancePreview() {
    AsktrixTheme {
        AttendanceScreen(
            state = AttendanceUiState(
                today = AttendanceToday(
                    checkedIn = true,
                    checkInAt = Instant.now().minusSeconds(11_000),
                    workedSeconds = 11_000,
                ),
                lastFixAccuracy = 14f,
            ),
            onToggle = {},
            onRequestPermission = {},
            onConsumeMessage = {},
        )
    }
}
