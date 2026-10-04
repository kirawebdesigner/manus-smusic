package com.smusic.app.domain.engine

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Streams a remote media file to local storage over HTTP(S).
 *
 * Resume semantics:
 * - If a `.part` file exists, the engine sends `Range: bytes=<existing>-` and appends on HTTP 206.
 * - If the server answers with a full-body response (typically 200), the `.part` file is discarded
 *   and the download restarts from byte zero, so a full body is never appended onto a partial file.
 * - On HTTP 416, the `Content-Range: bytes *\/<total>` header decides: if the `.part` file already
 *   holds exactly <total> bytes it is verified and finalized; otherwise the engine fails with a
 *   descriptive error and preserves the partial file for a later retry.
 *
 * Redirect strategy:
 * - Automatic following is disabled; redirects are followed manually so the `Range` header is
 *   re-attached on every hop. Relative `Location` targets are resolved against the current URL.
 *   Loops, malformed targets, and non-http(s) schemes are rejected.
 */
class DownloadEngine(
    private val bufferSize: Int = 64 * 1024,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRedirects: Int = 5,
    private val progressIntervalMs: Long = 250
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

        var connection: HttpURLConnection? = null
        try {
            connection = resolveRedirects(url, existingBytes)
            val responseCode = connection.responseCode

            if (existingBytes > 0 && responseCode == 416 /* HTTP_RANGE_NOT_SATISFIABLE */) {
                return handleRangeNotSatisfiable(partFile, targetFile, existingBytes, connection)
            }

            val isResuming = existingBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (existingBytes > 0 && !isResuming) {
                // Full-body response (typically 200): the server ignored the Range header,
                // so the partial file must be discarded instead of appended to.
                partFile.delete()
            }

            if (responseCode !in 200..299) {
                throw classifyHttpError(responseCode)
            }

            val serverContentLength = connection.contentLengthLong
            val totalBytes = when {
                serverContentLength > 0 && isResuming -> existingBytes + serverContentLength
                serverContentLength > 0 -> serverContentLength
                else -> -1L
            }

            var downloadedBytes = if (isResuming) existingBytes else 0L
            var lastReportTime = System.currentTimeMillis()
            var bytesSinceLastReport = 0L
            var smoothedSpeed = 0L

            connection.inputStream.use { input ->
                // append = isResuming: continue the partial file, or start a fresh one.
                FileOutputStream(partFile, isResuming).use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastReport += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsedSinceReport = now - lastReportTime
                        if (elapsedSinceReport >= progressIntervalMs) {
                            val rawSpeed = if (elapsedSinceReport > 0) (bytesSinceLastReport * 1000) / elapsedSinceReport else 0L
                            // Exponential moving average (30/70) so one fast or slow
                            // sampling window can't make the reported speed jump wildly.
                            smoothedSpeed = if (smoothedSpeed == 0L) {
                                rawSpeed
                            } else {
                                (rawSpeed * 3 + smoothedSpeed * 7) / 10
                            }
                            val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                            onProgress(
                                ProgressUpdate(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSecond = smoothedSpeed,
                                    progress = progress
                                )
                            )

                            lastReportTime = now
                            bytesSinceLastReport = 0L
                        }
                    }
                    output.flush()
                }
            }

            if (!partFile.exists() || partFile.length() == 0L) {
                throw PermanentDownloadException(
                    "Downloaded content is empty.",
                    "The server sent an empty file."
                )
            }

            finalizePartFile(partFile, targetFile)

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
            // Runs on success, failure, and coroutine cancellation alike: no leaked sockets or fds.
            connection?.disconnect()
        }
    }

    private fun handleRangeNotSatisfiable(
        partFile: File,
        targetFile: File,
        existingBytes: Long,
        connection: HttpURLConnection
    ): DownloadResult {
        val totalFromServer = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
        if (totalFromServer != null && existingBytes == totalFromServer && partFile.length() == totalFromServer) {
            // The partial file already contains the complete resource; verify and finalize it.
            finalizePartFile(partFile, targetFile)
            return DownloadResult(
                success = true,
                downloadedBytes = existingBytes,
                totalBytes = existingBytes,
                mimeType = null,
                targetFile = targetFile
            )
        }
        throw PermanentDownloadException(
            "Server rejected range request (HTTP 416): local $existingBytes bytes vs server total " +
                "${totalFromServer ?: "unknown"} bytes.",
            "The saved partial file doesn't match the server's file. Retry to restart the download."
        )
    }

    /** Maps an HTTP error status to a transient or permanent failure. */
    private fun classifyHttpError(code: Int): DownloadException {
        val message = "Server returned HTTP error code: $code"
        return when {
            code == 408 || code == 429 || code in 500..599 ->
                TransientDownloadException(message, "The server is busy (HTTP $code). Smusic will retry automatically.")
            code == 404 || code == 410 ->
                PermanentDownloadException(message, "This file isn't available on the server anymore (HTTP $code).")
            code == 401 || code == 403 ->
                PermanentDownloadException(message, "The server refused access to this file (HTTP $code).")
            else ->
                PermanentDownloadException(message, "The server rejected the download request (HTTP $code).")
        }
    }

    private fun finalizePartFile(partFile: File, targetFile: File) {
        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            // Fallback copy if rename fails across partitions
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val normalized = header.trim().removePrefix("bytes").trim()
        return when {
            normalized.startsWith("*/") -> normalized.substringAfter("*/").toLongOrNull()
            '/' in normalized -> normalized.substringAfter('/').toLongOrNull()
            else -> null
        }
    }

    /**
     * Opens connections along a redirect chain manually and returns the first non-3xx connection.
     * The [rangeStart] value (existing `.part` size) is re-attached as the Range header on every
     * hop so a resumed download stays resumable through redirects.
     */
    private fun resolveRedirects(startUrl: String, rangeStart: Long?): HttpURLConnection {
        var currentUrl = startUrl
        val visited = mutableSetOf(currentUrl)
        repeat(maxRedirects) {
            val connection = openConnection(currentUrl, rangeStart)
            val responseCode = connection.responseCode
            if (responseCode !in 300..399) return connection

            val nextUrl = runCatching { resolveRedirectTarget(currentUrl, connection.getHeaderField("Location")) }
                .getOrElse {
                    throw PermanentDownloadException(
                        "Malformed redirect from $currentUrl: ${it.message}",
                        "This link redirects to a broken address."
                    )
                }
            connection.disconnect()
            if (!visited.add(nextUrl)) {
                throw PermanentDownloadException(
                    "Redirect loop detected at $nextUrl",
                    "This link redirects endlessly and can't be downloaded."
                )
            }
            currentUrl = nextUrl
        }
        throw PermanentDownloadException(
            "Too many redirects (limit: $maxRedirects)",
            "This link redirects too many times and can't be downloaded."
        )
    }

    private fun resolveRedirectTarget(currentUrl: String, location: String?): String {
        if (location.isNullOrBlank()) {
            throw IOException("Redirect response missing Location header")
        }
        val resolved = URI(currentUrl).resolve(URI(location.trim()))
        val scheme = resolved.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IOException("Unsupported redirect scheme: ${scheme ?: "none"}")
        }
        return resolved.toString()
    }

    private fun openConnection(url: String, rangeStart: Long?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            // Redirects are followed manually by resolveRedirects() so the Range header
            // survives each hop.
            instanceFollowRedirects = false
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
