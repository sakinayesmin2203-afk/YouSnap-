package com.example.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Custom OkHttp RequestBody that emits smooth, accurate byte-level upload progress (0.0f..1.0f).
 * Supports File-backed streams safely across OkHttp retries as well as direct InputStreams.
 */
class ProgressRequestBody private constructor(
    private val file: File?,
    private val inputStream: InputStream?,
    private val contentType: MediaType?,
    private val knownLength: Long,
    private val onProgress: (progress: Float) -> Unit
) : RequestBody() {

    constructor(
        file: File,
        contentType: MediaType?,
        onProgress: (progress: Float) -> Unit
    ) : this(
        file = file,
        inputStream = null,
        contentType = contentType,
        knownLength = file.length(),
        onProgress = onProgress
    )

    constructor(
        inputStream: InputStream,
        contentType: MediaType?,
        knownLength: Long = -1L,
        onProgress: (progress: Float) -> Unit
    ) : this(
        file = null,
        inputStream = inputStream,
        contentType = contentType,
        knownLength = knownLength,
        onProgress = onProgress
    )

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long {
        if (file != null) return file.length()
        if (knownLength > 0L) return knownLength
        return try {
            val avail = inputStream?.available()?.toLong() ?: -1L
            if (avail > 0L) avail else -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        val totalLength = contentLength()
        val buffer = ByteArray(8192)
        var uploaded = 0L
        var lastReportedPercent = -1
        var lastFlushBytes = 0L

        val stream: InputStream = file?.let { FileInputStream(it) }
            ?: inputStream
            ?: return

        onProgress(0f)

        stream.use { activeStream ->
            var read: Int
            while (activeStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read

                // Flush every ~64KB so TCP transmission paces progress smoothly without buffering everything upfront
                if (uploaded - lastFlushBytes >= 65_536L) {
                    sink.flush()
                    lastFlushBytes = uploaded
                }

                if (totalLength > 0L) {
                    val fraction = (uploaded.toDouble() / totalLength.toDouble()).toFloat().coerceIn(0f, 1f)
                    val percent = (fraction * 100).toInt()
                    if (percent > lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(fraction)
                    }
                }
            }
            sink.flush()
        }

        onProgress(1.0f)
    }
}
