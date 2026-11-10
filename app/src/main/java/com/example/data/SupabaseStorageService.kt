package com.example.data

import com.example.BuildConfig

class SupabaseStorageService {
    companion object {
        // Supabase Storage Bucket Name (Matches user's Supabase Console: "YouSnap")
        const val BUCKET_NAME = "YouSnap"

        // Cloudflare Worker CDN / Supabase Storage endpoint for direct upload
        const val UPLOAD_ENDPOINT = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/$BUCKET_NAME/"

        val AUTHORIZATION_TOKEN: String
            get() {
                val secret = try { BuildConfig.SUPABASE_API_KEY } catch (e: Throwable) { "" }
                val key = if (secret.isNotBlank() && secret != "MY_SUPABASE_API_KEY") secret else "yousnap_secret_token_9126441937"
                return if (key.startsWith("Bearer ")) key else "Bearer $key"
            }

        val API_KEY: String
            get() {
                val secret = try { BuildConfig.SUPABASE_API_KEY } catch (e: Throwable) { "" }
                val key = if (secret.isNotBlank() && secret != "MY_SUPABASE_API_KEY") secret else "yousnap_secret_token_9126441937"
                return key.replace("Bearer ", "").trim()
            }
    }
}
