package com.example.ui.screens

import com.example.ui.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProfileEntity
import com.example.data.model.SettingsEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke

// Generic Sub-Screen TopBar to avoid repeating
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        navigationIcon = {
            IconButton(onClick = onBack) {
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

// 1. EDIT PROFILE SCREEN
@Composable
fun EditProfileScreen(
    viewModel: YouSnapViewModel,
    profile: ProfileEntity,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var username by remember { mutableStateOf(profile.username) }
    var bio by remember { mutableStateOf(profile.bio) }
    var category by remember { mutableStateOf(profile.category) }
    var profilePic by remember { mutableStateOf(profile.profilePic) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            profilePic = uri.toString()
        }
    }

    Scaffold(
        topBar = { SubScreenTopBar("Edit Profile", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile photo visual wrapper with preview logic
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFCAF45), Color(0xFFF56040), Color(0xFFE1306C))
                        )
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePic.isNotEmpty() && (profilePic.startsWith("content://") || profilePic.startsWith("http"))) {
                        AsyncImage(
                            model = profilePic,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = name.take(1).uppercase(),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 38.sp
                        )
                    }
                }
            }

            Text(
                "Change Profile Photo",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = profile.email,
                onValueChange = { },
                label = { Text("Email Address") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("edit_email_input"),
                singleLine = true,
                enabled = false, // Email is usually managed by account provider
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("edit_username_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("edit_category_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Biography Bio") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("edit_bio_input"),
                maxLines = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (username.trim().isEmpty() || name.trim().isEmpty()) {
                        android.widget.Toast.makeText(viewModel.app, "Fields cannot be empty!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateProfile(name, username, bio, category, profilePic) {
                            onBack()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_profile_button")
            ) {
                Text("Save Changes", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 2. PRIVACY & SECURITY SCREEN
@Composable
fun PrivacySecurityScreen(
    viewModel: YouSnapViewModel,
    settings: SettingsEntity,
    onNavigateTo: (Screen) -> Unit,
    onBack: () -> Unit
) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Change Password Dialog
    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your current password and set a new password.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.isNotBlank() && newPassword == confirmPassword) {
                            showChangePasswordDialog = false
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            android.widget.Toast.makeText(viewModel.app, "Password updated successfully! 🔒", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(viewModel.app, "Passwords do not match or are empty!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("Privacy and Security", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Account Privacy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Private Account Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("When private, only people you approve can see your reels.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = settings.isPrivateAccount,
                    onCheckedChange = {
                        viewModel.togglePrivateAccount()
                        android.widget.Toast.makeText(viewModel.app, if (!settings.isPrivateAccount) "Account is now Private" else "Account is now Public", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("private_account_switch")
                )
            }

            Text("Security Options", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // 2FA Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Two-Factor Authentication (2FA)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Secure your account with multi-factor passcodes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = settings.is2FaEnabled,
                    onCheckedChange = {
                        viewModel.toggle2Fa()
                        android.widget.Toast.makeText(viewModel.app, if (!settings.is2FaEnabled) "2FA Enabled" else "2FA Disabled", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("2fa_switch")
                )
            }

            // Change Password Navigation Item
            ListNavigationItem(
                title = "Change Password",
                subtitle = "Update your login password securely",
                icon = Icons.Default.Lock,
                onClick = { showChangePasswordDialog = true },
                testTag = "btn_change_password"
            )

            // Story & Live Privacy Navigation Item (Opens Full Screen)
            ListNavigationItem(
                title = "Story & Live Privacy",
                subtitle = "Hide stories and live broadcasts from specific accounts",
                icon = Icons.Default.VisibilityOff,
                onClick = { onNavigateTo(Screen.StoryLivePrivacy) },
                testTag = "btn_story_live_privacy"
            )

            // Clear Search Data Navigation Item (Opens Confirmation Dialog)
            var showClearSearchDialog by remember { mutableStateOf(false) }
            if (showClearSearchDialog) {
                AlertDialog(
                    onDismissRequest = { showClearSearchDialog = false },
                    title = { Text("Clear Search History") },
                    text = { Text("Are you sure you want to clear your search history?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearSearchHistory()
                            showClearSearchDialog = false
                            android.widget.Toast.makeText(viewModel.app, "Search History Cleared", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text("Clear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearSearchDialog = false }) { Text("Cancel") }
                    }
                )
            }
            ListNavigationItem(
                title = "Clear Search Data",
                subtitle = "Clear your recent search history and queries",
                icon = Icons.Default.History,
                onClick = { showClearSearchDialog = true },
                testTag = "btn_clear_search_data"
            )

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Navigation Links to sub lists
            ListNavigationItem(
                title = "Blocked Accounts",
                subtitle = "Manage accounts you have blocked",
                icon = Icons.Default.Block,
                onClick = { onNavigateTo(Screen.BlockedAccounts) },
                testTag = "btn_blocked_accounts"
            )

            ListNavigationItem(
                title = "Watch History",
                subtitle = "View and manage previously watched videos",
                icon = Icons.Default.History,
                onClick = { onNavigateTo(Screen.WatchHistory) },
                testTag = "btn_watch_history"
            )

            ListNavigationItem(
                title = "Login Activity logs",
                subtitle = "Review and log out active device sessions",
                icon = Icons.Default.Devices,
                onClick = { onNavigateTo(Screen.LoginActivity) },
                testTag = "btn_login_activity"
            )
        }
    }
}

// STORY & LIVE PRIVACY FULL SCREEN PAGE
private data class FollowerUserItem(val username: String, val displayName: String, var isHidden: Boolean)

@Composable
fun StoryLivePrivacyScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allUsers by viewModel.allUsers.collectAsState()
    val currentProfile by viewModel.profileState.collectAsState()
    
    // Local state to manage selections before saving
    val hiddenUsernames = remember(currentProfile.hiddenStoryFrom) {
        mutableStateListOf<String>().apply {
            addAll(currentProfile.hiddenStoryFrom.split(",").filter { it.isNotEmpty() })
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAllUsers()
    }

    val filteredList = allUsers.filter { user ->
        // Don't show myself in the list
        user.username.lowercase() != currentProfile.username.lowercase() &&
        (user.username.contains(searchQuery, ignoreCase = true) ||
         user.name.contains(searchQuery, ignoreCase = true))
    }

    val hiddenCount = hiddenUsernames.size

    Scaffold(
        topBar = { SubScreenTopBar("Story & Live Privacy", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Hide Story & Live From",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select followers to hide all your active stories and live video broadcasts from. Selected accounts won't be notified.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (hiddenCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Currently hiding from $hiddenCount user(s)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search followers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Followers List with Checkboxes
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.username }) { user ->
                    val isChecked = hiddenUsernames.contains(user.username)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .clickable {
                                if (isChecked) {
                                    hiddenUsernames.remove(user.username)
                                } else {
                                    hiddenUsernames.add(user.username)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (user.profilePic.startsWith("http")) {
                                    AsyncImage(
                                        model = user.profilePic,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = user.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = user.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "@${user.username}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!hiddenUsernames.contains(user.username)) {
                                        hiddenUsernames.add(user.username)
                                    }
                                } else {
                                    hiddenUsernames.remove(user.username)
                                }
                            }
                        )
                    }
                }
            }
            
            // Bottom Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.updateStoryPrivacy(hiddenUsernames.toList())
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done / Save Preferences", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// CLEAR SEARCH DATA FULL SCREEN PAGE
@Composable
fun ClearSearchDataScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    val recentSearches by viewModel.recentSearchesFlow.collectAsState()

    Scaffold(
        topBar = { SubScreenTopBar("Search History", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Recent Searches",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your search query log helps personalize content recommendations.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (recentSearches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "No Recent Searches",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Your search history is currently empty.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSearches, key = { it }) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = query,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.removeSearchQuery(query) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Clear All Search History Red Button
            if (recentSearches.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.clearSearchHistory()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            Text("Clear All Search History", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    }
}

// 3. BLOCKED ACCOUNTS SCREEN
@Composable
fun BlockedAccountsScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    val blockedList by viewModel.blockedAccounts.collectAsState()

    Scaffold(
        topBar = { SubScreenTopBar("Blocked Accounts", onBack) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (blockedList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😇", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Blocked Accounts", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(blockedList) { blocked ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("@${blocked.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(blocked.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }

                            Button(
                                onClick = { viewModel.unblockUser(blocked.id, blocked.username) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("unblock_${blocked.username}")
                            ) {
                                Text("Unblock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. WATCH HISTORY SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Watch History", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (watchHistory.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearWatchHistory()
                                Toast.makeText(context, "Watch History Cleared!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                "Clear", 
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (watchHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Watch History is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Videos you watch will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(watchHistory) { reel ->
                        val startColor = try { Color(android.graphics.Color.parseColor(reel.bgGradientStart)) } catch (e: Exception) { Color(0xFFFF1A75) }
                        val endColor = try { Color(android.graphics.Color.parseColor(reel.bgGradientEnd)) } catch (e: Exception) { Color(0xFF8A3AB9) }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clickable { 
                                    viewModel.navigateTo(Screen.Reels(startReelId = reel.id)) 
                                },
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(listOf(startColor, endColor)))
                            ) {
                                // Creator watermark
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = reel.creatorUsername,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = reel.caption,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "${reel.likesCount}",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                // Central play icon
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .align(Alignment.Center),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. LOGIN ACTIVITY SCREEN
@Composable
fun LoginActivityScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    val devices by viewModel.loginDevices.collectAsState()

    Scaffold(
        topBar = { SubScreenTopBar("Login Activity", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Where You're Logged In", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(device.deviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${device.location} • ${device.timeAgo}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        if (!device.deviceName.contains("This Device")) {
                            IconButton(
                                onClick = { viewModel.terminateSession(device.id, device.deviceName) },
                                modifier = Modifier.testTag("terminate_session_${device.id}")
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Terminate Session", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. CONTENT & MEDIA PREFERENCES SCREEN
@Composable
fun ContentPreferencesScreen(
    viewModel: YouSnapViewModel,
    settings: SettingsEntity,
    onBack: () -> Unit
) {
    var autoplaySetting by remember { mutableStateOf("Cellular + Wi-Fi") }
    var showAutoplayDialog by remember { mutableStateOf(false) }
    val autoplayOptions = listOf("Cellular + Wi-Fi", "Wi-Fi Only", "Never")

    var sensitiveSetting by remember { mutableStateOf("Standard") }
    var showSensitiveDialog by remember { mutableStateOf(false) }
    val sensitiveOptions = listOf("Standard (Default)", "Less Sensitive (Strict Filter)")

    val selectedContentLangs = remember { mutableStateListOf("Assamese", "English", "Hindi") }
    var showContentLangDialog by remember { mutableStateOf(false) }
    val allContentLangs = listOf(
        "Assamese",
        "English",
        "Hindi",
        "Bengali",
        "Bodo",
        "Nepali"
    )

    var showResetAlgorithmDialog by remember { mutableStateOf(false) }

    // Dialog for Autoplay Videos
    if (showAutoplayDialog) {
        AlertDialog(
            onDismissRequest = { showAutoplayDialog = false },
            icon = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Autoplay Videos", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    autoplayOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    autoplaySetting = option
                                    showAutoplayDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "Autoplay set to: $option", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (autoplaySetting == option),
                                onClick = {
                                    autoplaySetting = option
                                    showAutoplayDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "Autoplay set to: $option", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoplayDialog = false }) { Text("Close") }
            }
        )
    }

    // Dialog for Sensitive Content Control
    if (showSensitiveDialog) {
        AlertDialog(
            onDismissRequest = { showSensitiveDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sensitive Content Control", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sensitiveOptions.forEach { option ->
                        val label = if (option.contains("Standard")) "Standard" else "Less Sensitive"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    sensitiveSetting = label
                                    showSensitiveDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "Sensitive content control: $label", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (sensitiveSetting == label),
                                onClick = {
                                    sensitiveSetting = label
                                    showSensitiveDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "Sensitive content control: $label", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensitiveDialog = false }) { Text("Close") }
            }
        )
    }

    // Dialog for Multi-select Content Languages
    if (showContentLangDialog) {
        AlertDialog(
            onDismissRequest = { showContentLangDialog = false },
            icon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Content Languages", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select languages you prefer to see posts & reels in:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    allContentLangs.forEach { lang ->
                        val isSelected = selectedContentLangs.contains(lang)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isSelected) selectedContentLangs.remove(lang) else selectedContentLangs.add(lang)
                                }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) selectedContentLangs.add(lang) else selectedContentLangs.remove(lang)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lang, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showContentLangDialog = false
                    android.widget.Toast.makeText(viewModel.app, "Content preferences updated!", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            }
        )
    }

    // Confirmation AlertDialog for Reset Feed Algorithm
    if (showResetAlgorithmDialog) {
        AlertDialog(
            onDismissRequest = { showResetAlgorithmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = Color(0xFFE1306C),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Reset Feed Algorithm?", fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    text = "Are you sure you want to reset your recommendation algorithm? Your home feed, Explore page, and Reels preferences will be restored to default settings.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetAlgorithmDialog = false
                        viewModel.resetFeedAlgorithm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                ) {
                    Text("Reset Algorithm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAlgorithmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("Content & Media Preferences", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Media Uploads & Playback", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // High Quality Media Uploads Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("High Quality Media Uploads", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Always upload photos and reels in highest resolution, even on cellular.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = settings.highQualityUploads,
                    onCheckedChange = { viewModel.toggleHighQualityUploads() },
                    modifier = Modifier.testTag("hq_uploads_switch")
                )
            }

            // Autoplay Videos Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { showAutoplayDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Autoplay Videos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(autoplaySetting, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text("Content Filtering & Customization", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Sensitive Content Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { showSensitiveDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensitive Content Control", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(sensitiveSetting, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // Content Language (Multi-select)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { showContentLangDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Content Language", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = if (selectedContentLangs.isEmpty()) "None selected" else selectedContentLangs.joinToString(", "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // Reset Feed Algorithm Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE1306C).copy(alpha = 0.08f))
                    .clickable { showResetAlgorithmDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFE1306C))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Reset Feed Algorithm", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFE1306C))
                        Text("Clear personalized recommendations & refresh feed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFE1306C))
            }
        }
    }
}

// 7. CREATOR TOOLS SCREEN
@Composable
fun CreatorToolsScreen(
    viewModel: YouSnapViewModel,
    onNavigateTo: (Screen) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { SubScreenTopBar("Creator Tools", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Professional Management", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            ListNavigationItem(
                title = "Insights & Analytics",
                subtitle = "Measure reach, engagement, and impressions",
                icon = Icons.Default.BarChart,
                onClick = { onNavigateTo(Screen.InsightsScreen) },
                testTag = "btn_insights"
            )

            ListNavigationItem(
                title = "Monetization Eligibility",
                subtitle = "Check requirements for Creator Funds",
                icon = Icons.Default.MonetizationOn,
                onClick = { onNavigateTo(Screen.MonetizationScreen) },
                testTag = "btn_monetization"
            )

            ListNavigationItem(
                title = "Monetization Policy & Rules",
                subtitle = "Read terms, guidelines and monetization rules",
                icon = Icons.Default.Description,
                onClick = { onNavigateTo(Screen.MonetizationRules) },
                testTag = "btn_monetization_rules"
            )

            ListNavigationItem(
                title = "Boost Reel",
                subtitle = "Viral boost your video content",
                icon = Icons.Default.RocketLaunch,
                onClick = { onNavigateTo(Screen.BoostReel) },
                testTag = "btn_boost_reel"
            )
        }
    }
}

@Composable
fun MonetizationRulesScreen(
    onNavigateTo: (Screen) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { SubScreenTopBar("Monetization Policy & Rules", onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "YouSnap Creator Monetization Rules & Guidelines",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please read and understand the following monetization policies. Violating these rules will result in immediate demonetization or account termination.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // 1. Eligibility
            item {
                MonetizationSection("★ 1. Monetization Eligibility Rules", listOf(
                    "1. Age Requirement: Users must be at least 18 years old to apply for monetization.",
                    "2. Followers Requirement: Must have a minimum of 1,000 followers.",
                    "3. Views Requirement: Must achieve at least 500,000 qualified video views.",
                    "4. Community Guidelines: The account must not have any active community guidelines violations.",
                    "5. Application Process: Users can apply for monetization only after meeting all the specified criteria."
                ))
            }

            // 2. Earning
            item {
                MonetizationSection("★ 2. Monetization Earning Rules", listOf(
                    "1. Revenue Source: Creators will not be paid directly for raw video views; earnings will be generated through advertisements (Ads).",
                    "2. Fraud Prevention: Earnings generated from fake views, bots, and fake engagement will be canceled and forfeited.",
                    "3. Revenue Share: Creator earnings will be determined in accordance with YouSnap's official Revenue Sharing Policy."
                ))
            }

            // 3. Suspension & Violation
            item {
                Column {
                    Text("★ 3. Account Suspension & Violation Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Violating any of the following rules on YouSnap may result in video removal, account warnings, temporary suspension, or a permanent ban.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚫 Rule 1: Sexual Content & Nudity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    val rule1 = listOf(
                        "1. Uploading obscene or pornographic photos and videos is strictly prohibited.",
                        "2. Videos clearly depicting sexual acts are prohibited.",
                        "3. Publishing nude photos or videos with sexual intent is banned.",
                        "4. Sharing any individual's private or intimate photos/videos without their explicit consent is strictly forbidden.",
                        "5. Uploading child sexual abuse material (CSAM) will lead to an immediate account ban and reporting to the appropriate legal authorities.",
                        "6. Penalty: Serious violations will result in an immediate permanent ban along with legal action."
                    )
                    rule1.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚫 Rule 2: Abusive Language & Harassment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    val rule2 = listOf(
                        "1. Using extremely obscene or sexually suggestive profanity in videos, comments, or captions is prohibited.",
                        "2. Insulting, harassing, or threatening other users with abusive language is forbidden.",
                        "3. Repeatedly targeting any individual with abuse or bullying is strictly banned.",
                        "4. Hate speech based on race, religion, gender, or identity is prohibited.",
                        "5. Penalty:\n   - First Offense: Warning or video removal.\n   - Second Offense: Temporary suspension.\n   - Repeated/Severe Violations: Permanent ban."
                    )
                    rule2.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚫 Rule 3: Fake Views & Spam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    val rule3 = listOf(
                        "1. Purchasing fake followers or fake views is prohibited.",
                        "2. Using bots to artificially increase likes, views, or comments is banned.",
                        "3. Creating spam by repeatedly uploading the exact same video is forbidden.",
                        "4. Misusing the monetization system by creating fake accounts is strictly restricted.",
                        "5. Penalty: Monetization suspension, earnings review, or full account suspension."
                    )
                    rule3.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚫 Rule 4: Copyright Violation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    val rule4 = listOf(
                        "1. Uploading someone else's video as your own is prohibited.",
                        "2. Using copyrighted movies, music, or videos without proper permission or legal exemptions is banned.",
                        "3. Stealing content from other creators to gain monetization is strictly forbidden.",
                        "4. Penalty: Video removal, copyright strikes, or account suspension for severe/repeated violations."
                    )
                    rule4.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            // Summary
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📌 General Policy Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "All YouSnap users must strictly adhere to the Community Guidelines. YouSnap takes strong action against sexual exploitation, pornography, harassment, hate speech, fraud, and other serious violations. Suspension decisions will depend on the severity of the violation, available evidence, and the user's prior infraction history.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { onNavigateTo(Screen.MonetizationScreen) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Check Eligibility Tracker 📊",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun MonetizationRuleCard(
    title: String,
    description: String,
    icon: ImageVector,
    bulletPoints: List<String>,
    isWarning: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                Color(0xFFFFECEF)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isWarning) Color(0xFFFF4D4D) else Color(0xFFE1306C),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isWarning) Color(0xFF9E1B1B) else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isWarning) Color(0xFF7A1414) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 15.sp
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            ) {
                bulletPoints.forEach { point ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isWarning) Color(0xFFFF4D4D) else Color(0xFFE1306C)
                        )
                        Text(
                            text = point,
                            fontSize = 10.sp,
                            color = if (isWarning) Color(0xFF8F1E1E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// 8. INSIGHTS & ANALYTICS SCREEN (PROFESSIONAL DASHBOARD UI)
@Composable
fun InsightsScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    var selectedTimeframe by remember { mutableStateOf("Last 30 Days") }
    val timeframes = listOf("Last 7 Days", "Last 30 Days", "Last 90 Days")

    val personalPhotos by viewModel.personalPhotosState.collectAsState()
    val reels by viewModel.reelsState.collectAsState()

    val totalContentCount = personalPhotos.size + reels.size
    val baseReached = ((totalContentCount * 150L) + if (totalContentCount > 0) 300L else 0L)
    val baseViews = ((totalContentCount * 120L) + if (totalContentCount > 0) 250L else 0L)
    val baseVisits = ((totalContentCount * 45L) + if (totalContentCount > 0) 50L else 0L)
    val baseEngaged = ((totalContentCount * 25L) + if (totalContentCount > 0) 20L else 0L)

    val multiplier = when (selectedTimeframe) {
        "Last 7 Days" -> 0.35f
        "Last 90 Days" -> 2.8f
        else -> 1.0f
    }

    val accountsReached = (baseReached * multiplier).toLong()
    val reelsViews = (baseViews * multiplier).toLong()
    val profileVisits = (baseVisits * multiplier).toLong()
    val totalInteractions = (baseEngaged * multiplier).toLong()

    val reelsPct = if (totalContentCount == 0) 0f else (reelsViews.toFloat() / (accountsReached + reelsViews).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val profilePct = if (totalContentCount == 0) 0f else (profileVisits.toFloat() / (accountsReached + profileVisits).coerceAtLeast(1L)).coerceIn(0f, 1f)

    val reelsSubtitle = if (totalContentCount == 0 || reelsViews == 0L) "No activity yet" else "${(reelsPct * 100).toInt()}% of total reach"
    val profileSubtitle = if (totalContentCount == 0 || profileVisits == 0L) "No activity yet" else "${(profilePct * 100).toInt()}% profile conversion"

    var selectedDay by remember { mutableStateOf("Fri") }
    val dayReachMap = if (totalContentCount == 0) {
        mapOf("Mon" to "0", "Tue" to "0", "Wed" to "0", "Thu" to "0", "Fri" to "0", "Sat" to "0", "Sun" to "0")
    } else {
        mapOf(
            "Mon" to "${totalContentCount * 35}",
            "Tue" to "${totalContentCount * 50}",
            "Wed" to "${totalContentCount * 70}",
            "Thu" to "${totalContentCount * 55}",
            "Fri" to "${totalContentCount * 95}",
            "Sat" to "${totalContentCount * 80}",
            "Sun" to "${totalContentCount * 60}"
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("Insights & Analytics", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timeframe Selector Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeframes.forEach { timeframe ->
                    val isSelected = selectedTimeframe == timeframe
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color(0xFF2563EB)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { selectedTimeframe = timeframe }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeframe,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Hero Gradient Overview Card
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E1B4B),
                                    Color(0xFF2563EB)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Accounts Reached",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.25f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+15.2% 🚀",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "%,d".format(accountsReached),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Text(
                            text = "You reached ${if (selectedTimeframe == "Last 7 Days") "35% more" else "15.2% more"} accounts compared to previous period.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Stats Row inside Hero Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Engaged", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                Text("%,d".format(totalInteractions), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.3f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Engagement", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(if (accountsReached > 0) String.format("%.1f", (totalInteractions.toFloat() / accountsReached.toFloat()) * 100f) + "%" else "0.0%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.3f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Growth", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(if (totalContentCount > 0) "+${totalContentCount * 2.5f}%" else "0%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                            }
                        }
                    }
                }
            }

            // Weekly Reach Visual Bar Chart Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weekly Activity Trend",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedDay == "Fri") "Peak: Fri 🔥 (${dayReachMap["Fri"]})" else "$selectedDay: ${dayReachMap[selectedDay]} Reach ✨",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 7-day Bar Chart Visual
                    val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val barHeights = if (totalContentCount == 0) {
                        listOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f)
                    } else {
                        listOf(0.35f, 0.55f, 0.75f, 0.60f, 1.0f, 0.80f, 0.65f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weekDays.forEachIndexed { index, day ->
                            val heightFraction = barHeights[index]
                            val isSelected = day == selectedDay

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedDay = day
                                        android.widget.Toast.makeText(
                                            viewModel.app,
                                            "📊 $day Reach: ${dayReachMap[day]} accounts",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (isSelected) Brush.verticalGradient(
                                                listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                                            )
                                            else Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: Detailed Performance Metrics
            Text(
                text = "Performance Breakdown",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Metric 1: Reels Views
            ProfessionalMetricCard(
                title = "Reels Views",
                value = "%,d".format(reelsViews),
                subtitle = reelsSubtitle,
                percentage = reelsPct,
                icon = Icons.Default.PlayArrow,
                barColor = Color(0xFF2563EB)
            )

            // Metric 2: Profile Visits
            ProfessionalMetricCard(
                title = "Profile Visits",
                value = "%,d".format(profileVisits),
                subtitle = profileSubtitle,
                percentage = profilePct,
                icon = Icons.Default.Person,
                barColor = Color(0xFF06B6D4)
            )

            // Audience Demographics Summary
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Audience Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Top Location", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(if (totalContentCount > 0) "Active Network (100%)" else "No data yet", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Gender Split", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(if (totalContentCount > 0) "Community" else "No data yet", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Peak Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(if (totalContentCount > 0) "Real-time" else "No data yet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// Professional Styled Metric Card
@Composable
fun ProfessionalMetricCard(
    title: String,
    value: String,
    subtitle: String,
    percentage: Float,
    icon: ImageVector,
    barColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(barColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = barColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Smooth progress indicator
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
        }
    }
}

// 9. MONETIZATION SCREEN
@Composable
fun MonetizationScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val monetizationStatus by viewModel.monetizationStatus.collectAsState()

    // Dynamic interactive simulation states for requirements
    val followers by viewModel.followersCount.collectAsState()
    val videoViews by viewModel.videoViewsCount.collectAsState()
    val photoUploads by viewModel.photoUploadsCount.collectAsState()
    val storiesUploaded by viewModel.storiesUploadedCount.collectAsState()

    // Helper targets
    val targetFollowers = 1000
    val targetViews = 500000
    val targetPhotos = 10
    val targetStories = 20

    // Condition satisfaction checks
    val isFollowersMet = followers >= targetFollowers
    val isViewsMet = videoViews >= targetViews
    val isPhotosMet = photoUploads >= targetPhotos
    val isStoriesMet = storiesUploaded >= targetStories

    val allConditionsMet = isFollowersMet && isViewsMet && isPhotosMet && isStoriesMet

    Scaffold(
        topBar = { SubScreenTopBar("Monetization Eligibility", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Medal/Star status badge at the top
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF9E6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = "Monetization Status Badge",
                    tint = Color(0xFFFCAF45),
                    modifier = Modifier.size(54.dp)
                )
            }

            Text(
                text = "Monetization Status",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Status Banner matching user request
            val bannerBg = when (monetizationStatus) {
                "PENDING" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                "APPROVED" -> Color(0xFF2EB85C).copy(alpha = 0.15f)
                "REJECTED" -> Color(0xFFFF5252).copy(alpha = 0.15f)
                else -> if (allConditionsMet) Color(0xFF38EF7D).copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            }
            val bannerText = when (monetizationStatus) {
                "PENDING" -> "Monetization Application Status: PENDING ⏳ (Under Review)"
                "APPROVED" -> "Monetization Status: APPROVED ✅ (Earnings Active)"
                "REJECTED" -> "Monetization Status: REJECTED ❌ (Review requirements & Re-apply)"
                else -> if (allConditionsMet) "Eligible for Creator Fund & Gifts 🌟" else "Progressing towards Creator Fund 🚀"
            }
            val bannerColor = when (monetizationStatus) {
                "PENDING" -> Color(0xFFF57C00)
                "APPROVED" -> Color(0xFF2EB85C)
                "REJECTED" -> Color(0xFFFF5252)
                else -> if (allConditionsMet) Color(0xFF2EB85C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bannerBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bannerText,
                    color = bannerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Admin Panel Simulator Controls for real-time testing
            if (monetizationStatus == "PENDING") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Admin Panel Simulator (Test Real-time Approval/Rejection):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.updateMonetizationStatus("APPROVED") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EB85C)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simulate Approve", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.updateMonetizationStatus("REJECTED") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simulate Reject", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section Title: YouTube Studio Requirements Style
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Eligibility Requirements",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Helper action to quickly fill everything for testing
                Text(
                    text = "Auto-Fill ⚡",
                    color = Color(0xFFE1306C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            // Simulation: This would be connected to real data flow in production
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Metric Tracker 1: Followers
            EligibilityRequirementCard(
                title = "Followers",
                requirementText = "1,000 Followers",
                currentValue = followers,
                targetValue = targetFollowers,
                valueDisplay = "$followers / $targetFollowers",
                isMet = isFollowersMet,
                icon = Icons.Default.People
            )

            // Metric Tracker 2: Short Video Views
            val formattedCurrentViews = if (videoViews >= 1000) "${videoViews / 1000}K" else videoViews.toString()
            EligibilityRequirementCard(
                title = "Short Video Views",
                requirementText = "500K Views",
                currentValue = videoViews,
                targetValue = targetViews,
                valueDisplay = "$formattedCurrentViews / 500K",
                isMet = isViewsMet,
                icon = Icons.Default.PlayArrow
            )

            // Metric Tracker 3: Photo Uploads
            EligibilityRequirementCard(
                title = "Photo Uploads",
                requirementText = "Minimum 10 Photos",
                currentValue = photoUploads,
                targetValue = targetPhotos,
                valueDisplay = "$photoUploads / $targetPhotos",
                isMet = isPhotosMet,
                icon = Icons.Default.Photo
            )

            // Metric Tracker 4: Stories Uploaded
            EligibilityRequirementCard(
                title = "Stories Uploaded",
                requirementText = "Minimum 20 Stories",
                currentValue = storiesUploaded,
                targetValue = targetStories,
                valueDisplay = "$storiesUploaded / $targetStories",
                isMet = isStoriesMet,
                icon = Icons.Default.AddCircle
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Info text
            Text(
                text = "Keep posting beautiful visual content, vertical short clips, and engaging stories to satisfy all parameters!",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Apply Now Button
            val canApply = allConditionsMet && (monetizationStatus == "NONE" || monetizationStatus == "REJECTED")
            Button(
                onClick = {
                    if (canApply) {
                        viewModel.navigateTo(Screen.MonetizationForm)
                    }
                },
                enabled = canApply,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE1306C), // Pink/Red primary theme
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_now_button")
            ) {
                Text(
                    text = when (monetizationStatus) {
                        "PENDING" -> "Application Under Review (Pending) ⏳"
                        "APPROVED" -> "Monetization Active & Earning 💰"
                        "REJECTED" -> "Re-Apply Now ✨"
                        else -> if (allConditionsMet) "Apply Now ✨" else "Apply Now (Requirements Pending)"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// MONETIZATION APPLICATION FORM SCREEN
@Composable
fun MonetizationFormScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var villageAddress by remember { mutableStateOf("") }
    
    // Dynamic Payout Selection: "UPI" or "BANK"
    var payoutMethod by remember { mutableStateOf("UPI") }
    var upiId by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    
    var isDeclarationAccepted by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2EB85C),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Application Submitted!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Thank you, $fullName. Your monetization application and $payoutMethod payout details have been successfully submitted. Our Creator Verification Team will review your application within 2-3 business days.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("Monetization Details Form", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE1306C).copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFE1306C),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Creator Monetization Form",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Please fill in your personal details & payout method for creator verification.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Section 1: Personal Information
            Text(
                text = "1. Personal Information",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFE1306C)
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                placeholder = { Text("Enter your legal full name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("full_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 3) age = it },
                    label = { Text("Age") },
                    placeholder = { Text("e.g. 24") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("age_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = { Text("Father's Name") },
                    placeholder = { Text("Father's full name") },
                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f).testTag("father_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = villageAddress,
                onValueChange = { villageAddress = it },
                label = { Text("Village / Address") },
                placeholder = { Text("Enter your village/town & full address") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("village_address_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Section 2: Payout Details (Dynamic UPI vs Bank)
            Text(
                text = "2. Payout Details",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFE1306C)
            )

            // Payout Method Selector Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // UPI ID Option Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { payoutMethod = "UPI" }
                        .testTag("payout_method_upi"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (payoutMethod == "UPI") Color(0xFFE1306C).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(
                        width = if (payoutMethod == "UPI") 2.dp else 1.dp,
                        color = if (payoutMethod == "UPI") Color(0xFFE1306C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        RadioButton(
                            selected = payoutMethod == "UPI",
                            onClick = { payoutMethod = "UPI" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE1306C))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = if (payoutMethod == "UPI") Color(0xFFE1306C) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPI ID",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (payoutMethod == "UPI") Color(0xFFE1306C) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Bank Account Option Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { payoutMethod = "BANK" }
                        .testTag("payout_method_bank"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (payoutMethod == "BANK") Color(0xFFE1306C).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(
                        width = if (payoutMethod == "BANK") 2.dp else 1.dp,
                        color = if (payoutMethod == "BANK") Color(0xFFE1306C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        RadioButton(
                            selected = payoutMethod == "BANK",
                            onClick = { payoutMethod = "BANK" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE1306C))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = if (payoutMethod == "BANK") Color(0xFFE1306C) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bank Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (payoutMethod == "BANK") Color(0xFFE1306C) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Dynamic Inputs based on Payout Method
            if (payoutMethod == "UPI") {
                // Single UPI ID Input Field
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it.trim() },
                        label = { Text("UPI ID (VPA)") },
                        placeholder = { Text("e.g. username@okhdfcbank, name@upi") },
                        leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upi_id_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFE1306C),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Monthly creator earnings will be directly transferred to your verified UPI ID.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            } else {
                // Bank Account Input Fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        placeholder = { Text("e.g. State Bank of India, HDFC, Axis") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bank_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = accountHolderName,
                        onValueChange = { accountHolderName = it },
                        label = { Text("Account Holder Name") },
                        placeholder = { Text("Name as printed on bank passbook") },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_holder_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        placeholder = { Text("Enter bank account number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_number_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it.uppercase().trim() },
                        label = { Text("IFSC Code") },
                        placeholder = { Text("e.g. SBIN0001234") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ifsc_code_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Declaration Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDeclarationAccepted = !isDeclarationAccepted }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDeclarationAccepted,
                    onCheckedChange = { isDeclarationAccepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE1306C))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I hereby declare that all personal & payout details provided above are true, accurate and belong to me.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            // Submit Button with Dynamic Form Validation
            val isPayoutValid = if (payoutMethod == "UPI") {
                upiId.isNotBlank() && upiId.contains("@")
            } else {
                bankName.isNotBlank() && accountHolderName.isNotBlank() && accountNumber.isNotBlank() && ifscCode.isNotBlank()
            }

            val isFormValid = fullName.isNotBlank() &&
                    age.isNotBlank() &&
                    fatherName.isNotBlank() &&
                    villageAddress.isNotBlank() &&
                    isPayoutValid &&
                    isDeclarationAccepted

            Button(
                onClick = {
                    if (isFormValid) {
                        viewModel.submitMonetizationApplication(
                            fullName = fullName,
                            age = age,
                            fatherName = fatherName,
                            villageAddress = villageAddress,
                            payoutMethod = payoutMethod,
                            upiId = if (payoutMethod == "UPI") upiId else "",
                            bankName = if (payoutMethod == "BANK") bankName else "",
                            accountHolderName = if (payoutMethod == "BANK") accountHolderName else "",
                            accountNumber = if (payoutMethod == "BANK") accountNumber else "",
                            ifscCode = if (payoutMethod == "BANK") ifscCode else ""
                        )
                        showSuccessDialog = true
                    } else {
                        val errorMsg = if (!isPayoutValid) {
                            if (payoutMethod == "UPI") "Please enter a valid UPI ID (e.g. user@upi)" else "Please fill all bank details."
                        } else {
                            "Please fill all fields and accept the declaration."
                        }
                        android.widget.Toast.makeText(
                            viewModel.app,
                            errorMsg,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE1306C),
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_monetization_form_button")
            ) {
                Text(
                    text = "Submit Monetization Application 🚀",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Highly stylized card representing individual eligibility conditions with interactive controls
@Composable
fun EligibilityRequirementCard(
    title: String,
    requirementText: String,
    currentValue: Int,
    targetValue: Int,
    valueDisplay: String,
    isMet: Boolean,
    icon: ImageVector
) {
    val progressFraction = remember(currentValue, targetValue) {
        (currentValue.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Title block and status badge / simulation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isMet) Color(0xFF2EB85C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = requirementText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Value tracking and status text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isMet) Color(0xFF2EB85C) else Color(0xFFFF5252)
                )

                if (isMet) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF2EB85C),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Completed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2EB85C)
                        )
                    }
                } else {
                    val remainingPercent = ((1f - progressFraction) * 100).toInt()
                    Text(
                        text = "$remainingPercent% left",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252).copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isMet) Color(0xFF2EB85C) else Color(0xFFFF5252), // Pink/red for in-progress, Green for done
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
        }
    }
}


// 10. NOTIFICATION SETTINGS SCREEN
@Composable
fun NotificationSettingsScreen(
    viewModel: YouSnapViewModel,
    settings: SettingsEntity,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { SubScreenTopBar("Notifications Settings", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("App Alerts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Push Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Receive push notifications on likes, followers, and comments.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = settings.pushNotificationsEnabled,
                    onCheckedChange = { viewModel.togglePushNotifications() },
                    modifier = Modifier.testTag("push_notif_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pause All (Quiet Mode)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Temporarily silence all alerts for focus time.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = settings.quietModeEnabled,
                    onCheckedChange = { viewModel.toggleQuietMode() },
                    modifier = Modifier.testTag("quiet_mode_switch")
                )
            }
        }
    }
}

// 11. APP PREFERENCES & ACCESSIBILITY SCREEN
@Composable
fun AppPreferencesScreen(
    viewModel: YouSnapViewModel,
    settings: SettingsEntity,
    onBack: () -> Unit
) {
    // Interactive states
    var selectedLanguage by remember { mutableStateOf("Assamese") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val availableLanguages = listOf(
        "English (US)",
        "Assamese",
        "Hindi",
        "Bengali",
        "Bodo",
        "Nepali"
    )

    var selectedTheme by remember { mutableStateOf("Dark (Signature)") }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themes = listOf("System Default", "Dark (Signature)", "Light")

    val fontScale by viewModel.fontScaleFlow.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackFlow.collectAsState()
    var reduceMotionEnabled by remember { mutableStateOf(false) }
    var orientationLockEnabled by remember { mutableStateOf(true) }
    var captionsEnabled by remember { mutableStateOf(settings.captionsToggle) }

    // Dialog for App Language
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Select App Language", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableLanguages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedLanguage = language
                                    showLanguageDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "App language changed to $language", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (language == selectedLanguage),
                                onClick = {
                                    selectedLanguage = language
                                    showLanguageDialog = false
                                    android.widget.Toast.makeText(viewModel.app, "App language changed to $language", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = language, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog for App Theme
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("App Theme", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    themes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedTheme = theme
                                    showThemeDialog = false
                                    if (theme.contains("Dark")) {
                                        if (!settings.forceDarkMode) viewModel.toggleDarkMode()
                                    }
                                    android.widget.Toast.makeText(viewModel.app, "Theme set to $theme", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (theme == selectedTheme),
                                onClick = {
                                    selectedTheme = theme
                                    showThemeDialog = false
                                    if (theme.contains("Dark")) {
                                        if (!settings.forceDarkMode) viewModel.toggleDarkMode()
                                    }
                                    android.widget.Toast.makeText(viewModel.app, "Theme set to $theme", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = theme, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("App Preferences & Accessibility", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("General Preferences", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // App Language Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { showLanguageDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("App Language", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(selectedLanguage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // App Theme Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { showThemeDialog = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("App Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(selectedTheme, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text("Text & Font Scaling", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Text Size & Font Scaling Slider Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Text Size & Font Scaling", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = when {
                                fontScale <= 0.85f -> "Small (0.85x)"
                                fontScale <= 1.05f -> "Default (1.0x)"
                                fontScale <= 1.2f -> "Large (1.15x)"
                                else -> "Extra Large (1.3x)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sample: YouSnap feeds and reels look great at this text size! ✨",
                            fontSize = (13 * fontScale).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.85f..1.30f,
                        steps = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("A", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("A", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text("Accessibility & Controls", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Haptic Feedback Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Vibrate on button clicks, likes, and gesture actions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = { viewModel.toggleHapticFeedback() },
                    modifier = Modifier.testTag("haptic_feedback_switch")
                )
            }

            // Reduce Motion Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reduce Motion", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Minimize UI animations and visual transitions for screen sensitivity.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = reduceMotionEnabled,
                    onCheckedChange = {
                        reduceMotionEnabled = it
                        android.widget.Toast.makeText(viewModel.app, if (it) "Reduce motion enabled" else "Reduce motion disabled", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("reduce_motion_switch")
                )
            }

            // Captions & Subtitles Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Captions & Subtitles", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Show automatically generated video captions on reels and stories.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = captionsEnabled,
                    onCheckedChange = {
                        captionsEnabled = it
                        viewModel.toggleCaptions()
                    },
                    modifier = Modifier.testTag("captions_switch")
                )
            }

            // Screen Orientation Lock Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Screen Orientation Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Lock application layout strictly to portrait orientation.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = orientationLockEnabled,
                    onCheckedChange = {
                        orientationLockEnabled = it
                        android.widget.Toast.makeText(viewModel.app, if (it) "Orientation locked to portrait" else "Orientation auto-rotate enabled", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("orientation_lock_switch")
                )
            }
        }
    }
}

// 12. DATA & STORAGE SCREEN
@Composable
fun DataStorageScreen(
    viewModel: YouSnapViewModel,
    settings: SettingsEntity,
    onBack: () -> Unit
) {
    var dataSaverEnabled by remember { mutableStateOf(settings.dataSaverMode) }
    var downloadWifiOnly by remember { mutableStateOf(true) }
    var autoSaveGallery by remember { mutableStateOf(false) }

    var cacheSizeMb by remember { mutableFloatStateOf(45.2f) }

    Scaffold(
        topBar = { SubScreenTopBar("Data & Storage", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Cellular & Data Usage", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Data Saver Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Data Saver Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Reduces video streaming resolution to conserve cellular data.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = dataSaverEnabled,
                    onCheckedChange = {
                        dataSaverEnabled = it
                        viewModel.toggleDataSaver()
                    },
                    modifier = Modifier.testTag("data_saver_switch")
                )
            }

            // Download Over Wi-Fi Only Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Over Wi-Fi Only", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Download high-res media, posts, and offline reels over Wi-Fi networks only.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = downloadWifiOnly,
                    onCheckedChange = {
                        downloadWifiOnly = it
                        android.widget.Toast.makeText(viewModel.app, if (it) "Download on Wi-Fi only enabled" else "Cellular downloads allowed", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("download_wifi_switch")
                )
            }

            // Auto-Save to Gallery Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Automatically save photos & reels captured in YouSnap to device gallery.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = autoSaveGallery,
                    onCheckedChange = {
                        autoSaveGallery = it
                        android.widget.Toast.makeText(viewModel.app, if (it) "Auto-save to gallery enabled" else "Auto-save disabled", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("autosave_gallery_switch")
                )
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text("Storage Usage Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Storage Breakdown Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total App Storage Used", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val totalMb = 12.4f + 48.8f + 14.1f + cacheSizeMb + 18.2f
                        Text(
                            text = "%.1f MB".format(totalMb),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Segmented Visual Storage Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Box(modifier = Modifier.weight(12.4f).fillMaxHeight().background(Color(0xFF2563EB)))
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(48.8f).fillMaxHeight().background(Color(0xFFE1306C)))
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(14.1f).fillMaxHeight().background(Color(0xFFF59E0B)))
                        Spacer(modifier = Modifier.width(2.dp))
                        if (cacheSizeMb > 0f) {
                            Box(modifier = Modifier.weight(cacheSizeMb).fillMaxHeight().background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Box(modifier = Modifier.weight(18.2f).fillMaxHeight().background(Color(0xFF8B5CF6)))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Storage Items Breakdown
                    StorageBreakdownItem("Photos", "12.4 MB", Color(0xFF2563EB), Icons.Default.Photo)
                    StorageBreakdownItem("Videos & Reels", "48.8 MB", Color(0xFFE1306C), Icons.Default.Videocam)
                    StorageBreakdownItem("Downloads", "14.1 MB", Color(0xFFF59E0B), Icons.Default.Download)
                    StorageBreakdownItem("App Cache", "%.1f MB".format(cacheSizeMb), Color(0xFF10B981), Icons.Default.DeleteSweep)
                    StorageBreakdownItem("App Data & Databases", "18.2 MB", Color(0xFF8B5CF6), Icons.Default.Storage)
                }
            }

            // Clear Cache Action Card / Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                    .clickable {
                        if (cacheSizeMb > 0f) {
                            val cleared = cacheSizeMb
                            cacheSizeMb = 0f
                            viewModel.clearAppCache()
                            android.widget.Toast.makeText(viewModel.app, "Cleared %.1f MB of app cache! 🧹".format(cleared), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(viewModel.app, "Cache is already empty! ✨", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Clear Cache", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                        Text(
                            text = if (cacheSizeMb > 0f) "Free up %.1f MB of temporary cache files".format(cacheSizeMb) else "Cache memory is completely clean",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (cacheSizeMb > 0f) "Clear (%.1f MB)".format(cacheSizeMb) else "Clean ✨",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StorageBreakdownItem(
    label: String,
    sizeText: String,
    dotColor: Color,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(imageVector = icon, contentDescription = null, tint = dotColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(text = sizeText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// 13. SUPPORT & ABOUT SCREEN
@Composable
fun SupportHelpScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    var selectedProblemType by remember { mutableStateOf("Bug Report") }
    var problemDescription by remember { mutableStateOf("") }
    val problemTypes = listOf("Bug Report", "Monetization Issue", "Privacy Breach", "Feedback", "Other")

    Scaffold(
        topBar = { SubScreenTopBar("Support & About", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Help Center & Information", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Help Center Quick Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable {
                        Toast.makeText(viewModel.app, "Loading Help Center Docs...", Toast.LENGTH_SHORT).show()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Launch, contentDescription = "Launch", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Browse Help Center Docs", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text("View FAQs, policies, and troubleshooting articles.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text("Report a Problem Form", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Select Problem Category
            Text("Problem Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            var expandedDropdown by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedProblemType,
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        IconButton(onClick = { expandedDropdown = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { expandedDropdown = true }
                )
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    problemTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedProblemType = type
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // Description input
            OutlinedTextField(
                value = problemDescription,
                onValueChange = { problemDescription = it },
                label = { Text("What went wrong? Describe the issue in detail...") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("support_description_input"),
                maxLines = 6
            )

            Button(
                onClick = {
                    if (problemDescription.trim().isEmpty()) {
                        Toast.makeText(viewModel.app, "Please describe the problem first!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.submitSupportTicket(selectedProblemType, problemDescription)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("support_submit_button")
            ) {
                Text("Submit Ticket", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Version metadata
            Text(
                text = "YouSnap Android App • Version 1.0.0 (Prototyped Compose)\nDeveloped with pure Kotlin and Room Local persistence.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// 8. BOOST REEL SCREEN
@Composable
fun BoostReelScreen(
    viewModel: YouSnapViewModel,
    onBack: () -> Unit
) {
    var userName by remember { mutableStateOf("") }
    var reelLink by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf("Basic Boost ($2)") }
    val context = LocalContext.current

    Scaffold(
        topBar = { SubScreenTopBar("Boost Your Video", onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Coming Soon Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Feature Coming Soon",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("User Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
            OutlinedTextField(
                value = reelLink,
                onValueChange = { reelLink = it },
                label = { Text("Reel/Video Link") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
            Text("Select Plan:", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = true, onClick = null)
                Text("Basic Boost ($2)")
            }

            Button(
                onClick = {
                    android.widget.Toast.makeText(
                        context,
                        "This feature is currently under development. Coming Soon!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Proceed to Payment")
            }
        }
    }
}


@Composable
fun MonetizationSection(title: String, items: List<String>) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { item ->
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
