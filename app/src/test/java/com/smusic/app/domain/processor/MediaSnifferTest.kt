package com.smusic.app.domain.processor

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Magic-byte classification used to give extensionless downloads a truthful
 * extension so Media3 can route the file to the right renderer.
 */
class MediaSnifferTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("smusic-sniffer-").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Builds a header of at least 16 bytes from the given prefix bytes. */
    private fun header(vararg prefix: Byte): ByteArray {
        val bytes = ByteArray(64)
        prefix.forEachIndexed { index, byte -> bytes[index] = byte }
        return bytes
    }

    private fun stringHeader(prefix: String): ByteArray = header(*prefix.toByteArray())

    private fun write(name: String, bytes: ByteArray): File =
        File(tempDir, name).apply { writeBytes(bytes) }

    @Test
    fun `detects MP3 by ID3 tag`() {
        assertEquals("mp3", MediaSniffer.sniff(stringHeader("ID3\u0003\u0000")))
    }

    @Test
    fun `detects MP3 by MPEG frame sync`() {
        assertEquals("mp3", MediaSniffer.sniff(header(0xFF.toByte(), 0xFB.toByte())))
        assertEquals("mp3", MediaSniffer.sniff(header(0xFF.toByte(), 0xE0.toByte())))
    }

    @Test
    fun `detects FLAC`() {
        assertEquals("flac", MediaSniffer.sniff(stringHeader("fLaC")))
    }

    @Test
    fun `detects Ogg container`() {
        assertEquals("ogg", MediaSniffer.sniff(stringHeader("OggS")))
    }

    @Test
    fun `detects WAV via RIFF WAVE`() {
        val bytes = stringHeader("RIFF")
        "WAVE".toByteArray().copyInto(bytes, destinationOffset = 8)
        assertEquals("wav", MediaSniffer.sniff(bytes))
    }

    @Test
    fun `detects MP4 and distinguishes M4A brands`() {
        // Compact ISO-BMFF header: 4-byte box size, then 'ftyp' at offset 4.
        val mp4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".toByteArray() + ByteArray(52)
        assertEquals("mp4", MediaSniffer.sniff(mp4))

        val m4a = byteArrayOf(0, 0, 0, 0x18) + "ftypM4A ".toByteArray() + ByteArray(52)
        assertEquals("m4a", MediaSniffer.sniff(m4a))
    }

    @Test
    fun `detects EBML containers as webm or mkv`() {
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) +
            "webm".toByteArray() + ByteArray(56)
        assertEquals("webm", MediaSniffer.sniff(webm))

        val mkv = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(60)
        assertEquals("mkv", MediaSniffer.sniff(mkv))
    }

    @Test
    fun `returns null for unknown or short content`() {
        assertNull(MediaSniffer.sniff(ByteArray(0)))
        assertNull(MediaSniffer.sniff(ByteArray(8)))
        assertNull(MediaSniffer.sniff(stringHeader("<html><body>not media")))
    }

    @Test
    fun `sniffExtension reads a real file`() {
        val file = write("song.part", stringHeader("ID3") + ByteArray(1024))
        assertEquals("mp3", MediaSniffer.sniffExtension(file))
    }

    @Test
    fun `sniffExtension rejects missing tiny and non-file inputs`() {
        assertNull(MediaSniffer.sniffExtension(File(tempDir, "missing.bin")))
        assertNull(MediaSniffer.sniffExtension(write("tiny.bin", ByteArray(4))))
        assertNull(MediaSniffer.sniffExtension(tempDir)) // a directory, not a file
    }
}
