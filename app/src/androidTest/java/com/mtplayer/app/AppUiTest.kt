package com.mtplayer.app

import android.app.NotificationManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtplayer.app.data.prefs.VideoLayout
import com.mtplayer.app.designsystem.components.DayPart
import com.mtplayer.app.designsystem.components.Greeting
import com.mtplayer.app.designsystem.theme.ThemeMode
import com.mtplayer.app.feature.shell.MainActivity
import com.mtplayer.app.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

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

    @Test fun c_videosGridListFavouriteAndFolders() {
        assumeTrue("CI media not present", TestSupport.mediaStoreHas(CLIP, video = true))
        rule.onAllNodesWithText(s(R.string.tab_videos)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(CLIP), 20_000)
        val settings = app.container.settings
        val before = runBlocking { settings.current().videoLayout }
        // Toggle layout twice: the clip must stay visible in both grid and list form.
        repeat(2) {
            val toList = runBlocking { settings.current().videoLayout } == VideoLayout.GRID
            rule.onNodeWithContentDescription(s(if (toList) R.string.show_as_list else R.string.show_as_grid)).performClick()
            val want = if (toList) VideoLayout.LIST else VideoLayout.GRID
            rule.waitUntil(5_000) { runBlocking { settings.current().videoLayout } == want }
            rule.waitUntilAtLeastOneExists(hasText(CLIP), 5_000)
        }
        assertEquals(before, runBlocking { settings.current().videoLayout })

        // Favourite from the video menu, then it is listed under Saved > favourite videos.
        rule.onAllNodesWithContentDescription(s(R.string.more_options_for, CLIP)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.action_favorite)), 5_000)
        rule.onNodeWithText(s(R.string.action_favorite)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.added_to_favorites)), 5_000)

        // Folders view lists the Movies folder.
        rule.onAllNodesWithText(s(R.string.tab_folders)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Movies"), 5_000)
    }

    @Test fun d_homeGreetingJumpBackInAndAboutCredit() {
        rule.onAllNodesWithText(s(R.string.tab_home)).onFirst().performClick()
        // Top bar: the time-based greeting (allow the hour to roll over while the test runs).
        val greetings = DayPart.entries.map { s(it.greeting) }
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.jump_back_in)), 10_000)
        val shown = greetings.filter { rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("exactly one greeting in the top bar", 1, shown.size)
        assertTrue(shown.single() in listOf(s(Greeting.now().greeting), s(Greeting.dayPartFor((java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + 23) % 24).greeting)))
        // The old quick tiles are gone; both Jump back in cards are present.
        assertTrue(rule.onAllNodesWithText("Shuffle all").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("home_continue_music").assertExists()
        rule.onNodeWithTag("home_continue_video").assertExists()
        if (TestSupport.mediaStoreHas(TONE_ONE, video = false)) {
            // b_ played the CI tones, so the music card names one of them.
            rule.waitUntil(10_000) {
                // The card is clickable, so its texts are merged into the card node itself.
                rule.onAllNodes(hasTestTag("home_continue_music") and (hasText(TONE_ONE, substring = true) or hasText(TONE_TWO, substring = true)))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        // Settings > About shows the credit line.
        rule.onAllNodesWithContentDescription(s(R.string.settings)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.theme)), 5_000)
        rule.onNodeWithTag("about_credit").performScrollTo().assertIsDisplayed().assertTextEquals("Made with love by Tawfik Estiak Zihad")
        rule.onNodeWithContentDescription(s(R.string.back)).performClick()
    }

    @Test fun e_settingsToHomeAndNowPlayingBlocksTouches() {
        // A dock tab leaves Settings (regression: Home used to restore Settings).
        rule.onAllNodesWithContentDescription(s(R.string.settings)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(s(R.string.theme)), 5_000)
        rule.onAllNodesWithText(s(R.string.tab_home)).onFirst().performClick()
        rule.waitUntilDoesNotExist(hasText(s(R.string.theme)), 5_000)
        rule.onNodeWithTag("home_list").assertExists()

        assumeTrue("CI media not present", TestSupport.mediaStoreHas(TONE_ONE, video = false))
        rule.onAllNodesWithText(s(R.string.tab_music)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText(TONE_ONE), 20_000)
        rule.onAllNodesWithText(TONE_ONE).onFirst().performClick()
        assertTrue(TestSupport.waitUntil(15_000) { app.container.player.state.value.current?.title == TONE_ONE })

        val openLabel = s(R.string.open_now_playing)
        val miniPlayer = SemanticsMatcher("opens Now Playing") { it.config.getOrNull(SemanticsActions.OnClick)?.label == openLabel }
        rule.waitUntilAtLeastOneExists(miniPlayer, 10_000)
        rule.onNode(miniPlayer).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("now_playing"), 5_000)
        rule.waitForIdle()

        // Taps on empty parts of Now Playing must not reach the song list underneath.
        val overlay = rule.onNodeWithTag("now_playing")
        for (y in listOf(0.30f, 0.45f, 0.55f)) {
            overlay.performTouchInput { click(Offset(width * 0.5f, height * y)) }
            rule.waitForIdle()
        }
        Thread.sleep(500)
        assertEquals(TONE_ONE, app.container.player.state.value.current?.title)
        overlay.assertExists()

        // System back closes Now Playing and returns to the same tab.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntilDoesNotExist(hasTestTag("now_playing"), 5_000)
        rule.onAllNodesWithText(TONE_ONE).onFirst().assertExists()
        rule.onNodeWithContentDescription(s(R.string.close_player)).performClick()
        assertTrue(TestSupport.waitUntil(5_000) { !app.container.player.state.value.isPlaying })
    }

    companion object {
        const val TONE_ONE = "mt_ci_tone_one"
        const val TONE_TWO = "mt_ci_tone_two"
        const val CLIP = "mt_ci_clip"
    }
}
