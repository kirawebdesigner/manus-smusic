package com.smusic.app.domain.storage

import android.content.Context
import android.os.Environment
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import java.io.File
import java.util.UUID

/** One media file discovered on disk during a library scan. */
data class ScannedMedia(
    val file: File,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val sizeBytes: Long
)

/** Aggregated per-category storage numbers for the Settings screen. */
data class StorageUsage(
    val audioBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val otherBytes: Long = 0L,
    val temporaryBytes: Long = 0L,
    val itemCount: Int = 0
) {
    val totalBytes: Long get() = audioBytes + videoBytes + otherBytes + temporaryBytes
}

/**
 * Owns every filesystem path Smusic writes to. All downloads live under an
 * app-controlled `downloads/` root (no storage permission required) split into
 * `audio/`, `video/`, and `other/`; `.part` staging files sit beside their
 * target. Remote input (URLs, Content-Disposition names) can never steer a
 * write outside that root.
 */
class StorageManager(private val context: Context) {

    fun resolveTargetFile(
        title: String,
        container: String,
        destination: DownloadDestination,
        mediaType: MediaType,
        suggestedFilename: String? = null
    ): File {
        val baseDir = getStorageDirectory(destination.category, destination.customPath)
        val targetDir = if (!destination.subFolder.isNullOrBlank()) {
            File(baseDir, sanitizeFileName(destination.subFolder, "folder"))
        } else {
            baseDir
        }
        targetDir.mkdirs()

        // Prefer the server/URL-provided filename; fall back to the analyzed title.
        val rawName = suggestedFilename
            ?.takeIf { it.isNotBlank() }
            ?: "$title.${sanitizeExtension(container)}"
        val safeName = filenameWithExtension(rawName, container)

        var targetFile = File(targetDir, safeName)
        if (!isWithinDirectory(targetDir, targetFile)) {
            // Sanitization failed to contain the name (paranoia guard): use a UUID.
            targetFile = File(targetDir, "smusic_${UUID.randomUUID().toString().take(8)}.${sanitizeExtension(container)}")
        }

        // Handle duplicates without overwriting.
        val base = targetFile.nameWithoutExtension
        val ext = targetFile.extension
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(targetDir, "$base ($counter).$ext")
            counter++
        }

        return targetFile
    }

    fun getStorageDirectory(category: StorageCategory, customPath: String? = null): File {
        val downloadsRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_DIR)

        if (!customPath.isNullOrBlank()) {
            val custom = File(customPath)
            // Only paths inside app-controlled storage are accepted.
            if (custom.isAbsolute && !customPath.contains("..") && isWithinDirectory(downloadsRoot, custom)) {
                return custom
            }
        }

        return when (category) {
            StorageCategory.MUSIC -> File(downloadsRoot, "audio")
            StorageCategory.VIDEOS -> File(downloadsRoot, "video")
            StorageCategory.OTHER -> File(downloadsRoot, "other")
        }.also { it.mkdirs() }
    }

    fun validateIntegrity(file: File, expectedMinBytes: Long = 1024L): Boolean {
        if (!file.exists() || !file.isFile) return false
        return file.length() >= expectedMinBytes
    }

    /** Deletes leftover `.part` staging files. Returns how many were removed. */
    fun clearTemporaryFiles(): Int {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_DIR)
        if (!root.exists()) return 0
        var removed = 0
        root.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".part")) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    /** Recomputes per-category storage usage from disk. Runs on the caller's thread. */
    fun computeStorageUsage(): StorageUsage {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_DIR)
        if (!root.exists()) return StorageUsage()

        var audio = 0L
        var video = 0L
        var other = 0L
        var temp = 0L
        var count = 0

        root.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val length = file.length()
            if (file.name.endsWith(".part")) {
                temp += length
            } else {
                count++
                when (file.parentFile?.name) {
                    "audio" -> audio += length
                    "video" -> video += length
                    else -> other += length
                }
            }
        }
        return StorageUsage(audio, video, other, temp, count)
    }

    /**
     * Lists media files under the controlled download roots so orphaned files
     * (written by a download whose database row was lost) can be recovered
     * into the library. Staging files are excluded.
     */
    fun scanMediaFiles(): List<ScannedMedia> {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_DIR)
        if (!root.exists()) return emptyList()

        return root.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") && !it.name.startsWith(".") }
            .filter { sanitizeExtension(it.extension) in KNOWN_MEDIA_EXTENSIONS }
            .map { file ->
                val dirName = file.parentFile?.name
                val mediaType = when {
                    dirName == "video" -> MediaType.VIDEO
                    dirName == "audio" -> MediaType.AUDIO
                    else -> mediaTypeForExtension(file.extension)
                }
                ScannedMedia(
                    file = file,
                    title = file.nameWithoutExtension
                        .replace(Regex("[-_+]+"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .ifBlank { file.nameWithoutExtension },
                    mediaType = mediaType,
                    mimeType = mimeForExtension(file.extension),
                    sizeBytes = file.length()
                )
            }
            .toList()
    }

    companion object {
        const val ROOT_DIR = "downloads"

        private val KNOWN_MEDIA_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus",
            "mp4", "webm", "mkv", "mov", "avi"
        )

        /**
         * Strips path separators, traversal sequences, reserved characters, and
         * control characters from a remotely influenced name. Never returns a
         * name that can escape its directory.
         */
        fun sanitizeFileName(name: String, fallback: String): String {
            val cleaned = name
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
                .replace(Regex("\\.\\.+"), "_")
                .trim()
                .trim('.')
                .replace(Regex("\\s+"), " ")
                .take(100)
                .trim()
            return cleaned.ifBlank { fallback }
        }

        fun sanitizeExtension(ext: String): String {
            val clean = ext.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            return clean.ifBlank { "bin" }
        }

        /** Applies sanitization to a full filename while preserving its extension. */
        fun filenameWithExtension(rawFilename: String, container: String): String {
            val sanitized = sanitizeFileName(rawFilename, "smusic_download")
            return if ('.' in sanitized.substringAfterLast('/')) {
                sanitized
            } else {
                "$sanitized.${sanitizeExtension(container)}"
            }
        }

        /** True when [target] resolves inside [base] (canonical, traversal-proof). */
        fun isWithinDirectory(base: File, target: File): Boolean {
            return try {
                val basePath = base.canonicalPath.trimEnd(File.separatorChar) + File.separator
                target.canonicalPath.startsWith(basePath)
            } catch (_: Exception) {
                false
            }
        }

        private fun mediaTypeForExtension(ext: String): MediaType =
            if (ext.lowercase() in setOf("mp4", "webm", "mkv", "mov", "avi")) MediaType.VIDEO
            else MediaType.AUDIO

        private fun mimeForExtension(ext: String): String? = when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "application/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            else -> null
        }
    }
}
