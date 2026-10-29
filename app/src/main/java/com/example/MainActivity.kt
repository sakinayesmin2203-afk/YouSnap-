package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProfileEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.screens.*
import com.example.ui.theme.YouSnapTheme

class MainActivity : ComponentActivity() {

    private val viewModel: YouSnapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safe Firebase Initialization to prevent any startup crash
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                try {
                    com.google.firebase.FirebaseApp.initializeApp(this)
                } catch (e: Throwable) {
                    android.util.Log.w("MainActivity", "Default FirebaseApp init skipped or failed: ${e.message}")
                }
                // If default init was not possible, initialize with explicit project options
                if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:87251634633:android:b0490cfd2b846fadc74018")
                        .setApiKey("AIzaSyC8Kf0iuclEjg2OGgn8pgx-FQESSsMwvEY")
                        .setProjectId("yousnap-app")
                        .setStorageBucket("yousnap-app.firebasestorage.app")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Firebase safe initialization handled: ${e.message}")
        }

        // Initialize Google Mobile Ads SDK (AdMob)
        try {
            val config = com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
                .build()
            com.google.android.gms.ads.MobileAds.setRequestConfiguration(config)
            com.google.android.gms.ads.MobileAds.initialize(this) { initializationStatus ->
                android.util.Log.d("MainActivity", "AdMob initialized: $initializationStatus")
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "AdMob initialization notice: ${e.message}")
        }

        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settingsState.collectAsState()
            val profile by viewModel.profileState.collectAsState()
            val reels by viewModel.reelsState.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val fontScale by viewModel.fontScaleFlow.collectAsState()

            // Set lightTheme by forcing darkTheme = false as requested by the user to make the app white
            YouSnapTheme(darkTheme = false, fontScale = fontScale) {
                // Handle android system back button within sub settings navigation or opened photo on Home
                val isPostOpenedOnHome = currentScreen is Screen.Home && viewModel.hasOpenedPostFromProfile()
                BackHandler(enabled = (currentScreen != Screen.Home && currentScreen != Screen.Login) || isPostOpenedOnHome) {
                    if (isPostOpenedOnHome) {
                        viewModel.clearOpenedHomePost()
                        val popped = viewModel.navigateBack()
                        if (!popped) {
                            // Stay on home
                        }
                    } else {
                        val popped = viewModel.navigateBack()
                        if (!popped) {
                            // If cannot pop settings stack, default back to home
                            viewModel.clearAndNavigateTo(Screen.Home)
                        }
                    }
                }

                // Determine whether to display the Bottom Navigation Bar.
                // Hide it on deep submenus of Settings to maximize screen real-estate for forms.
                val showBottomNav = remember(currentScreen) {
                    currentScreen is Screen.Home ||
                    currentScreen is Screen.Reels ||
                    currentScreen is Screen.Search ||
                    currentScreen is Screen.Create ||
                    currentScreen is Screen.Notifications ||
                    currentScreen is Screen.Profile ||
                    currentScreen is Screen.OtherProfile ||
                    currentScreen is Screen.Settings
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            YouSnapBottomNavigation(
                                currentScreen = currentScreen,
                                profile = profile,
                                onSelectScreen = { screen ->
                                    if (screen == Screen.Home) {
                                        viewModel.clearOpenedHomePost()
                                        viewModel.clearAndNavigateTo(Screen.Home)
                                    } else {
                                        viewModel.navigateTo(screen)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (val activeScreen = currentScreen) {
                            is Screen.Login -> com.example.ui.screens.LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = { viewModel.clearAndNavigateTo(Screen.Home) }
                            )

                            is Screen.SignUp -> SignUpScreen(
                                viewModel = viewModel,
                                onSignUpSuccess = { viewModel.clearAndNavigateTo(Screen.Home) }
                            )

                            is Screen.ForgotPassword -> ForgotPasswordScreen(
                                viewModel = viewModel
                            )

                            // Core Feed Screen
                            is Screen.Home -> HomeScreen(
                                viewModel = viewModel,
                                reels = reels,
                                onNavigateTo = { viewModel.navigateTo(it) }
                            )

                            // Immersive Full-Screen Reels Feed
                            is Screen.Reels -> ReelsScreen(
                                viewModel = viewModel,
                                reels = reels,
                                startReelId = activeScreen.startReelId,
                                onNavigateBack = { viewModel.navigateBack() },
                                onNavigateTo = { viewModel.navigateTo(it) }
                            )

                            // Explore / Search Screen
                            is Screen.Search -> SearchScreen(
                                viewModel = viewModel,
                                reels = reels
                            )

                            // Create / Camera Full-Screen Interface
                            is Screen.Create -> CreateScreen(
                                viewModel = viewModel,
                                onClose = { viewModel.clearAndNavigateTo(Screen.Home) }
                            )

                            // Notifications List Screen
                            is Screen.Notifications -> NotificationsScreen(
                                viewModel = viewModel
                            )

                            // Profile Hub Screen (Current User)
                            is Screen.Profile -> ProfileScreen(
                                viewModel = viewModel,
                                profile = profile,
                                userReels = reels,
                                onNavigateTo = { viewModel.navigateTo(it) }
                            )

                            // Dynamic Other User Profile Screen
                            is Screen.OtherProfile -> UserProfileScreen(
                                username = activeScreen.username,
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateBack() },
                                onNavigateTo = { viewModel.navigateTo(it) }
                            )

                            // Root Settings Screen
                            is Screen.Settings -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateTo = { viewModel.navigateTo(it) },
                                onNavigateBack = { viewModel.navigateBack() }
                            )

                            // Sub-screens of Settings
                            is Screen.EditProfile -> EditProfileScreen(
                                viewModel = viewModel,
                                profile = profile,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.PrivacySecurity -> PrivacySecurityScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onNavigateTo = { viewModel.navigateTo(it) },
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.BlockedAccounts -> BlockedAccountsScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.WatchHistory -> WatchHistoryScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.StoryLivePrivacy -> StoryLivePrivacyScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.ClearSearchData -> ClearSearchDataScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.LoginActivity -> LoginActivityScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.ContentPreferences -> ContentPreferencesScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.CreatorTools -> CreatorToolsScreen(
                                viewModel = viewModel,
                                onNavigateTo = { viewModel.navigateTo(it) },
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.BoostReel -> BoostReelScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.InsightsScreen -> InsightsScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.MonetizationScreen -> MonetizationScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.MonetizationRules -> com.example.ui.screens.MonetizationRulesScreen(
                                viewModel = viewModel,
                                onNavigateTo = { viewModel.navigateTo(it) },
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.MonetizationForm -> MonetizationFormScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.NotificationSettings -> NotificationSettingsScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.AppPreferences -> AppPreferencesScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.DataStorage -> DataStorageScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { viewModel.navigateBack() }
                            )

                            is Screen.SupportHelp -> SupportHelpScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Custom Bottom Navigation Bar mimicking Instagram's flat, ultra-clean aesthetic with requested 5 tabs
@Composable
fun InstagramReelsIcon(isSelected: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val strokeWidth = 2.dp.toPx()
        
        val rectHeight = height * 0.72f
        val rectWidth = width
        val topOffset = height * 0.22f
        
        // Main clapperboard rounded frame
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, topOffset),
            size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        // Clapperboard top division line
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(0f, topOffset + 6.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(rectWidth, topOffset + 6.dp.toPx()),
            strokeWidth = strokeWidth
        )
        
        // Standard clapperboard diagonal lines
        val slants = listOf(0.25f, 0.5f, 0.75f)
        for (slant in slants) {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(rectWidth * slant, topOffset),
                end = androidx.compose.ui.geometry.Offset(rectWidth * slant - 4.dp.toPx(), topOffset + 6.dp.toPx()),
                strokeWidth = strokeWidth
            )
        }
        
        // Center play triangle
        val path = androidx.compose.ui.graphics.Path().apply {
            val cx = width / 2f
            val cy = topOffset + (rectHeight * 0.65f)
            val r = 4.dp.toPx()
            moveTo(cx - r * 0.7f, cy - r)
            lineTo(cx + r * 1.2f, cy)
            lineTo(cx - r * 0.7f, cy + r)
            close()
        }
        
        drawPath(
            path = path,
            color = tint,
            style = if (isSelected) androidx.compose.ui.graphics.drawscope.Fill 
                    else androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun YouSnapBottomNavigation(
    currentScreen: Screen,
    profile: ProfileEntity,
    onSelectScreen: (Screen) -> Unit
) {
    val activeColor = Color(0xFF2E2019)
    val inactiveColor = Color(0xFF8E7F76)
    val highlightBg = Color(0xFFF5EBE6) // Soft Light Background Oval Container/Highlight

    Card(
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .navigationBarsPadding()
            .testTag("yousnap_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Home
            val isHomeSelected = currentScreen is Screen.Home
            NavigationCapsuleItem(
                isSelected = isHomeSelected,
                label = "Home",
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                highlightBg = highlightBg,
                icon = { tint ->
                    Icon(
                        imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onSelectScreen(Screen.Home) },
                modifier = Modifier.testTag("nav_tab_home")
            )

            // Tab 2: Reels
            val isReelsSelected = currentScreen is Screen.Reels
            NavigationCapsuleItem(
                isSelected = isReelsSelected,
                label = "Reels",
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                highlightBg = highlightBg,
                icon = { tint ->
                    InstagramReelsIcon(
                        isSelected = isReelsSelected,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onSelectScreen(Screen.Reels()) },
                modifier = Modifier.testTag("nav_tab_reels")
            )

            // Tab 3: Post
            val isCreateSelected = currentScreen is Screen.Create
            NavigationCapsuleItem(
                isSelected = isCreateSelected,
                label = "Post",
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                highlightBg = highlightBg,
                icon = { tint ->
                    Icon(
                        imageVector = if (isCreateSelected) Icons.Filled.AddBox else Icons.Outlined.AddBox,
                        contentDescription = "Post",
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onSelectScreen(Screen.Create) },
                modifier = Modifier.testTag("nav_tab_create")
            )

            // Tab 4: Alerts
            val isNotifSelected = currentScreen is Screen.Notifications
            NavigationCapsuleItem(
                isSelected = isNotifSelected,
                label = "Alerts",
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                highlightBg = highlightBg,
                icon = { tint ->
                    Icon(
                        imageVector = if (isNotifSelected) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Alerts",
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onSelectScreen(Screen.Notifications) },
                modifier = Modifier.testTag("nav_tab_notifications")
            )

            // Tab 5: Profile
            val isProfileSelected = currentScreen is Screen.Profile || currentScreen is Screen.Settings
            NavigationCapsuleItem(
                isSelected = isProfileSelected,
                label = "Profile",
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                highlightBg = highlightBg,
                icon = { tint ->
                    val profilePic = profile.profilePic
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isProfileSelected) activeColor else inactiveColor.copy(alpha = 0.25f)),
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
                                text = profile.name.take(1).uppercase(),
                                color = if (isProfileSelected) Color.White else inactiveColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                onClick = { onSelectScreen(Screen.Profile) },
                modifier = Modifier.testTag("nav_tab_profile")
            )
        }
    }
}

@Composable
fun RowScope.NavigationCapsuleItem(
    isSelected: Boolean,
    label: String,
    activeColor: Color,
    inactiveColor: Color,
    highlightBg: Color,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTint = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) highlightBg else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(currentTint)
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            color = currentTint,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
