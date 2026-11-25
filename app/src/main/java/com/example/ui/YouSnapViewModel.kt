package com.example.ui

import android.app.Application
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.annotation.Keep
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdMobRevenueTracker
import com.example.data.model.*
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.utils.CompressionState
import com.example.ui.utils.DeepLinkHelper
import com.example.ui.utils.FeedRankingAlgorithm
import com.example.ui.utils.ProfilePhotoUtils
import com.example.ui.utils.SecurityUtils
import com.example.ui.utils.VideoCompressor
import com.example.ui.utils.YouSnapDeepLink
import android.widget.Toast
import com.example.data.YouSnapDatabase
import com.example.data.repository.DeleteResult
import com.example.data.repository.SupabaseRepository
import com.example.data.repository.UploadResult
import com.example.data.repository.UploadTransferState
import com.example.notifications.YouSnapNotificationHelper
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Stack
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

sealed class Screen {
    object Splash : Screen()
    object Login : Screen()
    object SignUp : Screen()
    object ForgotPassword : Screen()
    object Home : Screen()
    data class Reels(val startReelId: Int? = null, val startMediaUrl: String? = null) : Screen()
    object Search : Screen()
    object Create : Screen()
    object Notifications : Screen()
    object Profile : Screen()
    data class OtherProfile(val username: String) : Screen()
    object Settings : Screen()
    object EditProfile : Screen()
    object PrivacySecurity : Screen()
    object BlockedAccounts : Screen()
    object WatchHistory : Screen()
    object LoginActivity : Screen()
    object ContentPreferences : Screen()
    object CreatorTools : Screen()
    object BoostReel : Screen()
    object InsightsScreen : Screen()
    object MonetizationScreen : Screen()
    object MonetizationRules : Screen()
    object MonetizationForm : Screen()
    object NotificationSettings : Screen()
    object AppPreferences : Screen()
    object DataStorage : Screen()
    object SupportHelp : Screen()
    object StoryLivePrivacy : Screen()
    object ClearSearchData : Screen()
    object PinLock : Screen()
}

class YouSnapViewModel(application: Application) : AndroidViewModel(application) {

    val app = application
    private val supabaseRepository = SupabaseRepository()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val prefs = application.getSharedPreferences("yousnap_prefs", android.content.Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(SettingsEntity())
    val settingsState: StateFlow<SettingsEntity> = _settingsState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileEntity(name = "User", username = "user"))
    val profileState: StateFlow<ProfileEntity> = _profileState.asStateFlow()

    private val _reelsState = MutableStateFlow<List<ReelEntity>>(emptyList())
    val reelsState: StateFlow<List<ReelEntity>> = _reelsState.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _fontScaleFlow = MutableStateFlow(1.0f)
    val fontScaleFlow: StateFlow<Float> = _fontScaleFlow.asStateFlow()

    val personalPhotosState = MutableStateFlow<List<FullScreenPhotoData>>(emptyList())
    val allStories = MutableStateFlow<List<StoryModel>>(emptyList())
    val selectedHomePost = MutableStateFlow<FullScreenPhotoData?>(null)
    val homePostComments = MutableStateFlow<Map<String, List<CommentEntity>>>(emptyMap())
    val homePostLikedState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val homePostLikesCount = MutableStateFlow<Map<String, Int>>(emptyMap())
    val homePostSharesCount = MutableStateFlow<Map<String, Int>>(emptyMap())
    val homePostBookmarked = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isHomeRefreshing = MutableStateFlow(false)
    val userPrivacyMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val otherUserIsFollowing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isB2Uploading = MutableStateFlow(false)
    val b2UploadProgress = MutableStateFlow(0f)
    val uploadStatusMessage = MutableStateFlow("Preparing media...")
    val uploadErrorMessage = MutableStateFlow<String?>(null)
    val activeReelComments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val reelCommentsByReelId = MutableStateFlow<Map<Int, List<CommentEntity>>>(emptyMap())
    val currentCommentText = MutableStateFlow("")
    private val recentlyUploadedUrls = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inFlightUploadDocKeys = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inFlightPostLikeMutations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inFlightPostCommentMutations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inFlightPostShareMutations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inFlightReelLikeMutations = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val inFlightReelCommentMutations = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val inFlightReelShareMutations = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private var refreshFeedJob: kotlinx.coroutines.Job? = null
    private val pendingSyncLock = Any()
    private val reelFirestoreDocIdByMediaUrl = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val reelFirestoreDocIdByTimestamp = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val reelFirestoreDocIdById = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val reelCollectionByMediaUrl = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val reelCollectionById = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val userUidByUsername = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val usernameByUid = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val profileLinkByUid = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val photoShareLinkByKey = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val reelShareLinkByKey = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sessionBackfilledProfileLinkUids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val storyCreatorUidByDocId = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val storyCreatorUidByOwner = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val monetizedUserIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val nonMonetizedUserIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sessionLoggedAdViewMediaKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var cachedRemoteReels: List<ReelEntity> = emptyList()
    private var cachedRemoteVideos: List<ReelEntity> = emptyList()
    private var restrictedReelDocIds: Set<String> = emptySet()
    private var restrictedVideoDocIds: Set<String> = emptySet()
    private var storiesCollectionListener: ListenerRegistration? = null
    private var postsCollectionListener: ListenerRegistration? = null
    private var reelsCollectionListener: ListenerRegistration? = null
    private var videosCollectionListener: ListenerRegistration? = null
    private var usersCollectionListener: ListenerRegistration? = null
    private var commentsCollectionListener: ListenerRegistration? = null
    private var reelCommentsCollectionListener: ListenerRegistration? = null
    private var viralExpirationJob: kotlinx.coroutines.Job? = null

    fun clearUploadError() {
        uploadErrorMessage.value = null
    }

    val myFollowersCount = MutableStateFlow(0)
    val followersCount: StateFlow<Int> = myFollowersCount.asStateFlow()
    val myFollowingCount = MutableStateFlow(0)
    val currentUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    
    val allUsers = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val blockedAccounts = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val watchHistory = MutableStateFlow<List<ReelEntity>>(emptyList())
    val recentSearchesFlow = MutableStateFlow<List<String>>(emptyList())
    val bookmarkedReelIds = MutableStateFlow<Set<Int>>(emptySet())
    val isReelsRefreshing = MutableStateFlow(false)
    val notifications = MutableStateFlow<List<NotificationEntity>>(emptyList())
    val unreadNotificationsCount = MutableStateFlow(0)

    private val _accountRestrictionState = MutableStateFlow(AccountRestrictionState())
    val accountRestrictionState: StateFlow<AccountRestrictionState> = _accountRestrictionState.asStateFlow()
    private var accountStatusListener: ListenerRegistration? = null
    private var listenedAccountUid: String? = null
    private var monetizationStatusListener: ListenerRegistration? = null
    private var monetizationRequestsListener: ListenerRegistration? = null
    private var listenedMonetizationUid: String? = null
    private var latestMonetizationUserDoc: DocumentSnapshot? = null
    private var latestMonetizationRequestDocs: List<DocumentSnapshot> = emptyList()

    private var notifUidListener: ListenerRegistration? = null
    private var notifUsernameListener: ListenerRegistration? = null
    private val notifDocsByUid = java.util.concurrent.ConcurrentHashMap<String, NotificationEntity>()
    private val notifDocsByUsername = java.util.concurrent.ConcurrentHashMap<String, NotificationEntity>()
    private val displayedSystemNotifIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val appLaunchTimestampMs = System.currentTimeMillis()

    val selectedReelForPreview = MutableStateFlow<ReelEntity?>(null)
    val loginDevices = MutableStateFlow<List<LoginDeviceItem>>(listOf(
        LoginDeviceItem("1", "Pixel 8 Pro (This Device)", "Guwahati, Assam", "Active now"),
        LoginDeviceItem("2", "Chrome Browser on Windows", "Dispur, Assam", "2 days ago")
    ))

    val registeredEmailOrPhone = MutableStateFlow("")
    val registeredPassword = MutableStateFlow("")
    val adminPassword = MutableStateFlow("admin123")
    
    // Settings state
    val pushNotificationsEnabled = MutableStateFlow(true)
    val quietModeEnabled = MutableStateFlow(false)
    val captionsToggle = MutableStateFlow(false)
    val dataSaverMode = MutableStateFlow(false)
    val hapticFeedbackFlow = MutableStateFlow(true)
    val highQualityUploads = MutableStateFlow(true)
    val forceDarkMode = MutableStateFlow(false)
    
    // Monetization & Analytics state
    val monetizationStatus = MutableStateFlow("Not Eligible")
    val videoViewsCount = MutableStateFlow(0)
    val photoUploadsCount = MutableStateFlow(0)
    val storiesUploadedCount = MutableStateFlow(0)
    val profileVisitsCount = MutableStateFlow(0)

    private var activeCommentsReelId: Int? = null

    val currentStoryState = MutableStateFlow<StoryModel?>(null)
    private var storyListenerRegistration: ListenerRegistration? = null
    val searchQuery = MutableStateFlow("")
    val searchResultUsers = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val searchResultPosts = MutableStateFlow<List<ReelEntity>>(emptyList())

    private val backstack = Stack<Screen>()

    init {
        backstack.push(Screen.Splash)
        try {
            val savedPush = prefs.getBoolean("push_notifications_enabled", true)
            val savedQuiet = prefs.getBoolean("quiet_mode_enabled", false)
            pushNotificationsEnabled.value = savedPush
            quietModeEnabled.value = savedQuiet
            _settingsState.value = _settingsState.value.copy(
                pushNotifications = savedPush,
                quietMode = savedQuiet
            )
            YouSnapNotificationHelper.ensureChannel(app)
        } catch (_: Throwable) {}
        try {
            currentUser.value = auth.currentUser
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Safe init currentUser: ${e.message}")
        }
        try {
            restoreCachedProfileImmediate()
        } catch (_: Throwable) {}
        try { loadPersonalPhotosFromPrefs() } catch (e: Throwable) {}
        try { loadReelsFromPrefs() } catch (e: Throwable) {}
        try { loadStoriesFromPrefs() } catch (e: Throwable) {}
        try { loadHomeCommentsFromPrefs() } catch (e: Throwable) {}
        try { loadReelCommentsFromPrefs() } catch (e: Throwable) {}
        try { loadProfileVisitsFromPrefs() } catch (e: Throwable) {}
        try { recalculateCreatorStats() } catch (e: Throwable) {}
        try { listenToAuthChanges() } catch (e: Throwable) {}
        try { loadSearchHistory() } catch (e: Throwable) {}
        if (auth.currentUser != null) {
            try { fetchAndSyncUserProfile() } catch (e: Throwable) {}
            try { startListeningToFeedAndContent() } catch (e: Throwable) {}
            try { refreshHomeFeed() } catch (e: Throwable) {}
            try { loadFollowingFromFirestore() } catch (e: Throwable) {}
            try { startListeningToNotifications() } catch (e: Throwable) {}
            try { startListeningToAccountStatus() } catch (e: Throwable) {}
            try { startListeningToMonetizationStatus() } catch (e: Throwable) {}
            try { syncFcmToken() } catch (e: Throwable) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningToNotifications()
        stopListeningToAccountStatus()
        stopListeningToMonetizationStatus()
        stopListeningToFeedAndContent()
        viralExpirationJob?.cancel()
        viralExpirationJob = null
        storyListenerRegistration?.remove()
        storyListenerRegistration = null
    }

    private fun profileVisitsPrefsKey(): String {
        val identity = activeUserUid()?.takeIf { it.isNotBlank() }
            ?: _profileState.value.email.takeIf { it.isNotBlank() }?.lowercase()
            ?: _profileState.value.username.takeIf { it.isNotBlank() }?.lowercase()
            ?: "guest"
        return "profile_visits_" + identity.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun loadProfileVisitsFromPrefs() {
        profileVisitsCount.value = prefs.getInt(profileVisitsPrefsKey(), 0).coerceAtLeast(0)
    }

    fun recordProfileVisit() {
        val next = profileVisitsCount.value + 1
        profileVisitsCount.value = next
        prefs.edit().putInt(profileVisitsPrefsKey(), next).apply()
    }

    fun isOwnedByCurrentUser(creatorUsername: String, creatorUid: String): Boolean {
        val myUid = activeUserUid()
        if (!myUid.isNullOrBlank() && creatorUid.isNotBlank() && myUid == creatorUid) {
            return true
        }
        val myUsername = _profileState.value.username.trim()
        return myUsername.isNotBlank() && creatorUsername.trim().equals(myUsername, ignoreCase = true)
    }

    fun recalculateCreatorStats() {
        val myPhotos = personalPhotosState.value
            .filter { isOwnedByCurrentUser(it.creatorUsername, it.creatorUid) }
            .distinctBy { if (it.imageUrl.isNotBlank()) it.imageUrl else it.id }

        val myReels = _reelsState.value
            .filter { isOwnedByCurrentUser(it.creatorUsername, it.creatorUid) }
            .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.id}_${it.timestamp}" }

        val myStories = allStories.value
            .filter { isOwnedByCurrentUser(it.ownerUsername, "") }
            .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.ownerUsername}_${it.storyId}" }

        photoUploadsCount.value = myPhotos.size
        storiesUploadedCount.value = myStories.size
        videoViewsCount.value = myReels.sumOf { it.viewsCount.coerceAtLeast(0) }
        val currentStatus = _profileState.value.monetizationStatus
        if (currentStatus.isNotBlank()) {
            monetizationStatus.value = normalizeMonetizationStatus(currentStatus)
        }
    }

    private fun normalizeMonetizationStatus(rawStatus: String?): String {
        val trimmed = rawStatus?.trim().orEmpty()
        return when {
            trimmed.equals("Approved", ignoreCase = true) ||
                trimmed.equals("Approve", ignoreCase = true) -> "Approved"
            trimmed.equals("Rejected", ignoreCase = true) ||
                trimmed.equals("Reject", ignoreCase = true) -> "Rejected"
            trimmed.equals("Pending", ignoreCase = true) -> "Pending"
            trimmed.equals("Not Applied", ignoreCase = true) -> "Not Applied"
            trimmed.equals("Not Eligible", ignoreCase = true) ||
                trimmed.equals("NOT_ELIGIBLE", ignoreCase = true) ||
                trimmed.equals("NONE", ignoreCase = true) ||
                trimmed.isBlank() -> "Not Eligible"
            else -> trimmed
        }
    }

    private fun loadSearchHistory() {
        try {
            val searches = prefs.getStringSet("recent_searches", emptySet())?.toList() ?: emptyList()
            recentSearchesFlow.value = searches
        } catch (e: Throwable) {}
    }

    private fun updateUserMonetizationStatusCache(doc: DocumentSnapshot, user: ProfileEntity? = null) {
        val isApproved = doc.getBoolean("isMonetized") == true ||
            doc.getString("monetizationStatus")?.trim().equals("Approved", ignoreCase = true)
        if (isApproved) {
            monetizedUserIds.add(doc.id)
            nonMonetizedUserIds.remove(doc.id)
        } else {
            monetizedUserIds.remove(doc.id)
            nonMonetizedUserIds.add(doc.id)
        }
        AdMobRevenueTracker.updateUserMonetizationCache(doc.id, isApproved)
        if (user != null && user.username.isNotBlank()) {
            userUidByUsername[user.username.trim().lowercase()] = doc.id
            usernameByUid[doc.id] = user.username.trim()
        }
        doc.getString("profileLink")?.takeIf { it.isNotBlank() }?.let {
            profileLinkByUid[doc.id] = it
        }
    }

