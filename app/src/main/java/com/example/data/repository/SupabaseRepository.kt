package com.example.data.repository

import com.example.data.CdnConfig
import com.example.data.ProgressRequestBody
import com.example.data.SupabaseStorageService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UploadResult(
    val isSuccess: Boolean,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val errorMessage: String? = null,
    val responseCode: Int = 0
)

class SupabaseRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        filename: String,
        contentType: String,
        inputStream: InputStream,
        contentLength: Long = -1L,
        onProgress: (Float) -> Unit
    ): UploadResult {
        return try {
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(inputStream, mediaType, contentLength, onProgress)

            val authToken = SupabaseStorageService.AUTHORIZATION_TOKEN
            val apiKey = SupabaseStorageService.API_KEY

            // 1. Direct Supabase Storage REST API Upload (POST method with x-upsert: true)
            val directUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + filename
            val directRequest = Request.Builder()
                .url(directUrl)
                .addHeader("Authorization", authToken)
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            var response = client.newCall(directRequest).execute()
            var isSuccess = response.isSuccessful
            var responseCode = response.code
            var responseBody = response.body?.string() ?: ""
            response.close()

            // 2. If direct POST fails, attempt Cloudflare Worker Fallback
            if (!isSuccess) {
                val workerUrl = SupabaseStorageService.WORKER_ENDPOINT + filename
                val workerRequest = Request.Builder()
                    .url(workerUrl)
                    .addHeader("Authorization", authToken)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", contentType)
                    .addHeader("x-upsert", "true")
                    .post(requestBody)
                    .build()

                try {
                    response = client.newCall(workerRequest).execute()
                    if (response.isSuccessful) {
                        isSuccess = true
                        responseCode = response.code
                        responseBody = response.body?.string() ?: ""
                    }
                    response.close()
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseRepository", "Worker upload fallback failed: ${e.message}")
                }
            }

            if (isSuccess) {
                val fullCdnUrl = CdnConfig.getFullMediaUrl(filename)
                android.util.Log.d("SupabaseRepository", "Supabase Storage upload success: $fullCdnUrl (code: $responseCode)")
                UploadResult(
                    isSuccess = true,
                    fileUrl = fullCdnUrl,
                    fileName = filename,
                    responseCode = responseCode
                )
            } else {
                android.util.Log.e("SupabaseRepository", "Upload failed with code: $responseCode, details: $responseBody")
                UploadResult(
                    isSuccess = false,
                    errorMessage = "Supabase Storage upload failed (HTTP $responseCode: $responseBody)",
                    responseCode = responseCode
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Upload exception: ${e.message}")
            UploadResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Network exception during upload"
            )
        }
    }
}
