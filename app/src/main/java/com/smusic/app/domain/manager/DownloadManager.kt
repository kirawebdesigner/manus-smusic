package com.smusic.app.domain.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.database.SmusicDatabase
import com.smusic.app.data.worker.MediaDownloadWorker
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.domain.provider.ProviderRegistry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DownloadManager(private val context: Context) {

    private val database = SmusicDatabase(context.applicationContext)
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val providerRegistry = ProviderRegistry()

    private val _queueFlow = MutableStateFlow<List<DownloadJob>>(emptyList())
    val queueFlow: Flow<List<DownloadJob>> = _queueFlow.asStateFlow()

    private val _libraryFlow = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryFlow: Flow<List<LibraryItem>> = _libraryFlow.asStateFlow()

    init {
        refreshState()
    }

    suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        providerRegistry.analyze(url)
    }

    fun enqueueDownload(
        media: MediaInfo,
        format: MediaFormat,
        destination: DownloadDestination = DownloadDestination(StorageCategory.MUSIC)
    ): String {
        val jobId = UUID.randomUUID().toString()
        val job = DownloadJob(
            id = jobId,
            mediaInfo = media,
            selectedFormat = format,
            destination = destination,
            state = JobState.QUEUED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        database.enqueueJob(job)

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(workDataOf(MediaDownloadWorker.KEY_JOB_ID to jobId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("smusic_job_$jobId")
            .build()

        workManager.enqueue(request)
        refreshState()
        return jobId
    }

    fun cancelJob(jobId: String) {
        workManager.cancelAllWorkByTag("smusic_job_$jobId")
        database.updateJobState(
            id = jobId,
            state = JobState.CANCELLED,
            error = "Cancelled by user"
        )
        refreshState()
    }

    fun retryJob(jobId: String) {
        val job = database.getJob(jobId) ?: return
        database.retryJob(jobId)

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(workDataOf(MediaDownloadWorker.KEY_JOB_ID to jobId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("smusic_job_$jobId")
            .build()

        workManager.enqueue(request)
        refreshState()
    }

    fun removeJob(jobId: String) {
        workManager.cancelAllWorkByTag("smusic_job_$jobId")
        database.removeJob(jobId)
        refreshState()
    }

    fun clearCompleted() {
        database.clearCompletedJobs()
        refreshState()
    }

    fun toggleFavorite(itemId: String) {
        database.toggleFavorite(itemId)
        refreshState()
    }

    fun deleteLibraryItem(itemId: String) {
        database.deleteLibraryItem(itemId)
        refreshState()
    }

    fun searchLibrary(query: String): List<LibraryItem> {
        return if (query.isBlank()) {
            database.getAllLibraryItems()
        } else {
            database.searchLibrary(query)
        }
    }

    fun refreshState() {
        _queueFlow.value = database.getAllJobs()
        _libraryFlow.value = database.getAllLibraryItems()
    }

    fun getWorkInfo(jobId: String): Flow<WorkInfo?> {
        return workManager.getWorkInfosByTagFlow("smusic_job_$jobId").map { it.firstOrNull() }
    }
}
