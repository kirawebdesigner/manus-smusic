package com.smusic.app.domain.processor

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Reads intrinsic media metadata (currently duration) from local files using
 * the platform [MediaMetadataRetriever]. All calls are defensive: a corrupt or
 * unsupported file yields 0 rather than throwing.
 */
object MediaMetadataReader {

    /** Returns the duration in milliseconds, or 0 when it can't be determined. */
    fun readDurationMs(file: File): Long {
        if (!file.isFile || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
