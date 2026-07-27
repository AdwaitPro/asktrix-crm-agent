package com.asktrix.agent

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asktrix.agent.feature.attendance.AttendanceRoute
import com.asktrix.agent.feature.auth.LoginRoute
import com.asktrix.agent.feature.calls.CallHistoryRoute
import com.asktrix.agent.feature.client.ClientDetailRoute
import com.asktrix.agent.feature.dashboard.DashboardRoute
import com.asktrix.agent.feature.settings.SettingsRoute

/**
 * The navigation graph.
 *
 * The bottom bar appears only on the three top-level destinations. Login and client detail take the
 * full screen: tabs on a sign-in screen invite poking at a locked app, and a detail view is a
 * drill-down rather than a peer.
 */
@Composable
fun AsktrixApp(onReady: () -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) { onReady() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AsktrixBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.LOGIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showBottomBar) padding else PaddingValues(0.dp)),
        ) {
            composable(Route.LOGIN) {
                LoginRoute(
                    onSignedIn = {
                        navController.navigate(Route.DASHBOARD) {
                            // Signing in must not leave the login screen on the back stack.
                            popUpTo(Route.LOGIN) { inclusive = true }
                        }
                    },
                )
            }

            composable(Route.DASHBOARD) {
                DashboardRoute(
                    onClientClick = { clientId -> navController.navigate("${Route.CLIENT}/$clientId") },
                )
            }

            composable(Route.CALLS) { CallHistoryRoute() }

            composable(Route.ATTENDANCE) { AttendanceRoute() }

            composable(Route.SETTINGS) {
                SettingsRoute(
                    onSignedOut = {
                        navController.navigate(Route.LOGIN) {
                            // Sign-out wipes the back stack entirely: nothing behind the login
                            // screen should survive, since the cache it rendered is gone (§3).
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable("${Route.CLIENT}/{clientId}") {
                ClientDetailRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AsktrixBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BOTTOM_ITEMS.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            // Tabs switch rather than stacking up.
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_ITEMS = listOf(
    BottomItem(Route.DASHBOARD, "Clients", Icons.Outlined.Groups),
    BottomItem(Route.CALLS, "Calls", Icons.Outlined.Phone),
    BottomItem(Route.ATTENDANCE, "Attendance", Icons.Outlined.EventAvailable),
    BottomItem(Route.SETTINGS, "Settings", Icons.Outlined.Settings),
)

private val TOP_LEVEL_ROUTES = BOTTOM_ITEMS.map { it.route }.toSet()

object Route {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val CALLS = "calls"
    const val ATTENDANCE = "attendance"
    const val SETTINGS = "settings"
    const val CLIENT = "client"
}
