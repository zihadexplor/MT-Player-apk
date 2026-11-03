package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AudioTrack
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.MTBottomBar
import com.example.ui.components.MTTopBar
import com.example.ui.components.ManageHiddenFoldersDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.SongActionBottomSheet
import com.example.ui.components.SongDetailsDialog
import com.example.ui.components.VideoDetailsDialog
import com.example.ui.navigation.Screen
import com.example.ui.screens.equalizer.EqualizerScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.music.MusicScreen
import com.example.ui.screens.player.NowPlayingScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.video.VideoPlayerModal
import com.example.ui.screens.video.VideoScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.viewmodel.MainViewModel
import com.example.update.ui.UpdateHost

@Composable
fun MTPlayerApp(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val actionTrack by viewModel.actionTrack.collectAsStateWithLifecycle()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsStateWithLifecycle()
    val pendingPlaylistTrackIds by viewModel.pendingPlaylistTrackIds.collectAsStateWithLifecycle()

    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredAudioTracks.collectAsStateWithLifecycle()
    val rawTracks by viewModel.rawAudioTracks.collectAsStateWithLifecycle()
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val artists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val folders by viewModel.filteredFolders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()

    val filteredVideos by viewModel.filteredVideoItems.collectAsStateWithLifecycle()
    val videoFolders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val continueWatchingVideos by viewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val recentlyWatchedVideos by viewModel.recentlyWatchedVideos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val hiddenVideoFolderPaths by viewModel.hiddenVideoFolderPaths.collectAsStateWithLifecycle()
    val videoSort by viewModel.videoSort.collectAsStateWithLifecycle()
    val isVideoGridView by viewModel.isVideoGridView.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedVideoFolder.collectAsStateWithLifecycle()
    val activePlayingVideo by viewModel.activePlayingVideo.collectAsStateWithLifecycle()
    val videoForDetails by viewModel.videoForDetails.collectAsStateWithLifecycle()
    val showManageHiddenFoldersDialog by viewModel.showManageHiddenFoldersDialog.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val trackSort by viewModel.trackSort.collectAsStateWithLifecycle()
    val activeMusicTab by viewModel.activeMusicTab.collectAsStateWithLifecycle()
    val activeVideoTab by viewModel.activeVideoTab.collectAsStateWithLifecycle()

    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTrackIds by viewModel.selectedTrackIds.collectAsStateWithLifecycle()

    var trackForDetailsDialog by remember { mutableStateOf<AudioTrack?>(null) }

    // Permissions launcher for Android media access & notifications
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        viewModel.onPermissionResult(mediaGranted, false)
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    val isSplash = currentRoute == Screen.Splash.route
    val isSettings = currentRoute == Screen.Settings.route
    val isEqualizer = currentRoute == Screen.Equalizer.route
    val isSubpage = isSplash || isSettings || isEqualizer

    val topBarTitle = when (currentRoute) {
        Screen.Home.route -> "MT Player"
        Screen.Music.route -> "Music"
        Screen.Videos.route -> "Videos"
        Screen.Library.route -> "Library"
        Screen.Settings.route -> "Settings"
        Screen.Equalizer.route -> "Equalizer"
        else -> "MT Player"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_root_scaffold"),
            containerColor = DarkBackground,
            topBar = {
                if (!isSubpage) {
                    MTTopBar(
                        title = topBarTitle,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onScanClick = {
                            viewModel.scanMedia()
                        }
                    )
                }
            },
            bottomBar = {
                if (!isSubpage) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Floating Mini Player docked above Bottom Nav
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onClick = viewModel::openNowPlaying
                        )

                        MTBottomBar(
                            currentRoute = currentRoute,
                            onNavigateToRoute = { route ->
                                navController.navigate(route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    // Fast, lightweight transitions. Top-level tabs fade through; sub-pages
                    // (Settings / Equalizer) slide a short distance. Pop uses the same short
                    // timings so back navigation (including predictive back) feels instant.
                    enterTransition = {
                        if (isSubRoute(targetState.destination.route)) {
                            slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it / 6 } +
                                fadeIn(tween(NAV_ANIM_MS))
                        } else fadeIn(tween(NAV_ANIM_MS, delayMillis = NAV_FADE_OUT_MS))
                    },
                    exitTransition = { fadeOut(tween(NAV_FADE_OUT_MS)) },
                    popEnterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
                    popExitTransition = {
                        if (isSubRoute(initialState.destination.route)) {
                            slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it / 6 } +
                                fadeOut(tween(NAV_FADE_OUT_MS))
                        } else fadeOut(tween(NAV_FADE_OUT_MS))
                    }
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            onSplashFinished = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            stats = stats,
                            allTracks = filteredTracks,
                            recentlyPlayed = recentlyPlayed,
                            mostPlayed = mostPlayed,
                            recentVideos = filteredVideos,
                            selectedTrack = playbackState.currentTrack,
                            onTrackClick = { track, queue ->
                                viewModel.playTrack(track, queue)
                            },
                            onTrackOptionsClick = viewModel::openTrackOptions,
                            onVideoClick = viewModel::playVideo,
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onRescanClick = viewModel::scanMedia,
                            onNavigateToMusic = {
                                navController.navigate(Screen.Music.route)
                            },
                            onNavigateToVideos = {
                                navController.navigate(Screen.Videos.route)
                            },
                            onNavigateToLibrary = {
                                navController.navigate(Screen.Library.route)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            },
                            onNavigateToEqualizer = {
                                navController.navigate(Screen.Equalizer.route)
                            }
                        )
                    }

                    composable(Screen.Music.route) {
                        MusicScreen(
                            tracks = filteredTracks,
                            albums = albums,
                            artists = artists,
                            folders = folders,
                            stats = stats,
                            currentPlayingTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            activeTab = activeMusicTab,
                            currentSort = trackSort,
                            isSelectionMode = isSelectionMode,
                            selectedTrackIds = selectedTrackIds,
                            onTabChange = viewModel::onMusicTabChange,
                            onSortChange = viewModel::onSortChange,
                            onTrackClick = { track, queue ->
                                viewModel.playTrack(track, queue)
                            },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onTrackOptionsClick = viewModel::openTrackOptions,
                            onToggleSelectionMode = viewModel::toggleSelectionMode,
                            onToggleTrackSelection = viewModel::toggleTrackSelection,
                            onSelectAll = viewModel::selectAllTracks,
                            onClearSelection = viewModel::clearSelection,
                            onPlaySelected = viewModel::playSelectedTracks,
                            onAddSelectedToQueue = viewModel::addSelectedToQueue,
                            onAddSelectedToFavorites = viewModel::addSelectedToFavorites,
                            onAddSelectedToPlaylist = {
                                viewModel.openAddToPlaylist(selectedTrackIds.toList())
                            },
                            onRescanClick = viewModel::scanMedia,
                            onRequestPermission = {
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                                } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                permissionsLauncher.launch(permissions)
                            }
                        )
                    }

                    composable(Screen.Videos.route) {
                        VideoScreen(
                            videos = filteredVideos,
                            folders = videoFolders,
                            continueWatching = continueWatchingVideos,
                            recentlyWatched = recentlyWatchedVideos,
                            favorites = favoriteVideos,
                            hiddenFolderPaths = hiddenVideoFolderPaths,
                            activeTab = activeVideoTab,
                            currentSort = videoSort,
                            isGridView = isVideoGridView,
                            selectedFolder = selectedVideoFolder,
                            searchQuery = searchQuery,
                            onTabChange = viewModel::onVideoTabChange,
                            onSortChange = viewModel::onVideoSortChange,
                            onToggleLayout = viewModel::toggleVideoLayout,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
                            onVideoClick = viewModel::playVideo,
                            onToggleFavorite = viewModel::toggleVideoFavorite,
                            onOptionsClick = viewModel::showVideoDetails,
                            onFolderClick = viewModel::selectVideoFolder,
                            onBackFromFolder = { viewModel.selectVideoFolder(null) },
                            onHideFolder = viewModel::hideVideoFolder,
                            onManageHiddenFoldersClick = { viewModel.setShowManageHiddenFolders(true) },
                            onRescanClick = viewModel::scanMedia
                        )
                    }

                    composable(Screen.Library.route) {
                        LibraryScreen(
                            stats = stats,
                            playlists = playlists,
                            allTracks = rawTracks,
                            recentlyPlayed = recentlyPlayed,
                            mostPlayed = mostPlayed,
                            currentPlayingTrack = playbackState.currentTrack,
                            onCreatePlaylist = { name ->
                                viewModel.createPlaylist(name)
                            },
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                            onReorderPlaylist = viewModel::reorderPlaylist,
                            onTrackClick = { track, queue ->
                                viewModel.playTrack(track, queue)
                            },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onNavigateToMusic = {
                                navController.navigate(Screen.Music.route)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            },
                            onNavigateToEqualizer = {
                                navController.navigate(Screen.Equalizer.route)
                            },
                            onTrackOptionsClick = viewModel::openTrackOptions
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onRescanClick = viewModel::scanMedia,
                            onNavigateToEqualizer = {
                                navController.navigate(Screen.Equalizer.route)
                            }
                        )
                    }

                    composable(Screen.Equalizer.route) {
                        EqualizerScreen(
                            state = equalizerState,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onToggleEnabled = viewModel::setEqualizerEnabled,
                            onBandLevelChange = viewModel::setEqualizerBandLevel,
                            onPresetSelect = viewModel::applyEqualizerPreset,
                            onBassBoostChange = viewModel::setBassBoostStrength,
                            onVirtualizerChange = viewModel::setVirtualizerStrength,
                            onResetToDefault = viewModel::resetEqualizerToDefault
                        )
                    }
                }
            }
        }

        // Full Now Playing Screen Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                onClose = viewModel::closeNowPlaying,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeek = viewModel::seekTo,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::onToggleFavorite,
                onPlayQueueIndex = viewModel::playQueueIndex,
                onDismissError = viewModel::clearPlaybackError,
                onOpenEqualizer = {
                    viewModel.closeNowPlaying()
                    navController.navigate(Screen.Equalizer.route)
                }
            )
        }

        // Song Options Bottom Sheet
        actionTrack?.let { track ->
            SongActionBottomSheet(
                track = track,
                onDismiss = viewModel::closeTrackOptions,
                onPlayNext = { viewModel.playNext(track) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.openAddToPlaylist(listOf(track.id)) },
                onToggleFavorite = { viewModel.onToggleFavorite(track.id) },
                onShowDetails = { trackForDetailsDialog = track }
            )
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = playlists,
                onSelectPlaylist = { playlistId ->
                    viewModel.addTracksToPlaylist(playlistId, pendingPlaylistTrackIds)
                },
                onCreateNewPlaylist = { name ->
                    viewModel.createPlaylist(name, pendingPlaylistTrackIds)
                    viewModel.closeAddToPlaylist()
                },
                onDismiss = viewModel::closeAddToPlaylist
            )
        }

        // Song Details Dialog
        trackForDetailsDialog?.let { track ->
            SongDetailsDialog(
                track = track,
                onDismiss = { trackForDetailsDialog = null }
            )
        }

        // Video Details Dialog
        videoForDetails?.let { video ->
            VideoDetailsDialog(
                video = video,
                onDismiss = viewModel::dismissVideoDetails,
                onPlay = { viewModel.playVideo(video) },
                onToggleFavorite = { viewModel.toggleVideoFavorite(video.id) },
                onHideFolder = { viewModel.hideVideoFolder(video.folderPath) }
            )
        }

        // Manage Hidden Folders Dialog
        if (showManageHiddenFoldersDialog) {
            ManageHiddenFoldersDialog(
                hiddenFolderPaths = hiddenVideoFolderPaths,
                folders = videoFolders,
                onDismiss = { viewModel.setShowManageHiddenFolders(false) },
                onUnhideFolder = viewModel::unhideVideoFolder,
                onUnhideAll = viewModel::unhideAllVideoFolders
            )
        }

        // Full-screen Video Player Modal
        activePlayingVideo?.let { video ->
            VideoPlayerModal(
                video = video,
                onClose = viewModel::closeVideoPlayer,
                onProgressUpdate = viewModel::updateVideoPlaybackProgress
            )
        }

        // In-app updater popup (GitHub Releases). Hidden during splash and fullscreen video.
        UpdateHost(enabled = !isSplash && activePlayingVideo == null)
    }
}

private const val NAV_ANIM_MS = 180
private const val NAV_FADE_OUT_MS = 90

private fun isSubRoute(route: String?): Boolean =
    route == Screen.Settings.route || route == Screen.Equalizer.route
