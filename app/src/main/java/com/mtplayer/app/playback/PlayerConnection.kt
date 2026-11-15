package com.mtplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.os.bundleOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mtplayer.app.data.media.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@Immutable
data class NowPlaying(
    val mediaId: String,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: android.net.Uri?,
    val durationMs: Long,
    val isVideo: Boolean,
)

@Immutable
data class QueueEntry(val index: Int, val mediaId: String, val title: String, val artist: String, val artworkUri: android.net.Uri?)

@Immutable
data class PlayerUiState(
    val connected: Boolean = false,
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val sleepEndAt: Long = 0,
    val sleepFinishSong: Boolean = false,
    val error: String? = null,
) {
    val hasMedia: Boolean get() = current != null
}

/**
 * App-side handle to [PlaybackService] through a [MediaController]. Connected while the app is
 * in the foreground (process lifecycle), released in the background so the service can stop
 * itself when nothing is playing.
 */
class PlayerConnection(private val context: Context) : DefaultLifecycleObserver {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    override fun onStart(owner: LifecycleOwner) = connect()
    override fun onStop(owner: LifecycleOwner) = release()

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null || future !== f) { if (c != null) c.release(); return@addListener }
            controller = c
            c.addListener(playerListener)
            refresh()
            val queued = pending.toList()
            pending.clear()
            queued.forEach { it(c) }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(playerListener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Runs [block] now if connected, otherwise after connecting (so taps are never lost). */
    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending += block; connect() }
    }

    // --- Commands -------------------------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) songs.indices.random() else startIndex.coerceIn(0, songs.lastIndex)
            c.setMediaItems(songs.map(MediaItems::song), start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) { playSongs(songs); return@withController }
        val at = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItems(at, songs.map(MediaItems::song))
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) { playSongs(songs); return@withController }
        c.addMediaItems(songs.map(MediaItems::song))
    }

    fun togglePlay() = withController { c ->
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun play() = withController { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() }
    fun pause() = withController { it.pause() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms.coerceAtLeast(0)) }
    fun skipTo(index: Int) = withController { c -> if (index in 0 until c.mediaItemCount) { c.seekToDefaultPosition(index); c.play() } }
    fun removeAt(index: Int) = withController { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }
    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f)) }

    /** Stops playback and clears the queue; the media notification goes away. */
    fun stopAndClear() = withController { c ->
        c.stop()
        c.clearMediaItems()
    }

    fun setSleepTimer(minutes: Int, finishSong: Boolean, fade: Boolean) = withController { c ->
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP_TIMER, Bundle.EMPTY),
            bundleOf(
                PlaybackService.ARG_MINUTES to minutes,
                PlaybackService.ARG_FINISH_SONG to finishSong,
                PlaybackService.ARG_FADE to fade,
            ),
        )
    }

    /** Hands a video's audio to the music service (background audio from the video player). */
    fun playVideoAudio(item: MediaItem, positionMs: Long, speed: Float) = withController { c ->
        c.setMediaItem(item, positionMs)
        c.playbackParameters = PlaybackParameters(speed)
        c.prepare()
        c.play()
    }

    /** Current position, ticking while collected. Collect with lifecycle so it stops off-screen. */
    fun positionTicks(intervalMs: Long = 250): Flow<Pair<Long, Long>> = flow {
        while (true) {
            val c = controller
            if (c != null) emit(c.currentPosition.coerceAtLeast(0) to c.bufferedPosition.coerceAtLeast(0))
            delay(intervalMs)
        }
    }.distinctUntilChanged()

    // --- State ----------------------------------------------------------------------------

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerConnection.controller === controller) {
                this@PlayerConnection.controller = null
                future = null
                _state.value = _state.value.copy(connected = false)
            }
        }
    }

    private var lastTimelineQueue: List<QueueEntry> = emptyList()
    private var lastQueueSignature = 0L

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val extras = c.sessionExtras
        val queue = buildQueue(c)
        _state.value = PlayerUiState(
            connected = true,
            current = item?.let { toNowPlaying(it, c.duration) },
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.mediaItemCount > 0,
            sleepEndAt = extras.getLong(PlaybackService.EXTRA_SLEEP_END_AT, 0),
            sleepFinishSong = extras.getBoolean(PlaybackService.EXTRA_SLEEP_FINISH_SONG, false),
            error = c.playerError?.let { it.errorCodeName },
        )
    }

    private fun buildQueue(c: MediaController): List<QueueEntry> {
        val count = c.mediaItemCount
        // Cheap signature so we only rebuild the list when the playlist actually changed.
        var sig = count.toLong()
        for (i in 0 until count) sig = sig * 31 + c.getMediaItemAt(i).mediaId.hashCode()
        if (sig == lastQueueSignature && lastTimelineQueue.size == count) return lastTimelineQueue
        lastQueueSignature = sig
        lastTimelineQueue = List(count) { i ->
            val m = c.getMediaItemAt(i)
            QueueEntry(i, m.mediaId, m.mediaMetadata.title?.toString().orEmpty(), m.mediaMetadata.artist?.toString().orEmpty(), m.mediaMetadata.artworkUri)
        }
        return lastTimelineQueue
    }

    private fun toNowPlaying(item: MediaItem, duration: Long): NowPlaying {
        val md: MediaMetadata = item.mediaMetadata
        return NowPlaying(
            mediaId = item.mediaId,
            songId = MediaItems.songId(item),
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            artworkUri = md.artworkUri,
            durationMs = if (duration > 0) duration else md.durationMs ?: 0L,
            isVideo = MediaItems.isVideo(item),
        )
    }
}
