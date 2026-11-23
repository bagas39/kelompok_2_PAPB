package com.example.cloble.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.cloble.MainActivity
import com.example.cloble.R

object SystemNotificationHelper {

    const val CHANNEL_ID = "cloble_barter_notifications"
    const val CHANNEL_NAME = "Notifikasi Barter & Match CloBle"
    const val CHANNEL_DESC = "Pemberitahuan minat barter, respons match, dan aktivitas pakaian"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun hasBeenNotified(context: Context, userId: Long, notificationId: Long): Boolean {
        val prefs = context.getSharedPreferences("cloble_system_notifs_$userId", Context.MODE_PRIVATE)
        return prefs.getBoolean("notif_$notificationId", false)
    }

    fun markAsNotified(context: Context, userId: Long, notificationId: Long) {
        val prefs = context.getSharedPreferences("cloble_system_notifs_$userId", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notif_$notificationId", true).apply()
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String,
        body: String,
        relatedMatchId: Long? = null,
        destination: String = "notifications",
        notificationId: Long? = null,
        userId: Long? = null
    ): Boolean {
        initNotificationChannel(context)

        if (!hasNotificationPermission(context)) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (relatedMatchId != null) {
                putExtra("matchId", relatedMatchId)
            }
            if (notificationId != null && notificationId > 0L) {
                putExtra("notificationId", notificationId)
            }
            if (userId != null && userId > 0L) {
                putExtra("userId", userId)
            }
            putExtra("destination", destination)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postTestNotification(context: Context) {
        showNotification(
            context = context,
            id = 9999,
            title = "CloBle: Notifikasi Sistem Aktif",
            body = "Notifikasi barter CloBle berhasil masuk ke bilah status Android Anda!"
        )
    }
}
