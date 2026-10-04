package com.smusic.app.domain.provider

import android.util.Log
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadRequest
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import kotlinx.coroutines.CancellationException

/**
 * Registry of installed [MediaProvider]s. Providers are matched in order;
 * they only describe media — downloads are owned by
 * [com.smusic.app.domain.manager.DownloadManager].
 */
class ProviderRegistry(
    private val providers: List<MediaProvider> = listOf(
        // Checked first so Spotify URLs never fall through to the direct provider.
        SpotifyMetadataProvider(),
        DirectUrlProvider()
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
            ?: return AnalysisResult.Unsupported(
                "Smusic can't download media from this source. Smusic currently accepts " +
                    "direct HTTPS media links and public Spotify metadata."
            )

        return try {
            provider.analyze(trimmed)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Technical detail goes to logcat only; users see a friendly message.
            Log.w(TAG, "Analysis failed for provider ${provider.id}", error)
            AnalysisResult.Failed(ProviderError.AnalysisFailed)
        }
    }

    suspend fun getFormats(media: MediaInfo): List<MediaFormat> {
        val provider = findProvider(media.originalUrl)
        return provider?.getFormats(media) ?: media.formats
    }

    /** Resolves an analyzed candidate into a download-ready request. */
    suspend fun resolveMedia(media: MediaInfo, format: MediaFormat): DownloadRequest {
        val provider = findProvider(media.originalUrl)
        return provider?.resolveMedia(media, format) ?: DownloadRequest(
            url = media.originalUrl,
            mediaType = format.mediaType,
            mimeType = format.mimeType,
            container = format.container,
            suggestedFilename = media.fileName,
            providerId = "unknown",
            fileSizeBytes = format.fileSizeBytes
        )
    }

    companion object {
        private const val TAG = "ProviderRegistry"
    }
}
