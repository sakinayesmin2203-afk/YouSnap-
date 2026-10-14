package com.example.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

class ProgressRequestBody(
    private val inputStream: InputStream,
    private val contentType: MediaType?,
    private val onProgress: (progress: Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long {
        return try {
            inputStream.available().toLong()
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
