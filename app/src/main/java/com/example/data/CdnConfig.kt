package com.example.data

object CdnConfig {
    // Supabase Storage Bucket Name
    const val BUCKET_NAME = "YOUSNAP"

    // Cloudflare Worker CDN routing to Supabase Storage public YOUSNAP bucket
    const val CDN_BASE_URL = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/public/$BUCKET_NAME/"

    fun getFullMediaUrl(fileName: String?): String {
        if (fileName.isNullOrEmpty()) return ""
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            return fileName
        }
        val cleanFileName = if (fileName.startsWith("/")) fileName.substring(1) else fileName
        val cleanBaseUrl = if (CDN_BASE_URL.endsWith("/")) CDN_BASE_URL else "$CDN_BASE_URL/"
        return cleanBaseUrl + cleanFileName
    }
}
