package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.AdMobRevenueTracker
import com.example.ui.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Represents a single slide in the full-screen Story Viewer queue:
 * either a user story or a full-screen Story Native Video Ad.
 */
sealed class StoryFeedEntry {
    data class UserStory(val username: String) : StoryFeedEntry()
    data class Ad(
        val adSlotId: Int,
        val adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID
    ) : StoryFeedEntry()
}

/**
 * Builds the Story Viewer playback queue with a Story Ad inserted after every
 * `adInterval` stories (default: 3 stories).
 */
fun buildStoryQueueWithAds(
    orderedUsernames: List<String>,
    fallbackUsername: String,
    adInterval: Int = 3
): List<StoryFeedEntry> {
    val cleanList = orderedUsernames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .toMutableList()

    if (cleanList.isEmpty() && fallbackUsername.trim().isNotEmpty()) {
        cleanList.add(fallbackUsername.trim())
    } else if (fallbackUsername.trim().isNotEmpty() &&
        cleanList.none { it.equals(fallbackUsername.trim(), ignoreCase = true) }
    ) {
        cleanList.add(0, fallbackUsername.trim())
    }

    if (cleanList.isEmpty()) return emptyList()

    val queue = mutableListOf<StoryFeedEntry>()
    var storyCounter = 0
    var adSlotCounter = 1

    cleanList.forEach { user ->
        queue.add(StoryFeedEntry.UserStory(user))
        storyCounter++
        if (storyCounter >= adInterval) {
            queue.add(StoryFeedEntry.Ad(adSlotId = adSlotCounter++))
            storyCounter = 0
        }
    }
    return queue
}

/**
 * Full-Screen Story Native Video Ad with:
 * - Story-style top progress bar & auto-advance timer
 * - Clear 'Ad' label & Sponsored badge
 * - Full-screen MediaView + prominent CTA button
 * - Mute/Unmute and Close controls
 * - Tap left/right edges or swipe horizontally to navigate between stories without breaking CTA clicks
 * - Safe, non-blank fallback UI if ad fails to load
 */
