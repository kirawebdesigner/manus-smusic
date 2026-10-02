package com.smusic.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaRepository {
    private val registry = MediaProviderRegistry()
    private val _library = MutableStateFlow<List<MediaItem>>(emptyList())
    val library: StateFlow<List<MediaItem>> = _library.asStateFlow()

    suspend fun analyze(url: String): AnalysisResult = registry.analyze(url)

    suspend fun download(media: MediaItem, format: MediaFormat, onProgress: (Float) -> Unit): DownloadTask {
        var progress = 0f
        while (progress < 1f) {
            delay(80)
            progress = (progress + 0.08f).coerceAtMost(1f)
            onProgress(progress)
        }
        _library.value = listOf(media.copy(fileSize = if (format.kind == MediaKind.AUDIO) "8.4 MB" else "142 MB")) + _library.value.filterNot { it.id == media.id }
        return DownloadTask(media, format, 1f, DownloadState.COMPLETE)
    }
}
