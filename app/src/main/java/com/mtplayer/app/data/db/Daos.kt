package com.mtplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE media_type = :type ORDER BY added_at DESC")
    fun observe(type: String): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE media_type = :type AND media_id = :id")
    suspend fun delete(type: String, id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE media_type = :type AND media_id = :id)")
    suspend fun isFavorite(type: String, id: Long): Boolean
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
    val firstSongId: Long?,
) {
    val songCount: Int get() = itemCount
}

@Dao
interface PlaylistDao {
    @Query(
        """SELECT p.id, p.name, p.created_at AS createdAt, p.updated_at AS updatedAt,
           (SELECT COUNT(*) FROM playlist_items i WHERE i.playlist_id = p.id) AS itemCount,
           (SELECT i.song_id FROM playlist_items i WHERE i.playlist_id = p.id ORDER BY i.position LIMIT 1) AS firstSongId
           FROM playlists p ORDER BY p.updated_at DESC"""
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT song_id FROM playlist_items WHERE playlist_id = :id ORDER BY position")
    fun observeSongIds(id: Long): Flow<List<Long>>

    @Query("SELECT song_id FROM playlist_items WHERE playlist_id = :id ORDER BY position")
    suspend fun songIds(id: Long): List<Long>

    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlist_id = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_items WHERE playlist_id = :id")
    suspend fun clearItems(id: Long)

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM playlists WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    /** Appends songs that are not already in the playlist. Returns how many were added. */
    @Transaction
    suspend fun append(id: Long, songIds: List<Long>, now: Long): Int {
        val existing = songIds(id).toHashSet()
        var pos = maxPosition(id)
        val fresh = songIds.distinct().filter { it !in existing }
        if (fresh.isEmpty()) return 0
        insertItems(fresh.map { PlaylistItemEntity(playlistId = id, songId = it, position = ++pos, addedAt = now) })
        touch(id, now)
        return fresh.size
    }

    /** Rewrites the playlist order; used for remove and reorder so positions stay dense. */
    @Transaction
    suspend fun replaceItems(id: Long, songIds: List<Long>, now: Long) {
        clearItems(id)
        insertItems(songIds.distinct().mapIndexed { i, s -> PlaylistItemEntity(playlistId = id, songId = s, position = i, addedAt = now) })
        touch(id, now)
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history WHERE media_type = :type ORDER BY last_played_at DESC LIMIT :limit")
    fun observeRecent(type: String, limit: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE media_type = :type ORDER BY play_count DESC, last_played_at DESC LIMIT :limit")
    fun observeMostPlayed(type: String, limit: Int): Flow<List<PlayHistoryEntity>>

    @Query(
        """INSERT INTO play_history(media_type, media_id, last_played_at, play_count) VALUES(:type, :id, :now, 1)
           ON CONFLICT(media_type, media_id) DO UPDATE SET last_played_at = :now, play_count = play_count + 1"""
    )
    suspend fun recordPlay(type: String, id: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<PlayHistoryEntity>)

    @Query("DELETE FROM play_history WHERE media_type = :type")
    suspend fun clear(type: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAll()
}

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress WHERE video_id = :id")
    suspend fun get(id: Long): VideoProgressEntity?

    @Query("SELECT * FROM video_progress ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<VideoProgressEntity>>

    @Upsert
    suspend fun upsert(entity: VideoProgressEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<VideoProgressEntity>)

    @Query("DELETE FROM video_progress WHERE video_id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM video_progress")
    suspend fun clear()
}

@Dao
interface HiddenFolderDao {
    @Query("SELECT * FROM hidden_folders")
    fun observeAll(): Flow<List<HiddenFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: HiddenFolderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<HiddenFolderEntity>)

    @Query("DELETE FROM hidden_folders WHERE path = :path AND media_type = :type")
    suspend fun delete(path: String, type: String)
}

@Dao
interface QueueDao {
    @Query("SELECT song_id FROM queue_items ORDER BY position")
    suspend fun songIds(): List<Long>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clearItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QueueItemEntity>)

    @Upsert
    suspend fun upsertState(state: QueueStateEntity)

    @Query("UPDATE queue_state SET current_index = :index, position_ms = :positionMs, shuffle = :shuffle, repeat_mode = :repeat, updated_at = :now WHERE id = 0")
    suspend fun updatePosition(index: Int, positionMs: Long, shuffle: Boolean, repeat: Int, now: Long)

    @Transaction
    suspend fun replace(songIds: List<Long>, state: QueueStateEntity) {
        clearItems()
        insertItems(songIds.mapIndexed { i, id -> QueueItemEntity(i, id) })
        upsertState(state)
    }
}

@Dao
interface LyricsLinkDao {
    @Query("SELECT * FROM lyrics_links WHERE song_id = :id")
    suspend fun get(id: Long): LyricsLinkEntity?

    @Upsert
    suspend fun upsert(entity: LyricsLinkEntity)

    @Query("DELETE FROM lyrics_links WHERE song_id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: MetaEntity)
}
