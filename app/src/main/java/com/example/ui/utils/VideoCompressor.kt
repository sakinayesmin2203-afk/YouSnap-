package com.example.ui.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

sealed class CompressionState {
    data class Progress(val fraction: Float) : CompressionState()
    data class Completed(
        val file: File,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long
    ) : CompressionState()
    data class Error(val exception: Exception) : CompressionState()
}

object VideoCompressor {

    private const val DEFAULT_MAX_SHORT_SIDE_PX = 720
    private const val DEFAULT_TARGET_VIDEO_BITRATE_BPS = 2_200_000 // ~2.2 Mbps for crisp 720p mobile video
    private const val SKIP_COMPRESSION_MAX_BYTES = 2_500_000L // 2.5 MB threshold if already <= 720p

    /**
     * Compresses large videos using AndroidX Media3 Transformer while preserving visual quality
     * and emitting live compression progress (0.0f..1.0f) via Kotlin Flow.
     */
    @OptIn(UnstableApi::class)
    fun compressVideoFlow(
        context: Context,
        inputUri: Uri,
        maxShortSidePx: Int = DEFAULT_MAX_SHORT_SIDE_PX,
        targetBitrateBps: Int = DEFAULT_TARGET_VIDEO_BITRATE_BPS
    ): Flow<CompressionState> = callbackFlow {
        val appContext = context.applicationContext
        val outputFile = File(appContext.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        val mainHandler = Handler(Looper.getMainLooper())

        val videoMeta = withContext(Dispatchers.IO) { inspectVideo(appContext, inputUri) }
        val originalSize = videoMeta.sizeBytes

        // Fast-path only if the file is already compact (<= 2.5 MB) and resolution <= 720p
        val alreadyCompact = originalSize in 1L..SKIP_COMPRESSION_MAX_BYTES &&
            videoMeta.shortSide in 1..maxShortSidePx &&
            (videoMeta.bitrate <= 0 || videoMeta.bitrate <= targetBitrateBps)

        if (alreadyCompact) {
            val copiedFile = withContext(Dispatchers.IO) {
                copyUriToFile(appContext, inputUri, outputFile)
            }
            if (copiedFile != null && copiedFile.length() > 0L) {
                trySend(CompressionState.Progress(1.0f))
                trySend(
                    CompressionState.Completed(
                        file = copiedFile,
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = copiedFile.length()
                    )
                )
                close()
                awaitClose { }
                return@callbackFlow
            }
        }

        // Build resolution scaling effect (keep aspect ratio, downscale only if larger than 720p)
        val videoEffects = mutableListOf<Effect>()
        if (videoMeta.effectiveWidth > 0 && videoMeta.effectiveHeight > 0 && videoMeta.shortSide > maxShortSidePx) {
            val scale = maxShortSidePx.toFloat() / videoMeta.shortSide.toFloat()
            val targetWidth = makeEven((videoMeta.effectiveWidth * scale).roundToInt().coerceAtLeast(2))
            val targetHeight = makeEven((videoMeta.effectiveHeight * scale).roundToInt().coerceAtLeast(2))
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    targetWidth,
                    targetHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        } else {
            // Ensure at most 720p height when metadata dimensions are unavailable
            if (videoMeta.shortSide == 0) {
                videoEffects.add(Presentation.createForHeight(maxShortSidePx))
            }
        }

        val effectiveBitrate = if (videoMeta.bitrate in 1 until targetBitrateBps) {
            videoMeta.bitrate
        } else {
            targetBitrateBps
        }

        var transformer: Transformer? = null
        var isFinished = false
        val progressHolder = ProgressHolder()

        val progressPollRunnable = object : Runnable {
            override fun run() {
                if (isFinished) return
                val activeTransformer = transformer ?: return
                try {
                    val state = activeTransformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val fraction = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                        trySend(CompressionState.Progress(fraction))
                    }
                    if (!isFinished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 100L)
                    } else if (!isFinished) {
                        mainHandler.postDelayed(this, 150L)
                    }
                } catch (_: Exception) {
                    if (!isFinished) {
                        mainHandler.postDelayed(this, 200L)
                    }
                }
            }
        }

