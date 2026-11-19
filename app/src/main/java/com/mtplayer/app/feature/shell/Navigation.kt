package com.mtplayer.app.feature.shell

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.mtplayer.app.AppContainer

object Routes {
    const val HOME = "home"
    const val MUSIC = "music"
    const val VIDEOS = "videos"
    const val SAVED = "saved"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val HIDDEN = "hidden"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val GENRE = "genre/{name}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST = "playlist/{id}"
    const val VIDEO_FOLDER = "videoFolder/{path}"

    val topLevel = listOf(HOME, MUSIC, VIDEOS, SAVED)

    /** App-wide screens opened from the header; they never belong to a tab's saved state. */
    val global = setOf(SEARCH, SETTINGS, EQUALIZER, HIDDEN)
}

/**
 * Thin wrapper so screens navigate by intent, not by route strings.
 *
 * Now Playing is not a route: it is a full-screen overlay above the shell (see [AppRoot]) whose
 * visibility lives in [playerOpen]. Any other navigation closes it first.
 */
class Navigator(private val nav: NavHostController, private val playerOpen: MutableState<Boolean>) {
    val isPlayerOpen: Boolean get() = playerOpen.value

    fun back() {
        if (playerOpen.value) closePlayer() else nav.popBackStack()
    }

    fun player() { playerOpen.value = true }
    fun closePlayer() { playerOpen.value = false }

    /**
     * Switches to a top-level tab. Global screens (Settings, Search, ...) on top are dropped first:
     * saving them as part of a tab's state made "Home" from Settings restore Settings again.
     */
    fun top(route: String) {
        closePlayer()
        while (nav.currentBackStackEntry?.destination?.route in Routes.global && nav.previousBackStackEntry != null) {
            nav.popBackStack()
        }
        if (nav.currentBackStackEntry?.destination?.route == route) return
        nav.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            // Home is the start destination: its "saved state" is the stack that was just popped.
            restoreState = route != Routes.HOME
        }
    }

    private fun go(route: String, singleTop: Boolean = false) {
        closePlayer()
        nav.navigate(route) { launchSingleTop = singleTop }
    }

    fun search() = go(Routes.SEARCH, singleTop = true)
    fun settings() = go(Routes.SETTINGS, singleTop = true)
    fun equalizer() = go(Routes.EQUALIZER, singleTop = true)
    fun hiddenFolders() = go(Routes.HIDDEN, singleTop = true)
    fun album(id: Long) = go("album/$id")
    fun artist(name: String) = go("artist/${Uri.encode(name)}")
    fun genre(name: String) = go("genre/${Uri.encode(name)}")
    fun folder(path: String) = go("folder/${Uri.encode(path)}")
    fun playlist(id: Long) = go("playlist/$id")
    fun videoFolder(path: String) = go("videoFolder/${Uri.encode(path)}")
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> { error("No AppViewModel") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
