package com.example.ui

import android.app.Application
import android.net.Uri
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.utils.CompressionState
import com.example.ui.utils.VideoCompressor
import com.example.data.repository.SupabaseRepository
import com.example.data.repository.UploadResult
import com.example.data.repository.UploadTransferState
import com.example.notifications.YouSnapNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val homePostBookmarked = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isHomeRefreshing = MutableStateFlow(false)
    val userPrivacyMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val otherUserIsFollowing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isB2Uploading = MutableStateFlow(false)
    val b2UploadProgress = MutableStateFlow(0f)
    val uploadStatusMessage = MutableStateFlow("Preparing media...")
    val uploadErrorMessage = MutableStateFlow<String?>(null)
    val activeReelComments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val currentCommentText = MutableStateFlow("")
    private val recentlyUploadedUrls = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var refreshFeedJob: kotlinx.coroutines.Job? = null
    private val pendingSyncLock = Any()

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
    val monetizationStatus = MutableStateFlow("NOT_ELIGIBLE")
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
        try { loadProfileVisitsFromPrefs() } catch (e: Throwable) {}
        try { recalculateCreatorStats() } catch (e: Throwable) {}
        try { listenToAuthChanges() } catch (e: Throwable) {}
        try { fetchAndSyncUserProfile() } catch (e: Throwable) {}
        try { refreshHomeFeed() } catch (e: Throwable) {}
        try { fetchAllUsers() } catch (e: Throwable) {}
        try { loadSearchHistory() } catch (e: Throwable) {}
        try { loadFollowingFromFirestore() } catch (e: Throwable) {}
        try { startListeningToNotifications() } catch (e: Throwable) {}
        try { syncFcmToken() } catch (e: Throwable) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningToNotifications()
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
            monetizationStatus.value = currentStatus
        }
    }

    private fun loadSearchHistory() {
        try {
            val searches = prefs.getStringSet("recent_searches", emptySet())?.toList() ?: emptyList()
            recentSearchesFlow.value = searches
        } catch (e: Throwable) {}
    }

    fun fetchAllUsers() {
        try {
            db.collection("users").limit(100).get().addOnSuccessListener { snapshot ->
                allUsers.value = snapshot.documents.mapNotNull { it.toObject(ProfileEntity::class.java) }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "fetchAllUsers safe: ${e.message}")
        }
    }

    private fun listenToAuthChanges() {
        try {
            auth.addAuthStateListener { firebaseAuth ->
                currentUser.value = firebaseAuth.currentUser
                if (firebaseAuth.currentUser != null) {
                    fetchAndSyncUserProfile()
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
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("username", profile.username)
                put("bio", profile.bio)
                put("profilePic", profile.profilePic)
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
                    profilePic = obj.optString("profilePic", ""),
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
                    monetizationStatus = obj.optString("monetizationStatus", "NOT_ELIGIBLE")
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
            _profileState.value = cached
            myFollowersCount.value = cached.followersCount
            myFollowingCount.value = cached.followingCount
            monetizationStatus.value = cached.monetizationStatus.ifBlank { "NOT_ELIGIBLE" }
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
                monetizationStatus.value = cached.monetizationStatus.ifBlank { "NOT_ELIGIBLE" }
                loadProfileVisitsFromPrefs()
                recalculateCreatorStats()
            }
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val profile = doc.toObject(ProfileEntity::class.java)
                    if (profile != null) {
                        _profileState.value = profile
                        myFollowersCount.value = profile.followersCount
                        myFollowingCount.value = profile.followingCount
                        monetizationStatus.value = profile.monetizationStatus.ifBlank { "NOT_ELIGIBLE" }
                        saveProfileToPrefs(profile, uid)
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
        notifications.value = emptyList()
        unreadNotificationsCount.value = 0
        otherUserIsFollowing.value = emptyMap()
        currentUser.value = null
        _profileState.value = ProfileEntity(name = "User", username = "user")
        myFollowersCount.value = 0
        myFollowingCount.value = 0
        monetizationStatus.value = "NOT_ELIGIBLE"
        profileVisitsCount.value = 0
        recalculateCreatorStats()
        clearAndNavigateTo(Screen.Login)
    }

    fun hasOpenedPostFromProfile(): Boolean = false
    fun clearOpenedHomePost() {
        selectedHomePost.value = null
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
                val creatorUid = activeUserUid().orEmpty().ifBlank { safeOwner }
                val displayName = profile.name.ifBlank { ownerUsername }
                val creatorAvatar = profile.profilePic
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

                if (isStory) {
                    val newStory = StoryModel(
                        storyId = timestamp.toString(),
                        ownerUsername = ownerUsername,
                        mediaUrl = finalUrl,
                        mediaType = if (isVideo) "video" else "image",
                        caption = caption,
                        bgGradientStart = gradientStart,
                        bgGradientEnd = gradientEnd,
                        timestamp = timestamp
                    )
                    allStories.value = listOf(newStory) + allStories.value.filter { !it.ownerUsername.equals(ownerUsername, ignoreCase = true) }
                    saveStoriesToPrefs()
                } else if (!isVideo) {
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
                        firestoreDocId = firestoreDocId
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
                        "type" to "photo",
                        "userId" to creatorUid,
                        "creatorUid" to creatorUid,
                        "username" to ownerUsername,
                        "name" to displayName,
                        "creatorAvatar" to creatorAvatar,
                        "imageUrl" to finalUrl,
                        "mediaUrl" to finalUrl,
                        "caption" to caption,
                        "title" to caption,
                        "category" to resolvedCategory,
                        "timestamp" to timestamp,
                        "likesCount" to 0,
                        "commentsCount" to 0
                    )
                    else -> mapOf(
                        "id" to timestamp.toString(),
                        "reelIntId" to timestamp.toInt(),
                        "firestoreDocId" to firestoreDocId,
                        "type" to "reel",
                        "userId" to creatorUid,
                        "creatorUid" to creatorUid,
                        "username" to ownerUsername,
                        "name" to displayName,
                        "creatorAvatar" to creatorAvatar,
                        "mediaUrl" to finalUrl,
                        "thumbnailUrl" to "",
                        "audioTitle" to resolvedAudioTitle,
                        "category" to resolvedCategory,
                        "caption" to caption,
                        "timestamp" to timestamp,
                        "likesCount" to 0,
                        "commentsCount" to 0,
                        "viewsCount" to 0,
                        "bgGradientStart" to gradientStart,
                        "bgGradientEnd" to gradientEnd
                    )
                }

                val firestorePayload = basePayload + ("createdAt" to FieldValue.serverTimestamp())
                val firestoreSynced = syncDocumentToFirestoreWithRetry(
                    collection = targetCollection,
                    docId = firestoreDocId,
                    data = firestorePayload
                )
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

    private suspend fun flushPendingFirestoreDocs() {
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
        if (url.isBlank()) return null
        val isSupabaseUrl = url.contains(com.example.data.CdnConfig.PROJECT_ID, ignoreCase = true) ||
            url.contains("/storage/v1/object/", ignoreCase = true) ||
            url.contains("yousnap-cdn.sakinayesmin2203.workers.dev", ignoreCase = true)
        if (!isSupabaseUrl) return null
        val key = supabaseRepository.extractObjectKey(url)
        return key.takeIf { it.isNotBlank() }
    }

    private fun isUrlOrKeyDeleted(url: String, deletedSet: Set<String>): Boolean {
        if (url.isBlank()) return false
        if (url in deletedSet || url.substringBefore("?") in deletedSet) return true
        val key = extractSupabaseFilename(url)
        return key != null && key in deletedSet
    }

    fun refreshHomeFeed(showIndicator: Boolean = true, onComplete: () -> Unit = {}) {
        refreshFeedJob?.cancel()
        val launchedJob = viewModelScope.launch {
            if (showIndicator) {
                isHomeRefreshing.value = true
            }
            try {
                flushPendingFirestoreDocs()
                val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()

                val storiesSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("stories").limit(200).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore stories fetch notice: ${e.message}")
                        null
                    }
                }

                val postsSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("posts").limit(200).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore posts fetch notice: ${e.message}")
                        null
                    }
                }

                val reelsSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("reels").limit(200).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore reels fetch notice: ${e.message}")
                        null
                    }
                }

                val supabaseObjects = try {
                    supabaseRepository.listMediaFiles(limit = 1000)
                } catch (_: Exception) {
                    null
                }

                val activeSupabaseNames = supabaseObjects?.map { it.name }?.toSet().orEmpty()
                val supabaseOwnerByUrl = supabaseObjects
                    ?.filter { !it.ownerUsername.isNullOrBlank() }
                    ?.associate { it.publicUrl to it.ownerUsername!! }
                    .orEmpty()

                // Collect every Supabase media URL across Firestore + local caches to verify real-time existence
                val candidateUrls = buildSet {
                    storiesSnapshot?.documents?.forEach { doc ->
                        val url = com.example.data.CdnConfig.getFullMediaUrl(doc.getString("mediaUrl") ?: "")
                        if (url.isNotBlank()) add(url)
                    }
                    postsSnapshot?.documents?.forEach { doc ->
                        val url = com.example.data.CdnConfig.getFullMediaUrl(
                            doc.getString("imageUrl") ?: doc.getString("mediaUrl") ?: ""
                        )
                        if (url.isNotBlank()) add(url)
                    }
                    reelsSnapshot?.documents?.forEach { doc ->
                        val url = com.example.data.CdnConfig.getFullMediaUrl(doc.getString("mediaUrl") ?: "")
                        if (url.isNotBlank()) add(url)
                    }
                    allStories.value.forEach { if (it.mediaUrl.isNotBlank()) add(it.mediaUrl) }
                    personalPhotosState.value.forEach { if (it.imageUrl.isNotBlank()) add(it.imageUrl) }
                    _reelsState.value.forEach { if (it.mediaUrl.isNotBlank()) add(it.mediaUrl) }
                }.filter { url ->
                    val fname = extractSupabaseFilename(url)
                    fname != null &&
                        !isUrlOrKeyDeleted(url, deletedUrls) &&
                        fname !in activeSupabaseNames
                }

                // Verify directly against uncached Supabase URLs so RLS-restricted `list` returning []
                // NEVER deletes uploaded media, while files actually deleted in Supabase Storage ARE removed immediately.
                val existenceByUrl: Map<String, Boolean?> = if (candidateUrls.isEmpty()) {
                    emptyMap()
                } else {
                    withContext(Dispatchers.IO) {
                        withTimeoutOrNull(12000L) {
                            coroutineScope {
                                candidateUrls.map { url ->
                                    async {
                                        url to supabaseRepository.checkPublicFileExists(url)
                                    }
                                }.awaitAll().toMap()
                            }
                        }.orEmpty()
                    }
                }

                fun isMissingFromSupabase(url: String): Boolean {
                    if (url.isBlank()) return false
                    val fname = extractSupabaseFilename(url) ?: return false
                    if (fname in activeSupabaseNames) return false
                    return existenceByUrl[url] == false
                }

                // 1. Merge Stories (Firestore + Local/Cached + Public Supabase story_* fallback)
                val remoteStories = storiesSnapshot?.documents?.mapNotNull { doc ->
                    val story = doc.toObject(StoryModel::class.java) ?: return@mapNotNull null
                    val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(story.mediaUrl)
                    if (fixedUrl.isNotBlank() && isMissingFromSupabase(fixedUrl)) {
                        try { doc.reference.delete() } catch (_: Exception) {}
                        return@mapNotNull null
                    }
                    story.copy(mediaUrl = fixedUrl)
                }?.filter { it.mediaUrl.isBlank() || !isUrlOrKeyDeleted(it.mediaUrl, deletedUrls) }.orEmpty()

                val existingStories = allStories.value.filter {
                    (it.mediaUrl.isBlank() || (!isUrlOrKeyDeleted(it.mediaUrl, deletedUrls) && !isMissingFromSupabase(it.mediaUrl)))
                }
                val knownStoryUrls = (remoteStories.map { it.mediaUrl } + existingStories.map { it.mediaUrl })
                    .filter { it.isNotBlank() }.toSet()
                val knownStoryNames = knownStoryUrls.mapNotNull { extractSupabaseFilename(it) }.toSet()

                val fallbackStories = supabaseObjects.orEmpty()
                    .filter {
                        it.name.startsWith("story", ignoreCase = true) &&
                            !isUrlOrKeyDeleted(it.publicUrl, deletedUrls) &&
                            it.name !in deletedUrls &&
                            it.publicUrl !in knownStoryUrls &&
                            it.name !in knownStoryNames
                    }
                    .map { obj ->
                        val lower = obj.name.lowercase()
                        val isVid = lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                        val owner = obj.ownerUsername?.takeIf { it.isNotBlank() } ?: "user_${obj.timestamp % 10000}"
                        StoryModel(
                            storyId = obj.timestamp.takeIf { it > 0L }?.toString() ?: obj.name,
                            ownerUsername = owner,
                            mediaUrl = obj.publicUrl,
                            mediaType = if (isVid) "video" else "image",
                            timestamp = obj.timestamp
                        )
                    }

                val mergedStories = (remoteStories + existingStories + fallbackStories)
                    .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.ownerUsername}_${it.storyId}" }
                    .sortedByDescending { it.timestamp }
                allStories.value = mergedStories
                saveStoriesToPrefs()

                // 2. Merge Photos (Firestore + Local/Cached + Public Supabase image fallback)
                val remotePhotos = postsSnapshot?.documents?.mapNotNull { doc ->
                    val rawUrl = doc.getString("imageUrl") ?: doc.getString("mediaUrl") ?: ""
                    val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
                    if (fixedUrl.isNotBlank() && (isUrlOrKeyDeleted(fixedUrl, deletedUrls) || isMissingFromSupabase(fixedUrl))) {
                        if (isMissingFromSupabase(fixedUrl)) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                        }
                        return@mapNotNull null
                    }
                    val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
                    val id = doc.getString("id") ?: ts.takeIf { it > 0L }?.toString() ?: doc.id
                    val ownerFromUrl = supabaseOwnerByUrl[fixedUrl]
                    val uname = doc.getString("username")?.takeIf { it.isNotBlank() && it != "user" } ?: ownerFromUrl ?: "user"
                    val display = doc.getString("name")?.takeIf { it.isNotBlank() } ?: uname
                    val avatar = doc.getString("creatorAvatar") ?: ""
                    FullScreenPhotoData(
                        id = id,
                        title = doc.getString("caption") ?: doc.getString("title") ?: "",
                        imageUrl = fixedUrl,
                        creatorName = display,
                        creatorUsername = uname,
                        creatorAvatar = avatar,
                        creatorUid = doc.getString("creatorUid") ?: doc.getString("userId") ?: "",
                        initialLikes = (doc.getLong("likesCount") ?: 0).toInt(),
                        initialComments = (doc.getLong("commentsCount") ?: 0).toInt(),
                        initialIsLiked = false,
                        timeAgo = if (ts > 0L) "Recently" else "Just now",
                        firestoreDocId = doc.id
                    )
                }.orEmpty()

                val existingPhotos = personalPhotosState.value
                    .filter { it.imageUrl.isBlank() || (!isUrlOrKeyDeleted(it.imageUrl, deletedUrls) && !isMissingFromSupabase(it.imageUrl)) }
                    .map { photo ->
                        val betterOwner = supabaseOwnerByUrl[photo.imageUrl]
                        if ((photo.creatorUsername.isBlank() || photo.creatorUsername == "user") && !betterOwner.isNullOrBlank()) {
                            photo.copy(creatorUsername = betterOwner, creatorName = betterOwner)
                        } else photo
                    }
                val knownPhotoUrls = (remotePhotos.map { it.imageUrl } + existingPhotos.map { it.imageUrl })
                    .filter { it.isNotBlank() }.toSet()
                val knownPhotoNames = knownPhotoUrls.mapNotNull { extractSupabaseFilename(it) }.toSet()

                val fallbackPhotos = supabaseObjects.orEmpty()
                    .filter { obj ->
                        val lower = obj.name.lowercase()
                        !lower.startsWith("story") &&
                            (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) &&
                            !isUrlOrKeyDeleted(obj.publicUrl, deletedUrls) &&
                            obj.name !in deletedUrls &&
                            obj.publicUrl !in knownPhotoUrls &&
                            obj.name !in knownPhotoNames
                    }
                    .map { obj ->
                        val owner = obj.ownerUsername?.takeIf { it.isNotBlank() } ?: "user"
                        FullScreenPhotoData(
                            id = obj.timestamp.takeIf { it > 0L }?.toString() ?: obj.name,
                            title = "",
                            imageUrl = obj.publicUrl,
                            creatorName = if (owner == "user") "YouSnap User" else owner,
                            creatorUsername = owner,
                            creatorAvatar = "",
                            creatorUid = "",
                            initialLikes = 0,
                            initialComments = 0,
                            initialIsLiked = false,
                            timeAgo = "Recently",
                            firestoreDocId = null
                        )
                    }

                val mergedPhotos = (remotePhotos + existingPhotos + fallbackPhotos)
                    .distinctBy { if (it.imageUrl.isNotBlank()) it.imageUrl else it.id }
                    .sortedByDescending { it.id.toLongOrNull() ?: 0L }
                personalPhotosState.value = mergedPhotos
                savePersonalPhotosToPrefs()

                // 3. Merge Reels (Firestore + Local/Cached + Public Supabase video fallback)
                val existingReels = _reelsState.value
                    .filter { it.mediaUrl.isBlank() || (!isUrlOrKeyDeleted(it.mediaUrl, deletedUrls) && !isMissingFromSupabase(it.mediaUrl)) }
                    .map { reel ->
                        val betterOwner = supabaseOwnerByUrl[reel.mediaUrl]
                        if ((reel.creatorUsername.isBlank() || reel.creatorUsername == "user") && !betterOwner.isNullOrBlank()) {
                            reel.copy(creatorUsername = betterOwner, creatorName = betterOwner)
                        } else reel
                    }
                val existingThumbByUrl = existingReels
                    .filter { it.mediaUrl.isNotBlank() && it.thumbnailUrl.isNotBlank() }
                    .associate { it.mediaUrl to it.thumbnailUrl }
                val existingUidByUrl = existingReels
                    .filter { it.mediaUrl.isNotBlank() && it.creatorUid.isNotBlank() }
                    .associate { it.mediaUrl to it.creatorUid }

                val remoteReels = reelsSnapshot?.documents?.mapNotNull { doc ->
                    val rawUrl = doc.getString("mediaUrl") ?: ""
                    val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
                    if (fixedUrl.isNotBlank() && (isUrlOrKeyDeleted(fixedUrl, deletedUrls) || isMissingFromSupabase(fixedUrl))) {
                        if (isMissingFromSupabase(fixedUrl)) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                        }
                        return@mapNotNull null
                    }
                    val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
                    val reelId = doc.getLong("reelIntId")?.toInt()
                        ?: doc.getString("id")?.toLongOrNull()?.toInt()
                        ?: ts.takeIf { it > 0L }?.toInt()
                        ?: fixedUrl.hashCode()
                    val ownerFromUrl = supabaseOwnerByUrl[fixedUrl]
                    val uname = doc.getString("username")?.takeIf { it.isNotBlank() && it != "user" } ?: ownerFromUrl ?: "user"
                    val display = doc.getString("name")?.takeIf { it.isNotBlank() } ?: uname
                    val avatar = doc.getString("creatorAvatar") ?: ""
                    val audioTitle = doc.getString("audioTitle")?.takeIf { it.isNotBlank() } ?: "Original Audio"
                    ReelEntity(
                        id = reelId,
                        creatorName = display,
                        creatorUsername = uname,
                        creatorProfilePic = avatar,
                        creatorUid = doc.getString("creatorUid") ?: doc.getString("userId") ?: existingUidByUrl[fixedUrl] ?: "",
                        mediaUrl = fixedUrl,
                        thumbnailUrl = doc.getString("thumbnailUrl").orEmpty().ifBlank { existingThumbByUrl[fixedUrl] ?: "" },
                        caption = doc.getString("caption") ?: "",
                        audioTitle = audioTitle,
                        likesCount = (doc.getLong("likesCount") ?: 0).toInt(),
                        commentsCount = (doc.getLong("commentsCount") ?: 0).toInt(),
                        viewsCount = (doc.getLong("viewsCount") ?: 0).toInt(),
                        timestamp = ts,
                        bgGradientStart = doc.getString("bgGradientStart") ?: "#161616",
                        bgGradientEnd = doc.getString("bgGradientEnd") ?: "#161616"
                    )
                }.orEmpty()

                val knownReelUrls = (remoteReels.map { it.mediaUrl } + existingReels.map { it.mediaUrl })
                    .filter { it.isNotBlank() }.toSet()
                val knownReelNames = knownReelUrls.mapNotNull { extractSupabaseFilename(it) }.toSet()

                val fallbackReels = supabaseObjects.orEmpty()
                    .filter { obj ->
                        val lower = obj.name.lowercase()
                        !lower.startsWith("story") &&
                            (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm")) &&
                            !isUrlOrKeyDeleted(obj.publicUrl, deletedUrls) &&
                            obj.name !in deletedUrls &&
                            obj.publicUrl !in knownReelUrls &&
                            obj.name !in knownReelNames
                    }
                    .map { obj ->
                        val owner = obj.ownerUsername?.takeIf { it.isNotBlank() } ?: "user"
                        ReelEntity(
                            id = obj.timestamp.takeIf { it > 0L }?.toInt() ?: obj.publicUrl.hashCode(),
                            creatorName = if (owner == "user") "YouSnap User" else owner,
                            creatorUsername = owner,
                            creatorProfilePic = "",
                            creatorUid = existingUidByUrl[obj.publicUrl] ?: "",
                            mediaUrl = obj.publicUrl,
                            thumbnailUrl = existingThumbByUrl[obj.publicUrl] ?: "",
                            caption = "",
                            likesCount = 0,
                            commentsCount = 0,
                            viewsCount = 0,
                            timestamp = obj.timestamp,
                            bgGradientStart = "#161616",
                            bgGradientEnd = "#161616"
                        )
                    }

                val mergedReels = (remoteReels + existingReels + fallbackReels)
                    .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.id}_${it.timestamp}" }
                    .sortedByDescending { it.timestamp }
                _reelsState.value = mergedReels
                saveReelsToPrefs()
                recalculateCreatorStats()

                watchHistory.value = watchHistory.value.filter {
                    it.mediaUrl.isBlank() || (!isUrlOrKeyDeleted(it.mediaUrl, deletedUrls) && !isMissingFromSupabase(it.mediaUrl))
                }
                if (searchQuery.value.isNotBlank()) {
                    searchResultPosts.value = mergedReels.filter {
                        it.caption.contains(searchQuery.value, ignoreCase = true) ||
                            it.creatorUsername.contains(searchQuery.value, ignoreCase = true)
                    }
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

    fun loadCommentsForReel(id: Int) {
        activeCommentsReelId = id
        db.collection("reels_comments").whereEqualTo("reelId", id.toString()).get().addOnSuccessListener { snapshot ->
            val list = snapshot.documents.mapNotNull { it.toObject(CommentEntity::class.java) }
            activeReelComments.value = list
        }
    }

    fun toggleLikeHomePost(postId: String) {
        val isCurrentlyLiked = homePostLikedState.value[postId] ?: false
        val newLiked = !isCurrentlyLiked
        homePostLikedState.value = homePostLikedState.value + (postId to newLiked)
        val cleanId = postId.removePrefix("photo_")
        val targetPhoto = personalPhotosState.value.find { it.id == cleanId || it.id == postId }
        val baseCount = homePostLikesCount.value[postId] ?: targetPhoto?.initialLikes ?: 0
        val newCount = if (newLiked) baseCount + 1 else (baseCount - 1).coerceAtLeast(0)
        homePostLikesCount.value = homePostLikesCount.value + (postId to newCount)
        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == cleanId || p.id == postId) p.copy(initialLikes = newCount, initialIsLiked = newLiked) else p
        }
        savePersonalPhotosToPrefs()

        syncPostLikeToFirestore(targetPhoto, cleanId, newLiked)
    }

    fun notifyPhotoLikedFromViewer(photo: FullScreenPhotoData, isLiked: Boolean, newCount: Int) {
        val keys = listOf(photo.id, "photo_${photo.id}").distinct()
        homePostLikedState.value = homePostLikedState.value + keys.associateWith { isLiked }
        homePostLikesCount.value = homePostLikesCount.value + keys.associateWith { newCount }
        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == photo.id || p.imageUrl == photo.imageUrl) {
                p.copy(initialLikes = newCount, initialIsLiked = isLiked)
            } else p
        }
        savePersonalPhotosToPrefs()

        syncPostLikeToFirestore(photo, photo.id, isLiked)
    }

    private fun syncPostLikeToFirestore(targetPhoto: FullScreenPhotoData?, cleanId: String, newLiked: Boolean) {
        val myUid = activeUserUid().orEmpty().ifBlank { _profileState.value.username.ifBlank { "user" } }
        val docId = targetPhoto?.firestoreDocId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val delta = if (newLiked) 1L else -1L
                var updated = false
                if (!docId.isNullOrBlank()) {
                    awaitTask(
                        db.collection("posts").document(docId).update(
                            mapOf(
                                "likesCount" to FieldValue.increment(delta),
                                "likedBy" to if (newLiked) FieldValue.arrayUnion(myUid) else FieldValue.arrayRemove(myUid)
                            )
                        )
                    )
                    updated = true
                }
                if (!updated && cleanId.isNotBlank()) {
                    val snap = awaitTask(db.collection("posts").whereEqualTo("id", cleanId).limit(1).get())
                    for (doc in snap.documents) {
                        doc.reference.update(
                            mapOf(
                                "likesCount" to FieldValue.increment(delta),
                                "likedBy" to if (newLiked) FieldValue.arrayUnion(myUid) else FieldValue.arrayRemove(myUid)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Post like sync notice: ${e.message}")
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

    fun toggleBookmarkHomePost(postId: String): Boolean {
        val current = homePostBookmarked.value[postId] ?: false
        val new = !current
        homePostBookmarked.value = homePostBookmarked.value + (postId to new)
        return new
    }

    fun deletePhotoPost(postId: String, imageUrl: String, creatorUsername: String, creatorUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val cleanId = postId.replace("photo_", "")
            val targetPhoto = personalPhotosState.value.find {
                it.id == postId || it.id == cleanId || it.firestoreDocId == postId || (imageUrl.isNotEmpty() && it.imageUrl == imageUrl)
            }
            val effectiveUrl = imageUrl.ifBlank { targetPhoto?.imageUrl.orEmpty() }
            val targetDocId = targetPhoto?.firestoreDocId
            val targetKey = extractSupabaseFilename(effectiveUrl)

            // 1. Immediately remove from local state & mark deleted so UI & Insights update instantly
            if (effectiveUrl.isNotEmpty()) {
                markUrlDeleted(effectiveUrl)
                if (effectiveUrl.startsWith("/") || effectiveUrl.startsWith("file:", ignoreCase = true)) {
                    try { File(effectiveUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
                }
            }
            personalPhotosState.value = personalPhotosState.value.filter { photo ->
                val photoKey = extractSupabaseFilename(photo.imageUrl)
                photo.id != postId &&
                    photo.id != cleanId &&
                    (effectiveUrl.isEmpty() || photo.imageUrl != effectiveUrl) &&
                    (targetKey == null || photoKey != targetKey)
            }
            savePersonalPhotosToPrefs()
            recalculateCreatorStats()

            // 2. Delete from Supabase Storage and verify
            var supabaseDeleteResult = com.example.data.repository.DeleteResult(isSuccess = true)
            if (effectiveUrl.isNotEmpty() && targetKey != null) {
                supabaseDeleteResult = supabaseRepository.deleteFileDetailed(effectiveUrl)
            }

            // 3. Delete from Firestore (by docId, id, or imageUrl)
            try {
                if (!targetDocId.isNullOrEmpty()) {
                    awaitTask(db.collection("posts").document(targetDocId).delete())
                }
                val idCandidates = listOf(postId, cleanId).distinct().filter { it.isNotBlank() }
                if (idCandidates.isNotEmpty()) {
                    val byId = awaitTask(db.collection("posts").whereIn("id", idCandidates).get())
                    for (doc in byId.documents) {
                        try { awaitTask(doc.reference.delete()) } catch (_: Exception) {}
                    }
                }
                if (effectiveUrl.isNotEmpty()) {
                    val byUrl = awaitTask(db.collection("posts").whereEqualTo("imageUrl", effectiveUrl).get())
                    for (doc in byUrl.documents) {
                        try { awaitTask(doc.reference.delete()) } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Firestore photo delete notice: ${e.message}")
            }

            if (effectiveUrl.isNotEmpty() && !supabaseDeleteResult.isSuccess) {
                android.util.Log.w("YouSnapViewModel", "Supabase photo delete notice: ${supabaseDeleteResult.errorMessage}")
            }

            refreshHomeFeed(showIndicator = false)
            onSuccess()
        }
    }

    fun deleteReelVideo(reel: ReelEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val targetKey = extractSupabaseFilename(reel.mediaUrl)
            // 1. Remove immediately from local state & delete local thumbnail file
            if (reel.mediaUrl.isNotEmpty()) {
                markUrlDeleted(reel.mediaUrl)
                if (reel.mediaUrl.startsWith("/") || reel.mediaUrl.startsWith("file:", ignoreCase = true)) {
                    try { File(reel.mediaUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
                }
            }
            if (reel.thumbnailUrl.isNotBlank() && (reel.thumbnailUrl.startsWith("/") || reel.thumbnailUrl.startsWith("file:", ignoreCase = true))) {
                try { File(reel.thumbnailUrl.removePrefix("file://")).delete() } catch (_: Exception) {}
            }
            _reelsState.value = _reelsState.value.filter { item ->
                val itemKey = extractSupabaseFilename(item.mediaUrl)
                item.id != reel.id &&
                    (reel.mediaUrl.isEmpty() || item.mediaUrl != reel.mediaUrl) &&
                    (targetKey == null || itemKey != targetKey)
            }
            watchHistory.value = watchHistory.value.filter { item ->
                val itemKey = extractSupabaseFilename(item.mediaUrl)
                item.id != reel.id &&
                    (reel.mediaUrl.isEmpty() || item.mediaUrl != reel.mediaUrl) &&
                    (targetKey == null || itemKey != targetKey)
            }
            saveReelsToPrefs()
            recalculateCreatorStats()

            // 2. Delete file from Supabase storage and verify
            var supabaseDeleteResult = com.example.data.repository.DeleteResult(isSuccess = true)
            if (reel.mediaUrl.isNotEmpty() && targetKey != null) {
                supabaseDeleteResult = supabaseRepository.deleteFileDetailed(reel.mediaUrl)
            }

            // 3. Delete from Firestore collection "reels"
            try {
                val byStrId = awaitTask(db.collection("reels").whereEqualTo("id", reel.id.toString()).get())
                for (doc in byStrId.documents) {
                    try { awaitTask(doc.reference.delete()) } catch (_: Exception) {}
                }
                if (reel.timestamp > 0L) {
                    val byTsId = awaitTask(db.collection("reels").whereEqualTo("id", reel.timestamp.toString()).get())
                    for (doc in byTsId.documents) {
                        try { awaitTask(doc.reference.delete()) } catch (_: Exception) {}
                    }
                }
                if (reel.mediaUrl.isNotEmpty()) {
                    val byUrl = awaitTask(db.collection("reels").whereEqualTo("mediaUrl", reel.mediaUrl).get())
                    for (doc in byUrl.documents) {
                        try { awaitTask(doc.reference.delete()) } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Firestore reel delete notice: ${e.message}")
            }

            if (reel.mediaUrl.isNotEmpty() && !supabaseDeleteResult.isSuccess) {
                android.util.Log.w("YouSnapViewModel", "Supabase reel delete notice: ${supabaseDeleteResult.errorMessage}")
            }

            refreshHomeFeed(showIndicator = false)
            onComplete()
        }
    }

    fun addCommentToHomePost(postId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val user = profileState.value
        val cleanId = postId.removePrefix("photo_")
        val targetPhoto = personalPhotosState.value.find { it.id == cleanId || it.id == postId }
        val comment = CommentEntity(
            id = System.currentTimeMillis().toInt(),
            postId = postId,
            username = user.username,
            text = cleanText,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = homePostComments.value.toMutableMap()
        val currentList = currentMap[postId]?.toMutableList() ?: mutableListOf()
        currentList.add(comment)
        currentMap[postId] = currentList
        homePostComments.value = currentMap

        personalPhotosState.value = personalPhotosState.value.map { p ->
            if (p.id == cleanId || p.id == postId) p.copy(initialComments = p.initialComments + 1) else p
        }
        savePersonalPhotosToPrefs()

        val doc = mapOf(
            "postId" to postId,
            "username" to user.username,
            "text" to cleanText,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("comments").add(doc)

        val docId = targetPhoto?.firestoreDocId
        if (!docId.isNullOrBlank()) {
            db.collection("posts").document(docId).update("commentsCount", FieldValue.increment(1))
        } else if (cleanId.isNotBlank()) {
            db.collection("posts").whereEqualTo("id", cleanId).limit(1).get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.update("commentsCount", FieldValue.increment(1)) }
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
        val creatorUid = activeUserUid().orEmpty().ifBlank { ownerUsername }
        val timestamp = System.currentTimeMillis()
        val newStory = StoryModel(
            storyId = timestamp.toString(),
            ownerUsername = ownerUsername,
            mediaUrl = "",
            mediaType = "text",
            caption = text,
            bgGradientStart = gradientStart,
            bgGradientEnd = gradientEnd,
            timestamp = timestamp
        )
        allStories.value = listOf(newStory) + allStories.value.filter { !it.ownerUsername.equals(ownerUsername, ignoreCase = true) }
        saveStoriesToPrefs()
        recalculateCreatorStats()

        val baseStoryDoc = mapOf(
            "storyId" to timestamp.toString(),
            "firestoreDocId" to ownerUsername,
            "userId" to creatorUid,
            "creatorUid" to creatorUid,
            "username" to ownerUsername,
            "ownerUsername" to ownerUsername,
            "name" to displayName,
            "creatorAvatar" to profile.profilePic,
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
            val synced = syncDocumentToFirestoreWithRetry(
                collection = "stories",
                docId = ownerUsername,
                data = baseStoryDoc + ("createdAt" to FieldValue.serverTimestamp())
            )
            if (!synced) {
                savePendingFirestoreDoc("stories", ownerUsername, baseStoryDoc)
            }
            if (synced) {
                refreshHomeFeed(showIndicator = false)
            }
            onComplete()
        }
    }

    fun reportPost(postId: String, username: String, reason: String, details: String) {
        val report = mapOf(
            "postId" to postId,
            "reportedUsername" to username,
            "reason" to reason,
            "details" to details,
            "reporterUid" to (auth.currentUser?.uid ?: ""),
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("reports").add(report)
    }

    fun toggleLikeReel(reel: ReelEntity) {
        val newLiked = !reel.isLiked
        val newCount = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)
        _reelsState.value = _reelsState.value.map {
            if (it.id == reel.id) it.copy(isLiked = newLiked, likesCount = newCount) else it
        }
        saveReelsToPrefs()

        val uid = activeUserUid().orEmpty().ifBlank { _profileState.value.username.ifBlank { "user" } }
        val updateLikes: (com.google.firebase.firestore.DocumentSnapshot) -> Unit = { doc ->
            val likes = doc.get("likedBy") as? List<*> ?: emptyList<String>()
            if (likes.contains(uid)) {
                doc.reference.update("likedBy", FieldValue.arrayRemove(uid), "likesCount", FieldValue.increment(-1))
            } else {
                doc.reference.update("likedBy", FieldValue.arrayUnion(uid), "likesCount", FieldValue.increment(1))
            }
        }
        val query = if (reel.mediaUrl.isNotBlank()) {
            db.collection("reels").whereEqualTo("mediaUrl", reel.mediaUrl)
        } else {
            db.collection("reels").whereEqualTo("reelIntId", reel.id)
        }
        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                snapshot.documents.forEach(updateLikes)
            } else {
                db.collection("reels").whereEqualTo("id", reel.id.toString()).get().addOnSuccessListener { fallback ->
                    fallback.documents.forEach(updateLikes)
                }
            }
        }

        if (newLiked) {
            val safeSender = _profileState.value.username.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "user" }
            createAndPushFirestoreNotification(
                type = "like",
                recipientUsername = reel.creatorUsername,
                recipientUid = reel.creatorUid,
                detail = "liked your reel.",
                postId = reel.id.toString(),
                postMediaUrl = reel.mediaUrl,
                fixedDocId = "notif_like_reel_${safeSender}_${reel.id}"
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
        val query = if (reel.mediaUrl.isNotBlank()) {
            db.collection("reels").whereEqualTo("mediaUrl", reel.mediaUrl)
        } else {
            db.collection("reels").whereEqualTo("reelIntId", reel.id)
        }
        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    doc.reference.update("sharesCount", FieldValue.increment(1))
                }
            } else {
                db.collection("reels").whereEqualTo("id", reel.id.toString()).get().addOnSuccessListener { fallback ->
                    for (doc in fallback.documents) {
                        doc.reference.update("sharesCount", FieldValue.increment(1))
                    }
                }
            }
        }
    }

    fun addCommentToActiveReel() {
        val text = currentCommentText.value.trim()
        if (text.isEmpty()) return
        val user = profileState.value
        val reelId = activeCommentsReelId ?: selectedReelForPreview.value?.id ?: return
        val activeReel = _reelsState.value.find { it.id == reelId } ?: selectedReelForPreview.value
        val newComment = CommentEntity(
            id = System.currentTimeMillis().toInt(),
            reelId = reelId,
            username = user.username,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        activeReelComments.value = activeReelComments.value + newComment
        currentCommentText.value = ""
        _reelsState.value = _reelsState.value.map {
            if (it.id == reelId) it.copy(commentsCount = it.commentsCount + 1) else it
        }
        saveReelsToPrefs()

        val comment = mapOf(
            "reelId" to reelId.toString(),
            "username" to user.username,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("reels_comments").add(comment).addOnSuccessListener {
            loadCommentsForReel(reelId)
        }

        if (activeReel != null) {
            val query = if (activeReel.mediaUrl.isNotBlank()) {
                db.collection("reels").whereEqualTo("mediaUrl", activeReel.mediaUrl)
            } else {
                db.collection("reels").whereEqualTo("reelIntId", activeReel.id)
            }
            query.get().addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.update("commentsCount", FieldValue.increment(1)) }
            }

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
                if (snapshot != null && snapshot.exists()) {
                    val rawStory = snapshot.toObject(StoryModel::class.java)
                    if (rawStory != null) {
                        val normalized = normalizeStoryMedia(rawStory)
                        val latestLocal = allStories.value
                            .firstOrNull { it.ownerUsername.equals(username, ignoreCase = true) }
                            ?.let { normalizeStoryMedia(it) }
                        val resolved = if (latestLocal != null &&
                            latestLocal.timestamp > normalized.timestamp &&
                            latestLocal.mediaUrl.isNotBlank()
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
            val matchingStories = allStories.value.filter { it.ownerUsername.equals(username, ignoreCase = true) }
            var storyDeleteError: String? = null
            matchingStories.forEach { story ->
                if (story.mediaUrl.isNotBlank()) {
                    markUrlDeleted(story.mediaUrl)
                    val key = extractSupabaseFilename(story.mediaUrl)
                    if (key != null) {
                        val res = supabaseRepository.deleteFileDetailed(story.mediaUrl)
                        if (!res.isSuccess) {
                            storyDeleteError = res.errorMessage
                        }
                    }
                }
            }
            allStories.value = allStories.value.filterNot { it.ownerUsername.equals(username, ignoreCase = true) }
            saveStoriesToPrefs()
            recalculateCreatorStats()

            try {
                awaitTask(db.collection("stories").document(username).delete())
            } catch (e: Exception) {
                android.util.Log.w("YouSnapViewModel", "Firestore story delete notice: ${e.message}")
            }

            if (storyDeleteError != null) {
                android.util.Log.w("YouSnapViewModel", "Supabase story delete notice: $storyDeleteError")
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
            searchResultPosts.value = reelsState.value.filter { it.caption.contains(query, ignoreCase = true) || it.creatorUsername.contains(query, ignoreCase = true) }
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
    fun recordReelViewOnce(id: Int) {
        val matchedReel = _reelsState.value.find { it.id == id }
        _reelsState.value = _reelsState.value.map { reel ->
            if (reel.id == id) reel.copy(viewsCount = reel.viewsCount + 1) else reel
        }
        saveReelsToPrefs()
        recalculateCreatorStats()

        val query = if (matchedReel != null && matchedReel.mediaUrl.isNotBlank()) {
            db.collection("reels").whereEqualTo("mediaUrl", matchedReel.mediaUrl)
        } else {
            db.collection("reels").whereEqualTo("reelIntId", id)
        }
        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    doc.reference.update("viewsCount", FieldValue.increment(1))
                }
            } else {
                db.collection("reels").whereEqualTo("id", id.toString()).get().addOnSuccessListener { fallback ->
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
        db.collection("reel_reports").add(mapOf(
            "reelId" to reelId.toString(),
            "reason" to reason,
            "details" to details,
            "reporterUid" to (auth.currentUser?.uid ?: ""),
            "timestamp" to System.currentTimeMillis()
        ))
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
                        db.collection("users").document(uid).set(
                            mapOf(
                                "fcmToken" to token,
                                "fcmUpdatedAt" to System.currentTimeMillis(),
                                "username" to profile.username,
                                "name" to profile.name
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
                    "senderAvatar" to sender.profilePic,
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
        }

        if (myUsername.isNotBlank() && myUsername != "user") {
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

    fun reportStory(storyOwner: String, reason: String, details: String) {
        db.collection("story_reports").add(mapOf(
            "storyOwner" to storyOwner,
            "reason" to reason,
            "details" to details,
            "reporterUid" to (auth.currentUser?.uid ?: ""),
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun triggerStoryLikeNotification(username: String) {}

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
        updateMonetizationStatus("PENDING")
        val uid = activeUserUid() ?: return
        val data = mapOf(
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
            "email" to email,
            "country" to country,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("monetization_apps").document(uid).set(data)
    }

    fun updateMonetizationStatus(status: String) {
        monetizationStatus.value = status
        val updated = _profileState.value.copy(monetizationStatus = status)
        _profileState.value = updated
        val uid = activeUserUid()
        saveProfileToPrefs(updated, uid)
        if (!uid.isNullOrBlank()) {
            db.collection("users").document(uid).update("monetizationStatus", status)
        }
    }

    fun submitSupportTicket(subject: String, description: String) {
        db.collection("support_tickets").add(mapOf("subject" to subject, "description" to description, "timestamp" to System.currentTimeMillis()))
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

    fun updateProfile(name: String, username: String, bio: String, category: String, profilePic: String, onComplete: (Boolean) -> Unit) {
        val uid = activeUserUid()
        val oldUsername = _profileState.value.username
        val cleanUsername = username.trim().ifEmpty { oldUsername }
        val cleanName = name.trim().ifEmpty { _profileState.value.name }
        val updatedProfile = _profileState.value.copy(
            name = cleanName,
            username = cleanUsername,
            bio = bio,
            category = category,
            profilePic = profilePic
        )
        _profileState.value = updatedProfile
        saveProfileToPrefs(updatedProfile, uid)

        // If username changed, keep locally cached posts/reels/stories linked to the updated username
        if (!oldUsername.equals(cleanUsername, ignoreCase = true)) {
            personalPhotosState.value = personalPhotosState.value.map {
                if (it.creatorUsername.equals(oldUsername, ignoreCase = true) || (!uid.isNullOrBlank() && it.creatorUid == uid)) {
                    it.copy(creatorUsername = cleanUsername, creatorName = cleanName)
                } else it
            }
            savePersonalPhotosToPrefs()
            _reelsState.value = _reelsState.value.map {
                if (it.creatorUsername.equals(oldUsername, ignoreCase = true) || (!uid.isNullOrBlank() && it.creatorUid == uid)) {
                    it.copy(creatorUsername = cleanUsername, creatorName = cleanName)
                } else it
            }
            saveReelsToPrefs()
            allStories.value = allStories.value.map {
                if (it.ownerUsername.equals(oldUsername, ignoreCase = true)) it.copy(ownerUsername = cleanUsername) else it
            }
            saveStoriesToPrefs()
        }
        recalculateCreatorStats()

        if (uid.isNullOrBlank()) {
            onComplete(true)
            return
        }

        val updates = mapOf(
            "name" to cleanName,
            "username" to cleanUsername,
            "bio" to bio,
            "category" to category,
            "profilePic" to profilePic,
            "email" to updatedProfile.email
        )
        db.collection("users").document(uid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    fetchAndSyncUserProfile()
                    startListeningToNotifications()
                    syncFcmToken()
                }
                onComplete(true)
            }
    }
    
    fun setAppPin(pin: String, isEnabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("appPin", pin, "isPinEnabled", isEnabled)
        _settingsState.value = _settingsState.value.copy(appPin = pin, isPinEnabled = isEnabled)
        // Also update profile state for immediate UI feedback
        _profileState.value = _profileState.value.copy(isPinEnabled = isEnabled)
    }

    fun setAppPin(pin: String) {
        setAppPin(pin, pin.isNotEmpty())
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

    fun handleGoogleSignInSuccess(email: String, name: String, idToken: String?, photoUrl: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        val defaultUsername = normalizedEmail.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }
        val deterministicFallbackUid = "gmail_" + normalizedEmail.replace(Regex("[^a-z0-9]"), "_")

        val performLoginSuccess = { uid: String ->
            val cachedProfile = loadProfileFromPrefs(uid, normalizedEmail)
            val initialProfile = cachedProfile ?: ProfileEntity(
                id = uid.hashCode(),
                name = name.ifBlank { defaultUsername },
                username = defaultUsername,
                email = normalizedEmail,
                profilePic = photoUrl
            )
            // Apply cached/initial profile & active markers immediately before Firestore roundtrip
            _profileState.value = initialProfile
            myFollowersCount.value = initialProfile.followersCount
            myFollowingCount.value = initialProfile.followingCount
            monetizationStatus.value = initialProfile.monetizationStatus.ifBlank { "NOT_ELIGIBLE" }
            saveProfileToPrefs(initialProfile, uid)
            loadProfileVisitsFromPrefs()
            recalculateCreatorStats()

            try {
                val userDocRef = db.collection("users").document(uid)
                userDocRef.get().addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        userDocRef.set(initialProfile).addOnCompleteListener {
                            _profileState.value = initialProfile
                            saveProfileToPrefs(initialProfile, uid)
                            recalculateCreatorStats()
                            loadFollowingFromFirestore()
                            startListeningToNotifications()
                            syncFcmToken()
                            refreshHomeFeed()
                            onSuccess()
                        }
                    } else {
                        val remoteProfile = doc.toObject(ProfileEntity::class.java)
                        val resolvedProfile = if (remoteProfile != null) {
                            remoteProfile.copy(
                                email = remoteProfile.email.ifBlank { normalizedEmail },
                                profilePic = remoteProfile.profilePic.ifBlank { initialProfile.profilePic }
                            )
                        } else initialProfile
                        _profileState.value = resolvedProfile
                        myFollowersCount.value = resolvedProfile.followersCount
                        myFollowingCount.value = resolvedProfile.followingCount
                        monetizationStatus.value = resolvedProfile.monetizationStatus.ifBlank { "NOT_ELIGIBLE" }
                        saveProfileToPrefs(resolvedProfile, uid)
                        recalculateCreatorStats()
                        loadFollowingFromFirestore()
                        startListeningToNotifications()
                        syncFcmToken()
                        refreshHomeFeed()
                        onSuccess()
                    }
                }.addOnFailureListener {
                    loadFollowingFromFirestore()
                    startListeningToNotifications()
                    syncFcmToken()
                    refreshHomeFeed()
                    onSuccess()
                }
            } catch (e: Throwable) {
                loadFollowingFromFirestore()
                startListeningToNotifications()
                syncFcmToken()
                refreshHomeFeed()
                onSuccess()
            }
        }

        if (idToken != null) {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: auth.currentUser?.uid ?: deterministicFallbackUid
                    performLoginSuccess(uid)
                }.addOnFailureListener {
                    val existingUid = auth.currentUser?.takeIf {
                        it.email.equals(normalizedEmail, ignoreCase = true)
                    }?.uid ?: deterministicFallbackUid
                    performLoginSuccess(existingUid)
                }
            } catch (e: Throwable) {
                performLoginSuccess(deterministicFallbackUid)
            }
        } else {
            val existingUid = auth.currentUser?.takeIf {
                it.email.equals(normalizedEmail, ignoreCase = true)
            }?.uid ?: deterministicFallbackUid
            performLoginSuccess(existingUid)
        }
    }

    fun signUpWithFirebase(name: String, email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        val defaultUsername = normalizedEmail.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "user" }
        auth.createUserWithEmailAndPassword(normalizedEmail, pass).addOnSuccessListener { result ->
            val uid = result.user?.uid ?: return@addOnSuccessListener
            val profile = ProfileEntity(id = uid.hashCode(), name = name, username = defaultUsername, email = normalizedEmail)
            _profileState.value = profile
            saveProfileToPrefs(profile, uid)
            loadProfileVisitsFromPrefs()
            recalculateCreatorStats()
            db.collection("users").document(uid).set(profile).addOnCompleteListener {
                loadFollowingFromFirestore()
                startListeningToNotifications()
                syncFcmToken()
                refreshHomeFeed()
                onSuccess()
            }
        }.addOnFailureListener { onError(it.message ?: "Sign up failed") }
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
            obj.put("initialIsLiked", photo.initialIsLiked)
            obj.put("timeAgo", photo.timeAgo)
            obj.put("firestoreDocId", photo.firestoreDocId ?: "")
            array.put(obj)
        }
        prefs.edit().putString("personal_photos", array.toString()).apply()
    }

    fun loadPersonalPhotosFromPrefs() {
        val json = prefs.getString("personal_photos", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<FullScreenPhotoData>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val docId = obj.optString("firestoreDocId", "").takeIf { it.isNotBlank() }
                list.add(FullScreenPhotoData(
                    id = obj.getString("id"),
                    title = obj.optString("title", ""),
                    imageUrl = obj.optString("imageUrl", ""),
                    creatorName = obj.optString("creatorName", "User"),
                    creatorUsername = obj.optString("creatorUsername", "user"),
                    creatorAvatar = obj.optString("creatorAvatar", ""),
                    creatorUid = obj.optString("creatorUid", ""),
                    initialLikes = obj.optInt("initialLikes", 0),
                    initialComments = obj.optInt("initialComments", 0),
                    initialIsLiked = obj.optBoolean("initialIsLiked", false),
                    timeAgo = obj.optString("timeAgo", "Recently"),
                    firestoreDocId = docId
                ))
            }
            personalPhotosState.value = list
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
            obj.put("likesCount", reel.likesCount)
            obj.put("commentsCount", reel.commentsCount)
            obj.put("viewsCount", reel.viewsCount)
            obj.put("timestamp", reel.timestamp)
            obj.put("bgGradientStart", reel.bgGradientStart)
            obj.put("bgGradientEnd", reel.bgGradientEnd)
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
                    likesCount = obj.optInt("likesCount", 0),
                    commentsCount = obj.optInt("commentsCount", 0),
                    viewsCount = obj.optInt("viewsCount", 0),
                    timestamp = obj.optLong("timestamp", 0L),
                    bgGradientStart = obj.optString("bgGradientStart", "#161616"),
                    bgGradientEnd = obj.optString("bgGradientEnd", "#161616")
                ))
            }
            _reelsState.value = list
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error loading reels: ${e.message}")
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
                    timestamp = obj.optLong("timestamp", 0L)
                ))
            }
            allStories.value = list
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error loading stories: ${e.message}")
        }
    }
}

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
    val timestamp: Long = 0L
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
