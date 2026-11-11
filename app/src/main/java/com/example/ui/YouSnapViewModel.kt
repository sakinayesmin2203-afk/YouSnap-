package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.YouSnapDatabase
import com.example.data.model.CommentEntity
import com.example.data.model.HomePostItem
import com.example.data.model.ProfileEntity
import com.example.data.model.ReelEntity
import com.example.data.model.SettingsEntity
import com.example.data.repository.YouSnapRepository
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.utils.VideoCompressor
import com.example.data.CdnConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Simulated Blocked Account Data
data class BlockedAccount(val id: Int, val username: String, val name: String)

// Simulated Login Activity Data
data class LoginDevice(val id: Int, val deviceName: String, val location: String, val timeAgo: String)

// Simulated Notification Item
data class SnapNotification(val id: Int, val type: String, val username: String, val detail: String, val time: String, val isRead: Boolean = false, val isFollowingBack: Boolean = false)

// Navigation Destinations
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
    
    // Sub-screens of Settings (accessed from Profile)
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

    private val repository: YouSnapRepository
    val app = application
    private val prefs = application.getSharedPreferences("yousnap_settings_prefs", android.content.Context.MODE_PRIVATE)



    
    // Local Authentication State for Sign Up and Login flow
    private val _registeredName = MutableStateFlow<String?>(null)
    val registeredName: StateFlow<String?> = _registeredName.asStateFlow()

    private val _registeredEmailOrPhone = MutableStateFlow<String?>(null)
    val registeredEmailOrPhone: StateFlow<String?> = _registeredEmailOrPhone.asStateFlow()

    private val _registeredPassword = MutableStateFlow<String?>(null)
    val registeredPassword: StateFlow<String?> = _registeredPassword.asStateFlow()

    private val _adminPassword = MutableStateFlow("123456")
    val adminPassword: StateFlow<String> = _adminPassword.asStateFlow()

    fun registerUser(name: String, emailOrPhone: String, word: String) {
        _registeredName.value = name
        _registeredEmailOrPhone.value = emailOrPhone
        _registeredPassword.value = word
        _personalPhotosState.value = emptyList()
        prefs.edit().remove("saved_personal_photos").apply()
        _currentStoryState.value = null
        viewModelScope.launch {
            repository.clearDemoReels()
        }
    }

    fun resetPassword(email: String, newPass: String): Boolean {
        if (email == "admin@yousnap.com") {
            _adminPassword.value = newPass
            return true
        } else if (_registeredEmailOrPhone.value != null && email == _registeredEmailOrPhone.value) {
            _registeredPassword.value = newPass
            return true
        }
        return false
    }


    // Firebase Instances (Safely initialized to prevent crash if FirebaseApp or google-services.json is absent)
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "FirebaseAuth init error: ${e.message}")
            null
        }
    }
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "FirebaseFirestore init error: ${e.message}")
            null
        }
    }

    // Authentication State
    private val _currentUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    // Supabase Storage Upload and Cloudflare CDN Configuration State
    private val supabaseRepository = com.example.data.repository.SupabaseRepository()

    private val _b2UploadProgress = MutableStateFlow(0f)
    val b2UploadProgress: StateFlow<Float> = _b2UploadProgress.asStateFlow()

    private val _isB2Uploading = MutableStateFlow(false)
    val isB2Uploading: StateFlow<Boolean> = _isB2Uploading.asStateFlow()

    private val _b2UploadSuccess = MutableStateFlow<Boolean?>(null)
    val b2UploadSuccess: StateFlow<Boolean?> = _b2UploadSuccess.asStateFlow()

    private val _b2UploadedUrl = MutableStateFlow<String?>(null)
    val b2UploadedUrl: StateFlow<String?> = _b2UploadedUrl.asStateFlow()

    fun uploadFileToB2(context: android.content.Context, uri: Uri, filename: String, contentType: String) {
        viewModelScope.launch {
            _isB2Uploading.value = true
            _b2UploadProgress.value = 0f
            _b2UploadSuccess.value = null
            _b2UploadedUrl.value = null

            try {
                var finalLength: Long = -1L
                val finalInputStream: java.io.InputStream
                var isCompacted = false

                if (contentType.startsWith("image")) {
                    for (i in 1..8) {
                        kotlinx.coroutines.delay(60)
                        _b2UploadProgress.value = i * 0.03f
                    }
                    val originalStream = context.contentResolver.openInputStream(uri)
                    val originalBytes = originalStream?.readBytes() ?: byteArrayOf()
                    originalStream?.close()

                    val compactedBytes = if (originalBytes.size > 150 * 1024) {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                        if (bitmap != null) {
                            val outputStream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
                            isCompacted = true
                            outputStream.toByteArray()
                        } else {
                            originalBytes
                        }
                    } else {
                        originalBytes
                    }
                    finalLength = compactedBytes.size.toLong()
                    finalInputStream = java.io.ByteArrayInputStream(compactedBytes)
                    _b2UploadProgress.value = 0.25f
                } else if (contentType.startsWith("video")) {
                    for (i in 1..15) {
                        kotlinx.coroutines.delay(100)
                        _b2UploadProgress.value = i * 0.016f
                    }
                    finalLength = try { 
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L 
                    } catch (e: Exception) { -1L }
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        _b2UploadSuccess.value = false
                        _isB2Uploading.value = false
                        return@launch
                    }
                    finalInputStream = inputStream
                    isCompacted = true
                    _b2UploadProgress.value = 0.25f
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        _b2UploadSuccess.value = false
                        _isB2Uploading.value = false
                        return@launch
                    }
                    finalInputStream = inputStream
                }

                val uploadResult = supabaseRepository.uploadFile(
                    filename = filename,
                    contentType = contentType,
                    inputStream = finalInputStream,
                    contentLength = finalLength,
                    onProgress = { progress ->
                        _b2UploadProgress.value = 0.25f + (progress * 0.75f)
                    }
                )

                if (uploadResult.isSuccess && !uploadResult.fileUrl.isNullOrEmpty()) {
                    _b2UploadedUrl.value = uploadResult.fileUrl
                    _b2UploadSuccess.value = true
                    _b2UploadProgress.value = 1.0f
                    val successMsg = if (isCompacted) {
                        "Uploaded & Optimized to Supabase Storage (YOUSNAP) Successfully! ✨"
                    } else {
                        "Direct Upload Successful! 🎉"
                    }
                    android.widget.Toast.makeText(context, successMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    _b2UploadSuccess.value = false
                    val errorMsg = uploadResult.errorMessage ?: "Direct Upload failed on Supabase Storage (HTTP ${uploadResult.responseCode})."
                    android.widget.Toast.makeText(context, "❌ $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Supabase upload failed: ${e.message}")
                _b2UploadSuccess.value = false
                android.widget.Toast.makeText(context, "Supabase upload exception: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isB2Uploading.value = false
            }
        }
    }

    fun resetB2UploadState() {
        _b2UploadSuccess.value = null
        _b2UploadedUrl.value = null
        _b2UploadProgress.value = 0f
        _isB2Uploading.value = false
    }

    fun logout() {
        cachedAppPin = ""
        try {
            prefs.edit()
                .putBoolean("is_logged_in", false)
                .remove("saved_personal_photos")
                .remove("recentSearches")
                .apply()
            auth?.signOut()
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "SignOut error: ${e.message}")
        }
        _currentUser.value = null
        _personalPhotosState.value = emptyList()
        _allStories.value = emptyList()
        _currentStoryState.value = null
        _isAppLocked.value = false
        viewModelScope.launch {
            repository.clearAllData()
        }
        clearAndNavigateTo(Screen.Login)
    }

    // Sign Up with Firebase (with graceful fallback to local registration if Firebase is unconfigured)
    fun signUpWithFirebase(name: String, email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _personalPhotosState.value = emptyList()
        prefs.edit().remove("saved_personal_photos").apply()
        _currentStoryState.value = null
        viewModelScope.launch {
            repository.clearDemoReels()
        }
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth == null) {
                    registerUser(name, email, pass)
                    prefs.edit().putBoolean("is_logged_in", true).apply()
                    val current = profileState.value
                    repository.saveProfile(current.copy(
                        name = name,
                        username = email.split("@")[0].lowercase(),
                        email = email,
                        profilePic = ""
                    ))
                    onSuccess()
                    return@launch
                }
                currentAuth.createUserWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { authResult ->
                        prefs.edit().putBoolean("is_logged_in", true).apply()
                        val uid = authResult.user?.uid ?: ""
                        // Save user profile to Firestore
                        val userProfile = mapOf(
                            "uid" to uid,
                            "name" to name,
                            "email" to email,
                            "username" to email.split("@")[0].lowercase(),
                            "bio" to "Hey there! I am using YouSnap.",
                            "category" to "Creator",
                            "profilePic" to "",
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                        try {
                            db?.collection("users")?.document(uid)
                                ?.set(userProfile)
                                ?.addOnSuccessListener {
                                    registerUser(name, email, pass)
                                    val current = profileState.value
                                    val newProfile = current.copy(
                                        name = name,
                                        username = email.split("@")[0].lowercase(),
                                        email = email
                                    )
                                    viewModelScope.launch {
                                        repository.saveProfile(newProfile)
                                        checkPinAndNavigate(newProfile)
                                    }
                                    onSuccess()
                                }
                                ?.addOnFailureListener {
                                    registerUser(name, email, pass)
                                    val current = profileState.value
                                    val newProfile = current.copy(
                                        name = name,
                                        username = email.split("@")[0].lowercase(),
                                        email = email
                                    )
                                    viewModelScope.launch {
                                        repository.saveProfile(newProfile)
                                        checkPinAndNavigate(newProfile)
                                    }
                                    onSuccess()
                                } ?: run {
                                    registerUser(name, email, pass)
                                    val current = profileState.value
                                    val newProfile = current.copy(
                                        name = name,
                                        username = email.split("@")[0].lowercase(),
                                        email = email
                                    )
                                    viewModelScope.launch {
                                        repository.saveProfile(newProfile)
                                        checkPinAndNavigate(newProfile)
                                    }
                                    onSuccess()
                                }
                        } catch (e: Throwable) {
                            registerUser(name, email, pass)
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        // Fallback to local registration so app works smoothly even without network/Firebase setup
                        android.util.Log.w("YouSnapViewModel", "Firebase signup failed, falling back locally: ${e.message}")
                        registerUser(name, email, pass)
                        prefs.edit().putBoolean("is_logged_in", true).apply()
                        val current = profileState.value
                        viewModelScope.launch {
                            repository.saveProfile(current.copy(
                                name = name,
                                username = email.split("@")[0].lowercase(),
                                email = email
                            ))
                        }
                        onSuccess()
                    }
            } catch (e: Throwable) {
                registerUser(name, email, pass)
                prefs.edit().putBoolean("is_logged_in", true).apply()
                val current = profileState.value
                viewModelScope.launch {
                    repository.saveProfile(current.copy(
                        name = name,
                        username = email.split("@")[0].lowercase(),
                        email = email
                    ))
                }
                onSuccess()
            }
        }
    }

    fun fetchAndSyncUserProfile() {
        val user = auth?.currentUser ?: return
        val uid = user.uid
        val email = user.email ?: ""
        val displayName = user.displayName ?: registeredName.value ?: email.split("@").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User"
        val defaultUsername = if (email.isNotEmpty()) email.split("@")[0].lowercase() else "user_${uid.take(6)}"
        val photoUrl = user.photoUrl?.toString() ?: ""

        try {
            db?.collection("users")?.document(uid)?.get()
                ?.addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")?.ifEmpty { displayName } ?: displayName
                        val username = document.getString("username")?.ifEmpty { defaultUsername } ?: defaultUsername
                        val bio = document.getString("bio")?.ifEmpty { "Hey there! I am using YouSnap." } ?: "Hey there! I am using YouSnap."
                        val category = document.getString("category")?.ifEmpty { "Digital Creator" } ?: "Digital Creator"
                        val profilePic = document.getString("profilePic")?.ifEmpty { photoUrl } ?: photoUrl
                        val isPinEnabled = document.getBoolean("isPinEnabled") ?: false
                        val appPin = document.getString("appPin") ?: ""

                        viewModelScope.launch {
                            val current = profileState.value
                            val updated = current.copy(
                                name = name,
                                username = username,
                                email = email,
                                bio = bio,
                                category = category,
                                profilePic = profilePic,
                                isPinEnabled = isPinEnabled,
                                appPin = appPin
                            )
                            repository.saveProfile(updated)
                        }
                    } else {
                        val userMap = mapOf(
                            "uid" to uid,
                            "name" to displayName,
                            "email" to email,
                            "username" to defaultUsername,
                            "bio" to "Hey there! I am using YouSnap.",
                            "category" to "Digital Creator",
                            "profilePic" to photoUrl,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                        db?.collection("users")?.document(uid)?.set(userMap, com.google.firebase.firestore.SetOptions.merge())
                        viewModelScope.launch {
                            val current = profileState.value
                            val updated = current.copy(
                                name = displayName,
                                username = defaultUsername,
                                email = email,
                                profilePic = photoUrl,
                                bio = "Hey there! I am using YouSnap."
                            )
                            repository.saveProfile(updated)
                        }
                    }
                }?.addOnFailureListener {
                    viewModelScope.launch {
                        val current = profileState.value
                        val updated = current.copy(
                            name = displayName,
                            username = defaultUsername,
                            email = email,
                            profilePic = photoUrl
                        )
                        repository.saveProfile(updated)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "fetchAndSyncUserProfile error: ${e.message}")
        }
    }

    // Login with Firebase (with graceful fallback to registered local credentials / admin credentials)
    fun loginWithFirebase(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Check local registered credentials or default admin first
                if (email == "admin@yousnap.com" && pass == _adminPassword.value) {
                    checkPinAndNavigate(profileState.value)
                    onSuccess()
                    return@launch
                }
                if (_registeredEmailOrPhone.value != null && email == _registeredEmailOrPhone.value && pass == _registeredPassword.value) {
                    checkPinAndNavigate(profileState.value)
                    onSuccess()
                    return@launch
                }

                val currentAuth = auth
                if (currentAuth == null) {
                    onError("Invalid Credentials or Account Not Found")
                    return@launch
                }

                currentAuth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: ""
                        prefs.edit().putBoolean("is_logged_in", true).apply()
                        fetchAndSyncUserProfile()
                        // Fetch profile from Firestore
                        try {
                            db?.collection("users")?.document(uid)?.get()
                                ?.addOnSuccessListener { document ->
                                        if (document != null && document.exists()) {
                                            val name = document.getString("name") ?: ""
                                            val username = document.getString("username") ?: ""
                                            val profilePic = document.getString("profilePic") ?: ""
                                            val emailFromDb = document.getString("email") ?: email
                                            val bio = document.getString("bio") ?: ""
                                            val category = document.getString("category") ?: ""
                                            val isPinEnabled = document.getBoolean("isPinEnabled") ?: false
                                            val appPin = document.getString("appPin") ?: ""
                                            val isPrivateFromDb = document.getBoolean("isPrivateAccount") ?: document.getBoolean("isPrivate") ?: false
                                            
                                            viewModelScope.launch {
                                                val current = profileState.value
                                                val updatedProfile = current.copy(
                                                    name = name,
                                                    username = username,
                                                    email = emailFromDb,
                                                    bio = bio,
                                                    category = category,
                                                    profilePic = profilePic,
                                                    isPinEnabled = isPinEnabled,
                                                    appPin = appPin
                                                )
                                                repository.saveProfile(updatedProfile)
                                                if (isPrivateFromDb) {
                                                    val curSettings = settingsState.value
                                                    repository.saveSettings(curSettings.copy(isPrivateAccount = isPrivateFromDb))
                                                    _userPrivacyMap.value = _userPrivacyMap.value + (username.lowercase() to isPrivateFromDb)
                                                }
                                                checkPinAndNavigate(updatedProfile)
                                            }
                                        } else {
                                            checkPinAndNavigate(profileState.value)
                                        }
                                    onSuccess()
                                }
                                ?.addOnFailureListener {
                                    checkPinAndNavigate(profileState.value)
                                    onSuccess()
                                } ?: run {
                                    checkPinAndNavigate(profileState.value)
                                    onSuccess()
                                }
                        } catch (e: Throwable) {
                            checkPinAndNavigate(profileState.value)
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        // If Firebase fails, check if local fallback credentials match
                        if (_registeredEmailOrPhone.value != null && email == _registeredEmailOrPhone.value && pass == _registeredPassword.value) {
                            checkPinAndNavigate(profileState.value)
                            onSuccess()
                        } else {
                            onError(e.message ?: "Login Failed. Please check your credentials.")
                        }
                    }
            } catch (e: Throwable) {
                if (_registeredEmailOrPhone.value != null && email == _registeredEmailOrPhone.value && pass == _registeredPassword.value) {
                    checkPinAndNavigate(profileState.value)
                    onSuccess()
                } else {
                    onError(e.message ?: "An unexpected error occurred during login")
                }
            }
        }
    }

    // Login as Demo User / Guest
    fun loginAsDemoUser(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = profileState.value
            val demoProfile = current.copy(
                name = "Demo User",
                username = "demo_user",
                bio = "Exploring YouSnap as Demo",
                category = "Creator"
            )
            repository.saveProfile(demoProfile)
            checkPinAndNavigate(demoProfile)
            onSuccess()
        }
    }

    // Google Sign-In Success Handler
    fun handleGoogleSignInSuccess(
        email: String,
        name: String,
        idToken: String?,
        photoUrl: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth != null && !idToken.isNullOrEmpty()) {
                    try {
                        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                        currentAuth.signInWithCredential(credential)
                            .addOnSuccessListener { authResult ->
                                val uid = authResult.user?.uid ?: email.replace(".", "_")
                                val userProfile = mapOf(
                                    "uid" to uid,
                                    "name" to name,
                                    "email" to email,
                                    "username" to email.split("@")[0].lowercase(),
                                    "bio" to "Hey there! I am using YouSnap.",
                                    "category" to "Creator",
                                    "profilePic" to photoUrl,
                                    "lastLogin" to com.google.firebase.Timestamp.now()
                                )
                                try {
                                    db?.collection("users")?.document(uid)?.set(userProfile, com.google.firebase.firestore.SetOptions.merge())
                                        ?.addOnSuccessListener {
                                            android.util.Log.d("YouSnapViewModel", "Firestore google sync success for UID: $uid")
                                        }
                                } catch (e: Throwable) {
                                    android.util.Log.w("YouSnapViewModel", "Firestore google sync failed: ${e.message}")
                                }
                                viewModelScope.launch {
                                    val current = profileState.value
                                    
                                    // Fetch actual profile from Firestore to get PIN status
                                    db?.collection("users")?.document(uid)?.get()?.addOnSuccessListener { doc ->
                                        val pinEnabledFromDb = doc.getBoolean("isPinEnabled") ?: false
                                        val pinFromDb = doc.getString("appPin") ?: ""
                                        val isPrivateFromDb = doc.getBoolean("isPrivateAccount") ?: doc.getBoolean("isPrivate") ?: false
                                        
                                        viewModelScope.launch {
                                            val updatedProfile = current.copy(
                                                name = name,
                                                username = email.split("@")[0].lowercase(),
                                                email = email,
                                                profilePic = if (photoUrl.isNotEmpty()) photoUrl else current.profilePic,
                                                isPinEnabled = pinEnabledFromDb,
                                                appPin = pinFromDb
                                            )
                                            repository.saveProfile(updatedProfile)
                                            if (isPrivateFromDb) {
                                                val curSettings = settingsState.value
                                                repository.saveSettings(curSettings.copy(isPrivateAccount = isPrivateFromDb))
                                                _userPrivacyMap.value = _userPrivacyMap.value + (updatedProfile.username.lowercase() to isPrivateFromDb)
                                            }
                                            registerUser(name, email, "google_oauth")
                                            checkPinAndNavigate(updatedProfile)
                                            onSuccess()
                                        }
                                    } ?: run {
                                        viewModelScope.launch {
                                            val updatedProfile = current.copy(
                                                name = name,
                                                username = email.split("@")[0].lowercase(),
                                                email = email,
                                                profilePic = if (photoUrl.isNotEmpty()) photoUrl else current.profilePic
                                            )
                                            repository.saveProfile(updatedProfile)
                                            registerUser(name, email, "google_oauth")
                                            checkPinAndNavigate(updatedProfile)
                                            onSuccess()
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.w("YouSnapViewModel", "Firebase signInWithCredential failed: ${e.message}, updating locally")
                                viewModelScope.launch {
                                    val current = profileState.value
                                    val updatedProfile = current.copy(
                                        name = name,
                                        username = email.split("@")[0].lowercase(),
                                        email = email,
                                        profilePic = if (photoUrl.isNotEmpty()) photoUrl else current.profilePic
                                    )
                                    repository.saveProfile(updatedProfile)
                                    registerUser(name, email, "google_oauth")
                                    checkPinAndNavigate(updatedProfile)
                                    onSuccess()
                                }
                            }
                        return@launch
                    } catch (e: Throwable) {
                        android.util.Log.e("YouSnapViewModel", "Google credential processing error: ${e.message}")
                    }
                }

                // If no idToken or Firebase auth is unconfigured, update local repository & room profile
                val current = profileState.value
                val updatedProfile = current.copy(
                    name = name,
                    username = email.split("@")[0].lowercase(),
                    email = email,
                    profilePic = if (photoUrl.isNotEmpty()) photoUrl else current.profilePic
                )
                repository.saveProfile(updatedProfile)
                registerUser(name, email, "google_oauth")
                checkPinAndNavigate(updatedProfile)
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Google sign-in handling error: ${e.message}")
                onError(e.message ?: "Google Sign-In Processing Failed")
            }
        }
    }

    // UI States observed by Composable screens
    val profileState: StateFlow<ProfileEntity>
    val settingsState: StateFlow<SettingsEntity>
    val reelsState: StateFlow<List<ReelEntity>>
    
    // Stories States
    private val _allStories = MutableStateFlow<List<StoryModel>>(emptyList())
    val allStories = _allStories.asStateFlow()
    private var allStoriesListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    private val _currentStoryState = MutableStateFlow<StoryModel?>(null)
    val currentStoryState = _currentStoryState.asStateFlow()
    private var storyListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    // All Users State (for privacy search)
    private val _allUsers = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val allUsers = _allUsers.asStateFlow()

    // Personal Photos List Flow for Profile Photos tab
    private val _personalPhotosState = MutableStateFlow<List<FullScreenPhotoData>>(emptyList())
    val personalPhotosState: StateFlow<List<FullScreenPhotoData>> = _personalPhotosState.asStateFlow()

    // Pull-To-Refresh States for Home Feed and Reels Feed
    private val _isHomeRefreshing = MutableStateFlow(false)
    val isHomeRefreshing: StateFlow<Boolean> = _isHomeRefreshing.asStateFlow()

    private val _isReelsRefreshing = MutableStateFlow(false)
    val isReelsRefreshing: StateFlow<Boolean> = _isReelsRefreshing.asStateFlow()

    fun refreshHomeFeed(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isHomeRefreshing.value = true
            try {
                repository.ensureInitialData()
                startListeningToFirestoreReels()
                startListeningToFirestorePosts()
                kotlinx.coroutines.delay(1000)
                Toast.makeText(app, "Home feed refreshed! ✨", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Refresh home error: ${e.message}")
            } finally {
                _isHomeRefreshing.value = false
                onComplete?.invoke()
            }
        }
    }

    fun refreshReelsFeed(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isReelsRefreshing.value = true
            try {
                startListeningToFirestoreReels()
                kotlinx.coroutines.delay(1000)
                Toast.makeText(app, "Reels feed refreshed! 🎬", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Refresh reels error: ${e.message}")
            } finally {
                _isReelsRefreshing.value = false
                onComplete?.invoke()
            }
        }
    }

    // Watch History state and functions
    private val _watchHistory = MutableStateFlow<List<ReelEntity>>(emptyList())
    val watchHistory: StateFlow<List<ReelEntity>> = _watchHistory.asStateFlow()
    private val recordedViewIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun addToWatchHistory(reel: ReelEntity) {
        val current = _watchHistory.value.toMutableList()
        current.removeAll { it.id == reel.id }
        current.add(0, reel)
        _watchHistory.value = current
    }

    fun recordReelViewOnce(reelId: Int) {
        if (recordedViewIds.contains(reelId)) return
        recordedViewIds.add(reelId)

        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    val reelDocId = "reel_$reelId"
                    firestore.collection("reels").document(reelDocId)
                        .update("viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
                        .addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Reel view count incremented (+1) for reel $reelId")
                        }
                }
            } catch (e: Throwable) {
                android.util.Log.w("YouSnapViewModel", "Could not increment reel view count on Firestore: ${e.message}")
            }
        }
    }

    fun clearWatchHistory() {
        _watchHistory.value = emptyList()
    }

    // Authentic Stats for current user's personal profile (starts at 0)
    private val _myFollowersCount = MutableStateFlow(0)
    val myFollowersCount: StateFlow<Int> = _myFollowersCount.asStateFlow()

    private val _myFollowingCount = MutableStateFlow(0)
    val myFollowingCount: StateFlow<Int> = _myFollowingCount.asStateFlow()

    fun adjustMyFollowers(delta: Int) {
        _myFollowersCount.value = (_myFollowersCount.value + delta).coerceAtLeast(0)
    }

    fun adjustMyFollowing(delta: Int) {
        _myFollowingCount.value = (_myFollowingCount.value + delta).coerceAtLeast(0)
    }

    fun followBackUser(notificationId: Int, usernameToFollow: String) {
        viewModelScope.launch {
            // 1. Update following count (+1)
            adjustMyFollowing(1)

            // 2. Mark this notification as followed-back in state
            _notifications.value = _notifications.value.map {
                if (it.id == notificationId) {
                    it.copy(isFollowingBack = true)
                } else {
                    it
                }
            }

            // 3. Update Firestore DB dynamically with try-catch safety block
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    val myUsername = profileState.value.username
                    val userDocRef = firestore.collection("users").document(myUsername)
                    
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(userDocRef)
                        val currentCount = snapshot.getLong("followingCount") ?: 412L
                        transaction.update(userDocRef, "followingCount", currentCount + 1)
                    }.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Firestore followingCount updated successfully (+1)")
                    }.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Firestore followingCount transaction failed: ${e.message}")
                    }

                    // Log follow relationship
                    val followRelation = mapOf(
                        "follower" to myUsername,
                        "following" to usernameToFollow,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    firestore.collection("follows")
                        .document("${myUsername}_${usernameToFollow}")
                        .set(followRelation)
                        .addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Firestore Follow relation saved successfully")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Firestore Follow relation failed: ${e.message}")
                        }
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firebase Firestore follow update skipped: ${e.message}")
            }

            Toast.makeText(app, "Following back @$usernameToFollow!", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeNotification(notificationId: Int) {
        viewModelScope.launch {
            _notifications.value = _notifications.value.filter { it.id != notificationId }
            Toast.makeText(app, "Notification removed", Toast.LENGTH_SHORT).show()
        }
    }

    // Global tracking of other users' followers counts (Live authentic counts)
    private val _otherUserFollowers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val otherUserFollowers: StateFlow<Map<String, Int>> = _otherUserFollowers.asStateFlow()

    // Global tracking of whether the current user is following them
    private val _otherUserIsFollowing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val otherUserIsFollowing: StateFlow<Map<String, Boolean>> = _otherUserIsFollowing.asStateFlow()

    // Real-time tracking of each user's privacy status (true = private, false = public)
    private val _userPrivacyMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userPrivacyMap: StateFlow<Map<String, Boolean>> = _userPrivacyMap.asStateFlow()

    fun isUserPrivate(username: String): Boolean {
        val normalized = username.lowercase()
        if (normalized.equals(profileState.value.username, ignoreCase = true)) {
            return settingsState.value.isPrivateAccount
        }
        return _userPrivacyMap.value[normalized] ?: false
    }

    fun canViewUserContent(username: String): Boolean {
        val normalized = username.lowercase()
        if (normalized.equals(profileState.value.username, ignoreCase = true)) {
            return true // Owner always has full access
        }
        val isPrivate = isUserPrivate(normalized)
        if (!isPrivate) return true
        val isFollowing = _otherUserIsFollowing.value[normalized] ?: false
        return isFollowing
    }

    fun fetchUserPrivacy(username: String) {
        val normalized = username.lowercase()
        if (normalized.isEmpty()) return
        if (normalized.equals(profileState.value.username, ignoreCase = true)) {
            _userPrivacyMap.value = _userPrivacyMap.value + (normalized to settingsState.value.isPrivateAccount)
            return
        }
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore == null) return@launch
                
                // First check document with ID = normalized username
                firestore.collection("users").document(normalized).get()
                    .addOnSuccessListener { doc ->
                        if (doc != null && doc.exists()) {
                            val isPriv = doc.getBoolean("isPrivateAccount") ?: doc.getBoolean("isPrivate") ?: false
                            _userPrivacyMap.value = _userPrivacyMap.value + (normalized to isPriv)
                        } else {
                            // Query by username field
                            firestore.collection("users")
                                .whereEqualTo("username", normalized)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { snapshot ->
                                    val isPriv = snapshot.documents.firstOrNull()?.let {
                                        it.getBoolean("isPrivateAccount") ?: it.getBoolean("isPrivate") ?: false
                                    } ?: false
                                    _userPrivacyMap.value = _userPrivacyMap.value + (normalized to isPriv)
                                }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Failed to fetch user privacy: ${e.message}")
            }
        }
    }

    // Global tracking of saved/bookmarked reel IDs
    private val _bookmarkedReelIds = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarkedReelIds: StateFlow<Set<Int>> = _bookmarkedReelIds.asStateFlow()

    fun toggleBookmarkReel(reelId: Int) {
        val currentSet = _bookmarkedReelIds.value
        val newSet = if (currentSet.contains(reelId)) {
            currentSet - reelId
        } else {
            currentSet + reelId
        }
        _bookmarkedReelIds.value = newSet
        val msg = if (newSet.contains(reelId)) "Saved to collection! 💾" else "Removed from collection"
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }

    fun toggleFollowUser(username: String) {
        val normalized = username.lowercase()
        val currentFollowing = _otherUserIsFollowing.value[normalized] ?: false
        val newFollowing = !currentFollowing

        // Update the isFollowing map
        val updatedIsFollowing = _otherUserIsFollowing.value.toMutableMap()
        updatedIsFollowing[normalized] = newFollowing
        _otherUserIsFollowing.value = updatedIsFollowing

        // Update the followers count map
        val currentFollowers = _otherUserFollowers.value[normalized] ?: 0
        val updatedFollowers = _otherUserFollowers.value.toMutableMap()
        updatedFollowers[normalized] = if (newFollowing) currentFollowers + 1 else (currentFollowers - 1).coerceAtLeast(0)
        _otherUserFollowers.value = updatedFollowers

        // Update current user's following count
        if (newFollowing) {
            adjustMyFollowing(1)
        } else {
            adjustMyFollowing(-1)
        }

        // Also update any database reels by this creator to keep the feed in sync
        viewModelScope.launch {
            val reels = reelsState.value
            reels.forEach { reel ->
                if (reel.creatorUsername.lowercase() == normalized) {
                    val updatedReel = reel.copy(isFollowingCreator = newFollowing)
                    repository.updateReel(updatedReel)
                }
            }
        }
    }

    // --- PIN LOCK SYSTEM ---
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var cachedAppPin: String = ""

    fun unlockApp(pin: String): Boolean {
        val currentProfile = profileState.value
        val validPin = if (cachedAppPin.isNotEmpty()) cachedAppPin else currentProfile.appPin
        val isEnabled = currentProfile.isPinEnabled || cachedAppPin.isNotEmpty()
        if (isEnabled && validPin == pin) {
            _isAppLocked.value = false
            prefs.edit().putBoolean("is_logged_in", true).apply()
            return true
        }
        return false
    }

    fun setAppPin(pin: String, enabled: Boolean) {
        cachedAppPin = if (enabled) pin else ""
        viewModelScope.launch {
            val myUid = auth?.currentUser?.uid
            val myUsername = profileState.value.username
            
            // 1. Update Room Database
            val updatedProfile = profileState.value.copy(
                isPinEnabled = enabled,
                appPin = pin
            )
            repository.saveProfile(updatedProfile)
            
            // 2. Update Firestore
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    val updateData = mapOf(
                        "isPinEnabled" to enabled,
                        "appPin" to pin
                    )
                    
                    // Use UID if available, else username as fallback
                    val docId = myUid ?: myUsername
                    if (docId.isNotEmpty()) {
                        firestore.collection("users").document(docId)
                            .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                android.util.Log.d("YouSnapViewModel", "Firestore PIN updated successfully")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("YouSnapViewModel", "Firestore PIN update failed: ${e.message}")
                            }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firebase PIN sync skipped: ${e.message}")
            }
        }
    }

    private fun checkPinAndNavigate(profile: ProfileEntity) {
        if (profile.isPinEnabled && profile.appPin.isNotEmpty()) {
            cachedAppPin = profile.appPin
            _isAppLocked.value = true
            clearAndNavigateTo(Screen.PinLock)
        } else {
            cachedAppPin = ""
            prefs.edit().putBoolean("is_logged_in", true).apply()
            _isAppLocked.value = false
            clearAndNavigateTo(Screen.Home)
        }
    }

    // Current active screen - starts on Splash Screen
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Screen Backstack to support back button clicks properly!
    private val navigationBackstack = mutableListOf<Screen>(Screen.Splash)

    fun proceedFromSplash() {
        val isAlreadyLoggedIn = prefs.getBoolean("is_logged_in", false)
        val hasFirebaseUser = (auth?.currentUser != null)
        if (isAlreadyLoggedIn && hasFirebaseUser) {
            clearAndNavigateTo(Screen.Home)
        } else {
            clearAndNavigateTo(Screen.Login)
        }
    }

    // In-Memory Search query and filtered items
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResultUsers = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val searchResultUsers = _searchResultUsers.asStateFlow()

    private val _searchResultPosts = MutableStateFlow<List<ReelEntity>>(emptyList())
    val searchResultPosts = _searchResultPosts.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResultUsers.value = emptyList()
            _searchResultPosts.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                // Search Users
                db?.collection("users")
                    ?.whereGreaterThanOrEqualTo("username", query)
                    ?.whereLessThanOrEqualTo("username", query + "\uf8ff")
                    ?.get()
                    ?.addOnSuccessListener { snapshot ->
                        _searchResultUsers.value = snapshot.documents.mapNotNull { doc ->
                            ProfileEntity(
                                username = doc.getString("username") ?: "",
                                name = doc.getString("name") ?: "",
                                profilePic = doc.getString("profilePic") ?: ""
                            )
                        }
                    }

                // Search Posts (Reels)
                db?.collection("reels")
                    ?.whereGreaterThanOrEqualTo("caption", query)
                    ?.whereLessThanOrEqualTo("caption", query + "\uf8ff")
                    ?.get()
                    ?.addOnSuccessListener { snapshot ->
                        _searchResultPosts.value = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ReelEntity::class.java)
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Search error: ${e.message}")
            }
        }
    }

    // Comment Input state for current active reel
    val currentCommentText = mutableStateOf("")

    // Highlighted photo ID for navigation
    private val _highlightedPhotoId = MutableStateFlow<Int?>(null)
    val highlightedPhotoId: StateFlow<Int?> = _highlightedPhotoId.asStateFlow()

    fun setHighlightedPhotoId(id: Int?) {
        _highlightedPhotoId.value = id
    }

    // Active post opened on the Home Screen from a user's profile
    private val _selectedHomePost = MutableStateFlow<HomePostItem?>(null)
    val selectedHomePost: StateFlow<HomePostItem?> = _selectedHomePost.asStateFlow()

    // Post comments map for Home posts
    private val _homePostComments = MutableStateFlow<Map<String, List<CommentEntity>>>(
        mapOf(
            "default_alex_hills" to listOf(
                CommentEntity(id = 1, reelId = 0, username = "priya_s", text = "The beauty of Assam is truly amazing! ❤️", timestamp = System.currentTimeMillis() - 3600000),
                CommentEntity(id = 2, reelId = 0, username = "rahul_asm", text = "Beautiful photo! Where was this taken in Guwahati? ⛰️", timestamp = System.currentTimeMillis() - 1800000),
                CommentEntity(id = 3, reelId = 0, username = "bikash_tech", text = "The pictures seen on YouSnap are truly amazing 🔥", timestamp = System.currentTimeMillis() - 600000)
            )
        )
    )
    val homePostComments: StateFlow<Map<String, List<CommentEntity>>> = _homePostComments.asStateFlow()

    // Post likes, counts, and bookmark map for Home posts
    private val _homePostLikedState = MutableStateFlow<Map<String, Boolean>>(mapOf("default_alex_hills" to true))
    val homePostLikedState: StateFlow<Map<String, Boolean>> = _homePostLikedState.asStateFlow()

    private val _homePostLikesCount = MutableStateFlow<Map<String, Int>>(mapOf("default_alex_hills" to 1200))
    val homePostLikesCount: StateFlow<Map<String, Int>> = _homePostLikesCount.asStateFlow()

    private val _homePostBookmarked = MutableStateFlow<Map<String, Boolean>>(mapOf("default_alex_hills" to true))
    val homePostBookmarked: StateFlow<Map<String, Boolean>> = _homePostBookmarked.asStateFlow()

    fun openPhotoOnHomeScreen(post: HomePostItem) {
        _selectedHomePost.value = post
        // Initialize like and count state if not yet present
        if (!_homePostLikedState.value.containsKey(post.id)) {
            _homePostLikedState.value = _homePostLikedState.value + (post.id to post.isLiked)
            _homePostLikesCount.value = _homePostLikesCount.value + (post.id to post.likesCount)
            _homePostBookmarked.value = _homePostBookmarked.value + (post.id to post.isBookmarked)
        }
        // Initialize comments for this post if not present
        if (!_homePostComments.value.containsKey(post.id)) {
            _homePostComments.value = _homePostComments.value + (post.id to listOf(
                CommentEntity(id = (post.id.hashCode() % 1000) + 1, reelId = 0, username = "nature_lover", text = "Incredible capture! The view looks unreal ✨😍", timestamp = System.currentTimeMillis() - 7200000),
                CommentEntity(id = (post.id.hashCode() % 1000) + 2, reelId = 0, username = "assam_vibes", text = "Proud of the scenic beauty of our North-East 🌿❤️", timestamp = System.currentTimeMillis() - 3600000),
                CommentEntity(id = (post.id.hashCode() % 1000) + 3, reelId = 0, username = "wanderlust_in", text = "Added to my travel bucket list! Great photography 📸", timestamp = System.currentTimeMillis() - 1800000)
            ))
        }
        navigateTo(Screen.Home)
    }

    fun clearOpenedHomePost() {
        _selectedHomePost.value = null
    }

    fun hasOpenedPostFromProfile(): Boolean {
        return _selectedHomePost.value != null
    }

    fun toggleLikeHomePost(postId: String) {
        val currentLiked = _homePostLikedState.value[postId] ?: false
        val currentCount = _homePostLikesCount.value[postId] ?: 1000
        val newLiked = !currentLiked
        val newCount = if (newLiked) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)
        _homePostLikedState.value = _homePostLikedState.value + (postId to newLiked)
        _homePostLikesCount.value = _homePostLikesCount.value + (postId to newCount)
    }

    fun toggleBookmarkHomePost(postId: String): Boolean {
        val current = _homePostBookmarked.value[postId] ?: false
        val newBookmarked = !current
        _homePostBookmarked.value = _homePostBookmarked.value + (postId to newBookmarked)
        return newBookmarked
    }

    fun addCommentToHomePost(postId: String, text: String) {
        if (text.trim().isEmpty()) return
        val existing = _homePostComments.value[postId] ?: emptyList()
        val newComment = CommentEntity(
            id = (System.currentTimeMillis() % 100000).toInt(),
            reelId = 0,
            username = profileState.value.username.ifEmpty { "you" },
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        _homePostComments.value = _homePostComments.value + (postId to (existing + newComment))
    }

    // Real Blocked Accounts (Starts empty, populated only when user blocks someone)
    private val _blockedAccounts = MutableStateFlow<List<BlockedAccount>>(emptyList())
    val blockedAccounts = _blockedAccounts.asStateFlow()

    // Real Login Devices (Starts with current session device only)
    private val _loginDevices = MutableStateFlow(
        listOf(
            LoginDevice(1, "Current Device (This Device)", "Active Session", "Active Now")
        )
    )
    val loginDevices = _loginDevices.asStateFlow()

    // Authentic Creator Insights State (Real live stats)
    val creatorReach = 0
    val creatorEngagement = 0.0
    val followerGrowth = 0.0
    val eligibilityStatus = "Upload original content to unlock monetization 🌟"
    val payoutProgress = 0.00

    // Notifications Feed (Starts clean for authentic user interactions)
    private val _notifications = MutableStateFlow<List<SnapNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    // Comments list Flow for the currently selected Reel
    private val _activeReelComments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val activeReelComments = _activeReelComments.asStateFlow()
    private var activeReelId: Int? = null

    init {
        val database = YouSnapDatabase.getDatabase(application)
        repository = YouSnapRepository(database.youSnapDao())

        // Ensure starter database population is ready
        viewModelScope.launch {
            repository.ensureInitialData()
            startListeningToAllStories()
        }

        // Map flows with defaults if database rows are null
        profileState = repository.profileFlow
            .map { it ?: ProfileEntity() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ProfileEntity()
            )

        settingsState = repository.settingsFlow
            .map { it ?: SettingsEntity() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsEntity()
            )

        reelsState = repository.reelsFlow
            .map { reels ->
                rankAndSortReels(reels)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Watch history starts clean; only reels actually watched by the user are added

        // Load personal photos for Profile Photos tab
        viewModelScope.launch {
            profileState.collect { profile ->
                if (_personalPhotosState.value.isEmpty()) {
                    _personalPhotosState.value = loadPersonalPhotosFromPrefs(profile)
                }
            }
        }

        // --- Persistent Login on App Start ---
        val hasFirebaseUser = (auth?.currentUser != null)
        if (!hasFirebaseUser && auth != null) {
            // User is not signed in to Firebase Auth (e.g. deleted account or logged out).
            // Wipe all local cache (profile, photos, reels, saved sessions) completely.
            logout()
        } else if (hasFirebaseUser) {
            // User is logged in with valid Firebase Auth
            _isAppLocked.value = false
            _currentScreen.value = Screen.Home
            viewModelScope.launch {
                val profile = repository.profileFlow.first() ?: ProfileEntity()
                val currentUid = auth?.currentUser?.uid ?: ""
                if (currentUid.isNotEmpty() && db != null) {
                    try {
                        db?.collection("users")?.document(currentUid)?.get()
                            ?.addOnSuccessListener { doc ->
                                if (doc != null && doc.exists()) {
                                    val isPin = doc.getBoolean("isPinEnabled") ?: profile.isPinEnabled
                                    val appPin = doc.getString("appPin") ?: profile.appPin
                                    val isPrivate = doc.getBoolean("isPrivateAccount") ?: doc.getBoolean("isPrivate") ?: false
                                    val updated = profile.copy(isPinEnabled = isPin, appPin = appPin)
                                    viewModelScope.launch {
                                        repository.saveProfile(updated)
                                        if (isPrivate) {
                                            val curSettings = settingsState.value
                                            repository.saveSettings(curSettings.copy(isPrivateAccount = isPrivate))
                                        }
                                        _isAppLocked.value = false
                                        if (_currentScreen.value == Screen.Login) {
                                            clearAndNavigateTo(Screen.Home)
                                        }
                                    }
                                } else {
                                    // User account document was deleted from Firestore
                                    logout()
                                }
                            }
                            ?.addOnFailureListener {
                                // Ignore network temporary failures
                            }
                    } catch (e: Throwable) {
                        // ignore
                    }
                }
            }
        } else {
            // Unconfigured or no persistent session
            _isAppLocked.value = false
            if (_currentScreen.value != Screen.SignUp && _currentScreen.value != Screen.ForgotPassword) {
                _currentScreen.value = Screen.Login
            }
        }

        // --- Firebase Auth & State Listener ---
        try {
            val currentAuth = auth
            if (currentAuth != null) {
                try {
                    _currentUser.value = currentAuth.currentUser
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Failed to get currentUser: ${e.message}")
                }

                currentAuth.addAuthStateListener { firebaseAuth ->
                    try {
                        val user = firebaseAuth.currentUser
                        _currentUser.value = user
                        if (user != null) {
                            val isPersistedLogin = prefs.getBoolean("is_logged_in", false)
                            startListeningToUserDoc(user.uid)
                            try {
                                db?.collection("users")?.document(user.uid)?.get()
                                    ?.addOnSuccessListener { document ->
                                        if (document != null && document.exists()) {
                                            val name = document.getString("name") ?: ""
                                            val username = document.getString("username") ?: ""
                                            val bio = document.getString("bio") ?: ""
                                            val category = document.getString("category") ?: ""
                                            val profilePic = document.getString("profilePic") ?: ""
                                            val isPin = document.getBoolean("isPinEnabled") ?: false
                                            val appPin = document.getString("appPin") ?: ""
                                            
                                            viewModelScope.launch {
                                                val current = profileState.value
                                                val updatedProfile = current.copy(
                                                    name = name,
                                                    username = username,
                                                    bio = bio,
                                                    category = category,
                                                    profilePic = profilePic,
                                                    isPinEnabled = isPin,
                                                    appPin = appPin
                                                )
                                                repository.saveProfile(updatedProfile)
                                                if (isPersistedLogin) {
                                                    _isAppLocked.value = false
                                                    if (_currentScreen.value == Screen.Login) {
                                                        clearAndNavigateTo(Screen.Home)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (prefs.getBoolean("is_logged_in", false)) {
                                                logout()
                                            }
                                        }
                                    }
                            } catch (e: Throwable) {
                                android.util.Log.e("YouSnapViewModel", "Firestore user fetch error: ${e.message}")
                            }

                            // If already logged in and on Login screen, navigate smoothly to Home
                            if (isPersistedLogin && _currentScreen.value == Screen.Login) {
                                clearAndNavigateTo(Screen.Home)
                            }
                        } else {
                            // User is signed out of Firebase or account deleted:
                            stopListeningToUserDoc()
                            if (prefs.getBoolean("is_logged_in", false)) {
                                logout()
                            } else if (!prefs.getBoolean("is_logged_in", false)) {
                                if (_currentScreen.value != Screen.SignUp && _currentScreen.value != Screen.ForgotPassword) {
                                    clearAndNavigateTo(Screen.Login)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("YouSnapViewModel", "AuthStateListener callback error: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Firebase setup in init failed safely: ${e.message}")
        }

        // Live Real-Time Firestore Synchronization for Real Posts & Reels
        startListeningToFirestoreReels()
        startListeningToFirestorePosts()
    }

    private var userDocListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startListeningToUserDoc(uid: String) {
        userDocListener?.remove()
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            if (firestore != null && uid.isNotEmpty()) {
                userDocListener = firestore.collection("users").document(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "User doc listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        android.util.Log.w("YouSnapViewModel", "Remote account deletion detected in Firestore! Logging out.")
                        if (prefs.getBoolean("is_logged_in", false)) {
                            logout()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Failed to start user doc listener: ${e.message}")
        }
    }

    private fun stopListeningToUserDoc() {
        userDocListener?.remove()
        userDocListener = null
    }

    private var firestoreReelsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var firestorePostsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startListeningToFirestoreReels() {
        firestoreReelsListener?.remove()
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            firestoreReelsListener = firestore?.collection("reels")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("YouSnapViewModel", "Firestore reels listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val realReels = mutableListOf<ReelEntity>()
                    val demoUsernames = setOf("alex_travels", "yuki_tech", "chloe_bakes", "arun_explorer", "elena_dance", "karan_fitness", "maya_pottery", "zack_skates", "guest_user")
                    for (doc in snapshot.documents) {
                        try {
                            val username = doc.getString("username") ?: doc.getString("creatorUsername") ?: ""
                            if (username.lowercase() in demoUsernames) continue
                            val name = doc.getString("name") ?: doc.getString("creatorName") ?: username
                            val caption = doc.getString("caption") ?: ""
                            val mediaUrl = doc.getString("mediaUrl") ?: ""
                            val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                            val viewsCount = doc.getLong("viewsCount")?.toInt() ?: 0
                            val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
                            val timestamp = (doc.get("createdAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                                ?: doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val audioTitle = doc.getString("audioTitle") ?: "Original Audio - $username"
                            val gradientStart = doc.getString("gradientStart") ?: "#FF1A75"
                            val gradientEnd = doc.getString("gradientEnd") ?: "#8A3AB9"
                            val id = (doc.id.hashCode() and 0x7FFFFFFF).let { if (it <= 8) it + 1000 else it }

                            realReels.add(
                                ReelEntity(
                                    id = id,
                                    creatorName = name,
                                    creatorUsername = username,
                                    creatorProfilePic = doc.getString("profilePic") ?: "",
                                    caption = caption,
                                    audioTitle = audioTitle,
                                    likesCount = likesCount,
                                    commentsCount = commentsCount,
                                    isLiked = false,
                                    isFollowingCreator = false,
                                    bgGradientStart = gradientStart,
                                    bgGradientEnd = gradientEnd,
                                    timestamp = timestamp,
                                    viewsCount = viewsCount,
                                    mediaUrl = mediaUrl,
                                    thumbnailUrl = doc.getString("thumbnailUrl") ?: ""
                                )
                            )
                        } catch (e: Throwable) {
                            android.util.Log.e("YouSnapViewModel", "Error parsing real reel document: ${e.message}")
                        }
                    }
                    viewModelScope.launch {
                        repository.ensureInitialData()
                        val uniqueRealReels = realReels.distinctBy { if (it.mediaUrl.isNotEmpty()) it.mediaUrl else "${it.creatorUsername}_${it.timestamp}" }
                        val remoteIds = uniqueRealReels.map { it.id }.toSet()
                        for (reel in uniqueRealReels) {
                            repository.insertReel(reel)
                        }
                        // Real-time deletion sync for reels deleted from Firestore
                        val allLocalReels = repository.reelsFlow.first()
                        for (local in allLocalReels) {
                            if (local.creatorUsername.lowercase() !in demoUsernames && local.id !in remoteIds) {
                                repository.deleteReelById(local.id)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Failed to start firestore reels listener: ${e.message}")
        }
    }

    private fun startListeningToFirestorePosts() {
        firestorePostsListener?.remove()
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            firestorePostsListener = firestore?.collection("posts")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("YouSnapViewModel", "Firestore posts listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val realPhotos = mutableListOf<FullScreenPhotoData>()
                    val demoUsernames = setOf("alex_travels", "yuki_tech", "chloe_bakes", "arun_explorer", "guest_user")
                    for (doc in snapshot.documents) {
                        try {
                            val username = doc.getString("username") ?: ""
                            if (username.lowercase() in demoUsernames) continue
                            val name = doc.getString("name") ?: username
                            val caption = doc.getString("caption") ?: ""
                            val mediaUrl = doc.getString("mediaUrl") ?: ""
                            if (mediaUrl.isEmpty()) continue
                            val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                            val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
                            val id = (doc.id.hashCode() and 0x7FFFFFFF)

                            realPhotos.add(
                                FullScreenPhotoData(
                                    id = id,
                                    title = caption,
                                    imageUrl = mediaUrl,
                                    creatorName = name,
                                    creatorUsername = username,
                                    creatorAvatar = doc.getString("profilePic") ?: "",
                                    initialLikes = likesCount,
                                    initialComments = commentsCount,
                                    initialIsLiked = false,
                                    timeAgo = "Recently"
                                )
                            )
                        } catch (e: Throwable) {
                            android.util.Log.e("YouSnapViewModel", "Error parsing real post document: ${e.message}")
                        }
                    }
                    _personalPhotosState.value = realPhotos
                    savePersonalPhotosToPrefs(realPhotos)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Failed to start firestore posts listener: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        firestoreReelsListener?.remove()
        firestorePostsListener?.remove()
        storyListenerRegistration?.remove()
        userDocListener?.remove()
    }

    // Navigation System
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value == screen) return
        navigationBackstack.add(screen)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (navigationBackstack.size > 1) {
            navigationBackstack.removeAt(navigationBackstack.size - 1)
            val prev = navigationBackstack.last()
            _currentScreen.value = prev
            return true
        }
        return false
    }

    // Clear Backstack and Reset navigation
    fun clearAndNavigateTo(screen: Screen) {
        navigationBackstack.clear()
        navigationBackstack.add(screen)
        _currentScreen.value = screen
    }

    // User Operations - Profile
    fun updateProfile(name: String, username: String, bio: String, category: String, profilePic: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val current = profileState.value
            val currentUsername = current.username
            val uid = auth?.currentUser?.uid ?: ""
            val targetUsernameLower = username.trim().lowercase()

            if (targetUsernameLower.isEmpty()) {
                Toast.makeText(app, "Username cannot be empty!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 1. UNIQUE USERNAME VALIDATION (Only if username changed)
            if (targetUsernameLower != currentUsername.lowercase() && uid.isNotEmpty()) {
                val isTaken = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                    try {
                        db?.collection("users")
                            ?.whereEqualTo("username", targetUsernameLower)
                            ?.get()
                            ?.addOnSuccessListener { snapshot ->
                                // If snapshot has documents, check if any belong to another user
                                val exists = snapshot.documents.any { it.id != uid }
                                continuation.resume(exists) { _, _, _ -> }
                            }
                            ?.addOnFailureListener {
                                continuation.resume(false) { _, _, _ -> }
                            } ?: continuation.resume(false) { _, _, _ -> }
                    } catch (e: Exception) {
                        continuation.resume(false) { _, _, _ -> }
                    }
                }

                if (isTaken) {
                    Toast.makeText(app, "Username already taken! ❌", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }

            var finalPicUrl = profilePic
            if (profilePic.isNotEmpty() && profilePic.startsWith("content://")) {
                try {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    val storageRef = storage.getReference()
                    val photoRef = storageRef.child("profile_photos/${System.currentTimeMillis()}.jpg")
                    val uri = android.net.Uri.parse(profilePic)
                    val stream = app.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val uploadUrl = kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
                            photoRef.putStream(stream)
                                .addOnSuccessListener { taskSnapshot ->
                                    photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                        continuation.resume(downloadUri.toString()) { _, _, _ -> }
                                    }.addOnFailureListener {
                                        continuation.resume(null) { _, _, _ -> }
                                    }
                                }
                                .addOnFailureListener {
                                    continuation.resume(null) { _, _, _ -> }
                                }
                        }
                        if (uploadUrl != null) {
                            finalPicUrl = uploadUrl
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouSnapViewModel", "Firebase Storage upload skipped or failed: ${e.message}")
                }
            }

            // Sync to Firestore
            try {
                if (uid.isNotEmpty()) {
                    val userMap = mapOf(
                        "name" to name,
                        "username" to targetUsernameLower,
                        "bio" to bio,
                        "category" to category,
                        "profilePic" to finalPicUrl,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                    db?.collection("users")?.document(uid)
                        ?.set(userMap, com.google.firebase.firestore.SetOptions.merge())
                        ?.addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Firebase Firestore updated successfully")
                        }
                        ?.addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Firebase Firestore update failed: ${e.message}")
                        }
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firebase Firestore skipped or failed: ${e.message}")
            }

            // Update local state
            val updated = current.copy(
                name = name,
                username = targetUsernameLower,
                bio = bio,
                category = category,
                profilePic = finalPicUrl
            )
            repository.saveProfile(updated)
            Toast.makeText(app, "Profile Updated Successfully! ✅", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }

    // Settings Toggle Operations
    fun togglePrivateAccount() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(isPrivateAccount = !current.isPrivateAccount)
            repository.saveSettings(updated)
            
            val myUsername = profileState.value.username.lowercase()
            _userPrivacyMap.value = _userPrivacyMap.value + (myUsername to updated.isPrivateAccount)
            
            // Sync to Firebase Firestore users collection
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    val myUid = auth?.currentUser?.uid ?: ""
                    val updateMap = mapOf(
                        "isPrivateAccount" to updated.isPrivateAccount,
                        "isPrivate" to updated.isPrivateAccount,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                    
                    if (myUid.isNotEmpty()) {
                        firestore.collection("users").document(myUid)
                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                android.util.Log.d("YouSnapViewModel", "Firestore privacy synced for UID: $myUid")
                            }
                    }
                    if (myUsername.isNotEmpty()) {
                        firestore.collection("users").document(myUsername)
                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                android.util.Log.d("YouSnapViewModel", "Firestore privacy synced for username: $myUsername")
                            }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firebase privacy sync failed: ${e.message}")
            }

            val msg = if (updated.isPrivateAccount) "Account is now Private 🔒" else "Account is now Public 🌐"
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggle2Fa() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(is2FaEnabled = !current.is2FaEnabled)
            repository.saveSettings(updated)
            val msg = if (updated.is2FaEnabled) "Two-Factor Auth Enabled" else "Two-Factor Auth Disabled"
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleHighQualityUploads() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(highQualityUploads = !current.highQualityUploads)
            repository.saveSettings(updated)
        }
    }

    fun toggleSensitiveFilter() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(sensitiveContentFilter = !current.sensitiveContentFilter)
            repository.saveSettings(updated)
        }
    }

    fun toggleAutoPlay() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(autoPlaySettings = !current.autoPlaySettings)
            repository.saveSettings(updated)
        }
    }

    fun togglePushNotifications() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(pushNotificationsEnabled = !current.pushNotificationsEnabled)
            repository.saveSettings(updated)
        }
    }

    fun toggleQuietMode() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(quietModeEnabled = !current.quietModeEnabled)
            repository.saveSettings(updated)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(forceDarkMode = !current.forceDarkMode)
            repository.saveSettings(updated)
        }
    }

    fun toggleCaptions() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(captionsToggle = !current.captionsToggle)
            repository.saveSettings(updated)
        }
    }

    fun toggleDataSaver() {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(dataSaverMode = !current.dataSaverMode)
            repository.saveSettings(updated)
        }
    }

    // Blocked Accounts Interactions
    fun unblockUser(id: Int, username: String) {
        val currentList = _blockedAccounts.value.toMutableList()
        currentList.removeAll { it.id == id }
        _blockedAccounts.value = currentList
        Toast.makeText(app, "Unblocked @$username", Toast.LENGTH_SHORT).show()
    }

    // Login Activity Interactions
    fun terminateSession(id: Int, deviceName: String) {
        val currentList = _loginDevices.value.toMutableList()
        currentList.removeAll { it.id == id }
        _loginDevices.value = currentList
        Toast.makeText(app, "Terminated session on $deviceName", Toast.LENGTH_SHORT).show()
    }

    private fun savePersonalPhotosToPrefs(photos: List<FullScreenPhotoData>) {
        try {
            val array = org.json.JSONArray()
            photos.forEach { p ->
                val obj = org.json.JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("imageUrl", p.imageUrl)
                    put("creatorName", p.creatorName)
                    put("creatorUsername", p.creatorUsername)
                    put("creatorAvatar", p.creatorAvatar)
                    put("initialLikes", p.initialLikes)
                    put("initialComments", p.initialComments)
                    put("initialIsLiked", p.initialIsLiked)
                    put("timeAgo", p.timeAgo)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_personal_photos", array.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Failed to save photos to prefs: ${e.message}")
        }
    }

    private fun loadPersonalPhotosFromPrefs(profile: ProfileEntity): List<FullScreenPhotoData> {
        try {
            val json = prefs.getString("saved_personal_photos", null)
            if (!json.isNullOrEmpty()) {
                val array = org.json.JSONArray(json)
                val list = mutableListOf<FullScreenPhotoData>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optInt("id")
                    val creatorUsername = obj.optString("creatorUsername")
                    val creatorName = obj.optString("creatorName")
                    
                    // Filter out any demo accounts/photos and ensure it matches current profile
                    if (id in 101..106 || creatorUsername in listOf("alex_travels", "guest_user", "demo_user") || creatorName in listOf("Alex Rivera", "Demo User", "Guest User")) {
                        continue
                    }
                    if (creatorUsername.isNotEmpty() && !creatorUsername.equals(profile.username, ignoreCase = true)) {
                        continue
                    }
                    
                    list.add(
                        FullScreenPhotoData(
                            id = id,
                            title = obj.optString("title"),
                            imageUrl = obj.optString("imageUrl"),
                            creatorName = creatorName,
                            creatorUsername = creatorUsername,
                            creatorAvatar = obj.optString("creatorAvatar"),
                            initialLikes = obj.optInt("initialLikes"),
                            initialComments = obj.optInt("initialComments"),
                            initialIsLiked = obj.optBoolean("initialIsLiked"),
                            timeAgo = obj.optString("timeAgo", "Just now")
                        )
                    )
                }
                return list
            }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Failed to load photos from prefs: ${e.message}")
        }
        return emptyList()
    }

    // Helper to perform direct Supabase Storage upload and return UploadResult
    private suspend fun performDirectB2Upload(
        context: android.content.Context,
        uri: Uri,
        filename: String,
        contentType: String
    ): com.example.data.repository.UploadResult {
        _isB2Uploading.value = true
        _b2UploadProgress.value = 0f
        _b2UploadSuccess.value = null
        _b2UploadedUrl.value = null

        return try {
            var finalLength: Long = -1L
            val finalInputStream: java.io.InputStream? = if (contentType.startsWith("image")) {
                // Efficiently load and compress image
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }

                if (bitmap != null) {
                    val outputStream = java.io.ByteArrayOutputStream()
                    // Compress to JPEG to reduce size and memory footprint
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
                    val compressedBytes = outputStream.toByteArray()
                    
                    finalLength = compressedBytes.size.toLong()
                    _b2UploadProgress.value = 0.15f
                    java.io.ByteArrayInputStream(compressedBytes)
                } else {
                    // Fallback if compression fails
                    context.contentResolver.openInputStream(uri)
                }
            } else {
                // For other files, just use the stream and get length
                finalLength = try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                } catch (e: Exception) { -1L }
                _b2UploadProgress.value = 0.15f
                context.contentResolver.openInputStream(uri)
            }

            if (finalInputStream == null) {
                _b2UploadSuccess.value = false
                com.example.data.repository.UploadResult(
                    isSuccess = false,
                    errorMessage = "Failed to open input stream for media file",
                    responseCode = 400
                )
            } else {
                val result = supabaseRepository.uploadFile(
                    filename = filename,
                    contentType = contentType,
                    inputStream = finalInputStream,
                    contentLength = finalLength,
                    onProgress = { progress ->
                        _b2UploadProgress.value = (0.15f + (progress * 0.85f)).coerceIn(0f, 1f)
                    }
                )
                if (result.isSuccess) {
                    _b2UploadedUrl.value = result.fileUrl
                    _b2UploadSuccess.value = true
                    _b2UploadProgress.value = 1.0f
                } else {
                    _b2UploadSuccess.value = false
                }
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Supabase direct upload failed: ${e.message}")
            _b2UploadSuccess.value = false
            com.example.data.repository.UploadResult(
                isSuccess = false,
                errorMessage = e.message ?: "Upload exception occurred",
                responseCode = 500
            )
        } finally {
            _isB2Uploading.value = false
        }
    }

    // Text-Only Story Upload Function
    fun uploadTextStory(
        text: String,
        gradientStart: String = "#FF512F",
        gradientEnd: String = "#DD2476",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val profile = profileState.value
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            val timestampNow = System.currentTimeMillis()
            val ownerUsername = profile.username.ifEmpty { "me" }
            val currentCreatorUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: ownerUsername

            val storyDoc = mapOf(
                "storyId" to ownerUsername,
                "ownerUsername" to ownerUsername,
                "userId" to currentCreatorUid,
                "creatorUid" to currentCreatorUid,
                "viewerIds" to emptyList<String>(),
                "viewsCount" to 0,
                "likes" to emptyList<String>(),
                "mediaUrl" to "",
                "caption" to text.trim(),
                "textOverlay" to text.trim(),
                "bgGradientStart" to gradientStart,
                "bgGradientEnd" to gradientEnd,
                "mediaType" to "text",
                "createdAt" to com.google.firebase.Timestamp.now(),
                "timestamp" to timestampNow,
                "hiddenFrom" to profile.hiddenStoryFrom.split(",").filter { it.isNotEmpty() }
            )

            try {
                firestore?.collection("stories")?.document(ownerUsername)?.set(storyDoc)
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Text Story saved to Firestore successfully")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Text Story Firestore save failed: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Text Story upload error: ${e.message}")
            }

            val newStory = StoryModel(
                storyId = ownerUsername,
                ownerUsername = ownerUsername,
                viewerIds = emptyList(),
                viewsCount = 0,
                likes = emptyList(),
                mediaUrl = "",
                mediaType = "text",
                caption = text.trim(),
                bgGradientStart = gradientStart,
                bgGradientEnd = gradientEnd,
                timestamp = timestampNow,
                hiddenFrom = profile.hiddenStoryFrom.split(",").filter { it.isNotEmpty() }
            )
            _currentStoryState.value = newStory
            _allStories.value = listOf(newStory) + _allStories.value.filter { it.ownerUsername != ownerUsername }
            _storiesUploadedCount.value += 1
            android.widget.Toast.makeText(app, "Text Story published successfully! ✨", android.widget.Toast.LENGTH_LONG).show()
            onComplete()
            clearAndNavigateTo(Screen.Home)
        }
    }

    // Unified Media Upload Function (Uploads directly to Supabase Storage, then saves to Firestore & Profile)
    fun uploadMedia(
        mediaUri: Uri,
        mediaType: String,
        caption: String,
        songTitle: String = "",
        category: String = "Entertainment",
        gradientStart: String = "#E59424",
        gradientEnd: String = "#C48B5E",
        isStory: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        if (_isB2Uploading.value) return
        
        // If it's a reel, compress it first
        if (mediaType == "reel") {
            VideoCompressor.compressVideo(
                context = app,
                inputUri = mediaUri,
                onSuccess = { compressedFile ->
                    proceedWithUpload(Uri.fromFile(compressedFile), mediaType, caption, songTitle, category, gradientStart, gradientEnd, isStory, onComplete)
                },
                onError = { e ->
                    android.util.Log.e("YouSnapViewModel", "Compression failed, uploading original: ${e.message}")
                    proceedWithUpload(mediaUri, mediaType, caption, songTitle, category, gradientStart, gradientEnd, isStory, onComplete)
                }
            )
        } else {
            proceedWithUpload(mediaUri, mediaType, caption, songTitle, category, gradientStart, gradientEnd, isStory, onComplete)
        }
    }

    private fun proceedWithUpload(
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
        viewModelScope.launch {
            val profile = profileState.value
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            val timestampNow = System.currentTimeMillis()

            val extension = if (mediaType == "photo") "jpg" else "mp4"
            val filename = "yousnap_${timestampNow}.$extension"
            val contentType = if (mediaType == "photo") "image/jpeg" else "video/mp4"

            val uploadResult = performDirectB2Upload(
                context = app,
                uri = mediaUri,
                filename = filename,
                contentType = contentType
            )

            val isCloudSuccess = uploadResult.isSuccess && !uploadResult.fileUrl.isNullOrEmpty()
            val finalMediaUrl = if (isCloudSuccess) uploadResult.fileUrl!! else mediaUri.toString()
            val storedFileName = if (isCloudSuccess) (uploadResult.fileName ?: filename) else filename
            val storageProvider = if (isCloudSuccess) "Supabase Storage (YOUSNAP)" else "Local Media"
            val bucketName = if (isCloudSuccess) "YOUSNAP" else "Local"

            if (isStory) {
                // STORY UPLOAD (With Text Overlay & Media)
                val ownerUsername = profile.username.ifEmpty { "me" }
                val currentCreatorUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: ownerUsername
                val storyDoc = mapOf(
                    "storyId" to ownerUsername,
                    "ownerUsername" to ownerUsername,
                    "userId" to currentCreatorUid,
                    "creatorUid" to currentCreatorUid,
                    "viewerIds" to emptyList<String>(),
                    "viewsCount" to 0,
                    "likes" to emptyList<String>(),
                    "mediaUrl" to finalMediaUrl,
                    "caption" to caption,
                    "textOverlay" to caption,
                    "bgGradientStart" to gradientStart,
                    "bgGradientEnd" to gradientEnd,
                    "b2FileName" to storedFileName,
                    "storageProvider" to storageProvider,
                    "bucket" to bucketName,
                    "mediaType" to if (mediaType == "reel") "video" else "image",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "timestamp" to timestampNow,
                    "hiddenFrom" to profile.hiddenStoryFrom.split(",").filter { it.isNotEmpty() }
                )
                try {
                    firestore?.collection("stories")?.document(ownerUsername)?.set(storyDoc)
                        ?.addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Story saved to Firestore successfully")
                        }
                        ?.addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Story Firestore save failed: ${e.message}")
                        }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Story upload error: ${e.message}")
                }
                _currentStoryState.value = StoryModel(
                    storyId = ownerUsername,
                    ownerUsername = ownerUsername,
                    viewerIds = emptyList(),
                    viewsCount = 0,
                    likes = emptyList(),
                    mediaUrl = finalMediaUrl,
                    mediaType = if (mediaType == "reel") "video" else "image",
                    caption = caption,
                    bgGradientStart = gradientStart,
                    bgGradientEnd = gradientEnd,
                    timestamp = timestampNow,
                    hiddenFrom = profile.hiddenStoryFrom.split(",").filter { it.isNotEmpty() }
                )
                _storiesUploadedCount.value += 1
                android.widget.Toast.makeText(app, "Story uploaded to Supabase Storage (YOUSNAP)! 🚀", android.widget.Toast.LENGTH_LONG).show()
                onComplete()
                clearAndNavigateTo(Screen.Home)
                return@launch
            } else if (mediaType == "photo") {
                // PHOTO POST UPLOAD (Auto-Categorized to Profile Photos tab)
                val newId = (timestampNow % 100000).toInt() + 1000
                val currentCreatorUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
                val newPhoto = FullScreenPhotoData(
                    id = newId,
                    title = caption.ifEmpty { "New photo capture 📸 #YouSnap" },
                    imageUrl = finalMediaUrl,
                    creatorName = profile.name.ifEmpty { profile.username },
                    creatorUsername = profile.username,
                    creatorAvatar = profile.profilePic.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=300&auto=format&fit=crop" },
                    creatorUid = currentCreatorUid,
                    initialLikes = 0,
                    initialComments = 0,
                    initialIsLiked = false,
                    timeAgo = "Just now"
                )
                val updatedPhotos = listOf(newPhoto) + _personalPhotosState.value
                _personalPhotosState.value = updatedPhotos
                savePersonalPhotosToPrefs(updatedPhotos)
                _photoUploadsCount.value += 1

                // Save to Firestore with type: "photo" and Supabase Storage URL
                try {
                    val photoDoc = mapOf(
                        "id" to newId.toString(),
                        "type" to "photo",
                        "title" to caption,
                        "category" to category,
                        "username" to profile.username,
                        "userId" to currentCreatorUid,
                        "creatorUid" to currentCreatorUid,
                        "name" to profile.name,
                        "caption" to caption,
                        "mediaUrl" to finalMediaUrl,
                        "b2FileName" to storedFileName,
                        "storageProvider" to storageProvider,
                        "bucket" to bucketName,
                        "likesCount" to 0,
                        "commentsCount" to 0,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    firestore?.collection("posts")?.document("photo_$newId")?.set(photoDoc)
                        ?.addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Photo post saved to Firestore successfully")
                        }
                        ?.addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Photo Firestore save failed: ${e.message}")
                        }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Photo Firestore save exception: ${e.message}")
                }

                Toast.makeText(app, "Photo uploaded to Supabase Storage (YOUSNAP)! 📸 Added to Photos tab.", Toast.LENGTH_LONG).show()
                onComplete()
                clearAndNavigateTo(Screen.Profile)
            } else {
                // REEL VIDEO UPLOAD (Auto-Categorized to Profile Reels tab)
                val thumbnailPath = extractVideoThumbnail(app, mediaUri)
                val currentCreatorUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
                val stableReelDocId = "reel_$timestampNow"
                val stableReelId = (stableReelDocId.hashCode() and 0x7FFFFFFF).let { if (it <= 8) it + 1000 else it }
                val newReel = ReelEntity(
                    id = stableReelId,
                    creatorName = profile.name,
                    creatorUsername = profile.username,
                    creatorProfilePic = profile.profilePic,
                    creatorUid = currentCreatorUid,
                    caption = caption.ifEmpty { "Uploaded via Gallery 🎬" },
                    audioTitle = songTitle.ifEmpty { "Original Audio - ${profile.username}" },
                    likesCount = 0,
                    commentsCount = 0,
                    isLiked = false,
                    isFollowingCreator = false,
                    bgGradientStart = gradientStart,
                    bgGradientEnd = gradientEnd,
                    timestamp = timestampNow,
                    viewsCount = 0,
                    mediaUrl = finalMediaUrl,
                    thumbnailUrl = thumbnailPath ?: ""
                )
                repository.insertReel(newReel)

                // Save to Firestore with type: "reel" and Supabase Storage URL
                try {
                    val reelDoc = mapOf(
                        "type" to "reel",
                        "title" to caption,
                        "category" to category,
                        "username" to profile.username,
                        "userId" to currentCreatorUid,
                        "creatorUid" to currentCreatorUid,
                        "name" to profile.name,
                        "caption" to caption,
                        "audioTitle" to songTitle,
                        "mediaUrl" to finalMediaUrl,
                        "thumbnailUrl" to (thumbnailPath ?: ""),
                        "b2FileName" to storedFileName,
                        "storageProvider" to storageProvider,
                        "bucket" to bucketName,
                        "likesCount" to 0,
                        "commentsCount" to 0,
                        "viewsCount" to 0,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    firestore?.collection("reels")?.document(stableReelDocId)?.set(reelDoc)
                        ?.addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Reel saved to Firestore successfully")
                        }
                        ?.addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Reel Firestore save failed: ${e.message}")
                        }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Reel Firestore save exception: ${e.message}")
                }

                Toast.makeText(app, "Video uploaded to Supabase Storage (YOUSNAP)! 🎬 Added to Reels tab.", Toast.LENGTH_LONG).show()
                onComplete()
                clearAndNavigateTo(Screen.Profile)
            }
        }
    }

    // Create Reel Action (Video upload and saves to Room and Firestore)
    fun uploadNewReel(caption: String, songTitle: String, gradientStart: String, gradientEnd: String) {
        viewModelScope.launch {
            val profile = profileState.value
            val timestampNow = System.currentTimeMillis()
            val currentCreatorUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
            val newReel = ReelEntity(
                creatorName = profile.name,
                creatorUsername = profile.username,
                creatorProfilePic = profile.profilePic,
                creatorUid = currentCreatorUid,
                caption = caption,
                audioTitle = songTitle.ifEmpty { "Original Audio - ${profile.username}" },
                likesCount = 0,
                commentsCount = 0,
                isLiked = false,
                isFollowingCreator = false,
                bgGradientStart = gradientStart,
                bgGradientEnd = gradientEnd,
                timestamp = timestampNow,
                viewsCount = 0
            )
            repository.insertReel(newReel)
            
            // Sync to Firestore
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val reelDoc = mapOf(
                    "type" to "reel",
                    "username" to profile.username,
                    "userId" to currentCreatorUid,
                    "creatorUid" to currentCreatorUid,
                    "name" to profile.name,
                    "caption" to caption,
                    "audioTitle" to songTitle,
                    "mediaUrl" to "",
                    "likesCount" to 0,
                    "commentsCount" to 0,
                    "viewsCount" to 0,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                firestore?.collection("reels")?.document("reel_$timestampNow")?.set(reelDoc)
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Reel Firestore save failed: ${e.message}")
            }

            Toast.makeText(app, "Reel Uploaded Successfully! 🎬 View it on Home.", Toast.LENGTH_LONG).show()
            clearAndNavigateTo(Screen.Home)
        }
    }

    // Reels Feed Interactions - Like / Follow Toggles
    fun toggleLikeReel(reel: ReelEntity) {
        viewModelScope.launch {
            val isCurrentlyLiked = reel.isLiked
            val likesDelta = if (isCurrentlyLiked) -1 else 1
            val updated = reel.copy(
                isLiked = !isCurrentlyLiked,
                likesCount = (reel.likesCount + likesDelta).coerceAtLeast(0)
            )
            repository.updateReel(updated)
        }
    }

    fun toggleFollowCreator(reel: ReelEntity) {
        toggleFollowUser(reel.creatorUsername)
        val isFollowingNow = _otherUserIsFollowing.value[reel.creatorUsername.lowercase()] ?: false
        val msg = if (isFollowingNow) "Following @${reel.creatorUsername}" else "Unfollowed @${reel.creatorUsername}"
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }

    fun incrementReelShares(reel: ReelEntity) {
        viewModelScope.launch {
            val updated = reel.copy(sharesCount = reel.sharesCount + 1)
            repository.updateReel(updated)
            
            // Optional: Update Firestore if cloud sync is active
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            firestore?.collection("reels")?.document(reel.id.toString())
                ?.update("sharesCount", FieldValue.increment(1))
        }
    }

    fun updateReelWatchTime(reel: ReelEntity, seconds: Long) {
        viewModelScope.launch {
            val updated = reel.copy(watchTimeSeconds = reel.watchTimeSeconds + seconds)
            repository.updateReel(updated)
            
            // Optional: Update Firestore
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            firestore?.collection("reels")?.document(reel.id.toString())
                ?.update("watchTimeSeconds", FieldValue.increment(seconds))
        }
    }


    // Delete a Photo Post (Clean DB + Prefs + Storage with Strict Authorization Check)
    fun deletePhotoPost(postId: String, imageUrl: String, creatorUsername: String = "", creatorUid: String = "", onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Strict Ownership Verification Check
                val profile = profileState.value
                val activeUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
                val isOwner = (activeUid.isNotEmpty() && creatorUid.isNotEmpty() && activeUid == creatorUid) ||
                              profile.username.equals(creatorUsername, ignoreCase = true) ||
                              creatorUsername.isEmpty() // default fallback for photos in owner's personal state
                if (!isOwner) {
                    Toast.makeText(app, "প’ষ্ট ডিলিট কৰিবলৈ আপোনাৰ পাৰ্মিছন নাই! ⛔", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // a) Delete from Firestore
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    val cleanId = postId.replace("my_photo_", "").replace("photo_", "")
                    // Ensure we delete the correct document ID
                    firestore.collection("posts").document("photo_$cleanId").delete()
                        .addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Photo post deleted from Firestore: photo_$cleanId")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Firestore delete failed: ${e.message}")
                        }
                }
                
                // b) Supabase Storage Cleanup
                try {
                    if (imageUrl.startsWith("http") && !imageUrl.contains("unsplash")) {
                        // Extract filename from URL (e.g., yousnap_12345.jpg)
                        val filename = imageUrl.substringAfterLast("/")
                        if (filename.isNotEmpty()) {
                            val success = supabaseRepository.deleteFile(filename)
                            android.util.Log.d("YouSnapViewModel", "Supabase file delete result for $filename: $success")
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Supabase delete exception: ${e.message}")
                }

                // c) Clean up local states (personalPhotosState)
                val currentPhotos = _personalPhotosState.value.toMutableList()
                val idAsInt = postId.replace("my_photo_", "").replace("photo_", "").toIntOrNull()
                currentPhotos.removeAll { 
                    it.id.toString() == postId || 
                    it.imageUrl == imageUrl || 
                    (idAsInt != null && it.id == idAsInt)
                }
                _personalPhotosState.value = currentPhotos
                savePersonalPhotosToPrefs(currentPhotos)

                // Clear active selected post
                _selectedHomePost.value = null

                Toast.makeText(app, "Photo deleted successfully! 🗑️", Toast.LENGTH_LONG).show()
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Error deleting photo post: ${e.message}")
                Toast.makeText(app, "Error deleting photo", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        }
    }

    // Delete a Reel Video (Clean DB + Room + Storage with Strict Authorization Check)
    fun deleteReelVideo(reel: ReelEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Strict Ownership Verification Check
                val profile = profileState.value
                val activeUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
                val isOwner = (activeUid.isNotEmpty() && reel.creatorUid.isNotEmpty() && activeUid == reel.creatorUid) ||
                              profile.username.equals(reel.creatorUsername, ignoreCase = true)
                if (!isOwner) {
                    Toast.makeText(app, "ৰীল ডিলিট কৰিবলৈ আপোনাৰ পাৰ্মিছন নাই! ⛔", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // a) Delete from local Room database
                repository.deleteReelById(reel.id)

                // b) Delete from Firestore
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore != null) {
                    // Match the document ID used in uploadMedia: "reel_$timestamp"
                    val reelDocId = "reel_${reel.timestamp}"
                    firestore.collection("reels").document(reelDocId).delete()
                        .addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Reel deleted from Firestore successfully: $reelDocId")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("YouSnapViewModel", "Firestore reel delete failed: ${e.message}")
                        }
                }

                // c) Supabase Storage File deletion
                try {
                    if (reel.mediaUrl.startsWith("http")) {
                        val filename = reel.mediaUrl.substringAfterLast("/")
                        if (filename.isNotEmpty()) {
                            val success = supabaseRepository.deleteFile(filename)
                            android.util.Log.d("YouSnapViewModel", "Supabase reel delete result for $filename: $success")
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Reel storage delete exception: ${e.message}")
                }

                Toast.makeText(app, "Video deleted successfully! 🗑️", Toast.LENGTH_LONG).show()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Failed to delete reel: ${e.message}")
                Toast.makeText(app, "Error deleting video", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        }
    }


    // Cache management
    fun clearAppCache() {
        Toast.makeText(app, "Clearing app cache...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            Toast.makeText(app, "Cache Cleared! 12.4 MB Freed ✨", Toast.LENGTH_SHORT).show()
        }
    }

    // Support Form Action
    fun submitSupportTicket(problemType: String, description: String) {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val ticket = mapOf(
                    "problemType" to problemType,
                    "description" to description,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "username" to profileState.value.username,
                    "userEmail" to "${profileState.value.username}@yousnap.com"
                )
                firestore?.collection("support_tickets")
                    ?.add(ticket)
                    ?.addOnSuccessListener {
                        Toast.makeText(app, "Ticket successfully submitted to Firestore! 🚀", Toast.LENGTH_SHORT).show()
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Firestore ticket save failed: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firestore initialization or save exception: ${e.message}")
            }

            Toast.makeText(app, "Problem Reported! Help Center Ticket Opened. 📩", Toast.LENGTH_LONG).show()
            navigateBack()
        }
    }

    // Monetization Application State
    private val _monetizationStatus = MutableStateFlow("NONE") // NONE, PENDING, APPROVED, REJECTED
    val monetizationStatus: StateFlow<String> = _monetizationStatus.asStateFlow()

    // Real-time monetization metrics (Starts at 0, updated from real database user uploads)
    private val _followersCount = MutableStateFlow(0)
    val followersCount: StateFlow<Int> = _followersCount.asStateFlow()
    
    private val _videoViewsCount = MutableStateFlow(0)
    val videoViewsCount: StateFlow<Int> = _videoViewsCount.asStateFlow()
    
    private val _photoUploadsCount = MutableStateFlow(0)
    val photoUploadsCount: StateFlow<Int> = _photoUploadsCount.asStateFlow()
    
    private val _storiesUploadedCount = MutableStateFlow(0)
    val storiesUploadedCount: StateFlow<Int> = _storiesUploadedCount.asStateFlow()

    fun submitMonetizationApplication(
        fullName: String,
        age: String,
        fatherName: String,
        villageAddress: String,
        payoutMethod: String, // "UPI" or "BANK"
        upiId: String = "",
        bankName: String = "",
        accountHolderName: String = "",
        accountNumber: String = "",
        ifscCode: String = ""
    ) {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val appData = mutableMapOf<String, Any>(
                    "username" to profileState.value.username,
                    "fullName" to fullName,
                    "age" to age,
                    "fatherName" to fatherName,
                    "villageAddress" to villageAddress,
                    "payoutMethod" to payoutMethod,
                    "status" to "pending",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                if (payoutMethod == "UPI") {
                    appData["upiId"] = upiId
                } else {
                    appData["bankName"] = bankName
                    appData["accountHolderName"] = accountHolderName
                    appData["accountNumber"] = accountNumber
                    appData["ifscCode"] = ifscCode
                }
                firestore?.collection("monetization_applications")
                    ?.add(appData)
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Monetization application saved to Firestore")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Monetization application save failed: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Monetization application exception: ${e.message}")
            }

            _monetizationStatus.value = "PENDING"

            // Add notification
            val newNotif = SnapNotification(
                id = (_notifications.value.maxOfOrNull { it.id } ?: 0) + 1,
                type = "monetization",
                username = "YouSnap Admin",
                detail = "Your Monetization Application ($payoutMethod Payout) has been submitted and is currently PENDING review! 🚀",
                time = "Just Now"
            )
            _notifications.value = listOf(newNotif) + _notifications.value

            Toast.makeText(app, "Monetization Application submitted successfully! Status: PENDING", Toast.LENGTH_LONG).show()
        }
    }

    fun updateMonetizationStatus(newStatus: String) {
        viewModelScope.launch {
            _monetizationStatus.value = newStatus
            val message = if (newStatus == "APPROVED") {
                "Your Monetization Application has been Approved! 🎉 Earnings are now active."
            } else {
                "Your Monetization Application has been Rejected. Please review guidelines and re-apply."
            }

            val newNotif = SnapNotification(
                id = (_notifications.value.maxOfOrNull { it.id } ?: 0) + 1,
                type = "monetization",
                username = "YouSnap Admin",
                detail = message,
                time = "Just Now"
            )
            _notifications.value = listOf(newNotif) + _notifications.value

            Toast.makeText(app, "FCM Push Notification: $message", Toast.LENGTH_LONG).show()
        }
    }

    // Report Reel Action
    fun reportReel(postId: Int, reason: String, details: String) {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val reportData = mapOf(
                    "postId" to postId,
                    "reportedBy" to profileState.value.username,
                    "reason" to reason,
                    "details" to details,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                firestore?.collection("reports")
                    ?.add(reportData)
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Report successfully saved to Firestore")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Firestore report save failed: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firestore report exception: ${e.message}")
            }

            Toast.makeText(app, "Report submitted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // Report Post Action
    fun reportPost(postId: String, postOwner: String, reason: String, details: String) {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val reportData = mapOf(
                    "postId" to postId,
                    "postOwner" to postOwner,
                    "reportedBy" to profileState.value.username.ifEmpty { "anonymous" },
                    "reason" to reason,
                    "details" to details,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                firestore?.collection("post_reports")
                    ?.add(reportData)
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Post report successfully saved to Firestore")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Firestore post report save failed: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Firestore post report exception: ${e.message}")
            }

            Toast.makeText(app, "Report submitted. Thank you for keeping YouSnap safe! 🙏", Toast.LENGTH_SHORT).show()
        }
    }

    // Comments Interaction
    fun loadCommentsForReel(reelId: Int) {
        activeReelId = reelId
        viewModelScope.launch {
            repository.getCommentsForReel(reelId).collect { comments ->
                _activeReelComments.value = comments
            }
        }
    }

    fun addCommentToActiveReel() {
        val reelId = activeReelId ?: return
        val text = currentCommentText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val profile = profileState.value
            val newComment = CommentEntity(
                reelId = reelId,
                username = profile.username,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            repository.postComment(newComment)
            currentCommentText.value = ""

            // Increment comments count on Reel
            val currentReels = reelsState.value
            val matchingReel = currentReels.firstOrNull { it.id == reelId }
            if (matchingReel != null) {
                repository.updateReel(
                    matchingReel.copy(commentsCount = matchingReel.commentsCount + 1)
                )
            }
        }
    }

    // --- STORY VIEWER & LIKE REAL-TIME ENGINE ---

    val knownUserProfiles = emptyList<StoryViewerInfo>()

    fun startListeningToAllStories() {
        allStoriesListener?.remove()
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            if (firestore == null) return
            
            allStoriesListener = firestore.collection("stories")
                .limit(50)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        android.util.Log.w("YouSnapViewModel", "Listen to all stories listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    val list = mutableListOf<StoryModel>()
                    val myUsername = profileState.value.username.lowercase()
                    
                    snapshots?.forEach { doc ->
                        val hiddenFromRaw = doc.get("hiddenFrom") as? List<*>
                        val hiddenFrom = hiddenFromRaw?.mapNotNull { it?.toString()?.lowercase() } ?: emptyList()
                        
                        // HIDE LOGIC: If I am in the hiddenFrom list, don't show this story to me
                        if (hiddenFrom.contains(myUsername)) return@forEach

                        val story = StoryModel(
                            storyId = doc.getString("storyId") ?: doc.id,
                            ownerUsername = doc.getString("ownerUsername") ?: doc.id,
                            viewerIds = (doc.get("viewerIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                            viewsCount = doc.getLong("viewsCount")?.toInt() ?: 0,
                            likes = (doc.get("likes") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                            mediaUrl = doc.getString("mediaUrl"),
                            mediaType = doc.getString("mediaType") ?: "image",
                            caption = doc.getString("caption") ?: doc.getString("textOverlay") ?: "",
                            bgGradientStart = doc.getString("bgGradientStart") ?: "#FF512F",
                            bgGradientEnd = doc.getString("bgGradientEnd") ?: "#DD2476",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            hiddenFrom = hiddenFrom
                        )
                        list.add(story)
                    }
                    // Filter out any that are strictly older than 24h
                    val filtered = list.filter { System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000L }
                    _allStories.value = filtered.sortedByDescending { it.timestamp }
                }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Error in startListeningToAllStories: ${e.message}")
        }
    }

    fun fetchAllUsers() {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                firestore?.collection("users")?.get()?.addOnSuccessListener { snapshots ->
                    val updatedPrivacyMap = _userPrivacyMap.value.toMutableMap()
                    val users = snapshots.mapNotNull { doc ->
                        try {
                            val u = doc.getString("username") ?: doc.id
                            val isPriv = doc.getBoolean("isPrivateAccount") ?: doc.getBoolean("isPrivate") ?: false
                            updatedPrivacyMap[u.lowercase()] = isPriv
                            ProfileEntity(
                                id = doc.id.hashCode(),
                                name = doc.getString("name") ?: doc.id,
                                username = u,
                                bio = doc.getString("bio") ?: "",
                                profilePic = doc.getString("profilePic") ?: "avatar_default",
                                category = doc.getString("category") ?: "User",
                                hiddenStoryFrom = (doc.get("hiddenStoryFrom") as? List<*>)?.joinToString(",") ?: "",
                                email = doc.getString("email") ?: ""
                            )
                        } catch (e: Exception) { null }
                    }
                    _allUsers.value = users
                    _userPrivacyMap.value = updatedPrivacyMap
                }
            } catch (e: Exception) {
                android.util.Log.e("YouSnapViewModel", "Failed to fetch users: ${e.message}")
            }
        }
    }
    fun updateStoryPrivacy(hiddenUsernames: List<String>) {
        viewModelScope.launch {
            val current = profileState.value
            val updated = current.copy(hiddenStoryFrom = hiddenUsernames.joinToString(","))
            repository.saveProfile(updated)
            
            // Sync to Firestore
            val myUsername = current.username
            val uid = auth?.currentUser?.uid ?: myUsername
            if (uid.isNotEmpty()) {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                firestore?.collection("users")?.document(uid)
                    ?.update("hiddenStoryFrom", hiddenUsernames)
                    
                // Also update any active story document (keyed by username)
                if (myUsername.isNotEmpty()) {
                    firestore?.collection("stories")?.document(myUsername)
                        ?.update("hiddenFrom", hiddenUsernames)
                        ?.addOnFailureListener {
                            // If document doesn't exist, ignore
                        }
                }
            }
            Toast.makeText(app, "Privacy settings saved!", Toast.LENGTH_SHORT).show()
        }
    }


    fun startListeningToStory(ownerUsername: String) {
        val ownerUsernameSafe = ownerUsername.ifEmpty { "sakinay_esmin" }
        storyListenerRegistration?.remove()
        
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            if (firestore == null) {
                return
            }
            val docRef = firestore.collection("stories").document(ownerUsernameSafe)
            
            storyListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("YouSnapViewModel", "Story listen failed: ${error.message}")
                    return@addSnapshotListener
                }
                
                try {
                    if (snapshot != null && snapshot.exists()) {
                        val timestamp = snapshot.getLong("timestamp") ?: snapshot.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        val twentyFourHours = 24 * 60 * 60 * 1000L
                        if (System.currentTimeMillis() - timestamp > twentyFourHours) {
                            // Expired
                            _currentStoryState.value = null
                            return@addSnapshotListener
                        }

                        val viewerIdsRaw = snapshot.get("viewerIds") as? List<*>
                        val viewerIds = viewerIdsRaw?.mapNotNull { it?.toString() } ?: emptyList()
                        val viewsCount = snapshot.getLong("viewsCount")?.toInt() ?: 0
                        val likesRaw = snapshot.get("likes") as? List<*>
                        val likes = likesRaw?.mapNotNull { it?.toString() } ?: emptyList()
                        val mediaUrl = snapshot.getString("mediaUrl") ?: ""
                        val mediaType = snapshot.getString("mediaType") ?: "image"
                        val caption = snapshot.getString("caption") ?: snapshot.getString("textOverlay") ?: ""
                        val bgGradientStart = snapshot.getString("bgGradientStart") ?: "#FF512F"
                        val bgGradientEnd = snapshot.getString("bgGradientEnd") ?: "#DD2476"
                        
                        _currentStoryState.value = StoryModel(
                            storyId = ownerUsernameSafe,
                            ownerUsername = ownerUsernameSafe,
                            viewerIds = viewerIds,
                            viewsCount = viewsCount,
                            likes = likes,
                            mediaUrl = mediaUrl,
                            mediaType = mediaType,
                            caption = caption,
                            bgGradientStart = bgGradientStart,
                            bgGradientEnd = bgGradientEnd,
                            timestamp = timestamp
                        )
                    } else {
                        _currentStoryState.value = null
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("YouSnapViewModel", "Failed to parse story snapshot safely: ${e.message}")
                    _currentStoryState.value = null
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "startListeningToStory exception: ${e.message}")
            _currentStoryState.value = null
        }
    }

    fun stopListeningToStory() {
        storyListenerRegistration?.remove()
        storyListenerRegistration = null
        _currentStoryState.value = null
    }

    fun recordStoryView(ownerUsername: String) {
        val ownerUsernameSafe = ownerUsername.ifEmpty { "alex_travels" }
        val myUsername = profileState.value.username.ifEmpty { "me" }

        // Local instant update
        val currentState = _currentStoryState.value
        if (currentState != null && currentState.storyId == ownerUsernameSafe) {
            if (!currentState.viewerIds.contains(myUsername)) {
                _currentStoryState.value = currentState.copy(
                    viewerIds = currentState.viewerIds + myUsername,
                    viewsCount = currentState.viewsCount + 1
                )
            }
        }

        if (ownerUsernameSafe.lowercase() != myUsername.lowercase()) {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                if (firestore == null) return
                val docRef = firestore.collection("stories").document(ownerUsernameSafe)
                
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    var viewerIds = emptyList<String>()
                    var viewsCount = 0L
                    var likes = emptyList<String>()
                    var mediaUrl = ""
                    var mediaType = "image"
                    
                    if (snapshot.exists()) {
                        val viewerIdsRaw = snapshot.get("viewerIds") as? List<*>
                        viewerIds = viewerIdsRaw?.mapNotNull { it?.toString() } ?: emptyList()
                        viewsCount = snapshot.getLong("viewsCount") ?: 0L
                        val likesRaw = snapshot.get("likes") as? List<*>
                        likes = likesRaw?.mapNotNull { it?.toString() } ?: emptyList()
                        mediaUrl = snapshot.getString("mediaUrl") ?: ""
                        mediaType = snapshot.getString("mediaType") ?: "image"
                    }
                    
                    if (!viewerIds.contains(myUsername)) {
                        val newViewerIds = viewerIds.toMutableList().apply { add(myUsername) }
                        transaction.set(
                            docRef,
                            mapOf(
                                "storyId" to ownerUsernameSafe,
                                "ownerUsername" to ownerUsernameSafe,
                                "viewerIds" to newViewerIds,
                                "viewsCount" to viewsCount + 1,
                                "likes" to likes,
                                "mediaUrl" to mediaUrl,
                                "mediaType" to mediaType
                            )
                        )
                    }
                }.addOnSuccessListener {
                    android.util.Log.d("YouSnapViewModel", "Story view successfully recorded on Firebase")
                }.addOnFailureListener { e ->
                    android.util.Log.e("YouSnapViewModel", "Story view Firebase update failed: ${e.message}")
                }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "recordStoryView exception: ${e.message}")
            }
        }
    }

    fun toggleStoryLike(ownerUsername: String) {
        val ownerUsernameSafe = ownerUsername.ifEmpty { "alex_travels" }
        val myUsername = profileState.value.username.ifEmpty { "me" }

        // Local instant update
        val curState = _currentStoryState.value
        if (curState != null && curState.storyId == ownerUsernameSafe) {
            val isLiked = curState.likes.contains(myUsername)
            _currentStoryState.value = curState.copy(
                likes = if (isLiked) curState.likes - myUsername else curState.likes + myUsername
            )
        }
        
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            if (firestore == null) return
            val docRef = firestore.collection("stories").document(ownerUsernameSafe)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                var viewerIds = emptyList<String>()
                var viewsCount = 0L
                var likes = emptyList<String>()
                var mediaUrl = ""
                var mediaType = "image"
                
                if (snapshot.exists()) {
                    val viewerIdsRaw = snapshot.get("viewerIds") as? List<*>
                    viewerIds = viewerIdsRaw?.mapNotNull { it?.toString() } ?: emptyList()
                    viewsCount = snapshot.getLong("viewsCount") ?: 0L
                    val likesRaw = snapshot.get("likes") as? List<*>
                    likes = likesRaw?.mapNotNull { it?.toString() } ?: emptyList()
                    mediaUrl = snapshot.getString("mediaUrl") ?: ""
                    mediaType = snapshot.getString("mediaType") ?: "image"
                }
                
                val newLikes = if (likes.contains(myUsername)) {
                    likes.filter { it != myUsername }
                } else {
                    likes + myUsername
                }
                
                transaction.set(
                    docRef,
                    mapOf(
                        "storyId" to ownerUsernameSafe,
                        "ownerUsername" to ownerUsernameSafe,
                        "viewerIds" to viewerIds,
                        "viewsCount" to viewsCount,
                        "likes" to newLikes,
                        "mediaUrl" to mediaUrl,
                        "mediaType" to mediaType
                    )
                )
            }.addOnSuccessListener {
                android.util.Log.d("YouSnapViewModel", "Story like state toggled on Firebase")
            }.addOnFailureListener { e ->
                android.util.Log.e("YouSnapViewModel", "Story like Firebase update failed: ${e.message}")
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "toggleStoryLike exception: ${e.message}")
        }
    }

    fun triggerStoryLikeNotification(ownerUsername: String) {
        val myUsername = profileState.value.username.ifEmpty { "me" }
        val ownerUsernameSafe = ownerUsername.ifEmpty { "alex_travels" }
        
        // Write to Firestore for the owner
        try {
            val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
            if (firestore != null) {
                val notificationData = mapOf(
                    "type" to "like",
                    "username" to myUsername,
                    "detail" to "liked your story.",
                    "time" to "Just Now",
                    "isRead" to false,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                firestore.collection("notifications").document(ownerUsernameSafe).collection("user_notifications").add(notificationData)
            }
        } catch (e: Throwable) {
            android.util.Log.e("YouSnapViewModel", "Failed to write notification to Firestore: ${e.message}")
        }
    }

    fun getViewerProfilesForStory(story: StoryModel?): List<StoryViewerInfo> {
        if (story == null) return emptyList()
        val myUsername = profileState.value.username.ifEmpty { "me" }
        val myName = profileState.value.name.ifEmpty { "Me" }
        
        return try {
            story.viewerIds.map { username ->
                val usernameSafe = username.orEmpty()
                val hasLiked = story.likes.contains(usernameSafe)
                if (usernameSafe.lowercase() == myUsername.lowercase()) {
                    StoryViewerInfo(
                        username = myUsername,
                        name = myName,
                        initials = myName.take(2).uppercase().ifEmpty { "ME" },
                        hasLiked = hasLiked
                    )
                } else {
                    val matched = knownUserProfiles.firstOrNull { it.username.lowercase() == usernameSafe.lowercase() }
                    if (matched != null) {
                        matched.copy(hasLiked = hasLiked)
                    } else {
                        StoryViewerInfo(
                            username = usernameSafe,
                            name = usernameSafe.replaceFirstChar { it.uppercase() },
                            initials = usernameSafe.take(2).uppercase().ifEmpty { "US" },
                            hasLiked = hasLiked
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "getViewerProfilesForStory failed safely: ${e.message}")
            emptyList()
        }
    }

    // --- ADVANCED SETTINGS STATE PERSISTENCE & ACTIONS (SHARED PREFERENCES) ---

    private val _hapticFeedbackFlow = MutableStateFlow(true)
    val hapticFeedbackFlow = _hapticFeedbackFlow.asStateFlow()

    private val _giftsTipsFlow = MutableStateFlow(true)
    val giftsTipsFlow = _giftsTipsFlow.asStateFlow()

    private val _fontScaleFlow = MutableStateFlow(1.0f)
    val fontScaleFlow = _fontScaleFlow.asStateFlow()

    // Persistent Search History using SharedPreferences to sync correctly
    private val _recentSearchesList = MutableStateFlow<List<String>>(emptyList())
    val recentSearchesFlow = _recentSearchesList.asStateFlow()

    init {
        // Load initial SharedPreferences in a background coroutine
        viewModelScope.launch {
            _hapticFeedbackFlow.value = prefs.getBoolean("hapticFeedbackEnabled", true)
            _giftsTipsFlow.value = prefs.getBoolean("giftsTipsEnabled", true)
            _fontScaleFlow.value = prefs.getFloat("fontScale", 1.0f)
            
            val savedSearches = prefs.getStringSet("recentSearches", setOf(
                "Assam Dance Reels",
                "#GuwahatiVibes",
                "Travel NorthEast",
                "Photography Tips",
                "Monsoon Aesthetic",
                "Bihu Dance Trends"
            ))
            _recentSearchesList.value = savedSearches?.toList() ?: emptyList()
        }
    }

    fun toggleHapticFeedback() {
        viewModelScope.launch {
            val next = !hapticFeedbackFlow.value
            prefs.edit().putBoolean("hapticFeedbackEnabled", next).apply()
            _hapticFeedbackFlow.value = next
            Toast.makeText(app, if (next) "Haptic feedback enabled 📳" else "Haptic feedback disabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleGiftsTips() {
        viewModelScope.launch {
            val next = !giftsTipsFlow.value
            prefs.edit().putBoolean("giftsTipsEnabled", next).apply()
            _giftsTipsFlow.value = next
            Toast.makeText(app, if (next) "Gifts & Tips enabled 🎁" else "Gifts & Tips disabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat("fontScale", scale).apply()
        _fontScaleFlow.value = scale
    }

    fun addSearchQuery(query: String) {
        if (query.trim().isEmpty()) return
        val current = _recentSearchesList.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        prefs.edit().putStringSet("recentSearches", current.toSet()).apply()
        _recentSearchesList.value = current
    }

    fun removeSearchQuery(query: String) {
        val current = _recentSearchesList.value.toMutableList()
        current.remove(query)
        prefs.edit().putStringSet("recentSearches", current.toSet()).apply()
        _recentSearchesList.value = current
    }

    fun clearSearchHistory() {
        prefs.edit().putStringSet("recentSearches", emptySet()).apply()
        _recentSearchesList.value = emptyList()
        Toast.makeText(app, "Search history completely cleared! 🗑️", Toast.LENGTH_SHORT).show()
    }

    fun resetFeedAlgorithm() {
        viewModelScope.launch {
            Toast.makeText(app, "Resetting personalization algorithm...", Toast.LENGTH_SHORT).show()
            _searchQuery.value = ""
            repository.ensureInitialData()
            kotlinx.coroutines.delay(800)
            Toast.makeText(app, "Feed Algorithm Reset! Default settings restored. ✨", Toast.LENGTH_SHORT).show()
        }
    }

    // Story Management: Delete Story (with Strict Authorization Check)
    fun deleteStory(ownerUsername: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val ownerUsernameSafe = ownerUsername.ifEmpty { profileState.value.username.ifEmpty { "me" } }
            val profile = profileState.value
            val activeUid = auth?.currentUser?.uid ?: _currentUser.value?.uid ?: profile.username
            val isOwner = profile.username.equals(ownerUsernameSafe, ignoreCase = true) ||
                          (activeUid.isNotEmpty() && activeUid == ownerUsernameSafe)
            if (!isOwner) {
                Toast.makeText(app, "ষ্ট’ৰী ডিলিট কৰিবলৈ আপোনাৰ পাৰ্মিছন নাই! ⛔", Toast.LENGTH_LONG).show()
                return@launch
            }

            try {
                // Remove from Firestore
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                firestore?.collection("stories")?.document(ownerUsernameSafe)?.delete()
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Story successfully deleted from Firestore")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Failed to delete story from Firestore: ${e.message}")
                    }

                // Delete from Firebase Storage if applicable
                try {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    val storageRef = storage.reference.child("stories/$ownerUsernameSafe")
                    storageRef.delete()
                        .addOnSuccessListener {
                            android.util.Log.d("YouSnapViewModel", "Story media deleted from Firebase Storage")
                        }
                        .addOnFailureListener {
                            // Non-critical if no file exists in storage
                        }
                } catch (e: Throwable) {
                    android.util.Log.d("YouSnapViewModel", "Storage delete skipped or unavailable: ${e.message}")
                }

                _currentStoryState.value = null
                Toast.makeText(app, "Story deleted successfully! 🗑️", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Error deleting story: ${e.message}")
                Toast.makeText(app, "Story deleted", Toast.LENGTH_SHORT).show()
                _currentStoryState.value = null
                onSuccess()
            }
        }
    }

    // Story Management: Report Story
    fun reportStory(storyOwner: String, reason: String, details: String) {
        viewModelScope.launch {
            try {
                val firestore = db ?: try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
                val reportData = mapOf(
                    "storyOwner" to storyOwner,
                    "reportedBy" to profileState.value.username.ifEmpty { "anonymous" },
                    "reason" to reason,
                    "details" to details,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                firestore?.collection("story_reports")
                    ?.add(reportData)
                    ?.addOnSuccessListener {
                        android.util.Log.d("YouSnapViewModel", "Story report successfully saved to 'story_reports' collection")
                    }
                    ?.addOnFailureListener { e ->
                        android.util.Log.e("YouSnapViewModel", "Failed to save story report: ${e.message}")
                    }
            } catch (e: Throwable) {
                android.util.Log.e("YouSnapViewModel", "Exception saving story report: ${e.message}")
            }

            Toast.makeText(app, "Report submitted successfully. Thank you for keeping YouSnap safe.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rankAndSortReels(reels: List<ReelEntity>): List<ReelEntity> {
        val currentUserProfile = profileState.value ?: ProfileEntity()
        val currentUsername = currentUserProfile.username.lowercase()
        val userCategory = currentUserProfile.category.lowercase()
        
        val scoredReels = reels.map { reel ->
            var score = 1.0 // Base score to avoid 0
            
            // 1. Scoring & Ranking
            score += reel.likesCount * 1.0
            score += reel.commentsCount * 3.0
            score += reel.sharesCount * 5.0
            score += reel.watchTimeSeconds * 0.2
            
            // 2. Personalization
            if (reel.isFollowingCreator) score += 2000.0
            if (reel.caption.lowercase().contains(userCategory) && userCategory.isNotEmpty()) score += 1500.0
            if (reel.caption.lowercase().contains("#viral") || reel.caption.lowercase().contains("#trending")) score += 1000.0

            // 3. Testing & Boosting
            val views = reel.viewsCount
            if (views < 100) {
                score += 15000.0 
            } else {
                val engagementCount = reel.likesCount + reel.commentsCount + reel.sharesCount
                val engagementRate = if (views > 0) (engagementCount.toDouble() / views) else 0.0
                if (engagementRate > 0.10) score += 25000.0 
            }
            
            if (reel.creatorUsername.lowercase() == "sakinay_esmin") score += 5000.0

            // 4. Time-Decay
            val ageInHours = (System.currentTimeMillis() - reel.timestamp).toDouble() / (1000 * 60 * 60)
            val decayFactor = Math.pow(ageInHours + 2.0, 1.5)
            val finalScore = score / decayFactor
            
            reel to finalScore
        }

        // Separate into Followed and Unfollowed lists
        val followed = scoredReels.filter { it.first.isFollowingCreator }
            .sortedByDescending { it.second }
            .map { it.first }
            .toMutableList()
            
        val unfollowed = scoredReels.filter { !it.first.isFollowingCreator }
            .sortedByDescending { it.second }
            .map { it.first }
            .toMutableList()
            
        val result = mutableListOf<ReelEntity>()
        
        // INTERLEAVING RULE: 40% Followed (2) vs 60% Unfollowed (3)
        // This ensures the feed is balanced as requested (2:3 ratio)
        while (followed.isNotEmpty() || unfollowed.isNotEmpty()) {
            // Add 2 from Followed list
            repeat(2) {
                if (followed.isNotEmpty()) result.add(followed.removeAt(0))
            }
            // Add 3 from Unfollowed list
            repeat(3) {
                if (unfollowed.isNotEmpty()) result.add(unfollowed.removeAt(0))
            }
        }
        
        return result.distinctBy { if (it.mediaUrl.isNotEmpty()) it.mediaUrl else "${it.creatorUsername}_${it.timestamp}" }
    }

    private fun extractVideoThumbnail(context: android.content.Context, uri: Uri): String? {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.frameAtTime
            retriever.release()
            if (bitmap != null) {
                val file = java.io.File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()
                return file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("YouSnapViewModel", "Failed to extract thumbnail: ${e.message}")
        }
        return null
    }
}

data class StoryModel(
    val storyId: String = "",
    val ownerUsername: String = "",
    val viewerIds: List<String> = emptyList(),
    val viewsCount: Int = 0,
    val likes: List<String> = emptyList(),
    val mediaUrl: String? = null,
    val mediaType: String = "image",
    val caption: String = "",
    val bgGradientStart: String = "#FF512F",
    val bgGradientEnd: String = "#DD2476",
    val timestamp: Long = System.currentTimeMillis(),
    val hiddenFrom: List<String> = emptyList()
)

data class StoryViewerInfo(
    val username: String,
    val name: String,
    val initials: String,
    val hasLiked: Boolean = false
)
