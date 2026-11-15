package com.mtplayer.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object MediaType {
    const val SONG = "song"
    const val VIDEO = "video"
}

@Entity(tableName = "favorites", primaryKeys = ["media_type", "media_id"])
data class FavoriteEntity(
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlist_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlist_id"), Index(value = ["playlist_id", "song_id"], unique = true)],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "play_history", primaryKeys = ["media_type", "media_id"], indices = [Index("last_played_at")])
data class PlayHistoryEntity(
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    @ColumnInfo(name = "play_count") val playCount: Int,
)

@Entity(tableName = "video_progress", indices = [Index("updated_at")])
data class VideoProgressEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "hidden_folders", primaryKeys = ["path", "media_type"])
data class HiddenFolderEntity(
    val path: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "song_id") val songId: Long,
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    val shuffle: Boolean,
    @ColumnInfo(name = "repeat_mode") val repeatMode: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "lyrics_links")
data class LyricsLinkEntity(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: Long,
    val uri: String,
)

/** Small key/value table for one-time markers (e.g. legacy import) that must be atomic with data. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
