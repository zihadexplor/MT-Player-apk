package com.mtplayer.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test fun versionComparison() {
        assertTrue(UpdateManager.compareVersions("1.4.0", "1.3.0") > 0)
        assertTrue(UpdateManager.compareVersions("v1.10.0", "1.9.9") > 0)
        assertEquals(0, UpdateManager.compareVersions("1.3", "1.3.0"))
        assertEquals(0, UpdateManager.compareVersions("2.0.0-debug", "v2.0.0"))
        assertTrue(UpdateManager.compareVersions("1.3.0", "2.0.0") < 0)
    }

    @Test fun checksumParsing() {
        val hex = "a".repeat(64)
        val hex2 = "B".repeat(64)
        val map = UpdateManager.parseChecksums("$hex  release-MT-Player-v2.0.0.apk\n$hex2 *other.apk\ngarbage line\n")
        assertEquals(hex, map["release-MT-Player-v2.0.0.apk"])
        assertEquals("b".repeat(64), map["other.apk"])
        assertEquals(2, map.size)
    }

    @Test fun sha256OfBytes() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", UpdateManager.sha256(ByteArray(0)))
    }
}
