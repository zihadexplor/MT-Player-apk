package com.mtplayer.app.feature.shell

import android.net.Uri
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
    const val PLAYER = "player"
    const val EQUALIZER = "equalizer"
    const val HIDDEN = "hidden"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val GENRE = "genre/{name}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST = "playlist/{id}"
    const val VIDEO_FOLDER = "videoFolder/{path}"

    val topLevel = listOf(HOME, MUSIC, VIDEOS, SAVED)
    val fullScreen = setOf(PLAYER)
}

/** Thin wrapper so screens navigate by intent, not by route strings. */
class Navigator(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun top(route: String) = nav.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun player() = nav.navigate(Routes.PLAYER) { launchSingleTop = true }
    fun search() = nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    fun settings() = nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    fun equalizer() = nav.navigate(Routes.EQUALIZER) { launchSingleTop = true }
    fun hiddenFolders() = nav.navigate(Routes.HIDDEN) { launchSingleTop = true }
    fun album(id: Long) = nav.navigate("album/$id")
    fun artist(name: String) = nav.navigate("artist/${Uri.encode(name)}")
    fun genre(name: String) = nav.navigate("genre/${Uri.encode(name)}")
    fun folder(path: String) = nav.navigate("folder/${Uri.encode(path)}")
    fun playlist(id: Long) = nav.navigate("playlist/$id")
    fun videoFolder(path: String) = nav.navigate("videoFolder/${Uri.encode(path)}")
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> { error("No AppViewModel") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
