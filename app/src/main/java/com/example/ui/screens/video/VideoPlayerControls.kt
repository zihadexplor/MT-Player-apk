package com.example.ui.screens.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.model.VideoItem
import com.example.ui.components.formatDuration
import com.example.ui.theme.MonoFontFamily

enum class ScreenOrientationOption {
    SENSOR,
    LANDSCAPE,
    PORTRAIT
}

@Composable
fun VideoPlayerTopBar(
    video: VideoItem,
    currentSpeed: Float,
    isPipSupported: Boolean,
    onBackClick: () -> Unit,
    onToggleRotation: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onPipClick: () -> Unit,
    onInfoClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .testTag("video_player_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close player",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Title and badge (overlaying dark video scrim)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = video.resolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Action icons in horizontal scroll for smaller screens
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleRotation,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_rotate_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Rotate orientation",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onAudioTrackClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_audio_track_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = "Audio tracks",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSubtitleClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_subtitle_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "Subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSpeedClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_speed_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${currentSpeed}x",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = onAspectRatioClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_aspect_ratio_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Aspect ratio",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isPipSupported) {
                IconButton(
                    onClick = onPipClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("video_pip_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in picture",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_info_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Video info",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onLockClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_screen_lock_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Lock controls",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VideoPlayerCenterControls(
    isPlaying: Boolean,
    seekSeconds: Int = 10,
    onRewind10s: () -> Unit,
    onPlayPause: () -> Unit,
    onForward10s: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onRewind10s,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .minimumInteractiveComponentSize()
                .testTag("video_rewind_10s_btn")
        ) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "Rewind ${seekSeconds}s",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onPlayPause() }
                .testTag("video_play_pause_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        IconButton(
            onClick = onForward10s,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .minimumInteractiveComponentSize()
                .testTag("video_forward_10s_btn")
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Forward ${seekSeconds}s",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun VideoPlayerBottomBar(
    currentPositionMs: Long,
    durationMs: Long,
    sliderProgress: Float,
    isUserSeeking: Boolean,
    onSeekProgressChange: (Float) -> Unit,
    onSeekProgressFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val displayPos = if (isUserSeeking) {
            (sliderProgress * durationMs).toLong()
        } else currentPositionMs

        val safeDuration = if (durationMs > 0) durationMs else 1L
        val remainingMs = (safeDuration - displayPos).coerceAtLeast(0L)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayPos),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "-${formatDuration(remainingMs)}  /  ${formatDuration(durationMs)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp
            )
        }

        val currentRatio = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        Slider(
            value = if (isUserSeeking) sliderProgress else currentRatio,
            onValueChange = onSeekProgressChange,
            onValueChangeFinished = onSeekProgressFinish,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_seekbar")
        )
    }
}

@Composable
fun LockedFloatingUnlockButton(
    isVisible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.78f),
            tonalElevation = 8.dp,
            modifier = Modifier
                .clickable { onUnlock() }
                .testTag("video_screen_unlock_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock controls",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Controls Locked • Tap to Unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
