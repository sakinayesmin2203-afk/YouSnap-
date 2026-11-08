package com.example.data

class SupabaseStorageService {
    companion object {
        // Cloudflare Worker CDN / Supabase Storage endpoint for direct PUT/POST upload
        const val UPLOAD_ENDPOINT = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/yousnap-bucket/"
        const val AUTHORIZATION_TOKEN = "Bearer yousnap_secret_token_9126441937"
    }
}
