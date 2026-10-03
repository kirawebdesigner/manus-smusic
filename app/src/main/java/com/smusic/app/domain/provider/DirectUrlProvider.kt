package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.TrackMetadata
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectUrlProvider : MediaProvider {
    override val id: String = "direct_url"
    override val displayName: String = "Direct HTTPS URL"
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.METADATA_DISCOVERY,
        ProviderCapability.DIRECT_DOWNLOAD,
        ProviderCapability.FORMAT_SELECTION
    )

    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "avi")
    private val supportedExtensions = audioExtensions + videoExtensions

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim()
        val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        val path = uri.path ?: return false
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in supportedExtensions
    }

    override suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val uri = runCatching { URI.create(trimmed) }.getOrNull()
            ?: return@withContext AnalysisResult.Invalid("Invalid URL format.")
        val path = uri.path ?: ""
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in supportedExtensions) {
            return@withContext AnalysisResult.Unsupported("This file extension ($ext) is not directly supported.")
        }

        val isVideo = ext in videoExtensions
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.AUDIO
        val rawFileName = path.substringAfterLast('/')
        val titleCandidate = rawFileName
            .substringBeforeLast('.')
            .replace(Regex("[-_+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Direct Media Stream" }
        val title = titleCandidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val host = uri.host ?: "direct-source"

        val probe = probeHead(trimmed)
        val formats = buildFormats(ext, mediaType, probe.contentLengthBytes, probe.mimeType)

        val metadata = TrackMetadata(
            title = title,
            artist = host,
            genre = if (isVideo) "Video" else "Audio"
        )

        val media = MediaInfo(
            id = trimmed,
            title = title,
            uploader = host,
            source = "Direct Link",
            duration = "--:--",
            fileSize = probe.readableSize,
            mediaType = mediaType,
            originalUrl = trimmed,
            thumbnail = null,
            formats = formats,
            metadata = metadata,
            isDownloadable = true
        )

        AnalysisResult.Success(media, formats)
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    private fun buildFormats(ext: String, mediaType: MediaType, bytes: Long, probedMime: String?): List<MediaFormat> {
        val sizeLabel = if (bytes > 0) formatBytes(bytes) else "Size unknown"
        val formats = mutableListOf<MediaFormat>()

        if (mediaType == MediaType.VIDEO) {
            val mime = probedMime ?: "video/$ext"
            formats.add(
                MediaFormat(
                    id = "source_video",
                    label = "Original Video",
                    container = ext.uppercase(),
                    mimeType = mime,
                    codec = if (ext == "webm") "VP9/Opus" else "H.264/AAC",
                    resolution = "Source resolution",
                    bitrate = "Direct stream",
                    fileSize = sizeLabel,
                    fileSizeBytes = bytes,
                    mediaType = MediaType.VIDEO
                )
            )
            formats.add(
                MediaFormat(
                    id = "audio_extract",
                    label = "Audio Only",
                    container = "M4A",
                    mimeType = "audio/mp4",
                    codec = "AAC",
                    bitrate = "256 kbps",
                    fileSize = if (bytes > 0) formatBytes((bytes * 0.25).toLong()) else "Dynamic",
                    fileSizeBytes = (bytes * 0.25).toLong(),
                    mediaType = MediaType.AUDIO
                )
            )
        } else {
            val mime = probedMime ?: "audio/$ext"
            formats.add(
                MediaFormat(
                    id = "source_audio",
                    label = "Original Audio",
                    container = ext.uppercase(),
                    mimeType = mime,
                    codec = when (ext) {
                        "flac" -> "FLAC Lossless"
                        "ogg", "opus" -> "Opus"
                        "m4a", "aac" -> "AAC"
                        else -> "MP3"
                    },
                    bitrate = "Source stream",
                    fileSize = sizeLabel,
                    fileSizeBytes = bytes,
                    mediaType = MediaType.AUDIO
                )
            )
        }

        return formats
    }

    private fun probeHead(url: String): ProbeResult {
        return runCatching {
            var connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Mozilla/5.0 Smusic/1.0")
            }

            var code = connection.responseCode
            // Follow redirect if HEAD didn't follow automatically
            if (code in 300..399) {
                val newUrl = connection.getHeaderField("Location")
                if (!newUrl.isNullOrBlank()) {
                    connection.disconnect()
                    connection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        instanceFollowRedirects = true
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("User-Agent", "Mozilla/5.0 Smusic/1.0")
                    }
                    code = connection.responseCode
                }
            }

            val length = connection.contentLengthLong.coerceAtLeast(0L)
            val mime = connection.contentType?.substringBefore(';')?.trim()
            val acceptsRanges = connection.getHeaderField("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
            connection.disconnect()

            ProbeResult(
                contentLengthBytes = length,
                mimeType = mime,
                readableSize = if (length > 0) formatBytes(length) else "Size unknown",
                acceptsRanges = acceptsRanges
            )
        }.getOrElse {
            ProbeResult(0L, null, "Size unknown", false)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    private data class ProbeResult(
        val contentLengthBytes: Long,
        val mimeType: String?,
        val readableSize: String,
        val acceptsRanges: Boolean
    )
}
