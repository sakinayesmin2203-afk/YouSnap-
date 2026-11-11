package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.components.ReportPostBottomSheet

data class FullScreenPhotoData(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val creatorName: String,
    val creatorUsername: String,
    val creatorAvatar: String,
    val creatorUid: String = "",
    val initialLikes: Int,
    val initialComments: Int,
    val initialIsLiked: Boolean = false,
    val timeAgo: String = "Just now",
    val firestoreDocId: String? = null
)

data class PhotoCommentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val authorName: String,
    val authorUsername: String,
    val text: String,
    val timeAgo: String = "Just now",
    val isLiked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    photoData: FullScreenPhotoData,
    currentUsername: String = "",
    isOwner: Boolean? = null,
    onDismiss: () -> Unit,
    onLikeChanged: ((photoId: Int, isLiked: Boolean, newCount: Int) -> Unit)? = null,
    onDeletePhoto: ((photoData: FullScreenPhotoData) -> Unit)? = null
) {
    FullScreenPhotoViewer(
        photos = listOf(photoData),
        initialIndex = 0,
        currentUsername = currentUsername,
        isOwner = isOwner,
        onDismiss = onDismiss,
        onLikeChanged = onLikeChanged,
        onDeletePhoto = onDeletePhoto
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    photos: List<FullScreenPhotoData>,
    initialIndex: Int = 0,
    currentUsername: String = "",
    isOwner: Boolean? = null,
    onDismiss: () -> Unit,
    onLikeChanged: ((photoId: Int, isLiked: Boolean, newCount: Int) -> Unit)? = null,
    onDeletePhoto: ((photoData: FullScreenPhotoData) -> Unit)? = null
) {
    if (photos.isEmpty()) return

    val displayedPhotos = remember(photos) { mutableStateListOf(*photos.toTypedArray()) }
    if (displayedPhotos.isEmpty()) {
        onDismiss()
        return
    }

    val validIndex = initialIndex.coerceIn(0, displayedPhotos.size - 1)
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = validIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D0D0D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. TOP APP BAR (Instagram Posts Detail Header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color(0xFF0D0D0D))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("instagram_posts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Posts",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)

                // 2. SCROLLABLE POSTS FEED (LazyColumn allowing vertical scrolling through profile photos/videos)
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(displayedPhotos, key = { index, photo -> "${photo.id}_$index" }) { index, photo ->
                        InstagramPostCard(
                            photoData = photo,
                            photoIndex = index,
                            totalCount = displayedPhotos.size,
                            currentUsername = currentUsername,
                            isOwner = isOwner,
                            onLikeChanged = onLikeChanged,
                            onDeletePhoto = { deletedPhoto ->
                                displayedPhotos.remove(deletedPhoto)
                                onDeletePhoto?.invoke(deletedPhoto)
                                if (displayedPhotos.isEmpty()) {
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramPostCard(
    photoData: FullScreenPhotoData,
    photoIndex: Int,
    totalCount: Int,
    currentUsername: String = "",
    isOwner: Boolean? = null,
    onLikeChanged: ((photoId: Int, isLiked: Boolean, newCount: Int) -> Unit)? = null,
    onDeletePhoto: ((photoData: FullScreenPhotoData) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isLiked by remember(photoData.id) { mutableStateOf(photoData.initialIsLiked) }
    var likesCount by remember(photoData.id) { mutableStateOf(photoData.initialLikes) }
    var showBigHeartAnim by remember { mutableStateOf(false) }
    var isSaved by remember(photoData.id) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    // Options Menu & Dialog States
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val displayUsername = remember(photoData) {
        photoData.creatorUsername.ifEmpty {
            photoData.creatorName.lowercase().replace(" ", "_").ifEmpty { "yousnap_user" }
        }
    }

    val isAccountOwner = remember(photoData, isOwner, currentUsername) {
        if (isOwner != null) {
            isOwner
        } else if (currentUsername.isNotEmpty() && currentUsername.equals(photoData.creatorUsername, ignoreCase = true)) {
            true
        } else {
            photoData.creatorUsername.lowercase() == "you" || photoData.creatorUsername.isEmpty()
        }
    }

    // Dynamic Comments State based on actual post
    val commentsList = remember(photoData.id) { mutableStateListOf<PhotoCommentItem>() }
    var showCommentSheet by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "likeScale"
    )

    fun toggleLike() {
        if (!isLiked) {
            isLiked = true
            likesCount += 1
            showBigHeartAnim = true
            onLikeChanged?.invoke(photoData.id, true, likesCount)
        } else {
            isLiked = false
            likesCount = (likesCount - 1).coerceAtLeast(0)
            onLikeChanged?.invoke(photoData.id, false, likesCount)
        }
    }

    fun sharePost() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out @$displayUsername's post on YouSnap!\n${photoData.imageUrl}"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share post via")
        context.startActivity(shareIntent)
    }

    fun toggleSave() {
        isSaved = !isSaved
        val msg = if (isSaved) "Saved to your collection" else "Removed from saved items"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // Instagram Gradient Brush for Profile Avatar Ring
    val instagramGradient = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFF9CE34),
                Color(0xFFEE2A7B),
                Color(0xFF6228D7),
                Color(0xFFF9CE34)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .padding(bottom = 16.dp)
    ) {
        // A) HEADER: Creator Profile Ring + Dynamic Username + Audio Title + 3-dots Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Avatar with Instagram Gradient Ring
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, instagramGradient, CircleShape)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (photoData.creatorAvatar.isNotEmpty() && (photoData.creatorAvatar.startsWith("http") || photoData.creatorAvatar.startsWith("content://"))) {
                    AsyncImage(
                        model = photoData.creatorAvatar,
                        contentDescription = photoData.creatorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val initials = if (photoData.creatorName.length >= 2) photoData.creatorName.take(2).uppercase()
                                   else if (photoData.creatorName.isNotEmpty()) photoData.creatorName.take(1).uppercase()
                                   else "Y"
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Dynamic Username + Audio Track Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayUsername,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFF1F1F1),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Original Audio - @$displayUsername",
                        color = Color(0xFFE1E1E1),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Options 3-dots Menu Icon
            Box {
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("post_3dot_menu_button_${photoData.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    if (isAccountOwner) {
                        // 1. ACCOUNT OWNER OPTIONS: Delete ONLY
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    color = Color(0xFFFF4D4D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF4D4D),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    } else {
                        // 2. OTHER USER OPTIONS: Save photo, Share profile, Report account
                        // Option 1: Save photo
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Save photo",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = "Save photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                Toast.makeText(context, "Photo saved to gallery 📥", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Option 2: Share profile
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Share profile",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out @$displayUsername's profile on YouSnap!\nhttps://yousnap.app/@$displayUsername"
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share profile via")
                                context.startActivity(shareIntent)
                            }
                        )

                        // Option 3: Report account
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Report account",
                                    color = Color(0xFFFF4D4D),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Report,
                                    contentDescription = "Report account",
                                    tint = Color(0xFFFF4D4D),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                }
            }
        }

        // B) MEDIA CONTENT VIEW (Full-Width with Double-Tap Heart Animation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Square/Full-Width Instagram ratio
                .background(Color(0xFF121212))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isLiked) {
                                toggleLike()
                            } else {
                                showBigHeartAnim = true
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photoData.imageUrl,
                contentDescription = photoData.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("instagram_post_image_${photoData.id}")
            )

            // NOTE: "1/2" carousel indicator chip overlay REMOVED per user instructions.

            // Bottom-Right Mute/Unmute Icon Button for media
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clickable { isMuted = !isMuted }
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = "Audio toggle",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(16.dp)
                )
            }

            // Big Double-Tap Heart Pop Animation
            LaunchedEffect(showBigHeartAnim) {
                if (showBigHeartAnim) {
                    kotlinx.coroutines.delay(800)
                    showBigHeartAnim = false
                }
            }

            this@Column.AnimatedVisibility(
                visible = showBigHeartAnim,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF2A6D),
                    modifier = Modifier.size(90.dp)
                )
            }
        }

        // C) ACTION BAR: Like (Heart), Comment, Share, Bookmark (Save)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Like, Comment, Share
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. LIKE HEART BUTTON
                IconButton(
                    onClick = { toggleLike() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("post_like_button_${photoData.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF2A6D) else Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(likeScale)
                    )
                }

                // 2. COMMENT BUBBLE BUTTON
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showCommentSheet = true }
                        .testTag("post_comment_button_${photoData.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    if (commentsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${commentsList.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }

                // 3. SHARE PAPERPLANE BUTTON
                IconButton(
                    onClick = { sharePost() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("post_share_button_${photoData.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right Group: 4. BOOKMARK / SAVE BUTTON
            IconButton(
                onClick = { toggleSave() },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("post_save_button_${photoData.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // D) LIKES COUNT & LIKED BY SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            if (likesCount > 0) {
                Text(
                    text = "%,d likes".format(likesCount),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            } else {
                Text(
                    text = "Be the first to like this",
                    color = Color(0xFFA8A8A8),
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // E) CAPTION: Dynamic Username + Real Caption
            if (photoData.title.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(displayUsername)
                            append(" ")
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = photoData.title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // F) COMMENTS PREVIEW SECTION
            if (commentsList.isNotEmpty()) {
                commentsList.take(2).forEach { comment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = comment.authorUsername,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = comment.text,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Comment like",
                            tint = if (comment.isLiked) Color(0xFFFF2A6D) else Color(0xFF888888),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (commentsList.size > 2) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "View all ${commentsList.size} comments",
                        color = Color(0xFFA8A8A8),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showCommentSheet = true }
                    )
                }
            } else {
                Text(
                    text = "Add a comment...",
                    color = Color(0xFF8E8E8E),
                    fontSize = 12.5.sp,
                    modifier = Modifier.clickable { showCommentSheet = true }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // G) DYNAMIC TIMESTAMP
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = photoData.timeAgo,
                    color = Color(0xFF8E8E8E),
                    fontSize = 11.5.sp
                )
            }
        }
    }

    // Delete Confirmation Dialog for Account Owner
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Photo", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this photo? This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePhoto?.invoke(photoData)
                        Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF4D4D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Report Sheet for other user's account/post
    if (showReportDialog) {
        ReportPostBottomSheet(
            postUsername = displayUsername,
            onReportSubmit = { reason, details ->
                showReportDialog = false
                Toast.makeText(context, "Report submitted for @$displayUsername. Thank you.", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showReportDialog = false }
        )
    }

    // COMMENT MODAL BOTTOM SHEET
    if (showCommentSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showCommentSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            contentColor = Color.Black,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE0E0E0))
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .background(Color.White)
                    .navigationBarsPadding()
            ) {
                // Comments Title Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comments (${commentsList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            showCommentSheet = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

                // Comments List
                if (commentsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0xFFBDBDBD),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No comments yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "Start the conversation.",
                                fontSize = 12.5.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(commentsList.size, key = { commentsList[it].id }) { idx ->
                            val comment = commentsList[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEE2A7B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = comment.authorName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = comment.authorUsername,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = comment.timeAgo,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF757575)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comment.text,
                                        fontSize = 13.sp,
                                        color = Color.Black
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        commentsList[idx] = comment.copy(isLiked = !comment.isLiked)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like comment",
                                        tint = if (comment.isLiked) Color(0xFFFF2A6D) else Color(0xFF757575),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Comment Input Box
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3897F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Y",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            val postCommentAction = {
                                val trimmed = newCommentText.trim()
                                if (trimmed.isNotEmpty()) {
                                    commentsList.add(
                                        0,
                                        PhotoCommentItem(
                                            authorName = "You",
                                            authorUsername = "you",
                                            text = trimmed,
                                            timeAgo = "Just now"
                                        )
                                    )
                                    newCommentText = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    Toast.makeText(context, "Comment posted!", Toast.LENGTH_SHORT).show()
                                }
                            }

                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = {
                                    Text("Add a comment...", color = Color(0xFF757575), fontSize = 13.sp)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { postCommentAction() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF5F5F5),
                                    unfocusedContainerColor = Color(0xFFF5F5F5),
                                    focusedBorderColor = Color(0xFF3897F0),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("instagram_comment_input_field")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val hasText = newCommentText.trim().isNotEmpty()

                            IconButton(
                                onClick = postCommentAction,
                                enabled = hasText,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (hasText) Color(0xFF3897F0) else Color(0xFFEEEEEE),
                                        CircleShape
                                    )
                                    .testTag("instagram_comment_submit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Post",
                                    tint = if (hasText) Color.White else Color(0xFF757575),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
