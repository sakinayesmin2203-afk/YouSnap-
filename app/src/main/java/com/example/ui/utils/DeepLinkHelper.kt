package com.example.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.Toast

sealed class YouSnapDeepLink {
    data class UserProfile(val identifier: String) : YouSnapDeepLink()
    data class PhotoPost(val docId: String) : YouSnapDeepLink()
    data class ReelVideo(val docId: String) : YouSnapDeepLink()
}

object DeepLinkHelper {
    const val DOMAIN_HOST = "yousnap-app.web.app"
    const val BASE_HTTPS_URL = "https://$DOMAIN_HOST"
    const val CUSTOM_SCHEME = "yousnap"

    fun buildProfileUrl(uidOrUsername: String): String {
        val clean = uidOrUsername.trim().removePrefix("@").ifBlank { "user" }
        return "$BASE_HTTPS_URL/u/${Uri.encode(clean)}"
    }

    fun buildPostUrl(docId: String): String {
        val clean = docId.trim().removePrefix("photo_").ifBlank { "post" }
        return "$BASE_HTTPS_URL/post/${Uri.encode(clean)}"
    }

    fun buildReelUrl(docId: String): String {
        val clean = docId.trim().ifBlank { "reel" }
        return "$BASE_HTTPS_URL/reel/${Uri.encode(clean)}"
    }

    fun parseIntent(intent: Intent?): YouSnapDeepLink? {
        if (intent == null) return null
        val action = intent.action
        val data = intent.data
        if (data != null && (action == Intent.ACTION_VIEW || action == null || action == Intent.ACTION_MAIN)) {
            parseUri(data)?.let { return it }
        }
        val extraUrl = intent.getStringExtra("deep_link_url")
            ?: intent.getStringExtra("url")
            ?: intent.getStringExtra("link")
        if (!extraUrl.isNullOrBlank()) {
            return parseUri(Uri.parse(extraUrl))
        }
        return null
    }

    fun parseUri(uri: Uri?): YouSnapDeepLink? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments.orEmpty()
            .map { Uri.decode(it).trim() }
            .filter { it.isNotBlank() }

        when (scheme) {
            "https", "http" -> {
                if (host != DOMAIN_HOST && host != "www.$DOMAIN_HOST") return null
                if (segments.isEmpty()) return null
                val first = segments[0].lowercase()
                return when {
                    (first == "u" || first == "user" || first == "profile") && segments.size >= 2 -> {
                        val id = segments[1].removePrefix("@").trim()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.UserProfile(it) }
                    }
                    first.startsWith("@") && first.length > 1 -> {
                        val id = segments[0].removePrefix("@").trim()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.UserProfile(it) }
                    }
                    (first == "post" || first == "p" || first == "photo") && segments.size >= 2 -> {
                        val id = segments[1].trim()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.PhotoPost(it) }
                    }
                    (first == "reel" || first == "r" || first == "video") && segments.size >= 2 -> {
                        val id = segments[1].trim()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.ReelVideo(it) }
                    }
                    else -> null
                }
            }

            CUSTOM_SCHEME -> {
                return when (host) {
                    "u", "user", "profile" -> {
                        val id = segments.firstOrNull()?.removePrefix("@")?.trim().orEmpty()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.UserProfile(it) }
                    }
                    "post", "p", "photo" -> {
                        val id = segments.firstOrNull()?.trim().orEmpty()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.PhotoPost(it) }
                    }
                    "reel", "r", "video" -> {
                        val id = segments.firstOrNull()?.trim().orEmpty()
                        id.takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.ReelVideo(it) }
                    }
                    "open", "" -> {
                        if (segments.size < 2) return null
                        when (segments[0].lowercase()) {
                            "u", "user", "profile" -> segments[1].removePrefix("@").trim()
                                .takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.UserProfile(it) }
                            "post", "p", "photo" -> segments[1].trim()
                                .takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.PhotoPost(it) }
                            "reel", "r", "video" -> segments[1].trim()
                                .takeIf { it.isNotBlank() }?.let { YouSnapDeepLink.ReelVideo(it) }
                            else -> null
                        }
                    }
                    else -> null
                }
            }

            else -> return null
        }
    }

    fun copyLinkToClipboard(
        context: Context,
        label: String,
        url: String,
        toastMessage: String = "Link copied! 🔗"
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to copy link", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareLinkSheet(
        context: Context,
        subject: String,
        text: String,
        chooserTitle: String = "Share via"
    ): Boolean {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val shareIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
