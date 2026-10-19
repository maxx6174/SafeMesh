package com.safemesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safemesh.MainActivity
import com.safemesh.protocol.SafeMeshPacket

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SERVICE = "safemesh_mesh_channel"
        const val CHANNEL_ID_EMERGENCY = "safemesh_emergency_alerts"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "SafeMesh Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SafeMesh Bluetooth mesh discovery and relaying active"
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when emergency SOS packets are received nearby"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    fun buildForegroundServiceNotification(peerCount: Int, pendingPackets: Int): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle("SafeMesh Mesh Active")
            .setContentText("Connected peers: $peerCount | Pending relays: $pendingPackets")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showEmergencyNotification(packet: SafeMeshPacket) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("SHOW_EMERGENCY_ID", packet.messageId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val locText = if (packet.latitude != null && packet.longitude != null) {
            "Location: %.4f, %.4f".format(packet.latitude, packet.longitude)
        } else {
            "Coordinates not available"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
            .setContentTitle("EMERGENCY SOS RECEIVED")
            .setContentText("From Node ${packet.senderId.take(8)} - $locText")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_EMERGENCY, notification)
    }
}
