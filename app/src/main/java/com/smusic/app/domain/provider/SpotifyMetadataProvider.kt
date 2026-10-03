package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.TrackMetadata
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SpotifyMetadataProvider : MediaProvider {
    override val id: String = "spotify_metadata"
    override val displayName: String = "Spotify (Metadata Only)"
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.METADATA_DISCOVERY
    )

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.contains("open.spotify.com/track/") ||
                trimmed.contains("open.spotify.com/album/") ||
                trimmed.contains("open.spotify.com/playlist/") ||
                trimmed.contains("spotify.link/")
    }

    override suspend fun analyze(url: String): AnalysisResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val uri = runCatching { URI.create(trimmed) }.getOrNull()
            ?: return@withContext AnalysisResult.Invalid("Invalid Spotify URL format.")

        val path = uri.path ?: ""
        val entityType = when {
            path.contains("/track/") -> "Track"
            path.contains("/album/") -> "Album"
            path.contains("/playlist/") -> "Playlist"
            else -> "Spotify Media"
        }

        // Query public oEmbed endpoint for official title, author, and album artwork
        val oEmbedData = fetchOEmbed(trimmed)
        val title = oEmbedData?.optString("title")?.ifBlank { null }
            ?: run {
                val segment = path.substringAfterLast('/').substringBefore('?')
                "Spotify $entityType ($segment)"
            }
        val thumbnail = oEmbedData?.optString("thumbnail_url")?.ifBlank { null }

        // spotDL pattern: normalize metadata (title, artist, cover)
        val (parsedTitle, parsedArtist) = splitTitleArtist(title)

        val metadata = TrackMetadata(
            title = parsedTitle,
            artist = parsedArtist,
            coverUrl = thumbnail
        )

        val media = MediaInfo(
            id = trimmed,
            title = parsedTitle,
            uploader = parsedArtist,
            source = "Spotify ($entityType)",
            duration = "--:--",
            fileSize = "Metadata only",
            mediaType = MediaType.AUDIO,
            originalUrl = trimmed,
            thumbnail = thumbnail,
            formats = emptyList(),
            metadata = metadata,
            isDownloadable = false
        )

        AnalysisResult.MetadataOnly(
            media = media,
            message = "Smusic can read public Spotify metadata, but direct audio stream downloading from Spotify is not supported."
        )
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = emptyList()

    private fun fetchOEmbed(spotifyUrl: String): JSONObject? {
        return runCatching {
            val encoded = URLEncoder.encode(spotifyUrl, "UTF-8")
            val endpoint = "https://open.spotify.com/oembed?url=$encoded"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "Mozilla/5.0 Smusic/1.0")
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonString = reader.use { it.readText() }
                conn.disconnect()
                JSONObject(jsonString)
            } else {
                conn.disconnect()
                null
            }
        }.getOrNull()
    }

    private fun splitTitleArtist(rawTitle: String): Pair<String, String> {
        // oEmbed titles often look like "Song Name by Artist Name" or "Artist - Track"
        if (rawTitle.contains(" by ")) {
            val parts = rawTitle.split(" by ")
            return (parts[0].trim()) to (parts.getOrElse(1) { "Spotify Artist" }.trim())
        }
        if (rawTitle.contains(" - ")) {
            val parts = rawTitle.split(" - ")
            return (parts[0].trim()) to (parts.getOrElse(1) { "Spotify Artist" }.trim())
        }
        return rawTitle to "Spotify"
    }
}
