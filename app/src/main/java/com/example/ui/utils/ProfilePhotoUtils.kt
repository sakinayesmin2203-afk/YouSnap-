package com.example.ui.utils

object ProfilePhotoUtils {

    /**
     * Normalizes a profile photo URL so that ONLY valid remote HTTP(S) URLs are accepted
     * for persisted/shared user profiles and content creator avatars.
     * Local device URIs (content://, file://, /storage/...) and placeholder tokens
     * ("avatar_default") always return "".
     */
    fun normalizeRemotePhotoUrl(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        if (trimmed.equals("avatar_default", ignoreCase = true)) return ""
        if (trimmed.equals("null", ignoreCase = true)) return ""
        return if (
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            trimmed
        } else {
            ""
        }
    }

    /**
     * Returns true when [raw] points to a local device image URI or file path that
     * must be uploaded to Firebase Storage before saving.
     */
    fun isLocalImageReference(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        if (trimmed.equals("avatar_default", ignoreCase = true)) return false
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return false
        }
        return trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("android.resource://", ignoreCase = true) ||
            trimmed.startsWith("/")
    }

    /**
     * Resolves the first valid remote HTTP(S) avatar URL across Firestore/Auth fields.
     */
    fun resolveAvatarFromFirestoreFields(
        photoURL: String?,
        profilePic: String?,
        avatar: String?,
        authPhotoUrl: String? = null
    ): String {
        val candidates = listOf(photoURL, profilePic, avatar, authPhotoUrl)
        for (candidate in candidates) {
            val normalized = normalizeRemotePhotoUrl(candidate)
            if (normalized.isNotBlank()) return normalized
        }
        return ""
    }

    /**
     * Builds the canonical Firestore avatar fields map with identical remote URL across
     * `photoURL`, `profilePic`, and `avatar`.
     */
    fun buildAvatarFieldsMap(remotePhotoUrl: String): Map<String, String> {
        val clean = normalizeRemotePhotoUrl(remotePhotoUrl)
        return mapOf(
            "photoURL" to clean,
            "profilePic" to clean,
            "avatar" to clean
        )
    }
}
