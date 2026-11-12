package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings_table")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val isPrivateAccount: Boolean = false,
    val pushNotifications: Boolean = true,
    val darkMode: Boolean = false,
    val language: String = "English",
    val appPin: String = "",
    val isPinEnabled: Boolean = false,
    val fontScale: Float = 1.0f,
    val highQualityUploads: Boolean = true,
    val hapticFeedback: Boolean = true,
    val quietMode: Boolean = false,
    val captionsEnabled: Boolean = false,
    val dataSaver: Boolean = false
)
