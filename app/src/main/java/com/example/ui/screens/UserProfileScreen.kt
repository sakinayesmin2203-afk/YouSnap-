package com.example.ui.screens

import com.example.ui.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import coil.compose.AsyncImage
import com.example.data.model.HomePostItem
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.components.FullScreenPhotoViewer
import com.example.ui.components.ReportPostBottomSheet

private val WarmCreamBackground = Color(0xFFFAF6EE)
private val WarmCreamTextPrimary = Color(0xFF2E2019)
private val WarmCreamTextSecondary = Color(0xFF7A685D)
private val WarmCreamAccent = Color(0xFFE59424)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    viewModel: YouSnapViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit
) {
    val context = LocalContext.current
    val isFollowingMap by viewModel.otherUserIsFollowing.collectAsState()
    val followersMap by viewModel.otherUserFollowers.collectAsState()
    val allPersonalPhotos by viewModel.personalPhotosState.collectAsState()
    val allReels by viewModel.reelsState.collectAsState()
    val currentProfile by viewModel.profileState.collectAsState()

    val userPhotos = remember(allPersonalPhotos, username) {
        allPersonalPhotos.filter { it.creatorUsername.equals(username, ignoreCase = true) }
    }
    val userReels = remember(allReels, username) {
        allReels.filter { it.creatorUsername.equals(username, ignoreCase = true) }
    }

    val isFollowing = isFollowingMap[username.lowercase()] ?: false
    val followerCount = followersMap[username.lowercase()] ?: 0

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Posts, 1: Reels, 2: Tagged
    var selectedPhoto by remember { mutableStateOf<FullScreenPhotoData?>(null) }
    var likedPhotosState by remember { mutableStateOf(setOf<Int>()) }
    var photoLikesMap by remember { mutableStateOf(mapOf<Int, Int>()) }
    var photoCommentsMap by remember { mutableStateOf(mapOf<Int, List<String>>()) }

    val displayName = remember(username, currentProfile, userPhotos, userReels) {
        if (username.equals(currentProfile.username, ignoreCase = true)) {
            currentProfile.name.ifEmpty { username }
        } else {
            userPhotos.firstOrNull()?.creatorName ?: userReels.firstOrNull()?.creatorName ?: username
        }
    }

    val bioText = remember(username, currentProfile) {
        if (username.equals(currentProfile.username, ignoreCase = true)) {
            currentProfile.bio.ifEmpty { "Content Creator on YouSnap 📸" }
        } else {
            "Content Creator on YouSnap 📸"
        }
    }

    val userInitials = remember(displayName) {
        if (displayName.length >= 2) displayName.take(2).uppercase() else displayName.uppercase()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "@$username",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = WarmCreamTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("user_profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = WarmCreamTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = WarmCreamTextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(Color(0xFFFFF8F2))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Profile", color = Color(0xFF2A1810), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share",
                                    tint = Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Profile Link", "https://yousnap.app/$username")
                                    clipboard.setPrimaryClip(clip)
                                } catch (e: Exception) { }
                                Toast.makeText(context, "@$username's profile link copied!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Block User", color = Color(0xFF2A1810), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PersonRemove,
                                    contentDescription = "Block",
                                    tint = Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                Toast.makeText(context, "@$username blocked successfully!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp)

                        DropdownMenuItem(
                            text = { Text("Report Profile", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Report,
                                    contentDescription = "Report",
                                    tint = Color(0xFFB3261E)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WarmCreamBackground
                )
            )
        },
        containerColor = WarmCreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(WarmCreamBackground)
        ) {
            // Profile Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile Avatar Box
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFE59424), Color(0xFFC48B5E))
                                )
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitials,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 24.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStatColumn(count = "${userPhotos.size}", label = "Posts")
                        ProfileStatColumn(
                            count = formatEngCount(followerCount),
                            label = "Followers"
                        )
                        ProfileStatColumn(count = "0", label = "Following")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Display Name & Bio
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = WarmCreamTextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = bioText,
                    fontSize = 13.sp,
                    color = WarmCreamTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = "Website",
                        tint = WarmCreamAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "yousnap.app/$username",
                        fontSize = 12.sp,
                        color = WarmCreamAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row (Follow / Following, Share)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dynamic Follow / Following Button
                    Button(
                        onClick = {
                            viewModel.toggleFollowUser(username)
                            val currentlyFollowing = !isFollowing
                            if (currentlyFollowing) {
                                Toast.makeText(context, "Followed @$username! ✦", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Unfollowed @$username!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("other_profile_follow_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color(0xFFE8DFC8) else Color(0xFF2E2019),
                            contentColor = if (isFollowing) WarmCreamTextPrimary else Color.White
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Share Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out @$username on YouSnap! https://yousnap.app/$username")
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Profile").apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .width(44.dp)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFC48B5E)),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WarmCreamTextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Profile",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content Grid Tab Switcher Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = WarmCreamBackground,
                contentColor = WarmCreamTextPrimary,
                divider = { HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Posts Grid",
                            tint = if (selectedTab == 0) WarmCreamTextPrimary else WarmCreamTextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = "Reels",
                            tint = if (selectedTab == 1) WarmCreamTextPrimary else WarmCreamTextSecondary
                        )
                    }
                )
            }

            // Tab Content Display (3-Column Grid)
            when (selectedTab) {
                0 -> {
                    if (userPhotos.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(userPhotos, key = { it.id }) { photo ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .background(Color(0xFFE8DFC8))
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("user_photo_grid_item_${photo.id}")
                                ) {
                                    AsyncImage(
                                        model = photo.imageUrl,
                                        contentDescription = photo.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                viewModel.openPhotoOnHomeScreen(
                                                    HomePostItem(
                                                        id = "user_photo_${photo.id}",
                                                        username = username,
                                                        userDisplayName = displayName,
                                                        userAvatarUrl = "",
                                                        userInitials = displayName.take(2).uppercase(),
                                                        location = "Guwahati, Assam",
                                                        imageUrl = photo.imageUrl,
                                                        caption = photo.title,
                                                        hashtags = "#YouSnap #Assam #Memories",
                                                        likesCount = photo.initialLikes,
                                                        commentsCount = photo.initialComments,
                                                        isLiked = photo.initialIsLiked,
                                                        timeAgo = photo.timeAgo,
                                                        isFromUserProfile = true
                                                    )
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📷", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Posts Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = WarmCreamTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No photos shared by @$username yet.",
                                    fontSize = 13.sp,
                                    color = WarmCreamTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                1 -> {
                    if (userReels.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(userReels, key = { "reel_${it.id}" }) { reel ->
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
                                        .aspectRatio(0.7f)
                                        .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            onNavigateTo(Screen.Reels(startReelId = reel.id))
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Reel",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                                )
                                            )
                                            .padding(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = formatEngCount(reel.likesCount),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎬", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Reels Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = WarmCreamTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No video reels shared by @$username yet.",
                                    fontSize = 13.sp,
                                    color = WarmCreamTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Facebook-Style Fullscreen Photo Viewer
    selectedPhoto?.let { photo ->
        val isLiked = likedPhotosState.contains(photo.id)
        val currentLikes = photoLikesMap[photo.id] ?: photo.initialLikes

        FullScreenPhotoViewer(
            photoData = photo.copy(
                initialLikes = currentLikes,
                initialIsLiked = isLiked
            ),
            onDismiss = { selectedPhoto = null },
            onLikeChanged = { photoId, liked, newCount ->
                if (liked) {
                    likedPhotosState = likedPhotosState + photoId
                } else {
                    likedPhotosState = likedPhotosState - photoId
                }
                photoLikesMap = photoLikesMap + (photoId to newCount)
            }
        )
    }

    // Report Dialog
    if (showReportDialog) {
        ReportPostBottomSheet(
            postUsername = username,
            onReportSubmit = { reason, details ->
                viewModel.reportPost("user_$username", username, reason, details)
                showReportDialog = false
            },
            onDismissRequest = { showReportDialog = false }
        )
    }
}

@Composable
private fun ProfileStatColumn(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = WarmCreamTextPrimary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = WarmCreamTextSecondary
        )
    }
}
