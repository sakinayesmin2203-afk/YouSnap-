package com.example.data

class SupabaseStorageService {
    companion object {
        // Supabase Storage Bucket Name
        const val BUCKET_NAME = "YOUSNAP"

        // Cloudflare Worker CDN / Supabase Storage endpoint for direct PUT upload
        const val UPLOAD_ENDPOINT = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/$BUCKET_NAME/"
        const val AUTHORIZATION_TOKEN = "Bearer yousnap_secret_token_9126441937"
    }
}
