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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ads.AdMobRevenueTracker
import com.example.data.model.ReelEntity
import com.example.ui.Toast
import com.example.ui.theme.WarmCreamBackground
import com.example.ui.theme.WarmCreamTextPrimary
import com.example.ui.theme.WarmCreamTextSecondary
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

/**
 * Unified Home Feed entry model allowing Native Video Ads to sit seamlessly
 * alongside normal photo and video posts without mixing click targets.
 */
sealed class HomeFeedEntry {
    data class Photo(val photo: FullScreenPhotoData) : HomeFeedEntry()
    data class Video(val reel: ReelEntity) : HomeFeedEntry()
    data class NativeAd(
        val adSlotId: Int,
        val adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID
    ) : HomeFeedEntry()
}

/**
 * Builds the Home Feed list with a Native Video Ad inserted after every `adInterval`
 * posts (default: 5 posts across photos and videos).
 */
fun buildHomeFeedWithAds(
    photos: List<FullScreenPhotoData>,
    reels: List<ReelEntity>,
    adInterval: Int = 5
): List<HomeFeedEntry> {
    if (photos.isEmpty() && reels.isEmpty()) return emptyList()
    val entries = mutableListOf<HomeFeedEntry>()
    var postCounter = 0
    var adSlotCounter = 1

    photos.forEach { photo ->
        entries.add(HomeFeedEntry.Photo(photo))
        postCounter++
        if (postCounter >= adInterval) {
            entries.add(HomeFeedEntry.NativeAd(adSlotId = adSlotCounter++))
            postCounter = 0
        }
    }

    reels.forEach { reel ->
        entries.add(HomeFeedEntry.Video(reel))
        postCounter++
        if (postCounter >= adInterval) {
            entries.add(HomeFeedEntry.NativeAd(adSlotId = adSlotCounter++))
            postCounter = 0
        }
    }

    return entries
}

/**
 * Home Feed Native Video Ad card matching YouSnap's Warm Cream video post card.
 * - Starts muted by default and auto-plays when visible (`isAutoPlayActive == true`).
 * - Pauses automatically when scrolled out of view or when overlays open.
 * - Provides an isolated Mute/Unmute toggle button, clear "Ad" badge, and CTA button.
 * - Has no Like/Comment/Share buttons so post interactions never interfere with ad clicks.
 */
@Composable
fun HomeFeedNativeVideoAdCard(
    adSlotId: Int,
    isAutoPlayActive: Boolean,
    adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isAdMuted by remember(adSlotId) { mutableStateOf(true) }

    val latestAutoPlayActive by rememberUpdatedState(isAutoPlayActive)
    val latestAdMuted by rememberUpdatedState(isAdMuted)

    DisposableEffect(adUnitId) {
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .setCustomControlsRequested(true)
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()

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
                            vc.mute(latestAdMuted)
                            if (latestAutoPlayActive) vc.play() else vc.pause()
                        } catch (_: Throwable) {
                        }
                    }
                }
                nativeAd?.destroy()
                nativeAd = ad
                isLoading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    android.util.Log.d("HomeNativeAd", "Ad notice for $adUnitId: ${loadAdError.message}")
                    isLoading = false
                }

                override fun onAdLoaded() {
                    isLoading = false
                }
            })
            .withNativeAdOptions(adOptions)
            .build()

        try {
            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Throwable) {
            android.util.Log.d("HomeNativeAd", "Ad load notice: ${e.message}")
            isLoading = false
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

    // Sync play/pause & mute state cleanly with scroll visibility and mute button
    LaunchedEffect(nativeAd, isAutoPlayActive, isAdMuted) {
        val vc = nativeAd?.mediaContent?.videoController ?: return@LaunchedEffect
        if (vc.hasVideoContent() && vc.isCustomControlsEnabled) {
            try {
                vc.mute(isAdMuted)
                if (isAutoPlayActive) {
                    vc.play()
                } else {
                    vc.pause()
                }
            } catch (_: Throwable) {
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("home_native_ad_card_$adSlotId"),
        shape = RoundedCornerShape(20.dp),
        color = WarmCreamBackground,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE8DFC8))
    ) {
        val currentAd = nativeAd
        if (currentAd != null) {
            val hasVideo = currentAd.mediaContent?.hasVideoContent() == true
            val canControlMute = hasVideo && (currentAd.mediaContent?.videoController?.isCustomControlsEnabled == true)
            val toggleMuteAction = {
                val nextMuted = !isAdMuted
                isAdMuted = nextMuted
                val vc = currentAd.mediaContent?.videoController
                if (vc != null && vc.hasVideoContent() && vc.isCustomControlsEnabled) {
                    try {
                        vc.mute(nextMuted)
                    } catch (_: Throwable) {
                    }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    createHomeNativeAdView(
                        context = ctx,
                        ad = currentAd,
                        isMuted = isAdMuted,
                        showMuteControl = canControlMute,
                        onToggleMute = toggleMuteAction
                    )
                },
                update = { adView ->
                    updateHomeNativeAdView(
                        adView = adView,
                        ad = currentAd,
                        isMuted = isAdMuted,
                        showMuteControl = canControlMute,
                        onToggleMute = toggleMuteAction
                    )
                }
            )
        } else {
            HomeNativeAdFallbackCard(
                adSlotId = adSlotId,
                isLoading = isLoading,
                isMuted = isAdMuted,
                onToggleMute = { isAdMuted = !isAdMuted }
            )
        }
    }
}

