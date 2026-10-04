package com.smusic.app.data.database

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Executes the exact DDL [SmusicDatabase] runs on-device against a real SQLite
 * driver, so schema and migration regressions are caught by `./gradlew test`
 * without an emulator.
 */
class DatabaseSchemaTest {

    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        connection.close()
    }

    private fun exec(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    private fun columnNames(table: String): Set<String> {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rs ->
                val names = mutableSetOf<String>()
                while (rs.next()) names.add(rs.getString("name"))
                return names
            }
        }
    }

    private fun queryLong(sql: String): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                assertTrue("Expected a row for: $sql", rs.next())
                return rs.getLong(1)
            }
        }
    }

    private fun queryNullableLong(sql: String): Long? {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                assertTrue("Expected a row for: $sql", rs.next())
                val value = rs.getLong(1)
                return if (rs.wasNull()) null else value
            }
        }
    }

    private fun queryString(sql: String): String? {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                assertTrue("Expected a row for: $sql", rs.next())
                return rs.getString(1)
            }
        }
    }

    @Test
    fun `fresh install creates both tables with all v2 columns`() {
        DatabaseSchema.createStatements.forEach { exec(it) }

        val jobColumns = columnNames("download_jobs")
        assertTrue(jobColumns.containsAll(listOf(
            "id", "original_url", "title", "uploader", "thumbnail", "duration",
            "media_type", "format_id", "format_label", "container", "mime_type",
            "dest_category", "dest_subfolder", "state", "progress", "speed_bytes",
            "downloaded_bytes", "total_bytes", "local_path", "error_message",
            "retry_count", "created_at", "updated_at",
            // v2 additions
            "filename", "temp_path", "completed_at"
        )))

        val libraryColumns = columnNames("library_media")
        assertTrue(libraryColumns.containsAll(listOf(
            "id", "title", "creator", "album", "source", "duration_ms",
            "file_size_bytes", "readable_size", "media_type", "local_path",
            "mime_type", "added_date", "is_favorite", "playlist_name",
            // v2 additions
            "last_played", "play_count"
        )))
    }

    @Test
    fun `migration from v1 preserves rows and adds columns with honest defaults`() {
        // 1. Build the original v1 schema.
        exec(DatabaseSchema.downloadJobsV1)
        exec(DatabaseSchema.libraryMediaV1)

        // 2. Insert rows as a v1 app would have written them.
        exec(
            """
            INSERT INTO download_jobs (
                id, original_url, title, uploader, duration, media_type,
                format_id, format_label, container, mime_type, dest_category,
                state, progress, speed_bytes, downloaded_bytes, total_bytes,
                retry_count, created_at, updated_at
            ) VALUES (
                'job1', 'https://example.com/song.mp3', 'Song', 'Artist', '3:45', 'AUDIO',
                'f1', 'MP3 128kbps', 'mp3', 'audio/mpeg', 'MUSIC',
                'COMPLETED', 1.0, 0, 4096, 4096,
                0, 1000, 2000
            )
            """.trimIndent()
        )
        exec(
            """
            INSERT INTO library_media (
                id, title, creator, album, source, duration_ms, file_size_bytes,
                readable_size, media_type, local_path, mime_type, added_date, is_favorite
            ) VALUES (
                'lib1', 'Song', 'Artist', 'Album', 'Direct', 225000, 4096,
                '4.0 KB', 'AUDIO', '/data/song.mp3', 'audio/mpeg', 3000, 1
            )
            """.trimIndent()
        )

        // 3. Upgrade exactly as SmusicDatabase.onUpgrade does.
        DatabaseSchema.migrationStatements(1).forEach { exec(it) }

        // Existing data survives untouched.
        assertEquals("Song", queryString("SELECT title FROM download_jobs WHERE id = 'job1'"))
        assertEquals(4096L, queryLong("SELECT total_bytes FROM download_jobs WHERE id = 'job1'"))
        assertEquals("Song", queryString("SELECT title FROM library_media WHERE id = 'lib1'"))
        assertEquals(1L, queryLong("SELECT is_favorite FROM library_media WHERE id = 'lib1'"))

        // New columns exist and default to honest values for pre-existing rows.
        assertEquals(null, queryString("SELECT filename FROM download_jobs WHERE id = 'job1'"))
        assertEquals(null, queryNullableLong("SELECT completed_at FROM download_jobs WHERE id = 'job1'"))
        assertEquals(0L, queryLong("SELECT last_played FROM library_media WHERE id = 'lib1'"))
        assertEquals(0L, queryLong("SELECT play_count FROM library_media WHERE id = 'lib1'"))
    }

    @Test
    fun `upgrade from current version needs no statements`() {
        assertEquals(2, DatabaseSchema.VERSION)
        assertTrue(DatabaseSchema.migrationStatements(DatabaseSchema.VERSION).isEmpty())
    }

    @Test
    fun `unknown source versions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DatabaseSchema.migrationStatements(0) }
        assertThrows(IllegalArgumentException::class.java) { DatabaseSchema.migrationStatements(DatabaseSchema.VERSION + 1) }
    }

    @Test
    fun `migration statements are additive only`() {
        val statements = DatabaseSchema.migrationStatements(1)
        assertEquals(5, statements.size)
        statements.forEach { statement ->
            val upper = statement.uppercase()
            assertTrue("Migration must not drop/rewrite data: $statement", upper.startsWith("ALTER TABLE"))
        }
    }
}
