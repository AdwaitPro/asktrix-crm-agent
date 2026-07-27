package com.asktrix.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.asktrix.agent.feature.auth.LoginRoute
import com.asktrix.agent.feature.dashboard.DashboardRoute

/**
 * The navigation graph.
 *
 * Destinations are added as each feature module lands.
 */
@Composable
fun AsktrixApp(onReady: () -> Unit) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) { onReady() }

    NavHost(navController = navController, startDestination = Route.LOGIN) {
        composable(Route.LOGIN) {
            LoginRoute(onSignedIn = {
                navController.navigate(Route.DASHBOARD) {
                    // Signing in must not leave the login screen on the back stack.
                    popUpTo(Route.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Route.DASHBOARD) {
            DashboardRoute(
                onClientClick = { clientId ->
                    navController.navigate("${Route.CLIENT}/$clientId")
                },
            )
        }
    }
}

object Route {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val CLIENT = "client"
}