    private fun extractUserProfileFromDoc(doc: DocumentSnapshot): ProfileEntity? {
        val raw = doc.toObject(ProfileEntity::class.java) ?: return null
        val resolvedPhoto = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
            photoURL = doc.getString("photoURL"),
            profilePic = doc.getString("profilePic") ?: raw.profilePic,
            avatar = doc.getString("avatar")
        )
        return raw.copy(profilePic = resolvedPhoto)
    }

    private fun resolveCurrentUserRemotePhotoUrl(): String {
        val currentProfilePic = _profileState.value.profilePic
        val authPhotoUrl = try {
            auth.currentUser?.photoUrl?.toString()
        } catch (_: Throwable) {
            null
        }
        return ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
            photoURL = currentProfilePic,
            profilePic = authPhotoUrl,
            avatar = null
        )
    }

    private suspend fun uploadProfileImageToFirebaseStorage(
        uid: String,
        rawUriString: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (uid.isBlank() || currentUser == null || currentUser.uid != uid) {
            return@withContext Result.failure(
                IllegalStateException("Please sign in with your account before uploading a profile photo.")
            )
        }
        val trimmedUri = rawUriString.trim()
        if (trimmedUri.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("No profile photo selected.")
            )
        }
        val uri = if (trimmedUri.startsWith("/")) {
            Uri.fromFile(File(trimmedUri))
        } else {
            Uri.parse(trimmedUri)
        }

        val rawBytes = try {
            val stream = if (uri.scheme.equals("file", ignoreCase = true) && !uri.path.isNullOrBlank()) {
                File(uri.path!!).inputStream()
            } else {
                app.contentResolver.openInputStream(uri)
            }
            stream?.use { it.readBytes() }
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Could not read selected image file: ${e.localizedMessage ?: "Access denied"}", e)
            )
        }

        if (rawBytes == null || rawBytes.isEmpty()) {
            return@withContext Result.failure(
                Exception("Selected image file is empty or unreadable. Please choose another photo.")
            )
        }

        val uploadBytes = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            var sampleSize = 1
            val maxDim = 1024
            while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            if (decoded != null) {
                val bitmapToCompress = if (decoded.hasAlpha()) {
                    val opaque = Bitmap.createBitmap(
                        decoded.width,
                        decoded.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(opaque)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(decoded, 0f, 0f, null)
                    decoded.recycle()
                    opaque
                } else {
                    decoded
                }
                val out = ByteArrayOutputStream()
                bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 88, out)
                bitmapToCompress.recycle()
                out.toByteArray().takeIf { it.isNotEmpty() } ?: rawBytes
            } else {
                rawBytes
            }
        } catch (_: Throwable) {
            rawBytes
        }

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        suspend fun tryFirebaseBucket(storage: FirebaseStorage): Result<String> {
            return try {
                val storageRef = storage.reference.child("profile_images/$uid/avatar.jpg")
                val downloadUrl = withTimeoutOrNull(30_000L) {
                    val uploadSnap = awaitTask(storageRef.putBytes(uploadBytes, metadata))
                    val targetRef = uploadSnap.metadata?.reference ?: storageRef
                    awaitTask(targetRef.downloadUrl).toString().trim()
                } ?: return Result.failure(
                    Exception("Profile photo upload timed out. Please check your internet connection and try again.")
                )
                val normalized = ProfilePhotoUtils.normalizeRemotePhotoUrl(downloadUrl)
                if (normalized.isBlank()) {
                    Result.failure(Exception("Firebase Storage did not return a valid https:// download URL."))
                } else {
                    Result.success(normalized)
                }
            } catch (e: StorageException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // 1. Try default Firebase Storage bucket (from google-services.json)
        val primaryResult = tryFirebaseBucket(FirebaseStorage.getInstance())
        if (primaryResult.isSuccess) return@withContext primaryResult

        val primaryErr = primaryResult.exceptionOrNull()
        val primaryStorageCode = (primaryErr as? StorageException)?.errorCode

        // 2. If default bucket (.firebasestorage.app) returned 404 / bucket missing, try .appspot.com
        if (primaryStorageCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
            primaryStorageCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
            primaryStorageCode == StorageException.ERROR_PROJECT_NOT_FOUND
        ) {
            val appspotBucket = try {
                val projectId = com.google.firebase.FirebaseApp.getInstance().options.projectId
                    ?.takeIf { it.isNotBlank() } ?: "yousnap-app"
                tryFirebaseBucket(FirebaseStorage.getInstance("gs://$projectId.appspot.com"))
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (appspotBucket.isSuccess) return@withContext appspotBucket
        }

        // 3. If Firebase Storage is not provisioned/enabled on the Firebase project (e.g. HTTP 404
        //    "Object does not exist at location." / bucket not found / unconfigured rules),
        //    upload the avatar through the authenticated Supabase Storage + Cloudflare Worker pipeline.
        val primaryMsg = primaryErr?.message.orEmpty()
        val shouldTrySupabaseFallback = (primaryErr is StorageException && (
            primaryStorageCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                primaryStorageCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
                primaryStorageCode == StorageException.ERROR_PROJECT_NOT_FOUND ||
                primaryStorageCode == StorageException.ERROR_NOT_AUTHORIZED ||
                primaryStorageCode == StorageException.ERROR_NOT_AUTHENTICATED
            )) ||
            primaryMsg.contains("does not exist", ignoreCase = true) ||
            primaryMsg.contains("not found", ignoreCase = true) ||
            primaryMsg.contains("404", ignoreCase = true)

        if (shouldTrySupabaseFallback) {
            android.util.Log.w(
                "YouSnapViewModel",
                "Firebase Storage unavailable (code=$primaryStorageCode: ${primaryErr?.message}); uploading avatar via Supabase Storage fallback"
            )
            val safeUid = uid.replace(Regex("[^a-zA-Z0-9_-]"), "").ifEmpty { "user" }.take(32)
            val avatarFilename = "avatar__${safeUid}__${System.currentTimeMillis()}.jpg"
            val tempFile = File(app.cacheDir, avatarFilename)
            try {
                tempFile.writeBytes(uploadBytes)
                val supabaseResult = supabaseRepository.uploadFile(
                    file = tempFile,
                    filename = avatarFilename,
                    contentType = "image/jpeg"
                )
                val normalizedSupabaseUrl = ProfilePhotoUtils.normalizeRemotePhotoUrl(supabaseResult.fileUrl)
                if (supabaseResult.isSuccess && normalizedSupabaseUrl.isNotBlank()) {
                    return@withContext Result.success(normalizedSupabaseUrl)
                }
                val fallbackMsg = supabaseResult.errorMessage?.takeIf { it.isNotBlank() }
                    ?: primaryErr?.localizedMessage
                    ?: "Profile photo upload failed. Please try again."
                return@withContext Result.failure(Exception(fallbackMsg))
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception(
                        "Profile photo upload failed: ${e.localizedMessage ?: primaryErr?.localizedMessage ?: "Please try again."}",
                        e
                    )
                )
            } finally {
                runCatching { tempFile.delete() }
            }
        }

        val finalDetail = when (primaryStorageCode) {
            StorageException.ERROR_NOT_AUTHENTICATED,
            StorageException.ERROR_NOT_AUTHORIZED ->
                "Storage permission denied. Please sign in again or check Firebase Storage rules."
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                "Network timeout while uploading photo. Please try again."
            else -> primaryErr?.localizedMessage ?: "Firebase Storage upload failed."
        }
        return@withContext Result.failure(Exception(finalDetail, primaryErr))
    }

    fun fetchAllUsers() {
        try {
            if (usersCollectionListener == null) {
                startListeningToUsers()
            }
            db.collection("users").limit(100).get().addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    val user = extractUserProfileFromDoc(doc)
                    updateUserMonetizationStatusCache(doc, user)
                    user
                }
                allUsers.value = users
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "fetchAllUsers safe: ${e.message}")
        }
    }

    private fun listenToAuthChanges() {
        try {
            auth.addAuthStateListener { firebaseAuth ->
                currentUser.value = firebaseAuth.currentUser
                AdMobRevenueTracker.setFallbackViewerUid(activeUserUid())
                if (firebaseAuth.currentUser != null) {
                    fetchAndSyncUserProfile()
                    startListeningToFeedAndContent()
                    refreshHomeFeed(showIndicator = false)
                    loadFollowingFromFirestore()
                    startListeningToNotifications()
                    startListeningToAccountStatus()
                    startListeningToMonetizationStatus()
                    syncFcmToken()
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "listenToAuthChanges safe: ${e.message}")
        }
    }

    fun activeUserUid(): String? {
        return auth.currentUser?.uid ?: prefs.getString("active_user_uid", null)?.takeIf { it.isNotBlank() }
    }

    private fun emailPrefsKey(email: String): String {
        return "profile_by_email_" + email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
    }

    private fun uidPrefsKey(uid: String): String {
        return "profile_by_uid_" + uid.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun saveProfileToPrefs(profile: ProfileEntity, uid: String? = null) {
        try {
            val cleanProfilePic = ProfilePhotoUtils.normalizeRemotePhotoUrl(profile.profilePic)
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("username", profile.username)
                put("bio", profile.bio)
                put("profilePic", cleanProfilePic)
                put("category", profile.category)
                put("hiddenStoryFrom", profile.hiddenStoryFrom)
                put("email", profile.email)
                put("isPinEnabled", profile.isPinEnabled)
                put("appPin", profile.appPin)
                put("followersCount", profile.followersCount)
                put("followingCount", profile.followingCount)
                put("postCount", profile.postCount)
                put("videoViewsCount", profile.videoViewsCount)
                put("photoUploadsCount", profile.photoUploadsCount)
                put("storiesUploadedCount", profile.storiesUploadedCount)
                put("monetizationStatus", profile.monetizationStatus)
            }
            val json = obj.toString()
            val editor = prefs.edit()
            if (profile.email.isNotBlank()) {
                editor.putString(emailPrefsKey(profile.email), json)
                editor.putString("active_user_email", profile.email.trim().lowercase())
            }
            if (!uid.isNullOrBlank()) {
                editor.putString(uidPrefsKey(uid), json)
                editor.putString("active_user_uid", uid)
            }
            editor.apply()
        } catch (e: Exception) {
            android.util.Log.w("YouSnapViewModel", "saveProfileToPrefs notice: ${e.message}")
        }
    }

    private fun loadProfileFromPrefs(uid: String?, email: String?): ProfileEntity? {
        val candidates = listOfNotNull(
            email?.takeIf { it.isNotBlank() }?.let { prefs.getString(emailPrefsKey(it), null) },
            uid?.takeIf { it.isNotBlank() }?.let { prefs.getString(uidPrefsKey(it), null) }
        )
        for (raw in candidates) {
            try {
                val obj = JSONObject(raw)
                return ProfileEntity(
                    id = obj.optInt("id", 1),
                    name = obj.optString("name", "User"),
                    username = obj.optString("username", "user"),
                    bio = obj.optString("bio", ""),
                    profilePic = ProfilePhotoUtils.normalizeRemotePhotoUrl(obj.optString("profilePic", "")),
                    category = obj.optString("category", "Digital Creator"),
                    hiddenStoryFrom = obj.optString("hiddenStoryFrom", ""),
                    email = obj.optString("email", ""),
                    isPinEnabled = obj.optBoolean("isPinEnabled", false),
                    appPin = obj.optString("appPin", ""),
                    followersCount = obj.optInt("followersCount", 0),
                    followingCount = obj.optInt("followingCount", 0),
                    postCount = obj.optInt("postCount", 0),
                    videoViewsCount = obj.optInt("videoViewsCount", 0),
                    photoUploadsCount = obj.optInt("photoUploadsCount", 0),
                    storiesUploadedCount = obj.optInt("storiesUploadedCount", 0),
                    monetizationStatus = normalizeMonetizationStatus(obj.optString("monetizationStatus", "Not Eligible"))
                )
            } catch (_: Exception) {}
        }
        return null
    }

    private fun restoreCachedProfileImmediate() {
        val uid = auth.currentUser?.uid ?: prefs.getString("active_user_uid", null)
        val email = auth.currentUser?.email ?: prefs.getString("active_user_email", null)
        val cached = loadProfileFromPrefs(uid, email)
        if (cached != null) {
            val migratedPin = if (cached.appPin.isNotBlank() && !SecurityUtils.isHashedPin(cached.appPin)) {
                SecurityUtils.hashPin(cached.appPin)
            } else cached.appPin
            val normalizedCached = if (migratedPin != cached.appPin) {
                cached.copy(appPin = migratedPin).also { saveProfileToPrefs(it, uid) }
            } else cached
            _profileState.value = normalizedCached
            _settingsState.value = _settingsState.value.copy(
                appPin = normalizedCached.appPin,
                isPinEnabled = normalizedCached.isPinEnabled
            )
            myFollowersCount.value = normalizedCached.followersCount
            myFollowingCount.value = normalizedCached.followingCount
            monetizationStatus.value = normalizeMonetizationStatus(normalizedCached.monetizationStatus)
            loadProfileVisitsFromPrefs()
            recalculateCreatorStats()
        }
    }

    fun fetchAndSyncUserProfile() {
        try {
            val uid = activeUserUid() ?: return
            val email = auth.currentUser?.email ?: prefs.getString("active_user_email", null)
            loadProfileFromPrefs(uid, email)?.let { cached ->
                _profileState.value = cached
                myFollowersCount.value = cached.followersCount
                myFollowingCount.value = cached.followingCount
                monetizationStatus.value = normalizeMonetizationStatus(cached.monetizationStatus)
                loadProfileVisitsFromPrefs()
                recalculateCreatorStats()
            }
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    latestMonetizationUserDoc = doc
                    val profile = extractUserProfileFromDoc(doc)
                    val remoteLink = doc.getString("profileLink").orEmpty()
                    val expectedLink = DeepLinkHelper.buildProfileUrl(uid)
                    if (remoteLink.isBlank() && sessionBackfilledProfileLinkUids.add(uid)) {
                        db.collection("users").document(uid)
                            .set(mapOf("profileLink" to expectedLink), SetOptions.merge())
                    }
                    profileLinkByUid[uid] = remoteLink.ifBlank { expectedLink }
                    if (profile != null) {
                        val authPhotoUrl = ProfilePhotoUtils.normalizeRemotePhotoUrl(auth.currentUser?.photoUrl?.toString())
                        val resolvedPhoto = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                            photoURL = doc.getString("photoURL"),
                            profilePic = doc.getString("profilePic"),
                            avatar = doc.getString("avatar"),
                            authPhotoUrl = authPhotoUrl
                        )
                        val docPhotoUrl = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("photoURL"))
                        val docProfilePic = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("profilePic"))
                        val docAvatar = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("avatar"))
                        val hasLocalDirtyField =
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("photoURL")) ||
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("profilePic")) ||
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("avatar")) ||
                            doc.getString("profilePic").equals("avatar_default", ignoreCase = true)

                        if (auth.currentUser?.uid == uid &&
                            ((resolvedPhoto.isNotBlank() && (docPhotoUrl != resolvedPhoto || docProfilePic != resolvedPhoto || docAvatar != resolvedPhoto)) || hasLocalDirtyField)
                        ) {
                            db.collection("users").document(uid)
                                .set(ProfilePhotoUtils.buildAvatarFieldsMap(resolvedPhoto), SetOptions.merge())
                        }

                        if (profile.username.isNotBlank()) {
                            userUidByUsername[profile.username.trim().lowercase()] = uid
                            usernameByUid[uid] = profile.username.trim()
                        }
                        val rawResolvedProfile = profile.copy(
                            profilePic = resolvedPhoto,
                            monetizationStatus = resolveMonetizationStatusFromDocs(doc, latestMonetizationRequestDocs)
                        )
                        val shouldMigratePin = rawResolvedProfile.appPin.isNotBlank() &&
                            !SecurityUtils.isHashedPin(rawResolvedProfile.appPin)
                        val finalPinHash = if (shouldMigratePin) {
                            SecurityUtils.hashPin(rawResolvedProfile.appPin)
                        } else {
                            rawResolvedProfile.appPin
                        }
                        if (shouldMigratePin && auth.currentUser?.uid == uid) {
                            db.collection("users").document(uid)
                                .set(mapOf("appPin" to finalPinHash), SetOptions.merge())
                        }
                        val resolvedProfile = if (shouldMigratePin) {
                            rawResolvedProfile.copy(appPin = finalPinHash)
                        } else rawResolvedProfile

                        _profileState.value = resolvedProfile
                        _settingsState.value = _settingsState.value.copy(
                            appPin = resolvedProfile.appPin,
                            isPinEnabled = resolvedProfile.isPinEnabled
                        )
                        myFollowersCount.value = resolvedProfile.followersCount
                        myFollowingCount.value = resolvedProfile.followingCount
                        monetizationStatus.value = resolvedProfile.monetizationStatus
                        saveProfileToPrefs(resolvedProfile, uid)
                        loadProfileVisitsFromPrefs()
                        recalculateCreatorStats()
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "fetchAndSyncUserProfile safe: ${e.message}")
        }
    }

    fun proceedFromSplash() {
        try {
            val hasSession = auth.currentUser != null || !prefs.getString("active_user_email", "").isNullOrBlank()
            if (hasSession) {
                restoreCachedProfileImmediate()
                startListeningToAccountStatus()
                clearAndNavigateTo(Screen.Home)
            } else {
                clearAndNavigateTo(Screen.Login)
            }
        } catch (e: Throwable) {
            clearAndNavigateTo(Screen.Login)
        }
    }

    fun navigateTo(screen: Screen) {
        if ((screen is Screen.Profile ||
                (screen is Screen.OtherProfile && isOwnedByCurrentUser(screen.username, ""))) &&
            _currentScreen.value != screen
        ) {
            recordProfileVisit()
        }
        backstack.push(screen)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (backstack.size > 1) {
            backstack.pop()
            _currentScreen.value = backstack.peek()
            return true
        }
        return false
    }

    fun clearAndNavigateTo(screen: Screen) {
        backstack.clear()
        backstack.push(screen)
        _currentScreen.value = screen
    }

    fun logout() {
        try {
            auth.signOut()
        } catch (_: Throwable) {}
        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).requestEmail().build()
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(app, gso).signOut()
        } catch (e: Throwable) {
            android.util.Log.w("YouSnapViewModel", "GoogleSignIn signOut notice: ${e.message}")
        }
        // Clear active session markers ONLY (keep per-email profiles and cached media intact!)
        prefs.edit().remove("active_user_email").remove("active_user_uid").apply()
        stopListeningToNotifications()
        stopListeningToAccountStatus()
        stopListeningToMonetizationStatus()
        stopListeningToFeedAndContent()
        sessionLoggedAdViewMediaKeys.clear()
        AdMobRevenueTracker.clearActiveMediaContext()
        AdMobRevenueTracker.setFallbackViewerUid(null)
        _accountRestrictionState.value = AccountRestrictionState()
        notifications.value = emptyList()
        unreadNotificationsCount.value = 0
        otherUserIsFollowing.value = emptyMap()
        currentUser.value = null
        _profileState.value = ProfileEntity(name = "User", username = "user")
        myFollowersCount.value = 0
        myFollowingCount.value = 0
        monetizationStatus.value = "Not Eligible"
        profileVisitsCount.value = 0
        recalculateCreatorStats()
        clearAndNavigateTo(Screen.Login)
    }

    fun hasOpenedPostFromProfile(): Boolean = selectedHomePost.value != null
    fun clearOpenedHomePost() {
        selectedHomePost.value = null
    }

    private fun buildUserDocMap(profile: ProfileEntity, uid: String): Map<String, Any> {
        val link = DeepLinkHelper.buildProfileUrl(uid)
        profileLinkByUid[uid] = link
        if (profile.username.isNotBlank()) {
            userUidByUsername[profile.username.trim().lowercase()] = uid
            usernameByUid[uid] = profile.username.trim()
        }
        val remotePhoto = ProfilePhotoUtils.normalizeRemotePhotoUrl(profile.profilePic)
        return mapOf(
            "id" to profile.id,
            "name" to profile.name,
            "username" to profile.username,
            "bio" to profile.bio,
            "photoURL" to remotePhoto,
            "profilePic" to remotePhoto,
            "avatar" to remotePhoto,
            "category" to profile.category,
            "hiddenStoryFrom" to profile.hiddenStoryFrom,
            "email" to profile.email,
            "isPinEnabled" to profile.isPinEnabled,
            "appPin" to profile.appPin,
            "followersCount" to profile.followersCount,
            "followingCount" to profile.followingCount,
            "postCount" to profile.postCount,
            "videoViewsCount" to profile.videoViewsCount,
            "photoUploadsCount" to profile.photoUploadsCount,
            "storiesUploadedCount" to profile.storiesUploadedCount,
            "monetizationStatus" to profile.monetizationStatus,
            "profileLink" to link
        )
    }

    fun resolveUidForUsername(username: String): String? {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return null
        val key = clean.lowercase()
        if (key == _profileState.value.username.trim().lowercase()) {
            activeUserUid()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        userUidByUsername[key]?.takeIf { it.isNotBlank() }?.let { return it }
        personalPhotosState.value.firstOrNull {
            it.creatorUsername.equals(clean, ignoreCase = true) && it.creatorUid.isNotBlank()
        }?.creatorUid?.let { return it }
        _reelsState.value.firstOrNull {
            it.creatorUsername.equals(clean, ignoreCase = true) && it.creatorUid.isNotBlank()
        }?.creatorUid?.let { return it }
        return null
    }

    fun getMyProfileShareUrl(): String {
        val uid = activeUserUid()?.takeIf { it.isNotBlank() }
        if (uid != null) {
            return profileLinkByUid[uid] ?: DeepLinkHelper.buildProfileUrl(uid)
        }
        return DeepLinkHelper.buildProfileUrl(_profileState.value.username.ifBlank { "user" })
    }

    fun getProfileShareUrl(username: String, knownUid: String = ""): String {
        val uid = knownUid.takeIf { it.isNotBlank() } ?: resolveUidForUsername(username).orEmpty()
        if (uid.isNotBlank()) {
            return profileLinkByUid[uid] ?: DeepLinkHelper.buildProfileUrl(uid)
        }
        return DeepLinkHelper.buildProfileUrl(username.ifBlank { "user" })
    }

    fun getPhotoShareUrl(photo: FullScreenPhotoData): String {
        if (photo.shareLink.isNotBlank()) return photo.shareLink
        val docId = photo.firestoreDocId?.takeIf { it.isNotBlank() } ?: photo.id
        return photoShareLinkByKey[docId]
            ?: photoShareLinkByKey[photo.id]
            ?: DeepLinkHelper.buildPostUrl(docId)
    }

    fun getPhotoShareUrl(post: HomePostItem): String {
        val rawId = post.id.removePrefix("photo_")
        val docId = post.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: personalPhotosState.value.firstOrNull {
                it.id == rawId || it.id == post.id || it.firestoreDocId == post.firestoreDocId
            }?.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: rawId
        return photoShareLinkByKey[docId]
            ?: photoShareLinkByKey[rawId]
            ?: DeepLinkHelper.buildPostUrl(docId)
    }

    fun getReelShareUrl(reel: ReelEntity): String {
        val docId = reelFirestoreDocIdByMediaUrl[reel.mediaUrl]
            ?: reelFirestoreDocIdByTimestamp[reel.timestamp]
            ?: reelFirestoreDocIdById[reel.id]
            ?: reel.id.toString()
        return reelShareLinkByKey[docId]
            ?: reelShareLinkByKey[reel.id.toString()]
            ?: DeepLinkHelper.buildReelUrl(docId)
    }

    fun handleIncomingDeepLink(deepLink: YouSnapDeepLink) {
        viewModelScope.launch {
            try {
                when (deepLink) {
                    is YouSnapDeepLink.UserProfile -> openProfileFromDeepLink(deepLink.identifier)
                    is YouSnapDeepLink.PhotoPost -> openPhotoPostFromDeepLink(deepLink.docId)
                    is YouSnapDeepLink.ReelVideo -> openReelFromDeepLink(deepLink.docId)
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "handleIncomingDeepLink error: ${e.message}")
            }
        }
    }

    private fun openProfileFromDeepLink(rawIdentifier: String) {
        val cleanId = rawIdentifier.trim().removePrefix("@")
        if (cleanId.isBlank()) return
        val myUid = activeUserUid().orEmpty()
        val myUsername = _profileState.value.username.trim()
        if ((myUid.isNotBlank() && cleanId == myUid) ||
            (myUsername.isNotBlank() && cleanId.equals(myUsername, ignoreCase = true))
        ) {
            clearOpenedHomePost()
            navigateTo(Screen.Profile)
            return
        }

        val cachedUsername = usernameByUid[cleanId]
            ?: allUsers.value.firstOrNull { it.username.equals(cleanId, ignoreCase = true) }?.username
            ?: personalPhotosState.value.firstOrNull { it.creatorUid == cleanId && it.creatorUsername.isNotBlank() }?.creatorUsername
            ?: _reelsState.value.firstOrNull { it.creatorUid == cleanId && it.creatorUsername.isNotBlank() }?.creatorUsername

        if (!cachedUsername.isNullOrBlank()) {
            clearOpenedHomePost()
            navigateTo(Screen.OtherProfile(cachedUsername))
            return
        }

        db.collection("users").document(cleanId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = extractUserProfileFromDoc(doc)
                    updateUserMonetizationStatusCache(doc, user)
                    val resolvedUsername = user?.username?.takeIf { it.isNotBlank() }
                        ?: doc.getString("username")?.takeIf { it.isNotBlank() }
                        ?: cleanId
                    clearOpenedHomePost()
                    if (doc.id == myUid || (myUsername.isNotBlank() && resolvedUsername.equals(myUsername, ignoreCase = true))) {
                        navigateTo(Screen.Profile)
                    } else {
                        navigateTo(Screen.OtherProfile(resolvedUsername))
                    }
                } else {
                    db.collection("users").whereEqualTo("username", cleanId).limit(1).get()
                        .addOnSuccessListener { querySnap ->
                            val matchedDoc = querySnap.documents.firstOrNull()
                            if (matchedDoc != null) {
                                val user = extractUserProfileFromDoc(matchedDoc)
                                updateUserMonetizationStatusCache(matchedDoc, user)
                                val resolvedUsername = user?.username?.takeIf { it.isNotBlank() } ?: cleanId
                                clearOpenedHomePost()
                                navigateTo(Screen.OtherProfile(resolvedUsername))
                            } else {
                                clearOpenedHomePost()
                                navigateTo(Screen.OtherProfile(cleanId))
                            }
                        }
                        .addOnFailureListener {
                            clearOpenedHomePost()
                            navigateTo(Screen.OtherProfile(cleanId))
                        }
                }
            }
            .addOnFailureListener {
                clearOpenedHomePost()
                navigateTo(Screen.OtherProfile(cleanId))
            }
    }

    private fun openPhotoPostFromDeepLink(rawDocId: String) {
        val docId = rawDocId.trim().removePrefix("photo_")
        if (docId.isBlank()) return

        val localMatch = personalPhotosState.value.firstOrNull {
            it.firestoreDocId == docId ||
                it.id == docId ||
                it.firestoreDocId?.endsWith("_$docId") == true
        }
        if (localMatch != null) {
            selectedHomePost.value = localMatch
            if (_currentScreen.value !is Screen.Home) {
                clearAndNavigateTo(Screen.Home)
            }
            return
        }

        val showFetchedPhoto: (DocumentSnapshot) -> Unit = { doc ->
            if (!isDocumentRestricted(doc)) {
                val rawUrl = doc.getString("imageUrl") ?: doc.getString("mediaUrl") ?: ""
                val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
                val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
                val id = doc.getString("id") ?: ts.takeIf { it > 0L }?.toString() ?: doc.id
                val uname = doc.getString("username")?.takeIf { it.isNotBlank() && it != "user" } ?: "user"
                val matchedUser = allUsers.value.firstOrNull { it.username.equals(uname, ignoreCase = true) }
                val display = doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: matchedUser?.name?.takeIf { it.isNotBlank() }
                    ?: uname
                val avatar = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                    photoURL = matchedUser?.profilePic,
                    profilePic = doc.getString("creatorAvatar"),
                    avatar = doc.getString("photoURL")
                )
                val shareLink = doc.getString("shareLink")?.takeIf { it.isNotBlank() }
                    ?: DeepLinkHelper.buildPostUrl(doc.id)
                val photo = FullScreenPhotoData(
                    id = id,
                    title = doc.getString("caption") ?: doc.getString("title") ?: "",
                    imageUrl = fixedUrl,
                    creatorName = display,
                    creatorUsername = uname,
                    creatorAvatar = avatar,
                    creatorUid = doc.getString("creatorUid") ?: doc.getString("userId") ?: "",
                    initialLikes = (doc.getLong("likesCount") ?: 0L).toInt().coerceAtLeast(0),
                    initialComments = (doc.getLong("commentsCount") ?: 0L).toInt().coerceAtLeast(0),
                    initialIsLiked = false,
                    timeAgo = if (ts > 0L) formatRelativeTime(ts) else "Just now",
                    firestoreDocId = doc.id,
                    shareLink = shareLink
                )
                photoShareLinkByKey[doc.id] = shareLink
                photoShareLinkByKey[id] = shareLink
                personalPhotosState.value = (listOf(photo) + personalPhotosState.value)
                    .distinctBy { it.firestoreDocId ?: it.id }
                selectedHomePost.value = photo
                if (_currentScreen.value !is Screen.Home) {
                    clearAndNavigateTo(Screen.Home)
                }
            }
        }

        db.collection("posts").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    showFetchedPhoto(doc)
                } else {
                    db.collection("posts").whereEqualTo("id", docId).limit(1).get()
                        .addOnSuccessListener { snap ->
                            val matched = snap.documents.firstOrNull()
                            if (matched != null) {
                                showFetchedPhoto(matched)
                            } else {
                                Toast.makeText(app, "Post not found or unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(app, "Unable to open post link", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(app, "Unable to open post link", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openReelFromDeepLink(rawDocId: String) {
        val docId = rawDocId.trim()
        if (docId.isBlank()) return

        val localMatch = _reelsState.value.firstOrNull { reel ->
            reelFirestoreDocIdByMediaUrl[reel.mediaUrl] == docId ||
                reelFirestoreDocIdByTimestamp[reel.timestamp] == docId ||
                reelFirestoreDocIdById[reel.id] == docId ||
                reel.id.toString() == docId ||
                reel.timestamp.toString() == docId
        }
        if (localMatch != null) {
            clearOpenedHomePost()
            navigateTo(Screen.Reels(startReelId = localMatch.id, startMediaUrl = localMatch.mediaUrl))
            return
        }

        val handleFoundDocs: (List<DocumentSnapshot>, String) -> Boolean = { docs, sourceCollection ->
            val mapped = mapReelOrVideoDocuments(
                documents = docs,
                sourceCollection = sourceCollection,
                deletedUrls = emptySet(),
                pendingDeleteKeys = emptySet(),
                myUid = activeUserUid().orEmpty(),
                myUsername = _profileState.value.username.trim(),
                usersList = allUsers.value,
                followingMap = otherUserIsFollowing.value,
                existingThumbByUrl = emptyMap(),
                existingUidByUrl = emptyMap()
            )
            val reel = mapped.firstOrNull()
            if (reel != null) {
                _reelsState.value = (listOf(reel) + _reelsState.value)
                    .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.id}_${it.timestamp}" }
                clearOpenedHomePost()
                navigateTo(Screen.Reels(startReelId = reel.id, startMediaUrl = reel.mediaUrl))
                true
            } else {
                false
            }
        }

        db.collection("reels").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && handleFoundDocs(listOf(doc), "reels")) return@addOnSuccessListener
                db.collection("videos").document(docId).get()
                    .addOnSuccessListener { vDoc ->
                        if (vDoc.exists() && handleFoundDocs(listOf(vDoc), "videos")) return@addOnSuccessListener
                        db.collection("reels").whereEqualTo("id", docId).limit(1).get()
                            .addOnSuccessListener { qSnap ->
                                if (!qSnap.isEmpty && handleFoundDocs(qSnap.documents, "reels")) return@addOnSuccessListener
                                val intId = docId.toIntOrNull()
                                if (intId != null) {
                                    db.collection("reels").whereEqualTo("reelIntId", intId).limit(1).get()
                                        .addOnSuccessListener { intSnap ->
                                            if (intSnap.isEmpty || !handleFoundDocs(intSnap.documents, "reels")) {
                                                Toast.makeText(app, "Video not found or unavailable", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                } else {
                                    Toast.makeText(app, "Video not found or unavailable", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(app, "Unable to open video link", Toast.LENGTH_SHORT).show()
            }
    }

    fun uploadMedia(
        mediaUri: Uri,
        mediaType: String,
        caption: String,
        songTitle: String,
        category: String,
        gradientStart: String,
        gradientEnd: String,
        isStory: Boolean,
        onComplete: () -> Unit
    ) {
        if (isB2Uploading.value) return

        isB2Uploading.value = true
        b2UploadProgress.value = 0f
        uploadErrorMessage.value = null
        val isVideo = mediaType == "reel" || mediaType == "video" || mediaType == "video_reel"
        uploadStatusMessage.value = if (isVideo) "Compressing video..." else "Preparing photo..."

        viewModelScope.launch {
            var fileToUpload: File? = null
            val timestamp = System.currentTimeMillis()
            val extension = if (isVideo) "mp4" else "jpg"
            val currentProfile = profileState.value
            val ownerUsername = currentProfile.username.ifEmpty { "user" }
            val safeOwner = ownerUsername.lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }.take(32)
            val prefix = if (isStory) "story" else "yousnap"
            val filename = "${prefix}__${safeOwner}__${timestamp}.$extension"
            val contentType = if (isVideo) "video/mp4" else "image/jpeg"

            try {
                // Stage 1: Prepare / Compress Video (0% -> 40% for videos, 0% -> 5% for photos)
                if (isVideo) {
                    var compressionFailed = false
                    VideoCompressor.compressVideoFlow(
                        context = app,
                        inputUri = mediaUri
                    ).collect { state ->
                        when (state) {
                            is CompressionState.Progress -> {
                                b2UploadProgress.value = (state.fraction * 0.40f).coerceIn(0f, 0.40f)
                            }
                            is CompressionState.Completed -> {
                                fileToUpload = state.file
                                b2UploadProgress.value = 0.40f
                            }
                            is CompressionState.Error -> {
                                android.util.Log.e("YouSnapViewModel", "Compression failed, using original: ${state.exception.message}")
                                compressionFailed = true
                            }
                        }
                    }

                    if (compressionFailed || fileToUpload == null || !fileToUpload!!.exists() || fileToUpload!!.length() <= 0L) {
                        uploadStatusMessage.value = "Preparing video..."
                        fileToUpload = withContext(Dispatchers.IO) {
                            val fallbackFile = File(app.cacheDir, filename)
                            app.contentResolver.openInputStream(mediaUri)?.use { input ->
                                fallbackFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            fallbackFile
                        }
                        b2UploadProgress.value = 0.40f
                    }
                } else {
                    fileToUpload = withContext(Dispatchers.IO) {
                        val photoFile = File(app.cacheDir, filename)
                        app.contentResolver.openInputStream(mediaUri)?.use { input ->
                            photoFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        photoFile
                    }
                    b2UploadProgress.value = 0.05f
                }

                val targetFile = fileToUpload
                if (targetFile == null || !targetFile.exists() || targetFile.length() <= 0L) {
                    uploadErrorMessage.value = "Could not read selected media file."
                    isB2Uploading.value = false
                    b2UploadProgress.value = 0f
                    return@launch
                }

                // Extract a crisp local thumbnail frame for videos so profile/feed never falls back to blank color
                val localThumbPath = if (isVideo && !isStory) {
                    withContext(Dispatchers.IO) {
                        extractAndSaveVideoThumbnail(targetFile, timestamp)
                    }
                } else {
                    ""
                }

                // Stage 2: Live Byte-by-Byte Supabase Storage Upload via Flow
                // Videos: 40% -> 95% | Photos: 5% -> 95%
                val uploadStart = if (isVideo) 0.40f else 0.05f
                val uploadRange = 0.95f - uploadStart
                uploadStatusMessage.value = when {
                    isStory && isVideo -> "Uploading story video..."
                    isStory -> "Uploading story photo..."
                    isVideo -> "Uploading reel video..."
                    else -> "Uploading photo..."
                }

                var uploadResult: UploadResult? = null
                supabaseRepository.uploadFileFlow(
                    file = targetFile,
                    filename = filename,
                    contentType = contentType
                ).collect { transferState ->
                    when (transferState) {
                        is UploadTransferState.Progress -> {
                            b2UploadProgress.value = (uploadStart + (transferState.fraction * uploadRange))
                                .coerceIn(uploadStart, 0.95f)
                        }
                        is UploadTransferState.Completed -> {
                            uploadResult = transferState.result
                        }
                    }
                }

                val finalResult = uploadResult
                if (finalResult == null || !finalResult.isSuccess || finalResult.fileUrl.isNullOrEmpty()) {
                    uploadErrorMessage.value = finalResult?.errorMessage ?: "Media upload failed. Please check internet and retry."
                    isB2Uploading.value = false
                    b2UploadProgress.value = 0f
                    return@launch
                }

                val finalUrl = finalResult.fileUrl
                recentlyUploadedUrls[finalUrl] = System.currentTimeMillis()
                b2UploadProgress.value = 0.95f

                // Stage 3: Save Metadata Locally & Sync Directly to Firestore (95% -> 100%)
                val profile = profileState.value
                val creatorUid = resolveUploadUserId(safeOwner)
                val displayName = profile.name.ifBlank { ownerUsername }
                val creatorAvatar = resolveCurrentUserRemotePhotoUrl()
                val resolvedCategory = if (isVideo) category.ifBlank { "Entertainment" } else category.ifBlank { "Photo" }
                val resolvedAudioTitle = songTitle.ifBlank { "Original Audio" }
                val firestoreDocId = when {
                    isStory -> ownerUsername
                    !isVideo -> "post_${safeOwner}_${timestamp}"
                    else -> "reel_${safeOwner}_${timestamp}"
                }
                val targetCollection = when {
                    isStory -> "stories"
                    !isVideo -> "posts"
                    else -> "reels"
                }

                uploadStatusMessage.value = when {
                    isStory -> "Saving story to Firestore..."
                    isVideo -> "Saving reel to Firestore..."
                    else -> "Saving post to Firestore..."
                }
                b2UploadProgress.value = 0.96f
                val inFlightKey = "$targetCollection/$firestoreDocId"
                inFlightUploadDocKeys[inFlightKey] = System.currentTimeMillis()

                val itemShareLink = when {
                    isStory -> ""
                    !isVideo -> DeepLinkHelper.buildPostUrl(firestoreDocId)
                    else -> DeepLinkHelper.buildReelUrl(firestoreDocId)
                }

                if (isStory) {
                    val newStory = StoryModel(
                        storyId = timestamp.toString(),
                        ownerUsername = ownerUsername,
                        mediaUrl = finalUrl,
                        mediaType = if (isVideo) "video" else "image",
                        caption = caption,
                        bgGradientStart = gradientStart,
                        bgGradientEnd = gradientEnd,
                        timestamp = timestamp,
                        firestoreDocId = firestoreDocId
                    )
                    allStories.value = listOf(newStory) + allStories.value.filter { !it.ownerUsername.equals(ownerUsername, ignoreCase = true) }
                    saveStoriesToPrefs()
                } else if (!isVideo) {
                    photoShareLinkByKey[firestoreDocId] = itemShareLink
                    photoShareLinkByKey[timestamp.toString()] = itemShareLink
                    val newPhoto = FullScreenPhotoData(
                        id = timestamp.toString(),
                        title = caption,
                        imageUrl = finalUrl,
                        creatorName = displayName,
                        creatorUsername = ownerUsername,
                        creatorAvatar = creatorAvatar,
                        creatorUid = creatorUid,
                        initialLikes = 0,
                        initialComments = 0,
                        initialIsLiked = false,
                        timeAgo = "Just now",
                        firestoreDocId = firestoreDocId,
                        shareLink = itemShareLink
                    )
                    personalPhotosState.value = listOf(newPhoto) + personalPhotosState.value
                    savePersonalPhotosToPrefs()
                } else {
                    val newReel = ReelEntity(
                        id = timestamp.toInt(),
                        creatorName = displayName,
                        creatorUsername = ownerUsername,
                        creatorProfilePic = creatorAvatar,
                        creatorUid = creatorUid,
                        mediaUrl = finalUrl,
                        thumbnailUrl = localThumbPath,
                        caption = caption,
                        audioTitle = resolvedAudioTitle,
                        likesCount = 0,
                        commentsCount = 0,
                        viewsCount = 0,
                        timestamp = timestamp,
                        bgGradientStart = gradientStart,
                        bgGradientEnd = gradientEnd
                    )
                    _reelsState.value = listOf(newReel) + _reelsState.value
                    reelFirestoreDocIdByMediaUrl[finalUrl] = firestoreDocId
                    reelFirestoreDocIdByTimestamp[timestamp] = firestoreDocId
                    reelFirestoreDocIdById[newReel.id] = firestoreDocId
                    reelShareLinkByKey[firestoreDocId] = itemShareLink
                    reelShareLinkByKey[newReel.id.toString()] = itemShareLink
                    saveReelsToPrefs()
                }
                recalculateCreatorStats()

                val basePayload: Map<String, Any> = when {
                    isStory -> mapOf(
                        "storyId" to timestamp.toString(),
                        "firestoreDocId" to firestoreDocId,
                        "userId" to creatorUid,
                        "creatorUid" to creatorUid,
                        "username" to ownerUsername,
                        "ownerUsername" to ownerUsername,
                        "name" to displayName,
                        "creatorAvatar" to creatorAvatar,
                        "photoURL" to creatorAvatar,
                        "mediaUrl" to finalUrl,
                        "mediaType" to if (isVideo) "video" else "image",
                        "caption" to caption,
                        "timestamp" to timestamp,
                        "viewerIds" to emptyList<String>(),
                        "viewsCount" to 0,
                        "likes" to emptyList<String>(),
                        "bgGradientStart" to gradientStart,
                        "bgGradientEnd" to gradientEnd
                    )
                    !isVideo -> mapOf(
                        "id" to timestamp.toString(),
                        "firestoreDocId" to firestoreDocId,
                        "shareLink" to itemShareLink,
                        "type" to "photo",
                        "userId" to creatorUid,
                        "creatorUid" to creatorUid,
                        "username" to ownerUsername,
                        "name" to displayName,
                        "creatorAvatar" to creatorAvatar,
                        "photoURL" to creatorAvatar,
                        "imageUrl" to finalUrl,
                        "mediaUrl" to finalUrl,
                        "caption" to caption,
                        "title" to caption,
                        "category" to resolvedCategory,
                        "timestamp" to timestamp,
                        "likesCount" to 0,
                        "commentsCount" to 0,
                        "sharesCount" to 0,
                        "likedBy" to emptyList<String>()
                    )
                    else -> mapOf(
                        "id" to timestamp.toString(),
                        "reelIntId" to timestamp.toInt(),
                        "firestoreDocId" to firestoreDocId,
                        "shareLink" to itemShareLink,
                        "type" to "reel",
                        "userId" to creatorUid,
                        "creatorUid" to creatorUid,
                        "username" to ownerUsername,
                        "name" to displayName,
                        "creatorAvatar" to creatorAvatar,
                        "photoURL" to creatorAvatar,
                        "mediaUrl" to finalUrl,
                        "thumbnailUrl" to "",
                        "audioTitle" to resolvedAudioTitle,
                        "category" to resolvedCategory,
                        "caption" to caption,
                        "timestamp" to timestamp,
                        "likesCount" to 0,
                        "commentsCount" to 0,
                        "sharesCount" to 0,
                        "viewsCount" to 0,
                        "likedBy" to emptyList<String>(),
                        "bgGradientStart" to gradientStart,
                        "bgGradientEnd" to gradientEnd
                    )
                }

                val firestorePayload = basePayload + ("createdAt" to FieldValue.serverTimestamp())
                val firestoreSynced = try {
                    syncDocumentToFirestoreWithRetry(
                        collection = targetCollection,
                        docId = firestoreDocId,
                        data = firestorePayload
                    )
                } finally {
                    // Keep short protection only if write failed (saved in pending_firestore_docs)
                    inFlightUploadDocKeys.remove(inFlightKey)
                }
                if (!firestoreSynced) {
                    savePendingFirestoreDoc(targetCollection, firestoreDocId, basePayload)
                }

                b2UploadProgress.value = 1.0f
                uploadStatusMessage.value = "Upload complete!"
                delay(250L)

                isB2Uploading.value = false
                b2UploadProgress.value = 0f
                if (firestoreSynced) {
                    refreshHomeFeed(showIndicator = false)
                }
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Upload pipeline error: ${e.message}")
                uploadErrorMessage.value = e.localizedMessage ?: "Upload failed"
                isB2Uploading.value = false
                b2UploadProgress.value = 0f
            } finally {
                try { fileToUpload?.delete() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun syncDocumentToFirestoreWithRetry(
        collection: String,
        docId: String,
        data: Map<String, Any>,
        maxAttempts: Int = 3
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            db.enableNetwork()
        } catch (_: Exception) {}

        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                val writeConfirmed = withTimeoutOrNull(25_000L) {
                    awaitTask(db.collection(collection).document(docId).set(data))
                    true
                } ?: false

                if (writeConfirmed) {
                    removePendingFirestoreDoc(collection, docId)
                    android.util.Log.d("YouSnapViewModel", "Firestore sync confirmed: $collection/$docId (attempt $attempt)")
                    return@withContext true
                } else {
                    android.util.Log.w("YouSnapViewModel", "Firestore write timed out waiting for server ack: $collection/$docId (attempt $attempt/$maxAttempts)")
                }
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                lastError = e
                android.util.Log.e(
                    "YouSnapViewModel",
                    "Firestore write rejected [$collection/$docId] code=${e.code} attempt=$attempt: ${e.message}"
                )
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.INVALID_ARGUMENT
                ) {
                    break
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.e(
                    "YouSnapViewModel",
                    "Firestore write error [$collection/$docId] attempt=$attempt: ${e.message}"
                )
            }
            if (attempt < maxAttempts) {
                delay(attempt * 800L)
            }
        }
        android.util.Log.e(
            "YouSnapViewModel",
            "Firestore write queued/pending for retry [$collection/$docId]: ${lastError?.message ?: "timeout"}"
        )
        false
    }

    private fun savePendingFirestoreDoc(collection: String, docId: String, basePayload: Map<String, Any>) {
        synchronized(pendingSyncLock) {
            try {
                val currentRaw = prefs.getString("pending_firestore_docs", null)
                val currentArray = if (!currentRaw.isNullOrBlank()) JSONArray(currentRaw) else JSONArray()
                val updatedArray = JSONArray()
                for (i in 0 until currentArray.length()) {
                    val item = currentArray.optJSONObject(i) ?: continue
                    if (item.optString("collection") == collection && item.optString("docId") == docId) continue
                    updatedArray.put(item)
                }
                val dataJson = JSONObject()
                basePayload.forEach { (k, v) ->
                    when (v) {
                        is List<*> -> dataJson.put(k, JSONArray(v))
                        else -> dataJson.put(k, v)
                    }
                }
                val entry = JSONObject().apply {
                    put("collection", collection)
                    put("docId", docId)
                    put("data", dataJson)
                }
                updatedArray.put(entry)
                prefs.edit().putString("pending_firestore_docs", updatedArray.toString()).apply()
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Failed to save pending Firestore sync item: ${e.message}")
            }
        }
    }

    private fun removePendingFirestoreDoc(collection: String, docId: String) {
        synchronized(pendingSyncLock) {
            try {
                val currentRaw = prefs.getString("pending_firestore_docs", null) ?: return
                val currentArray = JSONArray(currentRaw)
                if (currentArray.length() == 0) return
                val updatedArray = JSONArray()
                var changed = false
                for (i in 0 until currentArray.length()) {
                    val item = currentArray.optJSONObject(i) ?: continue
                    if (item.optString("collection") == collection && item.optString("docId") == docId) {
                        changed = true
                        continue
                    }
                    updatedArray.put(item)
                }
                if (changed) {
                    prefs.edit().putString("pending_firestore_docs", updatedArray.toString()).apply()
                }
            } catch (_: Exception) {}
        }
    }

    private fun resolveUploadUserId(fallback: String): String {
        val firebaseUid = try {
            auth.currentUser?.uid
        } catch (_: Throwable) {
            null
        }
        return firebaseUid?.takeIf { it.isNotBlank() }
            ?: activeUserUid()?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun savePendingFirestoreDelete(collection: String, docId: String) {
        synchronized(pendingSyncLock) {
            try {
                val currentRaw = prefs.getString("pending_firestore_deletes", null)
                val currentArray = if (!currentRaw.isNullOrBlank()) JSONArray(currentRaw) else JSONArray()
                for (i in 0 until currentArray.length()) {
                    val item = currentArray.optJSONObject(i) ?: continue
                    if (item.optString("collection") == collection && item.optString("docId") == docId) {
                        return
                    }
                }
                currentArray.put(JSONObject().apply {
                    put("collection", collection)
                    put("docId", docId)
                })
                prefs.edit().putString("pending_firestore_deletes", currentArray.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun removePendingFirestoreDelete(collection: String, docId: String) {
        synchronized(pendingSyncLock) {
            try {
                val currentRaw = prefs.getString("pending_firestore_deletes", null) ?: return
                val currentArray = JSONArray(currentRaw)
                if (currentArray.length() == 0) return
                val updatedArray = JSONArray()
                var changed = false
                for (i in 0 until currentArray.length()) {
                    val item = currentArray.optJSONObject(i) ?: continue
                    if (item.optString("collection") == collection && item.optString("docId") == docId) {
                        changed = true
                        continue
                    }
                    updatedArray.put(item)
                }
                if (changed) {
                    prefs.edit().putString("pending_firestore_deletes", updatedArray.toString()).apply()
                }
            } catch (_: Exception) {}
        }
    }

    private data class FirestoreDeleteOutcome(
        val success: Boolean,
        val permanentError: String? = null
    )

    private fun savePendingSupabaseDelete(urlOrKey: String) {
        val clean = urlOrKey.trim()
        if (clean.isBlank()) return
        synchronized(pendingSyncLock) {
            try {
                val current = prefs.getStringSet("pending_supabase_deletes", emptySet())?.toMutableSet() ?: mutableSetOf()
                current.add(clean)
                prefs.edit().putStringSet("pending_supabase_deletes", current).apply()
            } catch (_: Exception) {}
        }
    }

    private fun removePendingSupabaseDelete(urlOrKey: String) {
        val clean = urlOrKey.trim()
        val key = extractSupabaseFilename(clean)
        synchronized(pendingSyncLock) {
            try {
                val current = prefs.getStringSet("pending_supabase_deletes", emptySet())?.toMutableSet() ?: return
                val changed = current.removeAll { entry ->
                    entry == clean || (key != null && extractSupabaseFilename(entry) == key)
                }
                if (changed) {
                    prefs.edit().putStringSet("pending_supabase_deletes", current).apply()
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun flushPendingSupabaseDeletes() {
        val pendingUrls: List<String> = synchronized(pendingSyncLock) {
            prefs.getStringSet("pending_supabase_deletes", emptySet())?.toList().orEmpty()
        }
        for (urlOrKey in pendingUrls) {
            val res = supabaseRepository.deleteFileDetailed(urlOrKey)
            if (res.isSuccess) {
                removePendingSupabaseDelete(urlOrKey)
            }
        }
    }

    private suspend fun deleteSupabaseMediaUrls(urls: Iterable<String>): List<DeleteResult> {
        val seenKeys = mutableSetOf<String>()
        val results = mutableListOf<DeleteResult>()
        for (rawUrl in urls) {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.startsWith("/") || trimmed.startsWith("file:", ignoreCase = true) || trimmed.startsWith("content:", ignoreCase = true)) {
                try { File(trimmed.removePrefix("file://")).delete() } catch (_: Exception) {}
                continue
            }
            val key = extractSupabaseFilename(trimmed) ?: continue
            if (!seenKeys.add(key.lowercase())) continue
            val target = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else {
                key
            }
            val res = supabaseRepository.deleteFileDetailed(target)
            results.add(res)
            if (res.isSuccess) {
                removePendingSupabaseDelete(target)
            } else if (res.responseCode !in listOf(401, 403, 405)) {
                savePendingSupabaseDelete(target)
            }
        }
        return results
    }

    private suspend fun docRefExists(docRef: DocumentReference): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverSnap = withTimeoutOrNull(5_000L) {
                awaitTask(docRef.get(Source.SERVER))
            }
            if (serverSnap != null) return@withContext serverSnap.exists()
        } catch (_: Exception) {}
        try {
            val fallbackSnap = withTimeoutOrNull(4_000L) {
                awaitTask(docRef.get())
            }
            fallbackSnap?.exists() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractRemoteUrlsFromDoc(doc: DocumentSnapshot): List<String> {
        return listOfNotNull(
            doc.getString("imageUrl"),
            doc.getString("mediaUrl"),
            doc.getString("videoUrl"),
            doc.getString("thumbnailUrl")
        ).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun buildMediaUrlVariants(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return emptyList()
        val fullUrl = com.example.data.CdnConfig.getFullMediaUrl(trimmed)
        val key = extractSupabaseFilename(trimmed)
        return buildList {
            add(trimmed)
            if (fullUrl.isNotBlank()) add(fullUrl)
            if (!key.isNullOrBlank()) {
                add(key)
                add("https://${com.example.data.CdnConfig.PROJECT_ID}.supabase.co/storage/v1/object/public/${com.example.data.CdnConfig.BUCKET_NAME}/$key")
                add("https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/public/${com.example.data.CdnConfig.BUCKET_NAME}/$key")
                add("https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/${com.example.data.CdnConfig.BUCKET_NAME}/$key")
            }
        }.distinct()
    }

    private suspend fun deleteFirestoreDocRefDetailed(
        docRef: DocumentReference,
        maxAttempts: Int = 3
    ): FirestoreDeleteOutcome = withContext(Dispatchers.IO) {
        try {
            db.enableNetwork()
        } catch (_: Exception) {}

        val col = docRef.parent.id
        val docId = docRef.id
        removePendingFirestoreDoc(col, docId)

        var lastError: Throwable? = null
        var permanentError: String? = null
        for (attempt in 1..maxAttempts) {
            try {
                val deleted = withTimeoutOrNull(20_000L) {
                    awaitTask(docRef.delete())
                    true
                } ?: false
                if (deleted) {
                    removePendingFirestoreDelete(col, docId)
                    android.util.Log.d("YouSnapViewModel", "Firestore delete confirmed: $col/$docId (attempt $attempt)")
                    return@withContext FirestoreDeleteOutcome(success = true)
                }
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                lastError = e
                android.util.Log.e(
                    "YouSnapViewModel",
                    "Firestore delete rejected [$col/$docId] code=${e.code} attempt=$attempt: ${e.message}"
                )
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) {
                    removePendingFirestoreDelete(col, docId)
                    return@withContext FirestoreDeleteOutcome(success = true)
                }
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.INVALID_ARGUMENT
                ) {
                    permanentError = "${e.code.name}: ${e.message ?: "Permission denied"}"
                    break
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.e("YouSnapViewModel", "Firestore delete error [$col/$docId] attempt=$attempt: ${e.message}")
            }
            if (attempt < maxAttempts) {
                delay(attempt * 700L)
            }
        }
        if (permanentError != null) {
            removePendingFirestoreDelete(col, docId)
            android.util.Log.e("YouSnapViewModel", "Firestore delete permanently rejected [$col/$docId]: $permanentError")
            return@withContext FirestoreDeleteOutcome(success = false, permanentError = permanentError)
        }
        android.util.Log.w("YouSnapViewModel", "Firestore delete queued for retry [$col/$docId]: ${lastError?.message ?: "timeout"}")
        savePendingFirestoreDelete(col, docId)
        FirestoreDeleteOutcome(success = false)
    }

    private suspend fun deleteFirestoreDocRefWithRetry(
        docRef: DocumentReference,
        maxAttempts: Int = 3
    ): Boolean = deleteFirestoreDocRefDetailed(docRef, maxAttempts).success

    private suspend fun flushPendingFirestoreDocs() {
        val pendingDeletes: List<Pair<String, String>> = synchronized(pendingSyncLock) {
            val raw = prefs.getString("pending_firestore_deletes", null)
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val arr = JSONArray(raw)
                    val list = mutableListOf<Pair<String, String>>()
                    for (i in 0 until arr.length()) {
                        val entry = arr.optJSONObject(i) ?: continue
                        val col = entry.optString("collection", "")
                        val id = entry.optString("docId", "")
                        if (col.isNotBlank() && id.isNotBlank()) list.add(col to id)
                    }
                    list
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        for ((col, id) in pendingDeletes) {
            deleteFirestoreDocRefWithRetry(db.collection(col).document(id), maxAttempts = 2)
        }

        flushPendingSupabaseDeletes()

        val snapshotEntries: List<Triple<String, String, Map<String, Any>>> = synchronized(pendingSyncLock) {
            val raw = prefs.getString("pending_firestore_docs", null)
            if (raw.isNullOrBlank()) return
            try {
                val arr = JSONArray(raw)
                val list = mutableListOf<Triple<String, String, Map<String, Any>>>()
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    val col = entry.optString("collection", "")
                    val id = entry.optString("docId", "")
                    val dataObj = entry.optJSONObject("data") ?: continue
                    if (col.isBlank() || id.isBlank()) continue
                    val map = mutableMapOf<String, Any>()
                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val value = dataObj.opt(key)) {
                            is JSONArray -> {
                                val strList = mutableListOf<String>()
                                for (j in 0 until value.length()) {
                                    strList.add(value.optString(j, ""))
                                }
                                map[key] = strList
                            }
                            JSONObject.NULL, null -> Unit
                            else -> map[key] = value
                        }
                    }
                    map["createdAt"] = FieldValue.serverTimestamp()
                    list.add(Triple(col, id, map))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        for ((col, id, payload) in snapshotEntries) {
            syncDocumentToFirestoreWithRetry(collection = col, docId = id, data = payload, maxAttempts = 2)
        }
    }

    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result)
            }.addOnFailureListener { exception ->
                if (cont.isActive) cont.resumeWithException(exception)
            }
        }

    private fun extractAndSaveVideoThumbnail(videoFile: File, timestamp: Long): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                val thumbsDir = File(app.filesDir, "reel_thumbs").apply { if (!exists()) mkdirs() }
                val thumbFile = File(thumbsDir, "thumb_${timestamp}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else {
                ""
            }
        } catch (e: Throwable) {
            android.util.Log.w("YouSnapViewModel", "Thumbnail extract skipped: ${e.message}")
            ""
        }
    }

    private fun extractSupabaseFilename(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("/") || trimmed.startsWith("file:", ignoreCase = true) || trimmed.startsWith("content:", ignoreCase = true)) {
            return null
        }
        val isHttp = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        if (isHttp) {
            val isSupabaseUrl = trimmed.contains(com.example.data.CdnConfig.PROJECT_ID, ignoreCase = true) ||
                trimmed.contains("/storage/v1/object/", ignoreCase = true) ||
                trimmed.contains("yousnap-cdn.sakinayesmin2203.workers.dev", ignoreCase = true)
            if (!isSupabaseUrl) return null
        }
        val key = supabaseRepository.extractObjectKey(trimmed)
        return key.takeIf { it.isNotBlank() }
    }

    private fun isUrlOrKeyDeleted(url: String, deletedSet: Set<String>): Boolean {
        if (url.isBlank()) return false
        if (url in deletedSet || url.substringBefore("?") in deletedSet) return true
        val key = extractSupabaseFilename(url)
        return key != null && key in deletedSet
    }

    private fun getPendingDeleteKeys(): Set<String> = synchronized(pendingSyncLock) {
        val raw = prefs.getString("pending_firestore_deletes", null)
        if (raw.isNullOrBlank()) emptySet() else try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    add("${entry.optString("collection")}/${entry.optString("docId")}")
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun getPendingUploadDocIds(collection: String): Set<String> {
        val now = System.currentTimeMillis()
        val ids = mutableSetOf<String>()
        inFlightUploadDocKeys.entries.removeIf { now - it.value > 120_000L }
        val prefix = "$collection/"
        inFlightUploadDocKeys.keys.forEach { key ->
            if (key.startsWith(prefix)) {
                ids.add(key.removePrefix(prefix))
            }
        }
        synchronized(pendingSyncLock) {
            val raw = prefs.getString("pending_firestore_docs", null)
            if (!raw.isNullOrBlank()) {
                try {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val entry = arr.optJSONObject(i) ?: continue
                        if (entry.optString("collection") == collection) {
                            val id = entry.optString("docId", "")
                            if (id.isNotBlank()) ids.add(id)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return ids
    }

    private fun startListeningToUsers() {
        if (usersCollectionListener != null) return
        try {
            usersCollectionListener = db.collection("users")
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Users listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val users = snapshot.documents.mapNotNull { doc ->
                        val user = extractUserProfileFromDoc(doc)
                        updateUserMonetizationStatusCache(doc, user)
                        user
                    }
                    if (users.isNotEmpty() || !snapshot.metadata.isFromCache) {
                        allUsers.value = users
                    }
                    val myUid = activeUserUid()
                    if (!myUid.isNullOrBlank()) {
                        snapshot.documents.firstOrNull { it.id == myUid }?.let { myDoc ->
                            latestMonetizationUserDoc = myDoc
                            val remoteLink = myDoc.getString("profileLink").orEmpty()
                            val expectedLink = DeepLinkHelper.buildProfileUrl(myUid)
                            if (remoteLink.isBlank() && sessionBackfilledProfileLinkUids.add(myUid)) {
                                db.collection("users").document(myUid)
                                    .set(mapOf("profileLink" to expectedLink), SetOptions.merge())
                            }
                            profileLinkByUid[myUid] = remoteLink.ifBlank { expectedLink }
                            extractUserProfileFromDoc(myDoc)?.let { freshProfile ->
                                val resolvedStatus = resolveMonetizationStatusFromDocs(myDoc, latestMonetizationRequestDocs)
                                val resolvedProfile = freshProfile.copy(monetizationStatus = resolvedStatus)
                                _profileState.value = resolvedProfile
                                myFollowersCount.value = resolvedProfile.followersCount
                                myFollowingCount.value = resolvedProfile.followingCount
                                monetizationStatus.value = resolvedStatus
                                saveProfileToPrefs(resolvedProfile, myUid)
                            }
                        }
                    }
                }
        } catch (t: Throwable) {
            android.util.Log.w("YouSnapViewModel", "Could not attach users listener: ${t.message}")
        }
    }

    fun startListeningToFeedAndContent() {
        startListeningToUsers()

        if (storiesCollectionListener == null) {
            try {
                storiesCollectionListener = db.collection("stories")
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Stories listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyStoriesSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach stories listener: ${t.message}")
            }
        }

        if (postsCollectionListener == null) {
            try {
                postsCollectionListener = db.collection("posts")
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Posts listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyPostsSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach posts listener: ${t.message}")
            }
        }

        if (reelsCollectionListener == null) {
            try {
                reelsCollectionListener = db.collection("reels")
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Reels listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyReelsSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach reels listener: ${t.message}")
            }
        }

        if (videosCollectionListener == null) {
            try {
                videosCollectionListener = db.collection("videos")
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Videos listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyVideosSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach videos listener: ${t.message}")
            }
        }

        if (commentsCollectionListener == null) {
            try {
                commentsCollectionListener = db.collection("comments")
                    .limit(500)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Comments listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyHomeCommentsSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach comments listener: ${t.message}")
            }
        }

        if (reelCommentsCollectionListener == null) {
            try {
                reelCommentsCollectionListener = db.collection("reels_comments")
                    .limit(500)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.w("YouSnapViewModel", "Reel comments listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        applyReelCommentsSnapshot(
                            documents = snapshot.documents,
                            isFromCache = snapshot.metadata.isFromCache
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not attach reel comments listener: ${t.message}")
            }
        }
    }

    fun stopListeningToFeedAndContent() {
        storiesCollectionListener?.remove()
        storiesCollectionListener = null
        postsCollectionListener?.remove()
        postsCollectionListener = null
        reelsCollectionListener?.remove()
        reelsCollectionListener = null
        videosCollectionListener?.remove()
        videosCollectionListener = null
        usersCollectionListener?.remove()
        usersCollectionListener = null
        commentsCollectionListener?.remove()
        commentsCollectionListener = null
        reelCommentsCollectionListener?.remove()
        reelCommentsCollectionListener = null
    }

    private fun isDocumentRestricted(doc: DocumentSnapshot): Boolean {
        if (doc.getBoolean("isBlocked") == true ||
            doc.getBoolean("isBanned") == true ||
            doc.getBoolean("isSuspended") == true
        ) {
            return true
        }
        val status = doc.getString("status")?.trim()?.lowercase().orEmpty()
        return status == "banned" || status == "suspended" || status == "blocked"
    }

    private fun applyStoriesSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && allStories.value.isNotEmpty()) {
            return
        }
        val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()
        val pendingDeleteKeys = getPendingDeleteKeys()
        val pendingStoryDocIds = getPendingUploadDocIds("stories")
        val restrictedDocIds = documents.filter { isDocumentRestricted(it) }.map { it.id }.toSet()

        val nowMs = System.currentTimeMillis()
        val remoteStories = documents.mapNotNull { doc ->
            if ("stories/${doc.id}" in pendingDeleteKeys) return@mapNotNull null
            if (isDocumentRestricted(doc)) return@mapNotNull null
            val story = doc.toObject(StoryModel::class.java) ?: return@mapNotNull null
            val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(story.mediaUrl)
            if (fixedUrl.isNotBlank() && isUrlOrKeyDeleted(fixedUrl, deletedUrls)) {
                return@mapNotNull null
            }
            val ownerUid = doc.getString("userId")?.takeIf { it.isNotBlank() }
                ?: doc.getString("creatorUid")?.takeIf { it.isNotBlank() }
                ?: ""
            if (ownerUid.isNotBlank()) {
                storyCreatorUidByDocId[doc.id] = ownerUid
                if (story.ownerUsername.isNotBlank()) {
                    storyCreatorUidByOwner[story.ownerUsername.trim().lowercase()] = ownerUid
                }
            }
            val viral = FeedRankingAlgorithm.extractViralMetadata(doc)
            val createdAtMs = FeedRankingAlgorithm.extractCreatedAtMillis(doc, story.timestamp)
            normalizeStoryMedia(
                story.copy(
                    mediaUrl = fixedUrl,
                    firestoreDocId = doc.id,
                    isViral = viral.isViral,
                    viralDurationDays = viral.viralDurationDays,
                    viralDurationHours = viral.viralDurationHours,
                    viralExpiresAt = viral.viralExpiresAt,
                    createdAtMillis = createdAtMs
                )
            )
        }

        val remoteDocIds = remoteStories.map { it.firestoreDocId }.filter { it.isNotBlank() }.toSet()
        val remoteOwners = remoteStories.map { it.ownerUsername.lowercase() }.toSet()
        val protectedPendingStories = allStories.value.filter { local ->
            val docKey = local.firestoreDocId.ifBlank { local.ownerUsername }
            docKey in pendingStoryDocIds &&
                local.firestoreDocId !in restrictedDocIds &&
                local.firestoreDocId !in remoteDocIds &&
                local.ownerUsername.lowercase() !in remoteOwners &&
                (local.mediaUrl.isBlank() || !isUrlOrKeyDeleted(local.mediaUrl, deletedUrls))
        }.map { normalizeStoryMedia(it) }

        val mergedStories = FeedRankingAlgorithm.rankStories(
            stories = (remoteStories + protectedPendingStories)
                .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.ownerUsername}_${it.storyId}" },
            nowMs = nowMs
        )

        allStories.value = mergedStories
        saveStoriesToPrefs()
        scheduleNextViralExpirationCheck()

        val activeViewerStory = currentStoryState.value
        if (activeViewerStory != null) {
            val updatedMatch = mergedStories.firstOrNull {
                it.ownerUsername.equals(activeViewerStory.ownerUsername, ignoreCase = true) ||
                    (activeViewerStory.firestoreDocId.isNotBlank() && it.firestoreDocId == activeViewerStory.firestoreDocId)
            }
            currentStoryState.value = updatedMatch
        }
        recalculateCreatorStats()
    }

    private fun applyPostsSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean,
        hasPendingWrites: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && personalPhotosState.value.isNotEmpty()) {
            return
        }
        val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()
        val pendingDeleteKeys = getPendingDeleteKeys()
        val pendingPostDocIds = getPendingUploadDocIds("posts")
        val restrictedDocIds = documents.filter { isDocumentRestricted(it) }.map { it.id }.toSet()
        val myUid = activeUserUid().orEmpty()
        val myUsername = _profileState.value.username.trim()
        val usersList = allUsers.value
        val existingLikedSnapshot = homePostLikedState.value
        val existingLikesCountSnapshot = homePostLikesCount.value
        val existingSharesCountSnapshot = homePostSharesCount.value
        val existingPhotosByKey = personalPhotosState.value.associateBy { it.firestoreDocId?.takeIf { d -> d.isNotBlank() } ?: it.id }
        val nowMs = System.currentTimeMillis()
        val mutationWindowMs = 15_000L

        val remoteLikedByKey = mutableMapOf<String, Boolean>()
        val remoteLikesCountByKey = mutableMapOf<String, Int>()
        val remoteSharesCountByKey = mutableMapOf<String, Int>()

        val remotePhotos = documents.mapNotNull { doc ->
            if ("posts/${doc.id}" in pendingDeleteKeys) return@mapNotNull null
            if (isDocumentRestricted(doc)) return@mapNotNull null
            val rawUrl = doc.getString("imageUrl") ?: doc.getString("mediaUrl") ?: ""
            val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
            if (fixedUrl.isNotBlank() && isUrlOrKeyDeleted(fixedUrl, deletedUrls)) {
                return@mapNotNull null
            }
            val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
            val id = doc.getString("id") ?: ts.takeIf { it > 0L }?.toString() ?: doc.id
            val uname = doc.getString("username")?.takeIf { it.isNotBlank() && it != "user" } ?: "user"
            val matchedUser = usersList.firstOrNull { it.username.equals(uname, ignoreCase = true) }
            val display = doc.getString("name")?.takeIf { it.isNotBlank() }
                ?: matchedUser?.name?.takeIf { it.isNotBlank() }
                ?: uname
            val avatar = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                photoURL = matchedUser?.profilePic,
                profilePic = doc.getString("creatorAvatar"),
                avatar = doc.getString("photoURL")
            )
            val remoteLikesCount = (doc.getLong("likesCount") ?: 0L).toInt().coerceAtLeast(0)
            val remoteCommentsCount = (doc.getLong("commentsCount") ?: 0L).toInt().coerceAtLeast(0)
            val remoteSharesCount = (doc.getLong("sharesCount") ?: 0L).toInt().coerceAtLeast(0)
            val postKey = "photo_$id"
            val existingPhoto = existingPhotosByKey[doc.id]
                ?: personalPhotosState.value.firstOrNull { it.id == id || it.firestoreDocId == doc.id }

            val hasInFlightLike = listOf(id, postKey, doc.id).any { k ->
                nowMs - (inFlightPostLikeMutations[k] ?: 0L) < mutationWindowMs
            }
            val hasInFlightComment = listOf(id, postKey, doc.id).any { k ->
                nowMs - (inFlightPostCommentMutations[k] ?: 0L) < mutationWindowMs
            }
            val hasInFlightShare = listOf(id, postKey, doc.id).any { k ->
                nowMs - (inFlightPostShareMutations[k] ?: 0L) < mutationWindowMs
            }

            val hasLikedByField = doc.contains("likedBy")
            val likedByList = (doc.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val remoteIsLiked = if (hasLikedByField) {
                (myUid.isNotBlank() && likedByList.contains(myUid)) ||
                    (myUsername.isNotBlank() && likedByList.any { it.equals(myUsername, ignoreCase = true) })
            } else {
                existingLikedSnapshot[postKey] ?: existingLikedSnapshot[id] ?: existingPhoto?.initialIsLiked ?: false
            }

            val resolvedIsLiked = if (hasInFlightLike) {
                existingLikedSnapshot[postKey] ?: existingLikedSnapshot[id] ?: existingPhoto?.initialIsLiked ?: remoteIsLiked
            } else {
                remoteIsLiked
            }
            val resolvedLikesCount = if (hasInFlightLike) {
                existingLikesCountSnapshot[postKey] ?: existingLikesCountSnapshot[id] ?: existingPhoto?.initialLikes ?: remoteLikesCount
            } else {
                remoteLikesCount
            }
            val loadedCommentsSize = maxOf(
                homePostComments.value[postKey]?.size ?: 0,
                homePostComments.value[id]?.size ?: 0,
                homePostComments.value[doc.id]?.size ?: 0
            )
            val resolvedCommentsCount = if (hasInFlightComment) {
                maxOf(remoteCommentsCount, loadedCommentsSize, existingPhoto?.initialComments ?: 0)
            } else {
                maxOf(remoteCommentsCount, loadedCommentsSize)
            }
            val resolvedSharesCount = if (hasInFlightShare) {
                maxOf(remoteSharesCount, existingSharesCountSnapshot[postKey] ?: existingSharesCountSnapshot[id] ?: existingPhoto?.sharesCount ?: 0)
            } else {
                remoteSharesCount
            }

            remoteLikesCountByKey[id] = resolvedLikesCount
            remoteLikesCountByKey[postKey] = resolvedLikesCount
            remoteSharesCountByKey[id] = resolvedSharesCount
            remoteSharesCountByKey[postKey] = resolvedSharesCount
            if (hasLikedByField || hasInFlightLike) {
                remoteLikedByKey[id] = resolvedIsLiked
                remoteLikedByKey[postKey] = resolvedIsLiked
            }

            val resolvedShareLink = doc.getString("shareLink")?.takeIf { it.isNotBlank() }
                ?: DeepLinkHelper.buildPostUrl(doc.id)
            photoShareLinkByKey[doc.id] = resolvedShareLink
            photoShareLinkByKey[id] = resolvedShareLink
            val viral = FeedRankingAlgorithm.extractViralMetadata(doc)
            val createdAtMs = FeedRankingAlgorithm.extractCreatedAtMillis(doc, ts)

            FullScreenPhotoData(
                id = id,
                title = doc.getString("caption") ?: doc.getString("title") ?: "",
                imageUrl = fixedUrl,
                creatorName = display,
                creatorUsername = uname,
                creatorAvatar = avatar,
                creatorUid = doc.getString("creatorUid") ?: doc.getString("userId") ?: "",
                initialLikes = resolvedLikesCount,
                initialComments = resolvedCommentsCount,
                sharesCount = resolvedSharesCount,
                initialIsLiked = resolvedIsLiked,
                timeAgo = if (ts > 0L) formatRelativeTime(ts) else "Just now",
                firestoreDocId = doc.id,
                shareLink = resolvedShareLink,
                isViral = viral.isViral,
                viralDurationDays = viral.viralDurationDays,
                viralDurationHours = viral.viralDurationHours,
                viralExpiresAt = viral.viralExpiresAt,
                createdAtMillis = createdAtMs
            )
        }

        val remoteDocIds = remotePhotos.mapNotNull { it.firestoreDocId }.toSet()
        val remoteIds = remotePhotos.map { it.id }.toSet()
        val remoteUrls = remotePhotos.map { it.imageUrl }.filter { it.isNotBlank() }.toSet()

        val protectedPendingPhotos = personalPhotosState.value.filter { local ->
            val docKey = local.firestoreDocId.orEmpty().ifBlank { local.id }
            (docKey in pendingPostDocIds || local.id in pendingPostDocIds) &&
                local.firestoreDocId !in restrictedDocIds &&
                local.firestoreDocId !in remoteDocIds &&
                local.id !in remoteIds &&
                (local.imageUrl.isBlank() || local.imageUrl !in remoteUrls) &&
                (local.imageUrl.isBlank() || !isUrlOrKeyDeleted(local.imageUrl, deletedUrls))
        }

        val mergedPhotos = FeedRankingAlgorithm.rankPhotos(
            photos = (remotePhotos + protectedPendingPhotos)
                .distinctBy { if (it.imageUrl.isNotBlank()) it.imageUrl else it.id },
            nowMs = nowMs
        )

        personalPhotosState.value = mergedPhotos
        savePersonalPhotosToPrefs()
        scheduleNextViralExpirationCheck()

        val activeIds = mergedPhotos.flatMap {
            listOfNotNull(it.id, "photo_${it.id}", it.firestoreDocId?.takeIf { d -> d.isNotBlank() })
        }.toSet()
        val nextLikesCount = homePostLikesCount.value.filterKeys { it in activeIds }.toMutableMap()
        val nextLikedState = homePostLikedState.value.filterKeys { it in activeIds }.toMutableMap()
        val nextSharesCount = homePostSharesCount.value.filterKeys { it in activeIds }.toMutableMap()
        if (!hasPendingWrites) {
            nextLikesCount.putAll(remoteLikesCountByKey)
            nextLikedState.putAll(remoteLikedByKey)
            nextSharesCount.putAll(remoteSharesCountByKey)
        }
        homePostLikesCount.value = nextLikesCount
        homePostLikedState.value = nextLikedState
        homePostSharesCount.value = nextSharesCount
        homePostComments.value = homePostComments.value.filterKeys { it in activeIds }
        homePostBookmarked.value = homePostBookmarked.value.filterKeys { it in activeIds }

        val openedPost = selectedHomePost.value
        if (openedPost != null) {
            val stillExists = mergedPhotos.firstOrNull {
                (!openedPost.firestoreDocId.isNullOrBlank() && it.firestoreDocId == openedPost.firestoreDocId) ||
                    it.id == openedPost.id ||
                    (openedPost.imageUrl.isNotBlank() && it.imageUrl == openedPost.imageUrl)
            }
            selectedHomePost.value = stillExists
        }

        recalculateCreatorStats()
    }

    private fun mapReelOrVideoDocuments(
        documents: List<DocumentSnapshot>,
        sourceCollection: String,
        deletedUrls: Set<String>,
        pendingDeleteKeys: Set<String>,
        myUid: String,
        myUsername: String,
        usersList: List<ProfileEntity>,
        followingMap: Map<String, Boolean>,
        existingThumbByUrl: Map<String, String>,
        existingUidByUrl: Map<String, String>
    ): List<ReelEntity> {
        val nowMs = System.currentTimeMillis()
        val mutationWindowMs = 15_000L
        val existingById = _reelsState.value.associateBy { it.id }
        val existingByUrl = _reelsState.value.filter { it.mediaUrl.isNotBlank() }.associateBy { it.mediaUrl }

        return documents.mapNotNull { doc ->
            if ("$sourceCollection/${doc.id}" in pendingDeleteKeys) return@mapNotNull null
            if (isDocumentRestricted(doc)) return@mapNotNull null
            val rawUrl = doc.getString("mediaUrl") ?: doc.getString("videoUrl") ?: ""
            val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
            if (fixedUrl.isNotBlank() && isUrlOrKeyDeleted(fixedUrl, deletedUrls)) {
                return@mapNotNull null
            }
            val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
            val reelId = doc.getLong("reelIntId")?.toInt()
                ?: doc.getString("id")?.toLongOrNull()?.toInt()
                ?: ts.takeIf { it > 0L }?.toInt()
                ?: doc.id.hashCode()
            val uname = doc.getString("username")?.takeIf { it.isNotBlank() && it != "user" } ?: "user"
            val matchedUser = usersList.firstOrNull { it.username.equals(uname, ignoreCase = true) }
            val display = doc.getString("name")?.takeIf { it.isNotBlank() }
                ?: matchedUser?.name?.takeIf { it.isNotBlank() }
                ?: uname
            val avatar = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                photoURL = matchedUser?.profilePic,
                profilePic = doc.getString("creatorAvatar"),
                avatar = doc.getString("photoURL")
            )
            val audioTitle = doc.getString("audioTitle")?.takeIf { it.isNotBlank() } ?: "Original Audio"
            val likedByList = (doc.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val remoteIsLiked = (myUid.isNotBlank() && likedByList.contains(myUid)) ||
                (myUsername.isNotBlank() && likedByList.any { it.equals(myUsername, ignoreCase = true) })
            val isFollowingCreator = followingMap.entries
                .firstOrNull { it.key.equals(uname, ignoreCase = true) }?.value ?: false

            val existingReel = existingById[reelId] ?: existingByUrl[fixedUrl]
            val hasInFlightLike = nowMs - (inFlightReelLikeMutations[reelId] ?: 0L) < mutationWindowMs
            val hasInFlightComment = nowMs - (inFlightReelCommentMutations[reelId] ?: 0L) < mutationWindowMs
            val hasInFlightShare = nowMs - (inFlightReelShareMutations[reelId] ?: 0L) < mutationWindowMs

            val remoteLikesCount = (doc.getLong("likesCount") ?: 0L).toInt().coerceAtLeast(0)
            val remoteCommentsCount = (doc.getLong("commentsCount") ?: 0L).toInt().coerceAtLeast(0)
            val remoteSharesCount = (doc.getLong("sharesCount") ?: 0L).toInt().coerceAtLeast(0)
            val loadedReelCommentsSize = reelCommentsByReelId.value[reelId]?.size ?: 0

            val resolvedIsLiked = if (hasInFlightLike && existingReel != null) existingReel.isLiked else remoteIsLiked
            val resolvedLikesCount = if (hasInFlightLike && existingReel != null) existingReel.likesCount else remoteLikesCount
            val resolvedCommentsCount = if (hasInFlightComment && existingReel != null) {
                maxOf(remoteCommentsCount, loadedReelCommentsSize, existingReel.commentsCount)
            } else {
                maxOf(remoteCommentsCount, loadedReelCommentsSize)
            }
            val resolvedSharesCount = if (hasInFlightShare && existingReel != null) {
                maxOf(remoteSharesCount, existingReel.sharesCount)
            } else {
                remoteSharesCount
            }

            if (fixedUrl.isNotBlank()) {
                reelFirestoreDocIdByMediaUrl[fixedUrl] = doc.id
                reelCollectionByMediaUrl[fixedUrl] = sourceCollection
            }
            if (rawUrl.isNotBlank()) {
                reelFirestoreDocIdByMediaUrl[rawUrl] = doc.id
                reelCollectionByMediaUrl[rawUrl] = sourceCollection
            }
            if (ts > 0L) reelFirestoreDocIdByTimestamp[ts] = doc.id
            reelFirestoreDocIdById[reelId] = doc.id
            reelCollectionById[reelId] = sourceCollection
            val resolvedShareLink = doc.getString("shareLink")?.takeIf { it.isNotBlank() }
                ?: DeepLinkHelper.buildReelUrl(doc.id)
            reelShareLinkByKey[doc.id] = resolvedShareLink
            reelShareLinkByKey[reelId.toString()] = resolvedShareLink
            val viral = FeedRankingAlgorithm.extractViralMetadata(doc)
            val createdAtMs = FeedRankingAlgorithm.extractCreatedAtMillis(doc, ts)

            ReelEntity(
                id = reelId,
                creatorName = display,
                creatorUsername = uname,
                creatorProfilePic = avatar,
                creatorUid = doc.getString("creatorUid") ?: doc.getString("userId") ?: existingUidByUrl[fixedUrl] ?: "",
                mediaUrl = fixedUrl,
                thumbnailUrl = doc.getString("thumbnailUrl").orEmpty().ifBlank { existingThumbByUrl[fixedUrl] ?: "" },
                caption = doc.getString("caption") ?: doc.getString("title") ?: "",
                audioTitle = audioTitle,
                likesCount = resolvedLikesCount,
                commentsCount = resolvedCommentsCount,
                sharesCount = resolvedSharesCount,
                viewsCount = (doc.getLong("viewsCount") ?: 0L).toInt().coerceAtLeast(0),
                isLiked = resolvedIsLiked,
                isFollowingCreator = isFollowingCreator,
                timestamp = ts,
                bgGradientStart = doc.getString("bgGradientStart") ?: "#161616",
                bgGradientEnd = doc.getString("bgGradientEnd") ?: "#161616",
                isViral = viral.isViral,
                viralDurationDays = viral.viralDurationDays,
                viralDurationHours = viral.viralDurationHours,
                viralExpiresAt = viral.viralExpiresAt,
                createdAtMillis = createdAtMs
            )
        }
    }

    private fun applyReelsSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && cachedRemoteReels.isNotEmpty()) {
            return
        }
        val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()
        val pendingDeleteKeys = getPendingDeleteKeys()
        restrictedReelDocIds = documents.filter { isDocumentRestricted(it) }.map { it.id }.toSet()

        val existingThumbByUrl = _reelsState.value
            .filter { it.mediaUrl.isNotBlank() && it.thumbnailUrl.isNotBlank() }
            .associate { it.mediaUrl to it.thumbnailUrl }
        val existingUidByUrl = _reelsState.value
            .filter { it.mediaUrl.isNotBlank() && it.creatorUid.isNotBlank() }
            .associate { it.mediaUrl to it.creatorUid }

        cachedRemoteReels = mapReelOrVideoDocuments(
            documents = documents,
            sourceCollection = "reels",
            deletedUrls = deletedUrls,
            pendingDeleteKeys = pendingDeleteKeys,
            myUid = activeUserUid().orEmpty(),
            myUsername = _profileState.value.username.trim(),
            usersList = allUsers.value,
            followingMap = otherUserIsFollowing.value,
            existingThumbByUrl = existingThumbByUrl,
            existingUidByUrl = existingUidByUrl
        )
        publishMergedReelsAndVideos()
    }

    private fun applyVideosSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && cachedRemoteVideos.isNotEmpty()) {
            return
        }
        val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()
        val pendingDeleteKeys = getPendingDeleteKeys()
        restrictedVideoDocIds = documents.filter { isDocumentRestricted(it) }.map { it.id }.toSet()

        val existingThumbByUrl = _reelsState.value
            .filter { it.mediaUrl.isNotBlank() && it.thumbnailUrl.isNotBlank() }
            .associate { it.mediaUrl to it.thumbnailUrl }
        val existingUidByUrl = _reelsState.value
            .filter { it.mediaUrl.isNotBlank() && it.creatorUid.isNotBlank() }
            .associate { it.mediaUrl to it.creatorUid }

        cachedRemoteVideos = mapReelOrVideoDocuments(
            documents = documents,
            sourceCollection = "videos",
            deletedUrls = deletedUrls,
            pendingDeleteKeys = pendingDeleteKeys,
            myUid = activeUserUid().orEmpty(),
            myUsername = _profileState.value.username.trim(),
            usersList = allUsers.value,
            followingMap = otherUserIsFollowing.value,
            existingThumbByUrl = existingThumbByUrl,
            existingUidByUrl = existingUidByUrl
        )
        publishMergedReelsAndVideos()
    }

    private fun publishMergedReelsAndVideos() {
        val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()
        val pendingReelDocIds = getPendingUploadDocIds("reels")
        val combinedRemote = cachedRemoteReels + cachedRemoteVideos
        val restrictedIds = restrictedReelDocIds + restrictedVideoDocIds

        val remoteIds = combinedRemote.map { it.id }.toSet()
        val remoteUrls = combinedRemote.map { it.mediaUrl }.filter { it.isNotBlank() }.toSet()
        val protectedPendingReels = _reelsState.value.filter { local ->
            val safeOwner = local.creatorUsername.lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }.take(32)
            val candidateDocId = if (local.timestamp > 0L) "reel_${safeOwner}_${local.timestamp}" else ""
            (candidateDocId in pendingReelDocIds || pendingReelDocIds.any { it.endsWith("_${local.timestamp}") }) &&
                candidateDocId !in restrictedIds &&
                local.id !in remoteIds &&
                (local.mediaUrl.isBlank() || local.mediaUrl !in remoteUrls) &&
                (local.mediaUrl.isBlank() || !isUrlOrKeyDeleted(local.mediaUrl, deletedUrls))
        }

        val nowMs = System.currentTimeMillis()
        val mergedReels = FeedRankingAlgorithm.rankReels(
            reels = (combinedRemote + protectedPendingReels)
                .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.id}_${it.timestamp}" },
            nowMs = nowMs
        )

        _reelsState.value = mergedReels
        saveReelsToPrefs()
        scheduleNextViralExpirationCheck()
        recalculateCreatorStats()

        val activeIds = mergedReels.map { it.id }.toSet()
        val activeUrls = mergedReels.map { it.mediaUrl }.filter { it.isNotBlank() }.toSet()
        watchHistory.value = watchHistory.value.filter { item ->
            item.id in activeIds || (item.mediaUrl.isNotBlank() && item.mediaUrl in activeUrls)
        }
        bookmarkedReelIds.value = bookmarkedReelIds.value.intersect(activeIds)
        selectedReelForPreview.value = selectedReelForPreview.value?.let { currentPreview ->
            mergedReels.firstOrNull {
                it.id == currentPreview.id || (currentPreview.mediaUrl.isNotBlank() && it.mediaUrl == currentPreview.mediaUrl)
            }
        }

        if (searchQuery.value.isNotBlank()) {
            searchResultPosts.value = mergedReels.filter {
                it.caption.contains(searchQuery.value, ignoreCase = true) ||
                    it.creatorUsername.contains(searchQuery.value, ignoreCase = true)
            }
        }
    }

    private fun scheduleNextViralExpirationCheck() {
        viralExpirationJob?.cancel()
        val nowMs = System.currentTimeMillis()
        val earliestExpiryMs = FeedRankingAlgorithm.findEarliestFutureExpiryMs(
            photos = personalPhotosState.value,
            reels = _reelsState.value,
            stories = allStories.value,
            nowMs = nowMs
        ) ?: return

        viralExpirationJob = viewModelScope.launch {
            while (isActive) {
                val currentMs = System.currentTimeMillis()
                val nextExpiry = FeedRankingAlgorithm.findEarliestFutureExpiryMs(
                    photos = personalPhotosState.value,
                    reels = _reelsState.value,
                    stories = allStories.value,
                    nowMs = currentMs
                ) ?: break
                val remainingMs = nextExpiry - currentMs
                if (remainingMs <= 0L) {
                    rerankFeedsForViralExpiration(currentMs)
                } else {
                    delay(minOf(remainingMs + 150L, 60_000L))
                    val afterWaitMs = System.currentTimeMillis()
                    if (afterWaitMs >= nextExpiry) {
                        rerankFeedsForViralExpiration(afterWaitMs)
                    }
                }
            }
        }
    }

    private fun rerankFeedsForViralExpiration(nowMs: Long = System.currentTimeMillis()) {
        val rankedPhotos = FeedRankingAlgorithm.rankPhotos(personalPhotosState.value, nowMs)
        if (rankedPhotos != personalPhotosState.value) {
            personalPhotosState.value = rankedPhotos
            savePersonalPhotosToPrefs()
        }

        val rankedReels = FeedRankingAlgorithm.rankReels(_reelsState.value, nowMs)
        if (rankedReels != _reelsState.value) {
            _reelsState.value = rankedReels
            saveReelsToPrefs()
        }

        val rankedStories = FeedRankingAlgorithm.rankStories(allStories.value, nowMs)
        if (rankedStories != allStories.value) {
            allStories.value = rankedStories
            saveStoriesToPrefs()
        }

        val q = searchQuery.value.trim()
        if (q.isNotEmpty()) {
            searchResultPosts.value = rankedReels.filter {
                it.caption.contains(q, ignoreCase = true) ||
                    it.creatorUsername.contains(q, ignoreCase = true)
            }
        }
    }

    fun refreshHomeFeed(showIndicator: Boolean = true, onComplete: () -> Unit = {}) {
        startListeningToFeedAndContent()
        refreshFeedJob?.cancel()
        val launchedJob = viewModelScope.launch {
            if (showIndicator) {
                isHomeRefreshing.value = true
            }
            try {
                flushPendingFirestoreDocs()

                suspend fun fetchLatest(collection: String): com.google.firebase.firestore.QuerySnapshot? =
                    withContext(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(8000L) {
                                awaitTask(
                                    db.collection(collection)
                                        .limit(200)
                                        .get(com.google.firebase.firestore.Source.SERVER)
                                )
                            }
                        } catch (_: Exception) {
                            try {
                                withTimeoutOrNull(8000L) {
                                    awaitTask(db.collection(collection).limit(200).get())
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("YouSnapViewModel", "Firestore $collection fetch notice: ${e.message}")
                                null
                            }
                        }
                    }

                val storiesSnapshot = fetchLatest("stories")
                val postsSnapshot = fetchLatest("posts")
                val reelsSnapshot = fetchLatest("reels")
                val videosSnapshot = fetchLatest("videos")
                val commentsSnapshot = fetchLatest("comments")
                val reelCommentsSnapshot = fetchLatest("reels_comments")

                if (commentsSnapshot != null) {
                    applyHomeCommentsSnapshot(commentsSnapshot.documents, commentsSnapshot.metadata.isFromCache)
                }
                if (reelCommentsSnapshot != null) {
                    applyReelCommentsSnapshot(reelCommentsSnapshot.documents, reelCommentsSnapshot.metadata.isFromCache)
                }
                if (storiesSnapshot != null) {
                    applyStoriesSnapshot(storiesSnapshot.documents, storiesSnapshot.metadata.isFromCache)
                }
                if (postsSnapshot != null) {
                    applyPostsSnapshot(
                        postsSnapshot.documents,
                        postsSnapshot.metadata.isFromCache,
                        postsSnapshot.metadata.hasPendingWrites()
                    )
                }
                if (reelsSnapshot != null) {
                    applyReelsSnapshot(reelsSnapshot.documents, reelsSnapshot.metadata.isFromCache)
                }
                if (videosSnapshot != null) {
                    applyVideosSnapshot(videosSnapshot.documents, videosSnapshot.metadata.isFromCache)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "refreshHomeFeed error: ${e.message}")
            } finally {
                if (refreshFeedJob === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]) {
                    isHomeRefreshing.value = false
                }
                onComplete()
            }
        }
        refreshFeedJob = launchedJob
    }

    private fun applyHomeCommentsSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && homePostComments.value.isNotEmpty()) {
            return
        }
        val grouped = mutableMapOf<String, MutableList<CommentEntity>>()
        val seenSignatures = mutableSetOf<String>()

        fun addForKey(key: String, entity: CommentEntity) {
            if (key.isBlank()) return
            val sig = "$key|${entity.username}|${entity.text}|${entity.timestamp}"
            if (seenSignatures.add(sig)) {
                grouped.getOrPut(key) { mutableListOf() }.add(entity)
            }
        }

        for (doc in documents) {
            val rawPostId = doc.getString("postId")?.trim().orEmpty()
            val cleanPostId = doc.getString("cleanPostId")?.trim()?.takeIf { it.isNotBlank() }
                ?: rawPostId.removePrefix("photo_")
            val firestoreDocId = doc.getString("firestoreDocId")?.trim().orEmpty()
            if (rawPostId.isBlank() && cleanPostId.isBlank() && firestoreDocId.isBlank()) continue

            val username = doc.getString("username")?.takeIf { it.isNotBlank() } ?: "user"
            val text = doc.getString("text")?.trim().orEmpty()
            if (text.isBlank()) continue
            val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val intId = (doc.getLong("id")?.toInt()) ?: doc.id.hashCode()
            val entity = CommentEntity(
                id = intId,
                postId = rawPostId.ifBlank { "photo_$cleanPostId" },
                username = username,
                text = text,
                timestamp = ts
            )

            if (cleanPostId.isNotBlank()) {
                addForKey(cleanPostId, entity)
                addForKey("photo_$cleanPostId", entity)
            }
            if (rawPostId.isNotBlank()) {
                addForKey(rawPostId, entity)
            }
            if (firestoreDocId.isNotBlank()) {
                addForKey(firestoreDocId, entity)
            }
        }

        // Preserve any very recent optimistic local comments not yet returned by server snapshot
        val recentCutoff = System.currentTimeMillis() - 15_000L
        homePostComments.value.forEach { (key, existingList) ->
            existingList.filter { it.timestamp >= recentCutoff }.forEach { recent ->
                addForKey(key, recent)
            }
        }

        val sortedMap = grouped.mapValues { (_, list) -> list.sortedBy { it.timestamp } }
        homePostComments.value = sortedMap
        saveHomeCommentsToPrefs()

        personalPhotosState.value = personalPhotosState.value.map { photo ->
            val countFromComments = maxOf(
                sortedMap["photo_${photo.id}"]?.size ?: 0,
                sortedMap[photo.id]?.size ?: 0,
                photo.firestoreDocId?.let { sortedMap[it]?.size ?: 0 } ?: 0
            )
            if (countFromComments > photo.initialComments) {
                photo.copy(initialComments = countFromComments)
            } else photo
        }
        selectedHomePost.value = selectedHomePost.value?.let { opened ->
            personalPhotosState.value.firstOrNull {
                it.id == opened.id || (!opened.firestoreDocId.isNullOrBlank() && it.firestoreDocId == opened.firestoreDocId)
            } ?: opened
        }
    }

    private fun applyReelCommentsSnapshot(
        documents: List<DocumentSnapshot>,
        isFromCache: Boolean
    ) {
        if (documents.isEmpty() && isFromCache && reelCommentsByReelId.value.isNotEmpty()) {
            return
        }
        val grouped = mutableMapOf<Int, MutableList<CommentEntity>>()
        val seenSignatures = mutableSetOf<String>()

        for (doc in documents) {
            val rawReelId = doc.getString("reelId")?.trim()
                ?: doc.getLong("reelId")?.toString()
                ?: ""
            val reelDocId = doc.getString("reelDocId")?.trim().orEmpty()
            val resolvedReelId = rawReelId.toIntOrNull()
                ?: reelFirestoreDocIdById.entries.firstOrNull { it.value == reelDocId }?.key
                ?: continue
            val username = doc.getString("username")?.takeIf { it.isNotBlank() } ?: "user"
            val text = doc.getString("text")?.trim().orEmpty()
            if (text.isBlank()) continue
            val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val intId = (doc.getLong("id")?.toInt()) ?: doc.id.hashCode()
            val sig = "$resolvedReelId|$username|$text|$ts"
            if (seenSignatures.add(sig)) {
                grouped.getOrPut(resolvedReelId) { mutableListOf() }.add(
                    CommentEntity(
                        id = intId,
                        reelId = resolvedReelId,
                        username = username,
                        text = text,
                        timestamp = ts
                    )
                )
            }
        }

        val recentCutoff = System.currentTimeMillis() - 15_000L
        reelCommentsByReelId.value.forEach { (rId, existingList) ->
            existingList.filter { it.timestamp >= recentCutoff }.forEach { recent ->
                val sig = "$rId|${recent.username}|${recent.text}|${recent.timestamp}"
                if (seenSignatures.add(sig)) {
                    grouped.getOrPut(rId) { mutableListOf() }.add(recent)
                }
            }
        }

        val sortedMap = grouped.mapValues { (_, list) -> list.sortedBy { it.timestamp } }
        reelCommentsByReelId.value = sortedMap
        saveReelCommentsToPrefs()

        val activeId = activeCommentsReelId
        if (activeId != null) {
            activeReelComments.value = sortedMap[activeId].orEmpty()
        }

        _reelsState.value = _reelsState.value.map { reel ->
            val countFromComments = sortedMap[reel.id]?.size ?: 0
            if (countFromComments > reel.commentsCount) {
                reel.copy(commentsCount = countFromComments)
            } else reel
        }
        selectedReelForPreview.value = selectedReelForPreview.value?.let { currentPreview ->
            _reelsState.value.firstOrNull { it.id == currentPreview.id } ?: currentPreview
        }
    }

    fun loadCommentsForHomePost(postId: String, firestoreDocId: String? = null) {
        val cleanId = postId.removePrefix("photo_")
        val photoKey = "photo_$cleanId"
        val docId = firestoreDocId?.takeIf { it.isNotBlank() }
            ?: personalPhotosState.value.firstOrNull { it.id == cleanId || it.id == postId }?.firestoreDocId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val collectedDocs = LinkedHashMap<String, DocumentSnapshot>()
                val queries = buildList {
                    if (photoKey.isNotBlank()) add(db.collection("comments").whereEqualTo("postId", photoKey).get())
                    if (cleanId.isNotBlank()) {
                        add(db.collection("comments").whereEqualTo("postId", cleanId).get())
                        add(db.collection("comments").whereEqualTo("cleanPostId", cleanId).get())
                    }
                    if (!docId.isNullOrBlank()) {
                        add(db.collection("comments").whereEqualTo("firestoreDocId", docId).get())
                        add(db.collection("comments").whereEqualTo("postId", docId).get())
                    }
                }
                for (task in queries) {
                    val snap = withTimeoutOrNull(5000L) { awaitTask(task) } ?: continue
                    snap.documents.forEach { collectedDocs[it.id] = it }
                }
                if (collectedDocs.isNotEmpty()) {
                    val mergedList = collectedDocs.values.mapNotNull { doc ->
                        val text = doc.getString("text")?.trim().orEmpty()
                        if (text.isBlank()) return@mapNotNull null
                        val uname = doc.getString("username")?.takeIf { it.isNotBlank() } ?: "user"
                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        CommentEntity(
                            id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                            postId = photoKey,
                            username = uname,
                            text = text,
                            timestamp = ts
                        )
                    }.distinctBy { "${it.username}|${it.text}|${it.timestamp}" }
                        .sortedBy { it.timestamp }

                    withContext(Dispatchers.Main) {
                        val currentMap = homePostComments.value.toMutableMap()
                        val existing = (currentMap[photoKey].orEmpty() + currentMap[cleanId].orEmpty() + mergedList)
                            .distinctBy { "${it.username}|${it.text}|${it.timestamp}" }
                            .sortedBy { it.timestamp }
                        currentMap[photoKey] = existing
                        if (cleanId.isNotBlank()) currentMap[cleanId] = existing
                        if (!docId.isNullOrBlank()) currentMap[docId] = existing
                        homePostComments.value = currentMap
                        saveHomeCommentsToPrefs()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "loadCommentsForHomePost notice: ${e.message}")
            }
        }
    }

    fun loadCommentsForReel(id: Int) {
        activeCommentsReelId = id
        activeReelComments.value = reelCommentsByReelId.value[id].orEmpty()
        db.collection("reels_comments").whereEqualTo("reelId", id.toString()).get().addOnSuccessListener { snapshot ->
            val list = snapshot.documents.mapNotNull { doc ->
                val text = doc.getString("text")?.trim().orEmpty()
                if (text.isBlank()) return@mapNotNull null
                CommentEntity(
                    id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                    reelId = id,
                    username = doc.getString("username")?.takeIf { it.isNotBlank() } ?: "user",
                    text = text,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }
            val merged = (reelCommentsByReelId.value[id].orEmpty() + list)
                .distinctBy { "${it.username}|${it.text}|${it.timestamp}" }
                .sortedBy { it.timestamp }
            reelCommentsByReelId.value = reelCommentsByReelId.value + (id to merged)
            if (activeCommentsReelId == id) {
                activeReelComments.value = merged
            }
            saveReelCommentsToPrefs()
        }
    }

    fun toggleLikeHomePost(postId: String) {
        val cleanId = postId.removePrefix("photo_")
        val photoKey = "photo_$cleanId"
        val isCurrentlyLiked = homePostLikedState.value[photoKey]
            ?: homePostLikedState.value[postId]
            ?: homePostLikedState.value[cleanId]
            ?: false
        val newLiked = !isCurrentlyLiked
        val targetPhoto = personalPhotosState.value.find { it.id == cleanId || it.id == postId }
            ?: selectedHomePost.value?.takeIf { it.id == cleanId || it.id == postId }
        val baseCount = homePostLikesCount.value[photoKey]
            ?: homePostLikesCount.value[postId]
            ?: homePostLikesCount.value[cleanId]
            ?: targetPhoto?.initialLikes
            ?: 0
        val newCount = if (newLiked) baseCount + 1 else (baseCount - 1).coerceAtLeast(0)

        val keys = listOfNotNull(postId, cleanId, photoKey, targetPhoto?.firestoreDocId?.takeIf { it.isNotBlank() }).distinct()
        val nowMs = System.currentTimeMillis()
        keys.forEach { inFlightPostLikeMutations[it] = nowMs }

        homePostLikedState.value = homePostLikedState.value + keys.associateWith { newLiked }
        homePostLikesCount.value = homePostLikesCount.value + keys.associateWith { newCount }
        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == cleanId || p.id == postId || (targetPhoto != null && p.firestoreDocId == targetPhoto.firestoreDocId && !p.firestoreDocId.isNullOrBlank())) {
                p.copy(initialLikes = newCount, initialIsLiked = newLiked)
            } else p
        }
        selectedHomePost.value = selectedHomePost.value?.let { opened ->
            if (opened.id == cleanId || opened.id == postId) opened.copy(initialLikes = newCount, initialIsLiked = newLiked) else opened
        }
        savePersonalPhotosToPrefs()

        syncPostLikeToFirestore(targetPhoto, cleanId, newLiked, newCount)
    }

    fun notifyPhotoLikedFromViewer(photo: FullScreenPhotoData, isLiked: Boolean, newCount: Int) {
        val cleanId = photo.id.removePrefix("photo_")
        val keys = listOfNotNull(photo.id, cleanId, "photo_$cleanId", photo.firestoreDocId?.takeIf { it.isNotBlank() }).distinct()
        val nowMs = System.currentTimeMillis()
        keys.forEach { inFlightPostLikeMutations[it] = nowMs }

        homePostLikedState.value = homePostLikedState.value + keys.associateWith { isLiked }
        homePostLikesCount.value = homePostLikesCount.value + keys.associateWith { newCount }
        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == photo.id || p.id == cleanId || (photo.imageUrl.isNotBlank() && p.imageUrl == photo.imageUrl)) {
                p.copy(initialLikes = newCount, initialIsLiked = isLiked)
            } else p
        }
        selectedHomePost.value = selectedHomePost.value?.let { opened ->
            if (opened.id == photo.id || opened.id == cleanId) opened.copy(initialLikes = newCount, initialIsLiked = isLiked) else opened
        }
        savePersonalPhotosToPrefs()

        syncPostLikeToFirestore(photo, cleanId, isLiked, newCount)
    }

    private suspend fun resolvePostDocRefs(
        targetPhoto: FullScreenPhotoData?,
        cleanId: String
    ): List<com.google.firebase.firestore.DocumentReference> {
        val refs = LinkedHashMap<String, com.google.firebase.firestore.DocumentReference>()
        val directDocId = targetPhoto?.firestoreDocId?.takeIf { it.isNotBlank() }
        if (directDocId != null) {
            val directRef = db.collection("posts").document(directDocId)
            val snap = try { withTimeoutOrNull(5000L) { awaitTask(directRef.get()) } } catch (_: Exception) { null }
            if (snap != null && snap.exists()) {
                refs[directRef.path] = directRef
            }
        }
        if (refs.isEmpty() && cleanId.isNotBlank()) {
            val snap = try {
                withTimeoutOrNull(5000L) {
                    awaitTask(db.collection("posts").whereEqualTo("id", cleanId).limit(3).get())
                }
            } catch (_: Exception) { null }
            snap?.documents?.forEach { refs[it.reference.path] = it.reference }
        }
        if (refs.isEmpty() && targetPhoto != null && targetPhoto.imageUrl.isNotBlank()) {
            val snap = try {
                withTimeoutOrNull(5000L) {
                    awaitTask(db.collection("posts").whereEqualTo("imageUrl", targetPhoto.imageUrl).limit(3).get())
                }
            } catch (_: Exception) { null }
            snap?.documents?.forEach { refs[it.reference.path] = it.reference }
        }
        if (refs.isEmpty() && directDocId != null) {
            refs["posts/$directDocId"] = db.collection("posts").document(directDocId)
        }
        return refs.values.toList()
    }

    private fun syncPostLikeToFirestore(
        targetPhoto: FullScreenPhotoData?,
        cleanId: String,
        newLiked: Boolean,
        expectedCount: Int
    ) {
        val myUid = activeUserUid().orEmpty().ifBlank { _profileState.value.username.ifBlank { "user" } }
        val myUsername = _profileState.value.username.trim()
        val mutationKeys = listOfNotNull(cleanId, "photo_$cleanId", targetPhoto?.id, targetPhoto?.firestoreDocId).distinct()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                val refs = resolvePostDocRefs(targetPhoto, cleanId)
                for (ref in refs) {
                    val snap = try { withTimeoutOrNull(5000L) { awaitTask(ref.get()) } } catch (_: Exception) { null }
                    val existingLikedBy = (snap?.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
                        ?: mutableListOf()
                    val alreadyContainsMe = existingLikedBy.contains(myUid) ||
                        (myUsername.isNotBlank() && existingLikedBy.any { it.equals(myUsername, ignoreCase = true) })
                    val currentCount = (snap?.getLong("likesCount") ?: 0L).toInt().coerceAtLeast(0)
                    val nextCount = if (newLiked) {
                        if (alreadyContainsMe) maxOf(currentCount, expectedCount) else currentCount + 1
                    } else {
                        if (alreadyContainsMe) (currentCount - 1).coerceAtLeast(0) else expectedCount.coerceAtLeast(0)
                    }

                    if (newLiked) {
                        if (!existingLikedBy.contains(myUid)) existingLikedBy.add(myUid)
                    } else {
                        existingLikedBy.removeAll { it == myUid || (myUsername.isNotBlank() && it.equals(myUsername, ignoreCase = true)) }
                    }

                    val payload = mapOf(
                        "likesCount" to nextCount,
                        "likedBy" to existingLikedBy.distinct()
                    )
                    for (attempt in 1..3) {
                        try {
                            withTimeoutOrNull(10_000L) {
                                awaitTask(ref.set(payload, SetOptions.merge()))
                            }
                            break
                        } catch (_: Exception) {
                            if (attempt < 3) delay(attempt * 500L)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Post like sync notice: ${e.message}")
            } finally {
                mutationKeys.forEach { inFlightPostLikeMutations.remove(it) }
            }
        }

        if (newLiked && targetPhoto != null) {
            val safeSender = _profileState.value.username.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "user" }
            createAndPushFirestoreNotification(
                type = "like",
                recipientUsername = targetPhoto.creatorUsername,
                recipientUid = targetPhoto.creatorUid,
                detail = "liked your photo.",
                postId = targetPhoto.firestoreDocId ?: targetPhoto.id,
                postMediaUrl = targetPhoto.imageUrl,
                fixedDocId = "notif_like_${safeSender}_${targetPhoto.id}"
            )
        }
    }

    fun incrementPostShares(photo: FullScreenPhotoData) {
        val cleanId = photo.id.removePrefix("photo_")
        val photoKey = "photo_$cleanId"
        val currentShares = homePostSharesCount.value[photoKey]
            ?: homePostSharesCount.value[photo.id]
            ?: homePostSharesCount.value[cleanId]
            ?: photo.sharesCount
        val nextShares = currentShares + 1
        val keys = listOfNotNull(photo.id, cleanId, photoKey, photo.firestoreDocId?.takeIf { it.isNotBlank() }).distinct()
        val nowMs = System.currentTimeMillis()
        keys.forEach { inFlightPostShareMutations[it] = nowMs }

        homePostSharesCount.value = homePostSharesCount.value + keys.associateWith { nextShares }
        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == photo.id || p.id == cleanId || (photo.imageUrl.isNotBlank() && p.imageUrl == photo.imageUrl)) {
                p.copy(sharesCount = nextShares)
            } else p
        }
        selectedHomePost.value = selectedHomePost.value?.let { opened ->
            if (opened.id == photo.id || opened.id == cleanId) opened.copy(sharesCount = nextShares) else opened
        }
        savePersonalPhotosToPrefs()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                val refs = resolvePostDocRefs(photo, cleanId)
                for (ref in refs) {
                    for (attempt in 1..3) {
                        try {
                            withTimeoutOrNull(10_000L) {
                                awaitTask(ref.set(mapOf("sharesCount" to FieldValue.increment(1)), SetOptions.merge()))
                            }
                            break
                        } catch (_: Exception) {
                            if (attempt < 3) delay(attempt * 500L)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Post share sync notice: ${e.message}")
            } finally {
                keys.forEach { inFlightPostShareMutations.remove(it) }
            }
        }
    }

    fun toggleBookmarkHomePost(postId: String): Boolean {
        val current = homePostBookmarked.value[postId] ?: false
        val new = !current
        homePostBookmarked.value = homePostBookmarked.value + (postId to new)
        return new
    }

    fun deletePhotoPost(postId: String, imageUrl: String, creatorUsername: String, creatorUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val trimmedPostId = postId.trim()
            val cleanId = trimmedPostId.removePrefix("photo_")
            val targetPhoto = personalPhotosState.value.find {
                (trimmedPostId.isNotEmpty() && (it.id == trimmedPostId || it.id == cleanId || it.firestoreDocId == trimmedPostId)) ||
                    (imageUrl.isNotBlank() && it.imageUrl == imageUrl)
            }
            val effectiveUrl = imageUrl.ifBlank { targetPhoto?.imageUrl.orEmpty() }
            val effectiveOwner = creatorUsername.ifBlank { targetPhoto?.creatorUsername.orEmpty() }
            val safeOwner = effectiveOwner.lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }.take(32)
            val targetKey = extractSupabaseFilename(effectiveUrl)
            val targetFullUrl = com.example.data.CdnConfig.getFullMediaUrl(effectiveUrl)

            // 1. Immediately remove from local state & mark deleted so UI & Insights update instantly
            if (effectiveUrl.isNotEmpty()) {
                markUrlDeleted(effectiveUrl)
                if (effectiveUrl.startsWith("/") || effectiveUrl.startsWith("file:", ignoreCase = true)) {
                    try { File(effectiveUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
                }
            }
            personalPhotosState.value = personalPhotosState.value.filter { photo ->
                val photoKey = extractSupabaseFilename(photo.imageUrl)
                val matchesPostId = trimmedPostId.isNotEmpty() && (
                    photo.id == trimmedPostId ||
                        photo.id == cleanId ||
                        photo.firestoreDocId == trimmedPostId
                    )
                val matchesUrl = effectiveUrl.isNotEmpty() && (photo.imageUrl == effectiveUrl || photo.imageUrl == targetFullUrl)
                val matchesKey = targetKey != null && photoKey == targetKey
                !matchesPostId && !matchesUrl && !matchesKey
            }
            selectedHomePost.value?.let { opened ->
                val openedKey = extractSupabaseFilename(opened.imageUrl)
                val openedMatches = (trimmedPostId.isNotEmpty() && (opened.id == trimmedPostId || opened.id == cleanId || opened.firestoreDocId == trimmedPostId)) ||
                    (effectiveUrl.isNotEmpty() && (opened.imageUrl == effectiveUrl || opened.imageUrl == targetFullUrl)) ||
                    (targetKey != null && openedKey == targetKey)
                if (openedMatches) selectedHomePost.value = null
            }
            savePersonalPhotosToPrefs()
            recalculateCreatorStats()

            // 2. Discover & delete from Firestore "posts" collection + collect all remote media URLs
            val extraMediaUrls = mutableListOf<String>()
            if (effectiveUrl.isNotBlank()) extraMediaUrls.add(effectiveUrl)
            var firestorePermanentError: String? = null

            withContext(Dispatchers.IO) {
                try {
                    val refsToDelete = linkedMapOf<String, DocumentReference>()
                    val explicitDocIds = listOfNotNull(
                        targetPhoto?.firestoreDocId?.takeIf { it.isNotBlank() },
                        trimmedPostId.takeIf { it.isNotBlank() },
                        cleanId.takeIf { it.isNotBlank() },
                        cleanId.toLongOrNull()?.let { "post_${safeOwner}_$it" },
                        targetPhoto?.id?.toLongOrNull()?.let { "post_${safeOwner}_$it" }
                    ).distinct()

                    for (docId in explicitDocIds) {
                        val ref = db.collection("posts").document(docId)
                        if (docRefExists(ref)) {
                            refsToDelete[ref.path] = ref
                        }
                    }

                    val idCandidates = listOfNotNull(
                        trimmedPostId.takeIf { it.isNotBlank() },
                        cleanId.takeIf { it.isNotBlank() },
                        targetPhoto?.id?.takeIf { it.isNotBlank() }
                    ).distinct().take(10)
                    if (idCandidates.isNotEmpty()) {
                        try {
                            val byId = awaitTask(db.collection("posts").whereIn("id", idCandidates).get())
                            byId.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                extraMediaUrls.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                        try {
                            val byFirestoreId = awaitTask(db.collection("posts").whereIn("firestoreDocId", idCandidates).get())
                            byFirestoreId.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                extraMediaUrls.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                    }

                    for (urlVariant in buildMediaUrlVariants(effectiveUrl)) {
                        try {
                            val byImageUrl = awaitTask(db.collection("posts").whereEqualTo("imageUrl", urlVariant).get())
                            byImageUrl.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                extraMediaUrls.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                        try {
                            val byMediaUrl = awaitTask(db.collection("posts").whereEqualTo("mediaUrl", urlVariant).get())
                            byMediaUrl.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                extraMediaUrls.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                    }

                    val uidCandidates = listOfNotNull(
                        auth.currentUser?.uid?.takeIf { it.isNotBlank() },
                        creatorUid.takeIf { it.isNotBlank() },
                        resolveUploadUserId(safeOwner).takeIf { it.isNotBlank() }
                    ).distinct()
                    val ownerDocs = mutableListOf<DocumentSnapshot>()
                    for (uid in uidCandidates) {
                        try {
                            ownerDocs.addAll(awaitTask(db.collection("posts").whereEqualTo("userId", uid).get()).documents)
                        } catch (_: Exception) {}
                        try {
                            ownerDocs.addAll(awaitTask(db.collection("posts").whereEqualTo("creatorUid", uid).get()).documents)
                        } catch (_: Exception) {}
                    }
                    val usernameVariants = listOf(effectiveOwner.trim(), safeOwner).filter { it.isNotBlank() }.distinct()
                    for (uname in usernameVariants) {
                        try {
                            ownerDocs.addAll(awaitTask(db.collection("posts").whereEqualTo("username", uname).get()).documents)
                        } catch (_: Exception) {}
                    }

                    ownerDocs.distinctBy { it.reference.path }.forEach { doc ->
                        val docUrl = doc.getString("imageUrl") ?: doc.getString("mediaUrl") ?: ""
                        val docFullUrl = com.example.data.CdnConfig.getFullMediaUrl(docUrl)
                        val docKey = extractSupabaseFilename(docUrl)
                        if ((targetKey != null && docKey == targetKey) ||
                            (effectiveUrl.isNotBlank() && (docUrl == effectiveUrl || docFullUrl == targetFullUrl)) ||
                            (cleanId.isNotBlank() && doc.getString("id") == cleanId) ||
                            (!targetPhoto?.firestoreDocId.isNullOrBlank() && doc.id == targetPhoto?.firestoreDocId)
                        ) {
                            refsToDelete[doc.reference.path] = doc.reference
                            extraMediaUrls.addAll(extractRemoteUrlsFromDoc(doc))
                        }
                    }

                    for (ref in refsToDelete.values) {
                        val outcome = deleteFirestoreDocRefDetailed(ref)
                        if (!outcome.success && outcome.permanentError != null && firestorePermanentError == null) {
                            firestorePermanentError = outcome.permanentError
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("YouSnapViewModel", "Firestore photo delete notice: ${e.message}")
                }
            }

            // 3. Delete all associated media files from Supabase Storage (queues transient errors for retry)
            extraMediaUrls.forEach { markUrlDeleted(it) }
            val supabaseResults = deleteSupabaseMediaUrls(extraMediaUrls)
            val supabasePermanentErr = supabaseResults.firstOrNull { !it.isSuccess && it.responseCode in listOf(401, 403, 405) }?.errorMessage

            if (firestorePermanentError != null || supabasePermanentErr != null) {
                val msg = firestorePermanentError?.let { "Cloud delete blocked: $it" }
                    ?: supabasePermanentErr
                    ?: "Could not delete media from cloud"
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }

            refreshHomeFeed(showIndicator = false)
            onSuccess()
        }
    }

    fun deleteReelVideo(reel: ReelEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val targetKey = extractSupabaseFilename(reel.mediaUrl)
            val safeOwner = reel.creatorUsername.lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }.take(32)
            val targetFullUrl = com.example.data.CdnConfig.getFullMediaUrl(reel.mediaUrl)
            val knownDocIds = listOfNotNull(
                reelFirestoreDocIdByMediaUrl[reel.mediaUrl],
                reelFirestoreDocIdByTimestamp[reel.timestamp],
                reelFirestoreDocIdById[reel.id]
            ).distinct().filter { it.isNotBlank() }

            // 1. Remove immediately from local state, Room cache, & local files
            if (reel.mediaUrl.isNotEmpty()) {
                markUrlDeleted(reel.mediaUrl)
                if (reel.mediaUrl.startsWith("/") || reel.mediaUrl.startsWith("file:", ignoreCase = true)) {
                    try { File(reel.mediaUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
                }
            }
            if (reel.thumbnailUrl.isNotBlank()) {
                if (reel.thumbnailUrl.startsWith("/") || reel.thumbnailUrl.startsWith("file:", ignoreCase = true)) {
                    try { File(reel.thumbnailUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
                } else {
                    markUrlDeleted(reel.thumbnailUrl)
                }
            }
            val filterOutDeleted: (ReelEntity) -> Boolean = { item ->
                val itemKey = extractSupabaseFilename(item.mediaUrl)
                item.id != reel.id &&
                    (reel.mediaUrl.isEmpty() || (item.mediaUrl != reel.mediaUrl && item.mediaUrl != targetFullUrl)) &&
                    (targetKey == null || itemKey != targetKey)
            }
            _reelsState.value = _reelsState.value.filter(filterOutDeleted)
            cachedRemoteReels = cachedRemoteReels.filter(filterOutDeleted)
            cachedRemoteVideos = cachedRemoteVideos.filter(filterOutDeleted)
            watchHistory.value = watchHistory.value.filter(filterOutDeleted)
            bookmarkedReelIds.value = bookmarkedReelIds.value - reel.id
            if (selectedReelForPreview.value?.id == reel.id) {
                selectedReelForPreview.value = null
            }
            saveReelsToPrefs()
            recalculateCreatorStats()

            // 2. Discover & delete from BOTH Firestore "reels" and "videos" collections + Room DB
            val mediaUrlsToDelete = mutableListOf<String>()
            if (reel.mediaUrl.isNotBlank()) mediaUrlsToDelete.add(reel.mediaUrl)
            if (reel.thumbnailUrl.isNotBlank()) mediaUrlsToDelete.add(reel.thumbnailUrl)
            var firestorePermanentError: String? = null

            withContext(Dispatchers.IO) {
                try {
                    YouSnapDatabase.getDatabase(app).youSnapDao().deleteReelById(reel.id)
                } catch (_: Exception) {}

                try {
                    val refsToDelete = linkedMapOf<String, DocumentReference>()
                    val explicitDocIds = (knownDocIds + listOfNotNull(
                        reel.timestamp.takeIf { it > 0L }?.let { "reel_${safeOwner}_$it" },
                        reel.timestamp.takeIf { it > 0L }?.let { "video_${safeOwner}_$it" },
                        reel.id.toString().takeIf { it != "0" },
                        reel.timestamp.takeIf { it > 0L }?.toString()
                    )).distinct().filter { it.isNotBlank() }

                    val urlVariants = buildMediaUrlVariants(reel.mediaUrl)
                    val uidCandidates = listOfNotNull(
                        auth.currentUser?.uid?.takeIf { it.isNotBlank() },
                        reel.creatorUid.takeIf { it.isNotBlank() },
                        resolveUploadUserId(safeOwner).takeIf { it.isNotBlank() }
                    ).distinct()
                    val usernameVariants = listOf(reel.creatorUsername.trim(), safeOwner).filter { it.isNotBlank() }.distinct()

                    for (collectionName in listOf("reels", "videos")) {
                        val colRef = db.collection(collectionName)

                        for (docId in explicitDocIds) {
                            val ref = colRef.document(docId)
                            if (docRefExists(ref)) {
                                refsToDelete[ref.path] = ref
                            }
                        }

                        try {
                            val byIntId = awaitTask(colRef.whereEqualTo("reelIntId", reel.id).get())
                            byIntId.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}

                        val strIds = listOfNotNull(
                            reel.id.toString().takeIf { it != "0" },
                            reel.timestamp.takeIf { it > 0L }?.toString()
                        ).distinct()
                        for (sid in strIds) {
                            try {
                                val byStrId = awaitTask(colRef.whereEqualTo("id", sid).get())
                                byStrId.documents.forEach { doc ->
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            } catch (_: Exception) {}
                        }

                        for (urlVariant in urlVariants) {
                            try {
                                val byMediaUrl = awaitTask(colRef.whereEqualTo("mediaUrl", urlVariant).get())
                                byMediaUrl.documents.forEach { doc ->
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            } catch (_: Exception) {}
                            try {
                                val byVideoUrl = awaitTask(colRef.whereEqualTo("videoUrl", urlVariant).get())
                                byVideoUrl.documents.forEach { doc ->
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            } catch (_: Exception) {}
                        }

                        if (targetKey != null || reel.timestamp > 0L) {
                            val ownerDocs = mutableListOf<DocumentSnapshot>()
                            for (uid in uidCandidates) {
                                try {
                                    ownerDocs.addAll(awaitTask(colRef.whereEqualTo("userId", uid).get()).documents)
                                } catch (_: Exception) {}
                                try {
                                    ownerDocs.addAll(awaitTask(colRef.whereEqualTo("creatorUid", uid).get()).documents)
                                } catch (_: Exception) {}
                            }
                            for (uname in usernameVariants) {
                                try {
                                    ownerDocs.addAll(awaitTask(colRef.whereEqualTo("username", uname).get()).documents)
                                } catch (_: Exception) {}
                                try {
                                    ownerDocs.addAll(awaitTask(colRef.whereEqualTo("ownerUsername", uname).get()).documents)
                                } catch (_: Exception) {}
                            }

                            ownerDocs.distinctBy { it.reference.path }.forEach { doc ->
                                val docUrl = doc.getString("mediaUrl") ?: doc.getString("videoUrl") ?: ""
                                val docFullUrl = com.example.data.CdnConfig.getFullMediaUrl(docUrl)
                                val docTs = doc.getLong("timestamp") ?: 0L
                                val docIntId = doc.getLong("reelIntId")?.toInt()
                                if ((targetKey != null && extractSupabaseFilename(docUrl) == targetKey) ||
                                    (reel.mediaUrl.isNotBlank() && (docUrl == reel.mediaUrl || docFullUrl == targetFullUrl)) ||
                                    (reel.timestamp > 0L && docTs == reel.timestamp) ||
                                    (docIntId != null && docIntId == reel.id)
                                ) {
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            }
                        }
                    }

                    for (ref in refsToDelete.values) {
                        val outcome = deleteFirestoreDocRefDetailed(ref)
                        if (!outcome.success && outcome.permanentError != null && firestorePermanentError == null) {
                            firestorePermanentError = outcome.permanentError
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("YouSnapViewModel", "Firestore reel delete notice: ${e.message}")
                }
            }

            // 3. Delete video + remote thumbnail from Supabase Storage (queues transient errors for retry)
            mediaUrlsToDelete.forEach { markUrlDeleted(it) }
            val supabaseResults = deleteSupabaseMediaUrls(mediaUrlsToDelete)
            val supabasePermanentErr = supabaseResults.firstOrNull { !it.isSuccess && it.responseCode in listOf(401, 403, 405) }?.errorMessage

            if (firestorePermanentError != null || supabasePermanentErr != null) {
                val msg = firestorePermanentError?.let { "Cloud delete blocked: $it" }
                    ?: supabasePermanentErr
                    ?: "Could not delete video from cloud"
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }

            refreshHomeFeed(showIndicator = false)
            onComplete()
        }
    }

    fun addCommentToHomePost(postId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val user = profileState.value
        val myUid = activeUserUid().orEmpty().ifBlank { user.username.ifBlank { "user" } }
        val cleanId = postId.removePrefix("photo_")
        val photoKey = "photo_$cleanId"
        val targetPhoto = personalPhotosState.value.find {
            it.id == cleanId || it.id == postId || it.firestoreDocId == postId
        } ?: selectedHomePost.value?.takeIf {
            it.id == cleanId || it.id == postId || it.firestoreDocId == postId
        }
        val resolvedDocId = targetPhoto?.firestoreDocId?.takeIf { it.isNotBlank() }
        val nowMs = System.currentTimeMillis()
        val comment = CommentEntity(
            id = nowMs.toInt(),
            postId = photoKey,
            username = user.username.ifBlank { "user" },
            text = cleanText,
            timestamp = nowMs
        )

        val keys = listOfNotNull(postId, cleanId, photoKey, resolvedDocId).distinct()
        keys.forEach { inFlightPostCommentMutations[it] = nowMs }

        val currentMap = homePostComments.value.toMutableMap()
        val mergedList = (currentMap[photoKey].orEmpty() + currentMap[cleanId].orEmpty() + comment)
            .distinctBy { "${it.username}|${it.text}|${it.timestamp}" }
            .sortedBy { it.timestamp }
        keys.forEach { key -> currentMap[key] = mergedList }
        homePostComments.value = currentMap
        saveHomeCommentsToPrefs()

        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == cleanId || p.id == postId || (resolvedDocId != null && p.firestoreDocId == resolvedDocId)) {
                p.copy(initialComments = maxOf(p.initialComments + 1, mergedList.size))
            } else p
        }
        selectedHomePost.value = selectedHomePost.value?.let { opened ->
            if (opened.id == cleanId || opened.id == postId) {
                opened.copy(initialComments = maxOf(opened.initialComments + 1, mergedList.size))
            } else opened
        }
        savePersonalPhotosToPrefs()

        val commentDoc = mapOf(
            "postId" to photoKey,
            "cleanPostId" to cleanId,
            "firestoreDocId" to (resolvedDocId ?: ""),
            "username" to user.username.ifBlank { "user" },
            "userName" to user.name.ifBlank { user.username.ifBlank { "User" } },
            "userUid" to myUid,
            "text" to cleanText,
            "timestamp" to nowMs,
            "createdAt" to FieldValue.serverTimestamp()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                for (attempt in 1..3) {
                    try {
                        withTimeoutOrNull(10_000L) {
                            awaitTask(db.collection("comments").add(commentDoc))
                        }
                        break
                    } catch (_: Exception) {
                        if (attempt < 3) delay(attempt * 500L)
                    }
                }
                val refs = resolvePostDocRefs(targetPhoto, cleanId)
                for (ref in refs) {
                    for (attempt in 1..3) {
                        try {
                            withTimeoutOrNull(10_000L) {
                                awaitTask(ref.set(mapOf("commentsCount" to FieldValue.increment(1)), SetOptions.merge()))
                            }
                            break
                        } catch (_: Exception) {
                            if (attempt < 3) delay(attempt * 500L)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Home post comment sync notice: ${e.message}")
            } finally {
                keys.forEach { inFlightPostCommentMutations.remove(it) }
            }
        }

        if (targetPhoto != null) {
            val preview = cleanText.take(80)
            createAndPushFirestoreNotification(
                type = "comment",
                recipientUsername = targetPhoto.creatorUsername,
                recipientUid = targetPhoto.creatorUid,
                detail = "commented: \"$preview\"",
                postId = targetPhoto.firestoreDocId ?: targetPhoto.id,
                postMediaUrl = targetPhoto.imageUrl
            )
        }
    }

    fun uploadTextStory(text: String, gradientStart: String, gradientEnd: String, onComplete: () -> Unit) {
        val profile = profileState.value
        val ownerUsername = profile.username.ifBlank { "user" }
        val displayName = profile.name.ifBlank { ownerUsername }
        val creatorUid = resolveUploadUserId(ownerUsername)
        val timestamp = System.currentTimeMillis()
        val newStory = StoryModel(
            storyId = timestamp.toString(),
            ownerUsername = ownerUsername,
            mediaUrl = "",
            mediaType = "text",
            caption = text,
            bgGradientStart = gradientStart,
            bgGradientEnd = gradientEnd,
            timestamp = timestamp,
            firestoreDocId = ownerUsername
        )
        allStories.value = listOf(newStory) + allStories.value.filter { !it.ownerUsername.equals(ownerUsername, ignoreCase = true) }
        saveStoriesToPrefs()
        recalculateCreatorStats()

        val remoteAvatar = resolveCurrentUserRemotePhotoUrl()
        val baseStoryDoc = mapOf(
            "storyId" to timestamp.toString(),
            "firestoreDocId" to ownerUsername,
            "userId" to creatorUid,
            "creatorUid" to creatorUid,
            "username" to ownerUsername,
            "ownerUsername" to ownerUsername,
            "name" to displayName,
            "creatorAvatar" to remoteAvatar,
            "photoURL" to remoteAvatar,
            "mediaUrl" to "",
            "mediaType" to "text",
            "caption" to text,
            "timestamp" to timestamp,
            "bgGradientStart" to gradientStart,
            "bgGradientEnd" to gradientEnd,
            "viewerIds" to emptyList<String>(),
            "viewsCount" to 0,
            "likes" to emptyList<String>()
        )
        viewModelScope.launch {
            val inFlightKey = "stories/$ownerUsername"
            inFlightUploadDocKeys[inFlightKey] = System.currentTimeMillis()
            val synced = try {
                syncDocumentToFirestoreWithRetry(
                    collection = "stories",
                    docId = ownerUsername,
                    data = baseStoryDoc + ("createdAt" to FieldValue.serverTimestamp())
                )
            } finally {
                inFlightUploadDocKeys.remove(inFlightKey)
            }
            if (!synced) {
                savePendingFirestoreDoc("stories", ownerUsername, baseStoryDoc)
            }
            if (synced) {
                refreshHomeFeed(showIndicator = false)
            }
            onComplete()
        }
    }

    private fun resolveUserUid(username: String, knownUid: String = ""): String {
        if (knownUid.isNotBlank()) return knownUid.trim()
        val key = username.trim().lowercase()
        if (key.isBlank()) return ""
        userUidByUsername[key]?.takeIf { it.isNotBlank() }?.let { return it }
        personalPhotosState.value
            .firstOrNull { it.creatorUsername.equals(username, ignoreCase = true) && it.creatorUid.isNotBlank() }
            ?.creatorUid?.let { return it }
        _reelsState.value
            .firstOrNull { it.creatorUsername.equals(username, ignoreCase = true) && it.creatorUid.isNotBlank() }
            ?.creatorUid?.let { return it }
        storyCreatorUidByOwner[key]?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun formatReportReason(reason: String, details: String): String {
        val cleanReason = reason.trim()
        val cleanDetails = details.trim()
        return when {
            cleanReason.equals("Other", ignoreCase = true) && cleanDetails.isNotBlank() -> cleanDetails
            cleanDetails.isNotBlank() && cleanReason.isNotBlank() -> "$cleanReason: $cleanDetails"
            cleanDetails.isNotBlank() -> cleanDetails
            else -> cleanReason.ifBlank { "Inappropriate Content" }
        }
    }

    private fun submitViolationReport(
        reportedCollection: String,
        reportedMediaId: String = "",
        reportedUsername: String = "",
        knownReportedUserId: String = "",
        reason: String,
        details: String = ""
    ) {
        val reporterUid = (auth.currentUser?.uid ?: activeUserUid()).orEmpty().trim()
        val finalReason = formatReportReason(reason, details)

        viewModelScope.launch(Dispatchers.IO) {
            var resolvedReportedUid = resolveUserUid(reportedUsername, knownReportedUserId)
            if (resolvedReportedUid.isBlank() && reportedUsername.isNotBlank()) {
                try {
                    val snap = awaitTask(
                        db.collection("users")
                            .whereEqualTo("username", reportedUsername.trim())
                            .limit(1)
                            .get()
                    )
                    resolvedReportedUid = snap.documents.firstOrNull()?.id.orEmpty()
                } catch (_: Exception) {}
            }
            if (resolvedReportedUid.isBlank() && reportedMediaId.isNotBlank() &&
                reportedCollection in listOf("posts", "reels", "stories", "videos")
            ) {
                try {
                    val mediaDoc = awaitTask(db.collection(reportedCollection).document(reportedMediaId).get())
                    resolvedReportedUid = mediaDoc.getString("userId")
                        ?: mediaDoc.getString("creatorUid")
                        ?: ""
                } catch (_: Exception) {}
            }

            val ticketData = hashMapOf<String, Any>(
                "category" to "Reports & Violations",
                "reporterUserId" to reporterUid,
                "reportedUserId" to resolvedReportedUid,
                "reportedMediaId" to reportedMediaId,
                "reportedCollection" to reportedCollection,
                "reason" to finalReason,
                "status" to "Pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (reportedUsername.isNotBlank()) {
                ticketData["reportedUsername"] = reportedUsername.trim()
            }
            if (details.trim().isNotBlank()) {
                ticketData["details"] = details.trim()
            }

            try {
                awaitTask(db.collection("support_tickets").add(ticketData))
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        app,
                        "Report submitted. Thank you!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Error submitting violation report: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        app,
                        "Could not submit report. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun reportAccount(
        username: String,
        reason: String,
        details: String,
        knownUid: String = ""
    ) {
        submitViolationReport(
            reportedCollection = "users",
            reportedMediaId = "",
            reportedUsername = username,
            knownReportedUserId = knownUid,
            reason = reason,
            details = details
        )
    }

    fun reportPost(
        postId: String,
        username: String,
        reason: String,
        details: String,
        creatorUid: String = "",
        firestoreDocId: String = ""
    ) {
        if (postId.startsWith("profile_")) {
            reportAccount(
                username = username.ifBlank { postId.removePrefix("profile_") },
                reason = reason,
                details = details,
                knownUid = creatorUid
            )
            return
        }
        val cleanId = postId.removePrefix("photo_")
        val matchedPhoto = personalPhotosState.value.firstOrNull {
            (firestoreDocId.isNotBlank() && it.firestoreDocId == firestoreDocId) ||
                it.firestoreDocId == postId ||
                it.firestoreDocId == cleanId ||
                it.id == postId ||
                it.id == cleanId
        }
        val resolvedDocId = firestoreDocId.takeIf { it.isNotBlank() }
            ?: matchedPhoto?.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: cleanId
        val resolvedUid = creatorUid.ifBlank { matchedPhoto?.creatorUid.orEmpty() }
        val resolvedUsername = username.ifBlank { matchedPhoto?.creatorUsername.orEmpty() }

        submitViolationReport(
            reportedCollection = "posts",
            reportedMediaId = resolvedDocId,
            reportedUsername = resolvedUsername,
            knownReportedUserId = resolvedUid,
            reason = reason,
            details = details
        )
    }

    private suspend fun resolveReelDocRefs(reel: ReelEntity): List<com.google.firebase.firestore.DocumentReference> {
        val refs = LinkedHashMap<String, com.google.firebase.firestore.DocumentReference>()
        val mappedCol = reelCollectionById[reel.id]
            ?: reel.mediaUrl.takeIf { it.isNotBlank() }?.let { reelCollectionByMediaUrl[it] }
        val mappedDocId = reelFirestoreDocIdById[reel.id]
            ?: reel.mediaUrl.takeIf { it.isNotBlank() }?.let { reelFirestoreDocIdByMediaUrl[it] }
            ?: reel.timestamp.takeIf { it > 0L }?.let { reelFirestoreDocIdByTimestamp[it] }

        if (!mappedCol.isNullOrBlank() && !mappedDocId.isNullOrBlank()) {
            val directRef = db.collection(mappedCol).document(mappedDocId)
            val snap = try { withTimeoutOrNull(5000L) { awaitTask(directRef.get()) } } catch (_: Exception) { null }
            if (snap != null && snap.exists()) {
                refs[directRef.path] = directRef
            }
        }

        if (refs.isEmpty()) {
            val collections = listOfNotNull(mappedCol, "reels", "videos").distinct()
            for (col in collections) {
                if (reel.mediaUrl.isNotBlank()) {
                    val snap = try {
                        withTimeoutOrNull(5000L) {
                            awaitTask(db.collection(col).whereEqualTo("mediaUrl", reel.mediaUrl).limit(3).get())
                        }
                    } catch (_: Exception) { null }
                    snap?.documents?.forEach { refs[it.reference.path] = it.reference }
                }
                if (refs.isEmpty()) {
                    val snap = try {
                        withTimeoutOrNull(5000L) {
                            awaitTask(db.collection(col).whereEqualTo("reelIntId", reel.id).limit(3).get())
                        }
                    } catch (_: Exception) { null }
                    snap?.documents?.forEach { refs[it.reference.path] = it.reference }
                }
                if (refs.isEmpty()) {
                    val snap = try {
                        withTimeoutOrNull(5000L) {
                            awaitTask(db.collection(col).whereEqualTo("id", reel.id.toString()).limit(3).get())
                        }
                    } catch (_: Exception) { null }
                    snap?.documents?.forEach { refs[it.reference.path] = it.reference }
                }
                if (refs.isNotEmpty()) break
            }
        }
        return refs.values.toList()
    }

    fun toggleLikeReel(reel: ReelEntity) {
        val currentReel = _reelsState.value.firstOrNull { it.id == reel.id } ?: reel
        val newLiked = !currentReel.isLiked
        val newCount = if (newLiked) currentReel.likesCount + 1 else (currentReel.likesCount - 1).coerceAtLeast(0)
        inFlightReelLikeMutations[currentReel.id] = System.currentTimeMillis()

        _reelsState.value = _reelsState.value.map {
            if (it.id == currentReel.id) it.copy(isLiked = newLiked, likesCount = newCount) else it
        }
        selectedReelForPreview.value = selectedReelForPreview.value?.let {
            if (it.id == currentReel.id) it.copy(isLiked = newLiked, likesCount = newCount) else it
        }
        saveReelsToPrefs()

        val uid = activeUserUid().orEmpty().ifBlank { _profileState.value.username.ifBlank { "user" } }
        val myUsername = _profileState.value.username.trim()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                val refs = resolveReelDocRefs(currentReel)
                for (ref in refs) {
                    val snap = try { withTimeoutOrNull(5000L) { awaitTask(ref.get()) } } catch (_: Exception) { null }
                    val existingLikedBy = (snap?.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
                        ?: mutableListOf()
                    val alreadyContainsMe = existingLikedBy.contains(uid) ||
                        (myUsername.isNotBlank() && existingLikedBy.any { it.equals(myUsername, ignoreCase = true) })
                    val currentLikes = (snap?.getLong("likesCount") ?: 0L).toInt().coerceAtLeast(0)
                    val nextLikes = if (newLiked) {
                        if (alreadyContainsMe) maxOf(currentLikes, newCount) else currentLikes + 1
                    } else {
                        if (alreadyContainsMe) (currentLikes - 1).coerceAtLeast(0) else newCount.coerceAtLeast(0)
                    }

                    if (newLiked) {
                        if (!existingLikedBy.contains(uid)) existingLikedBy.add(uid)
                    } else {
                        existingLikedBy.removeAll { it == uid || (myUsername.isNotBlank() && it.equals(myUsername, ignoreCase = true)) }
                    }

                    val payload = mapOf(
                        "likesCount" to nextLikes,
                        "likedBy" to existingLikedBy.distinct()
                    )
                    for (attempt in 1..3) {
                        try {
                            withTimeoutOrNull(10_000L) {
                                awaitTask(ref.set(payload, SetOptions.merge()))
                            }
                            break
                        } catch (_: Exception) {
                            if (attempt < 3) delay(attempt * 500L)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Reel like sync notice: ${e.message}")
            } finally {
                inFlightReelLikeMutations.remove(currentReel.id)
            }
        }

        if (newLiked) {
            val safeSender = _profileState.value.username.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "user" }
            createAndPushFirestoreNotification(
                type = "like",
                recipientUsername = currentReel.creatorUsername,
                recipientUid = currentReel.creatorUid,
                detail = "liked your reel.",
                postId = currentReel.id.toString(),
                postMediaUrl = currentReel.mediaUrl,
                fixedDocId = "notif_like_reel_${safeSender}_${currentReel.id}"
            )
        }
    }

    fun toggleFollowCreator(reel: ReelEntity) {
        toggleFollowUser(targetUsername = reel.creatorUsername, targetUid = reel.creatorUid)
    }

    fun toggleFollowUser(targetUsername: String, targetUid: String = "") {
        val cleanTarget = targetUsername.trim()
        if (cleanTarget.isBlank()) return
        val myProfile = _profileState.value
        val myUsername = myProfile.username.ifBlank { "user" }
        val myUid = activeUserUid().orEmpty().ifBlank { myUsername }
        if (cleanTarget.equals(myUsername, ignoreCase = true) || (targetUid.isNotBlank() && targetUid == myUid)) {
            return
        }

        val currentFollowing = otherUserIsFollowing.value.entries
            .firstOrNull { it.key.equals(cleanTarget, ignoreCase = true) }?.value ?: false
        val newFollowing = !currentFollowing

        val updatedMap = otherUserIsFollowing.value.toMutableMap()
        updatedMap[cleanTarget] = newFollowing
        otherUserIsFollowing.value = updatedMap

        _reelsState.value = _reelsState.value.map { reel ->
            if (reel.creatorUsername.equals(cleanTarget, ignoreCase = true)) {
                reel.copy(isFollowingCreator = newFollowing)
            } else reel
        }
        saveReelsToPrefs()

        notifications.value = notifications.value.map { notif ->
            if (notif.type.equals("follow", ignoreCase = true) && notif.username.equals(cleanTarget, ignoreCase = true)) {
                notif.copy(isFollowingBack = newFollowing)
            } else notif
        }

        val nextFollowingCount = (myFollowingCount.value + if (newFollowing) 1 else -1).coerceAtLeast(0)
        myFollowingCount.value = nextFollowingCount
        val updatedMyProfile = myProfile.copy(followingCount = nextFollowingCount)
        _profileState.value = updatedMyProfile
        saveProfileToPrefs(updatedMyProfile, myUid)
        allUsers.value = allUsers.value.map { user ->
            if (user.username.equals(cleanTarget, ignoreCase = true)) {
                user.copy(followersCount = (user.followersCount + if (newFollowing) 1 else -1).coerceAtLeast(0))
            } else user
        }

        val safeMyKey = myUid.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val safeTargetKey = cleanTarget.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val followDocId = "follow_${safeMyKey}_${safeTargetKey}"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedTargetUid = targetUid
                if (resolvedTargetUid.isBlank()) {
                    val userSnap = withTimeoutOrNull(4000L) {
                        awaitTask(db.collection("users").whereEqualTo("username", cleanTarget).limit(1).get())
                    }
                    resolvedTargetUid = userSnap?.documents?.firstOrNull()?.id.orEmpty()
                }

                if (newFollowing) {
                    val followDoc = mapOf(
                        "id" to followDocId,
                        "followerUid" to myUid,
                        "followerUsername" to myUsername,
                        "targetUid" to resolvedTargetUid,
                        "targetUsername" to cleanTarget,
                        "timestamp" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    awaitTask(db.collection("follows").document(followDocId).set(followDoc))
                } else {
                    awaitTask(db.collection("follows").document(followDocId).delete())
                }

                if (myUid.isNotBlank()) {
                    db.collection("users").document(myUid)
                        .set(mapOf("followingCount" to nextFollowingCount), SetOptions.merge())
                }
                if (resolvedTargetUid.isNotBlank()) {
                    db.collection("users").document(resolvedTargetUid)
                        .update("followersCount", FieldValue.increment(if (newFollowing) 1L else -1L))
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Follow sync notice: ${e.message}")
            }
        }

        if (newFollowing) {
            createAndPushFirestoreNotification(
                type = "follow",
                recipientUsername = cleanTarget,
                recipientUid = targetUid,
                detail = "started following you.",
                fixedDocId = "notif_follow_${safeMyKey}_${safeTargetKey}"
            )
        }
    }

    fun incrementReelShares(reel: ReelEntity) {
        val currentReel = _reelsState.value.firstOrNull { it.id == reel.id } ?: reel
        val nextShares = currentReel.sharesCount + 1
        inFlightReelShareMutations[currentReel.id] = System.currentTimeMillis()

        _reelsState.value = _reelsState.value.map {
            if (it.id == currentReel.id) it.copy(sharesCount = nextShares) else it
        }
        selectedReelForPreview.value = selectedReelForPreview.value?.let {
            if (it.id == currentReel.id) it.copy(sharesCount = nextShares) else it
        }
        saveReelsToPrefs()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                val refs = resolveReelDocRefs(currentReel)
                for (ref in refs) {
                    for (attempt in 1..3) {
                        try {
                            withTimeoutOrNull(10_000L) {
                                awaitTask(ref.set(mapOf("sharesCount" to FieldValue.increment(1)), SetOptions.merge()))
                            }
                            break
                        } catch (_: Exception) {
                            if (attempt < 3) delay(attempt * 500L)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Reel share sync notice: ${e.message}")
            } finally {
                inFlightReelShareMutations.remove(currentReel.id)
            }
        }
    }

    fun addCommentToActiveReel() {
        val text = currentCommentText.value.trim()
        if (text.isEmpty()) return
        val user = profileState.value
        val reelId = activeCommentsReelId ?: selectedReelForPreview.value?.id ?: return
        val activeReel = _reelsState.value.find { it.id == reelId } ?: selectedReelForPreview.value
        val nowMs = System.currentTimeMillis()
        val newComment = CommentEntity(
            id = nowMs.toInt(),
            reelId = reelId,
            username = user.username.ifBlank { "user" },
            text = text,
            timestamp = nowMs
        )
        inFlightReelCommentMutations[reelId] = nowMs

        val updatedComments = (reelCommentsByReelId.value[reelId].orEmpty() + newComment)
            .distinctBy { "${it.username}|${it.text}|${it.timestamp}" }
            .sortedBy { it.timestamp }
        reelCommentsByReelId.value = reelCommentsByReelId.value + (reelId to updatedComments)
        activeReelComments.value = updatedComments
        currentCommentText.value = ""
        saveReelCommentsToPrefs()

        _reelsState.value = _reelsState.value.map {
            if (it.id == reelId) it.copy(commentsCount = maxOf(it.commentsCount + 1, updatedComments.size)) else it
        }
        selectedReelForPreview.value = selectedReelForPreview.value?.let {
            if (it.id == reelId) it.copy(commentsCount = maxOf(it.commentsCount + 1, updatedComments.size)) else it
        }
        saveReelsToPrefs()

        val resolvedDocId = reelFirestoreDocIdById[reelId]
            ?: activeReel?.mediaUrl?.takeIf { it.isNotBlank() }?.let { reelFirestoreDocIdByMediaUrl[it] }
            ?: ""
        val myUid = (auth.currentUser?.uid ?: activeUserUid()).orEmpty()
        val commentDoc = mapOf(
            "reelId" to reelId.toString(),
            "reelDocId" to resolvedDocId,
            "userUid" to myUid,
            "username" to user.username.ifBlank { "user" },
            "text" to text,
            "timestamp" to nowMs,
            "createdAt" to FieldValue.serverTimestamp()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { db.enableNetwork() } catch (_: Exception) {}
                flushPendingFirestoreDocs()
                for (attempt in 1..3) {
                    try {
                        withTimeoutOrNull(10_000L) {
                            awaitTask(db.collection("reels_comments").add(commentDoc))
                        }
                        break
                    } catch (_: Exception) {
                        if (attempt < 3) delay(attempt * 500L)
                    }
                }
                if (activeReel != null) {
                    val refs = resolveReelDocRefs(activeReel)
                    for (ref in refs) {
                        for (attempt in 1..3) {
                            try {
                                withTimeoutOrNull(10_000L) {
                                    awaitTask(ref.set(mapOf("commentsCount" to FieldValue.increment(1)), SetOptions.merge()))
                                }
                                break
                            } catch (_: Exception) {
                                if (attempt < 3) delay(attempt * 500L)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Reel comment sync notice: ${e.message}")
            } finally {
                inFlightReelCommentMutations.remove(reelId)
            }
        }

        if (activeReel != null) {
            val preview = text.take(80)
            createAndPushFirestoreNotification(
                type = "comment",
                recipientUsername = activeReel.creatorUsername,
                recipientUid = activeReel.creatorUid,
                detail = "commented on your reel: \"$preview\"",
                postId = activeReel.id.toString(),
                postMediaUrl = activeReel.mediaUrl
            )
        }
    }

    private fun normalizeStoryMedia(story: StoryModel): StoryModel {
        val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(story.mediaUrl)
        val inferredType = when {
            story.mediaType.equals("text", ignoreCase = true) -> "text"
            story.mediaType.equals("video", ignoreCase = true) -> "video"
            story.mediaType.equals("image", ignoreCase = true) -> "image"
            fixedUrl.isBlank() && story.caption.isNotBlank() -> "text"
            fixedUrl.lowercase().let {
                it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(".webm")
            } -> "video"
            fixedUrl.isNotBlank() -> "image"
            else -> story.mediaType
        }
        return story.copy(mediaUrl = fixedUrl, mediaType = inferredType)
    }

    fun startListeningToStory(username: String) {
        storyListenerRegistration?.remove()
        storyListenerRegistration = null

        val cachedStory = allStories.value
            .firstOrNull { it.ownerUsername.equals(username, ignoreCase = true) }
            ?.let { normalizeStoryMedia(it) }
        if (cachedStory != null) {
            currentStoryState.value = cachedStory
        }

        storyListenerRegistration = db.collection("stories").document(username)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                if (snapshot.exists() && !isDocumentRestricted(snapshot)) {
                    val rawStory = snapshot.toObject(StoryModel::class.java)
                    if (rawStory != null) {
                        val ownerUid = snapshot.getString("userId")?.takeIf { it.isNotBlank() }
                            ?: snapshot.getString("creatorUid")?.takeIf { it.isNotBlank() }
                            ?: ""
                        if (ownerUid.isNotBlank()) {
                            storyCreatorUidByDocId[snapshot.id] = ownerUid
                            storyCreatorUidByOwner[username.trim().lowercase()] = ownerUid
                        }
                        val normalized = normalizeStoryMedia(rawStory.copy(firestoreDocId = snapshot.id))
                        val latestLocal = allStories.value
                            .firstOrNull { it.ownerUsername.equals(username, ignoreCase = true) }
                            ?.let { normalizeStoryMedia(it) }
                        val resolved = if (latestLocal != null &&
                            latestLocal.timestamp > normalized.timestamp &&
                            latestLocal.mediaUrl.isNotBlank() &&
                            (username in getPendingUploadDocIds("stories"))
                        ) {
                            latestLocal.copy(
                                viewsCount = maxOf(latestLocal.viewsCount, normalized.viewsCount),
                                viewerIds = (latestLocal.viewerIds + normalized.viewerIds).distinct(),
                                likes = (latestLocal.likes + normalized.likes).distinct()
                            )
                        } else {
                            normalized
                        }
                        currentStoryState.value = resolved
                        allStories.value = allStories.value.filterNot {
                            it.ownerUsername.equals(username, ignoreCase = true)
                        } + resolved
                        saveStoriesToPrefs()
                        recalculateCreatorStats()
                    }
                } else if (isDocumentRestricted(snapshot) || (!snapshot.metadata.isFromCache && username !in getPendingUploadDocIds("stories"))) {
                    currentStoryState.value = null
                    allStories.value = allStories.value.filterNot {
                        it.ownerUsername.equals(username, ignoreCase = true)
                    }
                    saveStoriesToPrefs()
                    recalculateCreatorStats()
                }
            }
    }

    fun toggleStoryLike(username: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stories").document(username).get().addOnSuccessListener { doc ->
            val likes = doc.get("likes") as? List<String> ?: emptyList()
            if (likes.contains(uid)) {
                doc.reference.update("likes", FieldValue.arrayRemove(uid))
            } else {
                doc.reference.update("likes", FieldValue.arrayUnion(uid))
            }
        }
    }
    fun deleteStory(username: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val trimmedUsername = username.trim()
            val safeOwner = trimmedUsername.lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }.take(32)
            val matchingStories = allStories.value.filter { it.ownerUsername.equals(trimmedUsername, ignoreCase = true) }
            val currentStory = currentStoryState.value?.takeIf { it.ownerUsername.equals(trimmedUsername, ignoreCase = true) }
            val storiesToDelete = (matchingStories + listOfNotNull(currentStory)).distinctBy { "${it.storyId}_${it.mediaUrl}_${it.firestoreDocId}" }

            // 1. Immediately remove locally so UI updates right away
            storiesToDelete.forEach { story ->
                if (story.mediaUrl.isNotBlank()) {
                    markUrlDeleted(story.mediaUrl)
                }
            }
            allStories.value = allStories.value.filterNot { it.ownerUsername.equals(trimmedUsername, ignoreCase = true) }
            if (currentStory != null) {
                currentStoryState.value = null
            }
            saveStoriesToPrefs()
            recalculateCreatorStats()

            // 2. Discover & delete from Firestore "stories" collection + collect all remote media URLs
            val mediaUrlsToDelete = storiesToDelete.map { it.mediaUrl }.filter { it.isNotBlank() }.toMutableList()
            var firestorePermanentError: String? = null

            withContext(Dispatchers.IO) {
                try {
                    val refsToDelete = linkedMapOf<String, DocumentReference>()
                    val explicitDocIds = (
                        listOf(trimmedUsername, trimmedUsername.lowercase(), safeOwner) +
                            storiesToDelete.map { it.firestoreDocId } +
                            storiesToDelete.map { it.storyId }
                        )
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                    for (docId in explicitDocIds) {
                        val ref = db.collection("stories").document(docId)
                        if (docRefExists(ref)) {
                            refsToDelete[ref.path] = ref
                        }
                    }

                    val usernameVariants = listOf(trimmedUsername, trimmedUsername.lowercase(), safeOwner)
                        .filter { it.isNotBlank() }
                        .distinct()
                    for (uname in usernameVariants) {
                        try {
                            val byOwnerUsername = awaitTask(db.collection("stories").whereEqualTo("ownerUsername", uname).get())
                            byOwnerUsername.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                        try {
                            val byUsername = awaitTask(db.collection("stories").whereEqualTo("username", uname).get())
                            byUsername.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                    }

                    storiesToDelete.forEach { story ->
                        for (urlVariant in buildMediaUrlVariants(story.mediaUrl)) {
                            try {
                                val byUrl = awaitTask(db.collection("stories").whereEqualTo("mediaUrl", urlVariant).get())
                                byUrl.documents.forEach { doc ->
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            } catch (_: Exception) {}
                        }
                        if (story.storyId.isNotBlank()) {
                            try {
                                val byStoryId = awaitTask(db.collection("stories").whereEqualTo("storyId", story.storyId).get())
                                byStoryId.documents.forEach { doc ->
                                    refsToDelete[doc.reference.path] = doc.reference
                                    mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    val uidCandidates = buildList {
                        if (trimmedUsername.equals(_profileState.value.username.trim(), ignoreCase = true)) {
                            auth.currentUser?.uid?.takeIf { it.isNotBlank() }?.let { add(it) }
                            resolveUploadUserId("").takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                        resolveUidForUsername(trimmedUsername)?.takeIf { it.isNotBlank() }?.let { add(it) }
                        storyCreatorUidByOwner[trimmedUsername.lowercase()]?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.distinct()
                    for (uid in uidCandidates) {
                        try {
                            val byUserId = awaitTask(db.collection("stories").whereEqualTo("userId", uid).get())
                            byUserId.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                        try {
                            val byCreatorUid = awaitTask(db.collection("stories").whereEqualTo("creatorUid", uid).get())
                            byCreatorUid.documents.forEach { doc ->
                                refsToDelete[doc.reference.path] = doc.reference
                                mediaUrlsToDelete.addAll(extractRemoteUrlsFromDoc(doc))
                            }
                        } catch (_: Exception) {}
                    }

                    for (ref in refsToDelete.values) {
                        val outcome = deleteFirestoreDocRefDetailed(ref)
                        if (!outcome.success && outcome.permanentError != null && firestorePermanentError == null) {
                            firestorePermanentError = outcome.permanentError
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("YouSnapViewModel", "Firestore story delete notice: ${e.message}")
                }
            }

            // 3. Delete all story media files from Supabase Storage (queues transient errors for retry)
            mediaUrlsToDelete.forEach { markUrlDeleted(it) }
            val supabaseResults = deleteSupabaseMediaUrls(mediaUrlsToDelete)
            val supabasePermanentErr = supabaseResults.firstOrNull { !it.isSuccess && it.responseCode in listOf(401, 403, 405) }?.errorMessage

            if (firestorePermanentError != null || supabasePermanentErr != null) {
                val msg = firestorePermanentError?.let { "Cloud delete blocked: $it" }
                    ?: supabasePermanentErr
                    ?: "Could not delete story from cloud"
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }

            refreshHomeFeed(showIndicator = false)
            onComplete()
        }
    }

    fun toggleHighQualityUploads() {
        val newVal = !highQualityUploads.value
        highQualityUploads.value = newVal
        _settingsState.value = _settingsState.value.copy(highQualityUploads = newVal)
    }
    fun toggleDarkMode() {
        val newVal = !forceDarkMode.value
        forceDarkMode.value = newVal
        _settingsState.value = _settingsState.value.copy(darkMode = newVal)
    }
    fun toggleCaptions() {
        val newVal = !captionsToggle.value
        captionsToggle.value = newVal
        _settingsState.value = _settingsState.value.copy(captionsEnabled = newVal)
    }
    fun toggleDataSaver() {
        val newVal = !dataSaverMode.value
        dataSaverMode.value = newVal
        _settingsState.value = _settingsState.value.copy(dataSaver = newVal)
    }
    fun toggleHapticFeedback() {
        val newVal = !hapticFeedbackFlow.value
        hapticFeedbackFlow.value = newVal
        _settingsState.value = _settingsState.value.copy(hapticFeedback = newVal)
    }
    fun togglePushNotifications() {
        val newVal = !pushNotificationsEnabled.value
        pushNotificationsEnabled.value = newVal
        _settingsState.value = _settingsState.value.copy(pushNotifications = newVal)
        prefs.edit().putBoolean("push_notifications_enabled", newVal).apply()
    }
    fun toggleQuietMode() {
        val newVal = !quietModeEnabled.value
        quietModeEnabled.value = newVal
        _settingsState.value = _settingsState.value.copy(quietMode = newVal)
        prefs.edit().putBoolean("quiet_mode_enabled", newVal).apply()
    }
    fun setFontScale(scale: Float) {
        _fontScaleFlow.value = scale
        _settingsState.value = _settingsState.value.copy(fontScale = scale)
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        if (query.isEmpty()) {
            searchResultUsers.value = emptyList()
            searchResultPosts.value = emptyList()
        } else {
            searchResultUsers.value = allUsers.value.filter { it.username.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
            val matching = reelsState.value.filter { it.caption.contains(query, ignoreCase = true) || it.creatorUsername.contains(query, ignoreCase = true) }
            searchResultPosts.value = FeedRankingAlgorithm.rankReels(matching, System.currentTimeMillis())
        }
    }

    fun removeSearchQuery(query: String) {
        val current = recentSearchesFlow.value.toMutableList()
        current.remove(query)
        recentSearchesFlow.value = current
        prefs.edit().putStringSet("recent_searches", current.toSet()).apply()
    }

    fun clearSearchHistory() {
        recentSearchesFlow.value = emptyList()
        prefs.edit().remove("recent_searches").apply()
    }

    fun clearAppCache() {
        viewModelScope.launch {
            try {
                app.cacheDir.deleteRecursively()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Failed to clear cache: ${e.message}")
            }
        }
    }

    fun unblockUser(username: String) {
        blockedAccounts.value = blockedAccounts.value.filter { it.username != username }
    }

    fun clearWatchHistory() { watchHistory.value = emptyList() }
    fun addToWatchHistory(reel: ReelEntity) { watchHistory.value = (listOf(reel) + watchHistory.value.filter { it.id != reel.id }).take(20) }
    fun setAdCreatorContextForPhoto(photo: FullScreenPhotoData?) {
        if (photo == null) {
            AdMobRevenueTracker.clearActiveMediaContext()
            return
        }
        val mediaId = photo.firestoreDocId?.takeIf { it.isNotBlank() } ?: photo.id.removePrefix("photo_")
        val creatorId = resolveUserUid(photo.creatorUsername, photo.creatorUid)
        AdMobRevenueTracker.setActiveMediaContext(creatorId, mediaId)
    }

    fun setAdCreatorContextForStory(username: String?, story: StoryModel?) {
        if (username.isNullOrBlank() && story == null) {
            AdMobRevenueTracker.clearActiveMediaContext()
            return
        }
        val docId = story?.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: story?.storyId?.takeIf { it.isNotBlank() }
            ?: username.orEmpty()
        val knownUid = storyCreatorUidByDocId[docId]
            ?: username?.let { storyCreatorUidByOwner[it.trim().lowercase()] }
            ?: ""
        val creatorId = resolveUserUid(username.orEmpty(), knownUid)
        AdMobRevenueTracker.setActiveMediaContext(creatorId, docId)
    }

    fun clearAdCreatorContext() {
        AdMobRevenueTracker.clearActiveMediaContext()
    }

    private fun logMonetizedMediaViewOnce(
        mediaCollection: String,
        mediaId: String,
        creatorUsername: String,
        knownCreatorUid: String
    ) {
        val cleanMediaId = mediaId.trim()
        if (cleanMediaId.isBlank()) return
        val viewerUid = (auth.currentUser?.uid ?: activeUserUid()).orEmpty().trim()
        if (viewerUid.isBlank()) return
        AdMobRevenueTracker.setFallbackViewerUid(viewerUid)

        val sessionKey = "${mediaCollection.trim().lowercase()}:$cleanMediaId"
        if (!sessionLoggedAdViewMediaKeys.add(sessionKey)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedCreatorId = resolveUserUid(creatorUsername, knownCreatorUid)
                if (resolvedCreatorId.isBlank() && creatorUsername.isNotBlank()) {
                    try {
                        val userSnap = awaitTask(
                            db.collection("users")
                                .whereEqualTo("username", creatorUsername.trim())
                                .limit(1)
                                .get()
                        )
                        userSnap.documents.firstOrNull()?.let { doc ->
                            updateUserMonetizationStatusCache(doc, doc.toObject(ProfileEntity::class.java))
                            resolvedCreatorId = doc.id
                        }
                    } catch (_: Exception) {}
                }
                if (resolvedCreatorId.isBlank() && mediaCollection in listOf("posts", "reels", "stories", "videos")) {
                    try {
                        val mediaDoc = awaitTask(db.collection(mediaCollection).document(cleanMediaId).get())
                        resolvedCreatorId = mediaDoc.getString("userId")
                            ?: mediaDoc.getString("creatorUid")
                            ?: ""
                    } catch (_: Exception) {}
                }

                // Only log if creator exists and viewer is NOT the creator
                if (resolvedCreatorId.isBlank() || resolvedCreatorId == viewerUid) {
                    return@launch
                }

                val isCreatorApproved = when {
                    resolvedCreatorId in monetizedUserIds -> true
                    resolvedCreatorId in nonMonetizedUserIds -> false
                    else -> {
                        val creatorDoc = awaitTask(db.collection("users").document(resolvedCreatorId).get())
                        val approved = creatorDoc.exists() && (
                            creatorDoc.getBoolean("isMonetized") == true ||
                                creatorDoc.getString("monetizationStatus")?.trim().equals("Approved", ignoreCase = true)
                            )
                        updateUserMonetizationStatusCache(creatorDoc, creatorDoc.toObject(ProfileEntity::class.java))
                        approved
                    }
                }

                if (!isCreatorApproved) {
                    return@launch
                }

                val viewEvent = hashMapOf<String, Any>(
                    "eventType" to "view",
                    "creatorId" to resolvedCreatorId,
                    "mediaId" to cleanMediaId,
                    "viewerId" to viewerUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                awaitTask(db.collection("ad_events").add(viewEvent))
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Silent ad_events view log notice: ${e.message}")
            }
        }
    }

    fun logPhotoAdViewOnce(photo: FullScreenPhotoData) {
        val docId = photo.firestoreDocId?.takeIf { it.isNotBlank() } ?: photo.id.removePrefix("photo_")
        logMonetizedMediaViewOnce(
            mediaCollection = "posts",
            mediaId = docId,
            creatorUsername = photo.creatorUsername,
            knownCreatorUid = photo.creatorUid
        )
    }

    fun logReelAdViewOnce(reel: ReelEntity) {
        val docId = reelFirestoreDocIdByMediaUrl[reel.mediaUrl]
            ?: reelFirestoreDocIdByTimestamp[reel.timestamp]
            ?: reelFirestoreDocIdById[reel.id]
            ?: reel.id.toString()
        val col = reelCollectionByMediaUrl[reel.mediaUrl]
            ?: reelCollectionById[reel.id]
            ?: "reels"
        logMonetizedMediaViewOnce(
            mediaCollection = col,
            mediaId = docId,
            creatorUsername = reel.creatorUsername,
            knownCreatorUid = reel.creatorUid
        )
    }

    fun logStoryAdViewOnce(username: String, explicitStory: StoryModel? = null) {
        val matchedStory = explicitStory ?: allStories.value.firstOrNull {
            it.ownerUsername.equals(username, ignoreCase = true)
        }
        val docId = matchedStory?.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: matchedStory?.storyId?.takeIf { it.isNotBlank() }
            ?: username
        val knownUid = storyCreatorUidByDocId[docId]
            ?: storyCreatorUidByOwner[username.trim().lowercase()]
            ?: ""
        logMonetizedMediaViewOnce(
            mediaCollection = "stories",
            mediaId = docId,
            creatorUsername = username,
            knownCreatorUid = knownUid
        )
    }

    fun recordReelViewOnce(id: Int) {
        val matchedReel = _reelsState.value.find { it.id == id }
        matchedReel?.let { logReelAdViewOnce(it) }
        _reelsState.value = _reelsState.value.map { reel ->
            if (reel.id == id) reel.copy(viewsCount = reel.viewsCount + 1) else reel
        }
        saveReelsToPrefs()
        recalculateCreatorStats()

        val targetCollection = (matchedReel?.let { reelCollectionByMediaUrl[it.mediaUrl] }
            ?: reelCollectionById[id])
            ?.takeIf { it == "reels" || it == "videos" }
            ?: "reels"

        val query = if (matchedReel != null && matchedReel.mediaUrl.isNotBlank()) {
            db.collection(targetCollection).whereEqualTo("mediaUrl", matchedReel.mediaUrl)
        } else {
            db.collection(targetCollection).whereEqualTo("reelIntId", id)
        }
        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    doc.reference.update("viewsCount", FieldValue.increment(1))
                }
            } else {
                db.collection(targetCollection).whereEqualTo("id", id.toString()).get().addOnSuccessListener { fallback ->
                    for (doc in fallback.documents) {
                        doc.reference.update("viewsCount", FieldValue.increment(1))
                    }
                }
            }
        }
    }
    fun updateReelWatchTime(id: Int, time: Long) {}
    fun updateReelWatchTime(reel: ReelEntity, time: Long = 1L) { updateReelWatchTime(reel.id, time) }

    fun refreshReelsFeed(onComplete: () -> Unit = {}) {
        isReelsRefreshing.value = true
        refreshHomeFeed {
            isReelsRefreshing.value = false
            onComplete()
        }
    }

    fun toggleBookmarkReel(id: Int) {
        val current = bookmarkedReelIds.value
        bookmarkedReelIds.value = if (current.contains(id)) current - id else current + id
    }

    fun reportReel(reelId: Int, reason: String, details: String) {
        val matchedReel = _reelsState.value.find { it.id == reelId }
            ?: selectedReelForPreview.value?.takeIf { it.id == reelId }
        val resolvedDocId = (matchedReel?.let {
            reelFirestoreDocIdByMediaUrl[it.mediaUrl]
                ?: reelFirestoreDocIdByTimestamp[it.timestamp]
        } ?: reelFirestoreDocIdById[reelId]).orEmpty().ifBlank { reelId.toString() }
        val resolvedCollection = (matchedReel?.let { reelCollectionByMediaUrl[it.mediaUrl] }
            ?: reelCollectionById[reelId])
            ?.takeIf { it == "reels" || it == "videos" }
            ?: "reels"

        submitViolationReport(
            reportedCollection = resolvedCollection,
            reportedMediaId = resolvedDocId,
            reportedUsername = matchedReel?.creatorUsername.orEmpty(),
            knownReportedUserId = matchedReel?.creatorUid.orEmpty(),
            reason = reason,
            details = details
        )
    }

    fun resetPassword(email: String, pass: String): Boolean {
        registeredPassword.value = pass
        try {
            auth.sendPasswordResetEmail(email)
        } catch (e: Exception) {}
        return true
    }

    fun loginAsDemoUser(onSuccess: () -> Unit) {
        val demoProfile = ProfileEntity(name = "Demo User", username = "demouser", email = "demo@yousnap.app")
        _profileState.value = demoProfile
        saveProfileToPrefs(demoProfile, "demouser")
        loadFollowingFromFirestore()
        startListeningToNotifications()
        startListeningToAccountStatus()
        syncFcmToken()
        onSuccess()
    }

    fun followBackUser(notifId: String, username: String) {
        val alreadyFollowing = otherUserIsFollowing.value.entries
            .firstOrNull { it.key.equals(username, ignoreCase = true) }?.value ?: false
        if (!alreadyFollowing) {
            toggleFollowUser(targetUsername = username)
        }
        notifications.value = notifications.value.map {
            if (it.id == notifId) it.copy(isFollowingBack = true, isRead = true) else it
        }
        unreadNotificationsCount.value = notifications.value.count { !it.isRead }
        if (notifId.isNotBlank()) {
            db.collection("notifications").document(notifId)
                .update(mapOf("isFollowingBack" to true, "isRead" to true))
        }
    }
    fun followBackUser(notifId: Int, username: String) {
        followBackUser(notifId.toString(), username)
    }

    fun markNotificationAsRead(notifId: String) {
        if (notifId.isBlank()) return
        notifications.value = notifications.value.map {
            if (it.id == notifId) it.copy(isRead = true) else it
        }
        unreadNotificationsCount.value = notifications.value.count { !it.isRead }
        db.collection("notifications").document(notifId).update("isRead", true)
    }

    fun markAllNotificationsAsRead() {
        val unreadIds = notifications.value.filter { !it.isRead }.map { it.id }
        if (unreadIds.isEmpty()) return
        notifications.value = notifications.value.map { it.copy(isRead = true) }
        unreadNotificationsCount.value = 0
        unreadIds.forEach { id ->
            if (id.isNotBlank()) {
                db.collection("notifications").document(id).update("isRead", true)
            }
        }
    }

    fun removeNotification(notifId: String) {
        notifDocsByUid.remove(notifId)
        notifDocsByUsername.remove(notifId)
        notifications.value = notifications.value.filter { it.id != notifId }
        unreadNotificationsCount.value = notifications.value.count { !it.isRead }
        if (notifId.isNotBlank()) {
            db.collection("notifications").document(notifId).delete()
        }
    }
    fun removeNotification(notifId: Int) {
        removeNotification(notifId.toString())
    }

    fun syncFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    prefs.edit().putString("fcm_token", token).apply()
                    val uid = activeUserUid()
                    val profile = _profileState.value
                    if (!uid.isNullOrBlank()) {
                        val link = DeepLinkHelper.buildProfileUrl(uid)
                        profileLinkByUid[uid] = link
                        db.collection("users").document(uid).set(
                            mapOf(
                                "fcmToken" to token,
                                "fcmUpdatedAt" to System.currentTimeMillis(),
                                "username" to profile.username,
                                "name" to profile.name,
                                "profileLink" to link
                            ),
                            SetOptions.merge()
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("YouSnapViewModel", "FCM token sync notice: ${t.message}")
        }
    }

    private fun loadFollowingFromFirestore() {
        val myUid = activeUserUid().orEmpty()
        val myUsername = _profileState.value.username.ifBlank { "user" }
        val followerKey = myUid.ifBlank { myUsername }
        if (followerKey.isBlank()) return

        db.collection("follows").whereEqualTo("followerUid", followerKey).get()
            .addOnSuccessListener { snapshot ->
                val map = otherUserIsFollowing.value.toMutableMap()
                snapshot.documents.forEach { doc ->
                    val target = doc.getString("targetUsername").orEmpty()
                    if (target.isNotBlank()) map[target] = true
                }
                otherUserIsFollowing.value = map
                _reelsState.value = _reelsState.value.map { reel ->
                    val following = map.entries.firstOrNull {
                        it.key.equals(reel.creatorUsername, ignoreCase = true)
                    }?.value ?: false
                    reel.copy(isFollowingCreator = following)
                }
            }
    }

    private fun createAndPushFirestoreNotification(
        type: String,
        recipientUsername: String,
        recipientUid: String = "",
        detail: String,
        postId: String = "",
        postMediaUrl: String = "",
        fixedDocId: String? = null
    ) {
        val sender = _profileState.value
        val senderUsername = sender.username.ifBlank { "user" }
        val senderUid = activeUserUid().orEmpty().ifBlank { senderUsername }
        val cleanRecipient = recipientUsername.trim()

        if ((recipientUid.isNotBlank() && recipientUid == senderUid) ||
            (cleanRecipient.isNotBlank() && cleanRecipient.equals(senderUsername, ignoreCase = true))
        ) {
            return
        }
        if (cleanRecipient.isBlank() && recipientUid.isBlank()) return

        val nowMs = System.currentTimeMillis()
        val docId = fixedDocId ?: "notif_${type.lowercase()}_${senderUsername.lowercase().replace(Regex("[^a-z0-9_]"), "")}_$nowMs"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedRecipientUid = recipientUid.trim()
                if (resolvedRecipientUid.isBlank() && cleanRecipient.isNotBlank()) {
                    val userSnap = withTimeoutOrNull(4000L) {
                        awaitTask(db.collection("users").whereEqualTo("username", cleanRecipient).limit(1).get())
                    }
                    resolvedRecipientUid = userSnap?.documents?.firstOrNull()?.id.orEmpty()
                }

                val payload = mapOf(
                    "id" to docId,
                    "type" to type.lowercase(),
                    "senderUid" to senderUid,
                    "senderUsername" to senderUsername,
                    "senderName" to sender.name.ifBlank { senderUsername },
                    "senderAvatar" to resolveCurrentUserRemotePhotoUrl(),
                    "recipientUid" to resolvedRecipientUid,
                    "recipientUsername" to cleanRecipient,
                    "postId" to postId,
                    "postMediaUrl" to postMediaUrl,
                    "detail" to detail,
                    "isFollowingBack" to (otherUserIsFollowing.value.entries.firstOrNull {
                        it.key.equals(cleanRecipient, ignoreCase = true)
                    }?.value ?: false),
                    "isRead" to false,
                    "timestamp" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                awaitTask(db.collection("notifications").document(docId).set(payload))
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Notification write notice: ${e.message}")
            }
        }
    }

    private fun stopListeningToNotifications() {
        notifUidListener?.remove()
        notifUidListener = null
        notifUsernameListener?.remove()
        notifUsernameListener = null
        notifDocsByUid.clear()
        notifDocsByUsername.clear()
    }

    fun startListeningToNotifications() {
        stopListeningToNotifications()
        val myUid = activeUserUid().orEmpty()
        val myUsername = _profileState.value.username.trim()
        if (myUid.isBlank() && (myUsername.isBlank() || myUsername == "user")) return

        fun parseNotif(doc: com.google.firebase.firestore.DocumentSnapshot): NotificationEntity? {
            val senderUsername = doc.getString("senderUsername") ?: doc.getString("username") ?: ""
            if (senderUsername.isBlank()) return null
            val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val type = (doc.getString("type") ?: "activity").lowercase()
            val followingMap = otherUserIsFollowing.value
            val isFollowingBack = doc.getBoolean("isFollowingBack") == true ||
                (followingMap.entries.firstOrNull { it.key.equals(senderUsername, ignoreCase = true) }?.value == true)
            return NotificationEntity(
                id = doc.id,
                type = type,
                username = senderUsername,
                senderUid = doc.getString("senderUid").orEmpty(),
                senderName = doc.getString("senderName").orEmpty().ifBlank { senderUsername },
                userAvatarUrl = doc.getString("senderAvatar").orEmpty(),
                recipientUid = doc.getString("recipientUid").orEmpty(),
                recipientUsername = doc.getString("recipientUsername").orEmpty(),
                postId = doc.getString("postId").orEmpty(),
                postMediaUrl = com.example.data.CdnConfig.getFullMediaUrl(doc.getString("postMediaUrl").orEmpty()),
                time = formatRelativeTime(ts),
                detail = doc.getString("detail").orEmpty(),
                isFollowingBack = isFollowingBack,
                isRead = doc.getBoolean("isRead") ?: false,
                timestamp = ts
            )
        }

        fun publishMerged() {
            val combined = LinkedHashMap<String, NotificationEntity>()
            notifDocsByUid.forEach { (id, item) -> combined[id] = item }
            notifDocsByUsername.forEach { (id, item) -> combined[id] = item }
            val sorted = combined.values.sortedByDescending { it.timestamp }
            notifications.value = sorted
            unreadNotificationsCount.value = sorted.count { !it.isRead }
        }

        fun handleSnapshot(
            targetMap: java.util.concurrent.ConcurrentHashMap<String, NotificationEntity>,
            snapshot: com.google.firebase.firestore.QuerySnapshot?
        ) {
            if (snapshot == null) return
            targetMap.clear()
            snapshot.documents.forEach { doc ->
                parseNotif(doc)?.let { targetMap[doc.id] = it }
            }
            publishMerged()

            snapshot.documentChanges.forEach { change ->
                if (change.type == DocumentChange.Type.ADDED) {
                    val notif = parseNotif(change.document) ?: return@forEach
                    val isSelf = (notif.senderUid.isNotBlank() && notif.senderUid == myUid) ||
                        notif.username.equals(myUsername, ignoreCase = true)
                    val isFresh = notif.timestamp >= (appLaunchTimestampMs - 5_000L)
                    if (!isSelf && !notif.isRead && isFresh && displayedSystemNotifIds.add(notif.id)) {
                        val title = when (notif.type.lowercase()) {
                            "follow" -> "New Follower"
                            "like" -> "New Like"
                            "comment" -> "New Comment"
                            else -> "YouSnap"
                        }
                        val body = "@${notif.username} ${notif.detail}".trim()
                        YouSnapNotificationHelper.showNotification(
                            context = app,
                            title = title,
                            body = body,
                            type = notif.type,
                            senderUsername = notif.username,
                            postId = notif.postId,
                            notificationKey = notif.id
                        )
                    }
                }
            }
        }

        if (myUid.isNotBlank()) {
            notifUidListener = db.collection("notifications")
                .whereEqualTo("recipientUid", myUid)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Notif UID listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    handleSnapshot(notifDocsByUid, snapshot)
                }
        } else if (myUsername.isNotBlank() && myUsername != "user") {
            notifUsernameListener = db.collection("notifications")
                .whereEqualTo("recipientUsername", myUsername)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Notif username listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    handleSnapshot(notifDocsByUsername, snapshot)
                }
        }
    }

    private fun formatRelativeTime(timestampMs: Long): String {
        val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val seconds = diff / 1000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            seconds < 60L -> "Just now"
            minutes < 60L -> "${minutes}m"
            hours < 24L -> "${hours}h"
            days < 7L -> "${days}d"
            else -> "${days / 7L}w"
        }
    }

    fun terminateSession(id: String, deviceName: String) {
        loginDevices.value = loginDevices.value.filter { it.id != id }
    }

    fun recordStoryView(username: String) {
        logStoryAdViewOnce(username)
        val myUser = profileState.value.username
        allStories.value = allStories.value.map { story ->
            if (story.ownerUsername.equals(username, ignoreCase = true)) {
                val updatedViewers = if (myUser.isNotBlank() && !story.viewerIds.contains(myUser)) {
                    story.viewerIds + myUser
                } else story.viewerIds
                story.copy(viewerIds = updatedViewers, viewsCount = story.viewsCount + 1)
            } else story
        }
        saveStoriesToPrefs()
        recalculateCreatorStats()
        db.collection("stories").document(username).update("viewerIds", FieldValue.arrayUnion(myUser), "viewsCount", FieldValue.increment(1))
    }

    fun stopListeningToStory() {
        storyListenerRegistration?.remove()
        storyListenerRegistration = null
        currentStoryState.value = null
    }
    
    fun getViewerProfilesForStory(story: StoryModel?): List<ViewerProfile> {
        if (story == null) return emptyList()
        return story.viewerIds.map { username ->
            ViewerProfile(
                username = username,
                name = username,
                initials = username.take(2).uppercase(),
                hasLiked = story.likes.contains(username)
            )
        }
    }

    fun reportStory(storyOwner: String, reason: String, details: String, storyDocId: String = "") {
        val matchedStory = allStories.value.firstOrNull {
            (storyDocId.isNotBlank() && it.firestoreDocId == storyDocId) ||
                it.ownerUsername.equals(storyOwner, ignoreCase = true)
        }
        val resolvedDocId = storyDocId.ifBlank { matchedStory?.firestoreDocId.orEmpty() }
            .ifBlank { storyOwner }
        val knownUid = storyCreatorUidByDocId[resolvedDocId]
            ?: storyCreatorUidByOwner[storyOwner.trim().lowercase()]
            ?: ""

        submitViolationReport(
            reportedCollection = "stories",
            reportedMediaId = resolvedDocId,
            reportedUsername = storyOwner,
            knownReportedUserId = knownUid,
            reason = reason,
            details = details
        )
    }

    fun triggerStoryLikeNotification(username: String) {}

    private fun extractMonetizationDocTimestampMs(doc: DocumentSnapshot): Long {
        val created = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
        val updated = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
        val submitted = doc.getLong("submittedAt") ?: 0L
        val ts = doc.getLong("timestamp") ?: 0L
        return maxOf(created, updated, submitted, ts)
    }

    private fun resolveMonetizationStatusFromDocs(
        userDoc: DocumentSnapshot?,
        requestDocs: List<DocumentSnapshot>
    ): String {
        val userStatusRaw = userDoc?.getString("monetizationStatus")
        val userStatus = if (userStatusRaw != null) {
            normalizeMonetizationStatus(userStatusRaw)
        } else {
            normalizeMonetizationStatus(_profileState.value.monetizationStatus)
        }
        val userIsMonetized = userDoc?.getBoolean("isMonetized") == true
        val userRejectedFlag = userDoc?.getBoolean("monetizationRejected") == true
        val userSubmittedAtMs = userDoc?.getLong("monetizationSubmittedAt") ?: 0L

        val latestReqDoc = requestDocs.maxByOrNull { extractMonetizationDocTimestampMs(it) }
        val reqStatus = latestReqDoc?.getString("status")?.takeIf { it.isNotBlank() }?.let {
            normalizeMonetizationStatus(it)
        }
        val reqTimestampMs = latestReqDoc?.let { extractMonetizationDocTimestampMs(it) } ?: 0L

        // If user just re-applied at T2 > old doc timestamp T1 while the new request doc is still arriving,
        // avoid letting the stale older request doc briefly override the fresh "Pending" re-application.
        val isStaleRequestBeforeReapply = latestReqDoc != null &&
            userStatus == "Pending" &&
            !userRejectedFlag &&
            !userIsMonetized &&
            userSubmittedAtMs > 0L &&
            reqTimestampMs > 0L &&
            userSubmittedAtMs > reqTimestampMs

        return when {
            userIsMonetized || userStatus == "Approved" -> "Approved"
            !isStaleRequestBeforeReapply && reqStatus == "Approved" && userStatus != "Rejected" && !userRejectedFlag -> "Approved"
            userRejectedFlag || userStatus == "Rejected" -> "Rejected"
            !isStaleRequestBeforeReapply && reqStatus == "Rejected" -> "Rejected"
            userStatus == "Pending" || reqStatus == "Pending" -> "Pending"
            else -> userStatus
        }
    }

    private fun publishResolvedMonetizationState(uid: String) {
        val userDoc = latestMonetizationUserDoc
        val resolvedStatus = resolveMonetizationStatusFromDocs(userDoc, latestMonetizationRequestDocs)
        monetizationStatus.value = resolvedStatus

        val updatedFollowers = if (userDoc != null && userDoc.exists()) {
            (userDoc.getLong("followersCount") ?: _profileState.value.followersCount.toLong()).toInt()
        } else {
            _profileState.value.followersCount
        }
        val updatedFollowing = if (userDoc != null && userDoc.exists()) {
            (userDoc.getLong("followingCount") ?: _profileState.value.followingCount.toLong()).toInt()
        } else {
            _profileState.value.followingCount
        }
        val updatedProfile = _profileState.value.copy(
            followersCount = updatedFollowers,
            followingCount = updatedFollowing,
            monetizationStatus = resolvedStatus
        )
        _profileState.value = updatedProfile
        myFollowersCount.value = updatedFollowers
        myFollowingCount.value = updatedFollowing
        saveProfileToPrefs(updatedProfile, uid)
    }

    fun stopListeningToMonetizationStatus() {
        monetizationStatusListener?.remove()
        monetizationStatusListener = null
        monetizationRequestsListener?.remove()
        monetizationRequestsListener = null
        listenedMonetizationUid = null
        latestMonetizationUserDoc = null
        latestMonetizationRequestDocs = emptyList()
    }

    fun startListeningToMonetizationStatus() {
        val uid = (auth.currentUser?.uid ?: activeUserUid())?.trim().orEmpty()
        if (uid.isBlank()) return
        if (listenedMonetizationUid == uid &&
            monetizationStatusListener != null &&
            monetizationRequestsListener != null
        ) return

        stopListeningToMonetizationStatus()
        listenedMonetizationUid = uid
        try {
            monetizationStatusListener = db.collection("users")
                .document(uid)
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Monetization user listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        latestMonetizationUserDoc = doc
                        publishResolvedMonetizationState(uid)
                    }
                }

            monetizationRequestsListener = db.collection("monetization_requests")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Monetization requests listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        latestMonetizationRequestDocs = snapshot.documents
                        publishResolvedMonetizationState(uid)
                    }
                }
        } catch (t: Throwable) {
            android.util.Log.w("YouSnapViewModel", "Could not attach monetization listeners: ${t.message}")
        }
    }

    fun syncUnappliedMonetizationStatus(isRealEligible: Boolean) {
        val uid = (auth.currentUser?.uid ?: activeUserUid())?.trim().orEmpty()
        if (uid.isBlank()) return
        val currentNormalized = normalizeMonetizationStatus(monetizationStatus.value)
        if (currentNormalized == "Pending" || currentNormalized == "Approved" || currentNormalized == "Rejected") {
            return
        }
        val cachedDoc = latestMonetizationUserDoc
        if (cachedDoc != null && (cachedDoc.getBoolean("isMonetized") == true || cachedDoc.getBoolean("monetizationRejected") == true)) {
            publishResolvedMonetizationState(uid)
            return
        }
        val hasExistingDecisionInRequests = latestMonetizationRequestDocs.any { reqDoc ->
            val st = normalizeMonetizationStatus(reqDoc.getString("status"))
            st == "Pending" || st == "Approved" || st == "Rejected"
        }
        if (hasExistingDecisionInRequests) {
            publishResolvedMonetizationState(uid)
            return
        }

        val targetStatus = if (isRealEligible) "Not Applied" else "Not Eligible"
        if (currentNormalized != targetStatus) {
            monetizationStatus.value = targetStatus
            val updated = _profileState.value.copy(monetizationStatus = targetStatus)
            _profileState.value = updated
            saveProfileToPrefs(updated, uid)
        }
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            val remoteRaw = doc.getString("monetizationStatus")?.trim().orEmpty()
            val alreadyApplied = doc.getBoolean("monetizationApplied") == true
            val isMonetized = doc.getBoolean("isMonetized") == true
            val isRejected = doc.getBoolean("monetizationRejected") == true
            val isAlreadyDecided = isMonetized ||
                isRejected ||
                remoteRaw.equals("Pending", ignoreCase = true) ||
                remoteRaw.equals("Approved", ignoreCase = true) ||
                remoteRaw.equals("Rejected", ignoreCase = true)
            if (!alreadyApplied && !isAlreadyDecided && remoteRaw != targetStatus && latestMonetizationRequestDocs.isEmpty()) {
                db.collection("users").document(uid).set(
                    mapOf("monetizationStatus" to targetStatus),
                    SetOptions.merge()
                )
            }
        }
    }

    fun submitMonetizationApplication(
        fullName: String = "",
        age: String = "",
        fatherName: String = "",
        villageAddress: String = "",
        payoutMethod: String = "",
        upiId: String = "",
        bankName: String = "",
        accountHolderName: String = "",
        accountNumber: String = "",
        ifscCode: String = "",
        name: String = "",
        email: String = "",
        country: String = ""
    ) {
        val uid = (auth.currentUser?.uid ?: activeUserUid())?.trim().orEmpty()
        if (uid.isBlank()) return
        val currentStatus = normalizeMonetizationStatus(monetizationStatus.value)
        if (currentStatus == "Pending" || currentStatus == "Approved") return

        val submittedAtMs = System.currentTimeMillis()
        val currentProfile = _profileState.value
        val totalFollowers = maxOf(myFollowersCount.value, currentProfile.followersCount).coerceAtLeast(0)
        val totalViews = maxOf(videoViewsCount.value, currentProfile.videoViewsCount).coerceAtLeast(0)

        monetizationStatus.value = "Pending"
        val updatedProfile = currentProfile.copy(monetizationStatus = "Pending")
        _profileState.value = updatedProfile
        saveProfileToPrefs(updatedProfile, uid)

        val requestData = hashMapOf<String, Any>(
            "userId" to uid,
            "creatorName" to currentProfile.name,
            "creatorUsername" to currentProfile.username,
            "creatorAvatar" to resolveCurrentUserRemotePhotoUrl(),
            "followersCount" to totalFollowers,
            "totalViews" to totalViews,
            "earnings" to 0,
            "status" to "Pending",
            "fullName" to fullName.ifEmpty { name },
            "age" to age,
            "fatherName" to fatherName,
            "villageAddress" to villageAddress,
            "payoutMethod" to payoutMethod,
            "upiId" to upiId,
            "bankName" to bankName,
            "accountHolderName" to accountHolderName,
            "accountNumber" to accountNumber,
            "ifscCode" to ifscCode,
            "email" to email.ifBlank { currentProfile.email },
            "country" to country,
            "submittedAt" to submittedAtMs,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("monetization_requests").add(requestData)

        db.collection("users").document(uid).set(
            mapOf(
                "monetizationStatus" to "Pending",
                "monetizationApplied" to true,
                "monetizationRejected" to false,
                "isMonetized" to false,
                "monetizationSubmittedAt" to submittedAtMs
            ),
            SetOptions.merge()
        )
    }

    fun updateMonetizationStatus(status: String) {
        val normalized = normalizeMonetizationStatus(status)
        monetizationStatus.value = normalized
        val updated = _profileState.value.copy(monetizationStatus = normalized)
        _profileState.value = updated
        val uid = activeUserUid()
        saveProfileToPrefs(updated, uid)
        if (!uid.isNullOrBlank()) {
            db.collection("users").document(uid).set(
                mapOf("monetizationStatus" to normalized),
                SetOptions.merge()
            )
        }
    }

    fun stopListeningToAccountStatus() {
        accountStatusListener?.remove()
        accountStatusListener = null
        listenedAccountUid = null
    }

    fun startListeningToAccountStatus() {
        val uid = activeUserUid()?.trim().orEmpty()
        if (uid.isBlank()) return
        if (listenedAccountUid == uid && accountStatusListener != null) return

        stopListeningToAccountStatus()
        listenedAccountUid = uid
        try {
            accountStatusListener = db.collection("users")
                .document(uid)
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Account status listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc == null || !doc.exists()) return@addSnapshotListener

                    val status = doc.getString("status")?.trim().orEmpty()
                    val isSuspendedFlag = doc.getBoolean("isSuspended") == true
                    val isBannedFlag = doc.getBoolean("isBanned") == true
                    val isRestrictedFlag = doc.getBoolean("isRestricted") == true

                    val isStatusBanned = status.equals("Banned", ignoreCase = true)
                    val isStatusSuspended = status.equals("Suspended", ignoreCase = true)
                    val isStatusActive = status.equals("Active", ignoreCase = true)
                    val hasExplicitRestrictedFalse = doc.contains("isRestricted") && doc.getBoolean("isRestricted") == false

                    val lastKnownStatus = prefs.getString("last_account_status_$uid", "").orEmpty()
                    val statusRestoredToActive = isStatusActive && hasExplicitRestrictedFalse &&
                        (lastKnownStatus.equals("Suspended", ignoreCase = true) ||
                            lastKnownStatus.equals("Banned", ignoreCase = true))

                    val shouldLock = when {
                        isStatusBanned || isStatusSuspended || isRestrictedFlag -> true
                        statusRestoredToActive -> false
                        isBannedFlag || isSuspendedFlag -> true
                        else -> false
                    }

                    if (status.isNotBlank()) {
                        prefs.edit().putString("last_account_status_$uid", status).apply()
                    }

                    if (shouldLock) {
                        val restrictionType = if (isStatusBanned || isBannedFlag) "Banned" else "Suspended"
                        val reason = listOfNotNull(
                            doc.getString("suspendReason"),
                            doc.getString("restrictionReason"),
                            doc.getString("banReason")
                        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        val alreadySubmitted = _accountRestrictionState.value.appealSubmitted ||
                            prefs.getBoolean("appeal_submitted_$uid", false)

                        _accountRestrictionState.value = _accountRestrictionState.value.copy(
                            isLocked = true,
                            restrictionType = restrictionType,
                            reason = reason,
                            appealSubmitted = alreadySubmitted
                        )
                    } else {
                        if (prefs.contains("appeal_submitted_$uid")) {
                            prefs.edit().remove("appeal_submitted_$uid").apply()
                        }
                        _accountRestrictionState.value = AccountRestrictionState(isLocked = false)
                    }
                }
        } catch (t: Throwable) {
            android.util.Log.w("YouSnapViewModel", "Could not attach account status listener: ${t.message}")
        }
    }

    fun submitAccountAppeal(message: String) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) {
            _accountRestrictionState.value = _accountRestrictionState.value.copy(
                appealError = "Please explain why your account should be restored."
            )
            return
        }
        val uid = activeUserUid()?.trim().orEmpty()
        if (uid.isBlank()) {
            _accountRestrictionState.value = _accountRestrictionState.value.copy(
                appealError = "Unable to identify your account. Please sign out and log in again."
            )
            return
        }
        if (_accountRestrictionState.value.isSubmittingAppeal) return

        _accountRestrictionState.value = _accountRestrictionState.value.copy(
            isSubmittingAppeal = true,
            appealError = null
        )

        val profile = _profileState.value
        val email = profile.email.ifBlank { auth.currentUser?.email.orEmpty() }
        val ticketData = hashMapOf(
            "userId" to uid,
            "creatorName" to profile.name,
            "creatorUsername" to profile.username,
            "creatorAvatar" to resolveCurrentUserRemotePhotoUrl(),
            "email" to email,
            "category" to "Suspension Appeal",
            "subject" to "Account Restore Request",
            "message" to cleanMessage,
            "status" to "Open",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("support_tickets")
            .add(ticketData)
            .addOnSuccessListener {
                prefs.edit().putBoolean("appeal_submitted_$uid", true).apply()
                _accountRestrictionState.value = _accountRestrictionState.value.copy(
                    isSubmittingAppeal = false,
                    appealSubmitted = true,
                    appealError = null
                )
            }
            .addOnFailureListener { e ->
                _accountRestrictionState.value = _accountRestrictionState.value.copy(
                    isSubmittingAppeal = false,
                    appealError = e.localizedMessage ?: "Failed to submit appeal. Please try again."
                )
            }
    }

    fun submitSupportTicket(subject: String, description: String) {
        val uid = auth.currentUser?.uid ?: activeUserUid().orEmpty()
        if (uid.isBlank()) return
        db.collection("support_tickets").add(
            mapOf(
                "userId" to uid,
                "subject" to subject,
                "description" to description,
                "status" to "Open",
                "timestamp" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    fun resetFeedAlgorithm() {}

    fun togglePrivateAccount() {
        val newVal = !settingsState.value.isPrivateAccount
        togglePrivateAccount(newVal)
    }

    fun togglePrivateAccount(isPrivate: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("isPrivateAccount", isPrivate)
        _settingsState.value = _settingsState.value.copy(isPrivateAccount = isPrivate)
    }
    
    fun toggleUserPrivacy(isPrivate: Boolean) { togglePrivateAccount(isPrivate) }

    fun updateProfile(
        name: String,
        username: String,
        bio: String,
        category: String,
        profilePic: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val currentFirebaseUser = auth.currentUser
        val uid = currentFirebaseUser?.uid ?: activeUserUid()
        if (uid.isNullOrBlank() || currentFirebaseUser == null) {
            onComplete(false, "Please sign in with your account to save profile changes.")
            return
        }

        val oldProfile = _profileState.value
        val oldUsername = oldProfile.username
        val cleanUsername = username.trim().ifEmpty { oldUsername }
        val cleanName = name.trim().ifEmpty { oldProfile.name }
        if (cleanUsername.isBlank() || cleanName.isBlank()) {
            onComplete(false, "Name and username cannot be empty.")
            return
        }

        viewModelScope.launch {
            try {
                // 1. If a local image (content://, file://, or file path) was selected, upload to
                // Firebase Storage first and retrieve the real https:// download URL.
                val finalRemotePhotoUrl = if (ProfilePhotoUtils.isLocalImageReference(profilePic)) {
                    val uploadResult = uploadProfileImageToFirebaseStorage(uid, profilePic)
                    if (uploadResult.isFailure) {
                        val errMsg = uploadResult.exceptionOrNull()?.localizedMessage
                            ?: "Failed to upload profile photo. Please try again."
                        onComplete(false, errMsg)
                        return@launch
                    }
                    uploadResult.getOrThrow()
                } else {
                    ProfilePhotoUtils.normalizeRemotePhotoUrl(profilePic)
                }

                // 2. Immediately update Cloud Firestore users/{uid} with photoURL, profilePic, and avatar.
                val profileLink = DeepLinkHelper.buildProfileUrl(uid)
                profileLinkByUid[uid] = profileLink
                val updates = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "username" to cleanUsername,
                    "bio" to bio.trim(),
                    "category" to category.trim(),
                    "photoURL" to finalRemotePhotoUrl,
                    "profilePic" to finalRemotePhotoUrl,
                    "avatar" to finalRemotePhotoUrl,
                    "email" to oldProfile.email,
                    "profileLink" to profileLink
                )

                withContext(Dispatchers.IO) {
                    val synced = withTimeoutOrNull(25_000L) {
                        awaitTask(db.collection("users").document(uid).set(updates, SetOptions.merge()))
                        true
                    } ?: false
                    if (!synced) {
                        throw Exception("Timed out while updating profile in Firestore. Please check your internet connection.")
                    }
                }

                // 3. Keep Firebase Auth user profile synced with displayName and https:// photoUrl.
                try {
                    val reqBuilder = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                    if (finalRemotePhotoUrl.isNotBlank()) {
                        reqBuilder.setPhotoUri(Uri.parse(finalRemotePhotoUrl))
                    }
                    awaitTask(currentFirebaseUser.updateProfile(reqBuilder.build()))
                } catch (_: Exception) {}

                // 4. Only after remote upload + Firestore write succeed, update local state & cached media.
                val updatedProfile = oldProfile.copy(
                    name = cleanName,
                    username = cleanUsername,
                    bio = bio.trim(),
                    category = category.trim(),
                    profilePic = finalRemotePhotoUrl
                )
                _profileState.value = updatedProfile
                saveProfileToPrefs(updatedProfile, uid)

                allUsers.value = allUsers.value.map { user ->
                    if (user.email.equals(updatedProfile.email, ignoreCase = true) ||
                        user.username.equals(oldUsername, ignoreCase = true) ||
                        user.username.equals(cleanUsername, ignoreCase = true)
                    ) {
                        updatedProfile
                    } else user
                }

                val hasNameOrAvatarOrUsernameChange = !oldUsername.equals(cleanUsername, ignoreCase = true) ||
                    oldProfile.name != cleanName ||
                    oldProfile.profilePic != finalRemotePhotoUrl

                if (hasNameOrAvatarOrUsernameChange) {
                    personalPhotosState.value = personalPhotosState.value.map {
                        if (it.creatorUsername.equals(oldUsername, ignoreCase = true) || it.creatorUid == uid) {
                            it.copy(
                                creatorUsername = cleanUsername,
                                creatorName = cleanName,
                                creatorAvatar = finalRemotePhotoUrl
                            )
                        } else it
                    }
                    savePersonalPhotosToPrefs()
                    _reelsState.value = _reelsState.value.map {
                        if (it.creatorUsername.equals(oldUsername, ignoreCase = true) || it.creatorUid == uid) {
                            it.copy(
                                creatorUsername = cleanUsername,
                                creatorName = cleanName,
                                creatorProfilePic = finalRemotePhotoUrl
                            )
                        } else it
                    }
                    saveReelsToPrefs()
                    allStories.value = allStories.value.map {
                        if (it.ownerUsername.equals(oldUsername, ignoreCase = true)) {
                            it.copy(ownerUsername = cleanUsername)
                        } else it
                    }
                    saveStoriesToPrefs()
                }
                recalculateCreatorStats()
                fetchAndSyncUserProfile()
                startListeningToNotifications()
                syncFcmToken()
                onComplete(true, null)
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "updateProfile failed: ${e.message}", e)
                val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Failed to save profile changes in Firestore. Please try again."
                onComplete(false, msg)
            }
        }
    }
    
    fun setAppPin(pin: String, isEnabled: Boolean) {
        val cleanPin = pin.trim()
        val hashedPin = if (isEnabled && cleanPin.isNotEmpty()) {
            if (SecurityUtils.isHashedPin(cleanPin)) cleanPin else SecurityUtils.hashPin(cleanPin)
        } else {
            ""
        }
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            db.collection("users").document(uid).set(
                mapOf("appPin" to hashedPin, "isPinEnabled" to isEnabled),
                SetOptions.merge()
            )
        }
        _settingsState.value = _settingsState.value.copy(appPin = hashedPin, isPinEnabled = isEnabled)
        val updatedProfile = _profileState.value.copy(appPin = hashedPin, isPinEnabled = isEnabled)
        _profileState.value = updatedProfile
        saveProfileToPrefs(updatedProfile, uid ?: activeUserUid())
    }

    fun setAppPin(pin: String) {
        setAppPin(pin, pin.isNotEmpty())
    }

    fun verifyAppPin(enteredPin: String): Boolean {
        val storedPin = _settingsState.value.appPin.ifBlank { _profileState.value.appPin }
        if (storedPin.isBlank()) return false
        val matched = SecurityUtils.verifyPin(enteredPin, storedPin)
        if (matched && !SecurityUtils.isHashedPin(storedPin)) {
            setAppPin(enteredPin, true)
        }
        return matched
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "Please sign in with your account first to change your password.")
            return
        }
        val hasPasswordProvider = user.providerData.any {
            it.providerId == EmailAuthProvider.PROVIDER_ID
        }
        val email = user.email?.trim().orEmpty()
        if (!hasPasswordProvider || email.isBlank()) {
            onResult(
                false,
                "Password change applies to email/password accounts. Google sign-in accounts manage passwords via Google Account."
            )
            return
        }
        if (currentPassword.isBlank()) {
            onResult(false, "Please enter your current password.")
            return
        }
        if (newPassword.length < 6) {
            onResult(false, "New password must be at least 6 characters.")
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        registeredPassword.value = newPassword
                        onResult(true, "Password updated successfully!")
                    }
                    .addOnFailureListener { updateErr ->
                        onResult(
                            false,
                            updateErr.localizedMessage ?: "Failed to update password. Please try again."
                        )
                    }
            }
            .addOnFailureListener { reauthErr ->
                val errorMessage = when (reauthErr) {
                    is FirebaseAuthInvalidCredentialsException -> "Current password is incorrect."
                    is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later."
                    else -> reauthErr.localizedMessage ?: "Re-authentication failed. Please check your current password."
                }
                onResult(false, errorMessage)
            }
    }

    fun unblockUser(id: Int, username: String) {
        unblockUser(username)
    }

    fun updateStoryPrivacy(hiddenUsernames: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        val hiddenStr = hiddenUsernames.joinToString(",")
        db.collection("users").document(uid).update("hiddenStoryFrom", hiddenStr).addOnSuccessListener {
            fetchAndSyncUserProfile()
        }
    }

    fun handleGoogleSignInSuccess(
        email: String,
        name: String,
        idToken: String?,
        photoUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedEmail = email.trim().lowercase()
        val defaultUsername = normalizedEmail.substringBefore("@").lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .ifEmpty { "user" }

        fun completeSignIn(uid: String) {
            val googleAuthPhotoUrl = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                photoURL = auth.currentUser?.photoUrl?.toString(),
                profilePic = photoUrl,
                avatar = null
            )
            val cachedProfile = loadProfileFromPrefs(uid, normalizedEmail)
            val initialProfile = (cachedProfile ?: ProfileEntity(
                id = uid.hashCode(),
                name = name.ifBlank { defaultUsername },
                username = defaultUsername,
                email = normalizedEmail,
                profilePic = googleAuthPhotoUrl
            )).let { base ->
                val resolvedCachedPhoto = ProfilePhotoUtils.normalizeRemotePhotoUrl(base.profilePic)
                    .ifBlank { googleAuthPhotoUrl }
                base.copy(
                    name = base.name.ifBlank { name.ifBlank { defaultUsername } },
                    username = base.username.ifBlank { defaultUsername },
                    email = base.email.ifBlank { normalizedEmail },
                    profilePic = resolvedCachedPhoto
                )
            }

            try {
                val userDocRef = db.collection("users").document(uid)
                userDocRef.get().addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        userDocRef.set(buildUserDocMap(initialProfile, uid), SetOptions.merge())
                            .addOnSuccessListener {
                                _profileState.value = initialProfile
                                myFollowersCount.value = initialProfile.followersCount
                                myFollowingCount.value = initialProfile.followingCount
                                monetizationStatus.value = normalizeMonetizationStatus(initialProfile.monetizationStatus)
                                saveProfileToPrefs(initialProfile, uid)
                                loadProfileVisitsFromPrefs()
                                recalculateCreatorStats()
                                loadFollowingFromFirestore()
                                startListeningToNotifications()
                                startListeningToAccountStatus()
                                startListeningToMonetizationStatus()
                                syncFcmToken()
                                refreshHomeFeed()
                                onSuccess()
                            }
                            .addOnFailureListener { writeErr ->
                                android.util.Log.e("YouSnapViewModel", "Google Sign-In create user doc failed: ${writeErr.message}", writeErr)
                                onError("Failed to save your Google profile to Firestore: ${writeErr.localizedMessage ?: "Please try again."}")
                            }
                    } else {
                        latestMonetizationUserDoc = doc
                        val remoteLink = doc.getString("profileLink").orEmpty()
                        val expectedLink = DeepLinkHelper.buildProfileUrl(uid)
                        profileLinkByUid[uid] = remoteLink.ifBlank { expectedLink }

                        val remoteProfile = extractUserProfileFromDoc(doc)
                        val existingRemotePhoto = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
                            photoURL = doc.getString("photoURL"),
                            profilePic = doc.getString("profilePic"),
                            avatar = doc.getString("avatar")
                        )
                        val finalPhotoUrl = existingRemotePhoto.ifBlank { googleAuthPhotoUrl }

                        val docPhotoUrl = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("photoURL"))
                        val docProfilePic = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("profilePic"))
                        val docAvatar = ProfilePhotoUtils.normalizeRemotePhotoUrl(doc.getString("avatar"))
                        val hasDirtyLocalAvatar =
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("photoURL")) ||
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("profilePic")) ||
                            ProfilePhotoUtils.isLocalImageReference(doc.getString("avatar")) ||
                            doc.getString("profilePic").equals("avatar_default", ignoreCase = true)

                        val needsAvatarSync =
                            (finalPhotoUrl.isNotBlank() && (docPhotoUrl != finalPhotoUrl || docProfilePic != finalPhotoUrl || docAvatar != finalPhotoUrl)) ||
                            hasDirtyLocalAvatar

                        val pendingSyncMap = mutableMapOf<String, Any>()
                        if (remoteLink.isBlank()) {
                            pendingSyncMap["profileLink"] = expectedLink
                        }
                        if (needsAvatarSync) {
                            pendingSyncMap.putAll(ProfilePhotoUtils.buildAvatarFieldsMap(finalPhotoUrl))
                        }

                        val resolvedMonetization = resolveMonetizationStatusFromDocs(doc, latestMonetizationRequestDocs)
                        val resolvedProfile = if (remoteProfile != null) {
                            remoteProfile.copy(
                                email = remoteProfile.email.ifBlank { normalizedEmail },
                                profilePic = finalPhotoUrl,
                                monetizationStatus = resolvedMonetization
                            )
                        } else {
                            initialProfile.copy(
                                profilePic = finalPhotoUrl,
                                monetizationStatus = resolvedMonetization
                            )
                        }

                        val finishLoginState = {
                            _profileState.value = resolvedProfile
                            myFollowersCount.value = resolvedProfile.followersCount
                            myFollowingCount.value = resolvedProfile.followingCount
                            monetizationStatus.value = resolvedMonetization
                            saveProfileToPrefs(resolvedProfile, uid)
                            loadProfileVisitsFromPrefs()
                            recalculateCreatorStats()
                            loadFollowingFromFirestore()
                            startListeningToNotifications()
                            startListeningToAccountStatus()
                            startListeningToMonetizationStatus()
                            syncFcmToken()
                            refreshHomeFeed()
                            onSuccess()
                        }

                        if (pendingSyncMap.isNotEmpty()) {
                            userDocRef.set(pendingSyncMap, SetOptions.merge())
                                .addOnSuccessListener { finishLoginState() }
                                .addOnFailureListener { syncErr ->
                                    android.util.Log.e("YouSnapViewModel", "Google Sign-In avatar Firestore sync failed: ${syncErr.message}", syncErr)
                                    onError("Failed to update your Google profile photo in Firestore: ${syncErr.localizedMessage ?: "Please try again."}")
                                }
                        } else {
                            finishLoginState()
                        }
                    }
                }.addOnFailureListener { getErr ->
                    android.util.Log.e("YouSnapViewModel", "Google Sign-In Firestore fetch failed: ${getErr.message}", getErr)
                    onError("Failed to connect to Firestore: ${getErr.localizedMessage ?: "Please check your internet connection and try again."}")
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Google Sign-In unexpected error: ${e.message}", e)
                onError("Google Sign-In failed: ${e.localizedMessage ?: "Please try again."}")
            }
        }

        if (!idToken.isNullOrBlank()) {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: auth.currentUser?.uid
                    if (uid.isNullOrBlank()) {
                        onError("Could not obtain your Firebase account ID. Please try again.")
                    } else {
                        completeSignIn(uid)
                    }
                }.addOnFailureListener { authErr ->
                    val existingUid = auth.currentUser?.takeIf {
                        it.email.equals(normalizedEmail, ignoreCase = true)
                    }?.uid
                    if (!existingUid.isNullOrBlank()) {
                        completeSignIn(existingUid)
                    } else {
                        onError("Firebase Google authentication failed: ${authErr.localizedMessage ?: "Please try again."}")
                    }
                }
            } catch (e: Throwable) {
                onError("Firebase Google authentication error: ${e.localizedMessage ?: "Please try again."}")
            }
        } else {
            val existingUid = auth.currentUser?.takeIf {
                it.email.equals(normalizedEmail, ignoreCase = true)
            }?.uid
            if (!existingUid.isNullOrBlank()) {
                completeSignIn(existingUid)
            } else {
                onError("Google Sign-In token is missing. Please try signing in again.")
            }
        }
    }

    fun signUpWithFirebase(
        name: String,
        email: String,
        pass: String,
        profilePhotoUri: Uri? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanName = name.trim()
        val normalizedEmail = email.trim().lowercase()
        val defaultUsername = normalizedEmail.substringBefore("@").lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .ifEmpty { "user" }

        viewModelScope.launch {
            try {
                val existingCurrent = auth.currentUser
                val firebaseUser = if (existingCurrent != null &&
                    existingCurrent.email.equals(normalizedEmail, ignoreCase = true)
                ) {
                    existingCurrent
                } else {
                    val authResult = awaitTask(auth.createUserWithEmailAndPassword(normalizedEmail, pass))
                    authResult.user ?: throw Exception("Account creation succeeded, but user session was unavailable.")
                }

                val uid = firebaseUser.uid
                val uploadedPhotoUrl = if (profilePhotoUri != null) {
                    val uploadResult = uploadProfileImageToFirebaseStorage(uid, profilePhotoUri.toString())
                    if (uploadResult.isFailure) {
                        val errMsg = uploadResult.exceptionOrNull()?.localizedMessage
                            ?: "Please try again."
                        onError("Account created, but profile photo upload failed: $errMsg")
                        return@launch
                    }
                    uploadResult.getOrThrow()
                } else {
                    ProfilePhotoUtils.normalizeRemotePhotoUrl(firebaseUser.photoUrl?.toString())
                }

                try {
                    val reqBuilder = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                    if (uploadedPhotoUrl.isNotBlank()) {
                        reqBuilder.setPhotoUri(Uri.parse(uploadedPhotoUrl))
                    }
                    awaitTask(firebaseUser.updateProfile(reqBuilder.build()))
                } catch (_: Exception) {}

                val profile = ProfileEntity(
                    id = uid.hashCode(),
                    name = cleanName,
                    username = defaultUsername,
                    email = normalizedEmail,
                    profilePic = uploadedPhotoUrl
                )

                withContext(Dispatchers.IO) {
                    val saved = withTimeoutOrNull(25_000L) {
                        awaitTask(
                            db.collection("users").document(uid)
                                .set(buildUserDocMap(profile, uid), SetOptions.merge())
                        )
                        true
                    } ?: false
                    if (!saved) {
                        throw Exception("Timed out while saving your profile to Firestore. Please try again.")
                    }
                }

                _profileState.value = profile
                saveProfileToPrefs(profile, uid)
                loadProfileVisitsFromPrefs()
                recalculateCreatorStats()
                loadFollowingFromFirestore()
                startListeningToNotifications()
                startListeningToAccountStatus()
                startListeningToMonetizationStatus()
                syncFcmToken()
                refreshHomeFeed()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "signUpWithFirebase failed: ${e.message}", e)
                onError(e.localizedMessage ?: "Sign up failed. Please try again.")
            }
        }
    }

    fun loginWithFirebase(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        auth.signInWithEmailAndPassword(normalizedEmail, pass).addOnSuccessListener { result ->
            val uid = result.user?.uid
            val cached = loadProfileFromPrefs(uid, normalizedEmail)
            if (cached != null) {
                _profileState.value = cached
                saveProfileToPrefs(cached, uid)
            } else if (uid != null) {
                val fallback = ProfileEntity(
                    id = uid.hashCode(),
                    name = normalizedEmail.substringBefore("@"),
                    username = normalizedEmail.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" },
                    email = normalizedEmail
                )
                _profileState.value = fallback
                saveProfileToPrefs(fallback, uid)
            }
            loadProfileVisitsFromPrefs()
            recalculateCreatorStats()
            fetchAndSyncUserProfile()
            loadFollowingFromFirestore()
            startListeningToNotifications()
            startListeningToAccountStatus()
            syncFcmToken()
            refreshHomeFeed()
            onSuccess()
        }.addOnFailureListener { onError(it.message ?: "Login failed") }
    }

    private fun markUrlDeleted(url: String) {
        if (url.isBlank()) return
        val current = prefs.getStringSet("deleted_media_urls", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url)
        val cleanUrl = url.substringBefore("?")
        if (cleanUrl.isNotBlank()) current.add(cleanUrl)
        extractSupabaseFilename(url)?.let { if (it.isNotBlank()) current.add(it) }
        prefs.edit().putStringSet("deleted_media_urls", current).apply()
    }

    fun savePersonalPhotosToPrefs() {
        val list = personalPhotosState.value
        val array = JSONArray()
        list.forEach { photo ->
            val obj = JSONObject()
            obj.put("id", photo.id)
            obj.put("title", photo.title)
            obj.put("imageUrl", photo.imageUrl)
            obj.put("creatorName", photo.creatorName)
            obj.put("creatorUsername", photo.creatorUsername)
            obj.put("creatorAvatar", photo.creatorAvatar)
            obj.put("creatorUid", photo.creatorUid)
            obj.put("initialLikes", photo.initialLikes)
            obj.put("initialComments", photo.initialComments)
            obj.put("sharesCount", photo.sharesCount)
            obj.put("initialIsLiked", photo.initialIsLiked)
            obj.put("timeAgo", photo.timeAgo)
            obj.put("firestoreDocId", photo.firestoreDocId ?: "")
            obj.put("shareLink", photo.shareLink)
            obj.put("isViral", photo.isViral)
            obj.put("viralDurationDays", photo.viralDurationDays)
            obj.put("viralDurationHours", photo.viralDurationHours)
            if (!photo.viralExpiresAt.isNullOrBlank()) {
                obj.put("viralExpiresAt", photo.viralExpiresAt)
            }
            obj.put("createdAtMillis", photo.createdAtMillis)
            array.put(obj)
        }
        prefs.edit().putString("personal_photos", array.toString()).apply()
    }

    fun loadPersonalPhotosFromPrefs() {
        val json = prefs.getString("personal_photos", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<FullScreenPhotoData>()
            val likedMap = mutableMapOf<String, Boolean>()
            val likesMap = mutableMapOf<String, Int>()
            val sharesMap = mutableMapOf<String, Int>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val docId = obj.optString("firestoreDocId", "").takeIf { it.isNotBlank() }
                val id = obj.getString("id")
                val shareLink = obj.optString("shareLink", "").ifBlank {
                    DeepLinkHelper.buildPostUrl(docId ?: id)
                }
                val initialLikes = obj.optInt("initialLikes", 0)
                val initialIsLiked = obj.optBoolean("initialIsLiked", false)
                val sharesCount = obj.optInt("sharesCount", 0)
                likedMap[id] = initialIsLiked
                likedMap["photo_$id"] = initialIsLiked
                likesMap[id] = initialLikes
                likesMap["photo_$id"] = initialLikes
                sharesMap[id] = sharesCount
                sharesMap["photo_$id"] = sharesCount
                list.add(FullScreenPhotoData(
                    id = id,
                    title = obj.optString("title", ""),
                    imageUrl = obj.optString("imageUrl", ""),
                    creatorName = obj.optString("creatorName", "User"),
                    creatorUsername = obj.optString("creatorUsername", "user"),
                    creatorAvatar = obj.optString("creatorAvatar", ""),
                    creatorUid = obj.optString("creatorUid", ""),
                    initialLikes = initialLikes,
                    initialComments = obj.optInt("initialComments", 0),
                    sharesCount = sharesCount,
                    initialIsLiked = initialIsLiked,
                    timeAgo = obj.optString("timeAgo", "Recently"),
                    firestoreDocId = docId,
                    shareLink = shareLink,
                    isViral = obj.optBoolean("isViral", false),
                    viralDurationDays = obj.optInt("viralDurationDays", 0),
                    viralDurationHours = obj.optInt("viralDurationHours", 0),
                    viralExpiresAt = obj.optString("viralExpiresAt", "").takeIf { it.isNotBlank() },
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                ))
            }
            personalPhotosState.value = FeedRankingAlgorithm.rankPhotos(list, System.currentTimeMillis())
            homePostLikedState.value = likedMap
            homePostLikesCount.value = likesMap
            homePostSharesCount.value = sharesMap
            scheduleNextViralExpirationCheck()
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error loading photos: ${e.message}")
        }
    }

    fun saveReelsToPrefs() {
        val list = _reelsState.value
        val array = JSONArray()
        list.forEach { reel ->
            val obj = JSONObject()
            obj.put("id", reel.id)
            obj.put("creatorName", reel.creatorName)
            obj.put("creatorUsername", reel.creatorUsername)
            obj.put("creatorProfilePic", reel.creatorProfilePic)
            obj.put("creatorUid", reel.creatorUid)
            obj.put("mediaUrl", reel.mediaUrl)
            obj.put("thumbnailUrl", reel.thumbnailUrl)
            obj.put("caption", reel.caption)
            obj.put("audioTitle", reel.audioTitle)
            obj.put("likesCount", reel.likesCount)
            obj.put("commentsCount", reel.commentsCount)
            obj.put("sharesCount", reel.sharesCount)
            obj.put("isLiked", reel.isLiked)
            obj.put("isFollowingCreator", reel.isFollowingCreator)
            obj.put("viewsCount", reel.viewsCount)
            obj.put("timestamp", reel.timestamp)
            obj.put("bgGradientStart", reel.bgGradientStart)
            obj.put("bgGradientEnd", reel.bgGradientEnd)
            obj.put("isViral", reel.isViral)
            obj.put("viralDurationDays", reel.viralDurationDays)
            obj.put("viralDurationHours", reel.viralDurationHours)
            if (!reel.viralExpiresAt.isNullOrBlank()) {
                obj.put("viralExpiresAt", reel.viralExpiresAt)
            }
            obj.put("createdAtMillis", reel.createdAtMillis)
            array.put(obj)
        }
        prefs.edit().putString("cached_reels", array.toString()).apply()
    }

    fun loadReelsFromPrefs() {
        val json = prefs.getString("cached_reels", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<ReelEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ReelEntity(
                    id = obj.optInt("id", i),
                    creatorName = obj.optString("creatorName", "User"),
                    creatorUsername = obj.optString("creatorUsername", "user"),
                    creatorProfilePic = obj.optString("creatorProfilePic", ""),
                    creatorUid = obj.optString("creatorUid", ""),
                    mediaUrl = obj.optString("mediaUrl", ""),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    caption = obj.optString("caption", ""),
                    audioTitle = obj.optString("audioTitle", "Original Audio"),
                    likesCount = obj.optInt("likesCount", 0),
                    commentsCount = obj.optInt("commentsCount", 0),
                    sharesCount = obj.optInt("sharesCount", 0),
                    isLiked = obj.optBoolean("isLiked", false),
                    isFollowingCreator = obj.optBoolean("isFollowingCreator", false),
                    viewsCount = obj.optInt("viewsCount", 0),
                    timestamp = obj.optLong("timestamp", 0L),
                    bgGradientStart = obj.optString("bgGradientStart", "#161616"),
                    bgGradientEnd = obj.optString("bgGradientEnd", "#161616"),
                    isViral = obj.optBoolean("isViral", false),
                    viralDurationDays = obj.optInt("viralDurationDays", 0),
                    viralDurationHours = obj.optInt("viralDurationHours", 0),
                    viralExpiresAt = obj.optString("viralExpiresAt", "").takeIf { it.isNotBlank() },
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                ))
            }
            _reelsState.value = FeedRankingAlgorithm.rankReels(list, System.currentTimeMillis())
            scheduleNextViralExpirationCheck()
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error loading reels: ${e.message}")
        }
    }

    private fun saveHomeCommentsToPrefs() {
        try {
            val root = JSONObject()
            homePostComments.value.forEach { (postKey, list) ->
                val arr = JSONArray()
                list.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("postId", c.postId)
                        put("username", c.username)
                        put("text", c.text)
                        put("timestamp", c.timestamp)
                    })
                }
                root.put(postKey, arr)
            }
            prefs.edit().putString("cached_home_comments", root.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.w("YouSnapViewModel", "saveHomeCommentsToPrefs notice: ${e.message}")
        }
    }

    private fun loadHomeCommentsFromPrefs() {
        val raw = prefs.getString("cached_home_comments", null) ?: return
        try {
            val root = JSONObject(raw)
            val map = mutableMapOf<String, List<CommentEntity>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val postKey = keys.next()
                val arr = root.optJSONArray(postKey) ?: continue
                val list = mutableListOf<CommentEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        CommentEntity(
                            id = o.optInt("id", i),
                            postId = o.optString("postId", postKey),
                            username = o.optString("username", "user"),
                            text = o.optString("text", ""),
                            timestamp = o.optLong("timestamp", 0L)
                        )
                    )
                }
                map[postKey] = list
            }
            homePostComments.value = map
        } catch (e: Exception) {
            android.util.Log.w("YouSnapViewModel", "loadHomeCommentsFromPrefs notice: ${e.message}")
        }
    }

    private fun saveReelCommentsToPrefs() {
        try {
            val root = JSONObject()
            reelCommentsByReelId.value.forEach { (reelId, list) ->
                val arr = JSONArray()
                list.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("reelId", c.reelId)
                        put("username", c.username)
                        put("text", c.text)
                        put("timestamp", c.timestamp)
                    })
                }
                root.put(reelId.toString(), arr)
            }
            prefs.edit().putString("cached_reel_comments", root.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.w("YouSnapViewModel", "saveReelCommentsToPrefs notice: ${e.message}")
        }
    }

    private fun loadReelCommentsFromPrefs() {
        val raw = prefs.getString("cached_reel_comments", null) ?: return
        try {
            val root = JSONObject(raw)
            val map = mutableMapOf<Int, List<CommentEntity>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val keyStr = keys.next()
                val reelId = keyStr.toIntOrNull() ?: continue
                val arr = root.optJSONArray(keyStr) ?: continue
                val list = mutableListOf<CommentEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        CommentEntity(
                            id = o.optInt("id", i),
                            reelId = o.optInt("reelId", reelId),
                            username = o.optString("username", "user"),
                            text = o.optString("text", ""),
                            timestamp = o.optLong("timestamp", 0L)
                        )
                    )
                }
                map[reelId] = list
            }
            reelCommentsByReelId.value = map
        } catch (e: Exception) {
            android.util.Log.w("YouSnapViewModel", "loadReelCommentsFromPrefs notice: ${e.message}")
        }
    }

    fun saveStoriesToPrefs() {
        val list = allStories.value
        val array = JSONArray()
        list.forEach { story ->
            val obj = JSONObject()
            obj.put("storyId", story.storyId)
            obj.put("ownerUsername", story.ownerUsername)
            obj.put("mediaUrl", story.mediaUrl)
            obj.put("mediaType", story.mediaType)
            obj.put("caption", story.caption)
            obj.put("bgGradientStart", story.bgGradientStart)
            obj.put("bgGradientEnd", story.bgGradientEnd)
            obj.put("timestamp", story.timestamp)
            obj.put("firestoreDocId", story.firestoreDocId)
            obj.put("isViral", story.isViral)
            obj.put("viralDurationDays", story.viralDurationDays)
            obj.put("viralDurationHours", story.viralDurationHours)
            if (!story.viralExpiresAt.isNullOrBlank()) {
                obj.put("viralExpiresAt", story.viralExpiresAt)
            }
            obj.put("createdAtMillis", story.createdAtMillis)
            array.put(obj)
        }
        prefs.edit().putString("cached_stories", array.toString()).apply()
    }

    fun loadStoriesFromPrefs() {
        val json = prefs.getString("cached_stories", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<StoryModel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(StoryModel(
                    storyId = obj.optString("storyId", ""),
                    ownerUsername = obj.optString("ownerUsername", "user"),
                    mediaUrl = obj.optString("mediaUrl", ""),
                    mediaType = obj.optString("mediaType", "image"),
                    caption = obj.optString("caption", ""),
                    bgGradientStart = obj.optString("bgGradientStart", "#E59424"),
                    bgGradientEnd = obj.optString("bgGradientEnd", "#C48B5E"),
                    timestamp = obj.optLong("timestamp", 0L),
                    firestoreDocId = obj.optString("firestoreDocId", ""),
                    isViral = obj.optBoolean("isViral", false),
                    viralDurationDays = obj.optInt("viralDurationDays", 0),
                    viralDurationHours = obj.optInt("viralDurationHours", 0),
                    viralExpiresAt = obj.optString("viralExpiresAt", "").takeIf { it.isNotBlank() },
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                ))
            }
            allStories.value = FeedRankingAlgorithm.rankStories(list, System.currentTimeMillis())
            scheduleNextViralExpirationCheck()
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error loading stories: ${e.message}")
        }
    }
}

