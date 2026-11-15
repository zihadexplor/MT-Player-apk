package com.mtplayer.app.data.repo

import com.mtplayer.app.data.db.FavoriteEntity
import com.mtplayer.app.data.db.HiddenFolderEntity
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.db.MtDatabase
import com.mtplayer.app.data.db.PlayHistoryEntity
import com.mtplayer.app.data.db.PlaylistEntity
import com.mtplayer.app.data.db.PlaylistSummary
import com.mtplayer.app.data.db.VideoProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** All user-owned data: favourites, playlists, history, video progress, hidden folders. */
class UserDataRepository(private val db: MtDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    // Favourites --------------------------------------------------------------------------
    val favoriteSongIds: Flow<List<Long>> = db.favorites().observe(MediaType.SONG).map { l -> l.map { it.mediaId } }
    val favoriteVideoIds: Flow<List<Long>> = db.favorites().observe(MediaType.VIDEO).map { l -> l.map { it.mediaId } }

    suspend fun isFavorite(type: String, id: Long) = db.favorites().isFavorite(type, id)

    /** Returns the new state (true = now a favourite). */
    suspend fun toggleFavorite(type: String, id: Long): Boolean {
        return if (db.favorites().isFavorite(type, id)) {
            db.favorites().delete(type, id); false
        } else {
            db.favorites().insert(FavoriteEntity(type, id, clock())); true
        }
    }

    suspend fun setFavorite(type: String, id: Long, favorite: Boolean) {
        if (favorite) db.favorites().insert(FavoriteEntity(type, id, clock())) else db.favorites().delete(type, id)
    }

    // Playlists ---------------------------------------------------------------------------
    val playlists: Flow<List<PlaylistSummary>> = db.playlists().observeSummaries()
    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlists().observe(id)
    fun playlistSongIds(id: Long): Flow<List<Long>> = db.playlists().observeSongIds(id)
    suspend fun playlistSongIdsNow(id: Long): List<Long> = db.playlists().songIds(id)

    sealed interface NameResult {
        data object Ok : NameResult
        data object Blank : NameResult
        data object TooLong : NameResult
        data object Duplicate : NameResult
    }

    suspend fun validateName(name: String, ignoreId: Long? = null): NameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameResult.Blank
        if (trimmed.length > MAX_NAME) return NameResult.TooLong
        val dupes = db.playlists().countByName(trimmed)
        if (dupes > 0) {
            // A rename of a playlist to (a case variant of) its own name is fine.
            val current = ignoreId?.let { db.playlists().observe(it).first() }
            val sameAsSelf = current != null && dupes == 1 && current.name.equals(trimmed, ignoreCase = true)
            return if (sameAsSelf) NameResult.Ok else NameResult.Duplicate
        }
        return NameResult.Ok
    }

    suspend fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Long {
        val now = clock()
        val id = db.playlists().insert(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
        if (songIds.isNotEmpty()) db.playlists().append(id, songIds, now)
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name.trim(), clock())
    suspend fun deletePlaylist(id: Long) = db.playlists().delete(id)
    suspend fun addToPlaylist(id: Long, songIds: List<Long>): Int = db.playlists().append(id, songIds, clock())

    suspend fun removeFromPlaylist(id: Long, songId: Long) {
        val remaining = db.playlists().songIds(id).filter { it != songId }
        db.playlists().replaceItems(id, remaining, clock())
    }

    suspend fun reorderPlaylist(id: Long, from: Int, to: Int) {
        val list = db.playlists().songIds(id).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        db.playlists().replaceItems(id, list, clock())
    }

    // History -----------------------------------------------------------------------------
    fun recent(type: String, limit: Int = 50): Flow<List<PlayHistoryEntity>> = db.history().observeRecent(type, limit)
    fun mostPlayed(type: String, limit: Int = 50): Flow<List<PlayHistoryEntity>> = db.history().observeMostPlayed(type, limit)
    /** History is best-effort: a storage failure must never interrupt playback. */
    suspend fun recordPlay(type: String, id: Long) {
        runCatching { db.history().recordPlay(type, id, clock()) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; android.util.Log.w("UserData", "recordPlay failed", it) }
    }
    suspend fun clearHistory() { db.history().clearAll(); db.videoProgress().clear() }

    // Video progress ----------------------------------------------------------------------
    val videoProgress: Flow<List<VideoProgressEntity>> = db.videoProgress().observeAll()
    suspend fun videoProgressFor(id: Long) = db.videoProgress().get(id)

    /** Saves progress; positions within the first 3 s or the last 2% are treated as "not started" / "finished". */
    suspend fun saveVideoProgress(id: Long, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val finished = positionMs >= durationMs * 0.98 || durationMs - positionMs < 3_000
        if (positionMs < 3_000 || finished) db.videoProgress().delete(id)
        else db.videoProgress().upsert(VideoProgressEntity(id, positionMs, durationMs, clock()))
    }

    // Hidden folders ----------------------------------------------------------------------
    val hiddenFolders: Flow<List<HiddenFolderEntity>> = db.hiddenFolders().observeAll()
    suspend fun hideFolder(path: String, type: String) = db.hiddenFolders().insert(HiddenFolderEntity(path, type))
    suspend fun unhideFolder(path: String, type: String) = db.hiddenFolders().delete(path, type)

    companion object { const val MAX_NAME = 80 }
}