private class AdMediaContainerFrame(context: Context) : FrameLayout(context) {
    var aspectRatio: Float = 16f / 9f
        set(value) {
            val clamped = value.coerceIn(0.85f, 1.78f)
            if (field != clamped) {
                field = clamped
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width > 0) {
            val desiredHeight = (width / aspectRatio).toInt().coerceAtLeast(1)
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}

private data class HomeNativeAdViewRefs(
    val mediaFrame: AdMediaContainerFrame,
    val mediaView: MediaView,
    val iconView: ImageView,
    val iconInitialView: TextView,
    val headlineView: TextView,
    val advertiserView: TextView,
    val bodyView: TextView,
    val ctaButton: Button,
    val muteTouchArea: FrameLayout,
    val muteIconView: ImageView,
    var boundAd: NativeAd? = null,
    var onToggleMuteClick: (() -> Unit)? = null
)

private fun dpToPx(context: Context, dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}

private fun createHomeNativeAdView(
    context: Context,
    ad: NativeAd,
    isMuted: Boolean,
    showMuteControl: Boolean,
    onToggleMute: () -> Unit
): NativeAdView {
    val primaryText = AndroidColor.parseColor("#2A1810")
    val secondaryText = AndroidColor.parseColor("#6E5A4F")
    val borderWarm = AndroidColor.parseColor("#C48B5E")
    val avatarFill = AndroidColor.parseColor("#E8DFC8")
    val adPillFill = AndroidColor.parseColor("#FFF0D6")
    val adPillStroke = AndroidColor.parseColor("#E59424")
    val adPillText = AndroidColor.parseColor("#9A5B00")
    val ctaBlue = AndroidColor.parseColor("#0095F6")

    val adView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(0, 0, 0, dpToPx(context, 12f))
    }

    // 1. Header Row (Avatar, Headline/Advertiser, and unmistakable 'Ad' Badge)
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(
            dpToPx(context, 14f),
            dpToPx(context, 10f),
            dpToPx(context, 38f), // Leave right margin clear for AdChoices icon
            dpToPx(context, 8f)
        )
    }

