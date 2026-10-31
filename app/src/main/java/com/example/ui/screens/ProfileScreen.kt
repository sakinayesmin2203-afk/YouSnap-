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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.layout.ContentScale
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
    // Filter reels to only show the user's uploaded ones
    val personalReels = remember(userReels, profile.username) {
        userReels.filter { it.creatorUsername == profile.username }
    }
    
    // Dynamic Personal Photos (Auto-categorized & synced with Firestore and local database)
    val personalPhotos by viewModel.personalPhotosState.collectAsState()
    
    var selectedReelForPreview by remember { mutableStateOf<ReelEntity?>(null) }
    var selectedProfileTab by remember { mutableIntStateOf(0) } // 0: Photos, 1: Reels
    var selectedPhotoForViewer by remember { mutableStateOf<FullScreenPhotoData?>(null) }
    val photoLikesMap = remember { mutableStateMapOf<Int, Int>() }
    val photoLikedStatusMap = remember { mutableStateMapOf<Int, Boolean>() }

    val followersCount by viewModel.myFollowersCount.collectAsState()
    val followingCount by viewModel.myFollowingCount.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "@${profile.username}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
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
                        StatItem(count = formatEngCount(followersCount), label = "Followers")
                        StatItem(count = formatEngCount(followingCount), label = "Following")
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

                // Category tag
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
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
                                        selectedPhotoForViewer = FullScreenPhotoData(
                                            id = photo.id,
                                            title = photo.title,
                                            imageUrl = photo.imageUrl,
                                            creatorName = profile.name.ifEmpty { profile.username },
                                            creatorUsername = profile.username,
                                            creatorAvatar = profile.profilePic,
                                            initialLikes = photo.initialLikes,
                                            initialComments = photo.initialComments,
                                            initialIsLiked = photo.initialIsLiked,
                                            timeAgo = photo.timeAgo
                                        )
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
                            items(personalReels) { reel ->
                                val startColor = remember(reel.bgGradientStart) {
                                    try { Color(android.graphics.Color.parseColor(reel.bgGradientStart)) } 
                                    catch (e: Exception) { Color(0xFFE1306C) }
                                }
                                val endColor = remember(reel.bgGradientEnd) {
                                    try { Color(android.graphics.Color.parseColor(reel.bgGradientEnd)) } 
                                    catch (e: Exception) { Color(0xFF833AB4) }
                                }

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
                                        .clickable { onNavigateTo(Screen.Reels(startReelId = reel.id)) }
                                        .testTag("profile_item_${reel.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumbnailToLoad = reel.thumbnailUrl.ifEmpty { reel.mediaUrl }
                                    if (thumbnailToLoad.isNotEmpty()) {
                                        AsyncImage(
                                            model = thumbnailToLoad,
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
                                            text = formatProfileViews(reel.viewsCount),
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
                            android.widget.Toast.makeText(
                                viewModel.app,
                                "Link copied for sharing!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Close Button
                    IconButton(
                        onClick = { selectedReelForPreview = null },
                        modifier = Modifier
                            .padding(16.dp)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .size(48.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
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

        // Full-Screen Facebook-Style Photo Viewer Dialog
        selectedPhotoForViewer?.let { activePhoto ->
            FullScreenPhotoViewer(
                photoData = activePhoto,
                onDismiss = { selectedPhotoForViewer = null },
                onLikeChanged = { photoId, liked, newCount ->
                    photoLikedStatusMap[photoId] = liked
                    photoLikesMap[photoId] = newCount
                }
            )
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

private fun formatProfileViews(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format("%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

