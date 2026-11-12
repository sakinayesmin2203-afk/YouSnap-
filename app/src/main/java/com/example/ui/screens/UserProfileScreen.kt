package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel

@Composable
fun UserProfileScreen(
    username: String,
    viewModel: YouSnapViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("User Profile: $username")
    }
}

@Composable
fun SettingsScreen(
    viewModel: YouSnapViewModel,
    onNavigateTo: (Screen) -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings Screen")
    }
}

@Composable
fun PinLockScreen(
    viewModel: YouSnapViewModel,
    onUnlockSuccess: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pin Lock Screen")
    }
}
