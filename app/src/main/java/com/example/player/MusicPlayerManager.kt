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
import com.example.util.CrashLogger
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
    var onTrackChanged: ((String) -> Unit)? = null,
    var onSavePosition: ((songId: String, posMs: Long, durMs: Long) -> Unit)? = null
) {
    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var crossfadeJob: Job? = null

    private var attachedPlayer: ExoPlayer? = null

    val player: ExoPlayer
        get() {
            val current = PlaybackService.getOrCreatePlayer(context)
            if (attachedPlayer !== current) {
                attachToPlayer(current)
            }
            return current
        }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var finishLastSongOnTimer: Boolean = false
    private var fadeOutAudioOnTimer: Boolean = true
    private var waitingForSongFinish: Boolean = false

    private var crossfadeDurationSec: Int = 0
    private var lastPositionPersistAtMs: Long = 0L
    private var currentQueue: List<AudioTrack> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
                updateCurrentPosition()
                persistCurrentTrackPositionNow()
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

            // Smooth crossfade ramp on track transition if enabled and not sleeping-fading
            if (crossfadeDurationSec > 0 &&
                (_sleepTimerRemainingSeconds.value == null || _sleepTimerRemainingSeconds.value!! > 30) &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            ) {
                triggerFadeIn(crossfadeDurationSec)
            }

            val currentIndex = player.currentMediaItemIndex
            val mediaId = mediaItem?.mediaId
            val track = if (mediaId != null) {
                currentQueue.find { it.id == mediaId }
            } else if (currentIndex in currentQueue.indices) {
                currentQueue[currentIndex]
            } else null

            if (track != null) {
                val resolvedIndex = currentQueue.indexOfFirst { it.id == track.id }.let {
                    if (it >= 0) it else currentIndex
                }
                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentQueueIndex = resolvedIndex,
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
            CrashLogger.logNonFatal("MusicPlayerManager", message, error)
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = message
                )
            }

            // Auto-advance to next song in background if one fails to prevent permanent freeze
            scope.launch {
                delay(1200L)
                val p = player
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.prepare()
                    p.play()
                }
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
        val p = PlaybackService.getOrCreatePlayer(context)
        attachToPlayer(p)
        startServiceSilently()
    }

    private fun attachToPlayer(newPlayer: ExoPlayer) {
        attachedPlayer?.removeListener(playerListener)
        newPlayer.addListener(playerListener)
        attachedPlayer = newPlayer
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

    fun ensureServiceRunning() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
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

    fun configureAudioBehavior(
        gapless: Boolean,
        autoPauseOnNoisy: Boolean,
        crossfadeSec: Int
    ) {
        crossfadeDurationSec = crossfadeSec.coerceIn(0, 10)
        val p = player
        p.setHandleAudioBecomingNoisy(autoPauseOnNoisy)
        p.pauseAtEndOfMediaItems = !gapless
    }

    private fun triggerFadeIn(seconds: Int) {
        crossfadeJob?.cancel()
        val durationMs = (seconds * 500L).coerceIn(400L, 2500L)
        val steps = 12
        val stepDelay = durationMs / steps
        crossfadeJob = scope.launch {
            player.volume = 0.15f
            for (i in 1..steps) {
                delay(stepDelay)
                if (_sleepTimerRemainingSeconds.value?.let { it <= 30 } == true) break
                val fraction = 0.15f + (0.85f * (i / steps.toFloat()))
                player.volume = fraction.coerceIn(0.15f, 1.0f)
            }
        }
    }

    private fun restoreExistingState() {
        val p = attachedPlayer ?: return
        val isPlaying = p.isPlaying
        val duration = p.duration.coerceAtLeast(0L)
        val position = p.currentPosition.coerceAtLeast(0L)
        val repeat = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
            else -> PlayerRepeatMode.OFF
        }

        _playbackState.update {
            it.copy(
                isPlaying = isPlaying,
                durationMs = duration,
                currentPositionMs = position,
                isShuffleEnabled = p.shuffleModeEnabled,
                repeatMode = repeat
            )
        }

        if (isPlaying) {
            startProgressTracker()
        }
    }

    fun restoreLastTrack(track: AudioTrack, positionMs: Long) {
        if (currentQueue.isNotEmpty() || _playbackState.value.currentTrack != null) return
        currentQueue = listOf(track)
        val safePos = positionMs.coerceIn(0L, track.durationMs.coerceAtLeast(positionMs))
        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = currentQueue,
                currentQueueIndex = 0,
                currentPositionMs = safePos,
                durationMs = track.durationMs,
                isPlaying = false,
                errorMessage = null
            )
        }
        try {
            val p = player
            p.setMediaItems(listOf(createMediaItem(track)), 0, safePos)
            p.prepare()
            p.pause()
        } catch (_: Exception) {
            // Keep UI state restored even if URI needs user action on play
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateCurrentPosition()
                val now = System.currentTimeMillis()
                if (now - lastPositionPersistAtMs >= 5000L) {
                    lastPositionPersistAtMs = now
                    persistCurrentTrackPositionNow()
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateCurrentPosition() {
        val p = player
        val pos = p.currentPosition.coerceAtLeast(0L)
        val dur = p.duration.let { if (it > 0) it else _playbackState.value.durationMs }
        val buf = p.bufferedPosition.coerceAtLeast(0L)
        _playbackState.update {
            it.copy(
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf
            )
        }
    }

    private fun persistCurrentTrackPositionNow() {
        val st = _playbackState.value
        val track = st.currentTrack ?: return
        if (st.durationMs > 0L) {
            onSavePosition?.invoke(track.id, st.currentPositionMs, st.durationMs)
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
            val p = player
            p.volume = 1.0f
            p.setMediaItems(mediaItems, startIndex, 0L)
            p.prepare()
            p.play()
            onTrackChanged?.invoke(track.id)
        } catch (e: Exception) {
            CrashLogger.logNonFatal("MusicPlayerManager", "Failed to load audio: ${track.title}", e)
            _playbackState.update {
                it.copy(errorMessage = "Failed to load audio: ${e.localizedMessage}")
            }
        }
    }

    fun playNext(track: AudioTrack) {
        val p = player
        val currentIndex = p.currentMediaItemIndex
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        val newQueue = currentQueue.toMutableList()
        newQueue.add(insertIndex, track)
        currentQueue = newQueue

        p.addMediaItem(insertIndex, createMediaItem(track))
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
            val newIndex = player.currentMediaItemIndex.coerceIn(-1, (currentQueue.size - 1).coerceAtLeast(-1))
            _playbackState.update {
                it.copy(
                    queue = currentQueue,
                    currentQueueIndex = newIndex,
                    currentTrack = currentQueue.getOrNull(newIndex) ?: it.currentTrack
                )
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val mutable = currentQueue.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        currentQueue = mutable
        val p = player
        p.moveMediaItem(fromIndex, toIndex)
        val currentId = _playbackState.value.currentTrack?.id
        val newIndex = if (currentId != null) {
            currentQueue.indexOfFirst { it.id == currentId }
        } else {
            p.currentMediaItemIndex
        }
        _playbackState.update {
            it.copy(
                queue = currentQueue,
                currentQueueIndex = newIndex
            )
        }
    }

    fun clearQueue() {
        stopProgressTracker()
        crossfadeJob?.cancel()
        val p = player
        p.stop()
        p.clearMediaItems()
        currentQueue = emptyList()
        _playbackState.value = PlaybackState(
            isShuffleEnabled = _playbackState.value.isShuffleEnabled,
            repeatMode = _playbackState.value.repeatMode
        )
    }

    fun togglePlayPause() {
        val p = player
        if (p.playbackState == Player.STATE_IDLE && currentQueue.isNotEmpty()) {
            val track = _playbackState.value.currentTrack ?: currentQueue.first()
            playTrack(track, currentQueue)
            return
        }
        if (p.isPlaying) {
            p.pause()
        } else {
            ensureServiceRunning()
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.play()
        }
    }

    fun pause() {
        val p = player
        if (p.isPlaying) {
            p.pause()
        }
        persistCurrentTrackPositionNow()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        _playbackState.update { it.copy(currentPositionMs = clamped) }
        val track = _playbackState.value.currentTrack
        val dur = _playbackState.value.durationMs
        if (track != null && dur > 0L) {
            onSavePosition?.invoke(track.id, clamped, dur)
        }
    }

    fun next() {
        val p = player
        ensureServiceRunning()
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.play()
        } else if (_playbackState.value.repeatMode == PlayerRepeatMode.ALL && currentQueue.isNotEmpty()) {
            p.seekTo(0, 0L)
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.play()
        }
    }

    fun previous() {
        val p = player
        ensureServiceRunning()
        if (p.currentPosition > 3000L) {
            p.seekTo(0L)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.play()
        } else {
            p.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val p = player
        val newShuffle = !p.shuffleModeEnabled
        p.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffleEnabled = newShuffle) }
    }

    fun cycleRepeatMode() {
        val p = player
        val nextMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = nextMode
    }

    fun playQueueIndex(index: Int) {
        if (index in currentQueue.indices) {
            ensureServiceRunning()
            val p = player
            p.seekTo(index, 0L)
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.play()
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
        crossfadeJob?.cancel()
        stopSleepTimer()
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = null
    }
}
