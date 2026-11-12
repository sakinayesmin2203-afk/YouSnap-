package com.example.data.repository

import com.example.data.CdnConfig
import com.example.data.ProgressRequestBody
import com.example.data.SupabaseStorageService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        file: File,
        filename: String,
        contentType: String,
        onProgress: (Float) -> Unit = {}
    ): UploadResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)

            val authToken = SupabaseStorageService.AUTHORIZATION_TOKEN
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())

            // 1. Direct Supabase Storage REST API Upload (POST method with x-upsert: true)
            val directUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename
            val request = Request.Builder()
                .url(directUrl)
                .addHeader("Authorization", authToken)
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            var response = client.newCall(request).execute()
            var isSuccess = response.isSuccessful
            var responseCode = response.code
            var responseBody = response.body?.string() ?: ""
            response.close()

            // 2. If direct fails, try worker fallback endpoint
            if (!isSuccess) {
                try {
                    val workerUrl = SupabaseStorageService.WORKER_ENDPOINT + encodedFilename
                    val workerRequest = Request.Builder()
                        .url(workerUrl)
                        .addHeader("Authorization", authToken)
                        .addHeader("apikey", apiKey)
                        .addHeader("Content-Type", contentType)
                        .addHeader("x-upsert", "true")
                        .post(file.asRequestBody(mediaType))
                        .build()

                    val workerResponse = client.newCall(workerRequest).execute()
                    if (workerResponse.isSuccessful) {
                        isSuccess = true
                        responseCode = workerResponse.code
                        responseBody = workerResponse.body?.string() ?: ""
                    }
                    workerResponse.close()
                } catch (we: Exception) {
                    android.util.Log.e("SupabaseRepository", "Worker fallback failed: ${we.message}")
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

    suspend fun uploadFile(
        filename: String,
        contentType: String,
        inputStream: InputStream,
        contentLength: Long = -1L,
        onProgress: (Float) -> Unit
    ): UploadResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(inputStream, mediaType, contentLength, onProgress)

            val authToken = SupabaseStorageService.AUTHORIZATION_TOKEN
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())

            val directUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename
            val request = Request.Builder()
                .url(directUrl)
                .addHeader("Authorization", authToken)
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (isSuccess) {
                val fullCdnUrl = CdnConfig.getFullMediaUrl(filename)
                UploadResult(
                    isSuccess = true,
                    fileUrl = fullCdnUrl,
                    fileName = filename,
                    responseCode = responseCode
                )
            } else {
                UploadResult(
                    isSuccess = false,
                    errorMessage = "Supabase Storage upload failed (HTTP $responseCode: $responseBody)",
                    responseCode = responseCode
                )
            }
        } catch (e: Exception) {
            UploadResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Network exception during upload"
            )
        }
    }

    suspend fun deleteFile(filename: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val cleanName = filename.substringAfterLast("/")
            val authToken = SupabaseStorageService.AUTHORIZATION_TOKEN
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString())
            val deleteUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename

            val request = Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", authToken)
                .addHeader("apikey", apiKey)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                android.util.Log.d("SupabaseRepository", "Delete response: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Delete exception: ${e.message}")
            false
        }
    }
}
