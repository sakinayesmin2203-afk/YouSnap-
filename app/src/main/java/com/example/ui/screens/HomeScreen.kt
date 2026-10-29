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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.HomePostItem
import com.example.data.model.ReelEntity
import com.example.ui.Screen
import com.example.ui.YouSnapViewModel
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReportPostBottomSheet
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.components.FullScreenPhotoViewer
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

    // User Story Cards - Only the real logged-in user's story
    val currentProfile by viewModel.profileState.collectAsState()
    val personalPhotos by viewModel.personalPhotosState.collectAsState()
    val storyState by viewModel.currentStoryState.collectAsState()
    val hasUploadedStory = storyState != null && !storyState?.mediaUrl.isNullOrEmpty()

    // Observe active post opened on Home Screen (e.g. from user profiles)
    val selectedHomePost by viewModel.selectedHomePost.collectAsState()
    val homePostCommentsMap by viewModel.homePostComments.collectAsState()
    val homePostLikedState by viewModel.homePostLikedState.collectAsState()
    val homePostLikesCount by viewModel.homePostLikesCount.collectAsState()
    val homePostBookmarked by viewModel.homePostBookmarked.collectAsState()
    val isHomeRefreshing by viewModel.isHomeRefreshing.collectAsState()

    var activePostForComments by remember { mutableStateOf<HomePostItem?>(null) }
    var activePostCommentInput by remember { mutableStateOf("") }
    var selectedPhotoForViewer by remember { mutableStateOf<FullScreenPhotoData?>(null) }
    var postToReport by remember { mutableStateOf<HomePostItem?>(null) }

    // Scroll to top when a photo is opened from a user's profile
    LaunchedEffect(selectedHomePost) {
        if (selectedHomePost != null) {
            listState.animateScrollToItem(0)
        }
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
                            if (hasUploadedStory) {
                                item(key = "my_uploaded_story") {
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
                                                AsyncImage(
                                                    model = storyState?.mediaUrl,
                                                    contentDescription = "My Story",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
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
                                }
                            } else {
                                item(key = "add_story_card") {
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
                        }
                    }
                }

                // Item B1: Dynamic Profile-Opened Post (if user clicked a photo from any user's profile)
                selectedHomePost?.let { profilePost ->
                    item(key = "profile_opened_post_${profilePost.id}") {
                        val isLiked = homePostLikedState[profilePost.id] ?: profilePost.isLiked
                        val likesCount = homePostLikesCount[profilePost.id] ?: profilePost.likesCount
                        val commentsForPost = homePostCommentsMap[profilePost.id] ?: emptyList()
                        val isBookmarked = homePostBookmarked[profilePost.id] ?: profilePost.isBookmarked

                        val isOwner = currentProfile.username.lowercase() == profilePost.username.lowercase()

                        FeedPhotoPostCard(
                            post = profilePost,
                            isLiked = isLiked,
                            likesCount = likesCount,
                            commentsCount = commentsForPost.size.coerceAtLeast(profilePost.commentsCount),
                            isBookmarked = isBookmarked,
                            onLikeToggle = { viewModel.toggleLikeHomePost(profilePost.id) },
                            onCommentClick = {
                                activePostForComments = profilePost
                            },
                            onShareClick = {
                                try {
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "YouSnap Post")
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Check out @${profilePost.username}'s photo on YouSnap! 📸✨"
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
                                if (profilePost.username == currentProfile.username) {
                                    onNavigateTo(Screen.Profile)
                                } else {
                                    onNavigateTo(Screen.OtherProfile(profilePost.username))
                                }
                            },
                            onPhotoClick = {
                                // Disabled when opened from profile as requested
                            },
                            onDismissBanner = {
                                viewModel.clearOpenedHomePost()
                            },
                            isOwner = isOwner,
                            onDeleteClick = {
                                viewModel.deletePhotoPost(profilePost.id, profilePost.imageUrl) {
                                    viewModel.clearOpenedHomePost()
                                }
                            },
                            onRequestReport = {
                                postToReport = profilePost
                            }
                        )
                    }
                }

                // Real uploaded photo posts from users
                if (personalPhotos.isNotEmpty()) {
                    items(personalPhotos, key = { "personal_photo_${it.id}" }) { photo ->
                        val postId = "photo_${photo.id}"
                        val isLiked = homePostLikedState[postId] ?: photo.initialIsLiked
                        val currentLikes = homePostLikesCount[postId] ?: photo.initialLikes
                        val currentAuthUid = viewModel.currentUser.collectAsState().value?.uid
                        val isPhotoOwner = currentProfile.username.equals(photo.creatorUsername, ignoreCase = true) ||
                                           (currentAuthUid != null && photo.creatorUid.isNotEmpty() && currentAuthUid == photo.creatorUid)

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
                                Toast.makeText(context, "Double tap photo to like! ❤️", Toast.LENGTH_SHORT).show()
                            },
                            onDismissBanner = null,
                            isOwner = isPhotoOwner,
                            onDeleteClick = if (isPhotoOwner) {
                                {
                                    viewModel.deletePhotoPost(photo.id.toString(), photo.imageUrl, photo.creatorUsername, photo.creatorUid) {}
                                }
                            } else null,
                            onRequestReport = {
                                postToReport = postItem
                            }
                        )
                    }
                }

                // Empty State when no real posts or reels exist yet
                if (reels.isEmpty() && selectedHomePost == null && personalPhotos.isEmpty()) {
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

                // Item C: Other Dynamic SQLite database Reels
                if (reels.isNotEmpty()) {
                    itemsIndexed(reels, key = { _, reel -> reel.id }) { _, reel ->
                        InstagramFeedPost(
                            reel = reel,
                            onLikeToggle = { viewModel.toggleLikeReel(reel) },
                            onCommentClick = { selectedReelForComments = reel },
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
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        viewModel.app,
                                        "Link copied!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onProfileClick = { onNavigateTo(Screen.OtherProfile(reel.creatorUsername)) }
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
            FullScreenPhotoViewer(
                photoData = photo,
                onDismiss = { selectedPhotoForViewer = null }
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

// Custom Composable rendering feed items
@Composable
fun InstagramFeedPost(
    reel: ReelEntity,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onShareClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val presetStart = remember(reel.bgGradientStart) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientStart)) }
        catch (e: Exception) { Color(0xFFFF1A75) }
    }
    val presetEnd = remember(reel.bgGradientEnd) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientEnd)) }
        catch (e: Exception) { Color(0xFF8A3AB9) }
    }

    var showBigHeartOverlay by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var localIsLiked by remember(reel.id, reel.isLiked) { mutableStateOf(reel.isLiked) }
    var localLikesCount by remember(reel.id, reel.likesCount) { mutableStateOf(reel.likesCount) }
    var localCommentsCount by remember(reel.id, reel.commentsCount) { mutableStateOf(reel.commentsCount) }
    var localShareCount by remember(reel.id) { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(WarmCreamBackground)
    ) {
        // Post Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
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

        // Post Image Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(presetStart, presetEnd)
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!localIsLiked) {
                                localIsLiked = true
                                localLikesCount++
                                onLikeToggle()
                            }
                            showBigHeartOverlay = true
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val mediaToDisplay = reel.mediaUrl.ifEmpty { reel.thumbnailUrl }
            if (mediaToDisplay.isNotEmpty()) {
                AsyncImage(
                    model = mediaToDisplay,
                    contentDescription = reel.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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

        // Facebook Style Action Buttons Layout (Bottom Bar)
        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Button (Thumbs Up 👍)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        localIsLiked = !localIsLiked
                        if (localIsLiked) localLikesCount++ else localLikesCount--
                        onLikeToggle()
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (localIsLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpAlt,
                    contentDescription = "Like",
                    tint = if (localIsLiked) Color(0xFF1877F2) else WarmCreamTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Like (${formatEngCount(localLikesCount)})",
                    fontSize = 13.sp,
                    fontWeight = if (localIsLiked) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (localIsLiked) Color(0xFF1877F2) else WarmCreamTextSecondary
                )
            }

            // Comment Button (Speech Bubble 💬)
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
                    text = "Comment (${localCommentsCount})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarmCreamTextSecondary
                )
            }

            // Share Button (Forward Curved Arrow)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        localShareCount++
                        onShareClick()
                    }
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
                    text = "Share (${localShareCount})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarmCreamTextSecondary
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE8DFC8), thickness = 1.dp, modifier = Modifier.padding(bottom = 6.dp))

        // Caption block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = "Liked by ${formatEngCount(localLikesCount)} people",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = WarmCreamTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "View all ${localCommentsCount} comments",
                fontSize = 12.sp,
                color = WarmCreamTextSecondary,
                modifier = Modifier
                    .clickable { onCommentClick() }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

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

    // Story data from real-time Firebase Firestore
    val storyState by viewModel.currentStoryState.collectAsState()
    val isOwner = activeUsername.lowercase() == loggedInUsername.lowercase()

    val viewsCount = storyState?.viewsCount ?: 0
    val likesList = storyState?.likes ?: emptyList()
    val isLiked = likesList.any {
        it.equals(loggedInUsername, ignoreCase = true) || (it.equals("me", ignoreCase = true) && loggedInUsername.isEmpty())
    }

    // Story progress animation with pause/resume support
    val shouldPauseTimer = isPaused || isHoldingToPause || showViewerSheet || showMenu || showDeleteConfirmDialog || showReportDialog
    LaunchedEffect(shouldPauseTimer, activeUsername) {
        try {
            if (!shouldPauseTimer) {
                val duration = 6000 // 6 seconds to view
                val interval = 40
                val steps = duration / interval
                var currentStep = (progress * steps).toInt()
                while (currentStep <= steps) {
                    if (shouldPauseTimer) break
                    progress = currentStep.toFloat() / steps
                    delay(interval.toLong())
                    currentStep++
                }
                if (currentStep > steps && !shouldPauseTimer) {
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
            // Media Loading Safety states
            var imageLoading by remember { mutableStateOf(true) }
            var imageError by remember { mutableStateOf(false) }

            val mediaModel: String? = if (!storyState?.mediaUrl.isNullOrEmpty()) {
                storyState?.mediaUrl
            } else {
                null
            }

            // Real Media Display / Background Image Container
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
                // Background image loading via Coil AsyncImage
                coil.compose.AsyncImage(
                    model = mediaModel,
                    contentDescription = "Story Media",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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

                // Error Fallback UI: Beautiful Dynamic Gradient overlay so user never experiences crash or empty screen
                if (imageError) {
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
                                text = if (isOwner) "Viewing your story..." else "Viewing $activeUsername's story...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "YouSnap Story Viewer",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Loading State Indicator
                if (imageLoading) {
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
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
            if (showViewerSheet && storyState != null) {
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
                            text = "Story Views (${storyState?.viewsCount ?: 0})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Safe and crash-free retrieval of viewer profiles
                        val viewersList = try {
                            viewModel.getViewerProfilesForStory(storyState)
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