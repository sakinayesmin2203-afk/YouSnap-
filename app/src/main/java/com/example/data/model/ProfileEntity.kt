package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_table")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Sakinay Esmin",
    val username: String = "sakinay_esmin",
    val bio: String = "Creative mind & digital explorer 🌟 | Building awesome apps with Compose!",
    val profilePic: String = "avatar_default",
    val category: String = "Digital Creator",
    val hiddenStoryFrom: String = "", // Comma-separated usernames
    val email: String = ""
)
