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

    @Test
    fun testPlayerSettingsPersistence() = runBlocking {
        val custom = com.example.data.database.PlayerSettingsEntity(
            id = 1,
            gaplessPlayback = false,
            autoPauseOnHeadset = false,
            crossfadeSeconds = 5,
            hardwareAcceleration = false,
            resumeVideo = false,
            defaultVideoSpeed = 1.5f,
            doubleTapSeekSeconds = 15,
            gestureBrightness = false,
            gestureVolume = true,
            gestureScrub = false,
            minAudioDurationSeconds = 45,
            autoScanOnLaunch = false,
            dynamicArtworkColor = false,
            videoBackgroundAudio = true
        )
        dao.savePlayerSettings(custom)

        val loaded = dao.getPlayerSettings().first()
        val loadedDirect = dao.getPlayerSettingsDirect()
        assertNotNull(loaded)
        assertNotNull(loadedDirect)
        assertEquals(custom, loaded)
        assertEquals(custom, loadedDirect)
    }

    @Test
    fun testMigration1To2PreservesDataAndSeedsPlayerSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("migration_test_v1_v2.db")
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        // 1. Build a v1 SQLite database matching exact v1 Room schemas
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(v1Db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_songs` (`song_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`song_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_songs_song_id` ON `favorite_songs` (`song_id`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_videos` (`video_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`video_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_videos_video_id` ON `favorite_videos` (`video_id`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `custom_playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_playlists_name` ON `custom_playlists` (`name`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref` (`playlist_id` TEXT NOT NULL, `song_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `song_id`))")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlist_id` ON `playlist_song_cross_ref` (`playlist_id`)")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_song_id` ON `playlist_song_cross_ref` (`song_id`)")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlist_id_order_index` ON `playlist_song_cross_ref` (`playlist_id`, `order_index`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `listening_history` (`song_id` TEXT NOT NULL, `played_at` INTEGER NOT NULL, `play_count` INTEGER NOT NULL, PRIMARY KEY(`song_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_history_song_id` ON `listening_history` (`song_id`)")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_played_at` ON `listening_history` (`played_at`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `video_watching_history` (`video_id` TEXT NOT NULL, `watched_at` INTEGER NOT NULL, `watch_count` INTEGER NOT NULL, PRIMARY KEY(`video_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_video_watching_history_video_id` ON `video_watching_history` (`video_id`)")
                    v1Db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_watching_history_watched_at` ON `video_watching_history` (`watched_at`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `last_music_playback_position` (`song_id` TEXT NOT NULL, `position_ms` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`song_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_last_music_playback_position_song_id` ON `last_music_playback_position` (`song_id`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `last_video_playback_position` (`video_id` TEXT NOT NULL, `position_ms` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`video_id`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_last_video_playback_position_video_id` ON `last_video_playback_position` (`video_id`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `equalizer_preferences` (`id` INTEGER NOT NULL, `is_enabled` INTEGER NOT NULL, `preset_name` TEXT NOT NULL, `bass_boost` INTEGER NOT NULL, `virtualizer` INTEGER NOT NULL, `band_levels_csv` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `theme_preferences` (`id` INTEGER NOT NULL, `theme_mode` TEXT NOT NULL, `accent_color_hex` TEXT NOT NULL, `pure_black_amoled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `hidden_folders` (`folder_path` TEXT NOT NULL, `media_type` TEXT NOT NULL, `hidden_at` INTEGER NOT NULL, PRIMARY KEY(`folder_path`))")
                    v1Db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_folders_folder_path` ON `hidden_folders` (`folder_path`)")
                    v1Db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_timer_preferences` (`id` INTEGER NOT NULL, `default_duration_minutes` INTEGER NOT NULL, `finish_last_song` INTEGER NOT NULL, `fade_out_enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")

                    v1Db.execSQL("INSERT INTO `favorite_songs` (`song_id`, `added_at`) VALUES ('legacy_song_1', 123456)")
                    v1Db.execSQL("INSERT INTO `theme_preferences` (`id`, `theme_mode`, `accent_color_hex`, `pure_black_amoled`) VALUES (1, 'DARK', '#1DB954', 1)")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        factory.create(config).use { helper ->
            helper.writableDatabase.close()
        }

        // 2. Open with Room v2 using ONLY MIGRATION_1_2 (no destructive fallback)
        val migratedDb = Room.databaseBuilder(context, MTPlayerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MTPlayerDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val migratedDao = migratedDb.mediaDao()
            val favs = migratedDao.getAllFavoriteSongIds().first()
            assertTrue(favs.contains("legacy_song_1"))

            val theme = migratedDao.getThemePreference().first()
            assertNotNull(theme)
            assertEquals("DARK", theme?.themeMode)
            assertTrue(theme!!.pureBlackAmoled)

            val settings = migratedDao.getPlayerSettingsDirect()
            assertNotNull(settings)
            assertEquals(1.0f, settings!!.defaultVideoSpeed)
            assertEquals(10, settings.doubleTapSeekSeconds)
            assertEquals(30, settings.minAudioDurationSeconds)
            assertTrue(settings.gaplessPlayback)
            assertFalse(settings.videoBackgroundAudio)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }
}

