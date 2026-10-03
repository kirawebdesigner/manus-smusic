package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.MediaInfo

class ProviderRegistry(
    private val providers: List<MediaProvider> = listOf(
        DirectUrlProvider(),
        SpotifyMetadataProvider()
    )
) {
    fun findProvider(url: String): MediaProvider? {
        val trimmed = url.trim()
        return providers.firstOrNull { it.canHandle(trimmed) }
    }

    suspend fun analyze(url: String): AnalysisResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return AnalysisResult.Invalid("Paste a media URL to begin.")
        }

        val provider = findProvider(trimmed)
            ?: return AnalysisResult.Unsupported("Smusic can't download media from this source. Smusic currently accepts direct HTTPS media links and public Spotify metadata.")

        return provider.analyze(trimmed)
    }

    suspend fun getFormats(media: MediaInfo): List<com.smusic.app.domain.model.MediaFormat> {
        val provider = findProvider(media.originalUrl)
        return provider?.getFormats(media) ?: media.formats
    }
}
