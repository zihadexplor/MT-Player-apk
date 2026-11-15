package com.mtplayer.app.data.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.mtplayer.app.core.Permissions
import com.mtplayer.app.data.db.HiddenFolderDao
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface LibraryState {
    data object Loading : LibraryState
    data object PermissionRequired : LibraryState
    data class Ready(val index: LibraryIndex, val refreshing: Boolean = false) : LibraryState
    data class Error(val message: String) : LibraryState
}

/** Data the library needs that does not come from MediaStore. */
data class LibraryFilter(val minDurationMs: Long, val hiddenSongFolders: Set<String>, val hiddenVideoFolders: Set<String>)

/**
 * Single owner of the in-memory library. Rebuilds the [LibraryIndex] on demand and when
 * MediaStore reports changes (debounced), on a background dispatcher.
 */
class LibraryRepository(
    private val context: Context,
    private val source: MediaStoreSource,
    private val settings: SettingsStore,
    private val hiddenFolders: HiddenFolderDao,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Last good index, or empty. Safe to read from any thread. */
    val index: LibraryIndex
        get() = (_state.value as? LibraryState.Ready)?.index ?: LibraryIndex.EMPTY

    private val mutex = Mutex()
    private var pending: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleRefresh() }
    }

    init {
        scope.launch {
            // Re-index when the filters change (minimum duration or hidden folders).
            settings.settings.map { it.minAudioDurationSec }.distinctUntilChanged().drop(1).collect { refresh() }
        }
        scope.launch {
            hiddenFolders.observeAll().distinctUntilChanged().drop(1).collect { refresh() }
        }
    }

    fun scheduleRefresh(delayMs: Long = 1_500) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            refresh()
        }
    }

    suspend fun refresh() {
        val hasAudio = Permissions.hasAudio(context)
        val hasVideo = Permissions.hasVideo(context)
        if (!hasAudio && !hasVideo) {
            _state.value = LibraryState.PermissionRequired
            return
        }
        startObserving()
        mutex.withLock {
            _state.update { if (it is LibraryState.Ready) it.copy(refreshing = true) else LibraryState.Loading }
            _state.value = try {
                val filter = loadFilter()
                val built = withContext(io) { build(hasAudio, hasVideo, filter) }
                LibraryState.Ready(built)
            } catch (e: SecurityException) {
                LibraryState.PermissionRequired
            } catch (e: Exception) {
                val previous = (_state.value as? LibraryState.Ready)?.index
                if (previous != null) LibraryState.Ready(previous) else LibraryState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun loadFilter(): LibraryFilter {
        val s = settings.current()
        val hidden = hiddenFolders.observeAll().first()
        return LibraryFilter(
            minDurationMs = s.minAudioDurationSec * 1000L,
            hiddenSongFolders = hidden.filter { it.mediaType == MediaType.SONG }.map { it.path }.toSet(),
            hiddenVideoFolders = hidden.filter { it.mediaType == MediaType.VIDEO }.map { it.path }.toSet(),
        )
    }

    private fun build(hasAudio: Boolean, hasVideo: Boolean, filter: LibraryFilter): LibraryIndex {
        val songs = if (hasAudio) source.querySongs(filter.minDurationMs).filter { it.folderPath !in filter.hiddenSongFolders } else emptyList()
        val videos = if (hasVideo) source.queryVideos().filter { it.folderPath !in filter.hiddenVideoFolders } else emptyList()
        return LibraryIndex(songs, videos)
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        source.observedUris.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
    }
}
