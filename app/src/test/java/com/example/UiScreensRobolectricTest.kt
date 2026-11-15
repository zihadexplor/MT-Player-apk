package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioTrack
import com.example.data.model.Genre
import com.example.data.model.MusicFolder
import com.example.data.model.PlaybackState
import com.example.data.model.Playlist
import com.example.data.model.StorageStats
import com.example.data.model.TrackSort
import com.example.data.model.VideoItem
import com.example.player.equalizer.EqualizerBand
import com.example.player.equalizer.EqualizerState
import com.example.ui.screens.equalizer.EqualizerScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.music.MusicScreen
import com.example.ui.screens.player.NowPlayingScreen
import com.example.ui.theme.MTPlayerTheme
import com.example.util.LyricLine
import com.example.util.LyricsResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class UiScreensRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTrack1 = AudioTrack(
        id = "track_1",
        title = "Midnight Echoes",
        artist = "Luna Synth",
        album = "Nocturne",
        durationMs = 215_000L,
        uri = "content://media/external/audio/media/1",
        genre = "Synthwave",
        folderName = "Music",
        folderPath = "/storage/emulated/0/Music",
        isFavorite = true
    )

    private val sampleTrack2 = AudioTrack(
        id = "track_2",
        title = "Solar Drift",
        artist = "Nova Pulse",
        album = "Horizon",
        durationMs = 184_000L,
        uri = "content://media/external/audio/media/2",
        genre = "Ambient",
        folderName = "Downloads",
        folderPath = "/storage/emulated/0/Downloads",
        isFavorite = false
    )

    private val sampleTracks = listOf(sampleTrack1, sampleTrack2)
    private val sampleStats = StorageStats(
        audioCount = 2,
        videoCount = 1,
        totalAudioDurationMs = 399_000L,
        totalStorageUsedBytes = 24_000_000L,
        hasStoragePermission = true
    )
    private val sampleVideos = listOf(
        VideoItem(
            id = "vid_1",
            title = "City Timelapse.mp4",
            durationMs = 95_000L,
            uri = "content://media/external/video/media/10",
            resolution = "1080p",
            width = 1920,
            height = 1080
        )
    )

    @Test
    fun `HomeScreen renders cleanly in Light theme`() {
        composeRule.setContent {
            MTPlayerTheme(themeMode = "LIGHT") {
                HomeScreen(
                    stats = sampleStats,
                    allTracks = sampleTracks,
                    recentlyPlayed = sampleTracks,
                    mostPlayed = sampleTracks,
                    recentVideos = sampleVideos,
                    selectedTrack = sampleTrack1,
                    onTrackClick = { _, _ -> },
                    onTrackOptionsClick = {},
                    onVideoClick = {},
                    onToggleFavorite = {},
                    onRescanClick = {},
                    onNavigateToMusic = {},
                    onNavigateToVideos = {},
                    onNavigateToLibrary = {},
                    onNavigateToSettings = {},
                    onNavigateToEqualizer = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen_root").assertIsDisplayed()
        composeRule.onAllNodesWithText("Midnight Echoes").onFirst().assertIsDisplayed()
    }

    @Test
    fun `MusicScreen renders all 6 tabs including Genres in Dark and Light themes`() {
        val albums = listOf(Album(1L, "Nocturne", "Luna Synth", 1, null, listOf(sampleTrack1)))
        val artists = listOf(Artist("Luna Synth", 1, 1, listOf(sampleTrack1)))
        val genres = listOf(Genre("Synthwave", 1, listOf(sampleTrack1)))
        val folders = listOf(MusicFolder("Music", "/storage/emulated/0/Music", 1, listOf(sampleTrack1)))

        composeRule.setContent {
            MTPlayerTheme(themeMode = "LIGHT") {
                MusicScreen(
                    tracks = sampleTracks,
                    albums = albums,
                    artists = artists,
                    genres = genres,
                    folders = folders,
                    stats = sampleStats,
                    currentPlayingTrack = sampleTrack1,
                    isPlaying = true,
                    activeTab = 3, // Genres tab
                    currentSort = TrackSort.TITLE_ASC,
                    isSelectionMode = false,
                    selectedTrackIds = emptySet(),
                    onTabChange = {},
                    onSortChange = {},
                    onTrackClick = { _, _ -> },
                    onToggleFavorite = {},
                    onTrackOptionsClick = {},
                    onToggleSelectionMode = {},
                    onToggleTrackSelection = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onPlaySelected = {},
                    onAddSelectedToQueue = {},
                    onAddSelectedToFavorites = {},
                    onAddSelectedToPlaylist = {},
                    onRescanClick = {},
                    onRequestPermission = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("music_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("music_tab_3", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("music_tab_5", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Synthwave").assertIsDisplayed()
    }

    @Test
    fun `NowPlayingScreen switches between Player, Lyrics, and Queue views`() {
        val state = PlaybackState(
            currentTrack = sampleTrack1,
            isPlaying = true,
            currentPositionMs = 12_000L,
            durationMs = 215_000L,
            queue = sampleTracks,
            currentQueueIndex = 0
        )
        val lyrics = LyricsResult(
            syncedLines = listOf(
                LyricLine(0L, "Intro verse line"),
                LyricLine(10_000L, "Active chorus line")
            ),
            sourceLabel = "Sidecar .lrc"
        )

        composeRule.setContent {
            MTPlayerTheme(themeMode = "AMOLED") {
                NowPlayingScreen(
                    playbackState = state,
                    onClose = {},
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onPlayQueueIndex = {},
                    onDismissError = {},
                    lyrics = lyrics,
                    sleepTimerRemainingSeconds = 600
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_screen_root").assertIsDisplayed()
        composeRule.onNodeWithText("Midnight Echoes").assertIsDisplayed()

        // Switch to Lyrics mode
        composeRule.onNodeWithTag("now_playing_lyrics_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Active chorus line").assertIsDisplayed()

        // Switch to Queue mode
        composeRule.onNodeWithTag("now_playing_queue_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Solar Drift").assertIsDisplayed()
    }

    @Test
    fun `LibraryScreen and EqualizerScreen render cleanly without contrast or layout errors`() {
        val eqState = EqualizerState(
            isEnabled = true,
            currentPreset = "Rock",
            bands = listOf(
                EqualizerBand(0, 60, 400),
                EqualizerBand(1, 230, 200),
                EqualizerBand(2, 910, -100)
            ),
            bassBoostStrength = 350,
            virtualizerStrength = 200
        )

        composeRule.setContent {
            MTPlayerTheme(themeMode = "DARK") {
                EqualizerScreen(
                    state = eqState,
                    onBackClick = {},
                    onToggleEnabled = {},
                    onBandLevelChange = { _, _ -> },
                    onPresetSelect = {},
                    onBassBoostChange = {},
                    onVirtualizerChange = {},
                    onResetToDefault = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("equalizer_screen_root").assertIsDisplayed()
        composeRule.onNodeWithText("Equalizer & Sound FX").assertIsDisplayed()
    }

    @Test
    fun `LibraryScreen renders playlists and quick collections in Light theme`() {
        composeRule.setContent {
            MTPlayerTheme(themeMode = "LIGHT") {
                LibraryScreen(
                    stats = sampleStats,
                    playlists = listOf(Playlist("workout_mix", "Workout Energy", listOf("track_1"))),
                    allTracks = sampleTracks,
                    recentlyPlayed = sampleTracks,
                    mostPlayed = sampleTracks,
                    currentPlayingTrack = sampleTrack1,
                    onCreatePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onRemoveTrackFromPlaylist = { _, _ -> },
                    onReorderPlaylist = { _, _, _ -> },
                    onTrackClick = { _, _ -> },
                    onToggleFavorite = {},
                    onNavigateToMusic = {},
                    onNavigateToFavorites = {},
                    onNavigateToSettings = {},
                    onTrackOptionsClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("playlist_item_workout_mix", useUnmergedTree = true).assertIsDisplayed()
    }
}
