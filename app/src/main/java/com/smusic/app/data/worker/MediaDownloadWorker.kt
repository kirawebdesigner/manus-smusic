package com.smusic.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smusic.app.R
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.database.SmusicDatabase
import com.smusic.app.domain.engine.DownloadEngine
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.domain.storage.StorageManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val database = SmusicDatabase(appContext)
    private val storageManager = StorageManager(appContext)
    private val downloadEngine = DownloadEngine()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = database.getJob(jobId) ?: return Result.failure()

        val destination = job.destination
        val targetFile = storageManager.resolveTargetFile(
            title = job.mediaInfo.title,
            container = job.selectedFormat.container,
            destination = destination,
            mediaType = job.mediaInfo.mediaType
        )

        database.updateJobState(
            id = jobId,
            state = JobState.DOWNLOADING,
            localPath = targetFile.absolutePath
        )

        setForeground(createForegroundInfo(job.mediaInfo.title, 0, "Starting download..."))

        return try {
            val result = downloadEngine.download(
                url = job.mediaInfo.originalUrl,
                targetFile = targetFile
            ) { progressUpdate ->
                val percent = (progressUpdate.progress * 100).toInt()
                val speedMb = "%.1f MB/s".format(progressUpdate.speedBytesPerSecond / 1_048_576.0)

                database.updateJobState(
                    id = jobId,
                    state = JobState.DOWNLOADING,
                    progress = progressUpdate.progress,
                    speedBytes = progressUpdate.speedBytesPerSecond,
                    downloadedBytes = progressUpdate.downloadedBytes,
                    totalBytes = progressUpdate.totalBytes
                )

                setProgressAsync(
                    workDataOf(
                        "progress" to progressUpdate.progress,
                        "speed" to progressUpdate.speedBytesPerSecond,
                        "downloaded" to progressUpdate.downloadedBytes,
                        "total" to progressUpdate.totalBytes
                    )
                )

                setForegroundAsync(
                    createForegroundInfo(
                        title = job.mediaInfo.title,
                        progress = percent,
                        statusText = "$percent% · $speedMb"
                    )
                )
            }

            // Post-download validation and library indexing
            database.updateJobState(
                id = jobId,
                state = JobState.PROCESSING,
                progress = 1f
            )

            if (!storageManager.validateIntegrity(result.targetFile)) {
                throw IOException("File integrity check failed (empty or corrupt file).")
            }

            // Add to persistent Library
            val readableSize = formatBytes(result.downloadedBytes)
            val libraryItem = LibraryItem(
                id = jobId,
                title = job.mediaInfo.title,
                creator = job.mediaInfo.uploader,
                album = job.mediaInfo.metadata?.album ?: "Smusic Downloads",
                source = job.mediaInfo.source,
                fileSizeBytes = result.downloadedBytes,
                readableSize = readableSize,
                mediaType = job.mediaInfo.mediaType,
                localPath = result.targetFile.absolutePath,
                mimeType = result.mimeType ?: job.selectedFormat.mimeType,
                addedDate = System.currentTimeMillis()
            )
            database.insertLibraryItem(libraryItem)

            // Notify Android MediaStore / MediaScanner
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(result.targetFile.absolutePath),
                arrayOf(libraryItem.mimeType)
            ) { _, _ -> }

            database.updateJobState(
                id = jobId,
                state = JobState.COMPLETED,
                progress = 1f,
                downloadedBytes = result.downloadedBytes,
                totalBytes = result.totalBytes,
                localPath = result.targetFile.absolutePath
            )

            Result.success(workDataOf("localPath" to result.targetFile.absolutePath))
        } catch (cancelled: CancellationException) {
            database.updateJobState(
                id = jobId,
                state = JobState.CANCELLED,
                error = "Download cancelled by user"
            )
            throw cancelled
        } catch (error: Exception) {
            database.updateJobState(
                id = jobId,
                state = JobState.FAILED,
                error = error.message ?: "Download failed"
            )

            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (error.message ?: "Download failed")))
            }
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, statusText: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smusic Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download speed and progress"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading: $title")
            .setContentText(statusText)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID + id.hashCode(), notification)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val CHANNEL_ID = "smusic_download_channel"
        private const val NOTIFICATION_ID = 4020
    }
}
