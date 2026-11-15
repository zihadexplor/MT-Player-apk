package com.mtplayer.app.data.repo

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    val plain: String,
    val source: Source,
) {
    enum class Source { EMBEDDED, SIDECAR, LINKED }
    val isSynced: Boolean get() = lines.isNotEmpty()

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
    fun activeIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\[[a-zA-Z]+:.*]$""")

    /**
     * Parses LRC (synced) or plain text. Supports multiple time tags per line, 2- or 3-digit
     * fractions and the `[offset:]` tag. Returns null for blank input.
     */
    fun parse(raw: String, source: Lyrics.Source): Lyrics? {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return null
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = ArrayList<LyricLine>()
        val plain = StringBuilder()
        for (line in text.lines()) {
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) {
                val t = line.trim()
                if (!metaTag.matches(t)) plain.appendLine(t)
                continue
            }
            // Only leading tags are timestamps; the rest of the line is the lyric.
            var end = 0
            val times = ArrayList<Long>()
            for (m in tags) {
                if (m.range.first != end) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                val frac = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                times += (min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0)
                end = m.range.last + 1
            }
            val lyric = line.substring(end).trim()
            times.forEach { lines += LyricLine(it, lyric) }
            plain.appendLine(lyric)
        }
        lines.sortBy { it.timeMs }
        val plainText = plain.toString().trim('\n')
        if (lines.isEmpty() && plainText.isBlank()) return null
        return Lyrics(lines, plainText, source)
    }
}