    val iconContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(context, 36f), dpToPx(context, 36f)).apply {
            marginEnd = dpToPx(context, 10f)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(avatarFill)
            setStroke(dpToPx(context, 1f), borderWarm)
        }
        clipToOutline = true
    }

    val iconView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val iconInitialView = TextView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        gravity = Gravity.CENTER
        text = "AD"
        setTextColor(primaryText)
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
    }
    iconContainer.addView(iconInitialView)
    iconContainer.addView(iconView)
    adView.iconView = iconView
    headerRow.addView(iconContainer)

    val titleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val headlineRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val headlineView = TextView(context).apply {
        setTextColor(primaryText)
        textSize = 13.5f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    adView.headlineView = headlineView
    headlineRow.addView(headlineView)

    val headerAdBadge = TextView(context).apply {
        text = "Ad"
        setTextColor(adPillText)
        textSize = 10.5f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dpToPx(context, 7f), dpToPx(context, 2f), dpToPx(context, 7f), dpToPx(context, 2f))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 6f).toFloat()
            setColor(adPillFill)
            setStroke(dpToPx(context, 1f), adPillStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(context, 8f)
        }
    }
    headlineRow.addView(headerAdBadge)
    titleCol.addView(headlineRow)

    val advertiserView = TextView(context).apply {
        setTextColor(secondaryText)
        textSize = 11f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    adView.advertiserView = advertiserView
    titleCol.addView(advertiserView)

    headerRow.addView(titleCol)
    container.addView(headerRow)

    // 2. Rounded Video Media Container (matching InstagramFeedPost 14dp rounded player)
    val mediaFrame = AdMediaContainerFrame(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(context, 12f)
            marginEnd = dpToPx(context, 12f)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 14f).toFloat()
            setColor(AndroidColor.parseColor("#0E0E12"))
        }
        clipToOutline = true
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setImageScaleType(ImageView.ScaleType.FIT_CENTER)
    }
    adView.mediaView = mediaView
    mediaFrame.addView(mediaView)

    // Top-left clear "Sponsored • Ad" pill inside media frame (top-right left clear for AdChoices)
    val mediaAdBadge = TextView(context).apply {
        text = "Ad • Sponsored"
        setTextColor(AndroidColor.WHITE)
        textSize = 10.5f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dpToPx(context, 9f), dpToPx(context, 4f), dpToPx(context, 9f), dpToPx(context, 4f))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 20f).toFloat()
            setColor(AndroidColor.argb(150, 0, 0, 0))
        }
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            topMargin = dpToPx(context, 10f)
            marginStart = dpToPx(context, 10f)
        }
    }
    mediaFrame.addView(mediaAdBadge)

    // Isolated 48dp touch-target Mute/Unmute button (not registered as an ad asset view)
    val muteTouchArea = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            dpToPx(context, 48f),
            dpToPx(context, 48f),
            Gravity.BOTTOM or Gravity.END
        ).apply {
            marginEnd = dpToPx(context, 6f)
            bottomMargin = dpToPx(context, 8f)
        }
        isClickable = true
        isFocusable = true
        contentDescription = if (isMuted) "Unmute video ad" else "Mute video ad"
    }

    val muteCircle = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            dpToPx(context, 36f),
            dpToPx(context, 36f),
            Gravity.CENTER
        )
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.argb(170, 0, 0, 0))
        }
    }

    val muteIconView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            dpToPx(context, 20f),
            dpToPx(context, 20f),
            Gravity.CENTER
        )
        setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
    }
    muteCircle.addView(muteIconView)
    muteTouchArea.addView(muteCircle)
    mediaFrame.addView(muteTouchArea)

    container.addView(mediaFrame)

    // 3. Body Copy
    val bodyView = TextView(context).apply {
        setTextColor(secondaryText)
        textSize = 12.5f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dpToPx(context, 14f), dpToPx(context, 10f), dpToPx(context, 14f), dpToPx(context, 6f))
    }
    adView.bodyView = bodyView
    container.addView(bodyView)

    // 4. Dedicated Full-Width CTA Button (no Like/Comment/Share buttons to prevent click confusion)
    val ctaButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 46f)
        ).apply {
            marginStart = dpToPx(context, 14f)
            marginEnd = dpToPx(context, 14f)
            topMargin = dpToPx(context, 6f)
        }
        setTextColor(AndroidColor.WHITE)
        textSize = 14f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 12f).toFloat()
            setColor(ctaBlue)
        }
        stateListAnimator = null
    }
    adView.callToActionView = ctaButton
    container.addView(ctaButton)

    adView.addView(container)

    val refs = HomeNativeAdViewRefs(
        mediaFrame = mediaFrame,
        mediaView = mediaView,
        iconView = iconView,
        iconInitialView = iconInitialView,
        headlineView = headlineView,
        advertiserView = advertiserView,
        bodyView = bodyView,
        ctaButton = ctaButton,
        muteTouchArea = muteTouchArea,
        muteIconView = muteIconView
    )
    adView.tag = refs

    muteTouchArea.setOnClickListener {
        refs.onToggleMuteClick?.invoke()
    }

    updateHomeNativeAdView(
        adView = adView,
        ad = ad,
        isMuted = isMuted,
        showMuteControl = showMuteControl,
        onToggleMute = onToggleMute
    )
    return adView
}

