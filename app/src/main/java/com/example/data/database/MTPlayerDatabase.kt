package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        FavoriteVideoEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        ListeningHistoryEntity::class,
        VideoWatchingHistoryEntity::class,
        LastMusicPlaybackEntity::class,
        LastVideoPlaybackEntity::class,
        EqualizerPreferenceEntity::class,
        ThemePreferenceEntity::class,
        HiddenFolderEntity::class,
        SleepTimerPreferenceEntity::class,
        PlayerSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MTPlayerDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: MTPlayerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `player_settings` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `gapless_playback` INTEGER NOT NULL,
                        `auto_pause_on_headset` INTEGER NOT NULL,
                        `crossfade_seconds` INTEGER NOT NULL,
                        `hardware_acceleration` INTEGER NOT NULL,
                        `resume_video` INTEGER NOT NULL,
                        `default_video_speed` REAL NOT NULL,
                        `double_tap_seek_seconds` INTEGER NOT NULL,
                        `gesture_brightness` INTEGER NOT NULL,
                        `gesture_volume` INTEGER NOT NULL,
                        `gesture_scrub` INTEGER NOT NULL,
                        `min_audio_duration_seconds` INTEGER NOT NULL,
                        `auto_scan_on_launch` INTEGER NOT NULL,
                        `dynamic_artwork_color` INTEGER NOT NULL,
                        `video_background_audio` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `player_settings` (
                        `id`, `gapless_playback`, `auto_pause_on_headset`, `crossfade_seconds`,
                        `hardware_acceleration`, `resume_video`, `default_video_speed`,
                        `double_tap_seek_seconds`, `gesture_brightness`, `gesture_volume`,
                        `gesture_scrub`, `min_audio_duration_seconds`, `auto_scan_on_launch`,
                        `dynamic_artwork_color`, `video_background_audio`
                    ) VALUES (1, 1, 1, 0, 1, 1, 1.0, 10, 1, 1, 1, 30, 1, 1, 0)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): MTPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MTPlayerDatabase::class.java,
                    "melody_player_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val now = System.currentTimeMillis()
                            db.execSQL(
                                "INSERT OR IGNORE INTO `custom_playlists` (`id`, `name`, `created_at`) VALUES ('favorites_default', 'Favorite Tracks', $now)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO `custom_playlists` (`id`, `name`, `created_at`) VALUES ('workout_mix', 'Workout Energy', $now)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO `custom_playlists` (`id`, `name`, `created_at`) VALUES ('chill_vibes', 'Late Night Chill', $now)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO `theme_preferences` (`id`, `theme_mode`, `accent_color_hex`, `pure_black_amoled`) VALUES (1, 'DARK', '#E8799B', 0)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO `sleep_timer_preferences` (`id`, `default_duration_minutes`, `finish_last_song`, `fade_out_enabled`) VALUES (1, 30, 0, 1)"
                            )
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO `player_settings` (
                                    `id`, `gapless_playback`, `auto_pause_on_headset`, `crossfade_seconds`,
                                    `hardware_acceleration`, `resume_video`, `default_video_speed`,
                                    `double_tap_seek_seconds`, `gesture_brightness`, `gesture_volume`,
                                    `gesture_scrub`, `min_audio_duration_seconds`, `auto_scan_on_launch`,
                                    `dynamic_artwork_color`, `video_background_audio`
                                ) VALUES (1, 1, 1, 0, 1, 1, 1.0, 10, 1, 1, 1, 30, 1, 1, 0)
                                """.trimIndent()
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
