package com.smusic.app.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import java.io.File

data class LibraryItem(
    val id: String,
    val title: String,
    val creator: String,
    val album: String = "Smusic Library",
    val source: String = "Direct",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val readableSize: String = "",
    val mediaType: MediaType = MediaType.AUDIO,
    val localPath: String,
    val mimeType: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playlistName: String? = null
)

class SmusicDatabase(context: Context) : SQLiteOpenHelper(context, "smusic_v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS download_jobs (
                id TEXT PRIMARY KEY,
                original_url TEXT NOT NULL,
                title TEXT NOT NULL,
                uploader TEXT NOT NULL,
                thumbnail TEXT,
                duration TEXT NOT NULL,
                media_type TEXT NOT NULL,
                format_id TEXT NOT NULL,
                format_label TEXT NOT NULL,
                container TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                dest_category TEXT NOT NULL,
                dest_subfolder TEXT,
                state TEXT NOT NULL,
                progress REAL NOT NULL DEFAULT 0.0,
                speed_bytes INTEGER NOT NULL DEFAULT 0,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                local_path TEXT,
                error_message TEXT,
                retry_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS library_media (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                creator TEXT NOT NULL,
                album TEXT NOT NULL,
                source TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                file_size_bytes INTEGER NOT NULL DEFAULT 0,
                readable_size TEXT NOT NULL,
                media_type TEXT NOT NULL,
                local_path TEXT NOT NULL UNIQUE,
                mime_type TEXT,
                added_date INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                playlist_name TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // --- DOWNLOAD QUEUE OPERATIONS ---

    fun enqueueJob(job: DownloadJob) {
        val values = ContentValues().apply {
            put("id", job.id)
            put("original_url", job.mediaInfo.originalUrl)
            put("title", job.mediaInfo.title)
            put("uploader", job.mediaInfo.uploader)
            put("thumbnail", job.mediaInfo.thumbnail)
            put("duration", job.mediaInfo.duration)
            put("media_type", job.mediaInfo.mediaType.name)
            put("format_id", job.selectedFormat.id)
            put("format_label", job.selectedFormat.label)
            put("container", job.selectedFormat.container)
            put("mime_type", job.selectedFormat.mimeType)
            put("dest_category", job.destination.category.name)
            put("dest_subfolder", job.destination.subFolder)
            put("state", job.state.name)
            put("progress", job.progress)
            put("speed_bytes", job.speedBytesPerSecond)
            put("downloaded_bytes", job.downloadedBytes)
            put("total_bytes", job.totalBytes)
            put("local_path", job.localPath)
            put("error_message", job.errorMessage)
            put("retry_count", job.retryCount)
            put("created_at", job.createdAt)
            put("updated_at", job.updatedAt)
        }
        writableDatabase.insertWithOnConflict("download_jobs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateJobState(
        id: String,
        state: JobState,
        progress: Float? = null,
        speedBytes: Long? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        localPath: String? = null,
        error: String? = null
    ) {
        val values = ContentValues().apply {
            put("state", state.name)
            put("updated_at", System.currentTimeMillis())
            progress?.let { put("progress", it) }
            speedBytes?.let { put("speed_bytes", it) }
            downloadedBytes?.let { put("downloaded_bytes", it) }
            totalBytes?.let { put("total_bytes", it) }
            localPath?.let { put("local_path", it) }
            error?.let { put("error_message", it) }
        }
        writableDatabase.update("download_jobs", values, "id = ?", arrayOf(id))
    }

    fun getJob(id: String): DownloadJob? {
        return readableDatabase.query(
            "download_jobs", null, "id = ?", arrayOf(id), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toJob() else null
        }
    }

    fun getAllJobs(): List<DownloadJob> {
        return readableDatabase.query(
            "download_jobs", null, null, null, null, null, "created_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toJob())
                }
            }
        }
    }

    fun getActiveJobs(): List<DownloadJob> {
        val activeStates = arrayOf(JobState.DOWNLOADING.name, JobState.PROCESSING.name, JobState.WAITING.name)
        val placeholders = activeStates.joinToString(",") { "?" }
        return readableDatabase.query(
            "download_jobs", null, "state IN ($placeholders)", activeStates, null, null, "created_at ASC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toJob()) }
        }
    }

    fun getQueuedJobs(): List<DownloadJob> {
        return readableDatabase.query(
            "download_jobs", null, "state = ?", arrayOf(JobState.QUEUED.name), null, null, "created_at ASC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toJob()) }
        }
    }

    fun removeJob(id: String) {
        writableDatabase.delete("download_jobs", "id = ?", arrayOf(id))
    }

    fun clearCompletedJobs() {
        writableDatabase.delete("download_jobs", "state = ?", arrayOf(JobState.COMPLETED.name))
    }

    fun retryJob(id: String) {
        val values = ContentValues().apply {
            put("state", JobState.QUEUED.name)
            put("error_message", null as String?)
            put("progress", 0f)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("download_jobs", values, "id = ?", arrayOf(id))
    }

    // --- LIBRARY OPERATIONS ---

    fun insertLibraryItem(item: LibraryItem) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("title", item.title)
            put("creator", item.creator)
            put("album", item.album)
            put("source", item.source)
            put("duration_ms", item.durationMs)
            put("file_size_bytes", item.fileSizeBytes)
            put("readable_size", item.readableSize)
            put("media_type", item.mediaType.name)
            put("local_path", item.localPath)
            put("mime_type", item.mimeType)
            put("added_date", item.addedDate)
            put("is_favorite", if (item.isFavorite) 1 else 0)
            put("playlist_name", item.playlistName)
        }
        writableDatabase.insertWithOnConflict("library_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllLibraryItems(): List<LibraryItem> {
        cleanStaleRecords()
        return readableDatabase.query(
            "library_media", null, null, null, null, null, "added_date DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLibraryItem())
                }
            }
        }
    }

    fun searchLibrary(query: String): List<LibraryItem> {
        val q = "%${query.trim()}%"
        return readableDatabase.query(
            "library_media", null, "title LIKE ? OR creator LIKE ? OR album LIKE ?",
            arrayOf(q, q, q), null, null, "title ASC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toLibraryItem()) }
        }
    }

    fun toggleFavorite(id: String) {
        writableDatabase.execSQL("UPDATE library_media SET is_favorite = (1 - is_favorite) WHERE id = ?", arrayOf(id))
    }

    fun deleteLibraryItem(id: String) {
        val item = readableDatabase.query("library_media", arrayOf("local_path"), "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        item?.let { File(it).delete() }
        writableDatabase.delete("library_media", "id = ?", arrayOf(id))
    }

    fun cleanStaleRecords() {
        val toRemove = mutableListOf<String>()
        readableDatabase.query("library_media", arrayOf("id", "local_path"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val path = cursor.getString(1)
                if (!File(path).exists()) {
                    toRemove.add(id)
                }
            }
        }
        if (toRemove.isNotEmpty()) {
            val placeholders = toRemove.joinToString(",") { "?" }
            writableDatabase.delete("library_media", "id IN ($placeholders)", toRemove.toTypedArray())
        }
    }

    // --- CURSOR MAPPERS ---

    private fun Cursor.toJob(): DownloadJob {
        val mediaType = MediaType.valueOf(getString(getColumnIndexOrThrow("media_type")))
        val mediaInfo = MediaInfo(
            id = getString(getColumnIndexOrThrow("original_url")),
            title = getString(getColumnIndexOrThrow("title")),
            uploader = getString(getColumnIndexOrThrow("uploader")),
            source = "Smusic Queue",
            duration = getString(getColumnIndexOrThrow("duration")),
            fileSize = "Dynamic",
            mediaType = mediaType,
            originalUrl = getString(getColumnIndexOrThrow("original_url")),
            thumbnail = getString(getColumnIndexOrThrow("thumbnail")),
            isDownloadable = true
        )
        val format = MediaFormat(
            id = getString(getColumnIndexOrThrow("format_id")),
            label = getString(getColumnIndexOrThrow("format_label")),
            container = getString(getColumnIndexOrThrow("container")),
            mimeType = getString(getColumnIndexOrThrow("mime_type")),
            fileSize = "",
            mediaType = mediaType
        )
        val destination = DownloadDestination(
            category = StorageCategory.valueOf(getString(getColumnIndexOrThrow("dest_category"))),
            subFolder = getString(getColumnIndexOrThrow("dest_subfolder"))
        )

        return DownloadJob(
            id = getString(getColumnIndexOrThrow("id")),
            mediaInfo = mediaInfo,
            selectedFormat = format,
            destination = destination,
            state = JobState.valueOf(getString(getColumnIndexOrThrow("state"))),
            progress = getFloat(getColumnIndexOrThrow("progress")),
            speedBytesPerSecond = getLong(getColumnIndexOrThrow("speed_bytes")),
            downloadedBytes = getLong(getColumnIndexOrThrow("downloaded_bytes")),
            totalBytes = getLong(getColumnIndexOrThrow("total_bytes")),
            localPath = getString(getColumnIndexOrThrow("local_path")),
            errorMessage = getString(getColumnIndexOrThrow("error_message")),
            retryCount = getInt(getColumnIndexOrThrow("retry_count")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun Cursor.toLibraryItem(): LibraryItem {
        return LibraryItem(
            id = getString(getColumnIndexOrThrow("id")),
            title = getString(getColumnIndexOrThrow("title")),
            creator = getString(getColumnIndexOrThrow("creator")),
            album = getString(getColumnIndexOrThrow("album")),
            source = getString(getColumnIndexOrThrow("source")),
            durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
            fileSizeBytes = getLong(getColumnIndexOrThrow("file_size_bytes")),
            readableSize = getString(getColumnIndexOrThrow("readable_size")),
            mediaType = MediaType.valueOf(getString(getColumnIndexOrThrow("media_type"))),
            localPath = getString(getColumnIndexOrThrow("local_path")),
            mimeType = getString(getColumnIndexOrThrow("mime_type")),
            addedDate = getLong(getColumnIndexOrThrow("added_date")),
            isFavorite = getInt(getColumnIndexOrThrow("is_favorite")) == 1,
            playlistName = getString(getColumnIndexOrThrow("playlist_name"))
        )
    }
}
