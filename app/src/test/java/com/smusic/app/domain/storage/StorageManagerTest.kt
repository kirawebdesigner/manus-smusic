package com.smusic.app.domain.storage

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the pure, unit-testable helpers of [StorageManager]: remote input
 * (Content-Disposition names, subfolder names) can never produce a filename
 * or path that escapes the app-controlled downloads root.
 */
class StorageManagerTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("smusic-storage-").toFile()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `sanitizeFileName strips path separators and traversal sequences`() {
        val result = StorageManager.sanitizeFileName("../../etc/passwd", "fallback")

        assertFalse(result.contains("/"))
        assertFalse(result.contains("\\"))
        assertFalse(result.contains(".."))
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `sanitizeFileName removes reserved and control characters`() {
        val result = StorageManager.sanitizeFileName("a<b>c:d*e?f\"g|h\u0000i\u001Fj", "fallback")

        for (char in listOf('<', '>', ':', '*', '?', '"', '|', '\u0000', '\u001F')) {
            assertFalse("Expected '$char' to be removed", result.contains(char))
        }
    }

    @Test
    fun `sanitizeFileName falls back when nothing survives`() {
        assertEquals("folder", StorageManager.sanitizeFileName("   ", "folder"))
        assertEquals("folder", StorageManager.sanitizeFileName("\u0000\u0001", "folder"))
        assertEquals("folder", StorageManager.sanitizeFileName("\t\n\r", "folder"))
    }

    @Test
    fun `sanitizeFileName caps length`() {
        val result = StorageManager.sanitizeFileName("x".repeat(500), "fallback")
        assertTrue(result.length <= 100)
    }

    @Test
    fun `sanitizeExtension keeps alphanumeric extensions only`() {
        assertEquals("mp3", StorageManager.sanitizeExtension("mp3"))
        assertEquals("mp3", StorageManager.sanitizeExtension(".MP3"))
        assertEquals("mp4", StorageManager.sanitizeExtension("mp4/../"))
        assertEquals("p4", StorageManager.sanitizeExtension("p4/../"))
        assertEquals("bin", StorageManager.sanitizeExtension(""))
        assertEquals("bin", StorageManager.sanitizeExtension("///"))
    }

    @Test
    fun `filenameWithExtension appends a container extension when missing`() {
        assertEquals("song.mp3", StorageManager.filenameWithExtension("song", "mp3"))
        assertEquals("song.mp3", StorageManager.filenameWithExtension("song.mp3", "mp4"))
        assertEquals("smusic_download.mp3", StorageManager.filenameWithExtension("", "mp3"))
    }

    @Test
    fun `filenameWithExtension keeps a sanitized hostile name contained`() {
        val result = StorageManager.filenameWithExtension("../../../etc/passwd.mp3", "mp3")

        assertFalse(result.contains("/"))
        assertFalse(result.contains(".."))
        assertTrue(result.endsWith(".mp3"))
    }

    @Test
    fun `isWithinDirectory accepts children and rejects escapes`() {
        val base = File(tempRoot, "downloads/audio").apply { mkdirs() }
        val inside = File(base, "song.mp3")
        val escape = File(base, "../evil.mp3")
        val sibling = File(tempRoot, "other/evil.mp3")

        assertTrue(StorageManager.isWithinDirectory(base, inside))
        assertFalse(StorageManager.isWithinDirectory(base, escape))
        assertFalse(StorageManager.isWithinDirectory(base, sibling))
        assertFalse(StorageManager.isWithinDirectory(base, base))
    }
}
