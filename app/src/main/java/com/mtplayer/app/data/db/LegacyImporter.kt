package com.mtplayer.app.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.mtplayer.app.data.prefs.Settings
import com.mtplayer.app.data.prefs.SettingsStore
import com.mtplayer.app.designsystem.theme.ThemeMode
import java.io.File

/**
 * One-time, read-only import of MT Player 1.x user data (ADR-006).
 *
 * * Opens `melody_player_local.db` with OPEN_READONLY and never modifies or deletes it, so a
 *   failed import can be retried and a downgrade still finds the original data.
 * * Writes everything in one Room transaction together with the completion marker, so the
 *   import is either fully applied or not at all.
 * * Skips the fake playlists 1.x created on first launch unless the user put songs in them.
 */
class LegacyImporter(
    private val context: Context,
    private val db: MtDatabase,
    private val settings: SettingsStore,
) {
    data class Report(
        val favoriteSongs: Int = 0,
        val favoriteVideos: Int = 0,
        val playlists: Int = 0,
        val playlistItems: Int = 0,
        val history: Int = 0,
        val videoProgress: Int = 0,
        val hiddenFolders: Int = 0,
        val skipped: Boolean = false,
    ) {
        val total get() = favoriteSongs + favoriteVideos + playlists + history + videoProgress + hiddenFolders
    }

    suspend fun importIfNeeded(): Report {
        if (db.meta().get(MARKER) != null) return Report(skipped = true)
        val file = context.getDatabasePath(LEGACY_DB)
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = if (file.exists()) readLegacy(file) else LegacyData()
        val prefFavVideos = prefs.getStringSet("favorite_video_ids", null).orEmpty().mapNotNull { it.toLongOrNull() }
        val prefHidden = prefs.getStringSet("hidden_video_folders", null).orEmpty()

        val now = System.currentTimeMillis()
        var report = Report()
        db.withTransaction {
            val favSongs = (legacy.favoriteSongs + legacy.defaultPlaylistSongs.map { it to now })
                .distinctBy { it.first }
                .map { FavoriteEntity(MediaType.SONG, it.first, it.second) }
            val favVideos = (legacy.favoriteVideos + prefFavVideos.map { it to now })
                .distinctBy { it.first }
                .map { FavoriteEntity(MediaType.VIDEO, it.first, it.second) }
            db.favorites().insertAll(favSongs)
            db.favorites().insertAll(favVideos)

            var items = 0
            for (pl in legacy.playlists) {
                val newId = db.playlists().insert(PlaylistEntity(name = pl.name, createdAt = pl.createdAt, updatedAt = pl.createdAt))
                items += db.playlists().append(newId, pl.songIds, pl.createdAt)
            }
            db.history().insertAll(legacy.history)
            db.videoProgress().insertAll(legacy.videoProgress)
            val hidden = (legacy.hidden + prefHidden.map { HiddenFolderEntity(it, MediaType.VIDEO) }).distinct()
            db.hiddenFolders().insertAll(hidden)

            report = Report(
                favoriteSongs = favSongs.size,
                favoriteVideos = favVideos.size,
                playlists = legacy.playlists.size,
                playlistItems = items,
                history = legacy.history.size,
                videoProgress = legacy.videoProgress.size,
                hiddenFolders = hidden.size,
            )
            db.meta().put(MetaEntity(MARKER, "v1:${report.total}:$now"))
        }
        legacy.settings?.let { imported -> settings.update { imported(it) } }
        return report
    }

    private class LegacyPlaylist(val name: String, val createdAt: Long, val songIds: List<Long>)

    private class LegacyData(
        val favoriteSongs: List<Pair<Long, Long>> = emptyList(),
        val favoriteVideos: List<Pair<Long, Long>> = emptyList(),
        val playlists: List<LegacyPlaylist> = emptyList(),
        val defaultPlaylistSongs: List<Long> = emptyList(),
        val history: List<PlayHistoryEntity> = emptyList(),
        val videoProgress: List<VideoProgressEntity> = emptyList(),
        val hidden: List<HiddenFolderEntity> = emptyList(),
        val settings: ((Settings) -> Settings)? = null,
    )

    private fun readLegacy(file: File): LegacyData {
        val sql = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return LegacyData()
        try {
            val favSongs = sql.rows("SELECT song_id, added_at FROM favorite_songs") { c ->
                c.getString(0)?.toLongOrNull()?.let { it to c.getLong(1) }
            }
            val favVideos = sql.rows("SELECT video_id, added_at FROM favorite_videos") { c ->
                c.getString(0)?.toLongOrNull()?.let { it to c.getLong(1) }
            }
            val refs = sql.rows("SELECT playlist_id, song_id FROM playlist_song_cross_ref ORDER BY playlist_id, order_index") { c ->
                val song = c.getString(1)?.toLongOrNull() ?: return@rows null
                c.getString(0).orEmpty() to song
            }.groupBy({ it.first }, { it.second })
            val playlists = sql.rows("SELECT id, name, created_at FROM custom_playlists ORDER BY created_at") { c ->
                val id = c.getString(0).orEmpty()
                val songs = refs[id].orEmpty()
                when {
                    id == DEFAULT_FAVORITES -> null
                    id in SEEDED && songs.isEmpty() -> null
                    else -> LegacyPlaylist(c.getString(1)?.trim().orEmpty().ifBlank { "Playlist" }, c.getLong(2), songs)
                }
            }
            val history = sql.rows("SELECT song_id, played_at, play_count FROM listening_history") { c ->
                c.getString(0)?.toLongOrNull()?.let { PlayHistoryEntity(MediaType.SONG, it, c.getLong(1), c.getInt(2).coerceAtLeast(1)) }
            } + sql.rows("SELECT video_id, watched_at, watch_count FROM video_watching_history") { c ->
                c.getString(0)?.toLongOrNull()?.let { PlayHistoryEntity(MediaType.VIDEO, it, c.getLong(1), c.getInt(2).coerceAtLeast(1)) }
            }
            val progress = sql.rows("SELECT video_id, position_ms, duration_ms, updated_at FROM last_video_playback_position") { c ->
                c.getString(0)?.toLongOrNull()?.let { VideoProgressEntity(it, c.getLong(1), c.getLong(2), c.getLong(3)) }
            }
            val hidden = sql.rows("SELECT folder_path, media_type FROM hidden_folders") { c ->
                val path = c.getString(0) ?: return@rows null
                when (c.getString(1)) {
                    "AUDIO" -> listOf(HiddenFolderEntity(path, MediaType.SONG))
                    "VIDEO" -> listOf(HiddenFolderEntity(path, MediaType.VIDEO))
                    else -> listOf(HiddenFolderEntity(path, MediaType.SONG), HiddenFolderEntity(path, MediaType.VIDEO))
                }
            }.flatten()
            return LegacyData(
                favoriteSongs = favSongs,
                favoriteVideos = favVideos,
                playlists = playlists,
                defaultPlaylistSongs = refs[DEFAULT_FAVORITES].orEmpty(),
                history = history,
                videoProgress = progress,
                hidden = hidden,
                settings = readSettings(sql),
            )
        } finally {
            sql.close()
        }
    }

    private fun readSettings(sql: SQLiteDatabase): ((Settings) -> Settings)? {
        val theme = sql.rows("SELECT theme_mode, pure_black_amoled FROM theme_preferences LIMIT 1") { c ->
            (c.getString(0) ?: "SYSTEM") to (c.getInt(1) != 0)
        }.firstOrNull()
        val player = sql.rows(
            """SELECT gapless_playback, auto_pause_on_headset, resume_video, default_video_speed,
               double_tap_seek_seconds, gesture_brightness, gesture_volume, gesture_scrub,
               min_audio_duration_seconds, dynamic_artwork_color, video_background_audio FROM player_settings LIMIT 1"""
        ) { c -> List(11) { i -> if (i == 3) c.getFloat(i) else c.getInt(i).toFloat() } }.firstOrNull()
        val sleep = sql.rows("SELECT default_duration_minutes, finish_last_song, fade_out_enabled FROM sleep_timer_preferences LIMIT 1") { c ->
            Triple(c.getInt(0), c.getInt(1) != 0, c.getInt(2) != 0)
        }.firstOrNull()
        val eq = sql.rows("SELECT is_enabled, bass_boost, virtualizer, band_levels_csv FROM equalizer_preferences LIMIT 1") { c ->
            Triple(c.getInt(0) != 0, c.getInt(1) to c.getInt(2), c.getString(3).orEmpty())
        }.firstOrNull()
        if (theme == null && player == null && sleep == null && eq == null) return null
        return { s ->
            var r = s
            theme?.let { (mode, black) ->
                r = when (mode) {
                    "AMOLED" -> r.copy(themeMode = ThemeMode.DARK, pureBlack = true)
                    "DARK" -> r.copy(themeMode = ThemeMode.DARK, pureBlack = black)
                    "LIGHT" -> r.copy(themeMode = ThemeMode.LIGHT)
                    else -> r.copy(themeMode = ThemeMode.SYSTEM, pureBlack = black)
                }
            }
            player?.let { p ->
                r = r.copy(
                    gapless = p[0] != 0f, pauseOnDisconnect = p[1] != 0f, resumeVideo = p[2] != 0f,
                    defaultVideoSpeed = p[3].takeIf { it in 0.25f..4f } ?: 1f,
                    doubleTapSeekSec = p[4].toInt().coerceIn(5, 60),
                    gestureBrightness = p[5] != 0f, gestureVolume = p[6] != 0f, gestureSeek = p[7] != 0f,
                    minAudioDurationSec = p[8].toInt().coerceIn(0, 600),
                    artworkTint = p[9] != 0f, videoBackgroundAudio = p[10] != 0f,
                )
            }
            sleep?.let { (minutes, finish, fade) ->
                r = r.copy(sleepDefaultMinutes = minutes.coerceIn(1, 600), sleepFinishSong = finish, sleepFade = fade)
            }
            eq?.let { (enabled, effects, bands) ->
                r = r.copy(
                    eqEnabled = enabled, bassBoost = effects.first.coerceIn(0, 1000), virtualizer = effects.second.coerceIn(0, 1000),
                    eqBands = bands.split(',').mapNotNull { it.trim().toIntOrNull() },
                )
            }
            r
        }
    }

    private inline fun <T> SQLiteDatabase.rows(query: String, map: (Cursor) -> T?): List<T> =
        runCatching {
            rawQuery(query, null).use { c ->
                buildList { while (c.moveToNext()) map(c)?.let { add(it) } }
            }
        }.getOrDefault(emptyList())

    companion object {
        const val LEGACY_DB = "melody_player_local.db"
        const val LEGACY_PREFS = "mt_player_video_prefs"
        const val MARKER = "legacy_import"
        private const val DEFAULT_FAVORITES = "favorites_default"
        private val SEEDED = setOf("workout_mix", "chill_vibes")
    }
}
