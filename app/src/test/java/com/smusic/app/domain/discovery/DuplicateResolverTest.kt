package com.smusic.app.domain.discovery

import com.smusic.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateResolverTest {
    private fun item(title: String, sourceId: String = "a", url: String = "https://example.com/$sourceId", duration: Long = 180_000L, version: TrackVersion = TrackVersion.STUDIO, isrc: String? = null) = DiscoveryItem(sourceId, url, "test", title, "Artist", durationMs = duration, mediaType = MediaType.AUDIO, version = version, isrc = isrc)

    @Test fun sameIsrcIsExistingEvenWhenSourceUrlsDiffer() {
        val existing = DuplicateResolver.identity(item("Song", "one", isrc = "us-abc-1"))
        val decision = DuplicateResolver.decide(item("Song", "two", isrc = "US-ABC-1"), listOf(existing), emptyList())
        assertTrue(decision is DuplicateDecision.Existing)
    }

    @Test fun repeatedPlaylistEntryIsQueuedOnlyOnce() {
        val first = DuplicateResolver.identity(item("Song", "first"))
        val decision = DuplicateResolver.decide(item("Song", "second"), emptyList(), listOf(first))
        assertTrue(decision is DuplicateDecision.Queued)
    }

    @Test fun liveVersionIsNotCollapsedIntoStudio() {
        val studio = DuplicateResolver.identity(item("Song"))
        val live = DuplicateResolver.decide(item("Song (Live)", version = TrackVersion.LIVE), listOf(studio), emptyList())
        assertEquals(DuplicateDecision.DifferentVersion, live)
    }

    @Test fun trackingParametersAreRemovedFromCanonicalUrl() {
        assertEquals("https://example.com/watch?v=abc", DuplicateResolver.canonicalUrl("https://EXAMPLE.com/watch?utm_source=x&v=abc#fragment"))
    }

    @Test fun uncertainDurationIsReviewable() {
        val existing = DuplicateResolver.identity(item("Song", duration = 100_000L))
        val decision = DuplicateResolver.decide(item("Song", sourceId = "other", url = "https://other.example/song", duration = 150_000L), listOf(existing), emptyList())
        assertEquals(DuplicateDecision.NeedsReview, decision)
    }
}
