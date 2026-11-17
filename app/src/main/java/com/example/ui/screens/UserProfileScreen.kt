package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    viewModel: YouSnapViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsState()
    val followingMap by viewModel.otherUserIsFollowing.collectAsState()
    val photos by viewModel.personalPhotosState.collectAsState()
    val reels by viewModel.reelsState.collectAsState()

    val userProfile = remember(allUsers, username) {
        allUsers.firstOrNull { it.username.equals(username, ignoreCase = true) }
    }
    val userPhotos = remember(photos, username) {
        photos.filter { it.creatorUsername.equals(username, ignoreCase = true) }
    }
    val userReels = remember(reels, username) {
        reels.filter { it.creatorUsername.equals(username, ignoreCase = true) }
    }
    val isFollowing = followingMap.entries
        .firstOrNull { it.key.equals(username, ignoreCase = true) }?.value ?: false
    val displayName = userProfile?.name?.ifBlank { username } ?: username
    val followersCount = userProfile?.followersCount ?: 0
    val followingCount = userProfile?.followingCount ?: 0

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "@$username",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val pic = userProfile?.profilePic.orEmpty()
                if (pic.isNotBlank() && (pic.startsWith("http") || pic.startsWith("content://"))) {
                    coil.compose.AsyncImage(
                        model = pic,
                        contentDescription = displayName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase().ifBlank { "U" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!userProfile?.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userProfile!!.bio,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${userPhotos.size + userReels.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$followersCount", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$followingCount", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Following", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = { viewModel.toggleFollowUser(targetUsername = username) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0095F6),
                    contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = if (isFollowing) "Following" else "Follow",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: YouSnapViewModel,
    onNavigateTo: (Screen) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings and privacy", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Account & Profile
            SettingsSectionHeader(title = "Your account")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Person,
                    title = "Edit Profile",
                    subtitle = "Change name, bio and profile picture",
                    onClick = { onNavigateTo(Screen.EditProfile) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy and Security",
                    subtitle = "Account privacy, password and security",
                    onClick = { onNavigateTo(Screen.PrivacySecurity) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.Block,
                    title = "Blocked Accounts",
                    subtitle = "Manage accounts you have blocked",
                    onClick = { onNavigateTo(Screen.BlockedAccounts) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.History,
                    title = "Watch History",
                    subtitle = "Reels and posts you watched",
                    onClick = { onNavigateTo(Screen.WatchHistory) }
                )
            }

            // Section 2: Preferences & Display
            SettingsSectionHeader(title = "Preferences")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Push notifications, sounds and alerts",
                    onClick = { onNavigateTo(Screen.NotificationSettings) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.Tune,
                    title = "Content Preferences",
                    subtitle = "Sensitive content and video quality",
                    onClick = { onNavigateTo(Screen.ContentPreferences) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.DisplaySettings,
                    title = "App Preferences",
                    subtitle = "Theme, font scale and accessibility",
                    onClick = { onNavigateTo(Screen.AppPreferences) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.Storage,
                    title = "Data Storage and Cache",
                    subtitle = "Manage device space and clear cache",
                    onClick = { onNavigateTo(Screen.DataStorage) }
                )
            }

            // Section 3: Creator & Monetization
            SettingsSectionHeader(title = "Creator tools")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                SettingsItem(
                    icon = Icons.Outlined.WorkspacePremium,
                    title = "Creator Tools and Insights",
                    subtitle = "Metrics, analytics and creator dashboard",
                    onClick = { onNavigateTo(Screen.CreatorTools) }
                )
            }

            // Section 4: Security & Support
            SettingsSectionHeader(title = "Support and About")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Devices,
                    title = "Login Activity",
                    subtitle = "Manage active sessions across devices",
                    onClick = { onNavigateTo(Screen.LoginActivity) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                SettingsItem(
                    icon = Icons.Outlined.HelpOutline,
                    title = "Support and Help",
                    subtitle = "Help center, report a problem and contact",
                    onClick = { onNavigateTo(Screen.SupportHelp) }
                )
            }

            // Section 5: Logout Action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = "Log out", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log out of YouSnap", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of your YouSnap account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    }
                ) {
                    Text("Log out", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
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
