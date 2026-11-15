package com.mtplayer.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayer.app.AppContainer
import com.mtplayer.app.data.db.HiddenFolderEntity
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.db.PlaylistSummary
import com.mtplayer.app.data.media.LibraryIndex
import com.mtplayer.app.data.media.LibraryState
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.data.prefs.Settings
import com.mtplayer.app.data.repo.UserDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContinueWatching(val video: Video, val progress: Float, val positionMs: Long)

/**
 * Activity-scoped view model shared by the main screens. Holds derived, ready-to-render state
 * (ids resolved to library items) so composables never query the database themselves.
 */
class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val started = SharingStarted.WhileSubscribed(5_000)

    val library: StateFlow<LibraryState> = c.library.state
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val index = library.map { (it as? LibraryState.Ready)?.index ?: LibraryIndex.EMPTY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryIndex.EMPTY)

    val favoriteSongIds: StateFlow<Set<Long>> = c.userData.favoriteSongIds.map { it.toSet() }.stateIn(viewModelScope, started, emptySet())
    val favoriteVideoIds: StateFlow<Set<Long>> = c.userData.favoriteVideoIds.map { it.toSet() }.stateIn(viewModelScope, started, emptySet())

    val favoriteSongs: StateFlow<List<Song>> = combine(c.userData.favoriteSongIds, index) { ids, idx -> ids.mapNotNull { idx.songById[it] } }
        .stateIn(viewModelScope, started, emptyList())
    val favoriteVideos: StateFlow<List<Video>> = combine(c.userData.favoriteVideoIds, index) { ids, idx -> ids.mapNotNull { idx.videoById[it] } }
        .stateIn(viewModelScope, started, emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = c.userData.playlists.stateIn(viewModelScope, started, emptyList())

    val recentSongs: StateFlow<List<Song>> = combine(c.userData.recent(MediaType.SONG, 50), index) { h, idx -> h.mapNotNull { idx.songById[it.mediaId] } }
        .stateIn(viewModelScope, started, emptyList())
    val mostPlayedSongs: StateFlow<List<Song>> = combine(c.userData.mostPlayed(MediaType.SONG, 50), index) { h, idx -> h.mapNotNull { idx.songById[it.mediaId] } }
        .stateIn(viewModelScope, started, emptyList())
    val recentVideos: StateFlow<List<Video>> = combine(c.userData.recent(MediaType.VIDEO, 50), index) { h, idx -> h.mapNotNull { idx.videoById[it.mediaId] } }
        .stateIn(viewModelScope, started, emptyList())

    val videoProgress: StateFlow<Map<Long, Float>> = c.userData.videoProgress.map { list ->
        list.filter { it.durationMs > 0 }.associate { it.videoId to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
    }.stateIn(viewModelScope, started, emptyMap())

    val continueWatching: StateFlow<List<ContinueWatching>> = combine(c.userData.videoProgress, index) { list, idx ->
        list.mapNotNull { p ->
            idx.videoById[p.videoId]?.let { ContinueWatching(it, (p.positionMs.toFloat() / p.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f), p.positionMs) }
        }.take(20)
    }.stateIn(viewModelScope, started, emptyList())

    val hiddenFolders: StateFlow<List<HiddenFolderEntity>> = c.userData.hiddenFolders.stateIn(viewModelScope, started, emptyList())

    fun refreshLibrary() { viewModelScope.launch { c.library.refresh() } }

    fun updateSettings(transform: (Settings) -> Settings) { viewModelScope.launch { c.settings.update(transform) } }

    fun toggleFavorite(type: String, id: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(c.userData.toggleFavorite(type, id)) }
    }

    fun setFavorite(type: String, id: Long, favorite: Boolean) {
        viewModelScope.launch { c.userData.setFavorite(type, id, favorite) }
    }

    suspend fun validatePlaylistName(name: String, ignoreId: Long? = null): UserDataRepository.NameResult =
        c.userData.validateName(name, ignoreId)

    fun createPlaylist(name: String, songIds: List<Long> = emptyList(), onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(c.userData.createPlaylist(name, songIds)) }
    }

    fun addToPlaylist(id: Long, songIds: List<Long>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch { onDone(c.userData.addToPlaylist(id, songIds)) }
    }

    fun renamePlaylist(id: Long, name: String) { viewModelScope.launch { c.userData.renamePlaylist(id, name) } }

    /** Deletes and returns the song ids so the caller can offer Undo. */
    fun deletePlaylist(id: Long, name: String, onDone: (List<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val ids = c.userData.playlistSongIdsNow(id)
            c.userData.deletePlaylist(id)
            onDone(ids)
        }
    }

    fun removeFromPlaylist(id: Long, songId: Long) { viewModelScope.launch { c.userData.removeFromPlaylist(id, songId) } }
    fun reorderPlaylist(id: Long, from: Int, to: Int) { viewModelScope.launch { c.userData.reorderPlaylist(id, from, to) } }

    fun hideFolder(path: String, type: String) { viewModelScope.launch { c.userData.hideFolder(path, type) } }
    fun unhideFolder(path: String, type: String) { viewModelScope.launch { c.userData.unhideFolder(path, type) } }

    fun clearHistory(onDone: () -> Unit = {}) { viewModelScope.launch { c.userData.clearHistory(); onDone() } }
}
