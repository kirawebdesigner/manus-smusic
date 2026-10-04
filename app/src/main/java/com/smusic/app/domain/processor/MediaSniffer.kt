package com.smusic.app.domain.processor

import java.io.File
import java.io.RandomAccessFile

/**
 * Infers a media container extension from a file's magic bytes.
 *
 * Direct URLs frequently expose no filename (or only `application/octet-stream`),
 * which leaves the stored file with a generic extension Media3 can't route to a
 * renderer. After a download completes, the worker uses this sniffer to give the
 * file a truthful extension based on its actual content.
 */
object MediaSniffer {

    /** Reads a small header from [file] and returns a known extension, or null. */
    fun sniffExtension(file: File): String? {
        if (!file.isFile || file.length() < 16L) return null
        val header = ByteArray(64)
        val read = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.read(header)
            }
        }.getOrDefault(-1)
        if (read < 16) return null
        return sniff(header)
    }

    /** Pure magic-byte classification over a file header. */
    fun sniff(header: ByteArray): String? {
        if (header.size < 16) return null

        // ID3 tag or MPEG frame sync → MP3
        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            return "mp3"
        }
        if ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0) {
            return "mp3"
        }

        // FLAC
        if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
        ) {
            return "flac"
        }

        // Ogg container (audio/opus, vorbis, flac-in-ogg)
        if (header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()
        ) {
            return "ogg"
        }

        // RIFF....WAVE
        if (matches(header, 0, "RIFF") && matches(header, 8, "WAVE")) {
            return "wav"
        }

        // ISO-BMFF (MP4 / M4A): size at 0..3, 'ftyp' at 4..7, brand at 8..11
        if (matches(header, 4, "ftyp")) {
            return when {
                matches(header, 8, "M4A") || matches(header, 8, "M4B") -> "m4a"
                else -> "mp4"
            }
        }

        // EBML (Matroska / WebM): 1A 45 DF A3
        if ((header[0].toInt() and 0xFF) == 0x1A && (header[1].toInt() and 0xFF) == 0x45 &&
            (header[2].toInt() and 0xFF) == 0xDF && (header[3].toInt() and 0xFF) == 0xA3
        ) {
            val head = String(header, Charsets.ISO_8859_1)
            return if (head.contains("webm")) "webm" else "mkv"
        }

        return null
    }

    private fun matches(header: ByteArray, offset: Int, token: String): Boolean {
        if (offset + token.length > header.size) return false
        for (i in token.indices) {
            if (header[offset + i] != token[i].code.toByte()) return false
        }
        return true
    }
}
