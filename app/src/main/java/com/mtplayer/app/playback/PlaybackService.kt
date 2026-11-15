package com.mtplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mtplayer.app.MTApp
import com.mtplayer.app.R
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.db.QueueStateEntity
import com.mtplayer.app.data.media.LibraryState
import com.mtplayer.app.feature.shell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Music playback service (ADR-003). Owns the single music [ExoPlayer] and [MediaLibrarySession].
 * The UI talks to it only through a MediaController, which is what makes Media3 post and
 * update the media notification and lock-screen controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val container by lazy { (application as MTApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    private var persistJob: Job? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepEndAt = 0L
    private var sleepFinishSong = false
    private var lastRecordedId: String? = null
    private var currentIsFavorite = false
    private var restored = false
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(playerListener)

        val openPlayer = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, openPlayer, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, player, callback)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL)
                .setChannelName(R.string.notification_channel_playback)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_notification) }
        )

        scope.launch {
            container.settings.settings.collect { s ->
                player.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
                container.audioEffects.apply(s)
            }
        }
        scope.launch {
            container.userData.favoriteSongIds.distinctUntilChanged().collect { ids ->
                val fav = MediaItems.songId(player.currentMediaItem)?.let { it in ids } ?: false
                if (fav != currentIsFavorite) { currentIsFavorite = fav; updateButtons() }
                favoriteIds = ids.toHashSet()
            }
        }
        updateButtons()
        scope.launch { restoreQueue(playWhenReady = false) }
    }

    private var favoriteIds: Set<Long> = emptySet()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistNow()
        // Keep playing in the background if music is playing; otherwise go away cleanly.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        persistNow()
        sleepJob?.cancel()
        session?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        session = null
        container.audioEffects.release()
        scope.cancel()
        super.onDestroy()
    }

    // --- Player events --------------------------------------------------------------------

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentIsFavorite = MediaItems.songId(mediaItem)?.let { it in favoriteIds } ?: false
            updateButtons()
            if (player.isPlaying) recordPlay()
            schedulePersist()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                recordPlay()
                startTicker()
            } else {
                tickerJob?.cancel()
                schedulePersist(0)
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) schedulePersist()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateButtons(); schedulePersist() }
        override fun onRepeatModeChanged(repeatMode: Int) { updateButtons(); schedulePersist() }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.audioEffects.attach(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Skip unplayable files instead of stopping the whole queue, but never loop forever
            // when every file in a repeating queue is broken.
            consecutiveErrors++
            if (consecutiveErrors < MAX_SKIPS && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && sleepFinishSong) finishSleep()
        }
    }

    private fun recordPlay() {
        val item = player.currentMediaItem ?: return
        if (item.mediaId == lastRecordedId) return
        lastRecordedId = item.mediaId
        val songId = MediaItems.songId(item) ?: return
        scope.launch { container.userData.recordPlay(MediaType.SONG, songId) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(15_000)
                persistPosition()
            }
        }
    }

    // --- Queue persistence (resume after process death) ------------------------------------

    private fun hasVideoItems(): Boolean = (0 until player.mediaItemCount).any { MediaItems.isVideo(player.getMediaItemAt(it)) }

    private fun schedulePersist(delayMs: Long = 800) {
        if (!restored) return
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(delayMs)
            persistQueue()
        }
    }

    private suspend fun persistQueue() {
        if (hasVideoItems()) return
        val ids = (0 until player.mediaItemCount).mapNotNull { MediaItems.songId(player.getMediaItemAt(it)) }
        val state = QueueStateEntity(
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            updatedAt = System.currentTimeMillis(),
        )
        withContext(NonCancellable + Dispatchers.IO) { container.db.queue().replace(ids, state) }
    }

    private suspend fun persistPosition() {
        if (!restored || hasVideoItems() || player.mediaItemCount == 0) return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0)
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode
        withContext(Dispatchers.IO) {
            container.db.queue().updatePosition(index, pos, shuffle, repeat, System.currentTimeMillis())
        }
    }

    /** Synchronous save used from onTaskRemoved/onDestroy, bounded so it can never cause an ANR. */
    private fun persistNow() {
        if (!restored || !::player.isInitialized) return
        persistJob?.cancel()
        runBlocking { withTimeoutOrNull(1_500) { persistQueue() } }
    }

    private suspend fun loadSavedQueue(): MediaSession.MediaItemsWithStartPosition? {
        val library = container.library
        if (library.state.value !is LibraryState.Ready) library.refresh()
        val index = library.index
        val (ids, state) = withContext(Dispatchers.IO) { container.db.queue().songIds() to container.db.queue().state() }
        if (ids.isEmpty() || state == null) return null
        val wanted = ids.getOrNull(state.currentIndex)
        val songs = ids.mapNotNull { index.songById[it] }
        if (songs.isEmpty()) return null
        val start = songs.indexOfFirst { it.id == wanted }
        return MediaSession.MediaItemsWithStartPosition(
            songs.map(MediaItems::song),
            if (start >= 0) start else 0,
            if (start >= 0) state.positionMs else 0L,
        ).also {
            player.shuffleModeEnabled = state.shuffle
            player.repeatMode = state.repeatMode
        }
    }

    private suspend fun restoreQueue(playWhenReady: Boolean) {
        try {
            if (player.mediaItemCount > 0) return
            val saved = loadSavedQueue() ?: return
            if (player.mediaItemCount > 0) return
            player.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
            player.prepare()
            player.playWhenReady = playWhenReady
        } finally {
            restored = true
        }
    }

    // --- Sleep timer ----------------------------------------------------------------------

    private fun startSleep(minutes: Int, finishSong: Boolean, fade: Boolean) {
        sleepJob?.cancel()
        player.volume = 1f
        if (minutes <= 0) {
            sleepEndAt = 0; sleepFinishSong = false
            publishExtras()
            return
        }
        sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
        sleepFinishSong = false
        publishExtras()
        sleepJob = scope.launch {
            val fadeMs = if (fade) 15_000L else 0L
            delay((sleepEndAt - System.currentTimeMillis() - fadeMs).coerceAtLeast(0))
            if (finishSong && player.isPlaying) {
                sleepFinishSong = true
                player.pauseAtEndOfMediaItems = true
                publishExtras()
                return@launch
            }
            if (fadeMs > 0) {
                val steps = 30
                repeat(steps) { i ->
                    player.volume = 1f - (i + 1) / steps.toFloat()
                    delay(fadeMs / steps)
                }
            }
            finishSleep()
        }
    }

    private fun finishSleep() {
        player.pause()
        player.pauseAtEndOfMediaItems = false
        player.volume = 1f
        sleepEndAt = 0
        sleepFinishSong = false
        publishExtras()
    }

    private fun publishExtras() {
        session?.setSessionExtras(Bundle().apply {
            putLong(EXTRA_SLEEP_END_AT, sleepEndAt)
            putBoolean(EXTRA_SLEEP_FINISH_SONG, sleepFinishSong)
        })
    }

    // --- Notification buttons -------------------------------------------------------------

    private fun buttons(): List<CommandButton> {
        val favorite = CommandButton.Builder(if (currentIsFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (currentIsFavorite) R.string.action_unfavorite else R.string.action_favorite))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setEnabled(MediaItems.songId(player.currentMediaItem) != null)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build()
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }
        val repeat = CommandButton.Builder(repeatIcon)
            .setDisplayName(getString(R.string.action_repeat))
            .setSessionCommand(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build()
        val shuffle = CommandButton.Builder(if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(getString(R.string.action_shuffle))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(favorite, repeat, shuffle)
    }

    private fun updateButtons() {
        session?.setMediaButtonPreferences(buttons())
    }

    // --- Session callback -----------------------------------------------------------------

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(buttons())
                .setSessionExtras(Bundle().apply {
                    putLong(EXTRA_SLEEP_END_AT, sleepEndAt)
                    putBoolean(EXTRA_SLEEP_FINISH_SONG, sleepFinishSong)
                })
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_TOGGLE_FAVORITE -> MediaItems.songId(player.currentMediaItem)?.let { id ->
                    scope.launch { container.userData.toggleFavorite(MediaType.SONG, id) }
                }
                CMD_CYCLE_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                CMD_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CMD_SLEEP_TIMER -> startSleep(
                    args.getInt(ARG_MINUTES, 0),
                    args.getBoolean(ARG_FINISH_SONG, false),
                    args.getBoolean(ARG_FADE, true),
                )
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Resolves bare media ids (e.g. from Bluetooth, Assistant or Android Auto) into playable items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val index = ensureLibrary()
            mediaItems.mapNotNull { item ->
                if (item.localConfiguration != null) item
                else item.mediaId.toLongOrNull()?.let { index.songById[it] }?.let(MediaItems::song)
            }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            loadSavedQueue() ?: throw UnsupportedOperationException("Nothing to resume")
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(MediaItems.browsable(ROOT, getString(R.string.app_name)), params)
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val song = mediaId.toLongOrNull()?.let { ensureLibrary().songById[it] }
            if (song != null) LibraryResult.ofItem(MediaItems.song(song), null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val index = ensureLibrary()
            val children: List<MediaItem> = when {
                parentId == ROOT -> listOf(
                    MediaItems.browsable(NODE_SONGS, getString(R.string.tab_songs), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                    MediaItems.browsable(NODE_ALBUMS, getString(R.string.tab_albums), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    MediaItems.browsable(NODE_FAVORITES, getString(R.string.favorites), mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST),
                )
                parentId == NODE_SONGS -> index.songs.map(MediaItems::song)
                parentId == NODE_ALBUMS -> index.albums.map {
                    MediaItems.browsable(NODE_ALBUM_PREFIX + it.id, it.title, it.artworkUri, MediaMetadata.MEDIA_TYPE_ALBUM)
                }
                parentId == NODE_FAVORITES -> container.userData.favoriteSongIds.first().mapNotNull { index.songById[it] }.map(MediaItems::song)
                parentId.startsWith(NODE_ALBUM_PREFIX) ->
                    index.album(parentId.removePrefix(NODE_ALBUM_PREFIX).toLongOrNull() ?: -1)?.songs.orEmpty().map(MediaItems::song)
                else -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(children.size)
            val to = (from + pageSize).coerceAtMost(children.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params)
        }
    }

    private suspend fun ensureLibrary() = container.library.let {
        if (it.state.value !is LibraryState.Ready) it.refresh()
        it.index
    }

    companion object {
        const val SESSION_ID = "mt.music"
        const val NOTIFICATION_CHANNEL = "playback"
        const val CMD_TOGGLE_FAVORITE = "mt.favorite"
        const val CMD_CYCLE_REPEAT = "mt.repeat"
        const val CMD_TOGGLE_SHUFFLE = "mt.shuffle"
        const val CMD_SLEEP_TIMER = "mt.sleep"
        const val ARG_MINUTES = "minutes"
        const val ARG_FINISH_SONG = "finish_song"
        const val ARG_FADE = "fade"
        const val EXTRA_SLEEP_END_AT = "sleep_end_at"
        const val EXTRA_SLEEP_FINISH_SONG = "sleep_finish_song"
        private const val MAX_SKIPS = 5
        private const val ROOT = "root"
        private const val NODE_SONGS = "songs"
        private const val NODE_ALBUMS = "albums"
        private const val NODE_FAVORITES = "favorites"
        private const val NODE_ALBUM_PREFIX = "album:"
    }
}
