package com.smusic.app.domain.util

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Rendering contract for byte counts, speeds, ETAs, and player timestamps. */
class FormattersTest {

    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun pinLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `formats byte counts at sensible magnitudes`() {
        assertEquals("0 B", Formatters.formatBytes(0))
        assertEquals("512 B", Formatters.formatBytes(512))
        assertEquals("1 KB", Formatters.formatBytes(1024))
        assertEquals("1.4 MB", Formatters.formatBytes(1_500_000))
        assertEquals("1.9 GB", Formatters.formatBytes(2_000_000_000))
    }

    @Test
    fun `formats transfer speeds`() {
        assertEquals("0 B/s", Formatters.formatSpeed(0))
        assertEquals("512 B/s", Formatters.formatSpeed(512))
        assertEquals("1 KB/s", Formatters.formatSpeed(1024))
        assertEquals("1.9 MB/s", Formatters.formatSpeed(2_000_000))
    }

    @Test
    fun `eta is null when it cannot be computed honestly`() {
        assertNull(Formatters.formatEta(0, 1000))
        assertNull(Formatters.formatEta(-100, 1000))
        assertNull(Formatters.formatEta(10_000, 0))
        assertNull(Formatters.formatEta(10_000, -5))
    }

    @Test
    fun `eta renders seconds minutes and hours`() {
        assertEquals("1s left", Formatters.formatEta(100, 1000))
        assertEquals("10s left", Formatters.formatEta(10_000, 1000))
        assertEquals("10m 0s left", Formatters.formatEta(600_000, 1000))
        assertEquals("1h 0m left", Formatters.formatEta(3_600_000, 1000))
    }

    @Test
    fun `formats player timestamps`() {
        assertEquals("00:00", Formatters.formatTime(0))
        assertEquals("00:05", Formatters.formatTime(5_000))
        assertEquals("03:45", Formatters.formatTime(225_000))
        assertEquals("1:02:03", Formatters.formatTime(3_723_000))
    }

    @Test
    fun `negative or missing values clamp to zero`() {
        assertEquals("00:00", Formatters.formatTime(-1))
        assertEquals("00:00", Formatters.formatRemaining(-5_000))
        assertEquals("03:45", Formatters.formatRemaining(225_000))
    }
}
