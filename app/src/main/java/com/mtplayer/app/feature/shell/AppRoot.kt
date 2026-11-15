package com.mtplayer.app.feature.shell

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtplayer.app.AppContainer
import com.mtplayer.app.MTApp
import com.mtplayer.app.R
import com.mtplayer.app.core.Permissions
import com.mtplayer.app.data.media.LibraryState
import com.mtplayer.app.designsystem.components.LoadingState
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.Messenger
import com.mtplayer.app.designsystem.components.MtSnackbarHost
import com.mtplayer.app.feature.detail.AlbumScreen
import com.mtplayer.app.feature.detail.ArtistScreen
import com.mtplayer.app.feature.detail.FolderScreen
import com.mtplayer.app.feature.detail.GenreScreen
import com.mtplayer.app.feature.detail.PlaylistScreen
import com.mtplayer.app.feature.detail.VideoFolderScreen
import com.mtplayer.app.feature.equalizer.EqualizerScreen
import com.mtplayer.app.feature.home.HomeScreen
import com.mtplayer.app.feature.music.MusicScreen
import com.mtplayer.app.feature.nowplaying.NowPlayingScreen
import com.mtplayer.app.feature.saved.SavedScreen
import com.mtplayer.app.feature.search.SearchScreen
import com.mtplayer.app.feature.settings.HiddenFoldersScreen
import com.mtplayer.app.feature.settings.SettingsScreen
import com.mtplayer.app.feature.settings.UpdatePrompt
import com.mtplayer.app.feature.videos.VideosScreen

private data class TopDest(val route: String, val label: Int, val icon: ImageVector)

private val destinations = listOf(
    TopDest(Routes.HOME, R.string.tab_home, Icons.Rounded.Home),
    TopDest(Routes.MUSIC, R.string.tab_music, Icons.Rounded.LibraryMusic),
    TopDest(Routes.VIDEOS, R.string.tab_videos, Icons.Rounded.Movie),
    TopDest(Routes.SAVED, R.string.tab_saved, Icons.Rounded.Bookmarks),
)

@Composable
fun AppRoot(container: AppContainer, vm: AppViewModel, openPlayerRequest: Int) {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember(snackbar) { Messenger(snackbar, scope) }
    val actions = remember { MediaActionsState() }
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current

    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0) navigator.player()
    }

    val app = context.applicationContext as MTApp
    val importMessage by app.importMessage.collectAsStateWithLifecycle()
    LaunchedEffect(importMessage) {
        importMessage?.let { messenger.show(it, long = true); app.consumeImportMessage() }
    }

    CompositionLocalProvider(
        LocalContainer provides container,
        LocalAppViewModel provides vm,
        LocalNavigator provides navigator,
        LocalMessenger provides messenger,
        LocalMediaActions provides actions,
    ) {
        val fullScreen = route in Routes.fullScreen
        val showBar = !fullScreen
        Row(Modifier.fillMaxSize()) {
            if (wide && showBar) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    destinations.forEach { d ->
                        val selected = backStack?.destination?.route == d.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigator.top(d.route) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(stringResource(d.label)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { MtSnackbarHost(snackbar) },
                bottomBar = {
                    if (showBar) {
                        Column {
                            AnimatedVisibility(
                                visible = playerState.hasMedia,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut(),
                            ) {
                                MiniPlayer(playerState, container.player, onOpen = navigator::player,
                                    modifier = if (wide) Modifier.navigationBarsPadding() else Modifier)
                            }
                            if (!wide) {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    destinations.forEach { d ->
                                        val selected = backStack?.destination?.route == d.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigator.top(d.route) },
                                            icon = { Icon(d.icon, contentDescription = null) },
                                            label = { Text(stringResource(d.label)) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    LibraryGate(library, onRetry = vm::refreshLibrary) {
                        AppNavHost(navController)
                    }
                }
            }
        }
        MediaActionsHost(actions)
        UpdatePrompt()
    }
}

@Composable
private fun AppNavHost(navController: androidx.navigation.NavHostController) {
    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.MUSIC) { MusicScreen() }
        composable(Routes.VIDEOS) { VideosScreen() }
        composable(Routes.SAVED) { SavedScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.HIDDEN) { HiddenFoldersScreen() }
        composable(Routes.EQUALIZER) { EqualizerScreen() }
        composable(
            Routes.PLAYER,
            enterTransition = { slideInVertically { it } },
            exitTransition = { fadeOut() },
            popExitTransition = { slideOutVertically { it } },
        ) { NowPlayingScreen() }
        composable(Routes.ALBUM, listOf(navArgument("id") { type = NavType.LongType })) {
            AlbumScreen(it.arguments?.getLong("id") ?: -1)
        }
        composable(Routes.ARTIST) { ArtistScreen(Uri.decode(it.arguments?.getString("name").orEmpty())) }
        composable(Routes.GENRE) { GenreScreen(Uri.decode(it.arguments?.getString("name").orEmpty())) }
        composable(Routes.FOLDER) { FolderScreen(Uri.decode(it.arguments?.getString("path").orEmpty())) }
        composable(Routes.PLAYLIST, listOf(navArgument("id") { type = NavType.LongType })) {
            PlaylistScreen(it.arguments?.getLong("id") ?: -1)
        }
        composable(Routes.VIDEO_FOLDER) { VideoFolderScreen(Uri.decode(it.arguments?.getString("path").orEmpty())) }
    }
}

/** Shows permission / loading / error states in place of the content until the library is usable. */
@Composable
private fun LibraryGate(state: LibraryState, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when (state) {
        is LibraryState.Ready -> content()
        LibraryState.Loading -> LoadingState(stringResource(R.string.loading_library))
        LibraryState.PermissionRequired -> PermissionRequest(onGranted = onRetry)
        is LibraryState.Error -> MessageState(
            icon = Icons.Rounded.Error,
            title = stringResource(R.string.library_error_title),
            body = stringResource(R.string.library_error_body),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun PermissionRequest(onGranted: () -> Unit) {
    val context = LocalContext.current
    val vm = LocalAppViewModel.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val permanentlyDenied = settings.permissionAskedOnce && activity != null &&
        Permissions.media.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } &&
        !Permissions.hasAudio(context) && !Permissions.hasVideo(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.updateSettings { it.copy(permissionAskedOnce = true) }
        onGranted()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onGranted() }
    MessageState(
        icon = Icons.Rounded.FolderOff,
        title = stringResource(R.string.permission_title),
        body = stringResource(if (permanentlyDenied) R.string.permission_body_denied else R.string.permission_body),
        actionLabel = stringResource(if (permanentlyDenied) R.string.open_settings else R.string.allow_access),
        onAction = {
            if (permanentlyDenied) {
                settingsLauncher.launch(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            } else {
                // Media access gates the library; notifications are optional (media controls are
                // exempt on Android 13+, but asking keeps them visible on every OEM build).
                launcher.launch(Permissions.media + listOfNotNull(Permissions.notifications))
            }
        },
    )
}
