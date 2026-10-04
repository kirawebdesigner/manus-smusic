package com.smusic.app.domain.discovery

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Stable identity fields used before any expensive audio fingerprinting is considered. */
data class MediaIdentity(
    val isrc: String? = null,
    val providerId: String? = null,
    val sourceId: String? = null,
    val canonicalUrl: String? = null,
    val normalizedCreator: String,
    val normalizedTitle: String,
    val durationMs: Long = 0L,
    val version: TrackVersion = TrackVersion.STUDIO,
)

sealed interface DuplicateDecision {
    data object New : DuplicateDecision
    data class Existing(val identity: MediaIdentity) : DuplicateDecision
    data class Queued(val identity: MediaIdentity) : DuplicateDecision
    data object DifferentVersion : DuplicateDecision
    data object NeedsReview : DuplicateDecision
}

object DuplicateResolver {
    private val versionMarkers = mapOf(
        "live" to TrackVersion.LIVE,
        "remix" to TrackVersion.REMIX,
        "acoustic" to TrackVersion.ACOUSTIC,
        "instrumental" to TrackVersion.INSTRUMENTAL,
        "extended" to TrackVersion.EXTENDED,
        "radio edit" to TrackVersion.RADIO_EDIT,
        "cover" to TrackVersion.COVER,
        "demo" to TrackVersion.DEMO,
        "official video" to TrackVersion.VIDEO,
        "music video" to TrackVersion.VIDEO,
    )

    fun identity(item: DiscoveryItem): MediaIdentity = MediaIdentity(
        isrc = item.isrc?.trim()?.uppercase(Locale.ROOT)?.ifBlank { null },
        providerId = item.providerId,
        sourceId = item.sourceId,
        canonicalUrl = canonicalUrl(item.sourceUrl),
        normalizedCreator = normalize(item.creator.orEmpty()),
        normalizedTitle = normalize(removeVersionMarker(item.title)),
        durationMs = item.durationMs,
        version = item.version.takeUnless { it == TrackVersion.STUDIO } ?: detectVersion(item.title),
    )

    fun decide(candidate: DiscoveryItem, existing: Collection<MediaIdentity>, queued: Collection<MediaIdentity>): DuplicateDecision {
        val value = identity(candidate)
        val exact = (existing + queued).firstOrNull { matches(value, it) }
        if (exact != null) return if (existing.contains(exact)) DuplicateDecision.Existing(exact) else DuplicateDecision.Queued(exact)
        val sameRecordingDifferentVersion = (existing + queued).any {
            value.normalizedCreator == it.normalizedCreator &&
                value.normalizedTitle == it.normalizedTitle &&
                closeDuration(value.durationMs, it.durationMs) &&
                value.version != it.version
        }
        if (sameRecordingDifferentVersion) return DuplicateDecision.DifferentVersion
        val probable = (existing + queued).any {
            value.normalizedCreator == it.normalizedCreator &&
                value.normalizedTitle == it.normalizedTitle &&
                !closeDuration(value.durationMs, it.durationMs)
        }
        return if (probable) DuplicateDecision.NeedsReview else DuplicateDecision.New
    }

    fun canonicalUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
        val keep = uri.query.orEmpty().split('&').filter { it.substringBefore('=').lowercase(Locale.ROOT) !in setOf("utm_source", "utm_medium", "utm_campaign", "si", "feature") }.sorted()
        "$scheme://$host$path${keep.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?") ?: ""}"
    }.getOrNull()

    fun normalize(raw: String): String = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun detectVersion(title: String): TrackVersion {
        val normalized = normalize(title)
        return versionMarkers.entries.firstOrNull { normalized.contains(it.key) }?.value ?: TrackVersion.STUDIO
    }

    private fun removeVersionMarker(title: String): String {
        var result = title
        versionMarkers.keys.forEach { marker -> result = result.replace(Regex("(?i)\\b${Regex.escape(marker)}\\b"), "") }
        return result
    }

    private fun matches(a: MediaIdentity, b: MediaIdentity): Boolean {
        if (a.isrc != null && b.isrc != null) return a.isrc == b.isrc && a.version == b.version
        if (a.providerId == b.providerId && a.sourceId == b.sourceId) return a.version == b.version
        if (a.canonicalUrl != null && a.canonicalUrl == b.canonicalUrl) return a.version == b.version
        return a.normalizedCreator == b.normalizedCreator && a.normalizedTitle == b.normalizedTitle && closeDuration(a.durationMs, b.durationMs) && a.version == b.version
    }

    private fun closeDuration(a: Long, b: Long): Boolean = a == 0L || b == 0L || kotlin.math.abs(a - b) <= 5_000L
}
