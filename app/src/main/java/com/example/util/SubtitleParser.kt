package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Lightweight offline parser for `.srt` and `.vtt` external subtitles.
 * Enables smooth real-time subtitle delay synchronization inside VideoPlayerModal.
 */
object SubtitleParser {

    private val timeLineRegex = Regex(
        """(\d{1,2}:)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[,.](\d{3})"""
    )

    suspend fun loadFromUri(context: Context, uri: Uri): List<SubtitleCue> = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull() ?: return@withContext emptyList()
        parse(raw)
    }

    fun parse(raw: String): List<SubtitleCue> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("""\n\s*\n"""))
        val cues = mutableListOf<SubtitleCue>()

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            if (lines[0].startsWith("WEBVTT", ignoreCase = true) || lines[0].startsWith("NOTE")) continue

            val timingIndex = lines.indexOfFirst { timeLineRegex.containsMatchIn(it) }
            if (timingIndex < 0) continue

            val match = timeLineRegex.find(lines[timingIndex]) ?: continue
            val startMs = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            val endMs = parseTimestampMs(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            )

            val textLines = lines.drop(timingIndex + 1)
            val cleanText = textLines.joinToString("\n")
                .replace(Regex("""<[^>]+>"""), "")
                .trim()

            if (cleanText.isNotBlank() && endMs >= startMs) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = cleanText))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun findActiveCueText(cues: List<SubtitleCue>, positionMs: Long, delayMs: Long): String? {
        if (cues.isEmpty()) return null
        // Positive delay means subtitles appear later -> look at an earlier timestamp in cue list
        val effectivePos = positionMs - delayMs
        if (effectivePos < 0L) return null
        val active = cues.filter { effectivePos in it.startMs..it.endMs }
        if (active.isEmpty()) return null
        return active.joinToString("\n") { it.text }
    }

    private fun parseTimestampMs(hoursRaw: String, minsRaw: String, secsRaw: String, millisRaw: String): Long {
        val hours = hoursRaw.removeSuffix(":").toLongOrNull() ?: 0L
        val mins = minsRaw.toLongOrNull() ?: 0L
        val secs = secsRaw.toLongOrNull() ?: 0L
        val millis = millisRaw.toLongOrNull() ?: 0L
        return ((hours * 3600L + mins * 60L + secs) * 1000L) + millis
    }
}
