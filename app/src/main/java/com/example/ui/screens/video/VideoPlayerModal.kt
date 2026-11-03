package com.example.ui.screens.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItem
import com.example.ui.theme.SpotifyGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerModal(
    video: VideoItem,
    onClose: () -> Unit,
    onProgressUpdate: (String, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(video.playbackPositionMs) }
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
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLockedUnlockPillVisible by remember { mutableStateOf(false) }
    var orientationMode by remember { mutableStateOf(ScreenOrientationOption.LANDSCAPE) }
    var isInPipMode by remember { mutableStateOf(false) }

    // Audio tracks & Subtitles state
    var audioTracksList by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var subtitleTracksList by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }
    var externalSubtitleName by remember { mutableStateOf<String?>(null) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }

    // HUD States
    var isBrightnessHUDVisible by remember { mutableStateOf(false) }
    var currentBrightness by remember {
        val initialWin = activity?.window?.attributes?.screenBrightness ?: -1.0f
        mutableFloatStateOf(if (initialWin > 0f) initialWin else 0.5f)
    }

    var isVolumeHUDVisible by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    var isScrubHUDVisible by remember { mutableStateOf(false) }
    var scrubTargetTimeMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var scrubStartTimeMs by remember { mutableLongStateOf(0L) }

    var isDoubleTapRewindVisible by remember { mutableStateOf(false) }
    var isDoubleTapForwardVisible by remember { mutableStateOf(false) }

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    var showResumePill by remember { mutableStateOf(video.playbackPositionMs > 5000L) }

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

    // Initialize ExoPlayer
    val exoPlayer = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(video.uri))
            setMediaItem(mediaItem)
            prepare()
            if (video.playbackPositionMs > 0L) {
                seekTo(video.playbackPositionMs)
            }
            playWhenReady = true
        }
    }

    // External Subtitle Picker
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

            val filename = uri.lastPathSegment ?: "subtitle"
            externalSubtitleName = filename
            val mimeType = if (filename.endsWith(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val curPos = exoPlayer.currentPosition
            val curPlaying = exoPlayer.playWhenReady
            val newMediaItem = MediaItem.Builder()
                .setUri(video.uri)
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()

            exoPlayer.setMediaItem(newMediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(curPos)
            exoPlayer.playWhenReady = curPlaying
            showToast("External subtitle loaded: $filename")
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

        // Initial default: Landscape
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Immersive full-screen: hide system status & navigation bars
        activity?.window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        // PiP Listener
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

    // Progress updates ticker
    LaunchedEffect(isPlaying, isUserSeeking) {
        while (true) {
            if (isPlaying && !isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
                currentOnProgressUpdate(video.id, currentPositionMs, durationMs)
            }
            delay(500L)
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

        // Gesture Overlay Layer (handles Taps, Double Taps, Horizontal Scrub, Vertical Brightness & Volume)
        if (!isInPipMode) {
            VideoGestureOverlay(
                isScreenLocked = isScreenLocked,
                onSingleTap = {
                    isControlsVisible = !isControlsVisible
                },
                onDoubleTapLeft = {
                    val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    isDoubleTapRewindVisible = true
                    coroutineScope.launch {
                        delay(650L)
                        isDoubleTapRewindVisible = false
                    }
                },
                onDoubleTapRight = {
                    val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
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
                    scrubStartTimeMs = exoPlayer.currentPosition
                    scrubTargetTimeMs = scrubStartTimeMs
                    scrubDeltaMs = 0L
                    isScrubHUDVisible = true
                },
                onScrubDelta = { deltaX ->
                    // 1 pixel = ~120ms of scrub
                    val deltaMs = (deltaX * 120f).toLong()
                    val newDelta = scrubDeltaMs + deltaMs
                    val newTarget = (scrubStartTimeMs + newDelta).coerceIn(0L, durationMs)
                    scrubDeltaMs = newTarget - scrubStartTimeMs
                    scrubTargetTimeMs = newTarget
                },
                onScrubCommit = {
                    exoPlayer.seekTo(scrubTargetTimeMs)
                    currentPositionMs = scrubTargetTimeMs
                },
                onBrightnessDelta = { delta ->
                    val newBrightness = (currentBrightness + delta).coerceIn(0.02f, 1.0f)
                    currentBrightness = newBrightness
                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                        screenBrightness = newBrightness
                    }
                    isBrightnessHUDVisible = true
                },
                onVolumeDelta = { delta ->
                    val deltaSteps = (delta * maxVolume).toInt()
                    val newVol = (currentVolumeLevel + deltaSteps).coerceIn(0, maxVolume)
                    if (newVol != currentVolumeLevel) {
                        currentVolumeLevel = newVol
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    }
                    isVolumeHUDVisible = true
                },
                onGestureEnd = {
                    coroutineScope.launch {
                        delay(900L)
                        isBrightnessHUDVisible = false
                        isVolumeHUDVisible = false
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

        // Buffering Indicator
        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = SpotifyGreen,
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
            maxVolume = maxVolume,
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
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp)
        )

        DoubleTapFeedback(
            isForward = true,
            isVisible = isDoubleTapForwardVisible,
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
                // Top App Bar
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

                // Center playback controls (Rewind, Play/Pause, Forward)
                VideoPlayerCenterControls(
                    isPlaying = isPlaying,
                    onRewind10s = {
                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
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
                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                // Bottom Scrubber Bar
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
