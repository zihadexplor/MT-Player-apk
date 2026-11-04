package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // -------------------------------------------------------------------------
    // 1. Favorite Songs
    // -------------------------------------------------------------------------
    @Query("SELECT song_id FROM favorite_songs ORDER BY added_at DESC")
    fun getAllFavoriteSongIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE song_id = :songId)")
    fun isSongFavorite(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteSong(favorite: FavoriteSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteSongs(favorites: List<FavoriteSongEntity>)

    @Query("DELETE FROM favorite_songs WHERE song_id = :songId")
    suspend fun deleteFavoriteSong(songId: String)

    @Query("DELETE FROM favorite_songs WHERE song_id NOT IN (:validSongIds)")
    suspend fun cleanupOrphanFavoriteSongs(validSongIds: List<String>)

    // -------------------------------------------------------------------------
    // 2. Favorite Videos
    // -------------------------------------------------------------------------
    @Query("SELECT video_id FROM favorite_videos ORDER BY added_at DESC")
    fun getAllFavoriteVideoIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE video_id = :videoId)")
    fun isVideoFavorite(videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteVideo(favorite: FavoriteVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteVideos(favorites: List<FavoriteVideoEntity>)

    @Query("DELETE FROM favorite_videos WHERE video_id = :videoId")
    suspend fun deleteFavoriteVideo(videoId: String)

    @Query("DELETE FROM favorite_videos WHERE video_id NOT IN (:validVideoIds)")
    suspend fun cleanupOrphanFavoriteVideos(validVideoIds: List<String>)

    // -------------------------------------------------------------------------
    // 3. Custom Playlists & 4. Song Relationships
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM custom_playlists ORDER BY created_at ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM custom_playlists ORDER BY created_at ASC")
    suspend fun getAllPlaylistsDirect(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Query("UPDATE custom_playlists SET name = :newName WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: String, newName: String)

    @Query("DELETE FROM custom_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlist_id = :playlistId ORDER BY order_index ASC")
    fun getSongsForPlaylist(playlistId: String): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT * FROM playlist_song_cross_ref ORDER BY playlist_id, order_index ASC")
    fun getAllPlaylistSongCrossRefs(): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT * FROM playlist_song_cross_ref ORDER BY playlist_id, order_index ASC")
    suspend fun getAllPlaylistSongCrossRefsDirect(): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongRef(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongRefs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun clearSongsFromPlaylist(playlistId: String)

    @Transaction
    suspend fun reorderPlaylistSongs(playlistId: String, songIdsInOrder: List<String>) {
        clearSongsFromPlaylist(playlistId)
        val refs = songIdsInOrder.mapIndexed { index, songId ->
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, orderIndex = index)
        }
        insertPlaylistSongRefs(refs)
    }

    @Query("DELETE FROM playlist_song_cross_ref WHERE song_id NOT IN (:validSongIds)")
    suspend fun cleanupOrphanPlaylistSongs(validSongIds: List<String>)

    // -------------------------------------------------------------------------
    // 5. Listening History
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM listening_history ORDER BY played_at DESC")
    fun getAllListeningHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY played_at DESC")
    suspend fun getAllListeningHistoryDirect(): List<ListeningHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningHistory(entity: ListeningHistoryEntity)

    @Query("UPDATE listening_history SET played_at = :playedAt, play_count = play_count + 1 WHERE song_id = :songId")
    suspend fun updateListeningCount(songId: String, playedAt: Long): Int

    @Transaction
    suspend fun recordSongPlayed(songId: String, timestamp: Long = System.currentTimeMillis()) {
        val updatedRows = updateListeningCount(songId, timestamp)
        if (updatedRows == 0) {
            insertListeningHistory(ListeningHistoryEntity(songId = songId, playedAt = timestamp, playCount = 1))
        }
    }

    @Query("DELETE FROM listening_history WHERE song_id NOT IN (:validSongIds)")
    suspend fun cleanupOrphanListeningHistory(validSongIds: List<String>)

    @Query("DELETE FROM listening_history")
    suspend fun clearListeningHistory()

    // -------------------------------------------------------------------------
    // 6. Video Watching History
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM video_watching_history ORDER BY watched_at DESC")
    fun getAllVideoWatchingHistory(): Flow<List<VideoWatchingHistoryEntity>>

    @Query("SELECT * FROM video_watching_history ORDER BY watched_at DESC")
    suspend fun getAllVideoWatchingHistoryDirect(): List<VideoWatchingHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoWatchingHistory(entity: VideoWatchingHistoryEntity)

    @Query("UPDATE video_watching_history SET watched_at = :watchedAt, watch_count = watch_count + 1 WHERE video_id = :videoId")
    suspend fun updateVideoWatchCount(videoId: String, watchedAt: Long): Int

    @Transaction
    suspend fun recordVideoWatched(videoId: String, timestamp: Long = System.currentTimeMillis()) {
        val updatedRows = updateVideoWatchCount(videoId, timestamp)
        if (updatedRows == 0) {
            insertVideoWatchingHistory(VideoWatchingHistoryEntity(videoId = videoId, watchedAt = timestamp, watchCount = 1))
        }
    }

    @Query("DELETE FROM video_watching_history WHERE video_id NOT IN (:validVideoIds)")
    suspend fun cleanupOrphanVideoWatchingHistory(validVideoIds: List<String>)

    @Query("DELETE FROM video_watching_history")
    suspend fun clearVideoWatchingHistory()

    // -------------------------------------------------------------------------
    // 7. Last Music Playback Position
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM last_music_playback_position ORDER BY updated_at DESC LIMIT 1")
    fun getLastMusicPlaybackPosition(): Flow<LastMusicPlaybackEntity?>

    @Query("SELECT * FROM last_music_playback_position WHERE song_id = :songId")
    fun getMusicPlaybackPosition(songId: String): Flow<LastMusicPlaybackEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMusicPlaybackPosition(entity: LastMusicPlaybackEntity)

    @Query("DELETE FROM last_music_playback_position WHERE song_id = :songId")
    suspend fun deleteMusicPlaybackPosition(songId: String)

    // -------------------------------------------------------------------------
    // 8. Last Video Playback Position
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM last_video_playback_position ORDER BY updated_at DESC")
    fun getAllVideoPlaybackPositions(): Flow<List<LastVideoPlaybackEntity>>

    @Query("SELECT * FROM last_video_playback_position WHERE video_id = :videoId")
    fun getVideoPlaybackPosition(videoId: String): Flow<LastVideoPlaybackEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVideoPlaybackPosition(entity: LastVideoPlaybackEntity)

    @Query("DELETE FROM last_video_playback_position WHERE video_id = :videoId")
    suspend fun deleteVideoPlaybackPosition(videoId: String)

    // -------------------------------------------------------------------------
    // 9. Equalizer Preferences
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM equalizer_preferences WHERE id = 1")
    fun getEqualizerPreference(): Flow<EqualizerPreferenceEntity?>

    @Query("SELECT * FROM equalizer_preferences WHERE id = 1")
    suspend fun getEqualizerPreferenceDirect(): EqualizerPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEqualizerPreference(entity: EqualizerPreferenceEntity)

    // -------------------------------------------------------------------------
    // 10. Theme Preferences
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM theme_preferences WHERE id = 1")
    fun getThemePreference(): Flow<ThemePreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveThemePreference(entity: ThemePreferenceEntity)

    // -------------------------------------------------------------------------
    // 11. Hidden Folders
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM hidden_folders ORDER BY hidden_at DESC")
    fun getAllHiddenFolders(): Flow<List<HiddenFolderEntity>>

    @Query("SELECT * FROM hidden_folders ORDER BY hidden_at DESC")
    suspend fun getAllHiddenFoldersDirect(): List<HiddenFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenFolder(entity: HiddenFolderEntity)

    @Query("DELETE FROM hidden_folders WHERE folder_path = :folderPath")
    suspend fun deleteHiddenFolder(folderPath: String)

    @Query("DELETE FROM hidden_folders")
    suspend fun deleteAllHiddenFolders()

    // -------------------------------------------------------------------------
    // 12. Sleep Timer Preferences
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM sleep_timer_preferences WHERE id = 1")
    fun getSleepTimerPreference(): Flow<SleepTimerPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSleepTimerPreference(entity: SleepTimerPreferenceEntity)
}
