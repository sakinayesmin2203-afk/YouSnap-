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
import okhttp3.CacheControl
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

data class DeleteResult(
    val isSuccess: Boolean,
    val responseCode: Int = 0,
    val errorMessage: String? = null,
    val details: String = ""
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
            val encodedFilename = android.net.Uri.encode(filename, "/")

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

            // 2. If direct fails, try Cloudflare Worker fallback authenticated with the user's Firebase ID Token
            if (!isSuccess) {
                var workerAuthHeader = SupabaseStorageService.getWorkerAuthorizationHeader(forceRefresh = false)
                if (!workerAuthHeader.isNullOrBlank()) {
                    for (tokenAttempt in 0..1) {
                        if (tokenAttempt == 1) {
                            val refreshedHeader = SupabaseStorageService.getWorkerAuthorizationHeader(forceRefresh = true)
                            if (refreshedHeader.isNullOrBlank() || refreshedHeader == workerAuthHeader) break
                            workerAuthHeader = refreshedHeader
                        }
                        try {
                            onProgress(0f)
                            val workerUrl = SupabaseStorageService.WORKER_ENDPOINT + encodedFilename
                            val workerRequestBody = ProgressRequestBody(file, mediaType, byteProgressWrapper)
                            val workerBuilder = Request.Builder()
                                .url(workerUrl)
                                .addHeader("User-Agent", "YouSnap-Android/1.0")
                                .addHeader("Authorization", workerAuthHeader!!)
                                .addHeader("Content-Type", contentType)
                                .post(workerRequestBody)

                            if (apiKey.isNotBlank()) {
                                workerBuilder.addHeader("apikey", apiKey)
                            }

                            client.newCall(workerBuilder.build()).execute().use { workerResponse ->
                                responseCode = workerResponse.code
                                responseBody = workerResponse.body?.string() ?: ""
                                if (workerResponse.isSuccessful) {
                                    isSuccess = true
                                }
                            }
                            if (isSuccess || (responseCode != 401 && responseCode != 403)) {
                                break
                            }
                        } catch (we: Exception) {
                            android.util.Log.e("SupabaseRepository", "Worker fallback failed: ${we.message}")
                            break
                        }
                    }
                } else {
                    android.util.Log.w("SupabaseRepository", "Skipped Worker upload fallback: no authenticated Firebase user token")
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
            val encodedFilename = android.net.Uri.encode(filename, "/")

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

    fun extractObjectKey(urlOrName: String): String {
        val trimmed = urlOrName.trim().substringBefore("#").substringBefore("?")
        if (trimmed.isBlank()) return ""
        val bucket = SupabaseStorageService.BUCKET_NAME
        val markers = listOf(
            "/storage/v1/object/public/$bucket/",
            "/storage/v1/object/authenticated/$bucket/",
            "/storage/v1/object/sign/$bucket/",
            "/storage/v1/object/$bucket/",
            "/$bucket/"
        )
        val rawKey = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val matchedMarker = markers.firstOrNull { trimmed.contains(it, ignoreCase = true) }
            if (matchedMarker != null) {
                val idx = trimmed.indexOf(matchedMarker, ignoreCase = true)
                trimmed.substring(idx + matchedMarker.length)
            } else {
                trimmed.substringAfterLast("/")
            }
        } else if (
            trimmed.startsWith("/") ||
            trimmed.startsWith("content:", ignoreCase = true) ||
            trimmed.startsWith("file:", ignoreCase = true)
        ) {
            return ""
        } else {
            val matchedMarker = markers.firstOrNull { trimmed.contains(it, ignoreCase = true) }
            if (matchedMarker != null) {
                val idx = trimmed.indexOf(matchedMarker, ignoreCase = true)
                trimmed.substring(idx + matchedMarker.length)
            } else {
                trimmed.trimStart('/')
            }
        }

        val decoded = try {
            android.net.Uri.decode(rawKey).trimStart('/')
        } catch (_: Exception) {
            rawKey.trimStart('/')
        }
        return if (decoded.startsWith("$bucket/", ignoreCase = true)) {
            decoded.substring(bucket.length + 1).trimStart('/')
        } else {
            decoded
        }
    }

    suspend fun deleteFile(filename: String): Boolean = deleteFileDetailed(filename).isSuccess

    suspend fun deleteFileDetailed(filename: String): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val cleanName = extractObjectKey(filename)
            if (cleanName.isBlank()) {
                return@withContext DeleteResult(
                    isSuccess = false,
                    errorMessage = "Empty or non-Supabase file key"
                )
            }

            val directAuthHeader = SupabaseStorageService.DIRECT_AUTHORIZATION_HEADER
            val apiKey = SupabaseStorageService.API_KEY
            val directBatchUrl = "https://${SupabaseStorageService.PROJECT_ID}.supabase.co/storage/v1/object/${SupabaseStorageService.BUCKET_NAME}"
            val workerBatchUrl = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/${SupabaseStorageService.BUCKET_NAME}"

            var lastCode = 0
            var lastBody = ""
            var lastLabel = ""
            var sawWorkerAuthFailure = false

            suspend fun executeAndVerify(
                request: Request,
                targetKey: String,
                label: String,
                allow404AsDeleted: Boolean
            ): Boolean {
                return try {
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty().trim()
                        lastCode = response.code
                        lastBody = body
                        lastLabel = label
                        android.util.Log.d("SupabaseRepository", "$label ($targetKey): HTTP ${response.code} $body")

                        if ((response.code == 401 || response.code == 403) && label.startsWith("Worker")) {
                            sawWorkerAuthFailure = true
                        }

                        val shortName = targetKey.substringAfterLast("/")
                        val hasErrorPayload = body.contains("\"error\"", ignoreCase = true)
                        val isEmptyBatchArray = body == "[]"
                        val isExplicitSingleSuccess = response.isSuccessful && !hasErrorPayload && !isEmptyBatchArray && (
                            body.contains("Successfully deleted", ignoreCase = true) ||
                                body.contains("\"deleted\":true", ignoreCase = true) ||
                                body.contains("\"deleted\": true", ignoreCase = true) ||
                                body.contains("\"ok\":true", ignoreCase = true) ||
                                body.contains("\"ok\": true", ignoreCase = true) ||
                                body.contains("\"success\":true", ignoreCase = true) ||
                                body.contains("\"success\": true", ignoreCase = true) ||
                                (response.code == 204 && body.isBlank()) ||
                                (label.startsWith("Worker") && body.isBlank())
                            )
                        val isExplicitBatchSuccess = response.isSuccessful && !hasErrorPayload && !isEmptyBatchArray &&
                            body.startsWith("[") && (
                            body.contains(targetKey, ignoreCase = true) ||
                                body.contains(shortName, ignoreCase = true) ||
                                body.contains("\"name\"", ignoreCase = true)
                            )

                        if (isExplicitSingleSuccess || isExplicitBatchSuccess) {
                            return true
                        }

                        // If HTTP 2xx returned an unfamiliar non-error payload (except empty `[]` from batch RLS),
                        // verify against public Storage whether the object is now absent.
                        if (response.isSuccessful && !hasErrorPayload && !isEmptyBatchArray) {
                            kotlinx.coroutines.delay(150L)
                            if (checkPublicFileExists(targetKey) == false) {
                                return true
                            }
                        }

                        val isObjectNotFound = (
                            body.contains("not_found", ignoreCase = true) ||
                                body.contains("Object not found", ignoreCase = true) ||
                                body.contains("NoSuchKey", ignoreCase = true)
                            ) && !body.contains("Bucket not found", ignoreCase = true)

                        if (allow404AsDeleted && response.code == 404 && isObjectNotFound) {
                            return true
                        }
                    }
                    false
                } catch (e: Exception) {
                    lastBody = e.localizedMessage ?: "Network exception"
                    lastLabel = label
                    android.util.Log.w("SupabaseRepository", "$label error ($targetKey): ${e.message}")
                    false
                }
            }

            suspend fun tryWorkerDeleteWithToken(
                targetKey: String,
                encodedPath: String,
                batchPayloadJson: String,
                jsonMediaType: okhttp3.MediaType?,
                workerAuthHeader: String
            ): Boolean {
                val workerSingleBuilder = Request.Builder()
                    .url(SupabaseStorageService.WORKER_ENDPOINT + encodedPath)
                    .addHeader("User-Agent", "YouSnap-Android/1.0")
                    .addHeader("Authorization", workerAuthHeader)
                    .delete()
                if (executeAndVerify(workerSingleBuilder.build(), targetKey, "Worker single delete", allow404AsDeleted = true)) {
                    return true
                }

                val workerBatchBuilder = Request.Builder()
                    .url(workerBatchUrl)
                    .addHeader("User-Agent", "YouSnap-Android/1.0")
                    .addHeader("Authorization", workerAuthHeader)
                    .delete(batchPayloadJson.toRequestBody(jsonMediaType))
                if (executeAndVerify(workerBatchBuilder.build(), targetKey, "Worker batch delete", allow404AsDeleted = true)) {
                    return true
                }

                if (apiKey.isNotBlank()) {
                    val workerSingleWithKey = Request.Builder()
                        .url(SupabaseStorageService.WORKER_ENDPOINT + encodedPath)
                        .addHeader("User-Agent", "YouSnap-Android/1.0")
                        .addHeader("Authorization", workerAuthHeader)
                        .addHeader("apikey", apiKey)
                        .delete()
                        .build()
                    if (executeAndVerify(workerSingleWithKey, targetKey, "Worker single delete (apikey)", allow404AsDeleted = true)) {
                        return true
                    }

                    val workerBatchWithKey = Request.Builder()
                        .url(workerBatchUrl)
                        .addHeader("User-Agent", "YouSnap-Android/1.0")
                        .addHeader("Authorization", workerAuthHeader)
                        .addHeader("apikey", apiKey)
                        .delete(batchPayloadJson.toRequestBody(jsonMediaType))
                        .build()
                    if (executeAndVerify(workerBatchWithKey, targetKey, "Worker batch delete (apikey)", allow404AsDeleted = true)) {
                        return true
                    }
                }
                return false
            }

            suspend fun attemptDeleteForKey(targetKey: String): Boolean {
                val encodedPath = android.net.Uri.encode(targetKey, "/")
                val batchPayloadJson = org.json.JSONObject().apply {
                    put("prefixes", JSONArray().apply { put(targetKey) })
                }.toString()
                val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                // 1. Direct Supabase Storage Single & Batch DELETE (do NOT short-circuit on 404 here,
                // because Supabase returns 404 not_found when anon lacks SELECT/DELETE RLS!)
                if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                    val directSingleBuilder = Request.Builder()
                        .url(SupabaseStorageService.DIRECT_SUPABASE_ENDPOINT + encodedPath)
                        .addHeader("apikey", apiKey)
                        .delete()
                    if (!directAuthHeader.isNullOrBlank()) {
                        directSingleBuilder.addHeader("Authorization", directAuthHeader)
                    }
                    if (executeAndVerify(directSingleBuilder.build(), targetKey, "Direct single delete", allow404AsDeleted = false)) {
                        return true
                    }

                    val directBatchBuilder = Request.Builder()
                        .url(directBatchUrl)
                        .addHeader("apikey", apiKey)
                        .delete(batchPayloadJson.toRequestBody(jsonMediaType))
                    if (!directAuthHeader.isNullOrBlank()) {
                        directBatchBuilder.addHeader("Authorization", directAuthHeader)
                    }
                    if (executeAndVerify(directBatchBuilder.build(), targetKey, "Direct batch delete", allow404AsDeleted = false)) {
                        return true
                    }
                }

                // 2. Cloudflare Worker Single & Batch DELETE (authenticated with Firebase Auth ID Token)
                val initialTokenHeader = SupabaseStorageService.getWorkerAuthorizationHeader(forceRefresh = false)
                if (!initialTokenHeader.isNullOrBlank()) {
                    sawWorkerAuthFailure = false
                    if (tryWorkerDeleteWithToken(targetKey, encodedPath, batchPayloadJson, jsonMediaType, initialTokenHeader)) {
                        return true
                    }
                    if (sawWorkerAuthFailure || lastCode == 401 || lastCode == 403) {
                        val refreshedTokenHeader = SupabaseStorageService.getWorkerAuthorizationHeader(forceRefresh = true)
                        if (!refreshedTokenHeader.isNullOrBlank() && refreshedTokenHeader != initialTokenHeader) {
                            if (tryWorkerDeleteWithToken(targetKey, encodedPath, batchPayloadJson, jsonMediaType, refreshedTokenHeader)) {
                                return true
                            }
                        }
                    }
                } else {
                    android.util.Log.w("SupabaseRepository", "Skipped Worker delete fallback: no authenticated Firebase user token")
                }

                return false
            }

            if (attemptDeleteForKey(cleanName)) {
                return@withContext DeleteResult(
                    isSuccess = true,
                    responseCode = lastCode.takeIf { it > 0 } ?: 200,
                    details = "$lastLabel succeeded"
                )
            }

            // Fallback: cross-check live bucket listing in case exact object key differs (e.g., encoding or prefix)
            val folderPrefix = if (cleanName.contains("/")) cleanName.substringBeforeLast("/") else ""
            val listedObjects = try {
                val rootList = listMediaFiles(limit = 1000, prefix = "").orEmpty()
                val subList = if (folderPrefix.isNotBlank()) listMediaFiles(limit = 1000, prefix = folderPrefix).orEmpty() else emptyList()
                (rootList + subList)
            } catch (_: Exception) {
                emptyList()
            }
            val shortClean = cleanName.substringAfterLast("/")
            val matchingListed = listedObjects.firstOrNull { obj ->
                obj.name.equals(cleanName, ignoreCase = true) ||
                    obj.name.substringAfterLast("/").equals(shortClean, ignoreCase = true)
            }
            if (matchingListed != null && matchingListed.name != cleanName) {
                if (attemptDeleteForKey(matchingListed.name)) {
                    return@withContext DeleteResult(
                        isSuccess = true,
                        responseCode = lastCode.takeIf { it > 0 } ?: 200,
                        details = "Deleted matched bucket object ${matchingListed.name}"
                    )
                }
            }

            // If we explicitly got a 404/object-not-found on public Storage ONLY when Worker didn't reject with 401/403/405
            // and direct didn't fail with RLS 403, confirm whether file is genuinely absent publicly.
            if (lastCode != 401 && lastCode != 403 && lastCode != 405 && checkPublicFileExists(cleanName) == false) {
                return@withContext DeleteResult(
                    isSuccess = true,
                    responseCode = 200,
                    details = "Confirmed absent from Supabase Storage"
                )
            }

            val errorSummary = when {
                !SupabaseStorageService.HAS_VALID_SUPABASE_KEY && SupabaseStorageService.getFirebaseIdToken(false) == null ->
                    "Missing both SUPABASE_API_KEY and Firebase Auth session for Worker delete."
                !SupabaseStorageService.HAS_VALID_SUPABASE_KEY && (lastCode == 401 || lastCode == 403 || lastCode == 405) ->
                    "SUPABASE_API_KEY not set in Secrets and Worker rejected DELETE (HTTP $lastCode)."
                lastCode == 401 || lastBody.contains("Invalid Compact JWS", ignoreCase = true) ->
                    "Storage authentication failed (HTTP $lastCode). Check Firebase session / SUPABASE_API_KEY."
                lastCode == 403 || lastBody.contains("row-level security", ignoreCase = true) || lastBody == "[]" ->
                    "Supabase Storage DELETE RLS policy is blocking removal on 'YouSnap' bucket (HTTP $lastCode)."
                lastCode == 405 ->
                    "Cloudflare Worker does not allow DELETE (HTTP 405) and direct DELETE is blocked."
                else -> "Remote delete failed ($lastLabel HTTP $lastCode: ${lastBody.take(120)})"
            }

            DeleteResult(
                isSuccess = false,
                responseCode = lastCode,
                errorMessage = errorSummary,
                details = "$lastLabel -> HTTP $lastCode $lastBody"
            )
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepository", "Delete exception: ${e.message}")
            DeleteResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Network exception during delete"
            )
        }
    }

    /**
     * Verifies directly against the public Supabase Storage object endpoint whether a media file
     * still exists in the bucket (independent of Postgres `storage.objects` SELECT RLS on list).
     *
     * Returns:
     * - `true` when the object is reachable (HTTP 200/206/416)
     * - `false` ONLY when Supabase Storage confirms the object does not exist (not "Bucket not found")
     * - `null` when offline, timed out, bucket is private, or server returned an indeterminate error
     */
    suspend fun checkPublicFileExists(mediaUrlOrName: String): Boolean? = withContext(Dispatchers.IO) {
        if (mediaUrlOrName.isBlank()) return@withContext null
        try {
            val trimmed = mediaUrlOrName.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val isKnownStorageHost =
                    trimmed.contains(CdnConfig.PROJECT_ID, ignoreCase = true) ||
                        trimmed.contains("/storage/v1/object/public/${CdnConfig.BUCKET_NAME}/", ignoreCase = true) ||
                        trimmed.contains("yousnap-cdn.sakinayesmin2203.workers.dev", ignoreCase = true)
                if (!isKnownStorageHost) return@withContext null
            } else if (
                trimmed.startsWith("/") ||
                trimmed.startsWith("content:", ignoreCase = true) ||
                trimmed.startsWith("file:", ignoreCase = true)
            ) {
                return@withContext null
            }

            val cleanName = extractObjectKey(trimmed)
            if (cleanName.isBlank()) return@withContext null

            val encodedName = android.net.Uri.encode(cleanName, "/")
            val cacheBuster = System.currentTimeMillis()
            val uncachedPublicUrl = "${CdnConfig.STORAGE_BASE_URL}$encodedName?cb=$cacheBuster"

            val headBuilder = Request.Builder()
                .url(uncachedPublicUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .addHeader("Pragma", "no-cache")
                .head()
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

            // Confirm 400/404/405 with a 1-byte cache-busted Range GET so we can inspect Supabase JSON error payload
            val rangeUrl = "${CdnConfig.STORAGE_BASE_URL}$encodedName?cb=${cacheBuster + 1}"
            val rangeBuilder = Request.Builder()
                .url(rangeUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .addHeader("Pragma", "no-cache")
                .addHeader("Range", "bytes=0-0")
                .get()
            if (SupabaseStorageService.HAS_VALID_SUPABASE_KEY) {
                rangeBuilder.addHeader("apikey", SupabaseStorageService.API_KEY)
            }

            client.newCall(rangeBuilder.build()).execute().use { getResp ->
                if (getResp.isSuccessful || getResp.code == 416) return@withContext true
                val body = getResp.body?.string().orEmpty()
                if (body.contains("Bucket not found", ignoreCase = true)) {
                    return@withContext null
                }
                if (getResp.code == 404) return@withContext false
                if (getResp.code == 400) {
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

    private fun parseObjectList(raw: String, prefix: String = ""): List<SupabaseStoredObject>? {
        if (raw.isBlank() || !raw.trim().startsWith("[")) return null
        val array = JSONArray(raw)
        val items = mutableListOf<SupabaseStoredObject>()
        val ownerRegex = Regex("^(?:yousnap|story)__([a-zA-Z0-9_]+)__\\d{10,15}\\.")
        val normalizedPrefix = prefix.trim().trim('/')
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawName = obj.optString("name", "")
            if (rawName.isBlank() || rawName.endsWith("/") || rawName.startsWith(".")) continue
            val fullName = if (normalizedPrefix.isNotBlank() && !rawName.startsWith("$normalizedPrefix/")) {
                "$normalizedPrefix/$rawName"
            } else {
                rawName
            }

            val extractedTs = Regex("(\\d{10,15})").find(rawName)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: 0L
            val extractedOwner = ownerRegex.find(rawName)?.groupValues?.getOrNull(1)
            items.add(
                SupabaseStoredObject(
                    name = fullName,
                    publicUrl = CdnConfig.getFullMediaUrl(fullName),
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
    suspend fun listMediaFiles(limit: Int = 1000, prefix: String = ""): List<SupabaseStoredObject>? = withContext(Dispatchers.IO) {
        val apiKey = SupabaseStorageService.API_KEY
        val normalizedPrefix = prefix.trim().trim('/')
        val payload = org.json.JSONObject().apply {
            put("prefix", normalizedPrefix)
            put("limit", limit)
            put("offset", 0)
            put("sortBy", org.json.JSONObject().apply {
                put("column", "created_at")
                put("order", "desc")
            })
        }.toString()
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
                        directResult = parseObjectList(raw, normalizedPrefix)
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

        // Fallback to Cloudflare Worker list endpoint authenticated with Firebase Auth ID Token
        val workerAuthHeader = SupabaseStorageService.getWorkerAuthorizationHeader()
        if (!workerAuthHeader.isNullOrBlank()) {
            try {
                val workerListUrl = "https://yousnap-cdn.sakinayesmin2203.workers.dev/storage/v1/object/list/${SupabaseStorageService.BUCKET_NAME}"
                val body = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val workerReqBuilder = Request.Builder()
                    .url(workerListUrl)
                    .addHeader("User-Agent", "YouSnap-Android/1.0")
                    .addHeader("Authorization", workerAuthHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(body)

                if (apiKey.isNotBlank()) {
                    workerReqBuilder.addHeader("apikey", apiKey)
                }

                client.newCall(workerReqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val workerItems = parseObjectList(response.body?.string().orEmpty(), normalizedPrefix)
                        if (!workerItems.isNullOrEmpty()) {
                            return@withContext workerItems
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SupabaseRepository", "Worker list fallback notice: ${e.message}")
            }
        }

        directResult
    }
}
