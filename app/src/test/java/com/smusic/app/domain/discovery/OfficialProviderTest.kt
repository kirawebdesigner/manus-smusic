package com.smusic.app.domain.discovery

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialProviderTest {
    @Test fun youtubeDurationParserHandlesHoursMinutesSeconds() {
        assertEquals(3_723_000L, YouTubeDataProvider.parseDuration("PT1H2M3S"))
        assertEquals(90_000L, YouTubeDataProvider.parseDuration("PT1M30S"))
        assertEquals(0L, YouTubeDataProvider.parseDuration("not-a-duration"))
    }

    @Test fun youtubeWithoutKeyExplainsConfiguration() = runBlocking {
        val result = YouTubeDataProvider("").search("test")
        assertTrue(result is DiscoveryResult.Unsupported)
    }

    @Test fun spotifyWithoutTokenExplainsConnection() = runBlocking {
        val provider = SpotifyWebApiProvider("client") { null }
        val result = provider.search("test")
        assertTrue(result is DiscoveryResult.Unsupported)
    }
}
