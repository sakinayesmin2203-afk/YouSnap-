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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class SupabaseStoredObject(
    val name: String,
    val publicUrl: String,
    val timestamp: Long,
    val ownerUsername: String? = null
)

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
            val rawName = filename.substringBefore("?").substringAfterLast("/")
            val cleanName = try {
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawName
            }
            if (cleanName.isBlank()) return@withContext false

            val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
            val apiKey = SupabaseStorageService.API_KEY
            val encodedFilename = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString()).replace("+", "%20")

            // 1. Direct Supabase Storage REST API DELETE /object/{bucket}/{file}
            if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                val deleteUrl = SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedFilename
                val requestBuilder = Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", apiKey)
                    .delete()
                if (!directAuthHeader.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", directAuthHeader)
                }
                try {
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        android.util.Log.d("SupabaseRepository", "Direct delete ($cleanName): ${response.code} $body")
                        if (response.isSuccessful && body.trim() != "[]" && !body.contains("\"error\"", ignoreCase = true)) {
                            return@withContext true
                        }
                        if (response.code == 404 && checkPublicFileExists(cleanName) != true) {
                            return@withContext true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseRepository", "Direct delete error: ${e.message}")
                }

                // 2. Supabase Storage Batch Delete DELETE /object/{bucket} with {"prefixes":[cleanName]}
                try {
                    val batchUrl = "https://${SupabaseStorageService.PROJECT_ID}.supabase.co/storage/v1/object/${SupabaseStorageService.BUCKET_NAME}"
                    val jsonPayload = org.json.JSONObject().apply {
                        put("prefixes", JSONArray().apply { put(cleanName) })
                    }.toString()
                    val batchBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val batchBuilder = Request.Builder()
                        .url(batchUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .delete(batchBody)
                    if (!directAuthHeader.isNullOrBlank()) {
                        batchBuilder.addHeader("Authorization", directAuthHeader)
                    }
                    client.newCall(batchBuilder.build()).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        android.util.Log.d("SupabaseRepository", "Batch delete ($cleanName): ${response.code} $body")
                        if (response.isSuccessful && body.trim() != "[]" && !body.contains("\"error\"", ignoreCase = true)) {
                            return@withContext true
                        }
                        if (response.code == 404 && checkPublicFileExists(cleanName) != true) {
                            return@withContext true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseRepository", "Batch delete error: ${e.message}")
                }
            }

            // 3. Cloudflare Worker DELETE fallback
            try {
                val workerDeleteUrl = SupabaseStorageService.WORKER_ENDPOINT + encodedFilename
                val workerReq = Request.Builder()
                    .url(workerDeleteUrl)
                    .addHeader("User-Agent", "YouSnap-Android/1.0")
                    .addHeader("Authorization", SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN)
                    .addHeader("apikey", apiKey.ifBlank { SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN.removePrefix("Bearer ") })
                    .delete()
                    .build()
                client.newCall(workerReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    android.util.Log.d("SupabaseRepository", "Worker delete ($cleanName): ${response.code} $body")
                    return@withContext response.isSuccessful || response.code == 404
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseRepository", "Worker delete error: ${e.message}")
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Delete exception: ${e.message}")
            false
        }
    }

    /**
     * Verifies directly against the public Supabase Storage object endpoint whether a media file
     * still exists in the bucket (independent of Postgres `storage.objects` SELECT RLS on list).
     *
     * Returns:
     * - `true` when the object is reachable (HTTP 200/206/416)
     * - `false` ONLY when Supabase Storage confirms the object does not exist (HTTP 404 or 400 not_found)
     * - `null` when offline, timed out, or server returned an indeterminate error (never delete local data)
     */
    suspend fun checkPublicFileExists(mediaUrlOrName: String): Boolean? = withContext(Dispatchers.IO) {
        if (mediaUrlOrName.isBlank()) return@withContext null
        try {
            val trimmed = mediaUrlOrName.trim()
            val rawName = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                    val isKnownStorageHost =
                        trimmed.contains(CdnConfig.PROJECT_ID, ignoreCase = true) ||
                            trimmed.contains("/storage/v1/object/public/${CdnConfig.BUCKET_NAME}/", ignoreCase = true) ||
                            trimmed.contains("yousnap-cdn.sakinayesmin2203.workers.dev", ignoreCase = true)
                    if (!isKnownStorageHost) return@withContext null
                    trimmed.substringBefore("?").substringAfterLast("/")
                }
                trimmed.startsWith("/") -> return@withContext null
                else -> trimmed.substringBefore("?").substringAfterLast("/")
            }

            val cleanName = try {
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawName
            }
            if (cleanName.isBlank()) return@withContext null

            val encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val publicUrl = CdnConfig.STORAGE_BASE_URL + encodedName

            val headBuilder = Request.Builder().url(publicUrl).head()
            if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                headBuilder.addHeader("apikey", SupabaseStorageService.API_KEY)
            }

            client.newCall(headBuilder.build()).execute().use { headResp ->
                if (headResp.isSuccessful) return@withContext true
                val code = headResp.code
                if (code != 400 && code != 404 && code != 405) {
                    return@withContext null
                }
            }

            // Confirm 400/404/405 with a 1-byte Range GET so we can inspect Supabase JSON error payload
            val rangeBuilder = Request.Builder()
                .url(publicUrl)
                .addHeader("Range", "bytes=0-0")
                .get()
            if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                rangeBuilder.addHeader("apikey", SupabaseStorageService.API_KEY)
            }

            client.newCall(rangeBuilder.build()).execute().use { getResp ->
                if (getResp.isSuccessful || getResp.code == 416) return@withContext true
                if (getResp.code == 404) return@withContext false
                if (getResp.code == 400) {
                    val body = getResp.body?.string().orEmpty()
                    if (body.contains("not_found", ignoreCase = true) ||
                        body.contains("Object not found", ignoreCase = true) ||
                        body.contains("NoSuchKey", ignoreCase = true) ||
                        body.contains("\"statusCode\":\"404\"", ignoreCase = true)
                    ) {
                        return@withContext false
                    }
                }
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("SupabaseRepository", "checkPublicFileExists notice ($mediaUrlOrName): ${e.message}")
            null
        }
    }

    private fun parseObjectList(raw: String): List<SupabaseStoredObject>? {
        if (raw.isBlank() || !raw.trim().startsWith("[")) return null
        val array = JSONArray(raw)
        val items = mutableListOf<SupabaseStoredObject>()
        val ownerRegex = Regex("^(?:yousnap|story)__([a-zA-Z0-9_]+)__\\d{10,15}\\.")
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name", "")
            if (name.isBlank() || name.endsWith("/") || name.startsWith(".")) continue

            val extractedTs = Regex("(\\d{10,15})").find(name)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: 0L
            val extractedOwner = ownerRegex.find(name)?.groupValues?.getOrNull(1)
            items.add(
                SupabaseStoredObject(
                    name = name,
                    publicUrl = CdnConfig.getFullMediaUrl(name),
                    timestamp = extractedTs,
                    ownerUsername = extractedOwner
                )
            )
        }
        return items
    }

    /**
     * Lists uploaded objects in the public YouSnap Supabase bucket so all users
     * can see newly uploaded photos, reels, and stories across refreshes.
     *
     * Note: Supabase Storage `/object/list` can return `[]` when `storage.objects` SELECT RLS
     * is not granted to anon; callers must NOT treat an empty list as proof of deletion without
     * verifying individual public URLs via [checkPublicFileExists].
     */
    suspend fun listMediaFiles(limit: Int = 1000): List<SupabaseStoredObject>? = withContext(Dispatchers.IO) {
        val apiKey = SupabaseStorageService.API_KEY
        val payload = """{"prefix":"","limit":$limit,"offset":0,"sortBy":{"column":"created_at","order":"desc"}}"""
        var directResult: List<SupabaseStoredObject>? = null

        if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
            try {
                val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
                val listUrl = "https://${SupabaseStorageService.PROJECT_ID}.supabase.co/storage/v1/object/list/${SupabaseStorageService.BUCKET_NAME}"
                val body = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(listUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)

                if (!directAuthHeader.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", directAuthHeader)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val raw = response.body?.string().orEmpty()
                        directResult = parseObjectList(raw)
                    } else {
                        android.util.Log.w("SupabaseRepository", "List bucket HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseRepository", "Direct list media error: ${e.message}")
            }
        }

        if (!directResult.isNullOrEmpty()) {
            return@withContext directResult
        }

        // Fallback to Cloudflare Worker list endpoint in case direct SELECT RLS returned [] or failed
        try {
            val workerListUrl = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/list/${SupabaseStorageService.BUCKET_NAME}"
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val workerReq = Request.Builder()
                .url(workerListUrl)
                .addHeader("User-Agent", "YouSnap-Android/1.0")
                .addHeader("Authorization", SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN)
                .addHeader("apikey", apiKey.ifBlank { SupabaseStorageService.WORKER_AUTHORIZATION_TOKEN.removePrefix("Bearer ") })
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(workerReq).execute().use { response ->
                if (response.isSuccessful) {
                    val workerItems = parseObjectList(response.body?.string().orEmpty())
                    if (!workerItems.isNullOrEmpty()) {
                        return@withContext workerItems
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SupabaseRepository", "Worker list fallback notice: ${e.message}")
        }

        directResult
    }
}
