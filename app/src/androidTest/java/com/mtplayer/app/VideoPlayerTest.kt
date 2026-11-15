package com.mtplayer.app

import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.mtplayer.app.video.VideoPlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Plays the CI clip, checks playback, picture-in-picture on Home, and saved resume position. */
@RunWith(AndroidJUnit4::class)
class VideoPlayerTest {
    private val instr = InstrumentationRegistry.getInstrumentation()

    private fun clipId(): Long? = TestSupport.context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
        "${MediaStore.Video.Media.TITLE} = ?", arrayOf(AppUiTest.CLIP), null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun activity(): VideoPlayerActivity? {
        var found: VideoPlayerActivity? = null
        instr.runOnMainSync {
            found = listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED).firstNotNullOfOrNull { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).filterIsInstance<VideoPlayerActivity>().firstOrNull()
            }
        }
        return found
    }

    private fun <T> onMain(block: () -> T): T { var r: T? = null; instr.runOnMainSync { r = block() }; @Suppress("UNCHECKED_CAST") return r as T }

    @Test fun playsEntersPipAndSavesResumePosition() {
        TestSupport.grantAll()
        val id = clipId()
        assumeTrue("CI clip not present", id != null)
        VideoPlayerActivity.start(TestSupport.context, id!!, listOf(id))

        assertTrue("video activity not shown", TestSupport.waitUntil(15_000) { activity() != null })
        val act = activity()!!
        assertTrue("video did not start", TestSupport.waitUntil(15_000) { onMain { act.ui.isPlaying } })
        assertTrue("no error expected", onMain { act.ui.error == null })
        Thread.sleep(3_000)

        // Leaving with Home while playing must enter picture-in-picture (auto-PiP default on).
        UiDevice.getInstance(instr).pressHome()
        assertTrue("did not enter PiP", TestSupport.waitUntil(10_000) { onMain { act.isInPictureInPictureMode } })
        assertTrue("PiP state not reflected in UI", onMain { act.ui.inPip })

        instr.runOnMainSync { act.finish() }
        assertTrue(TestSupport.waitUntil(10_000) { onMain { act.lifecycle.currentState == Lifecycle.State.DESTROYED } })

        val app = TestSupport.context.applicationContext as MTApp
        val progress = TestSupport.waitUntil(5_000) {
            runBlocking { app.container.db.videoProgress().get(id) }?.positionMs?.let { it > 1_000 } == true
        }
        assertTrue("resume position not saved", progress)
        assertNotNull(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id))
    }
}
