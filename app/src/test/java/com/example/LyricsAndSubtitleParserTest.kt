package com.example

import com.example.util.LyricsParser
import com.example.util.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndSubtitleParserTest {

    @Test
    fun `parseLrcContent parses timestamps, multi-tag lines, and offset`() {
        val lrc = """
            [ar:Archive Artist]
            [offset:500]
            [00:10.50]First synced line
            [00:15.00][00:45.25]Repeated chorus line
        """.trimIndent()

        val result = LyricsParser.parseLrcContent(lrc, source = "Test .lrc")
        assertTrue(result.isSynced)
        assertFalse(result.isEmpty)
        assertEquals("Test .lrc", result.sourceLabel)
        assertEquals(3, result.syncedLines.size)

        // offset = +500 subtracts 500ms from tag timestamps
        assertEquals(10_000L, result.syncedLines[0].timeMs)
        assertEquals("First synced line", result.syncedLines[0].text)

        assertEquals(14_500L, result.syncedLines[1].timeMs)
        assertEquals("Repeated chorus line", result.syncedLines[1].text)

        assertEquals(44_750L, result.syncedLines[2].timeMs)
        assertEquals("Repeated chorus line", result.syncedLines[2].text)
    }

    @Test
    fun `parseLrcContent falls back cleanly to plain lyrics`() {
        val plain = "Verse one line A\nVerse one line B\n\nChorus line"
        val result = LyricsParser.parseLrcContent(plain)
        assertFalse(result.isSynced)
        assertFalse(result.isEmpty)
        assertTrue(result.plainText.contains("Verse one line A"))
        assertTrue(result.plainText.contains("Chorus line"))
        assertTrue(LyricsParser.parseLrcContent("   ").isEmpty)
    }

    @Test
    fun `SubtitleParser parses SRT and strips HTML formatting tags`() {
        val srt = """
            1
            00:00:01,500 --> 00:00:04,200
            <i>Opening line</i>
            Second line

            2
            00:00:05,000 --> 00:00:07,000
            <b>Action</b> cue
        """.trimIndent()

        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1_500L, cues[0].startMs)
        assertEquals(4_200L, cues[0].endMs)
        assertEquals("Opening line\nSecond line", cues[0].text)
        assertEquals("Action cue", cues[1].text)
    }

    @Test
    fun `SubtitleParser parses WebVTT and applies positive and negative delay sync`() {
        val vtt = """
            WEBVTT

            NOTE Header comment ignored

            00:02.000 --> 00:04.000
            Synced caption
        """.trimIndent()

        val cues = SubtitleParser.parse(vtt)
        assertEquals(1, cues.size)
        assertEquals(2_000L, cues[0].startMs)
        assertEquals(4_000L, cues[0].endMs)

        // No delay -> active at 2500ms, silent at 4500ms
        assertEquals("Synced caption", SubtitleParser.findActiveCueText(cues, positionMs = 2_500L, delayMs = 0L))
        assertNull(SubtitleParser.findActiveCueText(cues, positionMs = 4_500L, delayMs = 0L))

        // Positive +1000ms delay -> caption appears 1s later (at video position 3000..5000ms)
        assertNull(SubtitleParser.findActiveCueText(cues, positionMs = 2_500L, delayMs = 1_000L))
        assertEquals("Synced caption", SubtitleParser.findActiveCueText(cues, positionMs = 4_500L, delayMs = 1_000L))

        // Negative -500ms delay -> caption appears 0.5s earlier (at video position 1500..3500ms)
        assertEquals("Synced caption", SubtitleParser.findActiveCueText(cues, positionMs = 1_600L, delayMs = -500L))
    }
}
