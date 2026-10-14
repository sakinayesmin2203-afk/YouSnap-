package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelEntity
import kotlinx.coroutines.delay

private fun formatViews(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format("%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

@Composable
fun ReelItem(
    reel: ReelEntity,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Interactive scale animation on double tap or like click
    var likeScale by remember { mutableStateOf(1f) }
    val animatedLikeScale by animateFloatAsState(
        targetValue = likeScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        finishedListener = { if (likeScale > 1f) likeScale = 1f }
    )

    // Parse start and end colors of gradient safely
    val startColor = remember(reel.bgGradientStart) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientStart)) } 
        catch (e: Exception) { Color(0xFFE1306C) }
    }
    val endColor = remember(reel.bgGradientEnd) {
        try { Color(android.graphics.Color.parseColor(reel.bgGradientEnd)) } 
        catch (e: Exception) { Color(0xFF833AB4) }
    }

    // Active high-fidelity video stream simulation using infinite transition rotation
    val infiniteTransition = rememberInfiniteTransition(label = "video_gradient")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_shift"
    )

    // Build simulated video stream colors rotating gently
    val dynamicBrush = Brush.linearGradient(
        colors = listOf(startColor, endColor, startColor),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(
            x = 800f + (gradientShift * 2f),
            y = 1200f + (gradientShift * 1.5f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullbleed Visual Background Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dynamicBrush)
                .graphicsLayer { alpha = 0.85f }
        ) {
            // Visual accent representing bokeh particles rising inside the video stream
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle static/dynamic design accent
            }
        }

        // Overlay Shadow (for subtitle readability)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Bottom and Right Sidebar layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Column: Creator and Caption details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Creator Header (Avatar, Username, Follow button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                            Text(
                                text = reel.creatorUsername.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reel.creatorUsername,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (reel.isViralBoosted) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFF9900))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VIRAL 🔥",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Text(
                            text = reel.creatorName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Follow Button
                    Button(
                        onClick = onFollowToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reel.isFollowingCreator) Color.White.copy(alpha = 0.2f) else Color.White,
                            contentColor = if (reel.isFollowingCreator) Color.White else Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("follow_button")
                    ) {
                        Text(
                            text = if (reel.isFollowingCreator) "Following" else "Follow",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

                // Caption (supports expandable lines)
                var isExpanded by remember { mutableStateOf(false) }
                Text(
                    text = reel.caption,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = if (isExpanded) 10 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(bottom = 8.dp)
                )

                // Audio Track Ticker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = "Music Note",
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

            // Right Column: Floating Interactions (Like, Comment, Share)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(60.dp)
            ) {
                // Like Interaction
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            likeScale = 1.3f
                            onLikeToggle()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(
                                scaleX = animatedLikeScale,
                                scaleY = animatedLikeScale
                            )
                            .testTag("like_button")
                    ) {
                        Icon(
                            imageVector = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (reel.isLiked) Color(0xFFE1306C) else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = reel.likesCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Comment Interaction
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onCommentClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ModeComment,
                            contentDescription = "Comment",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = reel.commentsCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Share Interaction
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Share",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                // Spinning Album Art Accent
                val albumRotateTransition = rememberInfiniteTransition(label = "album_rotation")
                val albumRotation by albumRotateTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "album_spin"
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = albumRotation }
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color.White, Color.Black)
                                )
                            )
                    )
                }
            }
        }
    }
}
