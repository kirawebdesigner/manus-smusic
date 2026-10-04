package com.smusic.app.data.database

/**
 * Versioned SQLite DDL and upgrade statements.
 *
 * Pure Kotlin (no Android imports) so the exact SQL executed on-device is
 * unit-testable against a real SQLite driver:
 * - fresh installs run [createStatements] (v1 tables + all migrations),
 * - upgrades run [migrationStatements] for every version between old and new.
 *
 * Migrations are additive only: existing rows are never dropped or rewritten.
 */
object DatabaseSchema {

    const val VERSION = 2

    /** Original v1 table (kept byte-for-byte as the migration baseline). */
    val downloadJobsV1: String = """
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
    """.trimIndent()

    /** Original v1 table (kept byte-for-byte as the migration baseline). */
    val libraryMediaV1: String = """
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
    """.trimIndent()

    /** v1 → v2: filename/temp/completed tracking for jobs, playback stats for library. */
    private val migrationToV2: List<String> = listOf(
        "ALTER TABLE download_jobs ADD COLUMN filename TEXT",
        "ALTER TABLE download_jobs ADD COLUMN temp_path TEXT",
        "ALTER TABLE download_jobs ADD COLUMN completed_at INTEGER",
        "ALTER TABLE library_media ADD COLUMN last_played INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE library_media ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0"
    )

    /** Complete DDL for a fresh install at [VERSION]. */
    val createStatements: List<String> =
        listOf(downloadJobsV1, libraryMediaV1) + migrationStatements(1)

    /**
     * Statements to upgrade from [fromVersion] to [VERSION], in order.
     * Empty when already at the current version.
     */
    fun migrationStatements(fromVersion: Int): List<String> {
        require(fromVersion in 1..VERSION) { "Unknown schema version: $fromVersion" }
        val statements = mutableListOf<String>()
        var version = fromVersion
        while (version < VERSION) {
            when (version) {
                1 -> statements += migrationToV2
            }
            version++
        }
        return statements
    }
}
