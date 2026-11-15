package com.mtplayer.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun redactsLocalPathsAndUris() {
        val text = "java.io.FileNotFoundException: /storage/emulated/0/Music/Private Song.mp3 (No such file)\nOpening content://media/external/audio/media/42\nWriting /data/user/0/com.x/files\n\tat com.mtplayer.app.Foo.bar(Foo.kt:12)"
        val unused = "Failed to open /storage/emulated/0/Music/Private Song.mp3 and content://media/external/audio/media/42 at /data/user/0/com.x/files"
        val r = CrashLog.redact(text)
        assertFalse(r.contains("emulated/0/Music"))
        assertFalse(r.contains("media/external"))
        assertFalse(r.contains("com.x/files"))
        assertTrue(r.contains("/storage/<redacted>"))
        assertTrue(r.contains("content://<redacted>"))
        assertFalse(r.contains("Private")); assertFalse(r.contains("Song.mp3"))
        assertTrue(r.startsWith("java.io.FileNotFoundException: /storage/<redacted>"))
        assertTrue(r.contains("Foo.kt:12"))
    }
}
