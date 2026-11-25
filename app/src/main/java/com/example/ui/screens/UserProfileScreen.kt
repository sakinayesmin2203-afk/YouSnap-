package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.FullScreenPhotoViewer
import com.example.ui.components.ReportPostBottomSheet


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    viewModel: YouSnapViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit
) {
    val context = LocalContext.current
    val allUsers by viewModel.allUsers.collectAsState()
    val followingMap by viewModel.otherUserIsFollowing.collectAsState()
    val rawPhotos by viewModel.personalPhotosState.collectAsState()
    val rawReels by viewModel.reelsState.collectAsState()
    val currentProfile by viewModel.profileState.collectAsState()

    LaunchedEffect(username) {
        viewModel.fetchAllUsers()
        viewModel.refreshHomeFeed(showIndicator = false)
    }

    val userProfile = remember(allUsers, username) {
        allUsers.firstOrNull { it.username.equals(username, ignoreCase = true) }
    }
    val userPhotos = remember(rawPhotos, username) {
        rawPhotos
            .filter { it.creatorUsername.equals(username, ignoreCase = true) }
            .distinctBy { if (it.imageUrl.isNotEmpty()) it.imageUrl else it.id }
            .sortedByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.id.toLongOrNull() ?: 0L }
    }
    val userReels = remember(rawReels, username) {
        rawReels
            .filter { it.creatorUsername.equals(username, ignoreCase = true) }
            .distinctBy { if (it.mediaUrl.isNotEmpty()) it.mediaUrl else "${it.timestamp}" }
            .sortedByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.timestamp }
    }

    val isFollowing = followingMap.entries
        .firstOrNull { it.key.equals(username, ignoreCase = true) }?.value ?: false

    val displayName = userProfile?.name?.takeIf { it.isNotBlank() }
        ?: userPhotos.firstOrNull()?.creatorName?.takeIf { it.isNotBlank() }
        ?: userReels.firstOrNull()?.creatorName?.takeIf { it.isNotBlank() }
        ?: username
    val profilePic = com.example.ui.utils.ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
        photoURL = userProfile?.profilePic,
        profilePic = userPhotos.firstOrNull()?.creatorAvatar,
        avatar = userReels.firstOrNull()?.creatorProfilePic
    )
    val categoryLabel = userProfile?.category?.takeIf { it.isNotBlank() } ?: "Digital Creator"
    val bioText = userProfile?.bio.orEmpty()
    val followersCount = userProfile?.followersCount ?: 0
    val followingCount = userProfile?.followingCount ?: 0
    val targetUid = userProfile?.let {
        userPhotos.firstOrNull()?.creatorUid?.takeIf { uid -> uid.isNotBlank() }
            ?: userReels.firstOrNull()?.creatorUid?.takeIf { uid -> uid.isNotBlank() }
            ?: ""
    } ?: ""

    var selectedProfileTab by remember { mutableIntStateOf(0) } // 0: Photos, 1: Reels
    var selectedPhotoIndexForViewer by remember { mutableStateOf<Int?>(null) }
    val photoLikesMap = remember { mutableStateMapOf<String, Int>() }
    val photoLikedStatusMap = remember { mutableStateMapOf<String, Boolean>() }
    val homePostCommentsMap by viewModel.homePostComments.collectAsState()
    val homePostSharesMap by viewModel.homePostSharesCount.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "@$username",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("user_profile_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Share Profile",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share Profile"
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    val profileUrl = viewModel.getProfileShareUrl(username, targetUid)
                                    com.example.ui.utils.DeepLinkHelper.shareLinkSheet(
                                        context = context,
                                        subject = "YouSnap Profile",
                                        text = profileUrl,
                                        chooserTitle = "Share profile via"
                                    )
                                },
                                modifier = Modifier.testTag("menu_share_profile")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copy Profile Link",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = "Copy Profile Link"
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    val profileUrl = viewModel.getProfileShareUrl(username, targetUid)
                                    com.example.ui.utils.DeepLinkHelper.copyLinkToClipboard(
                                        context = context,
                                        label = "Profile Link",
                                        url = profileUrl,
                                        toastMessage = "Profile link copied! 🔗"
                                    )
                                },
                                modifier = Modifier.testTag("menu_copy_profile_link")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Report",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = "Report",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showReportSheet = true
                                },
                                modifier = Modifier.testTag("menu_report_profile")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.fetchAllUsers()
                viewModel.refreshHomeFeed { isRefreshing = false }
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Profile Header Area (Matches ProfileScreen layout & proportions)
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Profile photo circle with Instagram-style gradient ring
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFFCAF45), Color(0xFFF56040), Color(0xFFE1306C))
                                    )
                                )
                                .padding(2.dp),
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
                                        text = displayName.take(1).uppercase().ifBlank { "U" },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 32.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Stats row (Posts, Followers, Following)
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.weight(1f)
                        ) {
                            StatItem(count = (userPhotos.size + userReels.size).toString(), label = "Posts")
                            StatItem(count = com.example.ui.utils.FormatUtils.formatCount(followersCount), label = "Followers")
                            StatItem(count = com.example.ui.utils.FormatUtils.formatCount(followingCount), label = "Following")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display Name
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Category Badge (e.g., Digital Creator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = categoryLabel,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (bioText.isNotBlank()) {
                        Text(
                            text = bioText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single Follow / Following Button (replaces Edit Profile & Creator Tools)
                    Button(
                        onClick = { viewModel.toggleFollowUser(targetUsername = username, targetUid = targetUid) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            } else {
                                Color(0xFF0095F6)
                            },
                            contentColor = if (isFollowing) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.White
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("user_profile_follow_button")
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tab bar (Photos & Reels)
                TabRow(
                    selectedTabIndex = selectedProfileTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) }
                ) {
                    Tab(
                        selected = selectedProfileTab == 0,
                        onClick = { selectedProfileTab = 0 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Photos Grid",
                                tint = if (selectedProfileTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    )
                    Tab(
                        selected = selectedProfileTab == 1,
                        onClick = { selectedProfileTab = 1 },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                contentDescription = "Reels",
                                tint = if (selectedProfileTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    )
                }

                // Tab Content
                when (selectedProfileTab) {
                    0 -> {
                        if (userPhotos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text("📸", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No uploaded Photos",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Photos shared by @$username will show up here.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("user_profile_photo_grid"),
                                contentPadding = PaddingValues(1.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                verticalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                items(userPhotos, key = { it.id }) { photo ->
                                    val currentLikes = photoLikesMap[photo.id] ?: photo.initialLikes

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.DarkGray)
                                            .clickable {
                                                val clickedIndex = userPhotos.indexOf(photo)
                                                if (clickedIndex >= 0) {
                                                    selectedPhotoIndexForViewer = clickedIndex
                                                }
                                            }
                                            .testTag("user_profile_photo_${photo.id}")
                                    ) {
                                        AsyncImage(
                                            model = photo.imageUrl,
                                            contentDescription = photo.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomStart)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                                    )
                                                )
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "❤️ %,d".format(currentLikes),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (userReels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text("📽️", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No uploaded Reels",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Reels shared by @$username will show up here.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("user_profile_reels_grid"),
                                contentPadding = PaddingValues(1.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                items(
                                    items = userReels,
                                    key = { it.mediaUrl.ifEmpty { "${it.id}_${it.timestamp}" } }
                                ) { reel ->
                                    val localThumbExists = remember(reel.thumbnailUrl) {
                                        reel.thumbnailUrl.isNotBlank() && (
                                            (!reel.thumbnailUrl.startsWith("/") && !reel.thumbnailUrl.startsWith("file:")) ||
                                                java.io.File(reel.thumbnailUrl.removePrefix("file://")).exists()
                                        )
                                    }
                                    val thumbnailToLoad = if (localThumbExists) reel.thumbnailUrl else reel.mediaUrl
                                    val thumbModel = remember(thumbnailToLoad) {
                                        if (thumbnailToLoad.isBlank()) {
                                            null
                                        } else if (thumbnailToLoad.startsWith("/")) {
                                            java.io.File(thumbnailToLoad)
                                        } else {
                                            ImageRequest.Builder(context)
                                                .data(thumbnailToLoad)
                                                .videoFrameMillis(500L)
                                                .crossfade(true)
                                                .build()
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(9f / 16f)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFF161616))
                                            .clickable {
                                                onNavigateTo(
                                                    Screen.Reels(
                                                        startReelId = reel.id,
                                                        startMediaUrl = reel.mediaUrl
                                                    )
                                                )
                                            }
                                            .testTag("user_profile_reel_${reel.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.45f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        if (thumbModel != null) {
                                            AsyncImage(
                                                model = thumbModel,
                                                contentDescription = reel.caption,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                                        startY = 100f
                                                    )
                                                )
                                        )

                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Views",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = com.example.ui.utils.FormatUtils.formatCount(reel.viewsCount),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
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

        // Full-Screen Photo Viewer
        selectedPhotoIndexForViewer?.let { startIndex ->
            val updatedPhotos = userPhotos.map { p ->
                val cleanId = p.id.removePrefix("photo_")
                val photoKey = "photo_$cleanId"
                val currentLikes = photoLikesMap[p.id] ?: p.initialLikes
                val isLiked = photoLikedStatusMap[p.id] ?: p.initialIsLiked
                val currentShares = homePostSharesMap[photoKey] ?: homePostSharesMap[p.id] ?: p.sharesCount
                val currentComments = maxOf(
                    p.initialComments,
                    (homePostCommentsMap[photoKey] ?: homePostCommentsMap[p.id] ?: emptyList()).size
                )
                p.copy(
                    initialLikes = currentLikes,
                    initialIsLiked = isLiked,
                    initialComments = currentComments,
                    sharesCount = currentShares
                )
            }
            if (updatedPhotos.isEmpty()) {
                LaunchedEffect(Unit) { selectedPhotoIndexForViewer = null }
            } else {
                DisposableEffect(startIndex) {
                    onDispose { viewModel.clearAdCreatorContext() }
                }
                FullScreenPhotoViewer(
                    photos = updatedPhotos,
                    initialIndex = startIndex.coerceIn(0, updatedPhotos.lastIndex),
                    currentUsername = currentProfile.username,
                    isOwner = false,
                    commentsByPostId = homePostCommentsMap,
                    onDismiss = { selectedPhotoIndexForViewer = null },
                    onLikeChanged = { photoId, liked, newCount ->
                        photoLikedStatusMap[photoId] = liked
                        photoLikesMap[photoId] = newCount
                        updatedPhotos.find { it.id == photoId }?.let { photo ->
                            viewModel.notifyPhotoLikedFromViewer(photo, liked, newCount)
                        }
                    },
                    onLoadComments = { photo ->
                        viewModel.loadCommentsForHomePost(photo.id, photo.firestoreDocId)
                    },
                    onAddComment = { photo, text ->
                        viewModel.addCommentToHomePost("photo_${photo.id.removePrefix("photo_")}", text)
                    },
                    onShareChanged = { photo ->
                        viewModel.incrementPostShares(photo)
                    },
                    onReportPhoto = { photoData, reason, details ->
                        viewModel.reportPost(
                            postId = photoData.id,
                            username = photoData.creatorUsername,
                            reason = reason,
                            details = details,
                            creatorUid = photoData.creatorUid,
                            firestoreDocId = photoData.firestoreDocId.orEmpty()
                        )
                    },
                    onPhotoViewed = { viewedPhoto ->
                        viewModel.setAdCreatorContextForPhoto(viewedPhoto)
                        viewModel.logPhotoAdViewOnce(viewedPhoto)
                    }
                )
            }
        }

        // Report Profile Bottom Sheet
        if (showReportSheet) {
            ReportPostBottomSheet(
                postUsername = username,
                title = "Report Profile",
                subtitle = "Help us understand the issue with @$username's profile. Your report is anonymous and confidential.",
                onReportSubmit = { reason, details ->
                    viewModel.reportAccount(username = username, reason = reason, details = details)
                    showReportSheet = false
                },
                onDismissRequest = { showReportSheet = false }
            )
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
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Security PIN Lock",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enter Security PIN",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your 6-digit PIN to unlock YouSnap.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = pinInput,
            onValueChange = { value ->
                if (value.length <= 6 && value.all { it.isDigit() }) {
                    pinInput = value
                    errorText = null
                }
            },
            label = { Text("6-digit PIN") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            ),
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth(0.75f).testTag("pin_lock_input")
        )
        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorText.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (viewModel.verifyAppPin(pinInput)) {
                    onUnlockSuccess()
                } else {
                    errorText = "Incorrect PIN. Please try again."
                }
            },
            enabled = pinInput.length == 6,
            modifier = Modifier.fillMaxWidth(0.75f).testTag("pin_lock_unlock_button")
        ) {
            Text("Unlock")
        }
    }
}