        mainHandler.post {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(appContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(effectiveBitrate)
                            .build()
                    )
                    .setEnableFallback(true)
                    .build()

                val builtTransformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            isFinished = true
                            mainHandler.removeCallbacks(progressPollRunnable)
                            val compressedSize = if (outputFile.exists()) outputFile.length() else 0L
                            if (compressedSize > 0L) {
                                trySend(CompressionState.Progress(1.0f))
                                trySend(
                                    CompressionState.Completed(
                                        file = outputFile,
                                        originalSizeBytes = if (originalSize > 0L) originalSize else compressedSize,
                                        compressedSizeBytes = compressedSize
                                    )
                                )
                            } else {
                                trySend(CompressionState.Error(IllegalStateException("Compressed video output file is empty")))
                            }
                            close()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            isFinished = true
                            mainHandler.removeCallbacks(progressPollRunnable)
                            runCatching { outputFile.delete() }
                            trySend(CompressionState.Error(exportException))
                            close()
                        }
                    })
                    .build()

                transformer = builtTransformer

                val mediaItem = MediaItem.fromUri(inputUri)
                val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItem)
                if (videoEffects.isNotEmpty()) {
                    editedMediaItemBuilder.setEffects(Effects(emptyList(), videoEffects))
                }

                trySend(CompressionState.Progress(0.01f))
                builtTransformer.start(editedMediaItemBuilder.build(), outputFile.absolutePath)
                mainHandler.postDelayed(progressPollRunnable, 100L)
            } catch (e: Exception) {
                isFinished = true
                mainHandler.removeCallbacks(progressPollRunnable)
                runCatching { outputFile.delete() }
                trySend(CompressionState.Error(e))
                close()
            }
        }

        awaitClose {
            isFinished = true
            mainHandler.post {
                mainHandler.removeCallbacks(progressPollRunnable)
                runCatching { transformer?.cancel() }
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Convenience callback wrapper for compressing video with Media3 Transformer.
     */
    fun compressVideo(
        context: Context,
        inputUri: Uri,
        onProgress: (Float) -> Unit = {},
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val appContext = context.applicationContext
        val outputFile = File(appContext.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        val mainHandler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var transformer: Transformer? = null
        var isFinished = false

        val pollRunnable = object : Runnable {
            override fun run() {
                if (isFinished) return
                val active = transformer ?: return
                try {
                    val state = active.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    if (!isFinished) {
                        mainHandler.postDelayed(this, 100L)
                    }
                } catch (_: Exception) {
                    if (!isFinished) mainHandler.postDelayed(this, 200L)
                }
            }
        }

        mainHandler.post {
            try {
                val meta = inspectVideo(appContext, inputUri)
                val videoEffects = mutableListOf<Effect>()
                if (meta.effectiveWidth > 0 && meta.effectiveHeight > 0 && meta.shortSide > DEFAULT_MAX_SHORT_SIDE_PX) {
                    val scale = DEFAULT_MAX_SHORT_SIDE_PX.toFloat() / meta.shortSide.toFloat()
                    val targetWidth = makeEven((meta.effectiveWidth * scale).roundToInt().coerceAtLeast(2))
                    val targetHeight = makeEven((meta.effectiveHeight * scale).roundToInt().coerceAtLeast(2))
                    videoEffects.add(
                        Presentation.createForWidthAndHeight(
                            targetWidth,
                            targetHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                }

                val encoderFactory = DefaultEncoderFactory.Builder(appContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(DEFAULT_TARGET_VIDEO_BITRATE_BPS)
                            .build()
                    )
                    .setEnableFallback(true)
                    .build()

                val built = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            isFinished = true
                            mainHandler.removeCallbacks(pollRunnable)
                            onProgress(1.0f)
                            onSuccess(outputFile)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            isFinished = true
                            mainHandler.removeCallbacks(pollRunnable)
                            runCatching { outputFile.delete() }
                            onError(exportException)
                        }
                    })
                    .build()

                transformer = built
                val mediaItem = MediaItem.fromUri(inputUri)
                val editedBuilder = EditedMediaItem.Builder(mediaItem)
                if (videoEffects.isNotEmpty()) {
                    editedBuilder.setEffects(Effects(emptyList(), videoEffects))
                }
                built.start(editedBuilder.build(), outputFile.absolutePath)
                mainHandler.postDelayed(pollRunnable, 100L)
            } catch (e: Exception) {
                isFinished = true
                mainHandler.removeCallbacks(pollRunnable)
                runCatching { outputFile.delete() }
                onError(e)
            }
        }
    }

    private data class VideoMetadata(
        val effectiveWidth: Int = 0,
        val effectiveHeight: Int = 0,
        val bitrate: Int = 0,
        val sizeBytes: Long = 0L
    ) {
        val shortSide: Int get() = min(effectiveWidth, effectiveHeight)
    }

    private fun inspectVideo(context: Context, uri: Uri): VideoMetadata {
        var width = 0
        var height = 0
        var rotation = 0
        var bitrate = 0
        var sizeBytes = 0L

        try {
            if (uri.scheme == "file" && uri.path != null) {
                sizeBytes = File(uri.path!!).length()
            } else {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            sizeBytes = cursor.getLong(idx)
                        }
                    }
                }
                if (sizeBytes <= 0L) {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.length > 0L) sizeBytes = afd.length
                    }
                }
            }
        } catch (_: Exception) {}

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val swapped = rotation == 90 || rotation == 270
        val effectiveW = if (swapped) height else width
        val effectiveH = if (swapped) width else height
        return VideoMetadata(effectiveW, effectiveH, bitrate, sizeBytes)
    }

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        } catch (_: Exception) {
            null
        }
    }

    private fun makeEven(value: Int): Int {
        return if (value % 2 == 0) value else value - 1
    }
}
