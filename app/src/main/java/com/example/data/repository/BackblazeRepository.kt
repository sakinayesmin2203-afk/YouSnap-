package com.example.data.repository

import com.example.data.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.InputStream

class BackblazeRepository(private val apiService: BackblazeB2Service) {

    private val securityToken = "Bearer yousnap_secret_token_9126441937"

    suspend fun requestPresignedUrl(filename: String, contentType: String): PresignedUrlResponse? {
        val response = apiService.getPresignedUrl(
            bearerToken = securityToken,
            request = PresignedUrlRequest(filename, contentType)
        )
        return if (response.isSuccessful) {
            response.body()
        } else {
            android.util.Log.e("BackblazeRepository", "Failed to get presigned URL: ${response.errorBody()?.string()}")
            null
        }
    }

    suspend fun uploadFile(
        uploadUrl: String,
        contentType: String,
        inputStream: InputStream,
        onProgress: (Float) -> Unit
    ): Boolean {
        val mediaType = contentType.toMediaTypeOrNull()
        val requestBody = ProgressRequestBody(inputStream, mediaType, onProgress)
        
        val response = apiService.uploadFileDirectly(
            uploadUrl = uploadUrl,
            contentType = contentType,
            fileBody = requestBody
        )
        return response.isSuccessful
    }
}
