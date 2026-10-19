package com.safemesh.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object EmergencySos : Screen("emergency_sos")
    data object EmergencyStatus : Screen("emergency_status/{messageId}") {
        fun createRoute(messageId: String) = "emergency_status/$messageId"
    }
    data object NearbyDevices : Screen("nearby_devices")
    data object Messages : Screen("messages")
    data object MessageDetails : Screen("message_details/{messageId}") {
        fun createRoute(messageId: String) = "message_details/$messageId"
    }
    data object ReceivedEmergency : Screen("received_emergency/{messageId}") {
        fun createRoute(messageId: String) = "received_emergency/$messageId"
    }
    data object Location : Screen("location")
    data object Settings : Screen("settings")
}
