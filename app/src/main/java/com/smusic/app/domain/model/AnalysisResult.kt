package com.smusic.app.domain.model

import com.smusic.app.domain.provider.ProviderError

sealed interface AnalysisResult {
    data class Success(
        val media: MediaInfo,
        val formats: List<MediaFormat>
    ) : AnalysisResult

    data class MetadataOnly(
        val media: MediaInfo,
        val message: String
    ) : AnalysisResult

    data class Unsupported(
        val message: String
    ) : AnalysisResult

    data class Invalid(
        val message: String
    ) : AnalysisResult

    /** Analysis failed with a categorized, user-facing [ProviderError]. */
    data class Failed(
        val error: ProviderError
    ) : AnalysisResult
}
