package com.mtplayer.app.video

import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.designsystem.theme.MtColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(UnstableApi::class)
private enum class Fit(val mode: Int, val label: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.aspect_fit),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.aspect_crop),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.aspect_stretch),
}

private enum class VSheet { AUDIO, SUBTITLES, SPEED }

private sealed interface Indicator {
    data class Brightness(val value: Float) : Indicator
    data class Volume(val value: Float) : Indicator
    data class Seek(val targetMs: Long, val deltaMs: Long) : Indicator
    data class Zoom(val scale: Float) : Indicator
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(activity: VideoPlayerActivity, model: VideoUiModel) {
    val player = model.player
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var fit by remember { mutableStateOf(Fit.FIT) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var indicator by remember { mutableStateOf<Indicator?>(null) }
    var sheet by remember { mutableStateOf<VSheet?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(activity::attachSubtitle) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Keep the screen awake only while actually playing (battery/heat).
    LaunchedEffect(model.isPlaying) {
        if (model.isPlaying) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // Auto-hide controls after 3.5s of inactivity while playing.
    LaunchedEffect(model.controlsVisible, model.interaction, model.isPlaying) {
        if (model.controlsVisible && model.isPlaying) { delay(3_500); model.controlsVisible = false }
    }
    LaunchedEffect(indicator) { if (indicator != null) { delay(900); indicator = null } }
    LaunchedEffect(model.seekFlash) { if (model.seekFlash != null) { delay(700); model.seekFlash = null } }
    LaunchedEffect(showUnlock) { if (showUnlock) { delay(2_500); showUnlock = false } }

    BackHandler(enabled = model.locked) { showUnlock = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(true)
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                    view.resizeMode = fit.mode
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = zoom; scaleY = zoom }
                    .onGloballyPositioned { c ->
                        val b = c.boundsInWindow()
                        activity.setVideoBounds(Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
                    },
            )
        }

        if (!model.inPip) {
            // Gesture layer: taps, double taps, vertical brightness/volume, horizontal seek, pinch zoom.
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(model.locked, model.doubleTapSec) {
                        detectTapGestures(
                            onTap = {
                                if (model.locked) showUnlock = true
                                else if (model.controlsVisible) model.controlsVisible = false else model.showControls()
                            },
                            onDoubleTap = { pos ->
                                if (model.locked) return@detectTapGestures
                                val p = model.player ?: return@detectTapGestures
                                val third = size.width / 3f
                                when {
                                    pos.x < third -> { p.seekTo((p.currentPosition - model.doubleTapSec * 1000L).coerceAtLeast(0)); model.flash(-model.doubleTapSec) }
                                    pos.x > third * 2 -> { p.seekTo(p.currentPosition + model.doubleTapSec * 1000L); model.flash(model.doubleTapSec) }
                                    else -> if (p.isPlaying) p.pause() else p.play()
                                }
                            },
                        )
                    }
                    .pointerInput(model.locked, model.gestures) {
                        if (model.locked) return@pointerInput
                        val (gBright, gVol, gSeek) = model.gestures
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val p = model.player
                            var mode = 0 // 0 undecided, 1 brightness, 2 volume, 3 seek, 4 zoom
                            var total = Offset.Zero
                            val startBrightness = activity.currentBrightness()
                            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val startPos = p?.currentPosition ?: 0L
                            val duration = model.durationMs
                            var startSpan = 0f
                            val startZoom = zoom
                            var seekTarget = startPos
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val span = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                                    if (mode != 4) { mode = 4; startSpan = span.coerceAtLeast(1f) }
                                    zoom = (startZoom * span / startSpan).coerceIn(1f, 4f)
                                    indicator = Indicator.Zoom(zoom)
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                val change = pressed.first()
                                total += change.positionChange()
                                if (mode == 0 && (abs(total.x) > touchSlop || abs(total.y) > touchSlop)) {
                                    mode = if (abs(total.y) > abs(total.x)) {
                                        if (down.position.x < size.width / 2f) { if (gBright) 1 else -1 } else { if (gVol) 2 else -1 }
                                    } else if (gSeek && duration > 0) 3 else -1
                                }
                                when (mode) {
                                    1 -> {
                                        val v = (startBrightness - total.y / (size.height * 0.8f)).coerceIn(0.01f, 1f)
                                        activity.setBrightness(v); indicator = Indicator.Brightness(v); change.consume()
                                    }
                                    2 -> {
                                        val v = (startVol - total.y / (size.height * 0.8f) * maxVol).coerceIn(0f, maxVol.toFloat())
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0)
                                        indicator = Indicator.Volume(v / maxVol); change.consume()
                                    }
                                    3 -> {
                                        val delta = (total.x / size.width * 90_000).toLong()
                                        seekTarget = (startPos + delta).coerceIn(0, duration)
                                        indicator = Indicator.Seek(seekTarget, seekTarget - startPos); change.consume()
                                    }
                                }
                            }
                            if (mode == 3) p?.seekTo(seekTarget)
                        }
                    },
            )
        }

        if (model.buffering && !model.inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
        }

        indicator?.let { IndicatorPill(it, Modifier.align(Alignment.Center)) }
        model.seekFlash?.let { (sec, _) ->
            IndicatorPill(Indicator.Seek(-1, sec * 1000L), Modifier.align(if (sec < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 48.dp))
        }

        model.error?.let { message -> ErrorOverlay(message, hasNext = model.hasNext, onNext = { model.error = null; player?.seekToNextMediaItem(); player?.prepare() }, onClose = activity::finish) }

        if (!model.inPip) {
            AnimatedVisibility(model.controlsVisible && !model.locked && model.error == null, enter = fadeIn(), exit = fadeOut()) {
                Controls(
                    activity = activity,
                    model = model,
                    fit = fit,
                    onFit = { fit = Fit.entries[(fit.ordinal + 1) % Fit.entries.size]; zoom = 1f; model.showControls() },
                    onSheet = { sheet = it },
                )
            }
            if (model.locked) {
                AnimatedVisibility(showUnlock, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                    OverlayButton(Icons.Rounded.LockOpen, stringResource(R.string.unlock), big = true) { model.locked = false; model.showControls() }
                }
            }
        }
    }

    when (sheet) {
        VSheet.AUDIO -> TrackSheet(model, C.TRACK_TYPE_AUDIO, onPickFile = null) { sheet = null }
        VSheet.SUBTITLES -> TrackSheet(model, C.TRACK_TYPE_TEXT, onPickFile = {
            subtitlePicker.launch(arrayOf("application/x-subrip", "text/*", "application/octet-stream", "text/vtt"))
        }) { sheet = null }
        VSheet.SPEED -> SpeedSheet(model) { sheet = null }
        null -> {}
    }
}

