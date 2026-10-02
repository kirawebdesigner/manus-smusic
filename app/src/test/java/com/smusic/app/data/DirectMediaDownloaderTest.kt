package com.smusic.app.data

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectMediaDownloaderTest {
    private lateinit var server: MockWebServer
    private val payload = "smusic-real-download-${"x".repeat(4096)}".toByteArray()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(payload)))
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun downloadsRealBytesAndReportsMonotonicProgress() {
        runBlocking {
            val target = Files.createTempFile("smusic-test-", ".mp3").toFile().apply { delete() }
            val progress = mutableListOf<Long>()
            val result = DirectMediaDownloader().download(server.url("/media.mp3").toString(), target) { downloaded, _, _ -> progress += downloaded }
            assertEquals(payload.size.toLong(), result.bytes)
            assertEquals(payload.size.toLong(), result.totalBytes)
            assertArrayEquals(payload, target.readBytes())
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
            target.delete()
        }
    }
}
