package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        SleepTimerPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MTPlayerDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: MTPlayerDatabase? = null

        fun getInstance(context: Context): MTPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MTPlayerDatabase::class.java,
                    "melody_player_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default starter playlists
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).mediaDao()
                                dao.insertPlaylists(
                                    listOf(
                                        PlaylistEntity(id = "favorites_default", name = "Favorite Tracks"),
                                        PlaylistEntity(id = "workout_mix", name = "Workout Energy"),
                                        PlaylistEntity(id = "chill_vibes", name = "Late Night Chill")
                                    )
                                )
                                dao.saveThemePreference(
                                    ThemePreferenceEntity(id = 1, themeMode = "DARK", accentColorHex = "#1DB954", pureBlackAmoled = true)
                                )
                                dao.saveSleepTimerPreference(
                                    SleepTimerPreferenceEntity(id = 1, defaultDurationMinutes = 30, finishLastSong = false, fadeOutEnabled = true)
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
