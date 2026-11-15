package com.mtplayer.app.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.mtplayer.app.MTApp
import com.mtplayer.app.R
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.media.LibraryState
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.data.prefs.Settings
import com.mtplayer.app.designsystem.theme.MtTheme
import com.mtplayer.app.designsystem.theme.ThemeMode
import com.mtplayer.app.playback.MediaItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen local video player (ADR-006). Owns its own ExoPlayer + MediaSession so music and
 * video never fight over one player; audio focus pauses music automatically. Handles resume,
 * PiP (auto-enter on 12+, user-leave-hint below), background-audio hand-off and TV remotes.
 */
class VideoPlayerActivity : ComponentActivity() {
    private val container by lazy { (application as MTApp).container }
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var progressJob: Job? = null
    private var settings: Settings = Settings()
    private var videos: List<Video> = emptyList()
    private var externalUri: Uri? = null
    private val subtitleByVideo = HashMap<Long, Uri>()
    private var handedOff = false

    val ui = VideoUiModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate restored=${savedInstanceState != null}")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.addListener(listener)
        player = p
        session = MediaSession.Builder(this, p).setId(SESSION_ID).build()
        ui.player = p

        registerPipReceiver()

        setContent {
            MtTheme(mode = ThemeMode.DARK, pureBlack = true, dynamicColor = false) {
                VideoPlayerScreen(activity = this, model = ui)
            }
        }