@Composable
private fun Controls(activity: VideoPlayerActivity, model: VideoUiModel, fit: Fit, onFit: () -> Unit, onSheet: (VSheet) -> Unit) {
    val player = model.player
    val scrim = MtColors.current.scrimStrong
    Box(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(scrim, Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) { activity.finish() }
            Text(model.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            OverlayButton(Icons.Rounded.Audiotrack, stringResource(R.string.audio_track)) { onSheet(VSheet.AUDIO) }
            OverlayButton(Icons.Rounded.ClosedCaption, stringResource(R.string.subtitles)) { onSheet(VSheet.SUBTITLES) }
            OverlayButton(Icons.Rounded.Speed, stringResource(R.string.playback_speed)) { onSheet(VSheet.SPEED) }
        }

        // Centre transport
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            OverlayButton(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), enabled = model.hasPrevious) { player?.seekToPreviousMediaItem(); model.showControls() }
            OverlayButton(Icons.Rounded.FastRewind, stringResource(R.string.rewind)) {
                player?.let { it.seekTo((it.currentPosition - model.doubleTapSec * 1000L).coerceAtLeast(0)) }; model.showControls()
            }
            OverlayButton(if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (model.isPlaying) R.string.pause else R.string.play), big = true) {
                player?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == androidx.media3.common.Player.STATE_ENDED) it.seekTo(0); it.play() } }
                model.showControls()
            }
            OverlayButton(Icons.Rounded.FastForward, stringResource(R.string.forward)) {
                player?.let { it.seekTo(it.currentPosition + model.doubleTapSec * 1000L) }; model.showControls()
            }
            OverlayButton(Icons.Rounded.SkipNext, stringResource(R.string.next), enabled = model.hasNext) { player?.seekToNextMediaItem(); model.showControls() }
        }

        // Bottom: seek bar + tools
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            VideoSeekBar(model)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(Icons.Rounded.Lock, stringResource(R.string.lock)) { model.locked = true; model.controlsVisible = false }
                OverlayButton(Icons.Rounded.AspectRatio, stringResource(R.string.aspect_ratio) + ": " + stringResource(fit.label), onClick = onFit)
                Text(stringResource(fit.label), style = MaterialTheme.typography.labelMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                OverlayButton(Icons.Rounded.Headphones, stringResource(R.string.background_audio), selected = model.backgroundAudio) {
                    model.backgroundAudio = !model.backgroundAudio; model.showControls()
                }
                OverlayButton(Icons.Rounded.ScreenRotation, stringResource(R.string.rotate)) { activity.toggleOrientation(); model.showControls() }
                if (activity.canPip) OverlayButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip)) { activity.enterPip() }
            }
        }
    }
}

