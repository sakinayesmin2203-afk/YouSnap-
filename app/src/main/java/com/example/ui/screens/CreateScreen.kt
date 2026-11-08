package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.Toast
import com.example.ui.YouSnapViewModel
import com.example.ui.theme.WarmCreamBackground
import com.example.ui.theme.WarmCreamTextPrimary

data class GradientPreset(
    val name: String,
    val startColorHex: String,
    val endColorHex: String,
    val startColor: Color,
    val endColor: Color
)

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

    // Story Sub-Mode: "MEDIA" (Photo/Video with Text Overlay) or "TEXT_ONLY" (Text-Only Story)
    var storySubMode by remember { mutableStateOf("MEDIA") }

    // Direct Gallery Picked Uri & detected type
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var detectedMediaType by remember { mutableStateOf("photo") } // "photo" or "reel"

    // Inputs: Title for Post, Text Overlay for Media Story, Text for Text-Only Story
    var titleText by remember { mutableStateOf("") }
    var storyTextOverlay by remember { mutableStateOf("") }
    var textOnlyStoryText by remember { mutableStateOf("") }

    // Gradient Presets for Text-Only Story
    val gradientPresets = remember {
        listOf(
            GradientPreset("Sunset", "#FF512F", "#DD2476", Color(0xFFFF512F), Color(0xFFDD2476)),
            GradientPreset("Neon Violet", "#8E2DE2", "#4A00E0", Color(0xFF8E2DE2), Color(0xFF4A00E0)),
            GradientPreset("Emerald Teal", "#11998E", "#38EF7D", Color(0xFF11998E), Color(0xFF38EF7D)),
            GradientPreset("Golden Hour", "#F2994A", "#F2C94C", Color(0xFFF2994A), Color(0xFFF2C94C)),
            GradientPreset("Passion Red", "#FF416C", "#FF4B2B", Color(0xFFFF416C), Color(0xFFFF4B2B)),
            GradientPreset("Midnight", "#232526", "#414345", Color(0xFF232526), Color(0xFF414345))
        )
    }
    var selectedGradientPreset by remember { mutableStateOf(gradientPresets[0]) }

    // Video Category Selection Dropdown for Posts
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
        storyTextOverlay = ""
        textOnlyStoryText = ""
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
            // 1. TOP BAR
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
            // 3. STORY SUB-MODE SWITCHER (If STORY is selected)
            // -------------------------------------------------------------
            if (selectedTab == "STORY") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedFilterChip(
                        selected = storySubMode == "MEDIA",
                        onClick = { storySubMode = "MEDIA" },
                        label = {
                            Text(
                                text = "📸 Photo / Video Story",
                                fontSize = 12.5.sp,
                                fontWeight = if (storySubMode == "MEDIA") FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            selectedContainerColor = Color(0xFFE59424),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFFAF6F0),
                            labelColor = WarmCreamTextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_sub_mode_media")
                    )

                    ElevatedFilterChip(
                        selected = storySubMode == "TEXT_ONLY",
                        onClick = { storySubMode = "TEXT_ONLY" },
                        label = {
                            Text(
                                text = "✍️ Text-Only Story",
                                fontSize = 12.5.sp,
                                fontWeight = if (storySubMode == "TEXT_ONLY") FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            selectedContainerColor = Color(0xFFE59424),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFFAF6F0),
                            labelColor = WarmCreamTextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_sub_mode_text_only")
                    )
                }
            }

            // -------------------------------------------------------------
            // 4. MAIN CONTENT BODY
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // =========================================================
                // CASE 1: STORY MODE - TEXT ONLY STORY
                // =========================================================
                if (selectedTab == "STORY" && storySubMode == "TEXT_ONLY") {
                    
                    // Live Interactive Text-Only Story Card (Direct Typing inside Card)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .testTag("text_story_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            selectedGradientPreset.startColor,
                                            selectedGradientPreset.endColor
                                        )
                                    )
                                )
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Direct Interactive Text Field inside Colored Gradient Card
                            androidx.compose.foundation.text.BasicTextField(
                                value = textOnlyStoryText,
                                onValueChange = { textOnlyStoryText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .testTag("text_story_input"),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        offset = Offset(2f, 2f),
                                        blurRadius = 6f
                                    )
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (textOnlyStoryText.isEmpty()) {
                                            Text(
                                                text = "Type your story here... ✍️",
                                                color = Color.White.copy(alpha = 0.75f),
                                                fontSize = 21.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                style = TextStyle(
                                                    shadow = Shadow(
                                                        color = Color.Black.copy(alpha = 0.4f),
                                                        offset = Offset(2f, 2f),
                                                        blurRadius = 6f
                                                    )
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            // Top badge
                            Surface(
                                shape = RoundedCornerShape(100),
                                color = Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Text Story",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Background Gradient Color Selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Select Background Style",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmCreamTextPrimary,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            gradientPresets.forEach { preset ->
                                val isSelected = preset.name == selectedGradientPreset.name
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(preset.startColor, preset.endColor)
                                            )
                                        )
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF2E2019) else Color.White.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedGradientPreset = preset },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                } else {
                    // =========================================================
                    // CASE 2: POST OR MEDIA STORY (PHOTO / VIDEO)
                    // =========================================================

                    // Compact Media Display Box
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6F0)),
                        border = BorderStroke(1.5.dp, Color(0xFFE59424)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (pickedMediaUri != null) 200.dp else 190.dp)
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

                                // Real-time Text Overlay preview for Story mode
                                if (selectedTab == "STORY" && storyTextOverlay.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = storyTextOverlay,
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

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
                                            text = if (detectedMediaType == "photo") "📸 Photo" else "🎬 Reel Video",
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

                    // Details Card: Title (for Post) or Text Overlay (for Story)
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
                                if (selectedTab == "POST") {
                                    // Title Input Field ONLY for POST
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

                                    // Category Dropdown for Reel Video Posts
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

                                        // Audio Track field
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
                                } else {
                                    // STORY MODE (Media Selected): NO Title option! Add Text Overlay input option
                                    OutlinedTextField(
                                        value = storyTextOverlay,
                                        onValueChange = { storyTextOverlay = it },
                                        label = { Text("Text / Caption Overlay") },
                                        placeholder = { Text("Write text to overlay on story... ✏️") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.TextFields,
                                                contentDescription = "Text Overlay",
                                                tint = Color(0xFFE59424)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("story_text_overlay_input"),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = false,
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFE59424),
                                            unfocusedBorderColor = Color(0xFFE0D8CE)
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // -------------------------------------------------------------
                // Live Upload Progress Indicator & Button
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
                                        text = "Uploading story media...",
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
                        if (selectedTab == "STORY" && storySubMode == "TEXT_ONLY") {
                            // Text-Only Story Upload
                            val text = textOnlyStoryText.trim()
                            if (text.isEmpty()) {
                                Toast.makeText(context, "Please enter some text for your story.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            viewModel.uploadTextStory(
                                text = text,
                                gradientStart = selectedGradientPreset.startColorHex,
                                gradientEnd = selectedGradientPreset.endColorHex,
                                onComplete = { onClose() }
                            )
                        } else {
                            // Media Upload (Post or Media Story)
                            val uri = pickedMediaUri
                            if (uri == null) {
                                Toast.makeText(context, "Please select a photo or video from gallery first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val captionToUse = if (selectedTab == "STORY") storyTextOverlay.trim() else titleText.trim()

                            viewModel.uploadMedia(
                                mediaUri = uri,
                                mediaType = detectedMediaType,
                                caption = captionToUse,
                                songTitle = "",
                                category = if (detectedMediaType == "reel" && selectedTab == "POST") selectedCategory else "Photo",
                                gradientStart = "#E59424",
                                gradientEnd = "#C48B5E",
                                isStory = (selectedTab == "STORY"),
                                onComplete = { onClose() }
                            )
                        }
                    },
                    enabled = !isB2Uploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (
                            (selectedTab == "STORY" && storySubMode == "TEXT_ONLY" && textOnlyStoryText.isNotBlank()) ||
                            (pickedMediaUri != null)
                        ) Color(0xFF2E2019) else Color(0xFFD6C8B4)
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
