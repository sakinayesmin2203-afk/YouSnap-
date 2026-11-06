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

    // Direct Gallery Picked Uri & detected type
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var detectedMediaType by remember { mutableStateOf("photo") } // "photo" or "reel"

    // Inputs: Title replaced Caption
    var titleText by remember { mutableStateOf("") }

    // Video Category Selection Dropdown
    val videoCategories = remember {
        listOf(
            "Entertainment",
            "Comedy",
            "Music & Dance",
            "Education & Tech",
            "Lifestyle & Vlog",
            "Gaming",
            "Fitness & Sports",
            "News & Trends",
            "Art & Creative",
            "Other"
        )
    }
    var selectedCategory by remember { mutableStateOf("Entertainment") }
    var categoryExpanded by remember { mutableStateOf(false) }

    // Android System Gallery Photo/Video Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedMediaUri = uri
            val mimeType = context.contentResolver.getType(uri)
            val uriString = uri.toString().lowercase()
            val isVideo = mimeType?.startsWith("video") == true ||
                    uriString.endsWith(".mp4") || uriString.endsWith(".mkv") ||
                    uriString.endsWith(".mov") || uriString.endsWith(".3gp") ||
                    uriString.endsWith(".webm")

            detectedMediaType = if (isVideo) "reel" else "photo"

            val detectedLabel = if (detectedMediaType == "photo") "Photo" else "Reel Video"
            Toast.makeText(context, "$detectedLabel selected!", Toast.LENGTH_SHORT).show()
        }
    }

    val launchPicker = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    // Reset picked media when tab changes
    LaunchedEffect(selectedTab) {
        pickedMediaUri = null
        titleText = ""
        selectedCategory = "Entertainment"
        categoryExpanded = false
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

                Text(
                    text = if (selectedTab == "POST") "New Post" else "New Story",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = WarmCreamTextPrimary
                )

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
            // 3. MAIN COMPACT PREVIEW & DETAILS BODY
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Item 1: Compact Media Display Box (Reduced height to 175dp / 190dp)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6F0)),
                    border = BorderStroke(1.5.dp, Color(0xFFE59424)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (pickedMediaUri != null) 175.dp else 190.dp)
                        .clickable { launchPicker() }
                        .testTag("open_photo_picker_card")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pickedMediaUri != null) {
                            // Render Compact Media Preview
                            AsyncImage(
                                model = pickedMediaUri,
                                contentDescription = "Gallery Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Media Type Badge at Top-Left
                            Surface(
                                shape = RoundedCornerShape(100),
                                color = if (detectedMediaType == "photo") Color(0xFF1B5E20) else Color(0xFF0D47A1),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (detectedMediaType == "photo") Icons.Default.PhotoCamera else Icons.Default.Movie,
                                        contentDescription = "Type",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (detectedMediaType == "photo") "📸 Photo Post" else "🎬 Reel Video",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Compact Change Media button at Bottom-Center
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                                    .clickable { launchPicker() }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Change Selection",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Change Media",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // Empty State: Compact invitation to pick media
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(Color(0xFFE59424).copy(alpha = 0.12f), RoundedCornerShape(100)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == "POST") Icons.Default.PhotoLibrary else Icons.Default.AutoAwesome,
                                        contentDescription = "Pick Media",
                                        tint = Color(0xFFE59424),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Select photo or video from gallery",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = WarmCreamTextPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { launchPicker() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59424)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Open Gallery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Details Card: Title, Category Dropdown, Audio Track
                if (pickedMediaUri != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEFE8DD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Item 3: Title Input Field (Replaces Caption)
                            OutlinedTextField(
                                value = titleText,
                                onValueChange = { titleText = it },
                                label = { Text("Title") },
                                placeholder = { Text("Enter post title... ✨") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Title,
                                        contentDescription = "Title",
                                        tint = Color(0xFFE59424)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_title_input"),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE59424),
                                    unfocusedBorderColor = Color(0xFFE0D8CE)
                                )
                            )

                            // Item 4: Video Category Dropdown Box (For Reel Video)
                            if (detectedMediaType == "reel") {
                                ExposedDropdownMenuBox(
                                    expanded = categoryExpanded,
                                    onExpandedChange = { categoryExpanded = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_category_dropdown")
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategory,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Video Category") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = "Category",
                                                tint = Color(0xFFE59424)
                                            )
                                        },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                                        },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFE59424),
                                            unfocusedBorderColor = Color(0xFFE0D8CE)
                                        )
                                    )

                                    ExposedDropdownMenu(
                                        expanded = categoryExpanded,
                                        onDismissRequest = { categoryExpanded = false }
                                    ) {
                                        videoCategories.forEach { categoryItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = categoryItem,
                                                        fontWeight = if (categoryItem == selectedCategory) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    selectedCategory = categoryItem
                                                    categoryExpanded = false
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                            )
                                        }
                                    }
                                }

                                // Item 5: Audio Track - Disabled and Non-clickable with "(Coming soon this feature)"
                                OutlinedTextField(
                                    value = "Audio Track (Coming soon this feature)",
                                    onValueChange = {},
                                    enabled = false,
                                    readOnly = true,
                                    label = { Text("Audio Track (Coming soon this feature)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Audiotrack,
                                            contentDescription = "Audio Track",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Coming Soon",
                                            tint = Color(0xFF9E9E9E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_song_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledBorderColor = Color(0xFFE0D8CE),
                                        disabledTextColor = Color(0xFF8C7E75),
                                        disabledLabelColor = Color(0xFF8C7E75),
                                        disabledLeadingIconColor = Color(0xFF8C7E75),
                                        disabledTrailingIconColor = Color(0xFF8C7E75),
                                        disabledContainerColor = Color(0xFFF7F4EF)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // -------------------------------------------------------------
                // Item 6: Live Backblaze B2 Upload Progress Indicator & Button
                // -------------------------------------------------------------
                val isB2Uploading by viewModel.isB2Uploading.collectAsState()
                val b2UploadProgress by viewModel.b2UploadProgress.collectAsState()
                val uploadPercent = (b2UploadProgress * 100).toInt().coerceIn(0, 100)

                if (isB2Uploading) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFE59424)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFE59424)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Uploading to Backblaze B2...",
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E2019),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "$uploadPercent%",
                                    fontSize = 14.sp,
                                    color = Color(0xFFE59424),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { b2UploadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFFE59424),
                                trackColor = Color(0xFFE59424).copy(alpha = 0.15f)
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
                            caption = titleText.trim(),
                            songTitle = "",
                            category = if (detectedMediaType == "reel") selectedCategory else "Photo",
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
                        .height(54.dp)
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
                            text = if (isB2Uploading) "Uploading ($uploadPercent%)..." else "Upload",
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
