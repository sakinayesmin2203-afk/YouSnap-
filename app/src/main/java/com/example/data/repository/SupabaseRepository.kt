package com.example.data.repository

import com.example.data.CdnConfig
import com.example.data.ProgressRequestBody
import com.example.data.SupabaseStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

sealed class UploadTransferState {
    data class Progress(val fraction: Float) : UploadTransferState()
    data class Completed(val result: UploadResult) : UploadTransferState()
}

class SupabaseRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Emits real-time byte-level upload progress (0.0f..1.0f) via Kotlin Flow,
     * followed by the final UploadResult.
     */
    fun uploadFileFlow(
        file: File,
        filename: String,
        contentType: String
    ): Flow<UploadTransferState> = callbackFlow {
        trySend(UploadTransferState.Progress(0f))
        val result = uploadFile(
            file = file,
            filename = filename,
            contentType = contentType,
            onProgress = { progress ->
                trySend(UploadTransferState.Progress(progress.coerceIn(0f, 1f)))
            }
        )
        if (result.isSuccess) {
            trySend(UploadTransferState.Progress(1.0f))
        }
        trySend(UploadTransferState.Completed(result))
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    suspend fun uploadFile(
        file: File,
        filename: String,
        contentType: String,
        onProgress: (Float) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext UploadResult(
                    isSuccess = false,
                    errorMessage = "Selected media file is empty or missing"
                )
            }

            val mediaType = contentType.toMediaTypeOrNull()
            val apiKey = SupabaseStorageService.API_KEY
            val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
            val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())

            // Reserve final 2% until HTTP server confirms upload success
            val byteProgressWrapper: (Float) -> Unit = { byteFraction ->
                onProgress((byteFraction * 0.98f).coerceIn(0f, 0.98f))
            }

            var isSuccess = false
            var responseCode = 0
            var responseBody = ""

            // 1. Direct Supabase Storage REST API Upload
            if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                val directUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename
                val directRequestBody = ProgressRequestBody(file, mediaType, byteProgressWrapper)
                val requestBuilder = Request.Builder()
                    .url(directUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", contentType)
                    .post(directRequestBody)

                if (!directAuthHeader.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", directAuthHeader)
                }

                try {
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        isSuccess = response.isSuccessful
                        responseCode = response.code
                        responseBody = response.body?.string() ?: ""
                    }
                } catch (directEx: Exception) {
                    android.util.Log.e("SupabaseRepository", "Direct upload failed, trying fallback: ${directEx.message}")
                    if (responseBody.isBlank()) {
                        responseBody = directEx.localizedMessage ?: "Direct upload network error"
                    }
                }
            } else {
                responseCode = 401
                responseBody = "SUPABASE_API_KEY is not configured in Secrets"
            }

            // 2. If direct fails (and not an explicit Supabase RLS block where worker also can't bypass unless configured),
            // try Cloudflare worker fallback endpoint with live progress too
            if (!isSuccess) {
                try {
                    onProgress(0f)
                    val workerUrl = SupabaseStorageService.WORKER_ENDPOINT + encodedFilename
                    val workerRequestBody = ProgressRequestBody(file, mediaType, byteProgressWrapper)
                    val workerRequest = Request.Builder()
                        .url(workerUrl)
                        .addHeader("User-Agent", "YouSnap-Android/1.0")
                        .addHeader("Authorization", SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN)
                        .addHeader("apikey", apiKey.ifBlank { SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN.removePrefix("Bearer ") })
                        .addHeader("Content-Type", contentType)
                        .post(workerRequestBody)
                        .build()

                    client.newCall(workerRequest).execute().use { workerResponse ->
                        if (workerResponse.isSuccessful) {
                            isSuccess = true
                            responseCode = workerResponse.code
                            responseBody = workerResponse.body?.string() ?: ""
                        }
                    }
                } catch (we: Exception) {
                    android.util.Log.e("SupabaseRepository", "Worker fallback failed: ${we.message}")
                }
            }

            if (isSuccess) {
                onProgress(1.0f)
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
                val friendlyError = when {
                    responseBody.contains("row-level security policy", ignoreCase = true) ->
                        "Supabase Storage 'YouSnap' bucket RLS policy is blocking uploads. Please allow INSERT/SELECT on 'YouSnap' bucket in Supabase."
                    responseBody.contains("Bucket not found", ignoreCase = true) ->
                        "Supabase Storage bucket 'YouSnap' not found."
                    responseBody.contains("Invalid Compact JWS", ignoreCase = true) || responseCode == 401 ->
                        "Invalid Supabase API key. Check SUPABASE_API_KEY in Secrets."
                    else -> "Supabase Storage upload failed (HTTP $responseCode: $responseBody)"
                }
                UploadResult(
                    isSuccess = false,
                    errorMessage = friendlyError,
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
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(inputStream, mediaType, contentLength) { fraction ->
                onProgress((fraction * 0.98f).coerceIn(0f, 0.98f))
            }

            val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())

            val directUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename
            val requestBuilder = Request.Builder()
                .url(directUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", contentType)
                .post(requestBody)

            if (!directAuthHeader.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", directAuthHeader)
            }
            val request = requestBuilder.build()

            var isSuccess = false
            var responseCode = 0
            var responseBody = ""
            client.newCall(request).execute().use { response ->
                isSuccess = response.isSuccessful
                responseCode = response.code
                responseBody = response.body?.string() ?: ""
            }

            if (isSuccess) {
                onProgress(1.0f)
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

    suspend fun deleteFile(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = filename.substringAfterLast("/")
            val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString())
            val deleteUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename

            val requestBuilder = Request.Builder()
                .url(deleteUrl)
                .addHeader("apikey", apiKey)
                .delete()

            if (!directAuthHeader.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", directAuthHeader)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                android.util.Log.d("SupabaseRepository", "Delete response: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Delete exception: ${e.message}")
            false
        }
    }
}
