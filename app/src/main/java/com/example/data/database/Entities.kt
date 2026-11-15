package com.example.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1. Favorite Songs
 */
@Entity(
    tableName = "favorite_songs",
    indices = [Index(value = ["song_id"], unique = true)]
)
data class FavoriteSongEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 2. Favorite Videos
 */
@Entity(
    tableName = "favorite_videos",
    indices = [Index(value = ["video_id"], unique = true)]
)
data class FavoriteVideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 3. Custom Playlists
 */
@Entity(
    tableName = "custom_playlists",
    indices = [Index(value = ["name"])]
)
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 4. Playlist Song Relationships (cross-reference with order)
 */
@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlist_id", "song_id"],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["song_id"]),
        Index(value = ["playlist_id", "order_index"])
    ]
)
data class PlaylistSongCrossRef(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 5. Listening History
 */
@Entity(
    tableName = "listening_history",
    indices = [Index(value = ["song_id"], unique = true), Index(value = ["played_at"])]
)
data class ListeningHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "played_at")
    val playedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "play_count")
    val playCount: Int = 1
)

/**
 * 6. Video Watching History
 */
@Entity(
    tableName = "video_watching_history",
    indices = [Index(value = ["video_id"], unique = true), Index(value = ["watched_at"])]
)
data class VideoWatchingHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "watched_at")
    val watchedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "watch_count")
    val watchCount: Int = 1
)

/**
 * 7. Last Music Playback Position
 */
@Entity(
    tableName = "last_music_playback_position",
    indices = [Index(value = ["song_id"], unique = true)]
)
data class LastMusicPlaybackEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 8. Last Video Playback Position
 */
@Entity(
    tableName = "last_video_playback_position",
    indices = [Index(value = ["video_id"], unique = true)]
)
data class LastVideoPlaybackEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 9. Equalizer Preferences
 */
@Entity(tableName = "equalizer_preferences")
data class EqualizerPreferenceEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,
    @ColumnInfo(name = "preset_name")
    val presetName: String = "Normal",
    @ColumnInfo(name = "bass_boost")
    val bassBoost: Int = 0,
    @ColumnInfo(name = "virtualizer")
    val virtualizer: Int = 0,
    @ColumnInfo(name = "band_levels_csv")
    val bandLevelsCsv: String = "" // e.g. "0,0,0,0,0"
)

/**
 * 10. Theme Preferences
 */
@Entity(tableName = "theme_preferences")
data class ThemePreferenceEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "theme_mode")
    val themeMode: String = "DARK", // DARK, AMOLED, LIGHT, SYSTEM
    @ColumnInfo(name = "accent_color_hex")
    val accentColorHex: String = "#E8799B",
    @ColumnInfo(name = "pure_black_amoled")
    val pureBlackAmoled: Boolean = false
)

/**
 * 11. Hidden Folders
 */
@Entity(
    tableName = "hidden_folders",
    indices = [Index(value = ["folder_path"], unique = true)]
)
data class HiddenFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "folder_path")
    val folderPath: String,
    @ColumnInfo(name = "media_type")
    val mediaType: String = "ALL", // AUDIO, VIDEO, ALL
    @ColumnInfo(name = "hidden_at")
    val hiddenAt: Long = System.currentTimeMillis()
)

/**
 * 12. Sleep Timer Preferences
 */
@Entity(tableName = "sleep_timer_preferences")
data class SleepTimerPreferenceEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "default_duration_minutes")
    val defaultDurationMinutes: Int = 30,
    @ColumnInfo(name = "finish_last_song")
    val finishLastSong: Boolean = false,
    @ColumnInfo(name = "fade_out_enabled")
    val fadeOutEnabled: Boolean = true
)

/**
 * 13. Persisted Player & Personalization Settings
 */
@Entity(tableName = "player_settings")
data class PlayerSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "gapless_playback")
    val gaplessPlayback: Boolean = true,
    @ColumnInfo(name = "auto_pause_on_headset")
    val autoPauseOnHeadset: Boolean = true,
    @ColumnInfo(name = "crossfade_seconds")
    val crossfadeSeconds: Int = 0,
    @ColumnInfo(name = "hardware_acceleration")
    val hardwareAcceleration: Boolean = true,
    @ColumnInfo(name = "resume_video")
    val resumeVideo: Boolean = true,
    @ColumnInfo(name = "default_video_speed")
    val defaultVideoSpeed: Float = 1.0f,
    @ColumnInfo(name = "double_tap_seek_seconds")
    val doubleTapSeekSeconds: Int = 10,
    @ColumnInfo(name = "gesture_brightness")
    val gestureBrightness: Boolean = true,
    @ColumnInfo(name = "gesture_volume")
    val gestureVolume: Boolean = true,
    @ColumnInfo(name = "gesture_scrub")
    val gestureScrub: Boolean = true,
    @ColumnInfo(name = "min_audio_duration_seconds")
    val minAudioDurationSeconds: Int = 30,
    @ColumnInfo(name = "auto_scan_on_launch")
    val autoScanOnLaunch: Boolean = true,
    @ColumnInfo(name = "dynamic_artwork_color")
    val dynamicArtworkColor: Boolean = true,
    @ColumnInfo(name = "video_background_audio")
    val videoBackgroundAudio: Boolean = false
)
