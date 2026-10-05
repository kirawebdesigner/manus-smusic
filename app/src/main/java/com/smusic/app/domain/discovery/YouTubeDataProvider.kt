package com.smusic.app.domain.discovery

import com.smusic.app.domain.model.MediaType
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Official YouTube Data API v3 metadata provider. It never returns protected media URLs. */
class YouTubeDataProvider(private val apiKey: String) : DiscoveryProvider {
    override val id: String = "youtube_data_api"
    override val displayName: String = "YouTube (Official Metadata)"

    override fun canSearch(): Boolean = apiKey.isNotBlank()

    override fun canResolve(input: String): Boolean {
        val host = runCatching { URI(input.trim()).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" || host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com"
    }

    override suspend fun search(query: String): DiscoveryResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DiscoveryResult.Unsupported("Add a YouTube Data API key in local.properties to search YouTube.")
        if (query.isBlank()) return@withContext DiscoveryResult.Failed("Enter a YouTube search query.")
        runCatching {
            val root = get("search", mapOf("part" to "snippet", "type" to "video", "q" to query, "maxResults" to "25"))
            val items = root.optJSONArray("items")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                        val snippet = item.optJSONObject("snippet") ?: continue
                        if (id.isNotBlank()) add(toDiscoveryItem(id, snippet))
                    }
                }
            }.orEmpty()
            DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.SEARCH, query, items = items))
        }.getOrElse { DiscoveryResult.Failed("YouTube search failed: ${it.message ?: "unknown error"}") }
    }

    override suspend fun resolve(input: String): DiscoveryResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DiscoveryResult.Unsupported("Add a YouTube Data API key in local.properties to resolve YouTube links.")
        val uri = runCatching { URI(input.trim()) }.getOrNull()
            ?: return@withContext DiscoveryResult.Failed("Invalid YouTube URL.")
        val playlistId = queryValue(uri, "list")
        if (playlistId != null) return@withContext resolvePlaylist(playlistId, input)
        val videoId = when (uri.host?.lowercase()) {
            "youtu.be" -> uri.path.trim('/').substringBefore('/')
            else -> queryValue(uri, "v")
        }
        if (videoId.isNullOrBlank()) return@withContext DiscoveryResult.Failed("The YouTube URL does not contain a video or playlist ID.")
        runCatching {
            val root = get("videos", mapOf("part" to "snippet,contentDetails", "id" to videoId))
            val item = root.optJSONArray("items")?.optJSONObject(0)
                ?: return@runCatching DiscoveryResult.Failed("YouTube video not found or unavailable.")
            val snippet = item.optJSONObject("snippet") ?: JSONObject()
            val details = item.optJSONObject("contentDetails") ?: JSONObject()
            val result = toDiscoveryItem(videoId, snippet, parseDuration(details.optString("duration")))
            DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.SINGLE, result.title, items = listOf(result)))
        }.getOrElse { DiscoveryResult.Failed("YouTube lookup failed: ${it.message ?: "unknown error"}") }
    }

    private suspend fun resolvePlaylist(playlistId: String, sourceUrl: String): DiscoveryResult {
        return runCatching {
            val playlist = get("playlists", mapOf("part" to "snippet", "id" to playlistId))
            val title = playlist.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("snippet")?.optString("title") ?: "YouTube playlist"
            val entries = mutableListOf<PlaylistEntry>()
            var token: String? = null
            var position = 0
            do {
                val params = mutableMapOf("part" to "snippet,contentDetails", "playlistId" to playlistId, "maxResults" to "50")
                token?.let { params["pageToken"] = it }
                val page = get("playlistItems", params)
                val array = page.optJSONArray("items")
                if (array != null) for (index in 0 until array.length()) {
                    if (entries.size >= MAX_PLAYLIST_ITEMS) break
                    val row = array.getJSONObject(index)
                    val snippet = row.optJSONObject("snippet") ?: continue
                    val id = row.optJSONObject("contentDetails")?.optString("videoId").orEmpty()
                    if (id.isBlank()) {
                        entries += PlaylistEntry(playlistId, position++, null, DiscoveryStatus.UNRESOLVED, "Unavailable playlist item")
                    } else {
                        entries += PlaylistEntry(playlistId, position++, toDiscoveryItem(id, snippet), DiscoveryStatus.NEW)
                    }
                }
                token = page.optString("nextPageToken").ifBlank { null }
            } while (token != null && entries.size < MAX_PLAYLIST_ITEMS)
            DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.PLAYLIST, title, entries = entries))
        }.getOrElse { DiscoveryResult.Failed("YouTube playlist lookup failed: ${it.message ?: "unknown error"}") }
    }

    private fun toDiscoveryItem(id: String, snippet: JSONObject, durationMs: Long = 0L) = DiscoveryItem(
        sourceId = id,
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        providerId = this.id,
        title = snippet.optString("title", "YouTube video"),
        creator = snippet.optString("channelTitle").ifBlank { null },
        durationMs = durationMs,
        thumbnailUrl = snippet.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")?.ifBlank { null },
        mediaType = MediaType.VIDEO
    )

    private fun get(path: String, params: Map<String, String>): JSONObject {
        val query = (params + ("key" to apiKey)).entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = (URL("https://www.googleapis.com/youtube/v3/$path?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Smusic/0.2")
        }
        return try {
            val response = connection
            val body = (if (response.responseCode in 200..299) response.inputStream else response.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (response.responseCode !in 200..299) error("HTTP ${response.responseCode}: ${JSONObject(body).optJSONObject("error")?.optString("message") ?: "request rejected"}")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun queryValue(uri: URI, key: String): String? = uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == key }?.substringAfter('=', "")?.ifBlank { null }

    companion object {
        private const val MAX_PLAYLIST_ITEMS = 200
        fun parseDuration(value: String): Long {
            val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(value) ?: return 0L
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: 0L
            val seconds = match.groupValues[3].toLongOrNull() ?: 0L
            return (hours * 3600 + minutes * 60 + seconds) * 1000
        }
    }
}
