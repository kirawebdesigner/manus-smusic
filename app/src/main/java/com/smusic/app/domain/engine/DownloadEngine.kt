package com.smusic.app.domain.engine

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DownloadEngine(
    private val bufferSize: Int = 64 * 1024,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) {
    data class ProgressUpdate(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long,
        val progress: Float
    )

    data class DownloadResult(
        val success: Boolean,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val mimeType: String?,
        val targetFile: File,
        val error: String? = null
    )

    suspend fun download(
        url: String,
        targetFile: File,
        onProgress: (ProgressUpdate) -> Unit
    ): DownloadResult {
        targetFile.parentFile?.mkdirs()
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")

        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        var connection = openConnection(url, rangeStart = if (existingBytes > 0) existingBytes else null)
        var responseCode = connection.responseCode

        // Follow HTTP redirects explicitly if needed
        var redirectCount = 0
        while (responseCode in 300..399 && redirectCount < 5) {
            val location = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            connection = openConnection(location, rangeStart = if (existingBytes > 0) existingBytes else null)
            responseCode = connection.responseCode
            redirectCount++
        }

        // Determine if server supported range request
        val isResuming = existingBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
        if (existingBytes > 0 && !isResuming) {
            // Server did not support Range, restart from byte 0
            connection.disconnect()
            partFile.delete()
            connection = openConnection(url, rangeStart = null)
            responseCode = connection.responseCode
        }

        if (responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("Server returned HTTP error code: $responseCode")
        }

        val serverContentLength = connection.contentLengthLong
        val totalBytes = when {
            serverContentLength > 0 && isResuming -> existingBytes + serverContentLength
            serverContentLength > 0 -> serverContentLength
            else -> -1L
        }

        var downloadedBytes = if (isResuming) existingBytes else 0L
        val startTime = System.currentTimeMillis()
        var lastReportTime = startTime
        var bytesSinceLastReport = 0L

        try {
            connection.inputStream.use { input ->
                partFile.outputStream().let { if (isResuming) partFile.outputStream() /* append handled by FileOutputStream(partFile, true) */ else partFile.outputStream() }
                val output = java.io.FileOutputStream(partFile, isResuming).buffered(bufferSize)
                output.use { stream ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break

                        stream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastReport += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsedSinceReport = now - lastReportTime
                        if (elapsedSinceReport >= 250) {
                            val speed = if (elapsedSinceReport > 0) (bytesSinceLastReport * 1000) / elapsedSinceReport else 0L
                            val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                            onProgress(
                                ProgressUpdate(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSecond = speed,
                                    progress = progress
                                )
                            )

                            lastReportTime = now
                            bytesSinceLastReport = 0L
                        }
                    }
                    stream.flush()
                }
            }

            if (!partFile.exists() || partFile.length() == 0L) {
                throw IOException("Downloaded content is empty.")
            }

            // Rename .part to targetFile
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across partitions
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            val finalProgress = ProgressUpdate(
                downloadedBytes = downloadedBytes,
                totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                speedBytesPerSecond = 0L,
                progress = 1f
            )
            onProgress(finalProgress)

            return DownloadResult(
                success = true,
                downloadedBytes = downloadedBytes,
                totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                mimeType = connection.contentType?.substringBefore(';'),
                targetFile = targetFile
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, rangeStart: Long?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Smusic/1.0")
            if (rangeStart != null && rangeStart > 0) {
                setRequestProperty("Range", "bytes=$rangeStart-")
            }
        }
    }
}
