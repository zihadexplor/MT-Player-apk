package com.mtplayer.app.feature.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tab navigation from secondary screens (Settings, Search, details) must always land on the tab. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NavigatorTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var navigator: Navigator
    private val playerOpen = mutableStateOf(false)

    private fun setUp() {
        rule.setContent {
            nav = rememberNavController()
            navigator = Navigator(nav, playerOpen)
            NavHost(nav, startDestination = Routes.HOME) {
                for (r in listOf(Routes.HOME, Routes.MUSIC, Routes.VIDEOS, Routes.SAVED, Routes.SETTINGS, Routes.SEARCH, Routes.EQUALIZER, Routes.HIDDEN)) {
                    composable(r) { Text(r) }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun route() = nav.currentBackStackEntry?.destination?.route
    private fun act(block: () -> Unit) { rule.runOnIdle(block); rule.waitForIdle() }

    @Test fun homeTabFromSettingsOpenedOnHome() {
        setUp()
        act { navigator.settings() }
        assertEquals(Routes.SETTINGS, route())
        act { navigator.top(Routes.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test fun homeTabFromSettingsOpenedOnMusic() {
        setUp()
        act { navigator.top(Routes.MUSIC) }
        act { navigator.settings() }
        act { navigator.top(Routes.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test fun repeatedTabHopsThroughSettingsAlwaysLand() {
        setUp()
        act { navigator.top(Routes.MUSIC) }
        act { navigator.top(Routes.HOME) }
        act { navigator.settings() }
        act { navigator.top(Routes.HOME) }
        assertEquals(Routes.HOME, route())
        act { navigator.top(Routes.MUSIC) }
        act { navigator.settings() }
        act { navigator.top(Routes.MUSIC) }
        assertEquals(Routes.MUSIC, route())
        act { navigator.search() }
        act { navigator.top(Routes.HOME) }
        assertEquals(Routes.HOME, route())
        act { navigator.top(Routes.MUSIC) }
        assertEquals(Routes.MUSIC, route())
    }

    @Test fun nestedGlobalScreensAreDroppedByATab() {
        setUp()
        act { navigator.settings() }
        act { navigator.hiddenFolders() }
        act { navigator.top(Routes.HOME) }
        assertEquals(Routes.HOME, route())
        assertEquals(null, nav.previousBackStackEntry?.destination?.route)
    }

    @Test fun nowPlayingIsAnOverlayThatBackAndNavigationClose() {
        setUp()
        act { navigator.top(Routes.MUSIC) }
        act { navigator.player() }
        assertTrue(navigator.isPlayerOpen)
        assertEquals(Routes.MUSIC, route())
        act { navigator.back() }
        assertFalse(navigator.isPlayerOpen)
        assertEquals("back closes the player, not the screen under it", Routes.MUSIC, route())
        act { navigator.player() }
        act { navigator.equalizer() }
        assertFalse(navigator.isPlayerOpen)
        assertEquals(Routes.EQUALIZER, route())
        act { navigator.player() }
        act { navigator.top(Routes.HOME) }
        assertFalse(navigator.isPlayerOpen)
        assertEquals(Routes.HOME, route())
    }
}
