package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
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
}
