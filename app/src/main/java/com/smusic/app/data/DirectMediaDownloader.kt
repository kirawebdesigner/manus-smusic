package com.smusic.app.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DirectMediaDownloader {
    data class Result(val bytes: Long, val totalBytes: Long, val mimeType: String?)

    suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long, speedBytesPerSecond: Long) -> Unit): Result {
        target.parentFile?.mkdirs()
        val existing = if (target.exists()) target.length() else 0L
        var connection = open(url, if (existing > 0) existing else null)
        var append = existing > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (existing > 0 && !append) { connection.disconnect(); target.delete(); connection = open(url, null) }
        val total = when {
            connection.contentLengthLong > 0 && append -> existing + connection.contentLengthLong
            connection.contentLengthLong > 0 -> connection.contentLengthLong
            else -> -1L
        }
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        val started = System.nanoTime()
        var downloaded = if (append) existing else 0L
        connection.inputStream.use { input -> target.outputStream().buffered(64 * 1024).use { output ->
            if (append) output.write(ByteArray(0))
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                downloaded += count
                val elapsedSeconds = ((System.nanoTime() - started) / 1_000_000_000L).coerceAtLeast(1L)
                onProgress(downloaded, total, downloaded / elapsedSeconds)
            }
        } }
        connection.disconnect()
        if (!target.exists() || target.length() == 0L) throw IOException("The downloaded file was empty")
        return Result(downloaded, total, connection.contentType)
    }

    private fun open(url: String, rangeStart: Long?): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true; requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "Smusic/0.1 Android")
        if (rangeStart != null) setRequestProperty("Range", "bytes=$rangeStart-")
    }
}
