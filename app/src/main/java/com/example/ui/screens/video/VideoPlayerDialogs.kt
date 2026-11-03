package com.example.ui.screens.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.VideoItem
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val channels: Int,
    val sampleRate: Int,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean
)

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Playback Speed",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Adjust video and audio speed seamlessly with pitch preservation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                val speedChunks = speeds.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speedChunks.forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSpeeds.forEach { speed ->
                                val isSelected = currentSpeed == speed
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) SpotifyGreen else DarkSurfaceContainerHigh,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSpeedSelect(speed)
                                            onDismiss()
                                        }
                                        .testTag("speed_chip_${speed}x")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            color = if (isSelected) Color.Black else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun AspectRatioDialog(
    currentResizeMode: Int,
    onResizeModeSelect: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit (Letterbox)", "Maintains original aspect ratio with black bars if necessary"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Crop / Fill", "Zooms video to fill the entire screen (eliminates black bars)"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch", "Stretches the video picture to touch all display borders"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "100% Original", "Renders without scaling at native pixel size")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (mode, title, desc) ->
                    val isSelected = currentResizeMode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onResizeModeSelect(mode, title)
                                onDismiss()
                            }
                            .testTag("aspect_ratio_item_$mode")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onResizeModeSelect(mode, title)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SpotifyGreen else TextPrimary
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun AudioTrackDialog(
    audioTracks: List<AudioTrackInfo>,
    onSelectTrack: (groupIndex: Int, trackIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Audio Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (audioTracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Single default audio track detected in this stream.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(audioTracks) { index, track ->
                        val isSelected = track.isSelected
                        val channelStr = when (track.channels) {
                            1 -> "Mono"
                            2 -> "Stereo (2.0)"
                            6 -> "5.1 Surround"
                            8 -> "7.1 Surround"
                            else -> if (track.channels > 0) "${track.channels} ch" else "Stereo"
                        }
                        val title = track.label ?: track.language?.uppercase() ?: "Track ${index + 1}"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTrack(track.groupIndex, track.trackIndex)
                                    onDismiss()
                                }
                                .testTag("audio_track_item_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectTrack(track.groupIndex, track.trackIndex)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = SpotifyGreen,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) SpotifyGreen else TextPrimary
                                    )
                                    Text(
                                        text = "$channelStr • ${if (track.sampleRate > 0) "${track.sampleRate / 1000} kHz" else "48 kHz"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun SubtitleOptionsDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    isSubtitlesDisabled: Boolean,
    externalSubtitleName: String?,
    subtitleDelayMs: Long,
    onPickExternalFile: () -> Unit,
    onSelectInternalTrack: (groupIndex: Int, trackIndex: Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Subtitles & Captions",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: External Subtitle File
                item {
                    Text(
                        text = "EXTERNAL SUBTITLE FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onPickExternalFile,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_external_subtitle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Select External Subtitle (.srt, .vtt)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (externalSubtitleName != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Loaded: $externalSubtitleName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Section 2: Tracks
                item {
                    HorizontalDivider(color = DarkOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SUBTITLE TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Disable subtitles option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSubtitlesDisabled) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDisableSubtitles() }
                            .testTag("disable_subtitles_item")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSubtitlesDisabled,
                                onClick = onDisableSubtitles,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subtitles Off",
                                color = if (isSubtitlesDisabled) SpotifyGreen else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                itemsIndexed(subtitleTracks) { index, track ->
                    val isSelected = !isSubtitlesDisabled && track.isSelected
                    val label = track.label ?: track.language?.uppercase() ?: "Subtitle Track ${index + 1}"

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectInternalTrack(track.groupIndex, track.trackIndex) }
                            .testTag("subtitle_track_item_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectInternalTrack(track.groupIndex, track.trackIndex) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) SpotifyGreen else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Section 3: Subtitle Delay Adjustment
                item {
                    HorizontalDivider(color = DarkOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUBTITLE SYNC DELAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (subtitleDelayMs >= 0) "+${subtitleDelayMs} ms" else "$subtitleDelayMs ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (subtitleDelayMs == 0L) TextSecondary else SpotifyGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stepper chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs - 1000L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_sub_1s")
                        ) {
                            Text("-1.0s", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs - 250L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_sub_250ms")
                        ) {
                            Text("-250ms", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(0L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_reset")
                        ) {
                            Text("Reset", fontSize = 11.sp, color = SpotifyGreen)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 250L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_add_250ms")
                        ) {
                            Text("+250ms", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 1000L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_add_1s")
                        ) {
                            Text("+1.0s", fontSize = 11.sp, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = subtitleDelayMs.toFloat(),
                        onValueChange = { onAdjustDelay(it.toLong()) },
                        valueRange = -5000f..5000f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = SpotifyGreen,
                            activeTrackColor = SpotifyGreen,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_delay_slider")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Done")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun VideoInfoDialog(
    video: VideoItem,
    videoFormat: Format?,
    audioFormat: Format?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Video Details",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow(label = "Title", value = video.title)
                InfoRow(
                    label = "Resolution",
                    value = if (video.width > 0 && video.height > 0) "${video.width} × ${video.height} (${video.resolution})" else video.resolution
                )
                InfoRow(label = "Duration", value = formatDuration(video.durationMs))
                InfoRow(label = "File Size", value = formatFileSize(video.sizeBytes))

                videoFormat?.let { vf ->
                    InfoRow(label = "Video Codec", value = vf.sampleMimeType ?: "Hardware H.264/HEVC")
                    if (vf.frameRate > 0) {
                        InfoRow(label = "Frame Rate", value = "%.2f fps".format(vf.frameRate))
                    }
                    if (vf.bitrate > 0) {
                        InfoRow(label = "Bitrate", value = "${vf.bitrate / 1000} kbps")
                    }
                }

                audioFormat?.let { af ->
                    InfoRow(label = "Audio Codec", value = af.sampleMimeType ?: "AAC / PCM")
                    if (af.channelCount > 0) {
                        val ch = when (af.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo (2.0)"
                            6 -> "5.1 Surround"
                            else -> "${af.channelCount} channels"
                        }
                        InfoRow(label = "Channels", value = ch)
                    }
                }

                InfoRow(label = "Folder", value = video.folderName)
                if (video.folderPath.isNotBlank()) {
                    InfoRow(label = "Path", value = video.folderPath)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PlaybackErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.90f),
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Unsupported Video Format",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This device's hardware decoders could not play this file, or the media stream is corrupted.\n\n$errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("error_close_btn")
                    ) {
                        Text("Close", color = TextPrimary)
                    }
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("error_retry_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
