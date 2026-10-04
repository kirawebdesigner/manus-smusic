package com.smusic.app.domain.util

/**
 * Shared formatting helpers for byte counts, speeds, ETAs, and timestamps.
 * Pure functions so screens, workers, and tests all render identically.
 */
object Formatters {

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    /** Human transfer speed: "1.4 MB/s", "320 KB/s", "0 B/s". */
    fun formatSpeed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1_048_576L -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
        bytesPerSecond >= 1_024L -> "%.0f KB/s".format(bytesPerSecond / 1_024.0)
        else -> "$bytesPerSecond B/s"
    }

    /**
     * ETA from remaining bytes and current speed, or null when it can't be
     * meaningfully computed (no speed yet, or unknown total size).
     */
    fun formatEta(remainingBytes: Long, speedBytesPerSecond: Long): String? {
        if (remainingBytes <= 0 || speedBytesPerSecond <= 0) return null
        val seconds = (remainingBytes / speedBytesPerSecond).coerceAtLeast(1L)
        return when {
            seconds < 60 -> "${seconds}s left"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        }
    }

    /** "03:45" (or "1:02:03" past an hour) for player positions. */
    fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /** "03:45" / "1:02:03" remaining time, sign removed. */
    fun formatRemaining(millis: Long): String = formatTime(millis.coerceAtLeast(0))
}
