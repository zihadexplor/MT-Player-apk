package com.mtplayer.app.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.designsystem.components.Artwork
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.playback.PlayerConnection
import com.mtplayer.app.playback.PlayerUiState
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Persistent mini player. Tap opens Now Playing; the close button (or a horizontal swipe)
 * stops playback, clears the queue and removes the notification.
 */
@Composable
fun MiniPlayer(state: PlayerUiState, player: PlayerConnection, onOpen: () -> Unit, modifier: Modifier = Modifier, docked: Boolean = false) {
    val current = state.current ?: return
    val scheme = MaterialTheme.colorScheme
    val messenger = LocalMessenger.current
    val stopped = stringResource(R.string.playback_stopped)
    val closeLabel = stringResource(R.string.close_player)
    val offset = remember(current.mediaId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { 120.dp.toPx() }
    val close = {
        player.stopAndClear()
        messenger.show(stopped)
    }

    // Docked: drawn inside the dock's container (no own surface). Standalone (tablet/TV): a floating card.
    Surface(
        color = if (docked) androidx.compose.ui.graphics.Color.Transparent else scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        shape = if (docked) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = if (docked) 0.dp else 6.dp,
        modifier = modifier
            .padding(horizontal = if (docked) 0.dp else 12.dp, vertical = if (docked) 0.dp else 6.dp)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (dismissPx * 2)).coerceIn(0f, 0.7f)
            }
            .pointerInput(current.mediaId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > dismissPx) close() else offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            }
            .semantics { customActions = listOf(CustomAccessibilityAction(closeLabel) { close(); true }) },
    ) {
        Column {
            val ticks by remember(player) { player.positionTicks(500) }.collectAsStateWithLifecycle(0L to 0L)
            val progress = if (current.durationMs > 0) (ticks.first.toFloat() / current.durationMs).coerceIn(0f, 1f) else 0f
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_now_playing), onClick = onOpen)
                    .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(current.artworkUri, null, Modifier.size(48.dp), shape = MaterialTheme.shapes.small, sizePx = 160)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                    Text(current.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = player::togglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        tint = scheme.onSurface,
                    )
                }
                IconButton(onClick = player::next, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next),
                        tint = if (state.hasNext) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f))
                }
                IconButton(onClick = close) {
                    Icon(Icons.Rounded.Close, contentDescription = closeLabel, tint = scheme.onSurfaceVariant)
                }
            }
            Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.extraSmall),
                    color = scheme.primary,
                    trackColor = scheme.onSurface.copy(alpha = 0.10f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
