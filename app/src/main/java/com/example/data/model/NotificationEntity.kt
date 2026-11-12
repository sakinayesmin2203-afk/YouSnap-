package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications_table")
data class NotificationEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "LIKE", "COMMENT", "FOLLOW"
    val username: String,
    val userAvatarUrl: String = "",
    val time: String = "Just now",
    val detail: String = "",
    val isFollowingBack: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
