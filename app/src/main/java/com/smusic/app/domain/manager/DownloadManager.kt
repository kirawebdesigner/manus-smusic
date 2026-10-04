package com.smusic.app.domain.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.database.SmusicDatabase
import com.smusic.app.data.settings.AppSettings
import com.smusic.app.data.worker.MediaDownloadWorker
import com.smusic.app.domain.engine.DownloadConcurrencyLimiter
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.domain.processor.MediaMetadataReader
import com.smusic.app.domain.provider.ProviderRegistry
import com.smusic.app.domain.storage.StorageManager
import com.smusic.app.domain.storage.StorageUsage
import com.smusic.app.domain.util.Formatters
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the persistent download queue.
 *
 * Durable state lives in SQLite; execution lives in WorkManager (unique work
 * per job, so duplicate workers are impossible). The UI observes [queueFlow] /
 * [libraryFlow], which refresh whenever a worker bumps [DownloadEvents].
 * Providers only describe media — this class owns the download lifecycle.
 */
class DownloadManager(context: Context) {

    private val appContext = context.applicationContext
    private val database = SmusicDatabase(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val providerRegistry = ProviderRegistry()
    private val storageManager = StorageManager(appContext)

    val settings = AppSettings(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queueFlow = MutableStateFlow<List<DownloadJob>>(emptyList())
    val queueFlow: StateFlow<List<DownloadJob>> = _queueFlow.asStateFlow()

    private val _libraryFlow = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryFlow: StateFlow<List<LibraryItem>> = _libraryFlow.asStateFlow()

    private val _libraryLoaded = MutableStateFlow(false)
    val libraryLoaded: StateFlow<Boolean> = _libraryLoaded.asStateFlow()

    init {
        DownloadConcurrencyLimiter.configure(settings.getMaxConcurrent())
        scope.launch {
            recoverInterruptedJobs()
            scanLibrary()
            refreshStateNow()
            _libraryLoaded.value = true
        }
        // Workers bump DownloadEvents after every database write; refresh here
        // so completions/progress reach the UI without polling. The collector
        // suspends during each refresh, so bursts of bumps self-throttle.
        DownloadEvents.revision
            .onEach { refreshStateNow() }
            .launchIn(scope)
    }

    suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        providerRegistry.analyze(url)
    }

    suspend fun enqueueDownload(
        media: MediaInfo,
        format: MediaFormat,
        destination: DownloadDestination = DownloadDestination(StorageCategory.MUSIC)
    ): String = withContext(Dispatchers.IO) {
        val request = providerRegistry.resolveMedia(media, format)
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val job = DownloadJob(
            id = jobId,
            mediaInfo = media,
            selectedFormat = format,
            destination = destination,
            state = JobState.QUEUED,
            filename = request.suggestedFilename,
            createdAt = now,
            updatedAt = now
        )

        database.enqueueJob(job)
        enqueueWork(jobId)
        DownloadEvents.bump()
        refreshState()
        jobId
    }

    /** Idempotent: terminal jobs (completed/failed/cancelled) are never clobbered. */
    fun cancelJob(jobId: String) {
        scope.launch {
            val job = database.getJob(jobId) ?: return@launch
            if (job.state in TERMINAL_STATES) return@launch
            workManager.cancelAllWorkByTag(jobTag(jobId))
            database.updateJobState(
                id = jobId,
                state = JobState.CANCELLED,
                error = "Cancelled by user"
            )
            DownloadEvents.bump()
            refreshState()
        }
    }

    /** Idempotent: only failed or cancelled jobs are re-queued. */
    fun retryJob(jobId: String) {
        scope.launch {
            val job = database.getJob(jobId) ?: return@launch
            if (job.state !in setOf(JobState.FAILED, JobState.CANCELLED)) return@launch
            database.retryJob(jobId)
            enqueueWork(jobId)
            DownloadEvents.bump()
            refreshState()
        }
    }

    /** Removes the queue record (and any staging file). Finalized media stays. */
    fun removeJob(jobId: String) {
        scope.launch {
            val job = database.getJob(jobId)
            workManager.cancelAllWorkByTag(jobTag(jobId))
            job?.let { cleanStagingFiles(it) }
            database.removeJob(jobId)
            DownloadEvents.bump()
            refreshState()
        }
    }

    fun clearCompleted() {
        scope.launch {
            database.clearCompletedJobs()
            DownloadEvents.bump()
            refreshState()
        }
    }

    fun toggleFavorite(itemId: String) {
        scope.launch {
            database.toggleFavorite(itemId)
            DownloadEvents.bump()
            refreshState()
        }
    }

    fun deleteLibraryItem(itemId: String) {
        scope.launch {
            database.deleteLibraryItem(itemId)
            DownloadEvents.bump()
            refreshState()
        }
    }

    /** Records a playback start (play count + last played). */
    fun recordPlay(itemId: String) {
        scope.launch { database.recordPlay(itemId) }
    }

    fun searchLibrary(query: String): List<LibraryItem> {
        return if (query.isBlank()) {
            database.getAllLibraryItems()
        } else {
            database.searchLibrary(query)
        }
    }

    /** Deletes leftover `.part` files. Safe to call from any dispatcher. */
    suspend fun clearTemporaryFiles(): Int = withContext(Dispatchers.IO) {
        val removed = storageManager.clearTemporaryFiles()
        refreshState()
        removed
    }

    /** Recomputes per-category storage usage from disk (off the main thread). */
    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        storageManager.computeStorageUsage()
    }

