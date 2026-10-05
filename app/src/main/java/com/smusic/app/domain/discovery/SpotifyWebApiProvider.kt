package com.smusic.app.domain.discovery

import com.smusic.app.domain.model.MediaType
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Spotify Web API metadata provider. Audio streams are never requested or downloaded. */
class SpotifyWebApiProvider(
    private val clientId: String,
    private val accessToken: suspend () -> String?
) : DiscoveryProvider {
    override val id: String = "spotify_web_api"
    override val displayName: String = "Spotify (Official Metadata)"

    override fun canSearch(): Boolean = clientId.isNotBlank()

    override fun canResolve(input: String): Boolean {
        val host = runCatching { URI(input.trim()).host?.lowercase() }.getOrNull() ?: return false
        return host == "open.spotify.com" || host == "spotify.link"
    }

    override suspend fun search(query: String): DiscoveryResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext DiscoveryResult.Unsupported("Add a Spotify client ID in local.properties.")
        val token = accessToken() ?: return@withContext DiscoveryResult.Unsupported("Connect Spotify before searching its catalog.")
        runCatching {
            val root = get("search", token, mapOf("q" to query, "type" to "track", "limit" to "20"))
            val tracks = root.optJSONObject("tracks")?.optJSONArray("items")
            val items = tracks?.let { array -> buildList { for (i in 0 until array.length()) add(toItem(array.getJSONObject(i))) } }.orEmpty()
            DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.SEARCH, query, items = items))
        }.getOrElse { DiscoveryResult.Failed("Spotify search failed: ${it.message ?: "unknown error"}") }
    }

    override suspend fun resolve(input: String): DiscoveryResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext DiscoveryResult.Unsupported("Add a Spotify client ID in local.properties.")
        val token = accessToken() ?: return@withContext DiscoveryResult.Unsupported("Connect Spotify before resolving playlists and albums.")
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return@withContext DiscoveryResult.Failed("Invalid Spotify URL.")
        val parts = uri.path.trim('/').split('/')
        val kind = parts.getOrNull(0)
        val id = parts.getOrNull(1)
        if (kind.isNullOrBlank() || id.isNullOrBlank()) return@withContext DiscoveryResult.Failed("Spotify URL is missing an item ID.")
        runCatching {
            when (kind) {
                "track" -> DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.SINGLE, items = listOf(toItem(get("tracks/$id", token)))))
                "album" -> resolveCollection("albums/$id", "tracks", token, DiscoveryKind.PLAYLIST)
                "playlist" -> resolveCollection("playlists/$id", "tracks", token, DiscoveryKind.PLAYLIST)
                else -> DiscoveryResult.Unsupported("Spotify $kind metadata is not supported yet.")
            }
        }.getOrElse { DiscoveryResult.Failed("Spotify lookup failed: ${it.message ?: "unknown error"}") }
    }

    private suspend fun resolveCollection(path: String, child: String, token: String, kind: DiscoveryKind): DiscoveryResult {
        val root = get(path, token)
        val title = root.optString("name", "Spotify collection")
        val tracks = root.optJSONObject(child) ?: JSONObject()
        val array = tracks.optJSONArray("items") ?: return DiscoveryResult.Success(DiscoveryPage(kind, title))
        val entries = buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val track = if (path.startsWith("playlists/")) row.optJSONObject("track") else row
                if (track == null || track.optString("id").isBlank()) add(PlaylistEntry(idFromPath(path), i, null, DiscoveryStatus.UNRESOLVED, "Unavailable Spotify item"))
                else add(PlaylistEntry(idFromPath(path), i, toItem(track), DiscoveryStatus.NEW))
            }
        }
        return DiscoveryResult.Success(DiscoveryPage(kind, title, entries = entries))
    }

    private fun toItem(track: JSONObject): DiscoveryItem {
        val artists = track.optJSONArray("artists")
        val creator = artists?.optJSONObject(0)?.optString("name")?.ifBlank { null }
        val album = track.optJSONObject("album")
        return DiscoveryItem(
            sourceId = track.optString("id"),
            sourceUrl = track.optJSONObject("external_urls")?.optString("spotify") ?: "https://open.spotify.com/track/${track.optString("id")}",
            providerId = id,
            title = track.optString("name", "Spotify track"),
            creator = creator,
            album = album?.optString("name")?.ifBlank { null },
            durationMs = track.optLong("duration_ms", 0L),
            thumbnailUrl = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.ifBlank { null },
            mediaType = MediaType.AUDIO,
            isrc = track.optJSONObject("external_ids")?.optString("isrc")?.ifBlank { null }
        )
    }

    private suspend fun get(path: String, token: String, params: Map<String, String> = emptyMap()): JSONObject {
        val query = if (params.isEmpty()) "" else params.entries.joinToString("&", prefix = "?") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = (URL("https://api.spotify.com/v1/$path$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "Smusic/0.2")
        }
        return try {
            val response = connection
            val body = (if (response.responseCode in 200..299) response.inputStream else response.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (response.responseCode !in 200..299) error("HTTP ${response.responseCode}: ${JSONObject(body).optString("message", "request rejected")}")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun idFromPath(path: String) = path.substringAfter('/').substringBefore('/')
}
