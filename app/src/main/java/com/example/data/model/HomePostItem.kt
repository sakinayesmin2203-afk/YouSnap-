package com.example.data.model

data class HomePostItem(
    val id: String,
    val username: String,
    val userDisplayName: String = "",
    val userAvatarUrl: String = "",
    val userInitials: String = "US",
    val location: String = "",
    val imageUrl: String = "",
    val imageRes: Int? = null,
    val caption: String = "",
    val hashtags: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val timeAgo: String = "Just now",
    val isFromUserProfile: Boolean = false
)
