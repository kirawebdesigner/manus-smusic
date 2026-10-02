package com.smusic.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.UUID

class MediaDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val repository = MediaRepository(applicationContext)
        val media = repository.findById(id) ?: return Result.failure()
        val target = File(applicationContext.filesDir, "downloads/${safeName(media.title)}.${extension(media.mimeType, media.kind)}")
        repository.update(media.copy(localPath = target.absolutePath, state = DownloadState.DOWNLOADING))
        setForeground(createForegroundInfo(media.title, 0))
        return try {
            val result = DirectMediaDownloader().download(url, target) { downloaded, total, speed ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                setProgressAsync(workDataOf("downloaded" to downloaded, "total" to total, "speed" to speed, "percent" to percent))
                setForegroundAsync(createForegroundInfo(media.title, percent))
            }
            repository.update(media.copy(localPath = target.absolutePath, mimeType = result.mimeType ?: media.mimeType, fileSize = readableSize(result.bytes), fileSizeBytes = result.bytes, state = DownloadState.COMPLETE))
            Result.success(workDataOf("path" to target.absolutePath))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            repository.update(media.copy(localPath = target.absolutePath, state = DownloadState.CANCELLED))
            throw cancelled
        } catch (error: IOException) {
            repository.update(media.copy(localPath = target.absolutePath, state = DownloadState.FAILED))
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to (error.message ?: "Download failed")))
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Smusic downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(com.smusic.app.R.drawable.ic_launcher_foreground).setContentTitle("Downloading $title").setProgress(100, progress, progress == 0).setOngoing(true).build()
        return ForegroundInfo(NOTIFICATION_ID + id.hashCode(), notification)
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { UUID.randomUUID().toString() }
    private fun extension(mime: String?, kind: MediaKind): String = mime?.substringAfterLast('/')?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: if (kind == MediaKind.AUDIO) "mp3" else "mp4"
    private fun readableSize(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1_000} KB" else "${bytes / 1_000_000} MB"
    companion object { const val KEY_ID = "id"; const val KEY_URL = "url"; private const val CHANNEL = "smusic_downloads"; private const val NOTIFICATION_ID = 2010 }
}
