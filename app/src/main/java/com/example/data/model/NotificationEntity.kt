package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications_table")
data class NotificationEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val type: String = "like", // "follow", "like", "comment"
    val username: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val userAvatarUrl: String = "",
    val recipientUid: String = "",
    val recipientUsername: String = "",
    val postId: String = "",
    val postMediaUrl: String = "",
    val time: String = "Just now",
    val detail: String = "",
    val isFollowingBack: Boolean = false,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
