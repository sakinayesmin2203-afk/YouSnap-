package com.example.ui.screens

import com.example.ui.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.CdnVideoPlayer
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.FullScreenNativeVideoAd
import com.example.ui.components.ReelsFeedItem
import com.example.ui.components.buildReelsFeedWithAds
import kotlinx.coroutines.delay

private fun formatViews(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format("%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReelsScreen(
    viewModel: YouSnapViewModel,
    reels: List<ReelEntity>,
    onNavigateBack: () -> Unit,
    onNavigateTo: ((Screen) -> Unit)? = null,
    startReelId: Int? = null,
    modifier: Modifier = Modifier
) {
    // Standard layout parameters
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val currentUserProfile by viewModel.profileState.collectAsState()
    
    val listState = rememberLazyListState()
    var selectedReelForComments by remember { mutableStateOf<ReelEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Build feed with a full-screen Native Ad after every 6 video reels
    val feedItems = remember(reels) {
        buildReelsFeedWithAds(reels, adInterval = 6)
    }

    // Auto-scroll to startReelId if specified
    LaunchedEffect(startReelId, feedItems) {
        if (startReelId != null && feedItems.isNotEmpty()) {
            val targetIndex = feedItems.indexOfFirst { it is ReelsFeedItem.Video && it.reel.id == startReelId }
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Automatically log viewed reels into Watch History
    val visibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(visibleItemIndex, feedItems) {
        if (feedItems.isNotEmpty() && visibleItemIndex in feedItems.indices) {
            val currentItem = feedItems[visibleItemIndex]
            if (currentItem is ReelsFeedItem.Video) {
                viewModel.addToWatchHistory(currentItem.reel)
            }
        }
    }

    // Load comments when a reel is clicked
    LaunchedEffect(selectedReelForComments) {
        selectedReelForComments?.let {
            viewModel.loadCommentsForReel(it.id)
        }
    }

    val isReelsRefreshing by viewModel.isReelsRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isReelsRefreshing,
        onRefresh = { viewModel.refreshReelsFeed() },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (reels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("🎬", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Reels Yet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Be the first creator to upload a real video on YouSnap!",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { onNavigateTo?.invoke(Screen.Create) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "+ Upload First Reel",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                // Immersive scrollable Reels feed where each item fills the screen viewport
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reels_vertical_feed")
                ) {
                    itemsIndexed(
                        feedItems,
                        key = { _, item ->
                            when (item) {
                                is ReelsFeedItem.Video -> "reel_${item.reel.id}"
                                is ReelsFeedItem.Ad -> "ad_${item.adSlotId}"
                            }
                        }
                    ) { index, item ->
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .background(Color.Black)
                        ) {
                            when (item) {
                                is ReelsFeedItem.Video -> {
                                    ReelViewportItem(
                                        reel = item.reel,
                                        viewModel = viewModel,
                                        onLikeToggle = { viewModel.toggleLikeReel(item.reel) },
                                        onCommentClick = { selectedReelForComments = item.reel },
                                        onFollowToggle = { viewModel.toggleFollowCreator(item.reel) },
                                        onNavigateTo = onNavigateTo
                                    )
                                }
                                is ReelsFeedItem.Ad -> {
                                    FullScreenNativeVideoAd(
                                        adUnitId = item.adUnitId,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // Overlay Header (displays back button and "Reels" title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Reels",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Comments Bottom Sheet overlay
        selectedReelForComments?.let { activeReel ->
            val commentsState by viewModel.activeReelComments.collectAsState()
            CommentBottomSheet(
                comments = commentsState,
                commentText = viewModel.currentCommentText.value,
                onCommentTextChange = { viewModel.currentCommentText.value = it },
                onPostComment = { viewModel.addCommentToActiveReel() },
                onDismissRequest = { selectedReelForComments = null },
                sheetState = sheetState
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelViewportItem(
    reel: ReelEntity,
    viewModel: YouSnapViewModel,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onNavigateTo: ((Screen) -> Unit)? = null
) {
    val currentUserProfile by viewModel.profileState.collectAsState()

    // Generate beautiful immersive gradients representing full-screen videos
    val startColor = remember(reel.bgGradientStart) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientStart)) }
        catch (e: Exception) { Color(0xFFFD1D1D) }
    }
    val endColor = remember(reel.bgGradientEnd) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientEnd)) }
        catch (e: Exception) { Color(0xFFFCAF45) }
    }

    // Double-tap to like visual detection
    var showHeartOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("Inappropriate Content") }
    var reportDetails by remember { mutableStateOf("") }
    val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!reel.isLiked) {
                            onLikeToggle()
                        }
                        showHeartOverlay = true
                    }
                )
            }
    ) {
        // Track Watch Time: Increment every 3 seconds while this Composable is active
        LaunchedEffect(reel.id) {
            while (true) {
                delay(3000)
                viewModel.updateReelWatchTime(reel, 3)
            }
        }

        // ACTUAL VIDEO PLAYER
        if (reel.mediaUrl.isNotEmpty()) {
            CdnVideoPlayer(
                videoUrl = reel.mediaUrl,
                modifier = Modifier.fillMaxSize(),
                autoPlay = true,
                isLooping = true
            )
        } else if (reel.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Heart Overlay Animation for double tap
        androidx.compose.animation.AnimatedVisibility(
            visible = showHeartOverlay,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.4f),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showHeartOverlay) {
                if (showHeartOverlay) {
                    delay(600)
                    showHeartOverlay = false
                }
            }
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = Color.White,
                modifier = Modifier.size(100.dp)
            )
        }

        // Bottom and Left Info Overlay (Username, follow button, caption, music label)
        val isCurrentUser = (reel.creatorUsername == currentUserProfile.username || reel.creatorUsername == "sakinay_esmin" || reel.creatorUsername == "Sakinay Esmin")
        val displayedUsername = if (isCurrentUser) currentUserProfile.username else reel.creatorUsername
        val displayedProfilePic = if (isCurrentUser) currentUserProfile.profilePic else ""
        val displayedName = if (isCurrentUser) currentUserProfile.name else reel.creatorUsername

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.82f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { 
                        if (isCurrentUser) {
                            onNavigateTo?.invoke(Screen.Profile)
                        } else {
                            onNavigateTo?.invoke(Screen.OtherProfile(reel.creatorUsername))
                        }
                    }
                    .padding(4.dp)
            ) {
                // Profile Circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentUser && displayedProfilePic.isNotEmpty() && (displayedProfilePic.startsWith("content://") || displayedProfilePic.startsWith("http"))) {
                        AsyncImage(
                            model = displayedProfilePic,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = displayedName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = displayedUsername,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )


                if (!isCurrentUser) {
                    Spacer(modifier = Modifier.width(10.dp))

                    // Follow Pill
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .clickable { onFollowToggle() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (reel.isFollowingCreator) "Following" else "Follow",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Views count row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Views",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${formatViews(reel.viewsCount)} views",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Caption Text
            Text(
                text = reel.caption,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Music Audio details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music icon",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = reel.audioTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right side vertical action icons bar (Like, Comment, Share, Bookmark, Music record)
        val context = LocalContext.current
        val bookmarkedIds by viewModel.bookmarkedReelIds.collectAsState()
        val isBookmarked = bookmarkedIds.contains(reel.id)

        // Continuous spinning animation for the audio vinyl disc
        val infiniteTransition = rememberInfiniteTransition(label = "VinylDiscRotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Thumbs Up Like icon
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (reel.isLiked) Color(0xFF3B5998) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = reel.likesCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Chat Comment icon
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = "Comment",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = reel.commentsCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Share icon (Triggers real system share sheet - YouTube Style)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        try {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_SUBJECT, 
                                    "Check out @${reel.creatorUsername}'s Reel on YouSnap!"
                                )
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT, 
                                    "🎥 Watch this amazing Reel by @${reel.creatorUsername} on YouSnap!\n\n\"${reel.caption}\"\n\nWatch here: https://yousnap.app/reel/${reel.id}"
                                )
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(shareIntent, "Share Reel via")
                            )
                            viewModel.incrementReelShares(reel)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Unable to open Share sheet",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Share",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bookmark icon
            IconButton(
                onClick = {
                    viewModel.toggleBookmarkReel(reel.id)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isBookmarked) Color(0xFFFCAF45) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // More Options (3-dot) icon
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    val isReelOwner = currentUserProfile.username.lowercase() == reel.creatorUsername.lowercase() ||
                                      (currentUserProfile.username.lowercase() == "sakinay_esmin" && reel.creatorUsername.lowercase() == "sakinay_esmin")

                    if (isReelOwner) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Delete Reel", 
                                    color = Color.Red, 
                                    fontWeight = FontWeight.Bold
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                viewModel.deleteReelVideo(reel) {
                                    viewModel.navigateBack()
                                }
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report Reel") },
                            onClick = {
                                showMoreMenu = false
                                showReportDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.MoreVert, contentDescription = "Report")
                            }
                        )
                    }
                }
            }

            // Spinning Vinyl audio thumbnail disc simulation
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .border(1.dp, Color.White, CircleShape)
                    .graphicsLayer { rotationZ = rotation }
                    .clickable {
                        Toast.makeText(
                            context,
                            "🎵 Now playing: ${reel.audioTitle}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 8.sp
                    )
                }
            }
        }

        if (showReportDialog) {
            val reasons = listOf("Inappropriate Content", "Spam", "Hate Speech", "Violence", "False Information", "Harassment")
            ModalBottomSheet(
                onDismissRequest = { showReportDialog = false },
                sheetState = reportSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Report Reel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please select a reason and provide additional details so we can investigate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = reason, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        label = { Text("Write report details / reason...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showReportDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                showReportDialog = false
                                viewModel.reportReel(reel.id, selectedReason, reportDetails)
                                reportDetails = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Submit Report")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