@Composable
fun FullScreenStoryNativeAd(
    adSlotId: Int,
    adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID,
    isLifecycleResumed: Boolean = true,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember(adSlotId) { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember(adSlotId) { mutableStateOf(true) }
    var adLoadFailed by remember(adSlotId) { mutableStateOf(false) }
    var isAdMuted by remember(adSlotId) { mutableStateOf(true) }
    var isPaused by remember(adSlotId) { mutableStateOf(false) }
    var progress by remember(adSlotId) { mutableFloatStateOf(0f) }

    val latestMuted by rememberUpdatedState(isAdMuted)
    val latestPaused by rememberUpdatedState(isPaused || !isLifecycleResumed)

    // Load Native Video Ad with safe fallback & revenue tracking
    DisposableEffect(adSlotId, adUnitId) {
        isLoading = true
        adLoadFailed = false

        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .setCustomControlsRequested(true)
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()

        try {
            val adLoader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad: NativeAd ->
                    AdMobRevenueTracker.attachNativeAdPaidEventListener(
                        nativeAd = ad,
                        creatorIdOverride = "platform",
                        mediaIdOverride = ""
                    )
                    val vc = ad.mediaContent?.videoController
                    if (vc != null && vc.hasVideoContent()) {
                        vc.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                            override fun onVideoMute(muted: Boolean) {
                                isAdMuted = muted
                            }
                        }
                        if (vc.isCustomControlsEnabled) {
                            try {
                                vc.mute(latestMuted)
                                if (latestPaused) vc.pause() else vc.play()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    nativeAd?.destroy()
                    nativeAd = ad
                    isLoading = false
                    adLoadFailed = false
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        android.util.Log.d("StoryNativeAd", "Ad notice for $adUnitId: ${loadAdError.message}")
                        isLoading = false
                        adLoadFailed = true
                    }

                    override fun onAdLoaded() {
                        isLoading = false
                        adLoadFailed = false
                    }
                })
                .withNativeAdOptions(adOptions)
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Throwable) {
            android.util.Log.e("StoryNativeAd", "Safe ad load fallback: ${e.message}")
            isLoading = false
            adLoadFailed = true
        }

        onDispose {
            try {
                nativeAd?.mediaContent?.videoController?.let { vc ->
                    if (vc.hasVideoContent() && vc.isCustomControlsEnabled) {
                        vc.pause()
                    }
                }
            } catch (_: Throwable) {
            }
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    // Sync video play/pause and mute with lifecycle & hold state
    LaunchedEffect(nativeAd, isPaused, isLifecycleResumed, isAdMuted) {
        val vc = nativeAd?.mediaContent?.videoController ?: return@LaunchedEffect
        if (vc.hasVideoContent() && vc.isCustomControlsEnabled) {
            try {
                vc.mute(isAdMuted)
                if (isPaused || !isLifecycleResumed) {
                    vc.pause()
                } else {
                    vc.play()
                }
            } catch (_: Throwable) {
            }
        }
    }

    // Timeout guard: if ad stays in loading state too long (e.g. slow network), reveal fallback UI cleanly
    LaunchedEffect(adSlotId, isLoading) {
        if (isLoading) {
            delay(4500L)
            if (isLoading && nativeAd == null) {
                isLoading = false
                adLoadFailed = true
            }
        }
    }

    // Story progress bar timer (10s for loaded ad, 5s for fallback)
    val effectivePaused = isLoading || isPaused || !isLifecycleResumed
    LaunchedEffect(adSlotId, effectivePaused, adLoadFailed, nativeAd) {
        if (!effectivePaused) {
            val totalDurationMs = if (adLoadFailed && nativeAd == null) 5000L else 10000L
            val intervalMs = 40L
            val totalSteps = (totalDurationMs / intervalMs).toInt().coerceAtLeast(1)
            var currentStep = (progress * totalSteps).toInt()
            while (currentStep <= totalSteps) {
                if (effectivePaused) break
                progress = (currentStep.toFloat() / totalSteps).coerceIn(0f, 1f)
                delay(intervalMs)
                currentStep++
            }
            if (currentStep > totalSteps && !effectivePaused) {
                onNext()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(adSlotId) {
                // Non-blocking horizontal swipe detector (Initial pass):
                // Lets normal taps on CTA / Mute / Close pass directly through, while swipes navigate stories.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startX = down.position.x
                    val startY = down.position.y
                    var handledSwipe = false
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (!handledSwipe && abs(dx) > 90f && abs(dx) > abs(dy) * 1.4f) {
                            handledSwipe = true
                            change.consume()
                            if (dx < 0f) onNext() else onPrevious()
                            break
                        }
                    }
                }
            }
            .testTag("story_ad_container_$adSlotId")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Story Progress Bar & Ad Header (matches UserStory top bar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AD",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = nativeAd?.advertiser?.takeIf { it.isNotBlank() }
                                    ?: nativeAd?.headline?.takeIf { it.isNotBlank() }
                                    ?: "Sponsored Story",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Sponsored • Ad",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 10.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFFFFD700))
                        ) {
                            Text(
                                text = "Ad",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isAdMuted = !isAdMuted },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("story_ad_mute_button_$adSlotId")
                        ) {
                            Icon(
                                imageVector = if (isAdMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isAdMuted) "Unmute Ad" else "Mute Ad",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("story_ad_close_button_$adSlotId")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Story",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Full-Screen Story Ad Media Body (sits cleanly below header so AdChoices & Close never collide)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                val loadedAd = nativeAd
                if (loadedAd != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> createStoryNativeAdView(ctx, loadedAd) },
                        update = { view -> populateStoryNativeAdView(view, loadedAd) }
                    )
                } else {
                    StoryAdFallbackContent(
                        adSlotId = adSlotId,
                        isLoading = isLoading,
                        onSkipToNext = onNext
                    )
                }

                // Left & Right Edge Tap Zones (kept away from top-right AdChoices and bottom CTA bar)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, bottom = 190.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.24f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPrevious
                            )
                            .testTag("story_ad_tap_prev_$adSlotId")
                    )
                    Spacer(modifier = Modifier.weight(0.52f))
                    Box(
                        modifier = Modifier
                            .weight(0.24f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNext
                            )
                            .testTag("story_ad_tap_next_$adSlotId")
                    )
                }
            }
        }
    }
}

private data class StoryNativeAdRefs(
    val iconView: ImageView,
    val headlineView: TextView,
    val advertiserView: TextView,
    val bodyView: TextView,
    val ctaButton: Button,
    var boundAd: NativeAd? = null
)

