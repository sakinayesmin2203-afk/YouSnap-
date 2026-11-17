package com.example.ui.screens

import com.example.ui.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.videoFrameMillis
import com.example.R
import com.example.data.model.HomePostItem
import com.example.data.model.ReelEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReportPostBottomSheet
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.components.FullScreenPhotoViewer
import com.example.ui.components.CdnVideoPlayer
import com.example.ui.theme.WarmCreamBackground
import com.example.ui.theme.WarmCreamTextPrimary
import com.example.ui.theme.WarmCreamTextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: YouSnapViewModel,
    reels: List<ReelEntity>,
    onNavigateTo: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedReelForComments by remember { mutableStateOf<ReelEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // State to manage story playback dialog
    var activeStoryUser by remember { mutableStateOf<String?>(null) }

    // User Story Cards - All active stories from Firestore
    val currentProfile by viewModel.profileState.collectAsState()
    val personalPhotos by viewModel.personalPhotosState.collectAsState()
    val allStories by viewModel.allStories.collectAsState()

    // Observe active post opened on Home Screen (e.g. from user profiles)
    val selectedHomePost by viewModel.selectedHomePost.collectAsState()
    val homePostCommentsMap by viewModel.homePostComments.collectAsState()
    val homePostLikedState by viewModel.homePostLikedState.collectAsState()
    val homePostLikesCount by viewModel.homePostLikesCount.collectAsState()
    val homePostBookmarked by viewModel.homePostBookmarked.collectAsState()
    val isHomeRefreshing by viewModel.isHomeRefreshing.collectAsState()
    val userPrivacyMap by viewModel.userPrivacyMap.collectAsState()
    val otherUserIsFollowing by viewModel.otherUserIsFollowing.collectAsState()

    val visiblePersonalPhotos = remember(personalPhotos, userPrivacyMap, otherUserIsFollowing, currentProfile) {
        personalPhotos.filter { photo ->
            val creator = photo.creatorUsername.lowercase()
            val isOwner = currentProfile.username.equals(creator, ignoreCase = true)
            val isPrivate = userPrivacyMap[creator] ?: false
            val isFollowing = otherUserIsFollowing[creator] ?: false
            !isPrivate || isOwner || isFollowing
        }.distinctBy { if (it.imageUrl.isNotEmpty()) it.imageUrl else "${it.id}" }
    }

    val visibleReels = remember(reels, userPrivacyMap, otherUserIsFollowing, currentProfile) {
        reels.filter { reel ->
            val creator = reel.creatorUsername.lowercase()
            val isOwner = currentProfile.username.equals(creator, ignoreCase = true)
            val isPrivate = userPrivacyMap[creator] ?: false
            val isFollowing = otherUserIsFollowing[creator] ?: false
            !isPrivate || isOwner || isFollowing
        }.distinctBy { if (it.mediaUrl.isNotEmpty()) it.mediaUrl else "${it.creatorUsername}_${it.timestamp}" }
    }

    var activePostForComments by remember { mutableStateOf<HomePostItem?>(null) }
    var activePostCommentInput by remember { mutableStateOf("") }
    var selectedPhotoForViewer by remember { mutableStateOf<FullScreenPhotoData?>(null) }
    var postToReport by remember { mutableStateOf<HomePostItem?>(null) }
    var isFeedMuted by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLifecycleResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAnyOverlayOpen = activeStoryUser != null ||
        selectedPhotoForViewer != null ||
        activePostForComments != null ||
        selectedReelForComments != null ||
        postToReport != null
    val canAutoPlayFeed = isLifecycleResumed && !isAnyOverlayOpen

    val activePlayingReelKey by remember {
        derivedStateOf {
            if (!canAutoPlayFeed) return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            layoutInfo.visibleItemsInfo
                .filter { (it.key as? String)?.startsWith("reel_") == true }
                .mapNotNull { info ->
                    val itemStart = info.offset
                    val itemEnd = info.offset + info.size
                    val visiblePx = (minOf(itemEnd, viewportEnd) - maxOf(itemStart, viewportStart)).coerceAtLeast(0)
                    if (info.size > 0 && visiblePx >= (info.size * 0.30f).toInt()) {
                        (info.key as String) to visiblePx
                    } else null
                }
                .maxByOrNull { it.second }
                ?.first
        }
    }

    // Scroll to top when a photo is opened from a user's profile
    LaunchedEffect(selectedHomePost) {
        if (selectedHomePost != null) {
            listState.animateScrollToItem(0)
        }
    }


    // Sync with Supabase Storage when Home is displayed
    LaunchedEffect(Unit) {
        viewModel.refreshHomeFeed(showIndicator = false)
    }

    // Trigger loading comments when a reel is selected
    LaunchedEffect(selectedReelForComments) {
        selectedReelForComments?.let {
            viewModel.loadCommentsForReel(it.id)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmCreamBackground) // Warm Beige / Light Cream Theme
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR: Large 'YouSnap' title on left & Search and Profile icons on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmCreamBackground)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedHomePost != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.clearOpenedHomePost()
                                val popped = viewModel.navigateBack()
                                if (!popped) {
                                    viewModel.clearAndNavigateTo(Screen.Home)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = WarmCreamTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Posts",
                            color = WarmCreamTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "YouSnap",
                        color = WarmCreamTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("yousnap_header_title")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onNavigateTo(Screen.Search) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WarmCreamTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            onNavigateTo(Screen.Settings)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("home_top_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = WarmCreamTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. CORE FEED SCROLLY (With Pull-To-Refresh)
            PullToRefreshBox(
                isRefreshing = isHomeRefreshing,
                onRefresh = { viewModel.refreshHomeFeed() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_feed_list")
                ) {
                    // Item A: Horizontal Stories Tray (Instagram Style Compact Circle)
                    val myStory = allStories.find { it.ownerUsername.lowercase() == currentProfile.username.lowercase() }
                    val otherStories = allStories.filter { it.ownerUsername.lowercase() != currentProfile.username.lowercase() }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. MY STORY / ADD STORY
                                item(key = "my_story_slot") {
                                    if (myStory != null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .clickable { activeStoryUser = currentProfile.username }
                                                .testTag("my_uploaded_story_item")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(Color(0xFFE59424), Color(0xFFF56040), Color(0xFFC48B5E))
                                                        )
                                                    )
                                                    .padding(2.5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                        .padding(2.dp)
                                                ) {
                                                    if (myStory.mediaType == "text") {
                                                        val gStart = remember(myStory.bgGradientStart) {
                                                            try { Color(android.graphics.Color.parseColor(myStory.bgGradientStart.ifBlank { "#E59424" })) }
                                                            catch (_: Exception) { Color(0xFFE59424) }
                                                        }
                                                        val gEnd = remember(myStory.bgGradientEnd) {
                                                            try { Color(android.graphics.Color.parseColor(myStory.bgGradientEnd.ifBlank { "#C48B5E" })) }
                                                            catch (_: Exception) { Color(0xFFC48B5E) }
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape)
                                                                .background(Brush.verticalGradient(listOf(gStart, gEnd))),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = myStory.caption.take(12).ifBlank { "Aa" },
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                textAlign = TextAlign.Center,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(4.dp)
                                                            )
                                                        }
                                                    } else {
                                                        val myStoryThumbModel = remember(myStory.mediaUrl, myStory.mediaType) {
                                                            if (myStory.mediaType == "video" || myStory.mediaUrl.lowercase().let {
                                                                    it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(".webm")
                                                                }
                                                            ) {
                                                                coil.request.ImageRequest.Builder(context)
                                                                    .data(myStory.mediaUrl)
                                                                    .videoFrameMillis(500L)
                                                                    .crossfade(true)
                                                                    .build()
                                                            } else {
                                                                myStory.mediaUrl
                                                            }
                                                        }
                                                        AsyncImage(
                                                            model = myStoryThumbModel,
                                                            contentDescription = "My Story",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Your story",
                                                color = WarmCreamTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        // Add Story Button
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .clickable { onNavigateTo(Screen.Create) }
                                                .testTag("add_story_button")
                                        ) {
                                            Box(
                                                modifier = Modifier.size(68.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFE8DFC8))
                                                        .border(1.5.dp, Color(0xFFC48B5E).copy(alpha = 0.4f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (currentProfile.profilePic.isNotEmpty() && (currentProfile.profilePic.startsWith("content://") || currentProfile.profilePic.startsWith("http"))) {
                                                        AsyncImage(
                                                            model = currentProfile.profilePic,
                                                            contentDescription = "Profile Photo",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Text(
                                                            text = currentProfile.name.take(1).uppercase().ifEmpty { "U" },
                                                            color = WarmCreamTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        )
                                                    }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF0095F6))
                                                        .border(2.dp, Color.White, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Add Story",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Your story",
                                                color = WarmCreamTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // 2. OTHER STORIES
                                items(otherStories, key = { it.storyId }) { story ->
                                    val hasViewed = story.viewerIds.contains(currentProfile.username)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.clickable { activeStoryUser = story.ownerUsername }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (hasViewed) {
                                                        Brush.linearGradient(listOf(Color.LightGray, Color.Gray))
                                                    } else {
                                                        Brush.linearGradient(listOf(Color(0xFFE59424), Color(0xFFF56040), Color(0xFFC48B5E)))
                                                    }
                                                )
                                                .padding(2.5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .padding(2.dp)
                                            ) {
                                                if (story.mediaType == "text") {
                                                    val gStart = remember(story.bgGradientStart) {
                                                        try { Color(android.graphics.Color.parseColor(story.bgGradientStart.ifBlank { "#E59424" })) }
                                                        catch (_: Exception) { Color(0xFFE59424) }
                                                    }
                                                    val gEnd = remember(story.bgGradientEnd) {
                                                        try { Color(android.graphics.Color.parseColor(story.bgGradientEnd.ifBlank { "#C48B5E" })) }
                                                        catch (_: Exception) { Color(0xFFC48B5E) }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(Brush.verticalGradient(listOf(gStart, gEnd))),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = story.caption.take(12).ifBlank { "Aa" },
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(4.dp)
                                                        )
                                                    }
                                                } else {
                                                    val storyThumbModel = remember(story.mediaUrl, story.mediaType) {
                                                        if (story.mediaType == "video" || story.mediaUrl.lowercase().let {
                                                                it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(".webm")
                                                            }
                                                        ) {
                                                            coil.request.ImageRequest.Builder(context)
                                                                .data(story.mediaUrl)
                                                                .videoFrameMillis(500L)
                                                                .crossfade(true)
                                                                .build()
                                                        } else {
                                                            story.mediaUrl
                                                        }
                                                    }
                                                    AsyncImage(
                                                        model = storyThumbModel,
                                                        contentDescription = "Story from ${story.ownerUsername}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = story.ownerUsername,
                                            color = WarmCreamTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (hasViewed) FontWeight.Normal else FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 64.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                // Item B1: Dynamic Profile-Opened Post (if user clicked a photo from any user's profile)
                selectedHomePost?.let { profilePost ->
                    item(key = "profile_opened_post_${profilePost.id}") {
                        val isLiked = homePostLikedState[profilePost.id] ?: profilePost.initialIsLiked
                        val likesCount = homePostLikesCount[profilePost.id] ?: profilePost.initialLikes
                        val commentsForPost = homePostCommentsMap[profilePost.id] ?: emptyList()
                        val isBookmarked = homePostBookmarked[profilePost.id] ?: false

                        val isOwner = currentProfile.username.lowercase() == profilePost.creatorUsername.lowercase()

                        val postItem = HomePostItem(
                            id = profilePost.id,
                            username = profilePost.creatorUsername,
                            userDisplayName = profilePost.creatorName,
                            userAvatarUrl = profilePost.creatorAvatar,
                            creatorUid = profilePost.creatorUid,
                            imageUrl = profilePost.imageUrl,
                            caption = profilePost.title,
                            likesCount = likesCount,
                            commentsCount = commentsForPost.size,
                            isLiked = isLiked,
                            isBookmarked = isBookmarked,
                            timeAgo = profilePost.timeAgo
                        )

                        FeedPhotoPostCard(
                            post = postItem,
                            isLiked = isLiked,
                            likesCount = likesCount,
                            commentsCount = postItem.commentsCount,
                            isBookmarked = isBookmarked,
                            onLikeToggle = { viewModel.toggleLikeHomePost(profilePost.id) },
                            onCommentClick = {
                                activePostForComments = postItem
                            },
                            onShareClick = {
                                try {
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "YouSnap Post")
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Check out @${profilePost.creatorUsername}'s photo on YouSnap! 📸✨"
                                        )
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share via").apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Link copied! 🔗", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBookmarkToggle = {
                                val saved = viewModel.toggleBookmarkHomePost(profilePost.id)
                                Toast.makeText(
                                    context,
                                    if (saved) "Post saved to bookmarks! 📌" else "Post removed from bookmarks!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onProfileClick = {
                                if (profilePost.creatorUsername == currentProfile.username) {
                                    onNavigateTo(Screen.Profile)
                                } else {
                                    onNavigateTo(Screen.OtherProfile(profilePost.creatorUsername))
                                }
                            },
                            onPhotoClick = {
                                selectedPhotoForViewer = profilePost
                            },
                            onDismissBanner = {
                                viewModel.clearOpenedHomePost()
                            },
                            isOwner = isOwner,
                            onDeleteClick = {
                                viewModel.deletePhotoPost(profilePost.id, profilePost.imageUrl, profilePost.creatorUsername, profilePost.creatorUid) {
                                    viewModel.clearOpenedHomePost()
                                }
                            },
                            onRequestReport = {
                                postToReport = postItem
                            }
                        )
                    }
                }

                // Real uploaded photo posts from users
                if (visiblePersonalPhotos.isNotEmpty()) {
                    items(visiblePersonalPhotos, key = { "personal_photo_${it.id}" }) { photo ->
                        val postId = "photo_${photo.id}"
                        val isLiked = homePostLikedState[postId] ?: photo.initialIsLiked
                        val currentLikes = homePostLikesCount[postId] ?: photo.initialLikes
                        val isPhotoOwner = viewModel.isOwnedByCurrentUser(photo.creatorUsername, photo.creatorUid)

                        val postItem = HomePostItem(
                            id = postId,
                            username = photo.creatorUsername,
                            userDisplayName = photo.creatorName,
                            userAvatarUrl = photo.creatorAvatar,
                            userInitials = photo.creatorName.take(2).uppercase().ifEmpty { "ME" },
                            creatorUid = photo.creatorUid,
                            location = "YouSnap",
                            imageUrl = photo.imageUrl,
                            caption = photo.title,
                            hashtags = "",
                            likesCount = currentLikes,
                            commentsCount = photo.initialComments,
                            isLiked = isLiked,
                            timeAgo = photo.timeAgo,
                            isFromUserProfile = true
                        )

                        FeedPhotoPostCard(
                            post = postItem,
                            isLiked = isLiked,
                            likesCount = currentLikes,
                            commentsCount = photo.initialComments,
                            isBookmarked = false,
                            onLikeToggle = { viewModel.toggleLikeHomePost(postId) },
                            onCommentClick = { activePostForComments = postItem },
                            onShareClick = {
                                try {
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "YouSnap Post")
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Check out @${photo.creatorUsername}'s photo on YouSnap! 📸✨"
                                        )
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share via").apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Link copied! 🔗", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBookmarkToggle = {
                                Toast.makeText(context, "Post saved to bookmarks! 📌", Toast.LENGTH_SHORT).show()
                            },
                            onProfileClick = {
                                if (isPhotoOwner) {
                                    onNavigateTo(Screen.Profile)
                                } else {
                                    onNavigateTo(Screen.OtherProfile(photo.creatorUsername))
                                }
                            },
                            onPhotoClick = {
                                selectedPhotoForViewer = photo.copy(initialIsLiked = isLiked)
                            },
                            onDismissBanner = null,
                            isOwner = isPhotoOwner,
                            onDeleteClick = if (isPhotoOwner) {
                                {
                                    viewModel.deletePhotoPost(photo.firestoreDocId ?: photo.id, photo.imageUrl, photo.creatorUsername, photo.creatorUid) {}
                                }
                            } else null,
                            onRequestReport = {
                                postToReport = postItem
                            }
                        )
                    }
                }

                // Empty State when no real posts or reels exist yet
                if (visibleReels.isEmpty() && selectedHomePost == null && visiblePersonalPhotos.isEmpty()) {
                    item(key = "home_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("📸", fontSize = 56.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Posts Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = WarmCreamTextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Be the first creator to share a photo or video on YouSnap!",
                                    fontSize = 14.sp,
                                    color = WarmCreamTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { onNavigateTo(Screen.Create) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0095F6)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Create Post / Reel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Item C: Auto-Playing Feed Video Reels
                if (visibleReels.isNotEmpty()) {
                    itemsIndexed(
                        items = visibleReels,
                        key = { _, reel -> "reel_${reel.id}_${reel.timestamp}_${reel.mediaUrl.hashCode()}" }
                    ) { _, reel ->
                        val itemKey = "reel_${reel.id}_${reel.timestamp}_${reel.mediaUrl.hashCode()}"
                        val isReelOwner = viewModel.isOwnedByCurrentUser(reel.creatorUsername, reel.creatorUid)
                        InstagramFeedPost(
                            reel = reel,
                            isOwner = isReelOwner,
                            isAutoPlayActive = canAutoPlayFeed && activePlayingReelKey == itemKey,
                            isMuted = isFeedMuted,
                            onToggleMute = { isFeedMuted = !isFeedMuted },
                            onDeleteClick = if (isReelOwner) {
                                { viewModel.deleteReelVideo(reel) {} }
                            } else null,
                            onLikeToggle = { viewModel.toggleLikeReel(reel) },
                            onCommentClick = {
                                viewModel.loadCommentsForReel(reel.id)
                                selectedReelForComments = reel
                            },
                            onFollowToggle = { viewModel.toggleFollowCreator(reel) },
                            onShareClick = {
                                try {
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "YouSnap Reel")
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Check out @${reel.creatorUsername}'s video on YouSnap! 🎬✨"
                                        )
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share via").apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    viewModel.app.startActivity(shareIntent)
                                    viewModel.incrementReelShares(reel)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        viewModel.app,
                                        "Link copied!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onProfileClick = {
                                if (isReelOwner) {
                                    onNavigateTo(Screen.Profile)
                                } else {
                                    onNavigateTo(Screen.OtherProfile(reel.creatorUsername))
                                }
                            },
                            onReelClick = {
                                onNavigateTo(
                                    Screen.Reels(
                                        startReelId = reel.id,
                                        startMediaUrl = reel.mediaUrl
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
        }

        // 3. COMMENTS BOTTOM SHEET (Home Feed Posts)
        activePostForComments?.let { post ->
            val commentsForPost = homePostCommentsMap[post.id] ?: emptyList()
            CommentBottomSheet(
                comments = commentsForPost,
                commentText = activePostCommentInput,
                onCommentTextChange = { activePostCommentInput = it },
                onPostComment = {
                    if (activePostCommentInput.trim().isNotEmpty()) {
                        viewModel.addCommentToHomePost(post.id, activePostCommentInput)
                        activePostCommentInput = ""
                    }
                },
                onDismissRequest = { activePostForComments = null },
                sheetState = sheetState
            )
        }

        // FULLSCREEN VIEWER (from Home Feed Photo clicks)
        selectedPhotoForViewer?.let { photo ->
            val isPhotoOwner = viewModel.isOwnedByCurrentUser(photo.creatorUsername, photo.creatorUid)
            FullScreenPhotoViewer(
                photoData = photo,
                currentUsername = currentProfile?.username ?: "",
                isOwner = isPhotoOwner,
                onDismiss = { selectedPhotoForViewer = null },
                onLikeChanged = { _, liked, count ->
                    viewModel.notifyPhotoLikedFromViewer(photo, liked, count)
                },
                onDeletePhoto = if (isPhotoOwner) {
                    { photoToDelete ->
                        viewModel.deletePhotoPost(
                            postId = photoToDelete.firestoreDocId ?: photoToDelete.id,
                            imageUrl = photoToDelete.imageUrl,
                            creatorUsername = photoToDelete.creatorUsername,
                            creatorUid = photoToDelete.creatorUid
                        ) {
                            selectedPhotoForViewer = null
                        }
                    }
                } else null
            )
        }

        // REPORT POST BOTTOM SHEET
        postToReport?.let { post ->
            ReportPostBottomSheet(
                postUsername = post.username,
                onReportSubmit = { reason, details ->
                    viewModel.reportPost(post.id, post.username, reason, details)
                    postToReport = null
                },
                onDismissRequest = { postToReport = null }
            )
        }

        // 4. COMMENTS BOTTOM SHEET (Dynamic SQLite Reels)
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

        // 5. STORY VIEWER OVERLAY DIALOG
        activeStoryUser?.let { storyUser ->
            StoryPlaybackDialog(
                username = storyUser,
                viewModel = viewModel,
                onNavigateTo = onNavigateTo,
                onDismiss = { activeStoryUser = null }
            )
        }
    }
}

@Composable
fun FeedPhotoPostCard(
    post: HomePostItem,
    isLiked: Boolean,
    likesCount: Int,
    commentsCount: Int,
    isBookmarked: Boolean,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onDismissBanner: (() -> Unit)? = null,
    isOwner: Boolean = false,
    onDeleteClick: (() -> Unit)? = null,
    onRequestReport: (() -> Unit)? = null
) {
    var showHeartOverlay by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(WarmCreamBackground)
    ) {
        // Post Header with User Profile Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onProfileClick() }
                    .padding(4.dp)
            ) {
                if (post.userAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = post.userAvatarUrl,
                        contentDescription = post.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFFC48B5E), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8DFC8))
                            .border(1.dp, Color(0xFFC48B5E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.userInitials,
                            color = WarmCreamTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = post.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = WarmCreamTextPrimary
                    )
                    Text(
                        text = post.location,
                        fontSize = 11.sp,
                        color = WarmCreamTextSecondary
                    )
                }
            }

            Box {
                IconButton(onClick = { showPostMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = WarmCreamTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showPostMenu,
                    onDismissRequest = { showPostMenu = false },
                    modifier = Modifier.background(Color(0xFFFFF8F2))
                ) {
                    if (isOwner && post.isFromUserProfile) {
                        DropdownMenuItem(
                            text = { Text("Delete Post", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFB3261E)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                onDeleteClick?.invoke()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isBookmarked) "Remove Bookmark" else "Save Post",
                                    color = Color(0xFF2A1810),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save",
                                    tint = if (isBookmarked) Color(0xFFE59424) else Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                onBookmarkToggle()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Copy Link", color = Color(0xFF2A1810), fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Copy Link",
                                    tint = Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Post Link", "https://yousnap.app/post/${post.username}")
                                    clipboard.setPrimaryClip(clip)
                                } catch (e: Exception) {}
                                Toast.makeText(context, "Link copied to clipboard! 🔗", Toast.LENGTH_SHORT).show()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("View Fullscreen", color = Color(0xFF2A1810), fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                onPhotoClick()
                            }
                        )

                        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp)

                        DropdownMenuItem(
                            text = { Text("Report Post", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Report,
                                    contentDescription = "Report",
                                    tint = Color(0xFFB3261E)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                if (onRequestReport != null) {
                                    onRequestReport()
                                } else {
                                    Toast.makeText(context, "Post reported. Thank you!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        if (isOwner) {
                            HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp)
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFB3261E)
                                    )
                                },
                                onClick = {
                                    showPostMenu = false
                                    onDeleteClick?.invoke()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Post Media Image with double-tap like gesture and single-tap to view
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isLiked) {
                                onLikeToggle()
                            }
                            showHeartOverlay = true
                        },
                        onTap = {
                            if (!post.isFromUserProfile) {
                                onPhotoClick()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (post.imageRes != null) {
                Image(
                    painter = painterResource(id = post.imageRes),
                    contentDescription = post.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (post.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = post.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // If default hills post, show title overlay
            if (post.id == "default_alex_hills") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "Mystical",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(2f, 2f),
                                blurRadius = 12f
                            )
                        )
                    )
                    Text(
                        text = "Assam",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(2f, 2f),
                                blurRadius = 12f
                            )
                        )
                    )
                }
            }

            // Fullscreen badge button in top right corner
            IconButton(
                onClick = onPhotoClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Animated Heart on double tap
            androidx.compose.animation.AnimatedVisibility(
                visible = showHeartOverlay,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.4f),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut()
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
                    tint = Color(0xFFE54B3C),
                    modifier = Modifier.size(84.dp)
                )
            }
        }

        // Action Buttons
        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLikeToggle() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpAlt,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFF1877F2) else WarmCreamTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Like (${formatEngCount(likesCount)})",
                    fontSize = 13.sp,
                    fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isLiked) Color(0xFF1877F2) else WarmCreamTextSecondary
                )
            }

            // Comment Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCommentClick() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ModeComment,
                    contentDescription = "Comment",
                    tint = WarmCreamTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Comment ($commentsCount)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarmCreamTextSecondary
                )
            }

            // Share Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShareClick() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = "Share",
                    tint = WarmCreamTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Share",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarmCreamTextSecondary
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp, modifier = Modifier.padding(bottom = 6.dp))

        // Post Details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Liked by ${formatEngCount(likesCount)} people",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = WarmCreamTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${post.username}: ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WarmCreamTextPrimary,
                    modifier = Modifier.clickable { onProfileClick() }
                )
                Text(
                    text = post.caption,
                    fontSize = 13.sp,
                    color = WarmCreamTextPrimary.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = post.hashtags,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = WarmCreamTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "View all $commentsCount comments",
                fontSize = 12.sp,
                color = WarmCreamTextSecondary,
                modifier = Modifier
                    .clickable { onCommentClick() }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

private data class UserStoryItem(
    val initials: String,
    val name: String,
    val imageRes: Int
)

// Custom Composable rendering feed items as a sleek inline Video Player card
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun InstagramFeedPost(
    reel: ReelEntity,
    isOwner: Boolean = false,
    isAutoPlayActive: Boolean = false,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onShareClick: () -> Unit,
    onProfileClick: () -> Unit,
    onReelClick: () -> Unit
) {
    var showBigHeartOverlay by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var localIsLiked by remember(reel.id, reel.isLiked) { mutableStateOf(reel.isLiked) }
    var localLikesCount by remember(reel.id, reel.likesCount) { mutableStateOf(reel.likesCount) }
    var localCommentsCount by remember(reel.id, reel.commentsCount) { mutableStateOf(reel.commentsCount) }
    var localShareCount by remember(reel.id) { mutableStateOf(0) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("home_video_post_card_${reel.id}"),
        shape = RoundedCornerShape(20.dp),
        color = WarmCreamBackground,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE8DFC8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Post Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onProfileClick() }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8DFC8))
                        .border(1.dp, Color(0xFFC48B5E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = reel.creatorUsername.take(2).uppercase(),
                        color = WarmCreamTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reel.creatorUsername,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = WarmCreamTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (reel.isFollowingCreator) "• Following" else "• Follow",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (reel.isFollowingCreator) WarmCreamTextSecondary else Color(0xFFE1306C),
                            modifier = Modifier.clickable { onFollowToggle() }
                        )
                    }
                    Text(
                        text = reel.audioTitle,
                        fontSize = 10.sp,
                        color = WarmCreamTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { showPostMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = WarmCreamTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showPostMenu,
                    onDismissRequest = { showPostMenu = false },
                    modifier = Modifier.background(Color(0xFFFFF8F2))
                ) {
                    if (isOwner && onDeleteClick != null) {
                        DropdownMenuItem(
                            text = { Text("Delete Reel", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Reel",
                                    tint = Color(0xFFB3261E)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                onDeleteClick()
                            }
                        )
                        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp)
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isBookmarked) "Remove Bookmark" else "Save Post",
                                color = Color(0xFF2A1810),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isBookmarked) Color(0xFFE59424) else Color(0xFF2A1810)
                            )
                        },
                        onClick = {
                            showPostMenu = false
                            isBookmarked = !isBookmarked
                            val msg = if (isBookmarked) "Post saved to bookmarks!" else "Post removed from bookmarks!"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy Link", color = Color(0xFF2A1810), fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = "Copy Link",
                                tint = Color(0xFF2A1810)
                            )
                        },
                        onClick = {
                            showPostMenu = false
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Post Link", "https://yousnap.app/post/${reel.creatorUsername}")
                                clipboard.setPrimaryClip(clip)
                            } catch (e: Exception) {
                                // Fallback
                            }
                            Toast.makeText(context, "Link copied to clipboard! 🔗", Toast.LENGTH_SHORT).show()
                        }
                    )

                    if (!isOwner) {
                        DropdownMenuItem(
                            text = { Text("Unfollow User", color = Color(0xFF2A1810), fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PersonRemove,
                                    contentDescription = "Unfollow",
                                    tint = Color(0xFF2A1810)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                onFollowToggle()
                                Toast.makeText(context, "Unfollowed @${reel.creatorUsername}!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp)

                        DropdownMenuItem(
                            text = { Text("Report Post", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Report,
                                    contentDescription = "Report",
                                    tint = Color(0xFFB3261E)
                                )
                            },
                            onClick = {
                                showPostMenu = false
                                Toast.makeText(context, "Post reported. Thank you!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Inline Video Player View with true aspect ratio, Mute/Unmute, progress strip & tap-to-Reels
        val mediaToDisplay = reel.thumbnailUrl.takeIf {
            it.isNotBlank() && ((!it.startsWith("/") && !it.startsWith("file:")) || java.io.File(it.removePrefix("file://")).exists())
        } ?: reel.mediaUrl
        val mediaRequestModel = remember(mediaToDisplay) {
            if (mediaToDisplay.isBlank()) {
                null
            } else if (mediaToDisplay.startsWith("/")) {
                java.io.File(mediaToDisplay)
            } else {
                coil.request.ImageRequest.Builder(context)
                    .data(mediaToDisplay)
                    .videoFrameMillis(500L)
                    .crossfade(true)
                    .build()
            }
        }
        var isFirstFrameRendered by remember(reel.mediaUrl, isAutoPlayActive) { mutableStateOf(false) }
        var isBuffering by remember(reel.mediaUrl, isAutoPlayActive) { mutableStateOf(false) }
        var detectedAspectRatio by remember(reel.mediaUrl) { mutableStateOf<Float?>(null) }
        var playbackProgress by remember(reel.mediaUrl, isAutoPlayActive) { mutableStateOf(0f) }

        val targetAspectRatio = (detectedAspectRatio ?: (16f / 9f)).coerceIn(0.75f, 1.78f)
        val animatedAspectRatio by animateFloatAsState(
            targetValue = targetAspectRatio,
            animationSpec = tween(durationMillis = 220),
            label = "inline_video_aspect_ratio"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .aspectRatio(animatedAspectRatio)
                .background(Color(0xFF0E0E12))
                .pointerInput(reel.id, reel.mediaUrl) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!localIsLiked) {
                                localIsLiked = true
                                localLikesCount++
                                onLikeToggle()
                            }
                            showBigHeartOverlay = true
                        },
                        onTap = {
                            onReelClick()
                        }
                    )
                }
                .testTag("home_video_player_view_${reel.id}"),
            contentAlignment = Alignment.Center
        ) {
            if (isAutoPlayActive && reel.mediaUrl.isNotBlank()) {
                CdnVideoPlayer(
                    videoUrl = reel.mediaUrl,
                    modifier = Modifier.fillMaxSize(),
                    autoPlay = true,
                    isLooping = true,
                    isMuted = isMuted,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    trackProgress = true,
                    onFirstFrameRendered = {
                        isFirstFrameRendered = true
                        isBuffering = false
                    },
                    onBufferingChanged = { buffering ->
                        isBuffering = buffering
                    },
                    onVideoSizeChanged = { width, height ->
                        if (width > 0 && height > 0) {
                            detectedAspectRatio = width.toFloat() / height.toFloat()
                        }
                    },
                    onProgressChanged = { positionMs, durationMs ->
                        if (durationMs > 0L) {
                            playbackProgress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                )
            }

            // Poster thumbnail stays visible until ExoPlayer renders its first video frame
            if (mediaRequestModel != null && (!isAutoPlayActive || !isFirstFrameRendered)) {
                AsyncImage(
                    model = mediaRequestModel,
                    contentDescription = reel.caption,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        if (detectedAspectRatio == null) {
                            val intrinsic = state.painter.intrinsicSize
                            if (intrinsic.width > 0f && intrinsic.height > 0f) {
                                detectedAspectRatio = intrinsic.width / intrinsic.height
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top-left subtle Reels badge hinting tap-to-open Reels screen
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isAutoPlayActive) "Playing • Tap for Reels" else "Reels",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Center buffering or paused play badge
            if (isAutoPlayActive && isBuffering && !isFirstFrameRendered) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp)
                )
            } else if (!isAutoPlayActive && reel.mediaUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Mute / Unmute Button directly on the Video Player View
            if (reel.mediaUrl.isNotBlank()) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 8.dp)
                        .size(48.dp)
                        .testTag("home_video_mute_button_${reel.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute video" else "Mute video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Subtle playback progress strip along bottom of the inline player
            if (isAutoPlayActive && reel.mediaUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(playbackProgress)
                            .background(Color(0xFFE1306C))
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showBigHeartOverlay,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.4f),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut()
            ) {
                LaunchedEffect(showBigHeartOverlay) {
                    if (showBigHeartOverlay) {
                        delay(600)
                        showBigHeartOverlay = false
                    }
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = Color(0xFFE54B3C),
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        // Inbuilt Bottom Action Bar directly beneath the Video Player View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Like Button
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            localIsLiked = !localIsLiked
                            if (localIsLiked) localLikesCount++ else localLikesCount--
                            onLikeToggle()
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (localIsLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (localIsLiked) Color(0xFFE1306C) else WarmCreamTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = formatEngCount(localLikesCount),
                        fontSize = 13.sp,
                        fontWeight = if (localIsLiked) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (localIsLiked) Color(0xFFE1306C) else WarmCreamTextPrimary
                    )
                }

                // Comment Button
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCommentClick() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = "Comment",
                        tint = WarmCreamTextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = localCommentsCount.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WarmCreamTextPrimary
                    )
                }

                // Share Button
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            localShareCount++
                            onShareClick()
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = "Share",
                        tint = WarmCreamTextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                    if (localShareCount > 0) {
                        Text(
                            text = localShareCount.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarmCreamTextPrimary
                        )
                    }
                }
            }

            // Open in Full Reels Screen shortcut
            IconButton(
                onClick = onReelClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_video_open_reels_${reel.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Watch in Reels",
                    tint = WarmCreamTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(
            color = Color(0xFFE8DFC8).copy(alpha = 0.7f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Caption block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (reel.caption.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${reel.creatorUsername} ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = WarmCreamTextPrimary,
                        modifier = Modifier.clickable { onProfileClick() }
                    )
                    Text(
                        text = reel.caption,
                        fontSize = 13.sp,
                        color = WarmCreamTextPrimary.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = if (localCommentsCount > 0) "View all $localCommentsCount comments" else "Add a comment...",
                fontSize = 12.sp,
                color = WarmCreamTextSecondary,
                modifier = Modifier
                    .clickable { onCommentClick() }
                    .padding(vertical = 2.dp)
            )
        }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPlaybackDialog(
    username: String,
    viewModel: YouSnapViewModel,
    onNavigateTo: (Screen) -> Unit,
    onDismiss: () -> Unit
) {
    // Crash-Free Navigation: Check if username is empty or invalid
    if (username.trim().isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    var activeUsername by remember { mutableStateOf(username) }
    var progress by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var isHoldingToPause by remember { mutableStateOf(false) }
    var showViewerSheet by remember { mutableStateOf(false) }
    var showLikeNotificationIndicator by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReportReason by remember { mutableStateOf("Inappropriate Content") }
    var reportDetails by remember { mutableStateOf("") }

    val currentProfile by viewModel.profileState.collectAsState()
    val loggedInUsername = currentProfile.username.orEmpty()
    val usernames = remember(activeUsername) {
        listOf(activeUsername)
    }
    val currentIndex = remember(activeUsername, usernames) {
        val idx = usernames.indexOfFirst { it.lowercase() == activeUsername.lowercase() }
        if (idx == -1) 0 else idx
    }

    fun navigateToNextStory() {
        if (currentIndex < usernames.size - 1) {
            progress = 0f
            activeUsername = usernames[currentIndex + 1]
        } else {
            onDismiss()
        }
    }

    fun navigateToPrevStory() {
        if (currentIndex > 0) {
            progress = 0f
            activeUsername = usernames[currentIndex - 1]
        } else {
            progress = 0f
        }
    }

    LaunchedEffect(showLikeNotificationIndicator) {
        if (showLikeNotificationIndicator != null) {
            delay(2500)
            showLikeNotificationIndicator = null
        }
    }

    // Start listening to real-time updates and record story view with robust try-catch
    LaunchedEffect(activeUsername) {
        try {
            viewModel.startListeningToStory(activeUsername)
            viewModel.recordStoryView(activeUsername)
        } catch (e: Exception) {
            android.util.Log.e("StoryPlaybackDialog", "Error starting story listener: ${e.message}")
        }
    }

    DisposableEffect(activeUsername) {
        onDispose {
            try {
                viewModel.stopListeningToStory()
            } catch (e: Exception) {
                android.util.Log.e("StoryPlaybackDialog", "Error stopping story listener: ${e.message}")
            }
        }
    }

    // Story data from real-time Firebase Firestore + immediate local allStories fallback
    val storyState by viewModel.currentStoryState.collectAsState()
    val allStoriesList by viewModel.allStories.collectAsState()
    val resolvedStory = remember(storyState, allStoriesList, activeUsername) {
        storyState ?: allStoriesList.firstOrNull { it.ownerUsername.equals(activeUsername, ignoreCase = true) }
    }
    val isOwner = activeUsername.lowercase() == loggedInUsername.lowercase()

    val viewsCount = resolvedStory?.viewsCount ?: 0
    val likesList = resolvedStory?.likes ?: emptyList()
    val isLiked = likesList.any {
        it.equals(loggedInUsername, ignoreCase = true) || (it.equals("me", ignoreCase = true) && loggedInUsername.isEmpty())
    }

    val mediaModel: String? = resolvedStory?.mediaUrl?.takeIf { it.isNotBlank() }
    val resolvedMediaType = remember(resolvedStory, mediaModel) {
        when {
            resolvedStory == null -> "loading"
            resolvedStory.mediaType.equals("text", ignoreCase = true) -> "text"
            resolvedStory.mediaType.equals("video", ignoreCase = true) -> "video"
            resolvedStory.mediaType.equals("image", ignoreCase = true) -> "image"
            mediaModel.isNullOrBlank() && resolvedStory.caption.isNotBlank() -> "text"
            !mediaModel.isNullOrBlank() && mediaModel.lowercase().let {
                it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(".webm")
            } -> "video"
            !mediaModel.isNullOrBlank() -> "image"
            else -> "text"
        }
    }

    // Media Loading Safety states keyed by active mediaUrl so state resets cleanly when story loads
    var imageLoading by remember(mediaModel, resolvedMediaType) {
        mutableStateOf(resolvedMediaType == "image" && !mediaModel.isNullOrEmpty())
    }
    var imageError by remember(mediaModel, resolvedMediaType) { mutableStateOf(false) }
    var videoReady by remember(mediaModel, resolvedMediaType) { mutableStateOf(false) }

    // Story progress animation with pause/resume support (pauses while story or media loads/buffers)
    val isMediaPreparing = resolvedMediaType == "loading" ||
        (resolvedMediaType == "image" && imageLoading) ||
        (resolvedMediaType == "video" && !videoReady)
    val shouldPauseTimer = isPaused || isHoldingToPause || showViewerSheet || showMenu || showDeleteConfirmDialog || showReportDialog
    val effectivePauseTimer = shouldPauseTimer || isMediaPreparing
    LaunchedEffect(effectivePauseTimer, activeUsername, resolvedMediaType) {
        try {
            if (!effectivePauseTimer) {
                val duration = if (resolvedMediaType == "video") 30000 else 6000
                val interval = 40
                val steps = duration / interval
                var currentStep = (progress * steps).toInt()
                while (currentStep <= steps) {
                    if (effectivePauseTimer) break
                    progress = currentStep.toFloat() / steps
                    delay(interval.toLong())
                    currentStep++
                }
                if (currentStep > steps && !effectivePauseTimer) {
                    navigateToNextStory()
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("StoryPlaybackDialog", "Progress timer interrupted: ${e.message}")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                .systemBarsPadding()
        ) {

            // Real Media Display
            var layoutWidth by remember { mutableStateOf(0) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { layoutWidth = it.width }
                    .pointerInput(activeUsername) {
                        detectTapGestures(
                            onPress = {
                                isHoldingToPause = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isHoldingToPause = false
                                }
                            },
                            onTap = { offset ->
                                if (layoutWidth > 0) {
                                    if (offset.x < layoutWidth * 0.35f) {
                                        navigateToPrevStory()
                                    } else {
                                        navigateToNextStory()
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    resolvedMediaType == "loading" -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    }
                    resolvedMediaType == "text" -> {
                        // TEXT-ONLY STORY DISPLAY
                        val gStart = remember(resolvedStory?.bgGradientStart) {
                            try { Color(android.graphics.Color.parseColor(resolvedStory?.bgGradientStart?.ifBlank { "#FF512F" } ?: "#FF512F")) } catch (_: Exception) { Color(0xFFFF512F) }
                        }
                        val gEnd = remember(resolvedStory?.bgGradientEnd) {
                            try { Color(android.graphics.Color.parseColor(resolvedStory?.bgGradientEnd?.ifBlank { "#DD2476" } ?: "#DD2476")) } catch (_: Exception) { Color(0xFFDD2476) }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(gStart, gEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 48.dp)
                            ) {
                                Text(
                                    text = resolvedStory?.caption.orEmpty(),
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 36.sp,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(2f, 4f),
                                            blurRadius = 8f
                                        )
                                    )
                                )
                            }
                        }
                    }
                    resolvedMediaType == "video" && !mediaModel.isNullOrEmpty() -> {
                        // VIDEO PLAYER FOR STORIES
                        CdnVideoPlayer(
                            videoUrl = mediaModel,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = !shouldPauseTimer,
                            isLooping = false,
                            isMuted = false,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            onFirstFrameRendered = { videoReady = true },
                            onVideoCompleted = { navigateToNextStory() }
                        )
                        if (!videoReady) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    !mediaModel.isNullOrEmpty() -> {
                        // PHOTO DISPLAY
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = "Story Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onLoading = {
                                imageLoading = true
                                imageError = false
                            },
                            onSuccess = {
                                imageLoading = false
                                imageError = false
                            },
                            onError = {
                                imageLoading = false
                                imageError = true
                            }
                        )
                    }
                    else -> {
                        imageError = true
                    }
                }

                // If media has a text overlay, display stylish overlay banner
                val storyCaption = resolvedStory?.caption ?: ""
                if (resolvedMediaType != "text" && resolvedMediaType != "loading" && storyCaption.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp, start = 20.dp, end = 20.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = storyCaption,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Error Fallback UI (only shown if media actually failed to load)
                if (imageError && resolvedMediaType == "image") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isOwner) {
                                        listOf(Color(0xFF2E0854), Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                    } else {
                                        listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isOwner) "🎬" else "✨",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isOwner) "Unable to load your story" else "Unable to load $activeUsername's story",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                // Loading State Indicator for image stories
                if (imageLoading && resolvedMediaType == "image") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            // Animated Like Notification Indicator Banner Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showLikeNotificationIndicator != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f)
                    .padding(top = 100.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE59424))
                        .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notification",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = showLikeNotificationIndicator ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top Progress bar and User info
            AnimatedVisibility(
                visible = !isHoldingToPause,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDismiss()
                                    if (isOwner) {
                                        onNavigateTo(Screen.Profile)
                                    } else {
                                        onNavigateTo(Screen.OtherProfile(activeUsername))
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val displayInitials = if (isOwner) "ME" else activeUsername.take(2).uppercase().ifEmpty { "US" }
                                Text(
                                    text = displayInitials,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOwner) "My Story" else activeUsername,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Story",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // BOTTOM LAYER: Semi-transparent Dark Bottom Action Bar Overlay
            AnimatedVisibility(
                visible = !isHoldingToPause,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Views Counter ("Seen by X" / Eye Icon)
                        Surface(
                            onClick = {
                                if (isOwner) {
                                    showViewerSheet = true
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isOwner) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.testTag("story_seen_counter")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = "Views Counter",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Seen by $viewsCount",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right: Action Buttons Row -> 3-Dot (More Options) is to the LEFT of Like (Heart) Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. 3-Dot (More Options) Button (Left of Like Icon)
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .testTag("story_more_options_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Story Options",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color(0xFF262626))
                                ) {
                                    if (isOwner) {
                                        // [A] Owner's Story: ONLY "Delete Story" option
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color(0xFFFF5252),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text("Delete Story", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                showDeleteConfirmDialog = true
                                            }
                                        )
                                    } else {
                                        // [B] Other User's Story: ONLY "Report Story" option
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Flag,
                                                        contentDescription = "Report",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text("Report Story", color = Color.White, fontWeight = FontWeight.SemiBold)
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                showReportDialog = true
                                            }
                                        )
                                    }
                                }
                            }

                            // 2. Like (Heart) Icon (Positioned to the right of 3-Dot)
                            if (!isOwner) {
                                val heartScale by animateFloatAsState(
                                    targetValue = if (isLiked) 1.35f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioHighBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "heart_scale"
                                )

                                IconButton(
                                    onClick = {
                                        try {
                                            viewModel.toggleStoryLike(activeUsername)
                                            if (!isLiked) {
                                                viewModel.triggerStoryLikeNotification(activeUsername)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("StoryPlaybackDialog", "Error toggling like: ${e.message}")
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isLiked) Color(0x33FF2D55) else Color.White.copy(alpha = 0.15f)
                                        )
                                        .testTag("story_like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like Story",
                                        tint = if (isLiked) Color(0xFFFF2D55) else Color.White,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(heartScale)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // INSTAGRAM-STYLE BOTTOM SHEET (Only shown when owner clicks "Seen by")
            if (showViewerSheet && resolvedStory != null) {
                ModalBottomSheet(
                    onDismissRequest = { showViewerSheet = false },
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Story Views (${resolvedStory.viewsCount})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Safe and crash-free retrieval of viewer profiles
                        val viewersList = try {
                            viewModel.getViewerProfilesForStory(resolvedStory)
                        } catch (e: Exception) {
                            android.util.Log.e("StoryPlaybackDialog", "Error getting viewers: ${e.message}")
                            emptyList()
                        }
                        
                        if (viewersList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No viewers yet",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(viewersList) { viewer ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(Color(0xFFE59424), Color(0xFFC48B5E))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val initialsSafe = viewer.initials.orEmpty().ifEmpty { "US" }
                                                Text(
                                                    text = initialsSafe,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            Column {
                                                Text(
                                                    text = viewer.name.orEmpty().ifEmpty { viewer.username.orEmpty() },
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "@${viewer.username.orEmpty()}",
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        
                                        if (viewer.hasLiked) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Liked",
                                                tint = Color.Red,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // DELETE STORY CONFIRMATION DIALOG
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = {
                        Text(
                            text = "Delete Story?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to delete this story? This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                viewModel.deleteStory(activeUsername) {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirmDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // REPORT STORY DIALOG
            if (showReportDialog) {
                val reportReasons = listOf(
                    "Inappropriate Content",
                    "Spam or Misleading",
                    "Harassment or Hate Speech",
                    "Violence or Dangerous Acts",
                    "Intellectual Property Violation",
                    "Other"
                )

                Dialog(
                    onDismissRequest = { showReportDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Report Story",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Help us understand the issue with @$activeUsername's story. Your report is confidential.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            reportReasons.forEach { reason ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedReportReason = reason }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedReportReason == reason,
                                        onClick = { selectedReportReason = reason }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = reportDetails,
                                onValueChange = { reportDetails = it },
                                label = { Text("Additional details (optional)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showReportDialog = false
                                        reportDetails = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        showReportDialog = false
                                        viewModel.reportStory(
                                            storyOwner = activeUsername,
                                            reason = selectedReportReason,
                                            details = reportDetails
                                        )
                                        reportDetails = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Submit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatEngCount(count: Int): String {
    return if (count >= 1000) {
        String.format("%.1fK", count / 1000.0).replace(".0", "")
    } else {
        count.toString()
    }
}