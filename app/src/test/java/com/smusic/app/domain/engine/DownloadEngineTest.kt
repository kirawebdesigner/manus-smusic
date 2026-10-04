package com.smusic.app.domain.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the current [DownloadEngine] architecture (`domain/engine`),
 * replacing the obsolete `data.DirectMediaDownloaderTest` from the v0.1.0
 * `data` package layout. The original intent is preserved: real bytes are
 * streamed to disk and progress is real and monotonic.
 */
class DownloadEngineTest {
    private lateinit var server: MockWebServer
    private val engine = DownloadEngine()
    private val payload = "smusic-real-download-${"x".repeat(4096)}".toByteArray()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newTargetDir(): File = Files.createTempDirectory("smusic-engine-").toFile()

    private fun partFileFor(target: File): File = File(target.parentFile, "${target.name}.part")

    private fun bodyResponse(mime: String, bytes: ByteArray): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", mime)
            .setBody(Buffer().write(bytes))

    @Test
    fun `downloads real bytes and reports monotonic progress`() = runBlocking {
        server.enqueue(bodyResponse("audio/mpeg", payload))
        val target = File(newTargetDir(), "media.mp3")
        val progress = mutableListOf<DownloadEngine.ProgressUpdate>()

        val result = engine.download(server.url("/media.mp3").toString(), target) { progress += it }

        assertTrue(result.success)
        assertEquals(payload.size.toLong(), result.downloadedBytes)
        assertEquals(payload.size.toLong(), result.totalBytes)
        assertEquals("audio/mpeg", result.mimeType)
        assertArrayEquals(payload, target.readBytes())
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes })
        assertEquals(1f, progress.last().progress)
        assertFalse(partFileFor(target).exists())
    }

    @Test
    fun `resumes a partial file by appending the 206 range body`() = runBlocking {
        val head = payload.copyOfRange(0, payload.size / 2)
        val tail = payload.copyOfRange(payload.size / 2, payload.size)
        val target = File(newTargetDir(), "media.mp3")
        partFileFor(target).writeBytes(head)

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Range", "bytes ${head.size}-${payload.size - 1}/${payload.size}")
                .setBody(Buffer().write(tail))
        )

        val progress = mutableListOf<DownloadEngine.ProgressUpdate>()
        val result = engine.download(server.url("/media.mp3").toString(), target) { progress += it }

        assertTrue(result.success)
        assertEquals(payload.size.toLong(), result.downloadedBytes)
        assertEquals(payload.size.toLong(), result.totalBytes)
        assertArrayEquals(payload, target.readBytes())
        assertFalse(partFileFor(target).exists())

        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("bytes=${head.size}-", request.getHeader("Range"))
        // Progress resumes from the pre-existing offset.
        assertTrue(progress.first().downloadedBytes >= head.size)
    }

    @Test
    fun `restarts from zero when the server ignores range and returns a full 200 body`() = runBlocking {
        val target = File(newTargetDir(), "media.mp3")
        partFileFor(target).writeBytes("stale-garbage-prefix".toByteArray())

        server.enqueue(bodyResponse("audio/mpeg", payload))

        val result = engine.download(server.url("/media.mp3").toString(), target) { }

        assertTrue(result.success)
        // The stale partial bytes must NOT be prefixed onto the full response body.
        assertArrayEquals(payload, target.readBytes())
        assertEquals(payload.size.toLong(), result.downloadedBytes)
        assertEquals(payload.size.toLong(), result.totalBytes)
    }

    @Test
    fun `finalizes the part file when 416 indicates it is already complete`() = runBlocking {
        val target = File(newTargetDir(), "media.mp3")
        partFileFor(target).writeBytes(payload)

        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */${payload.size}")
        )

        val result = engine.download(server.url("/media.mp3").toString(), target) { }

        assertTrue(result.success)
        assertEquals(payload.size.toLong(), result.downloadedBytes)
        assertArrayEquals(payload, target.readBytes())
        assertFalse(partFileFor(target).exists())
    }

    @Test
    fun `fails with a clear error on 416 with a mismatched part file and preserves it`() = runBlocking {
        val target = File(newTargetDir(), "media.mp3")
        val part = partFileFor(target)
        part.writeBytes("12345".toByteArray())

        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */100")
        )

        val error = runCatching { engine.download(server.url("/media.mp3").toString(), target) { } }

        assertTrue(error.exceptionOrNull() is IOException)
        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("416"))
        assertTrue(part.exists())
    }

    @Test
    fun `follows relative redirects and re-attaches the range header`() = runBlocking {
        val head = payload.copyOfRange(0, 100)
        val tail = payload.copyOfRange(100, payload.size)
        val target = File(newTargetDir(), "media.mp3")
        partFileFor(target).writeBytes(head)

        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/media.mp3"))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Range", "bytes 100-${payload.size - 1}/${payload.size}")
                .setBody(Buffer().write(tail))
        )

        val result = engine.download(server.url("/redirect").toString(), target) { }

        assertTrue(result.success)
        assertArrayEquals(payload, target.readBytes())

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/redirect", first.path)
        assertEquals("bytes=100-", first.getHeader("Range"))
        assertEquals("/media.mp3", second.path)
        assertEquals("bytes=100-", second.getHeader("Range"))
    }

    @Test
    fun `rejects redirect loops`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/loop"))
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/loop"))
        val target = File(newTargetDir(), "media.mp3")

        val error = runCatching { engine.download(server.url("/start").toString(), target) { } }

        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("Redirect loop"))
    }

    @Test
    fun `rejects malformed redirect locations`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "ht tp://example.invalid/x"))
        val target = File(newTargetDir(), "media.mp3")

        val error = runCatching { engine.download(server.url("/start").toString(), target) { } }

        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("Malformed redirect"))
    }

    @Test
    fun `rejects too many redirects`() = runBlocking {
        repeat(5) { hop ->
            server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/hop-${hop + 1}"))
        }
        val target = File(newTargetDir(), "media.mp3")

        val error = runCatching { engine.download(server.url("/start").toString(), target) { } }

        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("Too many redirects"))
    }

    @Test
    fun `throws a descriptive error for http error statuses`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val target = File(newTargetDir(), "media.mp3")

        val error = runCatching { engine.download(server.url("/missing.mp3").toString(), target) { } }

        assertEquals("Server returned HTTP error code: 404", error.exceptionOrNull()?.message)
        assertFalse(target.exists())
    }

    @Test
    fun `fails on empty downloaded content`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/mpeg"))
        val target = File(newTargetDir(), "media.mp3")

        val error = runCatching { engine.download(server.url("/empty.mp3").toString(), target) { } }

        assertEquals("Downloaded content is empty.", error.exceptionOrNull()?.message)
    }

    @Test
    fun `cancellation stops the download and preserves the partial file`() = runBlocking {
        val target = File(newTargetDir(), "media.mp3")
        server.enqueue(
            bodyResponse("audio/mpeg", payload).throttleBody(1, 50, TimeUnit.MILLISECONDS)
        )

        val result = withTimeoutOrNull(250) {
            engine.download(server.url("/media.mp3").toString(), target) { }
        }

        assertEquals(null, result) // timed out and cancelled mid-download
        val part = partFileFor(target)
        assertTrue(part.exists())
        val partialBytes = part.length()
        assertTrue(partialBytes > 0 && partialBytes < payload.size)
        assertFalse(target.exists())
    }
}
