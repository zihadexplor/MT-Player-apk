package com.example.ui.screens.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItem
import com.example.util.SubtitleCue
import com.example.util.SubtitleParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerModal(
    video: VideoItem,
    onClose: () -> Unit,
    onProgressUpdate: (String, Long, Long) -> Unit,
    defaultSpeed: Float = 1.0f,
    resumePlayback: Boolean = true,
    doubleTapSeekSeconds: Int = 10,
    gestureBrightnessEnabled: Boolean = true,
    gestureVolumeEnabled: Boolean = true,
    gestureScrubEnabled: Boolean = true,
    hardwareAcceleration: Boolean = true,
    videoBackgroundAudio: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val safeMaxVolume = if (maxVolume > 0) maxVolume else 1

    val initialPositionMs = if (resumePlayback) video.playbackPositionMs else 0L
    val seekDeltaMs = (doubleTapSeekSeconds.coerceAtLeast(1)) * 1000L

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(if (video.durationMs > 0) video.durationMs else 1L) }
    var isBuffering by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Audio & Video Formats
    var activeVideoFormat by remember { mutableStateOf<Format?>(null) }
    var activeAudioFormat by remember { mutableStateOf<Format?>(null) }

    // Screen adjustments
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentSpeed by remember(video.id, defaultSpeed) { mutableFloatStateOf(defaultSpeed) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLockedUnlockPillVisible by remember { mutableStateOf(false) }
    var orientationMode by remember { mutableStateOf(ScreenOrientationOption.LANDSCAPE) }
    var isInPipMode by remember { mutableStateOf(false) }

    // Audio tracks & Subtitles state
    var audioTracksList by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var subtitleTracksList by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }
    var externalSubtitleName by remember { mutableStateOf<String?>(null) }
    var externalSubtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var useExternalSubtitles by remember { mutableStateOf(false) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }

    // HUD States & Auto-hide Jobs
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }

    var isBrightnessHUDVisible by remember { mutableStateOf(false) }
    var currentBrightness by remember {
        val initialWin = activity?.window?.attributes?.screenBrightness ?: -1.0f
        mutableFloatStateOf(if (initialWin > 0f) initialWin else 0.5f)
    }

    var isVolumeHUDVisible by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var currentVolumeRatio by remember {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf((currentVol.toFloat() / safeMaxVolume.toFloat()).coerceIn(0f, 1f))
    }

    // Listen for physical volume button presses and system volume changes
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        currentVolumeLevel = currentVol
                        currentVolumeRatio = (currentVol.toFloat() / safeMaxVolume.toFloat()).coerceIn(0f, 1f)
                        isVolumeHUDVisible = true
                        volumeHideJob?.cancel()
                        volumeHideJob = coroutineScope.launch {
                            delay(1200L)
                            isVolumeHUDVisible = false
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    var isScrubHUDVisible by remember { mutableStateOf(false) }
    var scrubTargetTimeMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var scrubStartTimeMs by remember { mutableLongStateOf(0L) }

    var isDoubleTapRewindVisible by remember { mutableStateOf(false) }
    var isDoubleTapForwardVisible by remember { mutableStateOf(false) }

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    var showResumePill by remember { mutableStateOf(resumePlayback && video.playbackPositionMs > 5000L) }

    // Dialog sheets
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showVideoInfoDialog by remember { mutableStateOf(false) }

    val isPipSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun showToast(msg: String) {
        toastMessage = msg
        isToastVisible = true
        coroutineScope.launch {
            delay(2200L)
            isToastVisible = false
        }
    }

    // Initialize ExoPlayer with hardware/decoder fallback configuration & Media audio attributes
    val exoPlayer = remember(video.id, hardwareAcceleration) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (hardwareAcceleration) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )
            setEnableDecoderFallback(true)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(video.uri))
                setMediaItem(mediaItem)
                playbackParameters = PlaybackParameters(defaultSpeed)
                prepare()
                if (resumePlayback && video.playbackPositionMs > 0L) {
                    seekTo(video.playbackPositionMs)
                }
                playWhenReady = true
            }
    }

    // Pause video cleanly on background stop unless Background Audio or PiP is active
    val latestVideoBackgroundAudio by rememberUpdatedState(videoBackgroundAudio)
    val latestIsInPip by rememberUpdatedState(isInPipMode)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !latestVideoBackgroundAudio && !latestIsInPip) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // External Subtitle Picker (.srt / .vtt parsed with real-time sync delay support)
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle"
            coroutineScope.launch {
                val cues = SubtitleParser.loadFromUri(context, uri)
                if (cues.isNotEmpty()) {
                    externalSubtitleName = filename
                    externalSubtitleCues = cues
                    useExternalSubtitles = true
                    isSubtitlesDisabled = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showToast("Loaded $filename (${cues.size} cues)")
                } else {
                    showToast("No readable subtitle cues found in $filename")
                }
            }
        }
    }

    // Function to parse current tracks
    fun updateTracksList(tracks: Tracks) {
        val audios = mutableListOf<AudioTrackInfo>()
        val subs = mutableListOf<SubtitleTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audios.add(
                        AudioTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language,
                            label = format.label,
                            channels = format.channelCount,
                            sampleRate = format.sampleRate,
                            isSelected = isSelected
                        )
                    )
                    if (isSelected) {
                        activeAudioFormat = format
                    }
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subs.add(
                        SubtitleTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language,
                            label = format.label,
                            isSelected = isSelected
                        )
                    )
                } else if (trackType == C.TRACK_TYPE_VIDEO && isSelected) {
                    activeVideoFormat = format
                }
            }
        }
        audioTracksList = audios
        subtitleTracksList = subs
    }

    // Activity window and orientation lifecycle
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val originalBrightness = activity?.window?.attributes?.screenBrightness ?: -1.0f

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        activity?.window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        val compActivity = activity as? ComponentActivity
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        compActivity?.addOnPictureInPictureModeChangedListener(pipListener)

        onDispose {
            compActivity?.removeOnPictureInPictureModeChangedListener(pipListener)
            activity?.requestedOrientation = originalOrientation
            activity?.window?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                win.attributes = win.attributes.apply {
                    screenBrightness = originalBrightness
                }
            }
        }
    }

    // ExoPlayer listener lifecycle
    val currentOnProgressUpdate by rememberUpdatedState(onProgressUpdate)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                    if (exoPlayer.duration > 0) {
                        durationMs = exoPlayer.duration
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksList(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "Playback error (${error.errorCodeName})"
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            val finalPos = exoPlayer.currentPosition
            val finalDur = exoPlayer.duration
            currentOnProgressUpdate(video.id, finalPos, finalDur)
            exoPlayer.release()
        }
    }

    // Progress updates ticker (higher tick frequency when external subtitles are rendered)
    LaunchedEffect(isPlaying, isUserSeeking, useExternalSubtitles) {
        val tickInterval = if (useExternalSubtitles) 150L else 500L
        var saveTick = 0
        while (true) {
            if (isPlaying && !isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
                if (useExternalSubtitles) {
                    if (saveTick % 3 == 0) {
                        currentOnProgressUpdate(video.id, currentPositionMs, durationMs)
                    }
                    saveTick++
                } else {
                    currentOnProgressUpdate(video.id, currentPositionMs, durationMs)
                }
            }
            delay(tickInterval)
        }
    }

    // Auto-hide controls timer (3.5 seconds)
    val isAnyDialogOpen = showSpeedDialog || showAspectRatioDialog || showAudioTrackDialog || showSubtitleDialog || showVideoInfoDialog || playerError != null
    LaunchedEffect(isControlsVisible, isPlaying, isAnyDialogOpen, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isAnyDialogOpen && !isScreenLocked) {
            delay(3500L)
            isControlsVisible = false
        }
    }

    // Dismiss Resume Pill after 6 seconds
    LaunchedEffect(showResumePill) {
        if (showResumePill) {
            delay(6000L)
            showResumePill = false
        }
    }

    BackHandler {
        if (isScreenLocked) {
            isLockedUnlockPillVisible = true
            coroutineScope.launch {
                delay(3000L)
                isLockedUnlockPillVisible = false
            }
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_root")
    ) {
        // Video Surface via Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay Layer
        if (!isInPipMode) {
            VideoGestureOverlay(
                isScreenLocked = isScreenLocked,
                onSingleTap = {
                    isControlsVisible = !isControlsVisible
                },
                onDoubleTapLeft = {
                    val target = (exoPlayer.currentPosition - seekDeltaMs).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    isDoubleTapRewindVisible = true
                    coroutineScope.launch {
                        delay(650L)
                        isDoubleTapRewindVisible = false
                    }
                },
                onDoubleTapRight = {
                    val target = (exoPlayer.currentPosition + seekDeltaMs).coerceAtMost(durationMs)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    isDoubleTapForwardVisible = true
                    coroutineScope.launch {
                        delay(650L)
                        isDoubleTapForwardVisible = false
                    }
                },
                onDoubleTapCenter = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onScrubStart = {
                    if (gestureScrubEnabled) {
                        scrubStartTimeMs = exoPlayer.currentPosition
                        scrubTargetTimeMs = scrubStartTimeMs
                        scrubDeltaMs = 0L
                        isScrubHUDVisible = true
                    }
                },
                onScrubDelta = { deltaX ->
                    if (gestureScrubEnabled) {
                        val deltaMs = (deltaX * 120f).toLong()
                        val newDelta = scrubDeltaMs + deltaMs
                        val newTarget = (scrubStartTimeMs + newDelta).coerceIn(0L, durationMs)
                        scrubDeltaMs = newTarget - scrubStartTimeMs
                        scrubTargetTimeMs = newTarget
                    }
                },
                onScrubCommit = {
                    if (gestureScrubEnabled) {
                        exoPlayer.seekTo(scrubTargetTimeMs)
                        currentPositionMs = scrubTargetTimeMs
                    }
                },
                onBrightnessDelta = { delta ->
                    if (gestureBrightnessEnabled) {
                        val newBrightness = (currentBrightness + delta).coerceIn(0.02f, 1.0f)
                        currentBrightness = newBrightness
                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                            screenBrightness = newBrightness
                        }
                        isBrightnessHUDVisible = true
                        brightnessHideJob?.cancel()
                    }
                },
                onVolumeDelta = { delta ->
                    if (gestureVolumeEnabled) {
                        val newRatio = (currentVolumeRatio + delta * 1.25f).coerceIn(0f, 1f)
                        currentVolumeRatio = newRatio
                        val targetVol = (newRatio * safeMaxVolume).roundToInt().coerceIn(0, safeMaxVolume)
                        if (targetVol != currentVolumeLevel) {
                            currentVolumeLevel = targetVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        }
                        isVolumeHUDVisible = true
                        volumeHideJob?.cancel()
                    }
                },
                onGestureEnd = {
                    volumeHideJob?.cancel()
                    volumeHideJob = coroutineScope.launch {
                        delay(1200L)
                        isVolumeHUDVisible = false
                    }
                    brightnessHideJob?.cancel()
                    brightnessHideJob = coroutineScope.launch {
                        delay(1200L)
                        isBrightnessHUDVisible = false
                    }
                    coroutineScope.launch {
                        delay(900L)
                        isScrubHUDVisible = false
                    }
                },
                onLockedTap = {
                    isLockedUnlockPillVisible = true
                    coroutineScope.launch {
                        delay(3000L)
                        isLockedUnlockPillVisible = false
                    }
                }
            )
        }

        // Synchronized External Subtitle Caption Overlay (.srt / .vtt)
        if (useExternalSubtitles && !isSubtitlesDisabled && externalSubtitleCues.isNotEmpty()) {
            val activeCue = remember(externalSubtitleCues, currentPositionMs, subtitleDelayMs) {
                SubtitleParser.findActiveCueText(externalSubtitleCues, currentPositionMs, subtitleDelayMs)
            }
            if (!activeCue.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            horizontal = 24.dp,
                            vertical = if (isControlsVisible && !isScreenLocked && !isInPipMode) 96.dp else 28.dp
                        )
                        .testTag("external_subtitle_overlay")
                ) {
                    Text(
                        text = activeCue,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Buffering Indicator
        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }

        // On-screen HUD Overlays
        BrightnessHUD(
            brightness = currentBrightness,
            isVisible = isBrightnessHUDVisible,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        )

        VolumeHUD(
            volumeLevel = currentVolumeLevel,
            maxVolume = safeMaxVolume,
            volumeRatio = currentVolumeRatio,
            isVisible = isVolumeHUDVisible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        )

        ScrubSeekHUD(
            targetTimeMs = scrubTargetTimeMs,
            durationMs = durationMs,
            deltaMs = scrubDeltaMs,
            isVisible = isScrubHUDVisible,
            modifier = Modifier.align(Alignment.Center)
        )

        DoubleTapFeedback(
            isForward = false,
            isVisible = isDoubleTapRewindVisible,
            seekSeconds = doubleTapSeekSeconds,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp)
        )

        DoubleTapFeedback(
            isForward = true,
            isVisible = isDoubleTapForwardVisible,
            seekSeconds = doubleTapSeekSeconds,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 64.dp)
        )

        ToastHUD(
            message = toastMessage,
            isVisible = isToastVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        )

        // Resume notice pill
        if (showResumePill && !isScreenLocked && !isInPipMode) {
            ResumePill(
                playbackPositionMs = video.playbackPositionMs,
                onRestart = {
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                    showResumePill = false
                    showToast("Restarted from beginning")
                },
                onDismiss = { showResumePill = false },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 90.dp)
            )
        }

        // Locked Unlock Button
        LockedFloatingUnlockButton(
            isVisible = isScreenLocked && isLockedUnlockPillVisible,
            onUnlock = {
                isScreenLocked = false
                isLockedUnlockPillVisible = false
                isControlsVisible = true
                showToast("Screen Unlocked")
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 24.dp)
        )

        // Overlay Playback Controls (fades in/out)
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.80f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                VideoPlayerTopBar(
                    video = video,
                    currentSpeed = currentSpeed,
                    isPipSupported = isPipSupported,
                    onBackClick = onClose,
                    onToggleRotation = {
                        orientationMode = when (orientationMode) {
                            ScreenOrientationOption.LANDSCAPE -> ScreenOrientationOption.PORTRAIT
                            ScreenOrientationOption.PORTRAIT -> ScreenOrientationOption.SENSOR
                            ScreenOrientationOption.SENSOR -> ScreenOrientationOption.LANDSCAPE
                        }
                        activity?.requestedOrientation = when (orientationMode) {
                            ScreenOrientationOption.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            ScreenOrientationOption.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            ScreenOrientationOption.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        }
                        showToast("Orientation: ${orientationMode.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    },
                    onAudioTrackClick = { showAudioTrackDialog = true },
                    onSubtitleClick = { showSubtitleDialog = true },
                    onSpeedClick = { showSpeedDialog = true },
                    onAspectRatioClick = { showAspectRatioDialog = true },
                    onPipClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val rational = try {
                                if (video.width > 0 && video.height > 0) {
                                    val r = Rational(video.width, video.height)
                                    if (r.toFloat() in 0.41841f..2.39f) r else null
                                } else null
                            } catch (_: Exception) { null }

                            val pipBuilder = PictureInPictureParams.Builder()
                            if (rational != null) {
                                pipBuilder.setAspectRatio(rational)
                            }
                            isControlsVisible = false
                            activity?.enterPictureInPictureMode(pipBuilder.build())
                        }
                    },
                    onInfoClick = { showVideoInfoDialog = true },
                    onLockClick = {
                        isScreenLocked = true
                        isControlsVisible = false
                        isLockedUnlockPillVisible = true
                        coroutineScope.launch {
                            delay(3000L)
                            isLockedUnlockPillVisible = false
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                VideoPlayerCenterControls(
                    isPlaying = isPlaying,
                    seekSeconds = doubleTapSeekSeconds,
                    onRewind10s = {
                        val target = (exoPlayer.currentPosition - seekDeltaMs).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    },
                    onPlayPause = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onForward10s = {
                        val target = (exoPlayer.currentPosition + seekDeltaMs).coerceAtMost(durationMs)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                VideoPlayerBottomBar(
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    sliderProgress = sliderProgress,
                    isUserSeeking = isUserSeeking,
                    onSeekProgressChange = { newProgress ->
                        isUserSeeking = true
                        sliderProgress = newProgress
                    },
                    onSeekProgressFinish = {
                        val seekPos = (sliderProgress * durationMs).toLong()
                        exoPlayer.seekTo(seekPos)
                        currentPositionMs = seekPos
                        isUserSeeking = false
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Unsupported Format / Playback Error Banner
        playerError?.let { err ->
            PlaybackErrorOverlay(
                errorMessage = err,
                onRetry = {
                    playerError = null
                    val pos = exoPlayer.currentPosition
                    exoPlayer.prepare()
                    exoPlayer.seekTo(pos)
                    exoPlayer.play()
                },
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Dialogs & Modals
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = currentSpeed,
                onSpeedSelect = { speed ->
                    currentSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                    showToast("Speed: ${speed}x")
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        if (showAspectRatioDialog) {
            AspectRatioDialog(
                currentResizeMode = resizeMode,
                onResizeModeSelect = { mode, label ->
                    resizeMode = mode
                    showToast("Aspect: $label")
                },
                onDismiss = { showAspectRatioDialog = false }
            )
        }

        if (showAudioTrackDialog) {
            AudioTrackDialog(
                audioTracks = audioTracksList,
                onSelectTrack = { groupIndex, trackIndex ->
                    val tracks = exoPlayer.currentTracks
                    if (groupIndex in 0 until tracks.groups.size) {
                        val group = tracks.groups[groupIndex]
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                        showToast("Switched audio track")
                    }
                },
                onDismiss = { showAudioTrackDialog = false }
            )
        }

        if (showSubtitleDialog) {
            SubtitleOptionsDialog(
                subtitleTracks = subtitleTracksList,
                isSubtitlesDisabled = isSubtitlesDisabled,
                externalSubtitleName = externalSubtitleName,
                subtitleDelayMs = subtitleDelayMs,
                onPickExternalFile = {
                    subtitlePickerLauncher.launch(
                        arrayOf("*/*", "text/plain", "application/x-subrip", "text/vtt")
                    )
                },
                onSelectInternalTrack = { groupIndex, trackIndex ->
                    isSubtitlesDisabled = false
                    useExternalSubtitles = false
                    val tracks = exoPlayer.currentTracks
                    if (groupIndex in 0 until tracks.groups.size) {
                        val group = tracks.groups[groupIndex]
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(override)
                            .build()
                        showToast("Selected subtitle track")
                    }
                },
                onDisableSubtitles = {
                    isSubtitlesDisabled = true
                    useExternalSubtitles = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showToast("Subtitles turned off")
                },
                onAdjustDelay = { newDelayMs ->
                    subtitleDelayMs = newDelayMs
                    showToast("Subtitle delay: ${if (newDelayMs >= 0) "+$newDelayMs" else "$newDelayMs"} ms")
                },
                onDismiss = { showSubtitleDialog = false }
            )
        }

        if (showVideoInfoDialog) {
            VideoInfoDialog(
                video = video,
                videoFormat = activeVideoFormat,
                audioFormat = activeAudioFormat,
                onDismiss = { showVideoInfoDialog = false }
            )
        }
    }
}
