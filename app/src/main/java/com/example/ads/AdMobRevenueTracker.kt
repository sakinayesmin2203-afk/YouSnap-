package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Silent background tracker for Google AdMob impression-level ad revenue (onPaidEvent).
 * Supports Banner, Native, Interstitial, and Rewarded ad formats and writes directly
 * to Firestore `/ad_events` without displaying any UI or Toast to the user.
 */
object AdMobRevenueTracker {

    private const val TAG = "AdMobRevenueTracker"
    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val monetizedUserIds = ConcurrentHashMap.newKeySet<String>()
    private val nonMonetizedUserIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var fallbackViewerUid: String = ""

    @Volatile
    private var activeCreatorId: String? = null

    @Volatile
    private var activeMediaId: String? = null

    fun setFallbackViewerUid(uid: String?) {
        fallbackViewerUid = uid?.trim().orEmpty()
    }

    fun updateUserMonetizationCache(uid: String, isApproved: Boolean) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        if (isApproved) {
            monetizedUserIds.add(cleanUid)
            nonMonetizedUserIds.remove(cleanUid)
        } else {
            monetizedUserIds.remove(cleanUid)
            nonMonetizedUserIds.add(cleanUid)
        }
    }

    fun setActiveMediaContext(creatorId: String?, mediaId: String?) {
        activeCreatorId = creatorId?.trim()?.takeIf { it.isNotBlank() }
        activeMediaId = mediaId?.trim().orEmpty()
    }

    fun clearActiveMediaContext() {
        activeCreatorId = null
        activeMediaId = null
    }

    fun attachBannerPaidEventListener(
        adView: AdView,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null
    ) {
        adView.onPaidEventListener = OnPaidEventListener { adValue ->
            recordPaidEvent(
                adValue = adValue,
                adUnitType = "banner",
                creatorIdOverride = creatorIdOverride,
                mediaIdOverride = mediaIdOverride
            )
        }
    }

    fun attachNativeAdPaidEventListener(
        nativeAd: NativeAd,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null
    ) {
        nativeAd.setOnPaidEventListener { adValue ->
            recordPaidEvent(
                adValue = adValue,
                adUnitType = "native",
                creatorIdOverride = creatorIdOverride,
                mediaIdOverride = mediaIdOverride
            )
        }
    }

    fun attachInterstitialPaidEventListener(
        interstitialAd: InterstitialAd,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null
    ) {
        interstitialAd.onPaidEventListener = OnPaidEventListener { adValue ->
            recordPaidEvent(
                adValue = adValue,
                adUnitType = "interstitial",
                creatorIdOverride = creatorIdOverride,
                mediaIdOverride = mediaIdOverride
            )
        }
    }

    fun attachRewardedPaidEventListener(
        rewardedAd: RewardedAd,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null
    ) {
        rewardedAd.onPaidEventListener = OnPaidEventListener { adValue ->
            recordPaidEvent(
                adValue = adValue,
                adUnitType = "rewarded",
                creatorIdOverride = creatorIdOverride,
                mediaIdOverride = mediaIdOverride
            )
        }
    }

    fun createTrackedBannerAdView(
        context: Context,
        adUnitId: String,
        adSize: AdSize = AdSize.BANNER,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null,
        adListener: AdListener? = null
    ): AdView {
        return AdView(context).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
            attachBannerPaidEventListener(this, creatorIdOverride, mediaIdOverride)
            if (adListener != null) {
                this.adListener = adListener
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    fun loadTrackedInterstitialAd(
        context: Context,
        adUnitId: String,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null,
        onLoaded: (InterstitialAd) -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    attachInterstitialPaidEventListener(
                        interstitialAd = interstitialAd,
                        creatorIdOverride = creatorIdOverride,
                        mediaIdOverride = mediaIdOverride
                    )
                    onLoaded(interstitialAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    onFailed(loadAdError)
                }
            }
        )
    }

    fun loadTrackedRewardedAd(
        context: Context,
        adUnitId: String,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null,
        onLoaded: (RewardedAd) -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    attachRewardedPaidEventListener(
                        rewardedAd = rewardedAd,
                        creatorIdOverride = creatorIdOverride,
                        mediaIdOverride = mediaIdOverride
                    )
                    onLoaded(rewardedAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    onFailed(loadAdError)
                }
            }
        )
    }

    fun showTrackedRewardedAd(
        activity: Activity,
        rewardedAd: RewardedAd,
        onUserEarnedReward: (Int, String) -> Unit = { _, _ -> }
    ) {
        rewardedAd.show(activity) { rewardItem ->
            onUserEarnedReward(rewardItem.amount, rewardItem.type)
        }
    }

    fun recordPaidEvent(
        adValue: AdValue,
        adUnitType: String,
        creatorIdOverride: String? = null,
        mediaIdOverride: String? = null
    ) {
        val valueMicros = adValue.valueMicros
        if (valueMicros <= 0L) return

        val snapshotCreatorOverride = creatorIdOverride?.trim()
        val snapshotMediaOverride = mediaIdOverride?.trim()
        val snapshotActiveCreator = activeCreatorId
        val snapshotActiveMedia = activeMediaId
        val currencyCode = adValue.currencyCode.ifBlank { "USD" }
        val amountUsd = valueMicros.toDouble() / 1_000_000.0
        val viewerUid = try {
            FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty().ifBlank { fallbackViewerUid }
        } catch (_: Throwable) {
            fallbackViewerUid
        }

        trackerScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val rawCandidateCreator = when {
                    snapshotCreatorOverride.equals("platform", ignoreCase = true) -> "platform"
                    !snapshotCreatorOverride.isNullOrBlank() -> snapshotCreatorOverride
                    !snapshotActiveCreator.isNullOrBlank() -> snapshotActiveCreator
                    else -> "platform"
                }

                val isCreatorEligible = if (rawCandidateCreator == "platform" || rawCandidateCreator == viewerUid) {
                    false
                } else if (rawCandidateCreator in monetizedUserIds) {
                    true
                } else if (rawCandidateCreator in nonMonetizedUserIds) {
                    false
                } else {
                    try {
                        val userDoc = db.collection("users").document(rawCandidateCreator).get().await()
                        val approved = userDoc.exists() && (
                            userDoc.getBoolean("isMonetized") == true ||
                                userDoc.getString("monetizationStatus")?.trim().equals("Approved", ignoreCase = true)
                            )
                        updateUserMonetizationCache(rawCandidateCreator, approved)
                        approved
                    } catch (_: Throwable) {
                        false
                    }
                }

                val finalCreatorId = if (isCreatorEligible) rawCandidateCreator else "platform"
                val finalMediaId = when {
                    finalCreatorId == "platform" -> ""
                    !snapshotMediaOverride.isNullOrBlank() -> snapshotMediaOverride
                    else -> snapshotActiveMedia.orEmpty()
                }

                val eventPayload = hashMapOf<String, Any>(
                    "eventType" to "ad_revenue",
                    "creatorId" to finalCreatorId,
                    "mediaId" to finalMediaId,
                    "viewerId" to viewerUid,
                    "adUnitType" to adUnitType.lowercase(),
                    "valueMicros" to valueMicros,
                    "amountUsd" to amountUsd,
                    "currencyCode" to currencyCode,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("ad_events").add(eventPayload).await()
            } catch (t: Throwable) {
                Log.w(TAG, "Silent ad_revenue write notice: ${t.message}")
            }
        }
    }
}
