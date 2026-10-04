package com.smusic.app.domain.model

enum class JobState {
    QUEUED,
    ANALYZING,
    WAITING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}

data class DownloadJob(
    val id: String,
    val mediaInfo: MediaInfo,
    val selectedFormat: MediaFormat,
    val destination: DownloadDestination,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val speedBytesPerSecond: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val localPath: String? = null,
    /** Final `.part` staging path while the download runs (for recovery/cleanup). */
    val tempPath: String? = null,
    /** Server/URL-derived filename (with extension) when known. */
    val filename: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
