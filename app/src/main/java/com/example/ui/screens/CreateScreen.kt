package com.example.ui.screens

import com.example.ui.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.ui.YouSnapViewModel
import com.example.ui.theme.WarmCreamBackground
import com.example.ui.theme.WarmCreamTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    viewModel: YouSnapViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Active Tab Mode: "POST" (Unified Photo & Reel) and "STORY"
    var selectedTab by remember { mutableStateOf("POST") }

    // Direct Gallery Picked Uri
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var detectedMediaType by remember { mutableStateOf("photo") } // "photo" or "reel"

    // Caption and Song inputs
    var captionText by remember { mutableStateOf("") }
    var songTitleText by remember { mutableStateOf("") }

    // Android System Gallery Photo/Video Picker (Unified selector accepts Image & Video)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedMediaUri = uri
            // Smart Type Detection Logic
            val mimeType = context.contentResolver.getType(uri)
            val uriString = uri.toString().lowercase()
            val isVideo = mimeType?.startsWith("video") == true ||
                    uriString.endsWith(".mp4") || uriString.endsWith(".mkv") ||
                    uriString.endsWith(".mov") || uriString.endsWith(".3gp") ||
                    uriString.endsWith(".webm")

            detectedMediaType = if (isVideo) "reel" else "photo"

            val detectedLabel = if (detectedMediaType == "photo") "Photo" else "Reel Video"
            Toast.makeText(context, "$detectedLabel successfully selected!", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to launch unified picker
    val launchPicker = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    // Reset picked media when tab changes to avoid mismatched previews
    LaunchedEffect(selectedTab) {
        pickedMediaUri = null
        captionText = ""
        songTitleText = ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmCreamBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // -------------------------------------------------------------
            // 1. SIMPLE CLEAN TOP BAR
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmCreamBackground)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close (X) Icon
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("create_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = WarmCreamTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title
                Text(
                    text = if (selectedTab == "POST") "New Post" else "New Story",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = WarmCreamTextPrimary
                )

                // Spacer to balance top bar title
                Spacer(modifier = Modifier.size(36.dp))
            }

            HorizontalDivider(color = Color(0xFFEFE8DD), thickness = 1.dp)

            // -------------------------------------------------------------
            // 2. UNIFIED TAB SWITCHER ("POST" and "STORY")
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Unified Media Post Tab (Photo / Reel)
                FilterChip(
                    selected = selectedTab == "POST",
                    onClick = { selectedTab = "POST" },
                    label = {
                        Text(
                            text = "Photo & Reel Post",
                            fontWeight = if (selectedTab == "POST") FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Photo & Reel",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E2019),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_POST")
                )

                // Story Tab
                FilterChip(
                    selected = selectedTab == "STORY",
                    onClick = { selectedTab = "STORY" },
                    label = {
                        Text(
                            text = "Story",
                            fontWeight = if (selectedTab == "STORY") FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Story",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E2019),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_STORY")
                )
            }

            // -------------------------------------------------------------
            // 3. MAIN PREVIEW AND ACTION BODY
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Interactive Media Box
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6F0)),
                    border = BorderStroke(1.5.dp, Color(0xFFE59424)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clickable { launchPicker() }
                        .testTag("open_photo_picker_card")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pickedMediaUri != null) {
                            // Render Preview
                            AsyncImage(
                                model = pickedMediaUri,
                                contentDescription = "Gallery Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Smart Detection Type Badge at Top
                            Surface(
                                shape = RoundedCornerShape(100),
                                color = if (detectedMediaType == "photo") Color(0xFF1B5E20) else Color(0xFF0D47A1),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (detectedMediaType == "photo") Icons.Default.PhotoCamera else Icons.Default.Movie,
                                        contentDescription = "Type",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (detectedMediaType == "photo") "📸 Photo Post Detected" else "🎬 Reel Video Detected",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Overlay button to change selection
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                                    .clickable { launchPicker() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Change Selection",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Change Media",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // State empty: Prominent invitation to open unified gallery
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .background(Color(0xFFE59424).copy(alpha = 0.12f), RoundedCornerShape(100))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == "POST") Icons.Default.PhotoLibrary else Icons.Default.AutoAwesome,
                                        contentDescription = "Folder Icon",
                                        tint = Color(0xFFE59424),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Select photo or video from gallery",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = WarmCreamTextPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (selectedTab == "POST") {
                                        "Select any photo (saves to Photos) or video (saves to Reels)"
                                    } else {
                                        "Select any image or video asset for your Story"
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF8C7E75),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { launchPicker() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59424)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Open Gallery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Smart Type Detection Notice & Details Input
                if (pickedMediaUri != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEFE8DD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Category summary info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (detectedMediaType == "photo") Icons.Default.PhotoLibrary else Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFFE59424),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (detectedMediaType == "photo") {
                                        "Auto-Categorized: Photos Tab (type: 'photo')"
                                    } else {
                                        "Auto-Categorized: Reels Tab (type: 'reel')"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmCreamTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Caption Input Field
                            OutlinedTextField(
                                value = captionText,
                                onValueChange = { captionText = it },
                                label = { Text("Caption") },
                                placeholder = { Text("What's on your mind? ✨") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_caption_input"),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE59424),
                                    unfocusedBorderColor = Color(0xFFE0D8CE)
                                )
                            )

                            // If Reel Video, show optional Audio Title input
                            if (detectedMediaType == "reel") {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = songTitleText,
                                    onValueChange = { songTitleText = it },
                                    label = { Text("Audio Track") },
                                    placeholder = { Text("e.g. Original Audio") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Audiotrack,
                                            contentDescription = "Audio",
                                            tint = Color(0xFFE59424)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_song_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFE59424),
                                        unfocusedBorderColor = Color(0xFFE0D8CE)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // -------------------------------------------------------------
                // Unified Single "Upload" Button (Automatically stores in Backblaze B2)
                // -------------------------------------------------------------
                val isB2Uploading by viewModel.isB2Uploading.collectAsState()
                val b2UploadProgress by viewModel.b2UploadProgress.collectAsState()

                if (isB2Uploading) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFF007AFF).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { b2UploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF007AFF),
                                trackColor = Color(0xFF007AFF).copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Uploading to Backblaze B2: ${(b2UploadProgress * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = Color(0xFF2E2019),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val uri = pickedMediaUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select a photo or video from gallery first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        Toast.makeText(context, "Uploading to Backblaze B2...", Toast.LENGTH_SHORT).show()

                        viewModel.uploadMedia(
                            mediaUri = uri,
                            mediaType = detectedMediaType,
                            caption = captionText.trim(),
                            songTitle = songTitleText.trim(),
                            gradientStart = "#E59424",
                            gradientEnd = "#C48B5E",
                            isStory = (selectedTab == "STORY"),
                            onComplete = { onClose() }
                        )
                    },
                    enabled = !isB2Uploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pickedMediaUri != null) Color(0xFF2E2019) else Color(0xFFD6C8B4)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("bottom_submit_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isB2Uploading) "Uploading..." else "Upload",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CdnVideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
