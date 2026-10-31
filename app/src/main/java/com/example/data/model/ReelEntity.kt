package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reels_table")
data class ReelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val creatorName: String,
    val creatorUsername: String,
    val creatorProfilePic: String = "avatar_1",
    val creatorUid: String = "",
    val caption: String,
    val audioTitle: String = "Original Audio",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isFollowingCreator: Boolean = false,
    val bgGradientStart: String = "#FF1A75", // Hex representation for visual gradient background loops
    val bgGradientEnd: String = "#8A3AB9",
    val timestamp: Long = System.currentTimeMillis(),
    val isViralBoosted: Boolean = false,
    val viewsCount: Int = 0,
    val sharesCount: Int = 0,
    val watchTimeSeconds: Long = 0,
    val mediaUrl: String = "",
    val thumbnailUrl: String = ""
)