@Composable
private fun VideoSeekBar(model: VideoUiModel) {
    val player = model.player
    val position by produceState(0L, player) {
        while (true) { value = player?.currentPosition ?: 0L; delay(500) }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = model.durationMs.coerceAtLeast(1)
    val shown = dragging?.let { (it * duration).toLong() } ?: position
    val seekLabel = stringResource(R.string.seek)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Format.duration(shown), style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.width(56.dp))
        Slider(
            value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it; model.showControls() },
            onValueChangeFinished = { dragging?.let { player?.seekTo((it * duration).toLong()) }; dragging = null },
            enabled = model.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            ),
            modifier = Modifier.weight(1f).semantics { contentDescription = seekLabel },
        )
        Text(if (model.durationMs > 0) Format.duration(model.durationMs) else "--:--", style = MaterialTheme.typography.labelMedium,
            color = Color.White, modifier = Modifier.width(56.dp).padding(start = 8.dp))
    }
}

@Composable
private fun OverlayButton(icon: ImageVector, label: String, big: Boolean = false, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(if (big) 72.dp else 48.dp)
            .then(if (big) Modifier.background(MtColors.current.scrimStrong, CircleShape) else Modifier),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (big) 44.dp else 24.dp))
    }
}

@Composable
private fun IndicatorPill(indicator: Indicator, modifier: Modifier = Modifier) {
    val (icon, text) = when (indicator) {
        is Indicator.Brightness -> Icons.Rounded.Brightness6 to "${(indicator.value * 100).toInt()}%"
        is Indicator.Volume -> Icons.AutoMirrored.Rounded.VolumeUp to "${(indicator.value * 100).toInt()}%"
        is Indicator.Zoom -> Icons.Rounded.AspectRatio to "%.1fx".format(indicator.scale)
        is Indicator.Seek -> (if (indicator.deltaMs < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward) to buildString {
            if (indicator.targetMs >= 0) append(Format.duration(indicator.targetMs)).append("  ")
            append(if (indicator.deltaMs < 0) "−" else "+").append(abs(indicator.deltaMs) / 1000).append("s")
        }
    }
    Row(
        modifier.background(MtColors.current.scrimStrong, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun ErrorOverlay(message: String, hasNext: Boolean, onNext: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MtColors.current.scrimStrong), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Spacer(Modifier.size(16.dp))
            Row {
                if (hasNext) TextButton(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.next), color = Color.White) }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.close), color = Color.White) }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSheet(model: VideoUiModel, type: Int, onPickFile: (() -> Unit)?, onDismiss: () -> Unit) {
    val player = model.player ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = model.tracks.groups.filter { it.type == type && it.isSupported }
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(if (type == C.TRACK_TYPE_AUDIO) R.string.audio_track else R.string.subtitles),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (type == C.TRACK_TYPE_TEXT) {
                TrackRow(stringResource(R.string.subtitles_off), selected = disabled || groups.none { it.isSelected }) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    onDismiss()
                }
            }
            groups.forEachIndexed { gi, group ->
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    val name = listOfNotNull(
                        f.label,
                        f.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
                        if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null,
                    ).distinct().joinToString(" · ").ifBlank { stringResource(R.string.track_n, gi + 1) }
                    TrackRow(name, selected = !disabled && group.isTrackSelected(i)) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(type, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        onDismiss()
                    }
                }
            }
            if (groups.isEmpty() && type == C.TRACK_TYPE_AUDIO) {
                Text(stringResource(R.string.no_tracks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
            if (onPickFile != null) {
                TextButton(onClick = { onPickFile(); onDismiss() }, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.load_subtitle_file))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(model: VideoUiModel, onDismiss: () -> Unit) {
    val player = model.player ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.playback_speed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { s ->
                TrackRow("${s}x", selected = model.speed == s) { player.setPlaybackSpeed(s); onDismiss() }
            }
        }
    }
}

