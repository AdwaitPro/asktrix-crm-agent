package com.asktrix.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The application root.
 *
 * At launch the app has to resolve three things before it can route anywhere: the device integrity
 * verdict, the stored session, and the EMM managed configuration. Until those resolve, this is the
 * only correct state to show. The navigation graph is attached in the next wave, once
 * `:feature:auth` and `:feature:dashboard` expose their destinations.
 */
@Composable
fun AsktrixApp(onReady: () -> Unit) {
    LaunchedEffect(Unit) { onReady() }

    val startingUp = stringResource(R.string.a11y_starting_up)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = startingUp },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
