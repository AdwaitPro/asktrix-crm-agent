package com.asktrix.agent.feature.attendance

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import kotlinx.coroutines.launch

/**
 * In-app camera for the optional attendance photo (§11).
 *
 * Front camera, low resolution, and no gallery access — the photo exists to evidence that a person
 * was present, so anything more would collect data the requirement does not ask for.
 *
 * "Skip" is deliberately always available: the photo is optional, and a camera that fails must never
 * be able to prevent someone recording their attendance.
 */
@Composable
fun AttendanceCameraSheet(
    onCaptured: (ByteArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val camera = remember { AttendanceCamera(context) }
    var bound by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        bound = camera.bind(lifecycleOwner, preview)
        failed = !bound
    }

    DisposableEffect(Unit) { onDispose { camera.release() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance photo", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(RATIO_3_4)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        failed -> Text(
                            text = "Camera unavailable. You can still check in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(AsktrixTheme.spacing.lg),
                        )
                        !bound -> CircularProgressIndicator()
                        else -> AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(AsktrixTheme.spacing.md))
                Text(
                    text = "Stored with your check-in and retained for 90 days.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    capturing = true
                    scope.launch { onCaptured(camera.capture()) }
                },
                enabled = bound && !capturing,
                shape = RoundedCornerShape(10.dp),
            ) {
                if (capturing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Capture")
                }
            }
        },
        dismissButton = {
            // Always available: the photo is optional and must never block attendance.
            TextButton(onClick = { onCaptured(null) }) { Text("Skip photo") }
        },
    )
}

private const val RATIO_3_4 = 3f / 4f
