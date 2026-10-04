package com.smusic.app.domain.provider

import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.MediaType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavior tests for the reference provider: real HTTP probing (HEAD with GET
 * fallback), filename derivation from URL/Content-Disposition/query, MIME
 * classification, redirect handling, and user-facing error categorization.
 */
class DirectUrlProviderTest {

    private lateinit var server: MockWebServer
    private val provider = DirectUrlProvider()
    private val audioBody = Buffer().write(ByteArray(4096) { it.toByte() })

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String): String = server.url(path).toString()

    private fun analyze(path: String): AnalysisResult = runBlocking { provider.analyze(url(path)) }

    private fun headOk(mime: String, bodyBytes: Int, extraHeaders: Map<String, String> = emptyMap()): MockResponse {
        val response = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", mime)
            .setBody(Buffer().write(ByteArray(bodyBytes)))
        extraHeaders.forEach { (key, value) -> response.setHeader(key, value) }
        return response
    }

    @Test
    fun `analyzes a direct mp3 with successful HEAD probe`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") {
                    headOk("audio/mpeg", 4096, mapOf("Accept-Ranges" to "bytes"))
                } else {
                    MockResponse().setResponseCode(405)
                }
        }

        val result = analyze("/song.mp3")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        assertEquals(MediaType.AUDIO, success.media.mediaType)
        assertEquals(1, success.formats.size)
        assertEquals(4096L, success.formats[0].fileSizeBytes)
        assertEquals("audio/mpeg", success.formats[0].mimeType)
        assertEquals("MP3", success.formats[0].container)
        assertEquals("Song", success.media.title)
        assertEquals(true, success.media.supportsRangeRequests)
        // No invented technical details.
        assertEquals(null, success.formats[0].bitrate)
        assertEquals(null, success.formats[0].resolution)
    }

    @Test
    fun `falls back to ranged GET when HEAD is rejected`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "HEAD" -> MockResponse().setResponseCode(405)
                request.getHeader("Range") == "bytes=0-0" -> MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "audio/mpeg")
                    .setHeader("Content-Range", "bytes 0-0/8192")
                    .setBody(Buffer().write(byteArrayOf(0)))
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = analyze("/track")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        assertEquals(8192L, success.formats[0].fileSizeBytes)
        assertEquals(MediaType.AUDIO, success.media.mediaType)
    }

    @Test
    fun `strips query strings from URLs when deriving the filename`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") headOk("audio/mpeg", 1024) else MockResponse().setResponseCode(405)
        }

        val result = analyze("/My%20Track.mp3?token=abc123&expires=99#fragment")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        assertEquals("My Track", success.media.title)
        assertEquals("My Track.mp3", success.media.fileName)
    }

    @Test
    fun `uses Content-Disposition filename for extensionless URLs`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") {
                    headOk(
                        "application/octet-stream", 2048,
                        mapOf("Content-Disposition" to "attachment; filename=\"Cool Song.flac\"")
                    )
                } else {
                    MockResponse().setResponseCode(405)
                }
        }

        val result = analyze("/download?id=42")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        assertEquals("Cool Song.flac", success.media.fileName)
        assertEquals("Cool Song", success.media.title)
        assertEquals(MediaType.AUDIO, success.media.mediaType)
        assertEquals("audio/flac", success.formats[0].mimeType)
        assertEquals("FLAC", success.formats[0].container)
    }

    @Test
    fun `sanitizes hostile Content-Disposition filenames`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") {
                    headOk(
                        "audio/mpeg", 512,
                        mapOf("Content-Disposition" to "attachment; filename=\"../../../etc/passwd.mp3\"")
                    )
                } else {
                    MockResponse().setResponseCode(405)
                }
        }

        val result = analyze("/file")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        // Directory components are stripped; only the basename survives.
        assertEquals("passwd.mp3", success.media.fileName)
        assertFalse(success.media.fileName!!.contains("/"))
        assertFalse(success.media.fileName!!.contains(".."))
    }

    @Test
    fun `classifies extensionless URLs by MIME type`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") headOk("video/mp4", 8192) else MockResponse().setResponseCode(405)
        }

        val result = analyze("/stream")

        assertTrue(result is AnalysisResult.Success)
        val success = result as AnalysisResult.Success
        assertEquals(MediaType.VIDEO, success.media.mediaType)
        assertEquals("stream.mp4", success.media.fileName)
    }

    @Test
    fun `rejects non-media content as unknown media type`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") headOk("text/html; charset=utf-8", 256) else MockResponse().setResponseCode(405)
        }

        val result = analyze("/page")

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.UnknownMediaType, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `maps HTTP 404 to media unavailable`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(404)
        }

        val result = analyze("/gone.mp3")

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.MediaUnavailable, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `maps HTTP 403 to server rejected`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(403)
        }

        val result = analyze("/private.mp3")

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.ServerRejected, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `rejects malformed URLs without crashing`() {
        val result = runBlocking { provider.analyze("ht tp://not a url") }

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.InvalidUrl, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `rejects non-http schemes`() {
        val result = runBlocking { provider.analyze("ftp://example.com/file.mp3") }

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.InvalidUrl, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `maps connection failures to network unavailable`() {
        server.shutdown() // nothing listening anymore

        val result = analyze("/song.mp3")

        assertTrue(result is AnalysisResult.Failed)
        assertEquals(ProviderError.NetworkUnavailable, (result as AnalysisResult.Failed).error)
    }

    @Test
    fun `follows redirects during probing`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/old.mp3" -> MockResponse().setResponseCode(301).setHeader("Location", "/new.mp3")
                "/new.mp3" ->
                    if (request.method == "HEAD") headOk("audio/mpeg", 2048) else MockResponse().setResponseCode(405)
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = analyze("/old.mp3")

        assertTrue(result is AnalysisResult.Success)
    }

    @Test
    fun `canHandle accepts http and https with hosts only`() {
        assertTrue(provider.canHandle("https://example.com/file.mp3"))
        assertTrue(provider.canHandle("http://localhost:8080/file.mp3?x=1"))
        assertFalse(provider.canHandle("ftp://example.com/file.mp3"))
        assertFalse(provider.canHandle("not a url at all"))
        assertFalse(provider.canHandle(""))
    }

    @Test
    fun `resolveMedia produces a download request owned by the manager`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") headOk("audio/mpeg", 2048) else MockResponse().setResponseCode(405)
        }

        val success = analyze("/song.mp3") as AnalysisResult.Success
        val request = runBlocking { provider.resolveMedia(success.media, success.formats[0]) }

        assertEquals(success.media.originalUrl, request.url)
        assertEquals("direct_url", request.providerId)
        assertEquals(MediaType.AUDIO, request.mediaType)
        assertEquals("song.mp3", request.suggestedFilename)
    }
}
