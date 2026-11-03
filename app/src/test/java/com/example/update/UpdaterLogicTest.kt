package com.example.update

import com.example.update.ui.NoteLine
import com.example.update.ui.parseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdaterLogicTest {

    @Test
    fun semver_comparesNumericallyNotLexically() {
        assertTrue(SemVer.isNewer("1.10.0", "1.9.9"))
        assertTrue(SemVer.isNewer("v2.0.0", "1.99.99"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.1"))
        assertEquals(0, SemVer.compare("v1.2", "1.2.0"))
        assertEquals(0, SemVer.compare("1.2.3-debug", "1.2.3"))
    }

    private val sampleRelease = """
        {
          "tag_name": "v1.2.0",
          "name": "MT Player v1.2.0",
          "html_url": "https://github.com/o/r/releases/tag/v1.2.0",
          "published_at": "2026-11-03T10:00:00Z",
          "body": "## What's changed\n- feat: new updater (abc1234)\n<!-- versionCode: 42 -->",
          "assets": [
            { "id": 1, "name": "SHA256SUMS.txt", "size": 100, "url": "u1", "browser_download_url": "b1" },
            { "id": 2, "name": "release-MT-Player-v1.2.0.apk", "size": 12345, "url": "https://api.github.com/x/2",
              "browser_download_url": "https://github.com/x/2.apk", "digest": "sha256:ABCDEF" }
          ]
        }
    """.trimIndent()

    @Test
    fun parseRelease_picksApkAndVersionCode() {
        val r = GitHubReleaseClient.parseRelease(sampleRelease, UpdateSource.PUBLIC)
        assertEquals("1.2.0", r.versionName)
        assertEquals(42L, r.versionCode)
        assertEquals("release-MT-Player-v1.2.0.apk", r.asset.name)
        assertEquals("abcdef", r.asset.sha256)
        assertFalse(r.notes.contains("versionCode"))
    }

    @Test
    fun releaseCodec_roundTrips() {
        val r = GitHubReleaseClient.parseRelease(sampleRelease, UpdateSource.AUTHENTICATED)
        assertEquals(r, ReleaseCodec.decode(ReleaseCodec.encode(r)))
        assertNull(ReleaseCodec.decode("not json"))
    }

    @Test
    fun releaseNotes_parseHeadingsAndBullets() {
        val lines = parseNotes("## Features\n- **Updater** with [link](http://x)\n\nPlain text\n<!-- hidden -->")
        assertEquals(NoteLine.Heading("Features"), lines[0])
        assertEquals(NoteLine.Bullet("Updater with link"), lines[1])
        assertEquals(NoteLine.Paragraph("Plain text"), lines[2])
        assertEquals(3, lines.size)
    }
}
