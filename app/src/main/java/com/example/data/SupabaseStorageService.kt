package com.example.data

import com.example.BuildConfig

class SupabaseStorageService {
    companion object {
        // Supabase Project ID & Bucket Name (From user's Supabase Console)
        const val BUCKET_NAME = "YouSnap"
        const val PROJECT_ID = "crzvoplnzqbvljstnmmm"

        // Direct Supabase Storage REST API endpoint & Cloudflare Worker fallback
        const val DIRECT_SUPABASE_ENDPOINT = "https://$PROJECT_ID.supabase.co/storage/v1/object/$BUCKET_NAME/"
        const val WORKER_ENDPOINT = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/$BUCKET_NAME/"
        const val UPLOAD_ENDPOINT = DIRECT_SUPABASE_ENDPOINT

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
