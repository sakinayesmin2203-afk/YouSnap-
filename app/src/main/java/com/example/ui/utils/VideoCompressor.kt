package com.example.ui.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

object VideoCompressor {
    fun compressVideo(
        context: Context,
        inputUri: Uri,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val outputDir = context.cacheDir
        val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onSuccess(outputFile)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: androidx.media3.transformer.ExportException) {
                onError(exportException)
            }
        })

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            onError(e)
        }
    }
}
