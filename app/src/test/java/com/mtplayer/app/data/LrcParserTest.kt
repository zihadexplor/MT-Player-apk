package com.mtplayer.app.data

import com.mtplayer.app.data.repo.LrcParser
import com.mtplayer.app.data.repo.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    private val src = Lyrics.Source.SIDECAR

    @Test fun parsesSyncedLinesSortedWithFractions() {
        val l = LrcParser.parse("[ti:Song]\n[00:12.5]Second\n[00:01.23]First\n[01:02.345]Third", src)!!
        assertTrue(l.isSynced)
        assertEquals(listOf(1230L, 12500L, 62345L), l.lines.map { it.timeMs })
        assertEquals(listOf("First", "Second", "Third"), l.lines.map { it.text })
    }

    @Test fun multipleTagsAndOffset() {
        val l = LrcParser.parse("[offset:+500]\n[00:10.00][00:20.00]Chorus", src)!!
        assertEquals(listOf(9500L, 19500L), l.lines.map { it.timeMs })
        assertTrue(l.lines.all { it.text == "Chorus" })
    }

    @Test fun plainTextIsNotSynced() {
        val l = LrcParser.parse("\uFEFFLine one\r\nLine two", src)!!
        assertFalse(l.isSynced)
        assertEquals("Line one\nLine two", l.plain)
    }

    @Test fun blankAndMetadataOnlyReturnNull() {
        assertNull(LrcParser.parse("   \n", src))
        assertNull(LrcParser.parse("[ar:Artist]\n[ti:Title]", src))
    }

    @Test fun activeIndexBinarySearch() {
        val l = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c", src)!!
        assertEquals(-1, l.activeIndex(500))
        assertEquals(0, l.activeIndex(1000))
        assertEquals(1, l.activeIndex(8999))
        assertEquals(2, l.activeIndex(60_000))
    }
}
