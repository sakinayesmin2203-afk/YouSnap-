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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
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
    data class Reels(val startReelId: Int? = null) : Screen()
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
    
    // Monetization state
    val monetizationStatus = MutableStateFlow("NOT_ELIGIBLE")
    val videoViewsCount = MutableStateFlow(0)
    val photoUploadsCount = MutableStateFlow(0)
    val storiesUploadedCount = MutableStateFlow(0)

    val currentStoryState = MutableStateFlow<StoryModel?>(null)
    val searchQuery = MutableStateFlow("")
    val searchResultUsers = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val searchResultPosts = MutableStateFlow<List<ReelEntity>>(emptyList())

    private val backstack = Stack<Screen>()

    init {
        backstack.push(Screen.Splash)
        try {
            currentUser.value = auth.currentUser
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Safe init currentUser: ${e.message}")
        }
        try { loadPersonalPhotosFromPrefs() } catch (e: Throwable) {}
        try { loadReelsFromPrefs() } catch (e: Throwable) {}
        try { loadStoriesFromPrefs() } catch (e: Throwable) {}
        try { listenToAuthChanges() } catch (e: Throwable) {}
        try { fetchAndSyncUserProfile() } catch (e: Throwable) {}
        try { refreshHomeFeed() } catch (e: Throwable) {}
        try { fetchAllUsers() } catch (e: Throwable) {}
        try { loadSearchHistory() } catch (e: Throwable) {}
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

    fun fetchAndSyncUserProfile() {
        try {
            val uid = auth.currentUser?.uid ?: return
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val profile = doc.toObject(ProfileEntity::class.java)
                    if (profile != null) {
                        _profileState.value = profile
                        myFollowersCount.value = profile.followersCount
                        myFollowingCount.value = profile.followingCount
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "fetchAndSyncUserProfile safe: ${e.message}")
        }
    }

    fun proceedFromSplash() {
        try {
            if (auth.currentUser != null) {
                clearAndNavigateTo(Screen.Home)
            } else {
                clearAndNavigateTo(Screen.Login)
            }
        } catch (e: Throwable) {
            clearAndNavigateTo(Screen.Login)
        }
    }

    fun navigateTo(screen: Screen) {
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
        currentUser.value = null
        _profileState.value = ProfileEntity(name = "User", username = "user")
        myFollowersCount.value = 0
        myFollowingCount.value = 0
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
            val prefix = if (isStory) "story" else "yousnap"
            val filename = "${prefix}_${timestamp}.$extension"
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
                b2UploadProgress.value = 0.95f

                // Stage 3: Save Metadata Locally & Sync to Firestore (95% -> 100%)
                uploadStatusMessage.value = if (isStory) "Publishing story..." else "Publishing post..."
                val profile = profileState.value
                val ownerUsername = profile.username.ifEmpty { "user" }
                val creatorUid = auth.currentUser?.uid ?: ""

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
                    allStories.value = listOf(newStory) + allStories.value.filter { it.ownerUsername != ownerUsername }
                    saveStoriesToPrefs()
                } else if (!isVideo) {
                    val newPhoto = FullScreenPhotoData(
                        id = timestamp.toString(),
                        title = caption,
                        imageUrl = finalUrl,
                        creatorName = profile.name.ifEmpty { "User" },
                        creatorUsername = ownerUsername,
                        creatorAvatar = profile.profilePic,
                        creatorUid = creatorUid,
                        initialLikes = 0,
                        initialComments = 0,
                        initialIsLiked = false,
                        timeAgo = "Just now",
                        firestoreDocId = null
                    )
                    personalPhotosState.value = listOf(newPhoto) + personalPhotosState.value
                    savePersonalPhotosToPrefs()
                } else {
                    val newReel = ReelEntity(
                        id = timestamp.toInt(),
                        creatorName = profile.name.ifEmpty { "User" },
                        creatorUsername = ownerUsername,
                        creatorProfilePic = profile.profilePic,
                        mediaUrl = finalUrl,
                        caption = caption,
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

                var firestoreSynced = false
                try {
                    withTimeoutOrNull(15000L) {
                        if (isStory) {
                            val storyDoc = mapOf(
                                "storyId" to timestamp.toString(),
                                "ownerUsername" to ownerUsername,
                                "mediaUrl" to finalUrl,
                                "mediaType" to if (isVideo) "video" else "image",
                                "caption" to caption,
                                "timestamp" to timestamp,
                                "creatorUid" to creatorUid,
                                "viewerIds" to emptyList<String>(),
                                "viewsCount" to 0,
                                "likes" to emptyList<String>(),
                                "bgGradientStart" to gradientStart,
                                "bgGradientEnd" to gradientEnd
                            )
                            awaitTask(db.collection("stories").document(ownerUsername).set(storyDoc))
                        } else if (!isVideo) {
                            val postDoc = mapOf(
                                "id" to timestamp.toString(),
                                "type" to "photo",
                                "username" to ownerUsername,
                                "imageUrl" to finalUrl,
                                "caption" to caption,
                                "timestamp" to timestamp,
                                "creatorUid" to creatorUid,
                                "likesCount" to 0,
                                "commentsCount" to 0
                            )
                            awaitTask(db.collection("posts").add(postDoc))
                        } else {
                            val reelDoc = mapOf(
                                "id" to timestamp.toString(),
                                "type" to "reel",
                                "username" to ownerUsername,
                                "mediaUrl" to finalUrl,
                                "caption" to caption,
                                "timestamp" to timestamp,
                                "creatorUid" to creatorUid,
                                "likesCount" to 0,
                                "commentsCount" to 0,
                                "viewsCount" to 0,
                                "bgGradientStart" to gradientStart,
                                "bgGradientEnd" to gradientEnd
                            )
                            awaitTask(db.collection("reels").add(reelDoc))
                        }
                        firestoreSynced = true
                    }
                } catch (firestoreEx: Exception) {
                    android.util.Log.e("YouSnapViewModel", "Firestore sync warning: ${firestoreEx.message}")
                    val syncNote = if (auth.currentUser == null) {
                        "Uploaded! Sign in with Google & allow Firestore Rules for cloud sync."
                    } else {
                        "Uploaded! Allow write access in Firebase Console Firestore Rules for cloud sync."
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, syncNote, android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                b2UploadProgress.value = 1.0f
                uploadStatusMessage.value = "Upload complete!"
                delay(250L)

                isB2Uploading.value = false
                b2UploadProgress.value = 0f
                if (firestoreSynced) {
                    refreshHomeFeed()
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

    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result)
            }.addOnFailureListener { exception ->
                if (cont.isActive) cont.resumeWithException(exception)
            }
        }

    fun refreshHomeFeed(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            isHomeRefreshing.value = true
            try {
                val deletedUrls = prefs.getStringSet("deleted_media_urls", emptySet()) ?: emptySet()

                val storiesSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("stories").limit(100).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore stories fetch notice: ${e.message}")
                        null
                    }
                }

                val postsSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("posts").limit(100).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore posts fetch notice: ${e.message}")
                        null
                    }
                }

                val reelsSnapshot = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(8000L) {
                            awaitTask(db.collection("reels").limit(100).get())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("YouSnapViewModel", "Firestore reels fetch notice: ${e.message}")
                        null
                    }
                }

                val supabaseObjects = try {
                    supabaseRepository.listMediaFiles(limit = 100)
                } catch (_: Exception) {
                    emptyList()
                }

                // 1. Merge Stories (Firestore + Local/Cached + Public Supabase story_* fallback)
                val remoteStories = storiesSnapshot?.documents?.mapNotNull { doc ->
                    val story = doc.toObject(StoryModel::class.java) ?: return@mapNotNull null
                    val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(story.mediaUrl)
                    story.copy(mediaUrl = fixedUrl)
                }?.filter { it.mediaUrl.isBlank() || it.mediaUrl !in deletedUrls }.orEmpty()

                val existingStories = allStories.value.filter { it.mediaUrl.isBlank() || it.mediaUrl !in deletedUrls }
                val knownStoryUrls = (remoteStories.map { it.mediaUrl } + existingStories.map { it.mediaUrl })
                    .filter { it.isNotBlank() }.toSet()

                val fallbackStories = supabaseObjects
                    .filter { it.name.startsWith("story_", ignoreCase = true) && it.publicUrl !in deletedUrls && it.publicUrl !in knownStoryUrls }
                    .map { obj ->
                        val lower = obj.name.lowercase()
                        val isVid = lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                        StoryModel(
                            storyId = obj.timestamp.takeIf { it > 0L }?.toString() ?: obj.name,
                            ownerUsername = "user_${obj.timestamp % 10000}",
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
                    if (fixedUrl.isNotBlank() && fixedUrl in deletedUrls) return@mapNotNull null
                    val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
                    val id = doc.getString("id") ?: ts.takeIf { it > 0L }?.toString() ?: doc.id
                    FullScreenPhotoData(
                        id = id,
                        title = doc.getString("caption") ?: doc.getString("title") ?: "",
                        imageUrl = fixedUrl,
                        creatorName = doc.getString("username") ?: "User",
                        creatorUsername = doc.getString("username") ?: "user",
                        creatorAvatar = "",
                        creatorUid = doc.getString("creatorUid") ?: "",
                        initialLikes = (doc.getLong("likesCount") ?: 0).toInt(),
                        initialComments = (doc.getLong("commentsCount") ?: 0).toInt(),
                        initialIsLiked = false,
                        timeAgo = if (ts > 0L) "Recently" else "Just now",
                        firestoreDocId = doc.id
                    )
                }.orEmpty()

                val existingPhotos = personalPhotosState.value.filter { it.imageUrl.isBlank() || it.imageUrl !in deletedUrls }
                val knownPhotoUrls = (remotePhotos.map { it.imageUrl } + existingPhotos.map { it.imageUrl })
                    .filter { it.isNotBlank() }.toSet()

                val fallbackPhotos = supabaseObjects
                    .filter { obj ->
                        val lower = obj.name.lowercase()
                        !lower.startsWith("story_") &&
                            (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) &&
                            obj.publicUrl !in deletedUrls &&
                            obj.publicUrl !in knownPhotoUrls
                    }
                    .map { obj ->
                        FullScreenPhotoData(
                            id = obj.timestamp.takeIf { it > 0L }?.toString() ?: obj.name,
                            title = "",
                            imageUrl = obj.publicUrl,
                            creatorName = "YouSnap User",
                            creatorUsername = "user",
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
                val remoteReels = reelsSnapshot?.documents?.mapNotNull { doc ->
                    val rawUrl = doc.getString("mediaUrl") ?: ""
                    val fixedUrl = com.example.data.CdnConfig.getFullMediaUrl(rawUrl)
                    if (fixedUrl.isNotBlank() && fixedUrl in deletedUrls) return@mapNotNull null
                    val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
                    val reelId = doc.getString("id")?.toLongOrNull()?.toInt()
                        ?: ts.takeIf { it > 0L }?.toInt()
                        ?: fixedUrl.hashCode()
                    ReelEntity(
                        id = reelId,
                        creatorName = doc.getString("username") ?: "User",
                        creatorUsername = doc.getString("username") ?: "user",
                        creatorProfilePic = "",
                        mediaUrl = fixedUrl,
                        caption = doc.getString("caption") ?: "",
                        likesCount = (doc.getLong("likesCount") ?: 0).toInt(),
                        commentsCount = (doc.getLong("commentsCount") ?: 0).toInt(),
                        viewsCount = (doc.getLong("viewsCount") ?: 0).toInt(),
                        timestamp = ts,
                        bgGradientStart = doc.getString("bgGradientStart") ?: "#E1306C",
                        bgGradientEnd = doc.getString("bgGradientEnd") ?: "#F56040"
                    )
                }.orEmpty()

                val existingReels = _reelsState.value.filter { it.mediaUrl.isBlank() || it.mediaUrl !in deletedUrls }
                val knownReelUrls = (remoteReels.map { it.mediaUrl } + existingReels.map { it.mediaUrl })
                    .filter { it.isNotBlank() }.toSet()

                val fallbackReels = supabaseObjects
                    .filter { obj ->
                        val lower = obj.name.lowercase()
                        !lower.startsWith("story_") &&
                            (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm")) &&
                            obj.publicUrl !in deletedUrls &&
                            obj.publicUrl !in knownReelUrls
                    }
                    .map { obj ->
                        ReelEntity(
                            id = obj.timestamp.takeIf { it > 0L }?.toInt() ?: obj.publicUrl.hashCode(),
                            creatorName = "YouSnap User",
                            creatorUsername = "user",
                            creatorProfilePic = "",
                            mediaUrl = obj.publicUrl,
                            caption = "",
                            likesCount = 0,
                            commentsCount = 0,
                            viewsCount = 0,
                            timestamp = obj.timestamp,
                            bgGradientStart = "#E1306C",
                            bgGradientEnd = "#F56040"
                        )
                    }

                val mergedReels = (remoteReels + existingReels + fallbackReels)
                    .distinctBy { if (it.mediaUrl.isNotBlank()) it.mediaUrl else "${it.id}_${it.timestamp}" }
                    .sortedByDescending { it.timestamp }
                _reelsState.value = mergedReels
                saveReelsToPrefs()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "refreshHomeFeed error: ${e.message}")
            } finally {
                isHomeRefreshing.value = false
                onComplete()
            }
        }
    }

    fun loadCommentsForReel(id: Int) {
        db.collection("reels_comments").whereEqualTo("reelId", id.toString()).get().addOnSuccessListener { snapshot ->
            val list = snapshot.documents.mapNotNull { it.toObject(CommentEntity::class.java) }
            activeReelComments.value = list
        }
    }

    fun toggleLikeHomePost(postId: String) {
        val isCurrentlyLiked = homePostLikedState.value[postId] ?: false
        val newLiked = !isCurrentlyLiked
        homePostLikedState.value = homePostLikedState.value + (postId to newLiked)
        val currentCount = homePostLikesCount.value[postId] ?: 0
        val newCount = if (newLiked) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)
        homePostLikesCount.value = homePostLikesCount.value + (postId to newCount)
    }

    fun toggleBookmarkHomePost(postId: String): Boolean {
        val current = homePostBookmarked.value[postId] ?: false
        val new = !current
        homePostBookmarked.value = homePostBookmarked.value + (postId to new)
        return new
    }

    fun deletePhotoPost(postId: String, imageUrl: String, creatorUsername: String, creatorUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Delete from Supabase Storage
            if (imageUrl.isNotEmpty()) {
                markUrlDeleted(imageUrl)
                val filename = imageUrl.substringAfterLast("/")
                supabaseRepository.deleteFile(filename)
            }

            // Immediately remove from local state so UI updates instantly
            val targetDocId = personalPhotosState.value.find { it.id == postId }?.firestoreDocId
            personalPhotosState.value = personalPhotosState.value.filter { it.id != postId && (imageUrl.isEmpty() || it.imageUrl != imageUrl) }
            savePersonalPhotosToPrefs()

            // Delete from Firestore
            val cleanId = postId.replace("photo_", "")

            if (!targetDocId.isNullOrEmpty()) {
                db.collection("posts").document(targetDocId).delete().addOnCompleteListener {
                    refreshHomeFeed()
                    onSuccess()
                }
            } else {
                db.collection("posts").whereIn("id", listOf(postId, cleanId)).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                    refreshHomeFeed()
                    onSuccess()
                }.addOnFailureListener {
                    db.collection("posts").document(postId).delete().addOnCompleteListener {
                        refreshHomeFeed()
                        onSuccess()
                    }
                }
            }
        }
    }

    fun deleteReelVideo(reel: ReelEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            // 1. Delete file from Supabase storage
            if (reel.mediaUrl.isNotEmpty()) {
                markUrlDeleted(reel.mediaUrl)
                val filename = reel.mediaUrl.substringAfterLast("/")
                supabaseRepository.deleteFile(filename)
            }
            // 2. Remove immediately from local state
            _reelsState.value = _reelsState.value.filter { it.id != reel.id && (reel.mediaUrl.isEmpty() || it.mediaUrl != reel.mediaUrl) }
            saveReelsToPrefs()

            // 3. Delete from Firestore collection "reels"
            db.collection("reels").whereEqualTo("id", reel.id.toString()).get().addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                refreshHomeFeed()
                onComplete()
            }.addOnFailureListener {
                db.collection("reels").whereEqualTo("id", reel.id).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                    refreshHomeFeed()
                    onComplete()
                }.addOnFailureListener {
                    refreshHomeFeed()
                    onComplete()
                }
            }
        }
    }

    fun addCommentToHomePost(postId: String, text: String) {
        val user = profileState.value
        val comment = CommentEntity(
            id = System.currentTimeMillis().toInt(),
            postId = postId,
            username = user.username,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = homePostComments.value.toMutableMap()
        val currentList = currentMap[postId]?.toMutableList() ?: mutableListOf()
        currentList.add(comment)
        currentMap[postId] = currentList
        homePostComments.value = currentMap

        val doc = mapOf(
            "postId" to postId,
            "username" to user.username,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("comments").add(doc).addOnSuccessListener {
            refreshHomeFeed()
        }
    }

    fun uploadTextStory(text: String, gradientStart: String, gradientEnd: String, onComplete: () -> Unit) {
        val profile = profileState.value
        val timestamp = System.currentTimeMillis()
        val storyDoc = mapOf(
            "storyId" to timestamp.toString(),
            "ownerUsername" to profile.username,
            "mediaUrl" to "",
            "mediaType" to "text",
            "caption" to text,
            "timestamp" to timestamp,
            "creatorUid" to (auth.currentUser?.uid ?: ""),
            "bgGradientStart" to gradientStart,
            "bgGradientEnd" to gradientEnd,
            "viewerIds" to emptyList<String>(),
            "viewsCount" to 0,
            "likes" to emptyList<String>()
        )
        db.collection("stories").document(profile.username).set(storyDoc).addOnSuccessListener {
            onComplete()
            refreshHomeFeed()
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
        val uid = auth.currentUser?.uid ?: return
        db.collection("reels").whereEqualTo("id", reel.id.toString()).get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                val likes = doc.get("likedBy") as? List<String> ?: emptyList()
                if (likes.contains(uid)) {
                    doc.reference.update("likedBy", FieldValue.arrayRemove(uid), "likesCount", FieldValue.increment(-1))
                } else {
                    doc.reference.update("likedBy", FieldValue.arrayUnion(uid), "likesCount", FieldValue.increment(1))
                }
            }
        }
    }

    fun toggleFollowCreator(reel: ReelEntity) {
        val uid = auth.currentUser?.uid ?: return
        // Implement follow logic
    }

    fun incrementReelShares(reel: ReelEntity) {
        db.collection("reels").whereEqualTo("id", reel.id.toString()).get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                doc.reference.update("sharesCount", FieldValue.increment(1))
            }
        }
    }

    fun addCommentToActiveReel() {
        val text = currentCommentText.value.trim()
        if (text.isEmpty()) return
        val user = profileState.value
        val reelId = selectedReelForPreview.value?.id ?: return
        val comment = mapOf(
            "reelId" to reelId.toString(),
            "username" to user.username,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("reels_comments").add(comment).addOnSuccessListener {
            currentCommentText.value = ""
            loadCommentsForReel(reelId)
        }
    }

    fun startListeningToStory(username: String) {
        db.collection("stories").document(username).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val story = snapshot.toObject(StoryModel::class.java)
                if (story != null) {
                    allStories.value = allStories.value.filter { it.ownerUsername != username } + story
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
        db.collection("stories").document(username).delete().addOnSuccessListener {
            refreshHomeFeed()
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
    }
    fun toggleQuietMode() {
        val newVal = !quietModeEnabled.value
        quietModeEnabled.value = newVal
        _settingsState.value = _settingsState.value.copy(quietMode = newVal)
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
        db.collection("reels").whereEqualTo("id", id.toString()).get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                doc.reference.update("viewsCount", FieldValue.increment(1))
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
        _profileState.value = ProfileEntity(name = "Demo User", username = "demouser", email = "demo@yousnap.app")
        onSuccess()
    }

    fun followBackUser(notifId: String, username: String) {
        notifications.value = notifications.value.map {
            if (it.id == notifId) it.copy(isFollowingBack = true) else it
        }
    }
    fun followBackUser(notifId: Int, username: String) {
        followBackUser(notifId.toString(), username)
    }

    fun removeNotification(notifId: String) {
        notifications.value = notifications.value.filter { it.id != notifId }
    }
    fun removeNotification(notifId: Int) {
        removeNotification(notifId.toString())
    }

    fun terminateSession(id: String, deviceName: String) {
        loginDevices.value = loginDevices.value.filter { it.id != id }
    }

    fun recordStoryView(username: String) {
        val myUser = profileState.value.username
        db.collection("stories").document(username).update("viewerIds", FieldValue.arrayUnion(myUser), "viewsCount", FieldValue.increment(1))
    }

    fun stopListeningToStory() { currentStoryState.value = null }
    
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
        val uid = auth.currentUser?.uid ?: return
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
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("monetizationStatus", status)
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
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf("name" to name, "username" to username, "bio" to bio, "category" to category, "profilePic" to profilePic)
        db.collection("users").document(uid).update(updates).addOnCompleteListener { 
            if (it.isSuccessful) fetchAndSyncUserProfile()
            onComplete(it.isSuccessful) 
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
        val username = email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "")
        val performLoginSuccess = { uid: String ->
            try {
                val userDocRef = db.collection("users").document(uid)
                userDocRef.get().addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        val profile = ProfileEntity(id = uid.hashCode(), name = name, username = username.ifEmpty { "user" }, email = email, profilePic = photoUrl)
                        userDocRef.set(profile).addOnCompleteListener {
                            _profileState.value = profile
                            refreshHomeFeed()
                            onSuccess()
                        }
                    } else {
                        val profile = doc.toObject(ProfileEntity::class.java)
                        if (profile != null) {
                            _profileState.value = profile
                        }
                        refreshHomeFeed()
                        onSuccess()
                    }
                }.addOnFailureListener {
                    _profileState.value = ProfileEntity(name = name, username = username.ifEmpty { "user" }, email = email, profilePic = photoUrl)
                    refreshHomeFeed()
                    onSuccess()
                }
            } catch (e: Throwable) {
                _profileState.value = ProfileEntity(name = name, username = username.ifEmpty { "user" }, email = email, profilePic = photoUrl)
                refreshHomeFeed()
                onSuccess()
            }
        }

        if (idToken != null) {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: auth.currentUser?.uid ?: "google_${System.currentTimeMillis()}"
                    performLoginSuccess(uid)
                }.addOnFailureListener {
                    if (auth.currentUser != null) {
                        performLoginSuccess(auth.currentUser!!.uid)
                    } else {
                        auth.signInAnonymously().addOnSuccessListener { anonResult ->
                            val uid = anonResult.user?.uid ?: "google_${System.currentTimeMillis()}"
                            performLoginSuccess(uid)
                        }.addOnFailureListener {
                            performLoginSuccess("google_${System.currentTimeMillis()}")
                        }
                    }
                }
            } catch (e: Throwable) {
                performLoginSuccess("google_${System.currentTimeMillis()}")
            }
        } else {
            if (auth.currentUser != null) {
                performLoginSuccess(auth.currentUser!!.uid)
            } else {
                auth.signInAnonymously().addOnSuccessListener { anonResult ->
                    val uid = anonResult.user?.uid ?: "google_${System.currentTimeMillis()}"
                    performLoginSuccess(uid)
                }.addOnFailureListener {
                    performLoginSuccess("google_${System.currentTimeMillis()}")
                }
            }
        }
    }

    fun signUpWithFirebase(name: String, email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener { result ->
            val uid = result.user?.uid ?: return@addOnSuccessListener
            val profile = ProfileEntity(id = uid.hashCode(), name = name, username = email.substringBefore("@"), email = email)
            db.collection("users").document(uid).set(profile).addOnSuccessListener { onSuccess() }
        }.addOnFailureListener { onError(it.message ?: "Sign up failed") }
    }

    fun loginWithFirebase(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener { 
            fetchAndSyncUserProfile()
            refreshHomeFeed()
            onSuccess() 
        }.addOnFailureListener { onError(it.message ?: "Login failed") }
    }

    private fun markUrlDeleted(url: String) {
        if (url.isBlank()) return
        val current = prefs.getStringSet("deleted_media_urls", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url)
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
            obj.put("mediaUrl", reel.mediaUrl)
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
                    mediaUrl = obj.optString("mediaUrl", ""),
                    caption = obj.optString("caption", ""),
                    likesCount = obj.optInt("likesCount", 0),
                    commentsCount = obj.optInt("commentsCount", 0),
                    viewsCount = obj.optInt("viewsCount", 0),
                    timestamp = obj.optLong("timestamp", 0L),
                    bgGradientStart = obj.optString("bgGradientStart", "#E1306C"),
                    bgGradientEnd = obj.optString("bgGradientEnd", "#F56040")
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
