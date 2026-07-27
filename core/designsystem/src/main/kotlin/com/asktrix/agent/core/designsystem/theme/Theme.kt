package com.asktrix.agent.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightScheme = lightColorScheme(
    primary = Sky700,
    onPrimary = Slate50,
    primaryContainer = Sky300,
    onPrimaryContainer = Sky900,

    secondary = Slate700,
    onSecondary = Slate50,
    secondaryContainer = Slate200,
    onSecondaryContainer = Slate900,

    background = Slate50,
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,

    outline = Slate400,
    outlineVariant = Slate200,

    error = Rose700,
    onError = Slate50,
)

private val DarkScheme = darkColorScheme(
    primary = Sky400,
    onPrimary = Slate950,
    primaryContainer = Sky700,
    onPrimaryContainer = Sky300,

    secondary = Slate400,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate100,

    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,

    outline = Slate500,
    outlineVariant = Slate800,

    error = Rose500,
    onError = Slate950,
)

/**
 * The app theme.
 *
 * Dynamic colour is deliberately **not** used. This is an enterprise tool where a status colour has a
 * fixed meaning - "payment received" must look identical on every handset in the fleet, so a
 * wallpaper-derived palette would actively harm comprehension.
 */
@Composable
fun AsktrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalAsktrixSpacing provides AsktrixSpacing(),
        LocalAsktrixStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AsktrixTypography,
            content = content,
        )
    }
}

/** Theme accessors, so call sites read `AsktrixTheme.spacing.lg` rather than reaching into locals. */
object AsktrixTheme {

    val spacing: AsktrixSpacing
        @Composable @ReadOnlyComposable get() = LocalAsktrixSpacing.current

    val statusColors: AsktrixStatusColors
        @Composable @ReadOnlyComposable get() = LocalAsktrixStatusColors.current
}
