package com.smusic.app.domain.discovery

import com.smusic.app.domain.model.MediaType

/** User intent entering the discovery pipeline. */
sealed interface DiscoveryQuery {
    data class Text(val value: String) : DiscoveryQuery
    data class Url(val value: String) : DiscoveryQuery
}

enum class DiscoveryKind { SINGLE, SEARCH, PLAYLIST }

enum class DiscoveryStatus { NEW, DUPLICATE_EXISTING, DUPLICATE_QUEUED, DIFFERENT_VERSION, NEEDS_REVIEW, UNRESOLVED }

data class DiscoveryItem(
    val sourceId: String,
    val sourceUrl: String,
    val providerId: String,
    val title: String,
    val creator: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val isrc: String? = null,
    val version: TrackVersion = TrackVersion.STUDIO,
    val status: DiscoveryStatus = DiscoveryStatus.NEW,
)

data class PlaylistEntry(
    val playlistId: String,
    val position: Int,
    val item: DiscoveryItem?,
    val status: DiscoveryStatus,
    val message: String? = null,
)

data class DiscoveryPage(
    val kind: DiscoveryKind,
    val title: String? = null,
    val items: List<DiscoveryItem> = emptyList(),
    val entries: List<PlaylistEntry> = emptyList(),
)

sealed interface DiscoveryResult {
    data class Success(val page: DiscoveryPage) : DiscoveryResult
    data class Unsupported(val message: String) : DiscoveryResult
    data class Failed(val message: String) : DiscoveryResult
}

interface DiscoveryProvider {
    val id: String
    val displayName: String
    fun canSearch(): Boolean
    fun canResolve(input: String): Boolean
    suspend fun search(query: String): DiscoveryResult
    suspend fun resolve(input: String): DiscoveryResult
}

enum class TrackVersion { STUDIO, LIVE, REMIX, ACOUSTIC, INSTRUMENTAL, EXTENDED, RADIO_EDIT, COVER, DEMO, VIDEO, UNKNOWN }
