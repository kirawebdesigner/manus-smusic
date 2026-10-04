package com.smusic.app.domain.library

import com.smusic.app.data.database.LibraryItem
import java.io.File

/**
 * Case-insensitive in-memory library search over title, artist, album,
 * playlist, and filename. Operates on the already-loaded library list — it
 * never touches the filesystem or database per keystroke.
 */
object LibrarySearch {

    fun filter(items: List<LibraryItem>, query: String): List<LibraryItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { item -> matches(item, needle) }
    }

    fun matches(item: LibraryItem, needle: String): Boolean =
        item.title.contains(needle, ignoreCase = true) ||
            item.creator.contains(needle, ignoreCase = true) ||
            item.album.contains(needle, ignoreCase = true) ||
            (item.playlistName?.contains(needle, ignoreCase = true) == true) ||
            File(item.localPath).name.contains(needle, ignoreCase = true)
}
