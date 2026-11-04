package com.example.ui

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.example.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fake MediaStore with realistic edge cases (same-named folders, unknown tags, duplicates). */
class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun query(uri: Uri, projection: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
        val cols = projection ?: arrayOf(MediaStore.MediaColumns._ID)
        val c = MatrixCursor(cols)
        val isVideo = uri.pathSegments.contains("video")
        val n = if (isVideo) 30 else 60
        for (i in 1..n) {
            val folder = if (i % 3 == 0) "Download" else if (i % 3 == 1) "Music" else "<unknown>"
            val root = if (i % 2 == 0) "/storage/emulated/0" else "/storage/1234-ABCD"
            val row: List<Any?> = cols.map { col ->
                when (col) {
                    MediaStore.MediaColumns._ID -> i.toLong()
                    MediaStore.MediaColumns.TITLE -> if (i % 7 == 0) null else "Item $i"
                    MediaStore.Audio.AudioColumns.ARTIST -> if (i % 5 == 0) "<unknown>" else "Artist ${i % 4}"
                    MediaStore.Audio.AudioColumns.ALBUM -> if (i % 2 == 0) "Album A" else "album a"
                    MediaStore.Audio.AudioColumns.ALBUM_ID -> (i % 3).toLong()
                    MediaStore.MediaColumns.DURATION -> if (i % 9 == 0) 0L else 180_000L + i
                    MediaStore.MediaColumns.SIZE -> 1_000_000L * i
                    MediaStore.MediaColumns.DATE_ADDED -> 1_700_000_000L + i
                    MediaStore.MediaColumns.DATA -> "$root/$folder/file$i.${if (isVideo) "mp4" else "mp3"}"
                    MediaStore.MediaColumns.RELATIVE_PATH -> "$folder/"
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> folder
                    MediaStore.MediaColumns.WIDTH -> 1920
                    MediaStore.MediaColumns.HEIGHT -> 1080
                    else -> null
                }
            }
            c.addRow(row.toTypedArray<Any?>())
        }
        return c
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppNavigationCrashTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // Must exist before MainActivity starts scanning.
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
        }
    }

    @Before fun grant() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun tap(tag: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun tabsIfPresent(prefix: String, count: Int) {
        for (i in 0 until count) {
            val tag = "${prefix}_$i"
            if (rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) tap(tag)
        }
    }

    private fun snap(name: String) {
        try {
            val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
            java.io.File(dir, "app_$name.png").outputStream().use {
                rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].captureToImage()
                    .asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it)
            }
        } catch (_: Throwable) {}
    }

    @Test fun navigateEverywhereAndBack() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_tab_music", true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        snap("home"); tap("nav_tab_videos"); snap("videos")
        repeat(3) {
            tap("nav_tab_music"); tabsIfPresent("music_tab", 5)
            tap("nav_tab_videos"); tabsIfPresent("video_tab", 5)
            tap("nav_tab_library")
            rule.onAllNodesWithTag("playlist_item_workout_mix", true).fetchSemanticsNodes().firstOrNull()?.let {
                tap("playlist_item_workout_mix"); back()
            }
            tap("nav_tab_home")
            tap("nav_tab_videos"); tap("nav_tab_music"); tap("nav_tab_library")
            back(); back()
            tap("nav_tab_music")
            tap("topbar_settings_action"); back()
        }
    }
}
