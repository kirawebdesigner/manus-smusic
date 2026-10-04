package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadRequest
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.TrackMetadata
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reference implementation of [MediaProvider].
 *
 * Analyzes legitimate, publicly accessible direct HTTP(S) media URLs by probing
 * the server (HEAD first, ranged GET fallback) and reporting only metadata the
 * server actually exposes. No qualities, codecs, or bitrates are invented.
 */
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

    private val maxRedirects = 5
    private val connectTimeoutMs = 8_000
    private val readTimeoutMs = 8_000
    private val userAgent = "Mozilla/5.0 (Android; Mobile) Smusic/1.0"

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim()
        val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        return !uri.host.isNullOrBlank()
    }

    override suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val uri = runCatching { URI.create(trimmed) }.getOrNull()
            ?: return@withContext AnalysisResult.Failed(ProviderError.InvalidUrl)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return@withContext AnalysisResult.Failed(ProviderError.InvalidUrl)
        }
        if (uri.host.isNullOrBlank()) {
            return@withContext AnalysisResult.Failed(ProviderError.InvalidUrl)
        }

        // Probe the server. HEAD first; many servers reject it, so fall back to a
        // ranged GET that reads headers only (the 1-byte body is never consumed).
        val probe = try {
            val head = runCatching { probeRequest(trimmed, method = "HEAD", range = null) }.getOrNull()
            if (head != null && head.responseCode in 200..299) {
                head
            } else {
                probeRequest(trimmed, method = "GET", range = "bytes=0-0")
            }
        } catch (probeFailure: ProbeFailedException) {
            return@withContext AnalysisResult.Failed(probeFailure.error)
        } catch (networkError: IOException) {
            return@withContext AnalysisResult.Failed(ProviderError.NetworkUnavailable)
        }

        if (probe.responseCode !in 200..299) {
            return@withContext when (probe.responseCode) {
                404, 410 -> AnalysisResult.Failed(ProviderError.MediaUnavailable)
                401, 403, 429 -> AnalysisResult.Failed(ProviderError.ServerRejected)
                451 -> AnalysisResult.Failed(ProviderError.ProtectedContent)
                in 500..599 -> AnalysisResult.Failed(ProviderError.MediaUnavailable)
                else -> AnalysisResult.Failed(ProviderError.ServerRejected)
            }
        }

        val pathDecoded = decodeSafely(uri.path ?: "")
        val filename = filenameFromContentDisposition(probe.contentDisposition)
            ?: pathDecoded.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: filenameFromQuery(uri.rawQuery)
            ?: "smusic_download"

        // Extension: URL path first, then a filename the server itself provided.
        val ext = extensionOf(pathDecoded) ?: extensionOf(filename)
        val rawMime = probe.mimeType?.lowercase()?.substringBefore(';')?.trim()

        val mediaType = classifyMediaType(rawMime, ext)
            ?: return@withContext AnalysisResult.Failed(ProviderError.UnknownMediaType)

        if (rawMime?.startsWith("text/") == true) {
            return@withContext AnalysisResult.Failed(ProviderError.UnknownMediaType)
        }

        val mime = resolveMime(rawMime, ext, mediaType)
        val cleanBaseName = baseNameOf(filename)
        val title = prettifyTitle(cleanBaseName, fallback = uri.host ?: "Direct Media")

        val totalBytes = probe.totalBytes
        val formats = listOf(
            buildSourceFormat(
                mediaType = mediaType,
                container = ext?.uppercase() ?: containerFromMime(mime),
                mimeType = mime,
                bytes = totalBytes
            )
        )

        val metadata = TrackMetadata(
            title = title,
            artist = uri.host ?: "direct-source",
            genre = if (mediaType == MediaType.VIDEO) "Video" else "Audio"
        )

        val media = MediaInfo(
            id = trimmed,
            title = title,
            uploader = uri.host ?: "direct-source",
            source = "Direct Link",
            duration = "--:--",
            fileSize = if (totalBytes > 0) formatBytes(totalBytes) else "Size unknown",
            mediaType = mediaType,
            originalUrl = trimmed,
            fileName = if (filename.contains('.')) filename else "$filename.${containerFromMime(mime).lowercase()}",
            thumbnail = null,
            formats = formats,
            metadata = metadata,
            supportsRangeRequests = probe.acceptsRanges,
            isDownloadable = true
        )

        AnalysisResult.Success(media, formats)
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveMedia(media: MediaInfo, format: MediaFormat): DownloadRequest =
        DownloadRequest(
            url = media.originalUrl,
            mediaType = format.mediaType,
            mimeType = format.mimeType,
            container = format.container,
            suggestedFilename = media.fileName,
            providerId = id,
            fileSizeBytes = format.fileSizeBytes
        )

    // --- Probing -----------------------------------------------------------------

    private data class ProbeResult(
        val responseCode: Int,
        /** Total resource size in bytes when the server reports one, otherwise 0. */
        val totalBytes: Long,
        val mimeType: String?,
        val contentDisposition: String?,
        val acceptsRanges: Boolean,
        val finalUrl: String
    )

    /** Probe-level failure with an already-categorized user-facing error. */
    private class ProbeFailedException(
        val error: ProviderError,
        detail: String
    ) : IOException(detail)

    /**
     * Sends [method] to [startUrl], following redirects manually (max 5 hops,
     * loop detection, http/https only) so probe behavior matches the download
     * engine's redirect policy. Headers only: no response body is ever read.
     */
    private fun probeRequest(startUrl: String, method: String, range: String?): ProbeResult {
        var currentUrl = startUrl
        val visited = mutableSetOf(currentUrl)
        repeat(maxRedirects) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                if (range != null && method == "GET") {
                    setRequestProperty("Range", range)
                }
            }
            try {
                val code = connection.responseCode
                if (code !in 300..399) {
                    val contentRangeTotal = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                    val contentLength = connection.contentLengthLong
                    val total = when {
                        code == HttpURLConnection.HTTP_PARTIAL -> contentRangeTotal ?: 0L
                        contentLength > 0 -> contentLength
                        else -> contentRangeTotal ?: 0L
                    }
                    return ProbeResult(
                        responseCode = code,
                        totalBytes = total,
                        mimeType = connection.contentType?.substringBefore(';')?.trim(),
                        contentDisposition = connection.getHeaderField("Content-Disposition"),
                        acceptsRanges = connection.getHeaderField("Accept-Ranges")
                            ?.equals("bytes", ignoreCase = true) == true || code == HttpURLConnection.HTTP_PARTIAL,
                        finalUrl = currentUrl
                    )
                }
                val location = connection.getHeaderField("Location")
                val nextUrl = resolveRedirect(currentUrl, location)
                if (!visited.add(nextUrl)) {
                    throw ProbeFailedException(
                        ProviderError.ServerRejected,
                        "Redirect loop detected at $nextUrl"
                    )
                }
                currentUrl = nextUrl
            } finally {
                connection.disconnect()
            }
        }
        throw ProbeFailedException(
            ProviderError.ServerRejected,
            "Too many redirects (limit: $maxRedirects)"
        )
    }

    private fun resolveRedirect(currentUrl: String, location: String?): String {
        if (location.isNullOrBlank()) {
            throw ProbeFailedException(ProviderError.ServerRejected, "Redirect response missing Location header")
        }
        val resolved = runCatching { URI(currentUrl).resolve(URI(location.trim())) }
            .getOrElse {
                throw ProbeFailedException(
                    ProviderError.ServerRejected,
                    "Malformed redirect from $currentUrl: ${it.message}"
                )
            }
        val scheme = resolved.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw ProbeFailedException(
                ProviderError.ServerRejected,
                "Unsupported redirect scheme: ${scheme ?: "none"}"
            )
        }
        return resolved.toString()
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val normalized = header.trim().removePrefix("bytes").trim()
        return when {
            normalized.startsWith("*/") -> normalized.substringAfter("*/").toLongOrNull()
            '/' in normalized -> normalized.substringAfter('/').toLongOrNull()
            else -> null
        }
    }

    // --- Classification helpers --------------------------------------------------

    private fun classifyMediaType(mime: String?, ext: String?): MediaType? {
        val m = mime
        return when {
            m != null && m.startsWith("audio/") -> MediaType.AUDIO
            m != null && m.startsWith("video/") -> MediaType.VIDEO
            m == "application/ogg" -> MediaType.AUDIO
            m == "application/mp4" ->
                if (ext in videoExtensions) MediaType.VIDEO else MediaType.AUDIO
            ext != null && ext in audioExtensions -> MediaType.AUDIO
            ext != null && ext in videoExtensions -> MediaType.VIDEO
            else -> null
        }
    }

    private fun resolveMime(rawMime: String?, ext: String?, mediaType: MediaType): String {
        val unusable = rawMime.isNullOrBlank() ||
            rawMime in setOf("application/octet-stream", "binary/octet-stream", "application/binary")
        if (!unusable) return rawMime
        if (ext != null) mimeForExtension(ext)?.let { return it }
        return if (mediaType == MediaType.VIDEO) "video/mp4" else "audio/mpeg"
    }

    private fun mimeForExtension(ext: String): String? = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "application/ogg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        else -> null
    }

    private fun containerFromMime(mime: String): String = when (mime) {
        "audio/mpeg" -> "MP3"
        "audio/mp4", "audio/aac" -> "M4A"
        "audio/wav", "audio/x-wav" -> "WAV"
        "audio/flac" -> "FLAC"
        "application/ogg", "audio/ogg", "audio/opus" -> "OGG"
        "video/mp4" -> "MP4"
        "video/webm" -> "WEBM"
        "video/x-matroska" -> "MKV"
        "video/quicktime" -> "MOV"
        "video/x-msvideo" -> "AVI"
        else -> "BIN"
    }

    private fun extensionOf(path: String): String? {
        val name = path.substringAfterLast('/')
        if ('.' !in name) return null
        val ext = name.substringAfterLast('.').lowercase()
        return ext.takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
    }

    private fun decodeSafely(raw: String): String =
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

    /** Extracts a filename from an RFC 6266 / RFC 5987 Content-Disposition header. */
    private fun filenameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // RFC 5987: filename*=UTF-8''percent-encoded-name
        val extended = Regex("filename\\*\\s*=\\s*[^']*'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        val plain = Regex("filename\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
            ?: Regex("filename\\s*=\\s*([^;\\s]+)", RegexOption.IGNORE_CASE)
                .find(header)?.groupValues?.get(1)
        val candidate = extended ?: plain ?: return null
        val decoded = decodeSafely(candidate.trim().trim('"'))
        return sanitizeFilenameCandidate(decoded)
    }

    /** Looks for a filename in common query parameters (`?file=song.mp3`). */
    private fun filenameFromQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val keys = setOf("filename", "file", "name", "download", "attachment")
        for (pair in rawQuery.split('&')) {
            val key = pair.substringBefore('=').lowercase()
            if (key in keys) {
                val value = pair.substringAfter('=', "")
                val decoded = decodeSafely(value)
                sanitizeFilenameCandidate(decoded)?.let { return it }
            }
        }
        return null
    }

    /** Strips directory components and control characters from a remote filename. */
    private fun sanitizeFilenameCandidate(raw: String): String? {
        val withoutPaths = raw.replace('\\', '/').substringAfterLast('/')
        val withoutControl = withoutPaths.replace(Regex("[\\u0000-\\u001F\\u007F]"), "").trim()
        return withoutControl.takeIf { it.isNotBlank() && it != "." && it != ".." }
    }

    private fun baseNameOf(filename: String): String {
        val base = if ('.' in filename) filename.substringBeforeLast('.') else filename
        return base.takeIf { it.isNotBlank() } ?: "smusic_download"
    }

    private fun prettifyTitle(rawBaseName: String, fallback: String): String {
        val candidate = rawBaseName
            .replace(Regex("[-_+]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
        if (candidate.isBlank()) return fallback
        return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    // --- Format construction -----------------------------------------------------

    /**
     * Builds the single format the source actually provides. No codec,
     * resolution, or bitrate is fabricated when the server doesn't expose one.
     */
    private fun buildSourceFormat(
        mediaType: MediaType,
        container: String,
        mimeType: String,
        bytes: Long
    ): MediaFormat {
        val sizeLabel = if (bytes > 0) formatBytes(bytes) else "Size unknown"
        return MediaFormat(
            id = "source",
            label = if (mediaType == MediaType.VIDEO) "Original Video" else "Original Audio",
            container = container,
            mimeType = mimeType,
            codec = null,
            resolution = null,
            bitrate = null,
            fileSize = sizeLabel,
            fileSizeBytes = bytes,
            mediaType = mediaType,
            isDownloadable = true
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}
