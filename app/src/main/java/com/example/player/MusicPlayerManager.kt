package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackState
import com.example.data.model.PlayerRepeatMode
import com.example.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicPlayerManager(
    private val context: Context,
    private val onTrackChanged: ((String) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val player: ExoPlayer = PlaybackService.getOrCreatePlayer(context)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var finishLastSongOnTimer: Boolean = false
    private var fadeOutAudioOnTimer: Boolean = true
    private var waitingForSongFinish: Boolean = false

    private var currentQueue: List<AudioTrack> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
                updateCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    _playbackState.update {
                        it.copy(
                            durationMs = player.duration.coerceAtLeast(0L),
                            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                            errorMessage = null
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            currentPositionMs = it.durationMs
                        )
                    }
                    if (waitingForSongFinish) {
                        pause()
                        player.volume = 1.0f
                        waitingForSongFinish = false
                        _sleepTimerRemainingSeconds.value = null
                    }
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (waitingForSongFinish) {
                pause()
                player.volume = 1.0f
                waitingForSongFinish = false
                _sleepTimerRemainingSeconds.value = null
            }
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex in currentQueue.indices) {
                val track = currentQueue[currentIndex]
                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentQueueIndex = currentIndex,
                        currentPositionMs = 0L,
                        durationMs = if (track.durationMs > 0) track.durationMs else 0L,
                        errorMessage = null
                    )
                }
                onTrackChanged?.invoke(track.id)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Audio file not found on device"
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "Unsupported audio format"
                else -> "Playback error: ${error.localizedMessage ?: "File unplayable"}"
            }
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = message
                )
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                else -> PlayerRepeatMode.OFF
            }
            _playbackState.update { it.copy(repeatMode = mode) }
        }
    }

    init {
        player.addListener(playerListener)
        // Ensure PlaybackService is started for background lifecycle
        startServiceSilently()
        // Restore playback state if player was already playing in background
        restoreExistingState()
    }

    private fun startServiceSilently() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (_: Exception) {
            // Service will be started when playback begins
        }
    }

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            // Starting via startService is valid while the app is in the foreground and avoids
            // Android's strict 5-second startForeground deadline. MediaSessionService automatically
            // promotes itself to foreground once playback starts.
            context.startService(intent)
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(context, PlaybackService::class.java)
                    context.startForegroundService(intent)
                }
            } catch (_: Exception) {
                // Ignore fallback failure
            }
        }
    }

    private fun restoreExistingState() {
        val isPlaying = player.isPlaying
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
            else -> PlayerRepeatMode.OFF
        }

        _playbackState.update {
            it.copy(
                isPlaying = isPlaying,
                durationMs = duration,
                currentPositionMs = position,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = repeat
            )
        }

        if (isPlaying) {
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateCurrentPosition()
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateCurrentPosition() {
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.let { if (it > 0) it else _playbackState.value.durationMs }
        val buf = player.bufferedPosition.coerceAtLeast(0L)
        _playbackState.update {
            it.copy(
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf
            )
        }
    }

    private fun createMediaItem(track: AudioTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.albumArtUri?.let { artUri ->
                    setArtworkUri(Uri.parse(artUri))
                }
            }
            .build()

        return MediaItem.Builder()
            .setUri(Uri.parse(track.uri))
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = listOf(track)) {
        ensureServiceRunning()
        currentQueue = if (queue.isNotEmpty()) queue else listOf(track)
        val startIndex = currentQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        val mediaItems = currentQueue.map { createMediaItem(it) }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = currentQueue,
                currentQueueIndex = startIndex,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                errorMessage = null
            )
        }

        try {
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            onTrackChanged?.invoke(track.id)
        } catch (e: Exception) {
            _playbackState.update {
                it.copy(errorMessage = "Failed to load audio: ${e.localizedMessage}")
            }
        }
    }

    fun playNext(track: AudioTrack) {
        val currentIndex = player.currentMediaItemIndex
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        val newQueue = currentQueue.toMutableList()
        newQueue.add(insertIndex, track)
        currentQueue = newQueue

        player.addMediaItem(insertIndex, createMediaItem(track))
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    fun addToQueue(track: AudioTrack) {
        currentQueue = currentQueue + track
        player.addMediaItem(createMediaItem(track))
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    fun addMultipleToQueue(tracks: List<AudioTrack>) {
        currentQueue = currentQueue + tracks
        val mediaItems = tracks.map { createMediaItem(it) }
        player.addMediaItems(mediaItems)
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    fun removeFromQueue(index: Int) {
        if (index in currentQueue.indices) {
            val newQueue = currentQueue.toMutableList()
            newQueue.removeAt(index)
            currentQueue = newQueue
            player.removeMediaItem(index)
            _playbackState.update { it.copy(queue = currentQueue) }
        }
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_IDLE && currentQueue.isNotEmpty()) {
            val track = _playbackState.value.currentTrack ?: currentQueue.first()
            playTrack(track, currentQueue)
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            ensureServiceRunning()
            player.play()
        }
    }

    fun pause() {
        if (player.isPlaying) {
            player.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (_playbackState.value.repeatMode == PlayerRepeatMode.ALL && currentQueue.isNotEmpty()) {
            player.seekTo(0, 0L)
        }
    }

    fun previous() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffleEnabled = newShuffle) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
    }

    fun playQueueIndex(index: Int) {
        if (index in currentQueue.indices) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    fun startSleepTimer(minutes: Int, finishLastSong: Boolean = false, fadeOutAudio: Boolean = true) {
        stopSleepTimer()
        finishLastSongOnTimer = finishLastSong
        fadeOutAudioOnTimer = fadeOutAudio
        waitingForSongFinish = false

        val totalSeconds = minutes * 60
        _sleepTimerRemainingSeconds.value = totalSeconds

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining

                if (fadeOutAudioOnTimer && remaining <= 30) {
                    val volumeFraction = (remaining / 30f).coerceIn(0.05f, 1.0f)
                    player.volume = volumeFraction
                }
            }

            if (finishLastSongOnTimer) {
                waitingForSongFinish = true
                _sleepTimerRemainingSeconds.value = 0
            } else {
                pause()
                player.volume = 1.0f
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        waitingForSongFinish = false
        player.volume = 1.0f
        _sleepTimerRemainingSeconds.value = null
    }

    fun release() {
        stopProgressTracker()
        stopSleepTimer()
        player.removeListener(playerListener)
        // Note: Do not release shared player here so background service continues seamlessly!
    }
}
