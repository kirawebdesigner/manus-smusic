package com.smusic.app.data

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = MediaDatabase(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val registry = MediaProviderRegistry()

    suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        val basic = registry.analyze(url)
        if (basic !is AnalysisResult.Success) return@withContext basic
        val metadata = probe(url)
        val media = basic.media.copy(fileSize = metadata.sizeLabel, fileSizeBytes = metadata.contentLength, mimeType = metadata.mimeType, id = url)
        AnalysisResult.Success(media, basic.formats.map { it.copy(size = metadata.sizeLabel) })
    }

    fun all(): List<MediaItem> = database.all().mapNotNull { item -> if (item.localPath == null || java.io.File(item.localPath).exists()) item else item.copy(state = DownloadState.FAILED, localPath = null) }
    fun findById(id: String): MediaItem? = database.findById(id)
    fun update(item: MediaItem) = database.upsert(item)
    fun delete(item: MediaItem) { item.localPath?.let { java.io.File(it).delete() }; database.delete(item.id) }

    fun enqueue(media: MediaItem, format: MediaFormat): UUID {
        val existing = database.findByUrl(media.url)
        if (existing?.state == DownloadState.COMPLETE && existing.localPath?.let { java.io.File(it).exists() } == true) return UUID(0L, 0L)
        val queued = media.copy(state = DownloadState.QUEUED, mimeType = media.mimeType ?: mimeFor(format.kind), id = media.url)
        database.upsert(queued)
        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>().setInputData(workDataOf(MediaDownloadWorker.KEY_ID to queued.id, MediaDownloadWorker.KEY_URL to queued.url)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("smusic_download").build()
        workManager.enqueue(request)
        return request.id
    }

    fun work(id: UUID): Flow<WorkInfo?> = workManager.getWorkInfoByIdFlow(id)

    private fun probe(url: String): Probe = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; instanceFollowRedirects = true; connectTimeout = 10_000; readTimeout = 10_000; setRequestProperty("User-Agent", "Smusic/0.1 Android") }
        val code = connection.responseCode
        if (code !in 200..399) throw IllegalStateException("The server rejected this link")
        val length = connection.contentLengthLong.coerceAtLeast(0L)
        val mime = connection.contentType?.substringBefore(';')
        connection.disconnect()
        Probe(mime, length, if (length > 0) readableSize(length) else "Size unknown")
    }.getOrElse { Probe(null, 0L, "Size unknown") }

    private fun mimeFor(kind: MediaKind) = if (kind == MediaKind.AUDIO) "audio/mpeg" else "video/mp4"
    private fun readableSize(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1_000} KB" else "${bytes / 1_000_000} MB"
    private data class Probe(val mimeType: String?, val contentLength: Long, val sizeLabel: String)
}
