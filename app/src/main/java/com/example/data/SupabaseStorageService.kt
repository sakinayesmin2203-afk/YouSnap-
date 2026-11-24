package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SupabaseStorageService {
    companion object {
        // Supabase Project ID & Bucket Name (From user's Supabase Console)
        const val BUCKET_NAME = "YouSnap"
        const val PROJECT_ID = "crzvoplnzqbvljstnmmm"

        // Direct Supabase Storage REST API endpoint & Cloudflare Worker fallback
        const val DIRECT_SUPABASE_ENDPOINT = "https://$PROJECT_ID.supabase.co/storage/v1/object/$BUCKET_NAME/"
        const val WORKER_ENDPOINT = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/$BUCKET_NAME/"
        const val UPLOAD_ENDPOINT = DIRECT_SUPABASE_ENDPOINT

        private fun rawConfiguredKey(): String {
            val secret = try {
                BuildConfig.SUPABASE_API_KEY
            } catch (_: Throwable) {
                ""
            }
            return secret
                .trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .removePrefix("Bearer ")
                .trim()
        }

        val HAS_VALID_SUPABASE_KEY: Boolean
            get() {
                val key = rawConfiguredKey()
                return key.isNotBlank() && key != "MY_SUPABASE_API_KEY"
            }

        val API_KEY: String
            get() {
                val key = rawConfiguredKey()
                return if (key.isNotBlank() && key != "MY_SUPABASE_API_KEY") key else ""
            }

        /**
         * Supabase new keys (`sb_publishable_...` / `sb_secret_...`) must be passed in `apikey`
         * header only. Passing non-JWT keys in `Authorization: Bearer` triggers `Invalid Compact JWS`.
         * Legacy JWT keys (`eyJ...` with 2 dots) are passed in both `apikey` and `Authorization: Bearer`.
         */
        val DIRECT_AUTHORIZATION_HEADER: String?
            get() {
                val key = API_KEY
                if (key.isBlank()) return null
                val isJwt = key.count { it == '.' } == 2
                return if (isJwt) "Bearer $key" else null
            }

        val AUTHORIZATION_TOKEN: String
            get() = DIRECT_AUTHORIZATION_HEADER ?: "Bearer $API_KEY"

        /**
         * Fetches the signed-in user's real Firebase ID Token for authenticating requests
         * to the Cloudflare Worker instead of a hardcoded static bearer token.
         */
        suspend fun getFirebaseIdToken(forceRefresh: Boolean = false): String? {
            val currentUser = try {
                FirebaseAuth.getInstance().currentUser
            } catch (_: Throwable) {
                null
            } ?: return null

            return suspendCancellableCoroutine { continuation ->
                currentUser.getIdToken(forceRefresh)
                    .addOnSuccessListener { tokenResult ->
                        val token = tokenResult?.token?.trim()
                        if (continuation.isActive) {
                            continuation.resume(token?.takeIf { it.isNotEmpty() })
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.w("SupabaseStorageService", "Failed to fetch Firebase ID token: ${error.message}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        }

        suspend fun getWorkerAuthorizationHeader(forceRefresh: Boolean = false): String? {
            val idToken = getFirebaseIdToken(forceRefresh) ?: return null
            return "Bearer $idToken"
        }
    }
}
