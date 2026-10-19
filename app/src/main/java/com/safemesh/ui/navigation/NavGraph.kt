package com.safemesh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.safemesh.SafeMeshApplication
import com.safemesh.ui.screens.EmergencySosScreen
import com.safemesh.ui.screens.EmergencyStatusScreen
import com.safemesh.ui.screens.HomeScreen
import com.safemesh.ui.screens.LocationScreen
import com.safemesh.ui.screens.MessageDetailScreen
import com.safemesh.ui.screens.MessagesScreen
import com.safemesh.ui.screens.NearbyDevicesScreen
import com.safemesh.ui.screens.ReceivedEmergencyScreen
import com.safemesh.ui.screens.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    app: SafeMeshApplication,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                app = app,
                onNavigateToSos = { navController.navigate(Screen.EmergencySos.route) },
                onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                onNavigateToPeers = { navController.navigate(Screen.NearbyDevices.route) },
                onNavigateToLocation = { navController.navigate(Screen.Location.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onEmergencyAlertClick = { messageId ->
                    navController.navigate(Screen.ReceivedEmergency.createRoute(messageId))
                }
            )
        }

        composable(Screen.EmergencySos.route) {
            EmergencySosScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() },
                onSosDispatched = { messageId ->
                    navController.navigate(Screen.EmergencyStatus.createRoute(messageId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EmergencyStatus.route,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            EmergencyStatusScreen(
                app = app,
                messageId = messageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NearbyDevices.route) {
            NearbyDevicesScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Messages.route) {
            MessagesScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() },
                onMessageClick = { messageId ->
                    navController.navigate(Screen.MessageDetails.createRoute(messageId))
                }
            )
        }

        composable(
            route = Screen.MessageDetails.route,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            MessageDetailScreen(
                app = app,
                messageId = messageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ReceivedEmergency.route,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            ReceivedEmergencyScreen(
                app = app,
                messageId = messageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Location.route) {
            LocationScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
