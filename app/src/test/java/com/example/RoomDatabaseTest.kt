package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.EqualizerPreferenceEntity
import com.example.data.database.FavoriteSongEntity
import com.example.data.database.FavoriteVideoEntity
import com.example.data.database.HiddenFolderEntity
import com.example.data.database.LastMusicPlaybackEntity
import com.example.data.database.LastVideoPlaybackEntity
import com.example.data.database.MTPlayerDatabase
import com.example.data.database.MediaDao
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistSongCrossRef
import com.example.data.database.SleepTimerPreferenceEntity
import com.example.data.database.ThemePreferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var db: MTPlayerDatabase
    private lateinit var dao: MediaDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MTPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.mediaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFavoriteSongsPersistence() = runBlocking {
        dao.insertFavoriteSong(FavoriteSongEntity("song_1"))
        dao.insertFavoriteSong(FavoriteSongEntity("song_2"))

        val favorites = dao.getAllFavoriteSongIds().first()
        assertEquals(2, favorites.size)
        assertTrue(favorites.contains("song_1"))
        assertTrue(favorites.contains("song_2"))

        dao.deleteFavoriteSong("song_1")
        val updated = dao.getAllFavoriteSongIds().first()
        assertEquals(1, updated.size)
        assertFalse(updated.contains("song_1"))
    }

    @Test
    fun testFavoriteVideosPersistence() = runBlocking {
        dao.insertFavoriteVideo(FavoriteVideoEntity("video_100"))
        val isFav = dao.isVideoFavorite("video_100").first()
        assertTrue(isFav)

        dao.deleteFavoriteVideo("video_100")
        val isFavAfterDelete = dao.isVideoFavorite("video_100").first()
        assertFalse(isFavAfterDelete)
    }

    @Test
    fun testPlaylistsAndSongRelationships() = runBlocking {
        dao.insertPlaylist(PlaylistEntity(id = "pl_workout", name = "Gym Workout"))
        dao.insertPlaylistSongRef(PlaylistSongCrossRef("pl_workout", "song_1", 0))
        dao.insertPlaylistSongRef(PlaylistSongCrossRef("pl_workout", "song_2", 1))

        val playlists = dao.getAllPlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Gym Workout", playlists[0].name)

        val songs = dao.getSongsForPlaylist("pl_workout").first()
        assertEquals(2, songs.size)
        assertEquals("song_1", songs[0].songId)
        assertEquals("song_2", songs[1].songId)

        // Reorder
        dao.reorderPlaylistSongs("pl_workout", listOf("song_2", "song_1"))
        val reordered = dao.getSongsForPlaylist("pl_workout").first()
        assertEquals("song_2", reordered[0].songId)
        assertEquals("song_1", reordered[1].songId)
    }

    @Test
    fun testListeningAndVideoHistory() = runBlocking {
        dao.recordSongPlayed("song_jazz")
        dao.recordSongPlayed("song_jazz")

        val history = dao.getAllListeningHistory().first()
        assertEquals(1, history.size)
        assertEquals(2, history[0].playCount)

        dao.recordVideoWatched("vid_trailer")
        val vidHistory = dao.getAllVideoWatchingHistory().first()
        assertEquals(1, vidHistory.size)
        assertEquals(1, vidHistory[0].watchCount)
    }

    @Test
    fun testPlaybackPositions() = runBlocking {
        dao.saveMusicPlaybackPosition(LastMusicPlaybackEntity("song_epic", 45000L, 180000L))
        val musicPos = dao.getLastMusicPlaybackPosition().first()
        assertNotNull(musicPos)
        assertEquals(45000L, musicPos?.positionMs)

        dao.saveVideoPlaybackPosition(LastVideoPlaybackEntity("vid_movie", 120000L, 600000L))
        val vidPos = dao.getVideoPlaybackPosition("vid_movie").first()
        assertNotNull(vidPos)
        assertEquals(120000L, vidPos?.positionMs)
    }

    @Test
    fun testPreferencesPersistence() = runBlocking {
        dao.saveEqualizerPreference(
            EqualizerPreferenceEntity(
                id = 1,
                isEnabled = true,
                presetName = "Rock",
                bassBoost = 600,
                virtualizer = 400,
                bandLevelsCsv = "400,200,-100,300,500"
            )
        )
        val eq = dao.getEqualizerPreference().first()
        assertNotNull(eq)
        assertEquals("Rock", eq?.presetName)
        assertEquals(600, eq?.bassBoost)

        dao.saveThemePreference(
            ThemePreferenceEntity(
                id = 1,
                themeMode = "DARK",
                accentColorHex = "#1DB954",
                pureBlackAmoled = true
            )
        )
        val theme = dao.getThemePreference().first()
        assertNotNull(theme)
        assertTrue(theme!!.pureBlackAmoled)

        dao.saveSleepTimerPreference(
            SleepTimerPreferenceEntity(
                id = 1,
                defaultDurationMinutes = 45,
                finishLastSong = true,
                fadeOutEnabled = true
            )
        )
        val sleepTimer = dao.getSleepTimerPreference().first()
        assertNotNull(sleepTimer)
        assertEquals(45, sleepTimer?.defaultDurationMinutes)
        assertTrue(sleepTimer!!.finishLastSong)

        dao.insertHiddenFolder(HiddenFolderEntity("/storage/emulated/0/WhatsApp Video", "VIDEO"))
        val hidden = dao.getAllHiddenFolders().first()
        assertEquals(1, hidden.size)
        assertEquals("/storage/emulated/0/WhatsApp Video", hidden[0].folderPath)
    }

    @Test
    fun testClearListeningAndWatchingHistory() = runBlocking {
        dao.recordSongPlayed("s1")
        dao.recordSongPlayed("s2")
        val historyBefore = dao.getAllListeningHistory().first()
        assertEquals(2, historyBefore.size)

        dao.clearListeningHistory()
        val historyAfter = dao.getAllListeningHistory().first()
        assertTrue(historyAfter.isEmpty())

        dao.recordVideoWatched("v1")
        val videoHistoryBefore = dao.getAllVideoWatchingHistory().first()
        assertEquals(1, videoHistoryBefore.size)

        dao.clearVideoWatchingHistory()
        val videoHistoryAfter = dao.getAllVideoWatchingHistory().first()
        assertTrue(videoHistoryAfter.isEmpty())
    }

    @Test
    fun testThemePersonalizationPersistence() = runBlocking {
        dao.saveThemePreference(
            ThemePreferenceEntity(
                id = 1,
                themeMode = "AMOLED",
                accentColorHex = "#00E5FF",
                pureBlackAmoled = true
            )
        )
        val amoledTheme = dao.getThemePreference().first()
        assertNotNull(amoledTheme)
        assertEquals("AMOLED", amoledTheme?.themeMode)
        assertEquals("#00E5FF", amoledTheme?.accentColorHex)
        assertTrue(amoledTheme!!.pureBlackAmoled)

        dao.saveThemePreference(
            ThemePreferenceEntity(
                id = 1,
                themeMode = "LIGHT",
                accentColorHex = "#FFB300",
                pureBlackAmoled = false
            )
        )
        val lightTheme = dao.getThemePreference().first()
        assertNotNull(lightTheme)
        assertEquals("LIGHT", lightTheme?.themeMode)
        assertEquals("#FFB300", lightTheme?.accentColorHex)
        assertFalse(lightTheme!!.pureBlackAmoled)
    }
}
