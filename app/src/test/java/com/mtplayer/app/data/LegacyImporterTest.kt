package com.mtplayer.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mtplayer.app.data.db.LegacyImporter
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.db.MtDatabase
import com.mtplayer.app.data.prefs.SettingsStore
import com.mtplayer.app.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Builds a synthetic MT Player 1.x database (schema v2 of melody_player_local.db) and imports it. */
@RunWith(RobolectricTestRunner::class)
class LegacyImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: MtDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MtDatabase::class.java).allowMainThreadQueries().build()
        val file = context.getDatabasePath(LegacyImporter.LEGACY_DB)
        file.parentFile?.mkdirs(); file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { s ->
            s.execSQL("CREATE TABLE favorite_songs (song_id TEXT PRIMARY KEY NOT NULL, added_at INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE favorite_videos (video_id TEXT PRIMARY KEY NOT NULL, added_at INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE custom_playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE playlist_song_cross_ref (playlist_id TEXT NOT NULL, song_id TEXT NOT NULL, order_index INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(playlist_id, song_id))")
            s.execSQL("CREATE TABLE listening_history (song_id TEXT PRIMARY KEY NOT NULL, played_at INTEGER NOT NULL, play_count INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE video_watching_history (video_id TEXT PRIMARY KEY NOT NULL, watched_at INTEGER NOT NULL, watch_count INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE last_video_playback_position (video_id TEXT PRIMARY KEY NOT NULL, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE hidden_folders (folder_path TEXT PRIMARY KEY NOT NULL, media_type TEXT NOT NULL, hidden_at INTEGER NOT NULL)")
            s.execSQL("CREATE TABLE theme_preferences (id INTEGER PRIMARY KEY NOT NULL, theme_mode TEXT NOT NULL, accent_color_hex TEXT NOT NULL, pure_black_amoled INTEGER NOT NULL)")

            s.execSQL("INSERT INTO favorite_songs VALUES ('101', 1000), ('102', 2000), ('not-a-number', 3000)")
            s.execSQL("INSERT INTO favorite_videos VALUES ('501', 1500)")
            s.execSQL("INSERT INTO custom_playlists VALUES ('favorites_default', 'Favorites', 1), ('workout_mix', 'Workout Mix', 2), ('chill_vibes', 'Chill', 3), ('u1', 'Road trip', 4)")
            // Songs added through the v1 "favorites" playlist path must become favourites (v1 bug).
            s.execSQL("INSERT INTO playlist_song_cross_ref VALUES ('favorites_default', '103', 0, 1), ('chill_vibes', '104', 0, 1), ('u1', '102', 1, 1), ('u1', '101', 0, 1)")
            s.execSQL("INSERT INTO listening_history VALUES ('101', 5000, 7)")
            s.execSQL("INSERT INTO video_watching_history VALUES ('501', 6000, 2)")
            s.execSQL("INSERT INTO last_video_playback_position VALUES ('501', 30000, 60000, 6000)")
            s.execSQL("INSERT INTO hidden_folders VALUES ('/storage/WhatsApp', 'ALL', 1)")
            s.execSQL("INSERT INTO theme_preferences VALUES (1, 'AMOLED', '#E8799B', 0)")
        }
    }

    @After fun tearDown() { db.close() }

    @Test fun importsEverythingOnceAndKeepsLegacyFile() = runTest {
        val file = context.getDatabasePath(LegacyImporter.LEGACY_DB)
        val before = file.readBytes().contentHashCode()
        val settings = SettingsStore(context)
        val report = LegacyImporter(context, db, settings).importIfNeeded()

        assertEquals(3, report.favoriteSongs) // 101, 102, 103 (from favorites_default); invalid id skipped
        assertEquals(1, report.favoriteVideos)
        assertEquals(2, report.playlists) // empty seeded "workout_mix" skipped; non-empty "chill_vibes" kept; u1 kept
        val favSongs = db.favorites().observe(MediaType.SONG).first().map { it.mediaId }.toSet()
        assertEquals(setOf(101L, 102L, 103L), favSongs)

        val playlists = db.playlists().observeSummaries().first()
        assertEquals(setOf("Chill", "Road trip"), playlists.map { it.name }.toSet())
        val road = playlists.first { it.name == "Road trip" }
        assertEquals(listOf(101L, 102L), db.playlists().songIds(road.id)) // order_index respected

        assertEquals(30000L, db.videoProgress().get(501)!!.positionMs)
        assertEquals(7, db.history().observeMostPlayed(MediaType.SONG, 10).first().single().playCount)
        assertEquals(2, db.hiddenFolders().observeAll().first().size) // ALL -> song + video

        val s = settings.current()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertTrue(s.pureBlack)

        // Second run is a no-op and never duplicates data.
        assertTrue(LegacyImporter(context, db, settings).importIfNeeded().skipped)
        assertEquals(3, db.favorites().observe(MediaType.SONG).first().size)
        // The v1 database is opened read-only and left untouched.
        assertTrue(file.exists())
        assertEquals(before, file.readBytes().contentHashCode())
    }
}
