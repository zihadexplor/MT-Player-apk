package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioTrack
import com.example.data.model.MusicFolder
import com.example.data.model.PlaybackState
import com.example.data.model.Playlist
import com.example.data.model.StorageStats
import com.example.data.model.TrackSort
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.data.model.VideoSort
import com.example.data.repository.MediaRepository
import com.example.data.repository.OfflineMediaRepository
import com.example.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MediaRepository = OfflineMediaRepository(application.applicationContext)
    private val playerManager: MusicPlayerManager = MusicPlayerManager(
        context = application.applicationContext,
        onTrackChanged = { trackId ->
            viewModelScope.launch {
                repository.recordTrackPlayed(trackId)
            }
        }
    )

    private val equalizerManager: com.example.player.equalizer.EqualizerManager =
        com.example.player.equalizer.EqualizerManager.getInstance(application.applicationContext)

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val equalizerState: StateFlow<com.example.player.equalizer.EqualizerState> = equalizerManager.equalizerState

    val storageStats: StateFlow<StorageStats> = repository.storageStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    val rawAudioTracks: StateFlow<List<AudioTrack>> = repository.audioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawAlbums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawArtists: StateFlow<List<Artist>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMusicFolders: StateFlow<List<MusicFolder>> = repository.musicFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawVideoItems: StateFlow<List<VideoItem>> = repository.videoItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolders: StateFlow<List<VideoFolder>> = repository.videoFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatchingVideos: StateFlow<List<VideoItem>> = repository.continueWatchingVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatchedVideos: StateFlow<List<VideoItem>> = repository.recentlyWatchedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVideos: StateFlow<List<VideoItem>> = repository.favoriteVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenVideoFolderPaths: StateFlow<Set<String>> = repository.hiddenVideoFolderPaths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedTracks: StateFlow<List<AudioTrack>> = repository.recentlyPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedTracks: StateFlow<List<AudioTrack>> = repository.mostPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _trackSort = MutableStateFlow(TrackSort.TITLE_ASC)
    val trackSort = _trackSort.asStateFlow()

    private val _videoSort = MutableStateFlow(VideoSort.DATE_DESC)
    val videoSort = _videoSort.asStateFlow()

    private val _activeMusicTab = MutableStateFlow(0) // 0: Tracks, 1: Albums, 2: Artists, 3: Folders, 4: Favorites
    val activeMusicTab = _activeMusicTab.asStateFlow()

    private val _activeVideoTab = MutableStateFlow(0) // 0: All Videos, 1: Folders, 2: Continue, 3: Recent, 4: Favorites
    val activeVideoTab = _activeVideoTab.asStateFlow()

    private val _selectedVideoFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedVideoFolder = _selectedVideoFolder.asStateFlow()

    private val _activePlayingVideo = MutableStateFlow<VideoItem?>(null)
    val activePlayingVideo = _activePlayingVideo.asStateFlow()

    private val _videoForDetails = MutableStateFlow<VideoItem?>(null)
    val videoForDetails = _videoForDetails.asStateFlow()

    private val _showManageHiddenFoldersDialog = MutableStateFlow(false)
    val showManageHiddenFoldersDialog = _showManageHiddenFoldersDialog.asStateFlow()

    private val _isVideoGridView = MutableStateFlow(true)
    val isVideoGridView = _isVideoGridView.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded = _isNowPlayingExpanded.asStateFlow()

    // Multi-select state
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTrackIds = _selectedTrackIds.asStateFlow()

    // Context song options sheet
    private val _actionTrack = MutableStateFlow<AudioTrack?>(null)
    val actionTrack = _actionTrack.asStateFlow()

    // Add to playlist modal state
    private val _pendingPlaylistTrackIds = MutableStateFlow<List<String>>(emptyList())
    val pendingPlaylistTrackIds = _pendingPlaylistTrackIds.asStateFlow()

    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    val showAddToPlaylistDialog = _showAddToPlaylistDialog.asStateFlow()

    // Filtered & Sorted Tracks
    val filteredAudioTracks: StateFlow<List<AudioTrack>> = combine(
        rawAudioTracks,
        _searchQuery,
        _trackSort
    ) { tracks, query, sort ->
        val filtered = if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }

        when (sort) {
            TrackSort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            TrackSort.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            TrackSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            TrackSort.DURATION -> filtered.sortedByDescending { it.durationMs }
            TrackSort.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            TrackSort.ALBUM -> filtered.sortedBy { it.album.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums: StateFlow<List<Album>> = combine(
        rawAlbums,
        _searchQuery
    ) { albums, query ->
        if (query.isBlank()) albums
        else albums.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArtists: StateFlow<List<Artist>> = combine(
        rawArtists,
        _searchQuery
    ) { artists, query ->
        if (query.isBlank()) artists
        else artists.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFolders: StateFlow<List<MusicFolder>> = combine(
        rawMusicFolders,
        _searchQuery
    ) { folders, query ->
        if (query.isBlank()) folders
        else folders.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.path.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideoItems: StateFlow<List<VideoItem>> = combine(
        rawVideoItems,
        _searchQuery,
        _videoSort
    ) { videos, query, sort ->
        val filtered = if (query.isBlank()) videos
        else videos.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.folderName.contains(query, ignoreCase = true)
        }

        when (sort) {
            VideoSort.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            VideoSort.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            VideoSort.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
            VideoSort.DATE_ASC -> filtered.sortedBy { it.dateAdded }
            VideoSort.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            VideoSort.DURATION_ASC -> filtered.sortedBy { it.durationMs }
            VideoSort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanMedia()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortChange(sort: TrackSort) {
        _trackSort.value = sort
    }

    fun onMusicTabChange(tabIndex: Int) {
        _activeMusicTab.value = tabIndex
    }

    fun onVideoTabChange(tabIndex: Int) {
        _activeVideoTab.value = tabIndex
        if (_selectedVideoFolder.value != null && tabIndex != 1) {
            _selectedVideoFolder.value = null
        }
    }

    fun onVideoSortChange(sort: VideoSort) {
        _videoSort.value = sort
    }

    fun toggleVideoLayout() {
        _isVideoGridView.value = !_isVideoGridView.value
    }

    fun toggleVideoFavorite(videoId: String) {
        viewModelScope.launch {
            repository.toggleVideoFavorite(videoId)
        }
    }

    fun hideVideoFolder(folderPath: String) {
        viewModelScope.launch {
            repository.hideVideoFolder(folderPath)
            if (_selectedVideoFolder.value?.path == folderPath) {
                _selectedVideoFolder.value = null
            }
        }
    }

    fun unhideVideoFolder(folderPath: String) {
        viewModelScope.launch {
            repository.unhideVideoFolder(folderPath)
        }
    }

    fun unhideAllVideoFolders() {
        viewModelScope.launch {
            repository.unhideAllVideoFolders()
        }
    }

    fun selectVideoFolder(folder: VideoFolder?) {
        _selectedVideoFolder.value = folder
    }

    fun playVideo(video: VideoItem) {
        // Pause music playback if active
        if (playbackState.value.isPlaying) {
            playerManager.pause()
        }
        _activePlayingVideo.value = video
    }

    fun closeVideoPlayer() {
        _activePlayingVideo.value = null
    }

    fun updateVideoPlaybackProgress(videoId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repository.updateVideoProgress(videoId, positionMs, durationMs)
        }
    }

    fun clearVideoPlaybackProgress(videoId: String) {
        viewModelScope.launch {
            repository.clearVideoProgress(videoId)
        }
    }

    fun showVideoDetails(video: VideoItem) {
        _videoForDetails.value = video
    }

    fun dismissVideoDetails() {
        _videoForDetails.value = null
    }

    fun setShowManageHiddenFolders(show: Boolean) {
        _showManageHiddenFoldersDialog.value = show
    }

    // Playback actions
    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = filteredAudioTracks.value) {
        playerManager.playTrack(track, queue)
    }

    fun playNext(track: AudioTrack) {
        playerManager.playNext(track)
    }

    fun addToQueue(track: AudioTrack) {
        playerManager.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerManager.cycleRepeatMode()
    }

    fun playQueueIndex(index: Int) {
        playerManager.playQueueIndex(index)
    }

    fun openNowPlaying() {
        _isNowPlayingExpanded.value = true
    }

    fun closeNowPlaying() {
        _isNowPlayingExpanded.value = false
    }

    fun clearPlaybackError() {
        playerManager.clearError()
    }

    // Track Action Sheet
    fun openTrackOptions(track: AudioTrack) {
        _actionTrack.value = track
    }

    fun closeTrackOptions() {
        _actionTrack.value = null
    }

    // Multi-Selection
    fun toggleSelectionMode(initialTrackId: String? = null) {
        val newMode = !_isSelectionMode.value
        _isSelectionMode.value = newMode
        if (newMode && initialTrackId != null) {
            _selectedTrackIds.value = setOf(initialTrackId)
        } else if (!newMode) {
            _selectedTrackIds.value = emptySet()
        }
    }

    fun toggleTrackSelection(trackId: String) {
        val current = _selectedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _selectedTrackIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAllTracks(tracks: List<AudioTrack>) {
        _selectedTrackIds.value = tracks.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedTrackIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun playSelectedTracks() {
        val selected = rawAudioTracks.value.filter { _selectedTrackIds.value.contains(it.id) }
        if (selected.isNotEmpty()) {
            playTrack(selected.first(), selected)
            clearSelection()
        }
    }

    fun addSelectedToFavorites() {
        viewModelScope.launch {
            repository.addFavorites(_selectedTrackIds.value.toList())
            clearSelection()
        }
    }

    fun addSelectedToQueue() {
        val selected = rawAudioTracks.value.filter { _selectedTrackIds.value.contains(it.id) }
        playerManager.addMultipleToQueue(selected)
        clearSelection()
    }

    // Playlist actions
    fun openAddToPlaylist(trackIds: List<String>) {
        _pendingPlaylistTrackIds.value = trackIds
        _showAddToPlaylistDialog.value = true
    }

    fun closeAddToPlaylist() {
        _showAddToPlaylistDialog.value = false
        _pendingPlaylistTrackIds.value = emptyList()
    }

    fun createPlaylist(name: String, trackIds: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.createPlaylist(name, trackIds)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            repository.addTracksToPlaylist(playlistId, trackIds)
            closeAddToPlaylist()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun onToggleFavorite(trackId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        repository.updatePermissionStatus(granted, permanentlyDenied)
        if (granted) {
            scanMedia()
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            repository.scanDeviceMedia()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        equalizerManager.setBandLevel(bandIndex, levelMb)
    }

    fun applyEqualizerPreset(presetName: String) {
        equalizerManager.applyPreset(presetName)
    }

    fun setBassBoostStrength(strength: Int) {
        equalizerManager.setBassBoostStrength(strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        equalizerManager.setVirtualizerStrength(strength)
    }

    fun resetEqualizerToDefault() {
        equalizerManager.resetToDefault()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
