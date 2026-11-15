package com.mtplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * MT Player 2 database. Schemas are exported to app/schemas and every future version MUST ship
 * an explicit Migration plus a MigrationTest. Destructive fallback is intentionally never enabled
 * (ADR-006): losing favourites or playlists on update is a data-loss bug.
 */
@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayHistoryEntity::class,
        VideoProgressEntity::class,
        HiddenFolderEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
        LyricsLinkEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MtDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao
    abstract fun videoProgress(): VideoProgressDao
    abstract fun hiddenFolders(): HiddenFolderDao
    abstract fun queue(): QueueDao
    abstract fun lyricsLinks(): LyricsLinkDao
    abstract fun meta(): MetaDao

    companion object {
        const val NAME = "mtplayer.db"

        fun build(context: Context): MtDatabase =
            Room.databaseBuilder(context.applicationContext, MtDatabase::class.java, NAME)
                .build()
    }
}