private fun storyDp(context: Context, dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}

private fun createStoryNativeAdView(context: Context, ad: NativeAd): NativeAdView {
    val adView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val root = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(AndroidColor.BLACK)
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setImageScaleType(ImageView.ScaleType.FIT_CENTER)
    }
    adView.mediaView = mediaView
    root.addView(mediaView)

    // Bottom Gradient Overlay with Headline, Body & CTA Button
    val bottomOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        setPadding(
            storyDp(context, 18f),
            storyDp(context, 40f),
            storyDp(context, 18f),
            storyDp(context, 22f)
        )
        background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(AndroidColor.argb(230, 0, 0, 0), AndroidColor.TRANSPARENT)
        )
    }

    val infoRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val iconView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            storyDp(context, 42f),
            storyDp(context, 42f)
        ).apply {
            marginEnd = storyDp(context, 12f)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.argb(50, 255, 255, 255))
        }
        clipToOutline = true
    }
    adView.iconView = iconView
    infoRow.addView(iconView)

    val titleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val headlineView = TextView(context).apply {
        setTextColor(AndroidColor.WHITE)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    adView.headlineView = headlineView
    titleCol.addView(headlineView)

    val advertiserView = TextView(context).apply {
        setTextColor(AndroidColor.argb(210, 255, 215, 0))
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }
    adView.advertiserView = advertiserView
    titleCol.addView(advertiserView)

    infoRow.addView(titleCol)
    bottomOverlay.addView(infoRow)

    val bodyView = TextView(context).apply {
        setTextColor(AndroidColor.argb(230, 255, 255, 255))
        textSize = 13.5f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, storyDp(context, 10f), 0, storyDp(context, 14f))
    }
    adView.bodyView = bodyView
    bottomOverlay.addView(bodyView)

    val ctaButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            storyDp(context, 48f)
        )
        setTextColor(AndroidColor.WHITE)
        textSize = 15f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = storyDp(context, 24f).toFloat()
            setColor(AndroidColor.parseColor("#0095F6"))
        }
        stateListAnimator = null
    }
    adView.callToActionView = ctaButton
    bottomOverlay.addView(ctaButton)

    root.addView(bottomOverlay)
    adView.addView(root)

    adView.tag = StoryNativeAdRefs(
        iconView = iconView,
        headlineView = headlineView,
        advertiserView = advertiserView,
        bodyView = bodyView,
        ctaButton = ctaButton
    )
    populateStoryNativeAdView(adView, ad)
    return adView
}

private fun populateStoryNativeAdView(adView: NativeAdView, ad: NativeAd) {
    val refs = adView.tag as? StoryNativeAdRefs ?: return
    if (refs.boundAd === ad) return
    refs.boundAd = ad

    refs.headlineView.text = ad.headline ?: "Sponsored"
    refs.advertiserView.text = ad.advertiser?.takeIf { it.isNotBlank() }?.let { "Ad • $it" } ?: "Ad • Sponsored"

    val icon = ad.icon
    if (icon != null && icon.drawable != null) {
        refs.iconView.setImageDrawable(icon.drawable)
        refs.iconView.visibility = View.VISIBLE
    } else {
        refs.iconView.visibility = View.GONE
    }

    if (!ad.body.isNullOrBlank()) {
        refs.bodyView.text = ad.body
        refs.bodyView.visibility = View.VISIBLE
    } else {
        refs.bodyView.visibility = View.GONE
    }

    refs.ctaButton.text = ad.callToAction?.takeIf { it.isNotBlank() } ?: "Learn More"
    refs.ctaButton.visibility = View.VISIBLE

    adView.setNativeAd(ad)
}

@Composable
private fun StoryAdFallbackContent(
    adSlotId: Int,
    isLoading: Boolean,
    onSkipToNext: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Sponsored",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isLoading) "Loading Sponsored Story..." else "Discover Featured Apps",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap or swipe anytime to continue watching stories",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(18.dp))
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFFFFD700))
            ) {
                Text(
                    text = "Ad • Sponsored",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Explore creator tools, music, and featured apps on YouSnap Partner Network.",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Opening Sponsored link...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("story_ad_fallback_cta_$adSlotId"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Learn More",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                }

                Button(
                    onClick = onSkipToNext,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("story_ad_skip_button_$adSlotId"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Next",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
