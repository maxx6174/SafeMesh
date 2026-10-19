package com.safemesh.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.safemesh.SafeMeshApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SafeMeshForegroundService : Service() {
    private val tag = "SafeMeshService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        const val ACTION_START = "com.safemesh.action.START"
        const val ACTION_STOP = "com.safemesh.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, SafeMeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SafeMeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(tag, "Stopping SafeMesh background service")
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.i(tag, "Starting SafeMesh background service")
                val initialNotification = notificationHelper.buildForegroundServiceNotification(0, 0)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID_SERVICE,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, initialNotification)
                }

                observeMeshActivity()
                return START_STICKY
            }
        }
    }

    private fun observeMeshActivity() {
        val app = application as? SafeMeshApplication ?: return
        val meshManager = app.bluetoothMeshManager
        val peerRepo = app.peerRepository
        val engine = app.storeCarryForwardEngine

        scope.launch {
            combine(
                peerRepo.allPeersFlow,
                engine.pendingQueueCount
            ) { peers, queueCount ->
                Pair(peers.size, queueCount)
            }.collect { (peerCount, queueCount) ->
                val updatedNotification = notificationHelper.buildForegroundServiceNotification(peerCount, queueCount)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, updatedNotification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.i(tag, "SafeMesh background service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
