package com.smusic.app.data

import android.net.Uri

sealed interface AnalysisResult {
    data class Success(val media: MediaItem, val formats: List<MediaFormat>) : AnalysisResult
    data class Unsupported(val message: String) : AnalysisResult
    data class Invalid(val message: String) : AnalysisResult
}

data class MediaItem(
    val id: String,
    val title: String,
    val creator: String,
    val source: String,
    val duration: String,
    val fileSize: String,
    val kind: MediaKind,
    val url: String,
    val addedLabel: String = "Just now",
)

data class MediaFormat(val label: String, val detail: String, val size: String, val kind: MediaKind)
enum class MediaKind { AUDIO, VIDEO }

enum class DownloadState { IDLE, DOWNLOADING, COMPLETE, FAILED }

data class DownloadTask(val media: MediaItem, val format: MediaFormat, val progress: Float, val state: DownloadState)

interface MediaProvider {
    fun canHandle(uri: Uri): Boolean
    fun analyze(url: String): AnalysisResult
}

class DirectMediaProvider : MediaProvider {
    private val extensions = setOf("mp3", "m4a", "aac", "wav", "mp4", "webm", "mkv", "mov")
    override fun canHandle(uri: Uri): Boolean = uri.scheme == "https" && uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in extensions

    override fun analyze(url: String): AnalysisResult {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return AnalysisResult.Invalid("That link is not a valid URL.")
        if (!canHandle(uri)) return AnalysisResult.Unsupported("This source isn't supported for downloading yet. Smusic currently accepts direct media links.")
        val name = uri.lastPathSegment?.substringBeforeLast('.')?.replace('-', ' ')?.replace('_', ' ')?.ifBlank { "Untitled media" } ?: "Untitled media"
        val isVideo = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in setOf("mp4", "webm", "mkv", "mov")
        val item = MediaItem(uri.toString(), name.replaceFirstChar { it.uppercase() }, uri.host ?: "Direct source", "Direct URL", "--:--", "Unknown size", if (isVideo) MediaKind.VIDEO else MediaKind.AUDIO, url)
        val formats = if (isVideo) listOf(MediaFormat("Original", "Video · source quality", "Unknown size", MediaKind.VIDEO), MediaFormat("Audio only", "AAC · 256 kbps", "Unknown size", MediaKind.AUDIO)) else listOf(MediaFormat("Original", "Audio · source quality", "Unknown size", MediaKind.AUDIO))
        return AnalysisResult.Success(item, formats)
    }
}

class MediaProviderRegistry(private val providers: List<MediaProvider> = listOf(DirectMediaProvider())) {
    fun analyze(url: String): AnalysisResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return AnalysisResult.Invalid("Paste a media URL to begin.")
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return AnalysisResult.Invalid("That link is not a valid URL.")
        return providers.firstOrNull { it.canHandle(uri) }?.analyze(trimmed)
            ?: AnalysisResult.Unsupported("This source isn't supported for downloading yet. Smusic currently accepts direct media links.")
    }
}
