package com.asktrix.agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme

@Composable
fun SettingsRoute(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    SettingsScreen(state = state, onSignOut = viewModel::signOut)
}

@Composable
fun SettingsScreen(state: SettingsUiState, onSignOut: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AsktrixTheme.spacing.screenEdge),
        ) {
            Spacer(Modifier.height(AsktrixTheme.spacing.lg))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(AsktrixTheme.spacing.xl))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(AsktrixTheme.spacing.lg)) {
                    InfoRow("Device", state.deviceModel)
                    InfoRow("Android", state.androidVersion)
                    InfoRow("App version", state.appVersion)
                }
            }

            Spacer(Modifier.height(AsktrixTheme.spacing.lg))

            StatusBanner(
                icon = if (state.managedDevice) Icons.Outlined.GppGood else Icons.Outlined.GppMaybe,
                title = if (state.managedDevice) "Managed device" else "Not enrolled in management",
                body = if (state.managedDevice) {
                    "Policies are applied by your organisation."
                } else {
                    "Contact your administrator — this device is not under management."
                },
                warning = !state.managedDevice,
            )

            // Surfaced because an OEM battery manager silently throttling us is the usual cause of
            // missing location data, and the employee is the only one who can fix it on the handset.
            if (state.backgroundRestricted) {
                Spacer(Modifier.height(AsktrixTheme.spacing.md))
                StatusBanner(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "Background activity is restricted",
                    body = "Location and sync may stop. Open Settings, find this app, and allow " +
                        "unrestricted background activity.",
                    warning = true,
                )
            }

            if (state.integrityConcern) {
                Spacer(Modifier.height(AsktrixTheme.spacing.md))
                StatusBanner(
                    icon = Icons.Outlined.GppMaybe,
                    title = "Device integrity check failed",
                    body = "This device shows signs of tampering. Contact your administrator.",
                    warning = true,
                )
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onSignOut,
                enabled = !state.isSigningOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSigningOut) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign out", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(AsktrixTheme.spacing.sm))
            Text(
                text = "Signing out erases all cached client data from this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AsktrixTheme.spacing.xl))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = AsktrixTheme.spacing.xs)) {
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
private fun StatusBanner(icon: ImageVector, title: String, body: String, warning: Boolean) {
    val tone = if (warning) {
        AsktrixTheme.statusColors.warning
    } else {
        AsktrixTheme.statusColors.positive
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tone.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(AsktrixTheme.spacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(AsktrixTheme.spacing.md))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun SettingsPreview() {
    AsktrixTheme {
        SettingsScreen(
            state = SettingsUiState(
                deviceModel = "Xiaomi Redmi Note 13",
                androidVersion = "Android 14 (API 34)",
                appVersion = "0.1.0",
                managedDevice = true,
                backgroundRestricted = true,
            ),
            onSignOut = {},
        )
    }
}
