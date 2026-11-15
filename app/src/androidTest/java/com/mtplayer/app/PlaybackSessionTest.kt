package com.mtplayer.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtplayer.app.playback.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Verifies the media session and its notification on a real Android build: the notification must
 * appear, carry metadata, expose transport controls and follow track changes.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionTest {
    private val ctx = TestSupport.context
    private lateinit var controller: MediaController
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun item(file: File, id: String, title: String) = MediaItem.Builder()
        .setUri(Uri.fromFile(file)).setMediaId(id).setMimeType("audio/wav")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("MT CI").setIsPlayable(true).setIsBrowsable(false).build())
        .build()

    private fun mediaNotification(): Notification? =
        ctx.getSystemService(NotificationManager::class.java).activeNotifications
            .map { it.notification }
            .firstOrNull { it.channelId == PlaybackService.NOTIFICATION_CHANNEL }

    @Before fun setUp() {
        TestSupport.grantAll()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        controller = MediaController.Builder(ctx, token).setApplicationLooper(main.looper).buildAsync().get(20, TimeUnit.SECONDS)
    }

    @After fun tearDown() {
        onMain { controller.stop(); controller.clearMediaItems(); controller.release() }
    }

    @Test fun notificationAppearsWithMetadataControlsAndFollowsTrackChanges() {
        val a = File(ctx.cacheDir, "ci_a.wav").also { TestSupport.writeTone(it, 40) }
        val b = File(ctx.cacheDir, "ci_b.wav").also { TestSupport.writeTone(it, 40, 660.0) }
        onMain {
            controller.setMediaItems(listOf(item(a, "uri:a", "CI Track A"), item(b, "uri:b", "CI Track B")))
            controller.prepare(); controller.play()
        }
        assertTrue("player did not start", TestSupport.waitUntil(15_000) { onMain { controller.isPlaying } })
        assertEquals("CI Track A", onMain { controller.mediaMetadata.title?.toString() })

        assertTrue("media notification not posted", TestSupport.waitUntil(15_000) { mediaNotification() != null })
        assertTrue("notification title wrong", TestSupport.waitUntil(10_000) {
            mediaNotification()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "CI Track A"
        })
        val n = mediaNotification()!!
        assertTrue("expected transport actions, got ${n.actions?.size}", (n.actions?.size ?: 0) >= 3)
        assertTrue("notification must use MediaStyle session", n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))

        onMain { controller.seekToNextMediaItem() }
        assertTrue("notification did not update on track change", TestSupport.waitUntil(10_000) {
            mediaNotification()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "CI Track B"
        })

        onMain { controller.pause() }
        assertTrue(TestSupport.waitUntil(5_000) { onMain { !controller.isPlaying } })
        assertTrue("notification must stay while paused", mediaNotification() != null)

        val commands = onMain { controller.availableCommands }
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
        assertTrue(commands.contains(Player.COMMAND_SET_REPEAT_MODE))
    }

    @Test fun shuffleRepeatAndSeekWork() {
        val a = File(ctx.cacheDir, "ci_c.wav").also { TestSupport.writeTone(it, 40) }
        onMain { controller.setMediaItem(item(a, "uri:c", "CI Track C")); controller.prepare() }
        assertTrue(TestSupport.waitUntil(10_000) { onMain { controller.playbackState == Player.STATE_READY } })
        onMain { controller.seekTo(20_000); controller.shuffleModeEnabled = true; controller.repeatMode = Player.REPEAT_MODE_ONE }
        assertTrue(TestSupport.waitUntil(5_000) { onMain { controller.currentPosition in 19_000..21_500 } })
        assertTrue(onMain { controller.shuffleModeEnabled })
        assertEquals(Player.REPEAT_MODE_ONE, onMain { controller.repeatMode })
    }
}
