package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsResult(
    val syncedLines: List<LyricLine> = emptyList(),
    val plainText: String = "",
    val sourceLabel: String = ""
) {
    val isEmpty: Boolean get() = syncedLines.isEmpty() && plainText.isBlank()
    val isSynced: Boolean get() = syncedLines.isNotEmpty()
}

/**
 * Parses local `.lrc` / `.txt` sidecar lyrics files and safely scans ID3v2 USLT embedded lyrics.
 * Zero network usage — operates strictly on local device files.
 */
object LyricsParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val metaTagRegex = Regex("""^\[[a-zA-Z]+:.*]$""")

    suspend fun loadLyricsForTrack(
        filePath: String?,
        title: String,
        artist: String
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (!filePath.isNullOrBlank()) {
            val audioFile = File(filePath)
            val parent = audioFile.parentFile
            val baseName = audioFile.nameWithoutExtension

            // 1. Exact sidecar `.lrc` / `.txt` next to audio file
            if (parent != null && parent.exists()) {
                val lrcCandidates = listOf(
                    File(parent, "$baseName.lrc"),
                    File(parent, "$baseName.LRC"),
                    File(parent, "${sanitizeFileName(artist)} - ${sanitizeFileName(title)}.lrc"),
                    File(parent, "${sanitizeFileName(title)}.lrc")
                )
                for (candidate in lrcCandidates) {
                    if (candidate.exists() && candidate.isFile && candidate.length() in 1L..(512L * 1024L)) {
                        val parsed = runCatching {
                            parseLrcContent(candidate.readText(Charsets.UTF_8), source = "Sidecar .lrc")
                        }.getOrNull()
                        if (parsed != null && !parsed.isEmpty) return@withContext parsed
                    }
                }

                val txtCandidates = listOf(
                    File(parent, "$baseName.txt"),
                    File(parent, "${sanitizeFileName(title)}.txt")
                )
                for (candidate in txtCandidates) {
                    if (candidate.exists() && candidate.isFile && candidate.length() in 1L..(256L * 1024L)) {
                        val raw = runCatching { candidate.readText(Charsets.UTF_8).trim() }.getOrNull()
                        if (!raw.isNullOrBlank()) {
                            return@withContext LyricsResult(plainText = raw, sourceLabel = "Sidecar .txt")
                        }
                    }
                }
            }

            // 2. Embedded ID3v2 USLT lyrics tag inside MP3 files
            if (audioFile.exists() && audioFile.isFile && filePath.endsWith(".mp3", ignoreCase = true)) {
                val embedded = runCatching { extractEmbeddedId3Uslt(audioFile) }.getOrNull()
                if (!embedded.isNullOrBlank()) {
                    val parsed = parseLrcContent(embedded, source = "Embedded ID3")
                    if (!parsed.isEmpty) return@withContext parsed
                }
            }
        }
        LyricsResult()
    }

    fun parseLrcContent(raw: String, source: String = "Local Lyrics"): LyricsResult {
        if (raw.isBlank()) return LyricsResult()
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()

        var globalOffsetMs = 0L
        lines.forEach { line ->
            offsetRegex.find(line)?.let { match ->
                globalOffsetMs = match.groupValues[1].toLongOrNull() ?: 0L
            }
        }

        val synced = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val matches = timestampRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val content = timestampRegex.replace(trimmed, "").trim()
                for (match in matches) {
                    val mins = match.groupValues[1].toLongOrNull() ?: 0L
                    val secs = match.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = match.groupValues[3]
                    val millis = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    val timeMs = ((mins * 60L + secs) * 1000L + millis - globalOffsetMs).coerceAtLeast(0L)
                    synced.add(LyricLine(timeMs = timeMs, text = content.ifBlank { "♪" }))
                }
            } else if (!metaTagRegex.matches(trimmed)) {
                plainLines.add(trimmed)
            }
        }

        val sortedSynced = synced.sortedBy { it.timeMs }
        return if (sortedSynced.isNotEmpty()) {
            LyricsResult(
                syncedLines = sortedSynced,
                plainText = sortedSynced.joinToString("\n") { it.text },
                sourceLabel = source
            )
        } else {
            LyricsResult(
                syncedLines = emptyList(),
                plainText = plainLines.joinToString("\n").trim(),
                sourceLabel = source
            )
        }
    }

    private fun extractEmbeddedId3Uslt(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return null
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }
            val majorVersion = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val tagSize = syncSafeToInt(header[6], header[7], header[8], header[9])
                .coerceAtMost(512 * 1024)

            if ((flags and 0x40) != 0 && tagSize > 4) {
                val extHeader = ByteArray(4)
                raf.readFully(extHeader)
                val extSize = if (majorVersion >= 4) syncSafeToInt(extHeader[0], extHeader[1], extHeader[2], extHeader[3])
                else ((extHeader[0].toInt() and 0xFF) shl 24) or ((extHeader[1].toInt() and 0xFF) shl 16) or
                    ((extHeader[2].toInt() and 0xFF) shl 8) or (extHeader[3].toInt() and 0xFF)
                if (extSize > 0 && extSize < tagSize) {
                    raf.seek(10L + extSize)
                }
            }

            while (raf.filePointer < 10L + tagSize && raf.filePointer + 10 < raf.length()) {
                val frameHeader = ByteArray(10)
                raf.readFully(frameHeader)
                val frameId = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
                if (frameId.all { it == '\u0000' }) break

                val frameSize = if (majorVersion >= 4) {
                    syncSafeToInt(frameHeader[4], frameHeader[5], frameHeader[6], frameHeader[7])
                } else {
                    ((frameHeader[4].toInt() and 0xFF) shl 24) or
                        ((frameHeader[5].toInt() and 0xFF) shl 16) or
                        ((frameHeader[6].toInt() and 0xFF) shl 8) or
                        (frameHeader[7].toInt() and 0xFF)
                }

                if (frameSize <= 0 || frameSize > 256 * 1024) break

                if (frameId == "USLT" && frameSize > 4) {
                    val data = ByteArray(frameSize)
                    raf.readFully(data)
                    val encodingByte = data[0].toInt() and 0xFF
                    val charset: Charset = when (encodingByte) {
                        1 -> Charsets.UTF_16
                        2 -> Charsets.UTF_16BE
                        3 -> Charsets.UTF_8
                        else -> Charsets.ISO_8859_1
                    }
                    // Skip 3-byte language code + null-terminated content descriptor
                    var pos = 4
                    if (encodingByte == 1 || encodingByte == 2) {
                        while (pos + 1 < data.size && !(data[pos] == 0.toByte() && data[pos + 1] == 0.toByte())) {
                            pos += 2
                        }
                        pos += 2
                    } else {
                        while (pos < data.size && data[pos] != 0.toByte()) {
                            pos++
                        }
                        pos++
                    }
                    if (pos < data.size) {
                        return String(data, pos, data.size - pos, charset).trim('\u0000', ' ', '\n', '\r')
                    }
                } else {
                    raf.seek(raf.filePointer + frameSize)
                }
            }
        }
        return null
    }

    private fun syncSafeToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
            ((b1.toInt() and 0x7F) shl 14) or
            ((b2.toInt() and 0x7F) shl 7) or
            (b3.toInt() and 0x7F)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }
}
