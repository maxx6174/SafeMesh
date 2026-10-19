package com.safemesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.safemesh.service.SafeMeshForegroundService
import com.safemesh.ui.navigation.NavGraph
import com.safemesh.ui.navigation.Screen
import com.safemesh.ui.theme.SafeMeshTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val app = application as SafeMeshApplication
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        app.locationProvider.updatePermissionStatus(locationGranted)

        // If Bluetooth permissions are granted, start mesh discovery
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.BLUETOOTH_ADVERTISE] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }

        if (bluetoothGranted) {
            app.bluetoothMeshManager.startMesh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SafeMeshApplication
        requestPermissionsIfNeeded()

        // Check if opened from an Emergency notification
        val emergencyId = intent?.getStringExtra("SHOW_EMERGENCY_ID")
        val startDestination = if (emergencyId != null) {
            Screen.ReceivedEmergency.createRoute(emergencyId)
        } else {
            Screen.Home.route
        }

        // Start background relay service if enabled in preferences
        val prefs = getSharedPreferences("safemesh_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("background_service", true)) {
            SafeMeshForegroundService.startService(this)
        }

        setContent {
            SafeMeshTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    app = app,
                    startDestination = startDestination
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            val app = application as SafeMeshApplication
            app.locationProvider.updatePermissionStatus(true)
            app.bluetoothMeshManager.startMesh()
        }
    }
}
