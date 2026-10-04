package com.smusic.app.domain.library

import com.smusic.app.data.database.LibraryItem
import com.smusic.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure in-memory library search: case-insensitive matching over title,
 * creator, album, playlist, and filename — no I/O per keystroke.
 */
class LibrarySearchTest {

    private fun item(
        id: String,
        title: String,
        creator: String = "Unknown",
        album: String = "Smusic Library",
        playlistName: String? = null,
        localPath: String = "/storage/$id.mp3"
    ) = LibraryItem(
        id = id,
        title = title,
        creator = creator,
        album = album,
        mediaType = MediaType.AUDIO,
        localPath = localPath,
        playlistName = playlistName
    )

    private val library = listOf(
        item("1", title = "Midnight City", creator = "M83", album = "Hurry Up, We're Dreaming"),
        item("2", title = "Teardrop", creator = "Massive Attack", album = "Mezzanine", playlistName = "Chill"),
        item("3", title = "Intro", creator = "The xx", album = "xx", localPath = "/storage/the-xx-intro.mp3")
    )

    @Test
    fun `empty query returns the library untouched`() {
        assertEquals(library, LibrarySearch.filter(library, ""))
        assertEquals(library, LibrarySearch.filter(library, "   "))
    }

    @Test
    fun `matches title case-insensitively`() {
        val result = LibrarySearch.filter(library, "midnight")
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `matches creator and album`() {
        assertEquals(listOf("2"), LibrarySearch.filter(library, "massive").map { it.id })
        assertEquals(listOf("1"), LibrarySearch.filter(library, "dreaming").map { it.id })
    }

    @Test
    fun `matches playlist name`() {
        assertEquals(listOf("2"), LibrarySearch.filter(library, "chill").map { it.id })
    }

    @Test
    fun `matches the on-disk filename when metadata does not`() {
        // "the-xx-intro" appears only in the file name, not in title/creator/album.
        assertEquals(listOf("3"), LibrarySearch.filter(library, "the-xx").map { it.id })
    }

    @Test
    fun `returns nothing when no field matches`() {
        val result = LibrarySearch.filter(library, "definitely-not-here")
        assertTrue(result.isEmpty())
    }
}
