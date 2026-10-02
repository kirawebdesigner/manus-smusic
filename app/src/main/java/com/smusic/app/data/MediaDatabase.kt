package com.smusic.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MediaDatabase(context: Context) : SQLiteOpenHelper(context, "smusic.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE media (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                creator TEXT NOT NULL,
                source TEXT NOT NULL,
                duration TEXT NOT NULL,
                file_size TEXT NOT NULL,
                kind TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                added_label TEXT NOT NULL,
                local_path TEXT,
                mime_type TEXT,
                file_size_bytes INTEGER NOT NULL DEFAULT 0,
                download_date INTEGER NOT NULL,
                state TEXT NOT NULL
            )
        """.trimIndent())
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun upsert(item: MediaItem) {
        val values = ContentValues().apply {
            put("id", item.id); put("title", item.title); put("creator", item.creator); put("source", item.source)
            put("duration", item.duration); put("file_size", item.fileSize); put("kind", item.kind.name); put("url", item.url)
            put("added_label", item.addedLabel); put("local_path", item.localPath); put("mime_type", item.mimeType)
            put("file_size_bytes", item.fileSizeBytes); put("download_date", item.downloadDate); put("state", item.state.name)
        }
        writableDatabase.insertWithOnConflict("media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun findByUrl(url: String): MediaItem? = readableDatabase.query("media", null, "url = ?", arrayOf(url), null, null, null).use { if (it.moveToFirst()) it.toItem() else null }
    fun findById(id: String): MediaItem? = readableDatabase.query("media", null, "id = ?", arrayOf(id), null, null, null).use { if (it.moveToFirst()) it.toItem() else null }
    fun all(): List<MediaItem> = readableDatabase.query("media", null, null, null, null, null, "download_date DESC").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toItem()) } }
    fun delete(id: String) { writableDatabase.delete("media", "id = ?", arrayOf(id)) }

    private fun android.database.Cursor.toItem(): MediaItem = MediaItem(
        id = getString(getColumnIndexOrThrow("id")), title = getString(getColumnIndexOrThrow("title")), creator = getString(getColumnIndexOrThrow("creator")),
        source = getString(getColumnIndexOrThrow("source")), duration = getString(getColumnIndexOrThrow("duration")), fileSize = getString(getColumnIndexOrThrow("file_size")),
        kind = MediaKind.valueOf(getString(getColumnIndexOrThrow("kind"))), url = getString(getColumnIndexOrThrow("url")), addedLabel = getString(getColumnIndexOrThrow("added_label")),
        localPath = getString(getColumnIndexOrThrow("local_path")), mimeType = getString(getColumnIndexOrThrow("mime_type")), fileSizeBytes = getLong(getColumnIndexOrThrow("file_size_bytes")),
        downloadDate = getLong(getColumnIndexOrThrow("download_date")), state = DownloadState.valueOf(getString(getColumnIndexOrThrow("state")))
    )
}
