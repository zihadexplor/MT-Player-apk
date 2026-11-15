package com.mtplayer.app

import android.app.NotificationManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtplayer.app.feature.shell.MainActivity
import com.mtplayer.app.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlinx.coroutines.runBlocking
import com.mtplayer.app.designsystem.theme.ThemeMode

/**
 * End-to-end UI checks on the real app. Tests that need media use files that CI pushes to
 * /sdcard/Music and /sdcard/Movies (see .github/scripts/emulator-tests.sh); they are skipped
 * when that media is absent.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUiTest {
    init { TestSupport.grantAll() }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private fun s(id: Int, vararg args: Any) = res.getString(id, *args)
    private val app get() = TestSupport.context.applicationContext as MTApp

    @Before fun waitForShell() {
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.tab_home)), 15_000)
    }

    @Test fun a_tabsSettingsAndSearchWork() {
        for (tab in listOf(R.string.tab_music, R.string.tab_videos, R.string.tab_saved, R.string.tab_home)) {
            rule.onAllNodesWithText(s(tab)).onFirst().performClick()
            rule.waitForIdle()
        }
        rule.onAllNodesWithContentDescription(s(R.string.settings)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.theme)), 5_000)
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        for ((mode, expected) in listOf(
            R.string.theme_light to ThemeMode.LIGHT,
            R.string.theme_dark to ThemeMode.DARK,
            R.string.theme_system to ThemeMode.SYSTEM,
        )) {
            rule.onAllNodesWithText(s(R.string.theme)).onFirst().performClick()
            rule.waitUntilAtLeastOneExists(hasText(s(mode)) and radio, 5_000)
            rule.onNode(hasText(s(mode)) and radio).performClick()
            rule.waitUntil(5_000) { runBlocking { app.container.settings.current().themeMode } == expected }
            rule.waitUntil(5_000) { rule.onAllNodes(radio).fetchSemanticsNodes().isEmpty() }
        }
        rule.onNodeWithContentDescription(s(R.string.back)).performClick()
        rule.onAllNodesWithContentDescription(s(R.string.search)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasSetTextAction(), 5_000)
        rule.onNode(hasSetTextAction()).performTextInput("zzqx-no-such-media")
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.search_none_title)), 5_000)
    }

    @Test fun b_musicPlaybackFavouritePlaylistAndClose() {
        assumeTrue("CI media not present", TestSupport.mediaStoreHas(TONE_ONE, video = false))
        rule.onAllNodesWithText(s(R.string.tab_music)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(TONE_ONE), 20_000)
        rule.onAllNodesWithText(TONE_ONE).onFirst().performClick()

        // Mini player appears and the service plays the tapped song.
        rule.waitUntilAtLeastOneExists(hasContentDescription(s(R.string.close_player)), 10_000)
        assertTrue(TestSupport.waitUntil(15_000) { app.container.player.state.value.isPlaying })
        assertEquals(TONE_ONE, app.container.player.state.value.current?.title)
        val nm = TestSupport.context.getSystemService(NotificationManager::class.java)
        assertTrue("no media notification", TestSupport.waitUntil(15_000) {
            nm.activeNotifications.any { it.notification.channelId == PlaybackService.NOTIFICATION_CHANNEL }
        })

        // Next from the mini player.
        rule.onNodeWithContentDescription(s(R.string.next)).performClick()
        assertTrue("next did not change track", TestSupport.waitUntil(10_000) { app.container.player.state.value.current?.title == TONE_TWO })

        // Favourite from the song menu, then it is listed in Saved.
        rule.onAllNodesWithContentDescription(s(R.string.more_options_for, TONE_ONE)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.action_favorite)), 5_000)
        rule.onNodeWithText(s(R.string.action_favorite)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.added_to_favorites)), 5_000)

        // New playlist with the song from the add-to-playlist sheet.
        rule.onAllNodesWithContentDescription(s(R.string.more_options_for, TONE_ONE)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.add_to_playlist)), 5_000)
        rule.onNodeWithText(s(R.string.add_to_playlist)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.new_playlist)), 5_000)
        rule.onAllNodesWithText(s(R.string.new_playlist)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasSetTextAction(), 5_000)
        rule.onNode(hasSetTextAction()).performTextInput("CI list")
        rule.onNodeWithText(s(R.string.create)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.playlist_created, "CI list")), 5_000)

        rule.onAllNodesWithText(s(R.string.tab_saved)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(TONE_ONE), 5_000)

        // Close button stops playback and hides the mini player.
        rule.onNodeWithContentDescription(s(R.string.close_player)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.playback_stopped)), 5_000)
        rule.waitUntilDoesNotExist(hasContentDescription(s(R.string.close_player)), 5_000)
        assertTrue(TestSupport.waitUntil(5_000) { !app.container.player.state.value.isPlaying })
    }

    companion object {
        const val TONE_ONE = "mt_ci_tone_one"
        const val TONE_TWO = "mt_ci_tone_two"
        const val CLIP = "mt_ci_clip"
    }
}
