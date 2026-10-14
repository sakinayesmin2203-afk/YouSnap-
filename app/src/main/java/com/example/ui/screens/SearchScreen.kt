package com.example.ui.screens

import com.example.ui.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import com.example.data.model.ReelEntity
import com.example.ui.YouSnapViewModel
import com.example.ui.components.CommentBottomSheet
import com.example.ui.components.ReelItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: YouSnapViewModel,
    reels: List<ReelEntity>,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedReelForPreview by remember { mutableStateOf<ReelEntity?>(null) }

    // Filter reels based on user search query
    val filteredReels = remember(searchQuery, reels) {
        if (searchQuery.trim().isEmpty()) {
            reels
        } else {
            reels.filter {
                it.caption.contains(searchQuery, ignoreCase = true) ||
                it.creatorUsername.contains(searchQuery, ignoreCase = true) ||
                it.creatorName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search hashtags, creators, or topics...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("explore_search_bar")
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredReels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results for \"$searchQuery\"",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try searching for travel, coding, setup, or food!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Responsive Grid: 3 columns for mobile/compact, adapting beautifully
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("explore_grid"),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(filteredReels) { reel ->
                        // Parse gradient colors safely for search grid
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
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(0.dp))
                                .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
                                .clickable { selectedReelForPreview = reel }
                                .testTag("explore_item_${reel.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Dark tint
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f))
                            )

                            // Media indicator overlays (e.g., play icon)
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Reel",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            )

                            // Overlay caption info at bottom of grid item
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = "@${reel.creatorUsername}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded Video Preview Dialog when clicked on Search/Explore Grid!
        selectedReelForPreview?.let { previewReel ->
            var activePreviewReel by remember { mutableStateOf(previewReel) }
            // Observe the real database updates for likes/following in preview
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
                            Toast.makeText(
                                viewModel.app,
                                "Link copied for sharing!",
                                Toast.LENGTH_SHORT
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

                    // Bottom Sheet inside the Dialog for Comments
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
    }
}
