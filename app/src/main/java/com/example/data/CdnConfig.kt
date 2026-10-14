package com.example.data

object CdnConfig {
    const val CDN_BASE_URL = "https://yousnap-cdn.sakinayesmin2203.workers.dev/"

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
