package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.ReelEntity
import com.example.ui.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

// Production Native Ad Unit provided by user
const val ADMOB_NATIVE_AD_UNIT_ID = "ca-app-pub-2077385225737134/9497192934"

// Google Official Sample Native Video Ad Unit (used as test fallback when live publisher data is pending approval)
const val ADMOB_SAMPLE_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

/**
 * Model representing an item in the Reels vertical feed:
 * either a real video reel or a full-screen Native Ad.
 */
sealed class ReelsFeedItem {
    data class Video(val reel: ReelEntity) : ReelsFeedItem()
    data class Ad(val adSlotId: Int, val adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID) : ReelsFeedItem()
}

/**
 * Helper function to inject a Native Ad after every `adInterval` video reels (default: 6 videos).
 */
fun buildReelsFeedWithAds(reels: List<ReelEntity>, adInterval: Int = 6): List<ReelsFeedItem> {
    if (reels.isEmpty()) return emptyList()
    val feed = mutableListOf<ReelsFeedItem>()
    var videoCounter = 0
    var adSlotCounter = 1

    reels.forEach { reel ->
        feed.add(ReelsFeedItem.Video(reel))
        videoCounter++
        if (videoCounter >= adInterval) {
            feed.add(ReelsFeedItem.Ad(adSlotId = adSlotCounter++))
            videoCounter = 0
        }
    }
    return feed
}

/**
 * Full-Screen Native Video Ad Composable mimicking YouTube Shorts / Instagram Reels Ads.
 */
@Composable
fun FullScreenNativeVideoAd(
    adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeAdUnitId by remember(adUnitId) { mutableStateOf(adUnitId) }
    var triedSampleFallback by remember(adUnitId) { mutableStateOf(false) }

    DisposableEffect(activeAdUnitId) {
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT)
            .build()

        val adLoader = AdLoader.Builder(context, activeAdUnitId)
            .forNativeAd { ad: NativeAd ->
                nativeAd?.destroy()
                nativeAd = ad
                isLoading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    android.util.Log.d("ReelsNativeAd", "Ad notice for $activeAdUnitId: ${loadAdError.message}")
                    // If live ad has "Publisher data not found", try Google's official test ad unit
                    if (!triedSampleFallback && activeAdUnitId != ADMOB_SAMPLE_NATIVE_AD_UNIT_ID) {
                        triedSampleFallback = true
                        activeAdUnitId = ADMOB_SAMPLE_NATIVE_AD_UNIT_ID
                    } else {
                        isLoading = false
                    }
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
            android.util.Log.d("ReelsNativeAd", "Ad load notice: ${e.message}")
            if (!triedSampleFallback && activeAdUnitId != ADMOB_SAMPLE_NATIVE_AD_UNIT_ID) {
                triedSampleFallback = true
                activeAdUnitId = ADMOB_SAMPLE_NATIVE_AD_UNIT_ID
            } else {
                isLoading = false
            }
        }

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val currentAd = nativeAd

        if (currentAd != null) {
            // Render Real AdMob NativeAdView with AndroidView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    createNativeAdViewLayout(ctx, currentAd)
                },
                update = { view ->
                    populateNativeAdView(view, currentAd)
                }
            )
        } else {
            // Polished Shorts/Reels styled sponsor preview while loading or during account review
            AdFallbackPreview(isLoading = isLoading)
        }

        // Top "Sponsored • Ad" indicator badge
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Sponsored • Ad",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Creates a native Android NativeAdView programmatically for Compose rendering.
 */
private fun createNativeAdViewLayout(context: Context, ad: NativeAd): NativeAdView {
    val adView = NativeAdView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val rootLayout = android.widget.FrameLayout(context).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    // MediaView to play full-screen native video/image
    val mediaView = MediaView(context).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    adView.mediaView = mediaView
    rootLayout.addView(mediaView)

    // Gradient overlay for bottom readable text
    val bottomOverlay = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM
        )
        setPadding(40, 60, 40, 100)
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(android.graphics.Color.argb(220, 0, 0, 0), android.graphics.Color.TRANSPARENT)
        )
    }

    // Header row with Icon, Headline, and Advertiser
    val headerRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val iconView = ImageView(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(110, 110).apply {
            marginEnd = 24
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    adView.iconView = iconView
    headerRow.addView(iconView)

    val titleCol = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val headlineView = TextView(context).apply {
        setTextColor(android.graphics.Color.WHITE)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    adView.headlineView = headlineView
    titleCol.addView(headlineView)

    val advertiserView = TextView(context).apply {
        setTextColor(android.graphics.Color.argb(200, 255, 255, 255))
        textSize = 12f
        maxLines = 1
    }
    adView.advertiserView = advertiserView
    titleCol.addView(advertiserView)

    headerRow.addView(titleCol)
    bottomOverlay.addView(headerRow)

    // Body text
    val bodyView = TextView(context).apply {
        setTextColor(android.graphics.Color.argb(230, 255, 255, 255))
        textSize = 13f
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, 16, 0, 20)
    }
    adView.bodyView = bodyView
    bottomOverlay.addView(bodyView)

    // Call to action button (e.g. "Install Now", "Learn More")
    val ctaButton = Button(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            130
        )
        setTextColor(android.graphics.Color.WHITE)
        textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        val buttonBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(android.graphics.Color.parseColor("#0095F6"))
        }
        background = buttonBg
    }
    adView.callToActionView = ctaButton
    bottomOverlay.addView(ctaButton)

    rootLayout.addView(bottomOverlay)
    adView.addView(rootLayout)

    populateNativeAdView(adView, ad)
    return adView
}

/**
 * Populates data from NativeAd into the NativeAdView components.
 */
private fun populateNativeAdView(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline

    val icon = ad.icon
    if (icon != null && icon.drawable != null) {
        (adView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
        adView.iconView?.visibility = View.VISIBLE
    } else {
        adView.iconView?.visibility = View.GONE
    }

    if (ad.body != null) {
        (adView.bodyView as? TextView)?.text = ad.body
        adView.bodyView?.visibility = View.VISIBLE
    } else {
        adView.bodyView?.visibility = View.GONE
    }

    if (ad.advertiser != null) {
        (adView.advertiserView as? TextView)?.text = ad.advertiser
        adView.advertiserView?.visibility = View.VISIBLE
    } else {
        adView.advertiserView?.visibility = View.GONE
    }

    if (ad.callToAction != null) {
        (adView.callToActionView as? Button)?.text = ad.callToAction
        adView.callToActionView?.visibility = View.VISIBLE
    } else {
        (adView.callToActionView as? Button)?.text = "Learn More"
        adView.callToActionView?.visibility = View.VISIBLE
    }

    adView.setNativeAd(ad)
}

/**
 * Fallback UI component mimicking full-screen YouTube Shorts / Reels sponsored video.
 */
@Composable
fun AdFallbackPreview(isLoading: Boolean) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "ad_bg_anim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anim_progress"
    )

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F2027),
            Color(0xFF203A43),
            Color(0xFF2C5364)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // Center Media Graphic
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Sponsored",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isLoading) "Loading Sponsor..." else "Discover Featured Apps",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Special creator offers & partners on YouSnap",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }

        // Bottom Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0095F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "YouSnap Partner Network",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "4.9 ★ Sponsored",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text(
                text = "Experience top recommended creator tools, music samples, and entertainment on YouSnap.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Opening Sponsored link...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0095F6)
                ),
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
}
