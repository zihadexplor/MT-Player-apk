package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioTrack
import com.example.data.model.StorageStats
import com.example.data.model.VideoItem
import com.example.ui.components.EmptyMediaView
import com.example.ui.components.StorageStatBanner
import com.example.ui.components.TrackItemRow
import com.example.ui.components.VideoGridCard
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Calendar

@Composable
fun HomeScreen(
    stats: StorageStats,
    allTracks: List<AudioTrack>,
    recentlyPlayed: List<AudioTrack>,
    mostPlayed: List<AudioTrack>,
    recentVideos: List<VideoItem>,
    selectedTrack: AudioTrack?,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRescanClick: () -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToVideos: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val greeting = rememberGreeting()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_root")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Greeting
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Offline Player • Background Audio Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Storage Stats Hero Card
            item {
                StorageStatBanner(
                    stats = stats,
                    onRescanClick = onRescanClick
                )
            }

            // Quick Category Shortcuts
            item {
                Column {
                    Text(
                        text = "Quick Navigation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAccessCard(
                            title = "Music Library",
                            subtitle = "${stats.audioCount} songs",
                            icon = Icons.Default.MusicNote,
                            onClick = onNavigateToMusic,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_card_music"
                        )
                        QuickAccessCard(
                            title = "Local Videos",
                            subtitle = "${stats.videoCount} clips",
                            icon = Icons.Default.Videocam,
                            onClick = onNavigateToVideos,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_card_videos"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAccessCard(
                            title = "Playlists Hub",
                            subtitle = "Custom Lists",
                            icon = Icons.Default.Folder,
                            onClick = onNavigateToLibrary,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_card_library"
                        )
                        QuickAccessCard(
                            title = "Equalizer & FX",
                            subtitle = "Sound Profiles",
                            icon = Icons.Default.Equalizer,
                            onClick = onNavigateToEqualizer,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_card_audio_engine"
                        )
                    }
                }
            }

            // Recently Played Section (Phase 4 requirement)
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recently Played",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(recentlyPlayed.take(3), key = { "recent_${it.id}" }) { track ->
                    TrackItemRow(
                        track = track,
                        isSelected = selectedTrack?.id == track.id,
                        onTrackClick = { onTrackClick(track, recentlyPlayed) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onMoreOptionsClick = { onTrackOptionsClick(track) }
                    )
                }
            }

            // Most Played Section (Phase 4 requirement)
            if (mostPlayed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Most Played",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(mostPlayed.take(3), key = { "most_${it.id}" }) { track ->
                    TrackItemRow(
                        track = track,
                        isSelected = selectedTrack?.id == track.id,
                        onTrackClick = { onTrackClick(track, mostPlayed) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onMoreOptionsClick = { onTrackOptionsClick(track) }
                    )
                }
            }

            // Device Music Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Music",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onNavigateToMusic() }
                            .padding(4.dp)
                    )
                }
            }

            if (allTracks.isEmpty()) {
                item {
                    EmptyMediaView(
                        title = "No audio tracks detected",
                        subtitle = "Put MP3, FLAC, WAV, AAC, or M4A files in local storage and tap Rescan.",
                        buttonText = "Scan Storage",
                        onButtonClick = onRescanClick
                    )
                }
            } else {
                items(allTracks.take(5), key = { "track_${it.id}" }) { track ->
                    TrackItemRow(
                        track = track,
                        isSelected = selectedTrack?.id == track.id,
                        onTrackClick = { onTrackClick(track, allTracks) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onMoreOptionsClick = { onTrackOptionsClick(track) }
                    )
                }
            }

            // Videos Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onNavigateToVideos() }
                            .padding(4.dp)
                    )
                }
            }

            if (recentVideos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No local video files found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentVideos.take(2), key = { "video_${it.id}" }) { video ->
                    VideoGridCard(
                        video = video,
                        onVideoClick = { onVideoClick(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun rememberGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
