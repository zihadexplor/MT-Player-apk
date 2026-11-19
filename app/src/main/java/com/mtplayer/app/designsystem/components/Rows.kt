package com.mtplayer.app.designsystem.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.designsystem.theme.MtColors
import com.mtplayer.app.ui.image.VideoThumb

@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Three-bar "now playing" indicator; static when paused or when the user disabled animations. */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val animate = playing && !reducedMotion()
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = listOf(0.45f, 0.9f, 0.65f).mapIndexed { i, base ->
        if (animate) {
            val v by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse), label = "bar$i",
            )
            v
        } else base
    }
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

/**
 * Standard two-line media row: artwork, title, subtitle, optional trailing text and an overflow
 * button. Min height 64dp; overflow button is a 48dp target.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    artworkShape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
    active: Boolean = false,
    playing: Boolean = false,
    favorite: Boolean = false,
    trailing: String? = null,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val stateText = if (active) stringResource(if (playing) R.string.state_playing else R.string.state_paused) else null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick ?: onMore)
            .semantics { if (stateText != null) stateDescription = stateText }
            .padding(start = 16.dp, end = if (onMore != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(artwork, contentDescription = null, modifier = Modifier.size(52.dp), shape = artworkShape, fallbackIcon = fallbackIcon, sizePx = 160)
            if (active) {
                Box(Modifier.size(52.dp).clip(artworkShape).background(scheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    PlayingBars(playing)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                color = if (active) scheme.primary else scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.favorite), tint = MtColors.current.favorite, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        if (onMore != null) {
            val more = stringResource(R.string.more_options_for, title)
            IconButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = more }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, overline: String? = null) {
    SectionHeading(title = title, modifier = modifier, overline = overline, actionLabel = actionLabel, onAction = onAction, top = if (overline != null) 24.dp else 20.dp)
}

/** Grid tile for albums/artists/playlists. */
@Composable
fun TileCard(
    title: String,
    subtitle: String,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Artwork(artwork, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.medium, fallbackIcon = fallbackIcon, iconSize = 40.dp, sizePx = 384)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Video grid card with duration badge, resolution and resume progress. */
@Composable
fun VideoCard(
    video: Video,
    progress: Float?,
    favorite: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onMore)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            Artwork(VideoThumb(video.id, video.uri), contentDescription = null, modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium, fallbackIcon = Icons.Rounded.Movie, iconSize = 36.dp)
            Badge(Format.duration(video.durationMs), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (video.resolutionLabel.isNotEmpty()) Badge(video.resolutionLabel, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (favorite) {
                Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.favorite), tint = MtColors.current.onScrim,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).padding(horizontal = 1.dp),
                    color = scheme.primary,
                    trackColor = MtColors.current.scrimSoft,
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${video.folderName} · ${Format.size(video.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val more = stringResource(R.string.more_options_for, video.title)
            IconButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = more }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VideoListRow(
    video: Video,
    progress: Float?,
    favorite: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = 80.dp)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 10f)) {
            Artwork(VideoThumb(video.id, video.uri), contentDescription = null, modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.small, fallbackIcon = Icons.Rounded.Movie)
            Badge(Format.duration(video.durationMs), Modifier.align(Alignment.BottomEnd).padding(4.dp))
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    color = scheme.primary, trackColor = MtColors.current.scrimSoft, drawStopIndicator = {})
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.favorite), tint = MtColors.current.favorite, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(listOf(video.resolutionLabel, Format.size(video.sizeBytes)).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        val more = stringResource(R.string.more_options_for, video.title)
        IconButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = more }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** Small label on top of imagery; always light text on a dark scrim, so it is readable on any frame. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MtColors.current.onScrim,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MtColors.current.scrimStrong).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
