package com.example.ui.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import java.io.File

object VideoCompressor {

    fun compressVideo(
        context: Context,
        inputUri: Uri,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cacheFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

        val transformer = Transformer.Builder(context)
            .setTransformationRequest(
                TransformationRequest.Builder()
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    onSuccess(cacheFile)
                }

                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                    onError(exportException)
                }
            })
            .build()

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        try {
            transformer.start(editedMediaItem, cacheFile.absolutePath)
        } catch (e: Exception) {
            onError(e)
        }
    }
}
