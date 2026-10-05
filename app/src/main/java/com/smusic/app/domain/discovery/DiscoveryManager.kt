package com.smusic.app.domain.discovery

import com.smusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates official discovery providers without exposing transport details to UI. */
class DiscoveryManager(
    private val spotifyToken: suspend () -> String? = { null },
    private val youtubeKey: String = BuildConfig.SMUSIC_YOUTUBE_API_KEY,
    private val spotifyClientId: String = BuildConfig.SMUSIC_SPOTIFY_CLIENT_ID
) {
    private val providers: List<DiscoveryProvider> = listOf(
        YouTubeDataProvider(youtubeKey),
        SpotifyWebApiProvider(spotifyClientId, spotifyToken)
    )

    suspend fun execute(input: String): DiscoveryResult = withContext(Dispatchers.IO) {
        val value = input.trim()
        if (value.isBlank()) return@withContext DiscoveryResult.Failed("Enter a name or paste a source link.")
        val provider = providers.firstOrNull { it.canResolve(value) }
        return@withContext if (provider != null) {
            provider.resolve(value)
        } else {
            val searchable = providers.filter { it.canSearch() }
            if (searchable.isEmpty()) {
                DiscoveryResult.Unsupported("Add a YouTube API key or connect Spotify to search by name.")
            } else {
                // Search the configured providers in stable order and merge only successful items.
                val results = searchable.map { it.search(value) }
                val items = results.filterIsInstance<DiscoveryResult.Success>().flatMap { it.page.items }
                if (items.isNotEmpty()) {
                    DiscoveryResult.Success(DiscoveryPage(DiscoveryKind.SEARCH, value, items = items))
                } else {
                    results.firstOrNull { it !is DiscoveryResult.Unsupported } ?: results.first()
                }
            }
        }
    }
}
