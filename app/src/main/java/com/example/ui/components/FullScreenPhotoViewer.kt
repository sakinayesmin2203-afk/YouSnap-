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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

data class FullScreenPhotoData(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val creatorName: String,
    val creatorUsername: String,
    val creatorAvatar: String,
    val initialLikes: Int,
    val initialComments: Int,
    val initialIsLiked: Boolean = false,
    val timeAgo: String = "2 hours ago"
)

data class PhotoCommentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val authorName: String,
    val authorUsername: String,
    val text: String,
    val timeAgo: String = "Just now"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    photoData: FullScreenPhotoData,
    onDismiss: () -> Unit,
    onLikeChanged: ((photoId: Int, isLiked: Boolean, newCount: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isLiked by remember(photoData.id) { mutableStateOf(photoData.initialIsLiked) }
    var likesCount by remember(photoData.id) { mutableStateOf(photoData.initialLikes) }
    var showBigHeartAnim by remember { mutableStateOf(false) }

    // Comments State
    val commentsList = remember(photoData.id) {
        mutableStateListOf(
            PhotoCommentItem(
                authorName = "Priyanka Bora",
                authorUsername = "priya_b",
                text = "Absolutely stunning capture! The lighting is so pure. ✨",
                timeAgo = "1 hour ago"
            ),
            PhotoCommentItem(
                authorName = "Rahul Sharma",
                authorUsername = "rahul_clicks",
                text = "Love this perspective! Which spot was this taken at? 📸",
                timeAgo = "45 mins ago"
            ),
            PhotoCommentItem(
                authorName = "Ananya Das",
                authorUsername = "ananya_d",
                text = "Gorgeous view! Adding this to my travel bucket list 🔥",
                timeAgo = "20 mins ago"
            )
        )
    }
    var showCommentSheet by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    var isSaved by remember(photoData.id) { mutableStateOf(false) }

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

    fun sharePhoto() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out this photo by ${photoData.creatorName} (@${photoData.creatorUsername}): ${photoData.title}\n${photoData.imageUrl}"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share photo via")
        context.startActivity(shareIntent)
    }

    fun toggleSave() {
        isSaved = !isSaved
        val msg = if (isSaved) "Saved to your collection" else "Removed from saved items"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = {
            if (showCommentSheet) {
                showCommentSheet = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050505))
        ) {
            // Main Content: Column with Top Bar, Center Photo, and Bottom Facebook Action Bar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1. TOP BAR (Facebook style: Close button + Creator info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back / Close button (X)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape)
                            .testTag("photo_viewer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Creator Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = photoData.creatorAvatar,
                            contentDescription = photoData.creatorName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Creator Name & Username + Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = photoData.creatorName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${photoData.creatorUsername}",
                                color = Color(0xFFB0B3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = " • ${photoData.timeAgo}",
                                color = Color(0xFF8A8D91),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. CENTER: Full Photo display with Double-tap to Like
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                    // Photo image
                    AsyncImage(
                        model = photoData.imageUrl,
                        contentDescription = photoData.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("fullscreen_photo_image")
                    )

                    // Big Double-Tap Heart Animation
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
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF2A6D),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Photo Title / Caption Text
                if (photoData.title.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF111111))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = photoData.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. BOTTOM: Facebook-Style Reactions & Action Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF161616), Color(0xFF0D0D0D))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                ) {
                    // Reactions Count Summary Row (Facebook style: Thumb & Heart icons + count)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Like Reaction indicators
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Facebook Blue Thumb Circle
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFF1877F2), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ThumbUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            // Red Heart Circle (overlapped)
                            Box(
                                modifier = Modifier
                                    .offset(x = (-4).dp)
                                    .size(20.dp)
                                    .background(Color(0xFFFA383E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "%,d likes".format(likesCount),
                                color = Color(0xFFB0B3B8),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("photo_viewer_likes_count")
                            )
                        }

                        // Right: Comments and Shares summary
                        Text(
                            text = "${commentsList.size} comments • 18 shares",
                            color = Color(0xFFB0B3B8),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showCommentSheet = true }
                        )
                    }

                    // Divider Line
                    HorizontalDivider(
                        color = Color(0xFF333333),
                        thickness = 0.7.dp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    // Four Clean Facebook-Style Action Buttons: Like, Comment, Share, Save (Matching Facebook Reference Screenshot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. LIKE BUTTON
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { toggleLike() }
                                .padding(vertical = 10.dp)
                                .testTag("photo_viewer_like_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (isLiked) Color(0xFF1877F2) else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(likeScale)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLiked) "Liked" else "Like",
                                color = if (isLiked) Color(0xFF1877F2) else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }

                        // 2. COMMENT BUTTON
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCommentSheet = true }
                                .padding(vertical = 10.dp)
                                .testTag("photo_viewer_comment_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Comment",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Comment",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 3. SHARE BUTTON
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sharePhoto() }
                                .padding(vertical = 10.dp)
                                .testTag("photo_viewer_share_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 4. SAVE BUTTON (From user's reference screenshot)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { toggleSave() }
                                .padding(vertical = 10.dp)
                                .testTag("photo_viewer_save_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isSaved) Color(0xFF1877F2) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) "Saved" else "Save",
                                color = if (isSaved) Color(0xFF1877F2) else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSaved) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 4. COMMENT BOTTOM SHEET OVERLAY (Clean White Facebook-Style ModalBottomSheet)
            if (showCommentSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        showCommentSheet = false
                    },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color.White,
                    contentColor = Color(0xFF050505),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    dragHandle = null,
                    modifier = Modifier.fillMaxHeight(0.75f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 6.dp)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFCCD0D5))
                                .align(Alignment.CenterHorizontally)
                        )

                        // Header: Title & Close Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comments (${commentsList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF050505)
                            )
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    showCommentSheet = false
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFF0F2F5), CircleShape)
                                    .testTag("photo_comment_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close comments",
                                    tint = Color(0xFF65676B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE4E6EB), thickness = 0.8.dp)

                        // Comments List (weight 1f so it shrinks flexibly when keyboard opens)
                        if (commentsList.isEmpty()) {
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
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFBCC0C8),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No comments yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF050505)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Be the first to share your thoughts!",
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF65676B)
                                    )
                                }
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        })
                                    },
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(commentsList, key = { it.id }) { comment ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Author Avatar with clean colorful circle
                                        val avatarColor = remember(comment.authorName) {
                                            when (comment.authorName.hashCode().mod(5)) {
                                                0 -> Color(0xFF1877F2) // FB Blue
                                                1 -> Color(0xFF42B72A) // Green
                                                2 -> Color(0xFFFA383E) // Coral
                                                3 -> Color(0xFF8B5CF6) // Purple
                                                else -> Color(0xFFF59E0B) // Amber
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(avatarColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = comment.authorName.take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Author Name & Comment Bubble (Classic Facebook Soft Gray Bubble on White)
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(Color(0xFFF0F2F5))
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = comment.authorName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = Color(0xFF050505)
                                                )
                                                Text(
                                                    text = comment.timeAgo,
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF65676B)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = comment.text,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF1C1E21),
                                                lineHeight = 18.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Input Field (Clean White Surface with Soft Gray Pill Field)
                        Surface(
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                HorizontalDivider(color = Color(0xFFE4E6EB), thickness = 0.8.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Current User Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1877F2)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Y",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
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
                                            Text("Write a comment...", color = Color(0xFF65676B), fontSize = 13.5.sp)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = ImeAction.Send
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = { postCommentAction() }
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFFF0F2F5),
                                            unfocusedContainerColor = Color(0xFFF0F2F5),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = Color(0xFF050505),
                                            unfocusedTextColor = Color(0xFF050505)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("photo_comment_input_field")
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    val hasCommentText = newCommentText.trim().isNotEmpty()

                                    IconButton(
                                        onClick = postCommentAction,
                                        enabled = hasCommentText,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(
                                                if (hasCommentText) Color(0xFF1877F2) else Color(0xFFE4E6EB),
                                                CircleShape
                                            )
                                            .testTag("photo_comment_submit_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Post Comment",
                                            tint = if (hasCommentText) Color.White else Color(0xFF8A8D91),
                                            modifier = Modifier.size(18.dp)
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
}
