package com.smusic.app.domain.model

/**
 * A download-ready description of one media candidate, produced by a provider's
 * `resolveMedia`. Providers never own the download lifecycle: the request is
 * handed to [com.smusic.app.domain.manager.DownloadManager], which persists it
 * and schedules WorkManager execution.
 */
data class DownloadRequest(
    val url: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val container: String,
    /** Server- or URL-derived filename (with extension) when known. */
    val suggestedFilename: String?,
    val providerId: String,
    val fileSizeBytes: Long = 0L
)
