package com.smusic.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smusic.app.R
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.database.SmusicDatabase
import com.smusic.app.data.settings.AppSettings
import com.smusic.app.domain.engine.DownloadConcurrencyLimiter
import com.smusic.app.domain.engine.DownloadEngine
import com.smusic.app.domain.engine.DownloadException
import com.smusic.app.domain.engine.PermanentDownloadException
import com.smusic.app.domain.engine.TransientDownloadException
import com.smusic.app.domain.manager.DownloadEvents
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.processor.MediaMetadataReader
import com.smusic.app.domain.processor.MediaSniffer
import com.smusic.app.domain.storage.StorageManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Foreground worker that streams one queued download to storage.
 *
 * Failure policy: [PermanentDownloadException] fails the job immediately;
 * transient I/O failures retry at most [MAX_ATTEMPTS] times (only when the
 * auto-retry setting is on), each retry showing the job back in the queue with
 * a friendly message. Cancellation is idempotent — it never overwrites a
 * terminal state.
 */
class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val database = SmusicDatabase(appContext)
    private val storageManager = StorageManager(appContext)
    private val downloadEngine = DownloadEngine()
    private val settings = AppSettings(appContext)

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = database.getJob(jobId) ?: return Result.failure()

        DownloadConcurrencyLimiter.configure(settings.getMaxConcurrent())
        val gate = DownloadConcurrencyLimiter.acquire()
        return try {
            runDownload(jobId, job)
        } catch (cancelled: CancellationException) {
            markCancelled(jobId)
            throw cancelled
        } finally {
            DownloadConcurrencyLimiter.release(gate)
            DownloadEvents.bump()
        }
    }

    private suspend fun runDownload(jobId: String, job: DownloadJob): Result {
        val targetFile = storageManager.resolveTargetFile(
            title = job.mediaInfo.title,
            container = job.selectedFormat.container,
            destination = job.destination,
            mediaType = job.mediaInfo.mediaType,
            suggestedFilename = job.filename
        )
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")

        database.updateJobState(
            id = jobId,
            state = JobState.DOWNLOADING,
            localPath = targetFile.absolutePath,
            tempPath = partFile.absolutePath
        )
        DownloadEvents.bump()

        setForeground(createForegroundInfo(job.mediaInfo.title, 0, "Starting download..."))

        try {
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
                DownloadEvents.bump()

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
                throw PermanentDownloadException(
                    "File integrity check failed (empty or corrupt file).",
                    "The downloaded file is incomplete or corrupt. Retry the download."
                )
            }

            // Give the file a truthful extension based on its actual content when
            // the source exposed no usable filename (e.g. octet-stream downloads).
            val finalFile = enforceKnownExtension(result.targetFile)

            val durationMs = MediaMetadataReader.readDurationMs(finalFile)
            val readableSize = formatBytes(finalFile.length())
            val libraryItem = LibraryItem(
                id = jobId,
                title = job.mediaInfo.title,
                creator = job.mediaInfo.uploader,
                album = job.mediaInfo.metadata?.album ?: "Smusic Downloads",
                source = job.mediaInfo.source,
                durationMs = durationMs,
                fileSizeBytes = finalFile.length(),
                readableSize = readableSize,
                mediaType = job.mediaInfo.mediaType,
                localPath = finalFile.absolutePath,
                mimeType = result.mimeType ?: job.selectedFormat.mimeType,
                addedDate = System.currentTimeMillis()
            )
            database.insertLibraryItem(libraryItem)

            // Notify Android MediaStore / MediaScanner
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(finalFile.absolutePath),
                arrayOf(libraryItem.mimeType)
            ) { _, _ -> }

            database.updateJobState(
                id = jobId,
                state = JobState.COMPLETED,
                progress = 1f,
                downloadedBytes = result.downloadedBytes,
                totalBytes = result.totalBytes,
                localPath = finalFile.absolutePath,
                completedAt = System.currentTimeMillis()
            )
            DownloadEvents.bump()

            return Result.success(workDataOf("localPath" to finalFile.absolutePath))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (permanent: PermanentDownloadException) {
            failJob(jobId, permanent)
            return Result.failure(workDataOf("error" to permanent.userMessage))
        } catch (transient: TransientDownloadException) {
            return retryOrFail(jobId, transient)
        } catch (io: IOException) {
            // Connection resets, timeouts, DNS failures: transient by nature.
            return retryOrFail(
                jobId,
                TransientDownloadException(
                    io.message ?: "I/O error",
                    "Couldn't reach the server. Check your connection and try again.",
                    io
                )
            )
        } catch (error: Exception) {
            return retryOrFail(
                jobId,
                TransientDownloadException(
                    error.message ?: error.javaClass.simpleName,
                    "Something went wrong during the download.",
                    error
                )
            )
        }
    }

    private suspend fun retryOrFail(jobId: String, cause: DownloadException): Result {
        val mayRetry = settings.isAutoRetryEnabled() && runAttemptCount < MAX_ATTEMPTS - 1
        return if (mayRetry) {
            // Back to the visible queue while WorkManager waits out the backoff.
            database.updateJobState(
                id = jobId,
                state = JobState.QUEUED,
                error = cause.userMessage
            )
            DownloadEvents.bump()
            Result.retry()
        } else {
            failJob(jobId, cause)
            Result.failure(workDataOf("error" to cause.userMessage))
        }
    }

    private fun failJob(jobId: String, cause: DownloadException) {
        database.updateJobState(
            id = jobId,
            state = JobState.FAILED,
            error = cause.userMessage
        )
        // Technical detail goes to logcat; the UI only sees cause.userMessage.
        android.util.Log.w(TAG, "Download $jobId failed: ${cause.message}", cause)
        DownloadEvents.bump()
    }

    /** Idempotent: only cancels jobs that are still in a cancellable state. */
    private fun markCancelled(jobId: String) {
        val current = database.getJob(jobId)?.state ?: return
        if (current in CANCELLABLE_STATES) {
            database.updateJobState(
                id = jobId,
                state = JobState.CANCELLED,
                error = "Cancelled by user"
            )
        }
    }

    /**
     * Renames files that ended up with a generic extension (`.bin`) to the
     * container actually detected in their bytes, so Media3 can route them to
     * the right renderer. Already-known extensions are left untouched.
     */
    private fun enforceKnownExtension(file: File): File {
        val currentExt = file.extension.lowercase()
        if (currentExt in KNOWN_MEDIA_EXTENSIONS) return file
        val sniffed = MediaSniffer.sniffExtension(file) ?: return file
        if (sniffed == currentExt) return file
        val renamed = File(file.parentFile, "${file.nameWithoutExtension}.$sniffed")
        if (renamed.exists()) return file
        return if (file.renameTo(renamed)) renamed else file
    }

    private fun createForegroundInfo(title: String, progress: Int, statusText: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // minSdk is 26, so NotificationChannel is always available.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smusic Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows real-time download speed and progress"
        }
        manager.createNotificationChannel(channel)

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
        private const val MAX_ATTEMPTS = 3
        private const val TAG = "MediaDownloadWorker"

        private val CANCELLABLE_STATES = setOf(
            JobState.ANALYZING,
            JobState.QUEUED,
            JobState.WAITING,
            JobState.DOWNLOADING,
            JobState.PROCESSING
        )

        private val KNOWN_MEDIA_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus",
            "mp4", "webm", "mkv", "mov", "avi"
        )
    }
}
