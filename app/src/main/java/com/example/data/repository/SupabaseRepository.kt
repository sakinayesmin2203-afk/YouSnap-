package com.example.data.repository

import com.example.data.ProgressRequestBody
import com.example.data.SupabaseStorageService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

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
    ): Boolean {
        return try {
            val uploadUrl = SupabaseStorageService.UPLOAD_ENDPOINT + filename
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(inputStream, mediaType, contentLength, onProgress)

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", SupabaseStorageService.AUTHORIZATION_TOKEN)
                .addHeader("Content-Type", contentType)
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            if (!isSuccess) {
                android.util.Log.e("SupabaseRepository", "Upload failed with code: ${response.code}, message: ${response.message}")
            }
            response.close()
            isSuccess
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Upload exception: ${e.message}")
            false
        }
    }
}
