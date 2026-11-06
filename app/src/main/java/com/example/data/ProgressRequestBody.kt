package com.example.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

class ProgressRequestBody(
    private val inputStream: InputStream,
    private val contentType: MediaType?,
    private val knownLength: Long = -1L,
    private val onProgress: (progress: Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long {
        if (knownLength > 0L) return knownLength
        return try {
            val avail = inputStream.available().toLong()
            if (avail > 0L) avail else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(2048)
        val totalLength = contentLength()
        var uploaded = 0L

        inputStream.use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                if (totalLength > 0) {
                    onProgress(uploaded.toFloat() / totalLength)
                }
            }
        }
    }
}
