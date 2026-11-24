package com.example.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YouSnapMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("YouSnapFCM", "Refreshed FCM token received")
        val prefs = getSharedPreferences("yousnap_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Throwable) {
            null
        }

        if (!uid.isNullOrBlank()) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(
                        mapOf(
                            "fcmToken" to token,
                            "fcmUpdatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
            } catch (t: Throwable) {
                Log.w("YouSnapFCM", "Deferred FCM token sync: ${t.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: "activity"
        val senderUsername = data["senderUsername"] ?: data["username"] ?: ""
        val postId = data["postId"] ?: ""
        val notificationId = data["notificationId"] ?: message.messageId ?: ""

        val defaultTitle = when (type.lowercase()) {
            "follow" -> "New Follower"
            "like" -> "New Like"
            "comment" -> "New Comment"
            else -> "YouSnap"
        }

        val title = message.notification?.title
            ?: data["title"]
            ?: defaultTitle

        val body = message.notification?.body
            ?: data["body"]
            ?: when (type.lowercase()) {
                "follow" -> if (senderUsername.isNotBlank()) "@$senderUsername started following you." else "Someone started following you."
                "like" -> if (senderUsername.isNotBlank()) "@$senderUsername liked your post." else "Someone liked your post."
                "comment" -> if (senderUsername.isNotBlank()) "@$senderUsername commented on your post." else "Someone commented on your post."
                else -> "You have new activity on YouSnap."
            }

        YouSnapNotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            type = type,
            senderUsername = senderUsername,
            postId = postId,
            notificationKey = notificationId
        )
    }
}
