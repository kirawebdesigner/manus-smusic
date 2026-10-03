package com.smusic.app.domain.storage

import android.content.Context
import android.os.Environment
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import java.io.File
import java.util.UUID

class StorageManager(private val context: Context) {

    fun resolveTargetFile(
        title: String,
        container: String,
        destination: DownloadDestination,
        mediaType: MediaType
    ): File {
        val baseDir = getStorageDirectory(destination.category, destination.customPath)
        val targetDir = if (!destination.subFolder.isNullOrBlank()) {
            File(baseDir, sanitizeName(destination.subFolder))
        } else {
            baseDir
        }
        targetDir.mkdirs()

        val safeTitle = sanitizeName(title)
        val ext = sanitizeExtension(container)
        var targetFile = File(targetDir, "$safeTitle.$ext")

        // Handle duplicates without overwriting
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(targetDir, "$safeTitle ($counter).$ext")
            counter++
        }

        return targetFile
    }

    fun getStorageDirectory(category: StorageCategory, customPath: String? = null): File {
        if (!customPath.isNullOrBlank()) {
            val custom = File(customPath)
            if (custom.isAbsolute && !customPath.contains("..")) {
                return custom
            }
        }

        return when (category) {
            StorageCategory.MUSIC -> {
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: File(context.filesDir, "Music")
            }
            StorageCategory.VIDEOS -> {
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: File(context.filesDir, "Videos")
            }
            StorageCategory.OTHER -> {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "Downloads")
            }
        }
    }

    fun validateIntegrity(file: File, expectedMinBytes: Long = 1024L): Boolean {
        if (!file.exists() || !file.isFile) return false
        val length = file.length()
        return length >= expectedMinBytes
    }

    private fun sanitizeName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\.\\.+"), "_")
            .trim()
            .take(100)
            .ifBlank { "Smusic_${UUID.randomUUID().toString().take(8)}" }
    }

    private fun sanitizeExtension(ext: String): String {
        val clean = ext.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return clean.ifBlank { "bin" }
    }
}
