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
            val uploadUrl = SupabaseStorageService.UPLOAD_ENDPOINT + filename
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(inputStream, mediaType, contentLength, onProgress)

            val rawToken = SupabaseStorageService.AUTHORIZATION_TOKEN.replace("Bearer ", "").trim()
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", SupabaseStorageService.AUTHORIZATION_TOKEN)
                .addHeader("apikey", rawToken)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val responseCode = response.code
            val responseMessage = response.message
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (isSuccess) {
                val fullCdnUrl = CdnConfig.getFullMediaUrl(filename)
                android.util.Log.d("SupabaseRepository", "Upload success: $fullCdnUrl")
                UploadResult(
                    isSuccess = true,
                    fileUrl = fullCdnUrl,
                    fileName = filename,
                    responseCode = responseCode
                )
            } else {
                val errorDetails = if (responseBody.isNotBlank()) responseBody else responseMessage
                android.util.Log.e("SupabaseRepository", "Upload failed with code: $responseCode, details: $errorDetails")
                UploadResult(
                    isSuccess = false,
                    errorMessage = "Supabase Storage upload failed (HTTP $responseCode: $errorDetails)",
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
