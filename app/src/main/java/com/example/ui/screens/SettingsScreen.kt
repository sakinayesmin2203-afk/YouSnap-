package com.example.ui.screens

import com.example.ui.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: YouSnapViewModel,
    onNavigateTo: (Screen) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings and Activity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Account Info & Profile
            SettingsSectionHeader(title = "Your Account")
            SettingsRow(
                icon = Icons.Default.Person,
                title = "Edit Profile",
                subtitle = "Name, username, bio, category, picture",
                onClick = { onNavigateTo(Screen.EditProfile) },
                testTag = "settings_row_edit_profile"
            )

            // Section 2: Privacy and Security
            SettingsSectionHeader(title = "How You Use YouSnap")
            SettingsRow(
                icon = Icons.Default.Lock,
                title = "Privacy & Security",
                subtitle = "Private account, Blocked accounts, 2FA, comments, login logs",
                onClick = { onNavigateTo(Screen.PrivacySecurity) },
                testTag = "settings_row_privacy_security"
            )
            SettingsRow(
                icon = Icons.Default.NotificationsActive,
                title = "Notifications",
                subtitle = "Push notifications, Quiet mode",
                onClick = { onNavigateTo(Screen.NotificationSettings) },
                testTag = "settings_row_notifications"
            )

            // Section 3: App Preferences
            SettingsSectionHeader(title = "App Preferences & Sizing")
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "App Preferences & Accessibility",
                subtitle = "Forced Dark Mode switch, Captions & Subtitles",
                onClick = { onNavigateTo(Screen.AppPreferences) },
                testTag = "settings_row_app_preferences"
            )
            SettingsRow(
                icon = Icons.Default.VideoSettings,
                title = "Content & Media Preferences",
                subtitle = "High-Quality media uploads, Sensitive filters, Auto-Play",
                onClick = { onNavigateTo(Screen.ContentPreferences) },
                testTag = "settings_row_content_preferences"
            )
            SettingsRow(
                icon = Icons.Default.SdCard,
                title = "Data & Storage",
                subtitle = "Data saver toggle, Clear cache logs",
                onClick = { onNavigateTo(Screen.DataStorage) },
                testTag = "settings_row_data_storage"
            )

            // Section 4: Monetization & Creator Tools
            SettingsSectionHeader(title = "For Professionals")
            SettingsRow(
                icon = Icons.Default.MonetizationOn,
                title = "Creator & Monetization Tools",
                subtitle = "Insights, Fund eligibility, Gifts settings",
                onClick = { onNavigateTo(Screen.CreatorTools) },
                testTag = "settings_row_creator_tools"
            )

            // Section 5: Support and Info
            SettingsSectionHeader(title = "More Info & Support")
            SettingsRow(
                icon = Icons.Default.HelpOutline,
                title = "Support & About",
                subtitle = "Help center, report a problem, version",
                onClick = { onNavigateTo(Screen.SupportHelp) },
                testTag = "settings_row_support_help"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Log Out Button (Red!)
            Button(
                onClick = { showLogOutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .testTag("log_out_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Log Out",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Log Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Beautiful Confirmation Dialog for Log Out
        if (showLogOutDialog) {
            AlertDialog(
                onDismissRequest = { showLogOutDialog = false },
                title = { Text("Log Out of YouSnap?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to log out of @${viewModel.profileState.collectAsState().value.username}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogOutDialog = false
                            Toast.makeText(
                                viewModel.app,
                                "Logged Out Successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearAndNavigateTo(Screen.Login)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogOutDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate to sub-menu",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
