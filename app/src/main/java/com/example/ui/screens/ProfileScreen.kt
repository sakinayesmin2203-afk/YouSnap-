package com.example.ui.screens

import com.example.ui.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.HomePostItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ProfileEntity
import com.example.data.model.ReelEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.components.FullScreenPhotoViewer
import com.example.ui.components.ReelItem
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VideoLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: YouSnapViewModel,
    profile: ProfileEntity,
    userReels: List<ReelEntity>,
    onNavigateTo: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUid = remember(profile.email, profile.username) { viewModel.activeUserUid() }

    LaunchedEffect(Unit) {
        viewModel.refreshHomeFeed(showIndicator = false)
    }

    // Filter reels to only show the user's uploaded ones, deduplicated and chronological
    val personalReels = remember(userReels, profile.username, currentUid) {
        userReels
            .filter {
                it.creatorUsername.equals(profile.username, ignoreCase = true) ||
                    (!currentUid.isNullOrBlank() && it.creatorUid == currentUid)
            }
            .distinctBy { if (it.mediaUrl.isNotEmpty()) it.mediaUrl else "${it.timestamp}" }
            .sortedByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.timestamp }
    }
    
    // Dynamic Personal Photos (Only current user's photos, deduplicated and chronological)
    val rawPersonalPhotos by viewModel.personalPhotosState.collectAsState()
    val personalPhotos = remember(rawPersonalPhotos, profile.username, currentUid) {
        rawPersonalPhotos
            .filter {
                it.creatorUsername.equals(profile.username, ignoreCase = true) ||
                    (!currentUid.isNullOrBlank() && it.creatorUid == currentUid)
            }
            .distinctBy { if (it.imageUrl.isNotEmpty()) it.imageUrl else "${it.id}" }
            .sortedByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.id.toLongOrNull() ?: 0L }
    }
    
    var selectedReelForPreview by remember { mutableStateOf<ReelEntity?>(null) }
    var selectedProfileTab by remember { mutableIntStateOf(0) } // 0: Photos, 1: Reels
    var selectedPhotoIndexForViewer by remember { mutableStateOf<Int?>(null) }
    val photoLikesMap = remember { mutableStateMapOf<String, Int>() }
    val photoLikedStatusMap = remember { mutableStateMapOf<String, Boolean>() }
    val homePostCommentsMap by viewModel.homePostComments.collectAsState()
    val homePostSharesMap by viewModel.homePostSharesCount.collectAsState()

    val followersCount by viewModel.myFollowersCount.collectAsState()
    val followingCount by viewModel.myFollowingCount.collectAsState()
    val settings by viewModel.settingsState.collectAsState()

    var showProfileMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (settings.isPrivateAccount) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Private Account",
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "@${profile.username}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        if (settings.isPrivateAccount) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Private",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showProfileMenu = true },
                            modifier = Modifier.testTag("profile_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Profile options"
                            )
                        }
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
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
                                    showProfileMenu = false
                                    val profileUrl = viewModel.getMyProfileShareUrl()
                                    val displayName = profile.name.ifBlank { profile.username }
                                    com.example.ui.utils.DeepLinkHelper.shareLinkSheet(
                                        context = context,
                                        subject = "YouSnap Profile",
                                        text = profileUrl,
                                        chooserTitle = "Share Profile via"
                                    )
                                },
                                modifier = Modifier.testTag("profile_share_button")
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
                                    showProfileMenu = false
                                    val profileUrl = viewModel.getMyProfileShareUrl()
                                    com.example.ui.utils.DeepLinkHelper.copyLinkToClipboard(
                                        context = context,
                                        label = "Profile Link",
                                        url = profileUrl,
                                        toastMessage = "Profile link copied! 🔗"
                                    )
                                },
                                modifier = Modifier.testTag("profile_copy_link_button")
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
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.fetchAndSyncUserProfile()
                viewModel.refreshHomeFeed { isRefreshing = false }
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Profile Header Area
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Profile photo circle
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
                                if (profile.profilePic.isNotEmpty() && (profile.profilePic.startsWith("content://") || profile.profilePic.startsWith("http"))) {
                                    AsyncImage(
                                        model = profile.profilePic,
                                        contentDescription = "Profile Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = profile.name.take(1).uppercase(),
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
                            StatItem(count = (personalPhotos.size + personalReels.size).toString(), label = "Posts")
                            StatItem(count = com.example.ui.utils.FormatUtils.formatCount(followersCount), label = "Followers")
                            StatItem(count = com.example.ui.utils.FormatUtils.formatCount(followingCount), label = "Following")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bio Description details
                    Text(
                        text = profile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Category tag & Private tag
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
                                text = profile.category,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (settings.isPrivateAccount) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF2E2019).copy(alpha = 0.08f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Private Account",
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Private",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = profile.bio,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row with Edit Profile and Creator Tools shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateTo(Screen.EditProfile) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Text(
                                text = "Edit Profile",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { onNavigateTo(Screen.CreatorTools) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("creator_tools_button")
                        ) {
                            Text(
                                text = "Creator Tools 💰",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab bar visual (Photos & Reels)
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
                        // Photos Feed Grid with Full-Screen Facebook-style Viewer Click
                        if (personalPhotos.isEmpty()) {
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
                                        text = "Photos you create will show up here, synced with the database!",
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
                                    .testTag("profile_photo_grid"),
                                contentPadding = PaddingValues(1.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                verticalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                items(personalPhotos, key = { it.id }) { photo ->
                                    val currentLikes = photoLikesMap[photo.id] ?: photo.initialLikes
                                    val isLiked = photoLikedStatusMap[photo.id] ?: photo.initialIsLiked

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.DarkGray)
                                            .clickable {
                                                val clickedIndex = personalPhotos.indexOf(photo)
                                                if (clickedIndex >= 0) {
                                                    selectedPhotoIndexForViewer = clickedIndex
                                                }
                                            }
                                            .testTag("my_profile_photo_${photo.id}")
                                    ) {
                                        AsyncImage(
                                            model = photo.imageUrl,
                                            contentDescription = photo.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Subtle like counter indicator overlay
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
                        // Reels feed grid
                        if (personalReels.isEmpty()) {
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
                                        text = "Reels you create will show up here, synced with the database!",
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
                                    .testTag("profile_grid"),
                                contentPadding = PaddingValues(1.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                items(
                                    items = personalReels,
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
                                            .clickable { onNavigateTo(Screen.Reels(startReelId = reel.id)) }
                                            .testTag("profile_item_${reel.id}"),
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

                                        // Dark gradient overlay for view count readability
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

                                        // View Count Overlay with Play icon
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

        // Expanded video view dialog when clicking a profile tile
        selectedReelForPreview?.let { previewReel ->
            var activePreviewReel by remember { mutableStateOf(previewReel) }
            val currentReels by viewModel.reelsState.collectAsState()
            LaunchedEffect(currentReels) {
                currentReels.firstOrNull { it.id == previewReel.id }?.let {
                    activePreviewReel = it
                }
            }

            Dialog(
                onDismissRequest = { selectedReelForPreview = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    var showPreviewComments by remember { mutableStateOf(false) }

                    ReelItem(
                        reel = activePreviewReel,
                        onLikeToggle = { viewModel.toggleLikeReel(activePreviewReel) },
                        onCommentClick = { showPreviewComments = true },
                        onFollowToggle = { viewModel.toggleFollowCreator(activePreviewReel) },
                        onShareClick = {
                            val reelUrl = viewModel.getReelShareUrl(activePreviewReel)
                            val shared = com.example.ui.utils.DeepLinkHelper.shareLinkSheet(
                                context = context,
                                subject = "YouSnap Reel",
                                text = reelUrl,
                                chooserTitle = "Share Reel via"
                            )
                            if (!shared) {
                                com.example.ui.utils.DeepLinkHelper.copyLinkToClipboard(
                                    context = context,
                                    label = "Reel Link",
                                    url = reelUrl,
                                    toastMessage = "Reel link copied! 🔗"
                                )
                            }
                            viewModel.incrementReelShares(activePreviewReel)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top-end action buttons (Delete if owner + Close)
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .align(Alignment.TopEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (viewModel.isOwnedByCurrentUser(activePreviewReel.creatorUsername, activePreviewReel.creatorUid)) {
                            IconButton(
                                onClick = {
                                    viewModel.deleteReelVideo(activePreviewReel) {
                                        selectedReelForPreview = null
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFB3261E).copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Reel",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedReelForPreview = null },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    // Comments Sheet inside dialog
                    if (showPreviewComments) {
                        val commentsState by viewModel.activeReelComments.collectAsState()
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        
                        LaunchedEffect(Unit) {
                            viewModel.loadCommentsForReel(activePreviewReel.id)
                        }

                        CommentBottomSheet(
                            comments = commentsState,
                            commentText = viewModel.currentCommentText.value,
                            onCommentTextChange = { viewModel.currentCommentText.value = it },
                            onPostComment = { viewModel.addCommentToActiveReel() },
                            onDismissRequest = { showPreviewComments = false },
                            sheetState = sheetState
                        )
                    }
                }
            }
        }

        // Full-Screen Instagram-Style Scrollable Photo Viewer Dialog
        selectedPhotoIndexForViewer?.let { startIndex ->
            val updatedPhotos = personalPhotos.map { p ->
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
                FullScreenPhotoViewer(
                    photos = updatedPhotos,
                    initialIndex = startIndex.coerceIn(0, updatedPhotos.lastIndex),
                    currentUsername = profile.username,
                    isOwner = true,
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
                    onDeletePhoto = { photoToDelete ->
                        viewModel.deletePhotoPost(
                            postId = photoToDelete.firestoreDocId ?: photoToDelete.id.toString(),
                            imageUrl = photoToDelete.imageUrl,
                            creatorUsername = photoToDelete.creatorUsername,
                            creatorUid = photoToDelete.creatorUid,
                            onSuccess = {}
                        )
                    }
                )
            }
        }
    }
}



@Composable
fun StatItem(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = count,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

