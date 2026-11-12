package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comments_table")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reelId: Int = 0,
    val postId: String = "",
    val username: String = "you_snap_user",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
