package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings_table")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val isPrivateAccount: Boolean = false,
    val is2FaEnabled: Boolean = false,
    val highQualityUploads: Boolean = true,
    val sensitiveContentFilter: Boolean = false,
    val autoPlaySettings: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val quietModeEnabled: Boolean = false,
    val forceDarkMode: Boolean = true, // default to True for an Insta-dark look, can toggle
    val captionsToggle: Boolean = true,
    val dataSaverMode: Boolean = false
)
