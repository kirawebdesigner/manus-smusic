package com.smusic.app.domain.model

enum class MediaType { AUDIO, VIDEO }

data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val releaseYear: Int? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val coverUrl: String? = null,
    val isrc: String? = null
)

data class MediaFormat(
    val id: String,
    val label: String,
    val container: String,
    val mimeType: String,
    val codec: String? = null,
    val resolution: String? = null,
    val bitrate: String? = null,
    val fileSize: String,
    val fileSizeBytes: Long = 0L,
    val mediaType: MediaType = MediaType.AUDIO,
    val isDownloadable: Boolean = true
)

data class MediaInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val source: String,
    val duration: String,
    val fileSize: String,
    val mediaType: MediaType,
    val originalUrl: String,
    /** Server- or URL-derived filename (with extension) when the source exposes one. */
    val fileName: String? = null,
    val thumbnail: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    val metadata: TrackMetadata? = null,
    /** Whether the source advertised HTTP range support (resume), when known. */
    val supportsRangeRequests: Boolean? = null,
    val isDownloadable: Boolean = true
)

enum class StorageCategory {
    MUSIC,
    VIDEOS,
    OTHER
}

data class DownloadDestination(
    val category: StorageCategory,
    val subFolder: String? = null,
    val customPath: String? = null
)
