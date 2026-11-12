package com.example.data

object CdnConfig {
    const val BUCKET_NAME = "YouSnap"
    const val PROJECT_ID = "crzvoplnzqbvljstnmmm"
    const val STORAGE_BASE_URL = "https://$PROJECT_ID.supabase.co/storage/v1/object/public/$BUCKET_NAME/"
    
    fun getFullMediaUrl(filename: String): String {
        if (filename.isEmpty()) return ""
        if (filename.startsWith("http://") || filename.startsWith("https://")) {
            // Fix legacy placeholder urls so uploaded media streams directly from Supabase Storage
            if (filename.contains("yousnap.app/storage/v1/object/public/yousnap/")) {
                val fname = filename.substringAfterLast("/")
                return "$STORAGE_BASE_URL$fname"
            }
            return filename
        }
        return "$STORAGE_BASE_URL$filename"
    }
}