        lifecycleScope.launch {
            settings = container.settings.current()
            ui.doubleTapSec = settings.doubleTapSeekSec
            ui.gestures = Triple(settings.gestureBrightness, settings.gestureVolume, settings.gestureSeek)
            ui.backgroundAudio = settings.videoBackgroundAudio
            load(intent, savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress()
        lifecycleScope.launch { load(intent, null) }
    }

    private suspend fun load(intent: Intent, saved: Bundle?) {
        val p = player ?: return
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            externalUri = data
            videos = emptyList()
            val title = data.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.video_player)
            p.setMediaItem(MediaItem.Builder().setUri(data).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build(),
                saved?.getLong(KEY_POSITION) ?: 0L)
            p.prepare(); p.play()
            return
        }
        val startId = saved?.getLong(KEY_VIDEO_ID, -1L)?.takeIf { it >= 0 } ?: intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        val ids = intent.getLongArrayExtra(EXTRA_SIBLINGS)?.toList().orEmpty().ifEmpty { listOf(startId) }
        val index = awaitIndex()
        videos = ids.mapNotNull { index.videoById[it] }
        val startIndex = videos.indexOfFirst { it.id == startId }
        if (startIndex < 0) {
            ui.error = getString(R.string.video_not_found)
            return
        }
        val start = videos[startIndex]
        val resumeAt = saved?.getLong(KEY_POSITION)
            ?: if (settings.resumeVideo) container.userData.videoProgressFor(start.id)?.positionMs ?: 0L else 0L
        p.setMediaItems(videos.map(::itemFor), startIndex, resumeAt)
        p.playbackParameters = PlaybackParameters(settings.defaultVideoSpeed)
        p.prepare()
        p.play()
        applyOrientation(start.width, start.height)
        container.userData.recordPlay(MediaType.VIDEO, start.id)
    }

    private suspend fun awaitIndex() = container.library.let { lib ->
        if (lib.state.value !is LibraryState.Ready) lib.refresh()
        (lib.state.first { it !is LibraryState.Loading } as? LibraryState.Ready)?.index ?: com.mtplayer.app.data.media.LibraryIndex.EMPTY
    }

    private fun itemFor(video: Video): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(video.uri)
            .setMediaId(video.id.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).setDisplayTitle(video.title).build())
        subtitleByVideo[video.id]?.let { builder.setSubtitleConfigurations(listOf(subtitleConfig(it))) }
        return builder.build()
    }

    private fun subtitleConfig(uri: Uri): MediaItem.SubtitleConfiguration {
        val name = (contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "").lowercase()
        val mime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            name.endsWith(".ttml") || name.endsWith(".xml") || name.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage(null)
            .setLabel(name.substringBeforeLast('.').ifBlank { getString(R.string.external_subtitle) })
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
    }

    /** Attaches a user-picked subtitle file (SAF) to the current video and keeps the position. */
    fun attachSubtitle(uri: Uri) {
        val p = player ?: return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val idx = p.currentMediaItemIndex
        val pos = p.currentPosition
        val video = videos.getOrNull(idx)
        val item = if (video != null) {
            subtitleByVideo[video.id] = uri
            itemFor(video)
        } else {
            p.currentMediaItem?.buildUpon()?.setSubtitleConfigurations(listOf(subtitleConfig(uri)))?.build() ?: return
        }
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        p.replaceMediaItem(idx, item)
        p.seekTo(idx, pos)
        p.prepare()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            ui.isPlaying = player.isPlaying
            ui.buffering = player.playbackState == Player.STATE_BUFFERING
            ui.durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            ui.title = player.mediaMetadata.title?.toString().orEmpty()
            ui.hasNext = player.hasNextMediaItem()
            ui.hasPrevious = player.hasPreviousMediaItem()
            ui.speed = player.playbackParameters.speed
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                updatePip()
                if (player.isPlaying) startProgressLoop() else saveProgress()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            ui.error = null
            val video = mediaItem?.mediaId?.toLongOrNull()?.let { id -> videos.firstOrNull { it.id == id } } ?: return
            applyOrientation(video.width, video.height)
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                lifecycleScope.launch {
                    container.userData.recordPlay(MediaType.VIDEO, video.id)
                    if (settings.resumeVideo && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        container.userData.videoProgressFor(video.id)?.positionMs?.takeIf { it > 0 }?.let { player?.seekTo(it) }
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                ui.videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                updatePip()
            }
        }

        override fun onTracksChanged(tracks: Tracks) { ui.tracks = tracks }

        override fun onPlayerError(error: PlaybackException) {
            ui.error = getString(R.string.video_error, error.errorCodeName)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) { delay(5_000); saveProgress() }
        }
    }

    private fun saveProgress() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val pos = p.currentPosition
        container.appScope.launch { container.userData.saveVideoProgress(id, pos, duration) }
    }

    // --- Orientation & system bars --------------------------------------------------------

    private var userOrientation: Int? = null

    private fun applyOrientation(width: Int, height: Int) {
        if (container.deviceClass.isTv || userOrientation != null) return
        if (width <= 0 || height <= 0) return
        requestedOrientation = if (height > width) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun toggleOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val target = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        userOrientation = target
        requestedOrientation = target
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // --- Brightness / volume used by gestures ---------------------------------------------

    fun currentBrightness(): Float {
        val v = window.attributes.screenBrightness
        if (v >= 0f) return v
        return runCatching {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
    }

    // --- Picture in picture ---------------------------------------------------------------

    private val pipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && !container.deviceClass.isTv
    }
    val canPip: Boolean get() = pipSupported

    private fun pipParams(): PictureInPictureParams {
        val aspect = ui.videoAspect.takeIf { it > 0f } ?: (16f / 9f)
        val clamped = aspect.coerceIn(1f / 2.39f, 2.39f)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
            .setActions(pipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(settings.autoPip && !ui.backgroundAudio && player?.isPlaying == true)
            builder.setSeamlessResizeEnabled(true)
        }
        ui.videoBounds?.let { builder.setSourceRectHint(it) }
        return builder.build()
    }

    private fun pipActions(): List<RemoteAction> {
        fun action(icon: Int, title: Int, code: Int) = RemoteAction(
            Icon.createWithResource(this, icon), getString(title), getString(title),
            PendingIntent.getBroadcast(this, code, Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP, code),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val playing = player?.isPlaying == true
        return listOf(
            action(R.drawable.ic_pip_replay, R.string.rewind, PIP_REWIND),
            if (playing) action(R.drawable.ic_pip_pause, R.string.pause, PIP_TOGGLE) else action(R.drawable.ic_pip_play, R.string.play, PIP_TOGGLE),
            action(R.drawable.ic_pip_forward, R.string.forward, PIP_FORWARD),
        )
    }

    private fun updatePip() {
        if (!pipSupported) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    fun enterPip() {
        if (!pipSupported) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && settings.autoPip && player?.isPlaying == true && !ui.backgroundAudio) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.i(TAG, "onPictureInPictureModeChanged $isInPictureInPictureMode")
        ui.inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // PiP window was dismissed: stop like closing the player.
            player?.pause()
            finish()
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val p = player ?: return
            when (intent.getIntExtra(EXTRA_PIP, -1)) {
                PIP_TOGGLE -> if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() }
                PIP_REWIND -> p.seekTo((p.currentPosition - ui.doubleTapSec * 1000L).coerceAtLeast(0))
                PIP_FORWARD -> p.seekTo(p.currentPosition + ui.doubleTapSec * 1000L)
            }
        }
    }

    private fun registerPipReceiver() {
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // The PiP callback is the primary signal; the configuration (windowing mode) and lifecycle
    // hooks keep the UI correct if a platform version delivers it late or not at all.
    private fun syncPipState() {
        val now = isInPictureInPictureMode
        if (ui.inPip != now) {
            Log.i(TAG, "PiP state synced from window: $now")
            ui.inPip = now
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncPipState()
    }

    override fun onResume() {
        super.onResume()
        syncPipState()
    }

    override fun onPause() {
        super.onPause()
        syncPipState()
    }

    // --- Lifecycle ------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        handedOff = false
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        if (isInPictureInPictureMode) return
        if (isChangingConfigurations) return
        val p = player ?: return
        if (ui.backgroundAudio && p.isPlaying && videos.isNotEmpty()) {
            val video = videos.getOrNull(p.currentMediaItemIndex)
            if (video != null) {
                container.player.playVideoAudio(MediaItems.videoAsAudio(video), p.currentPosition, p.playbackParameters.speed)
                handedOff = true
                p.pause()
                finish()
                return
            }
        }
        p.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let { p ->
            p.currentMediaItem?.mediaId?.toLongOrNull()?.let { outState.putLong(KEY_VIDEO_ID, it) }
            outState.putLong(KEY_POSITION, p.currentPosition)
        }
    }

    override fun onDestroy() {
        saveProgress()
        runCatching { unregisterReceiver(pipReceiver) }
        progressJob?.cancel()
        session?.release(); session = null
        player?.removeListener(listener)
        player?.release(); player = null
        ui.player = null
        super.onDestroy()
    }

    // --- TV / keyboard --------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val p = player ?: return super.onKeyDown(keyCode, event)
        if (ui.locked && keyCode != KeyEvent.KEYCODE_BACK) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (!ui.controlsVisible) { ui.showControls(); return true }
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (p.isPlaying) p.pause() else p.play(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> if (!ui.controlsVisible || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                p.seekTo((p.currentPosition - ui.doubleTapSec * 1000L).coerceAtLeast(0)); ui.flash(-ui.doubleTapSec); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!ui.controlsVisible || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                p.seekTo(p.currentPosition + ui.doubleTapSec * 1000L); ui.flash(ui.doubleTapSec); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun setVideoBounds(rect: Rect) { ui.videoBounds = rect }

    companion object {
        private const val TAG = "MtVideo"
        private const val SESSION_ID = "mt_video"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_SIBLINGS = "siblings"
        private const val KEY_VIDEO_ID = "state_video_id"
        private const val KEY_POSITION = "state_position"
        private const val ACTION_PIP = "com.mtplayer.app.video.PIP"
        private const val EXTRA_PIP = "pip"
        private const val PIP_TOGGLE = 1
        private const val PIP_REWIND = 2
        private const val PIP_FORWARD = 3

        fun start(context: Context, videoId: Long, siblingIds: List<Long>) {
            // Cap the playlist so huge libraries don't bloat the Intent (binder limit).
            val ids = if (siblingIds.size > 500) {
                val i = siblingIds.indexOf(videoId).coerceAtLeast(0)
                siblingIds.subList((i - 250).coerceAtLeast(0), (i + 250).coerceAtMost(siblingIds.size))
            } else siblingIds
            context.startActivity(
                Intent(context, VideoPlayerActivity::class.java)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
                    .putExtra(EXTRA_SIBLINGS, ids.ifEmpty { listOf(videoId) }.toLongArray())
                    .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Compose-observable UI state for the video player. */
class VideoUiModel {
    var player: ExoPlayer? by mutableStateOf(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var durationMs by mutableStateOf(0L)
    var title by mutableStateOf("")
    var hasNext by mutableStateOf(false)
    var hasPrevious by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var videoAspect by mutableFloatStateOf(0f)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var error by mutableStateOf<String?>(null)
    var inPip by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var backgroundAudio by mutableStateOf(false)
    var doubleTapSec by mutableStateOf(10)
    var gestures by mutableStateOf(Triple(true, true, true))
    var videoBounds: Rect? = null

    /** Monotonic counter the UI uses to restart its auto-hide timer. */
    var interaction by mutableStateOf(0)
    var seekFlash by mutableStateOf<Pair<Int, Long>?>(null)

    fun showControls() { controlsVisible = true; interaction++ }
    fun flash(seconds: Int) { seekFlash = seconds to System.nanoTime() }
}