@Keep
data class StoryModel(
    val storyId: String = "",
    val ownerUsername: String = "",
    val viewerIds: List<String> = emptyList(),
    val viewsCount: Int = 0,
    val likes: List<String> = emptyList(),
    val mediaUrl: String = "",
    val mediaType: String = "",
    val caption: String = "",
    val bgGradientStart: String = "",
    val bgGradientEnd: String = "",
    val timestamp: Long = 0L,
    val firestoreDocId: String = "",
    @get:com.google.firebase.firestore.PropertyName("isViral")
    @com.google.firebase.firestore.PropertyName("isViral")
    val isViral: Boolean = false,
    val viralDurationDays: Int = 0,
    val viralDurationHours: Int = 0,
    val viralExpiresAt: String? = null,
    val createdAtMillis: Long = 0L
)

data class ViewerProfile(
    val username: String = "",
    val name: String = "",
    val initials: String = "",
    val avatar: String = "",
    val hasLiked: Boolean = false
)

data class LoginDeviceItem(
    val id: String = "",
    val deviceName: String = "",
    val location: String = "",
    val timeAgo: String = ""
)

data class AccountRestrictionState(
    val isLocked: Boolean = false,
    val restrictionType: String = "Suspended",
    val reason: String = "",
    val appealSubmitted: Boolean = false,
    val isSubmittingAppeal: Boolean = false,
    val appealError: String? = null
)