private fun updateHomeNativeAdView(
    adView: NativeAdView,
    ad: NativeAd,
    isMuted: Boolean,
    showMuteControl: Boolean,
    onToggleMute: () -> Unit
) {
    val refs = adView.tag as? HomeNativeAdViewRefs ?: return
    refs.onToggleMuteClick = onToggleMute

    refs.muteTouchArea.visibility = if (showMuteControl) View.VISIBLE else View.GONE
    refs.muteIconView.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
    refs.muteTouchArea.contentDescription = if (isMuted) "Unmute video ad" else "Mute video ad"

    if (refs.boundAd !== ad) {
        refs.boundAd = ad

        val mediaAspect = ad.mediaContent?.aspectRatio ?: 0f
        refs.mediaFrame.aspectRatio = if (mediaAspect > 0f) mediaAspect else (16f / 9f)

        refs.headlineView.text = ad.headline ?: "Sponsored"
        refs.advertiserView.text = ad.advertiser?.takeIf { it.isNotBlank() }?.let { "$it • Sponsored" } ?: "Sponsored"

        val icon = ad.icon
        if (icon != null && icon.drawable != null) {
            refs.iconView.setImageDrawable(icon.drawable)
            refs.iconView.visibility = View.VISIBLE
            refs.iconInitialView.visibility = View.GONE
        } else {
            refs.iconView.visibility = View.GONE
            refs.iconInitialView.visibility = View.VISIBLE
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
}

@Composable
private fun HomeNativeAdFallbackCard(
    adSlotId: Int,
    isLoading: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // Header Row matching InstagramFeedPost + Clear 'Ad' Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                        text = "AD",
                        color = WarmCreamTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YouSnap Partner Network",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = WarmCreamTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sponsored",
                        fontSize = 11.sp,
                        color = WarmCreamTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFFF0D6),
                border = BorderStroke(1.dp, Color(0xFFE59424))
            ) {
                Text(
                    text = "Ad",
                    color = Color(0xFF9A5B00),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        // Media Frame matching InstagramFeedPost 16:9 dark box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .aspectRatio(16f / 9f)
                .background(Color(0xFF0E0E12)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading Sponsored Video...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Sponsored",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Discover Featured Apps on YouSnap",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-left Ad badge
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Ad • Sponsored",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            // Mute/Unmute button matching video post placement
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 8.dp)
                    .size(48.dp)
                    .testTag("home_ad_mute_button_$adSlotId")
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
                        contentDescription = if (isMuted) "Unmute ad" else "Mute ad",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Experience top recommended creator tools and apps on YouSnap.",
            fontSize = 12.5.sp,
            color = WarmCreamTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(48.dp)
                .testTag("home_ad_cta_button_$adSlotId"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Learn More",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
