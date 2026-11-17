package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object YouSnapNotificationHelper {
    const val CHANNEL_ID = "yousnap_social_activity"
    private const val CHANNEL_NAME = "Social Activity"
    private const val CHANNEL_DESC = "Follows, likes, and comments on your YouSnap posts"

    const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    const val EXTRA_NOTIF_TYPE = "notif_type"
    const val EXTRA_NOTIF_SENDER = "notif_sender"
    const val EXTRA_NOTIF_POST_ID = "notif_post_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        type: String = "activity",
        senderUsername: String = "",
        postId: String = "",
        notificationKey: String = ""
    ) {
        val prefs = context.getSharedPreferences("yousnap_prefs", Context.MODE_PRIVATE)
        val pushEnabled = prefs.getBoolean("push_notifications_enabled", true)
        val quietMode = prefs.getBoolean("quiet_mode_enabled", false)
        if (!pushEnabled || quietMode) return

        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
            putExtra(EXTRA_NOTIF_TYPE, type)
            putExtra(EXTRA_NOTIF_SENDER, senderUsername)
            putExtra(EXTRA_NOTIF_POST_ID, postId)
        }

        val uniqueKey = notificationKey.ifBlank { "${type}_${senderUsername}_${postId}_${System.currentTimeMillis()}" }
        val requestCode = uniqueKey.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "YouSnap" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (se: SecurityException) {
            android.util.Log.w("YouSnapNotification", "Notification permission not granted: ${se.message}")
        } catch (t: Throwable) {
            android.util.Log.w("YouSnapNotification", "Could not display notification: ${t.message}")
        }
    }
}
