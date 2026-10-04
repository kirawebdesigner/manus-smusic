package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadRequest
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo

enum class ProviderCapability {
    METADATA_DISCOVERY,
    DIRECT_DOWNLOAD,
    FORMAT_SELECTION
}

interface MediaProvider {
    val id: String
    val displayName: String
    val capabilities: Set<ProviderCapability>

    fun canHandle(url: String): Boolean
    suspend fun analyze(url: String): AnalysisResult
    suspend fun getFormats(media: MediaInfo): List<MediaFormat>

    /**
     * Resolves an analyzed media candidate plus the user-selected format into a
     * concrete [DownloadRequest]. The provider only describes *what* to
     * download; it never executes or owns the download itself.
     */
    suspend fun resolveMedia(media: MediaInfo, format: MediaFormat): DownloadRequest = DownloadRequest(
        url = media.originalUrl,
        mediaType = format.mediaType,
        mimeType = format.mimeType,
        container = format.container,
        suggestedFilename = media.fileName,
        providerId = id,
        fileSizeBytes = format.fileSizeBytes
    )
}