    fun refreshState() {
        scope.launch { refreshStateNow() }
    }

    private suspend fun refreshStateNow() {
        val jobs = database.getAllJobs()
        val library = database.getAllLibraryItems()
        _queueFlow.value = jobs
        _libraryFlow.value = library
    }

    fun getWorkInfo(jobId: String): Flow<WorkInfo?> {
        return workManager.getWorkInfosByTagFlow(jobTag(jobId)).map { it.firstOrNull() }
    }

    // --- Internals ---------------------------------------------------------------

    /**
     * Re-enqueues jobs whose worker died with the process (app closed mid
     * download) and marks jobs that keep getting interrupted as failed so the
     * user can retry manually. Jobs with live work are left alone.
     */
    private suspend fun recoverInterruptedJobs() {
        val pending = database.getJobsInStates(
            JobState.ANALYZING,
            JobState.QUEUED,
            JobState.WAITING,
            JobState.DOWNLOADING,
            JobState.PROCESSING
        )
        for (job in pending) {
            val unfinished = runCatching {
                workManager.getWorkInfosByTag(jobTag(job.id)).get()
                    .any { !it.state.isFinished }
            }.getOrDefault(true) // can't verify → assume WorkManager still owns it

            if (unfinished) continue

            if (job.retryCount < MAX_RECOVERY_ATTEMPTS) {
                database.incrementRetryCount(job.id)
                database.updateJobState(
                    id = job.id,
                    state = JobState.QUEUED,
                    error = "Recovered after an interruption"
                )
                enqueueWork(job.id)
            } else {
                database.updateJobState(
                    id = job.id,
                    state = JobState.FAILED,
                    error = "The download was interrupted repeatedly. Tap retry to restart it."
                )
            }
        }
    }

    /**
     * Startup library scan: prunes rows whose files were deleted outside the
     * app and recovers orphaned media files that never got a library row.
     */
    private suspend fun scanLibrary() {
        database.cleanStaleRecords()

        val knownPaths = database.getAllLibraryItems().map { it.localPath }.toSet()
        storageManager.scanMediaFiles().forEach { scanned ->
            if (scanned.file.absolutePath in knownPaths) return@forEach
            database.insertLibraryItem(
                LibraryItem(
                    id = "file:${scanned.file.absolutePath}",
                    title = scanned.title,
                    creator = "Unknown artist",
                    album = "Smusic Library",
                    source = "Local file",
                    durationMs = MediaMetadataReader.readDurationMs(scanned.file),
                    fileSizeBytes = scanned.sizeBytes,
                    readableSize = Formatters.formatBytes(scanned.sizeBytes),
                    mediaType = scanned.mediaType,
                    localPath = scanned.file.absolutePath,
                    mimeType = scanned.mimeType,
                    addedDate = scanned.file.lastModified().takeIf { it > 0 }
                        ?: System.currentTimeMillis()
                )
            )
        }
    }

    private fun enqueueWork(jobId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.isWifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(workDataOf(MediaDownloadWorker.KEY_JOB_ID to jobId))
            .setConstraints(constraints)
            .addTag(jobTag(jobId))
            .build()

        // Unique per job: a job can never have two workers running at once.
        workManager.enqueueUniqueWork(jobTag(jobId), ExistingWorkPolicy.KEEP, request)
    }

    private fun cleanStagingFiles(job: DownloadJob) {
        job.tempPath?.let { runCatching { File(it).delete() } }
        if (job.state != JobState.COMPLETED) {
            job.localPath?.let { runCatching { File("$it.part").delete() } }
        }
    }

    private fun jobTag(jobId: String) = "smusic_job_$jobId"

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 3

        private val TERMINAL_STATES = setOf(
            JobState.COMPLETED,
            JobState.FAILED,
            JobState.CANCELLED
        )
    }
}
