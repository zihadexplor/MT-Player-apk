package com.example.ui.screens.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioTrack
import com.example.data.model.MusicFolder
import com.example.data.model.StorageStats
import com.example.data.model.TrackSort
import com.example.ui.components.BatchActionBar
import com.example.ui.components.EmptyMediaView
import com.example.ui.components.PermissionPromptCard
import com.example.ui.components.TrackItemRow
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkOutlineVariant
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun MusicScreen(
    tracks: List<AudioTrack>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<MusicFolder>,
    stats: StorageStats,
    currentPlayingTrack: AudioTrack?,
    isPlaying: Boolean,
    activeTab: Int,
    currentSort: TrackSort,
    isSelectionMode: Boolean,
    selectedTrackIds: Set<String>,
    onTabChange: (Int) -> Unit,
    onSortChange: (TrackSort) -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
    onToggleSelectionMode: (String?) -> Unit,
    onToggleTrackSelection: (String) -> Unit,
    onSelectAll: (List<AudioTrack>) -> Unit,
    onClearSelection: () -> Unit,
    onPlaySelected: () -> Unit,
    onAddSelectedToQueue: () -> Unit,
    onAddSelectedToFavorites: () -> Unit,
    onAddSelectedToPlaylist: () -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Tracks", "Albums", "Artists", "Folders", "Favorites")
    var showSortMenu by remember { mutableStateOf(false) }

    var expandedAlbumId by remember { mutableStateOf<Long?>(null) }
    var expandedArtistName by remember { mutableStateOf<String?>(null) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }

    val favoriteTracks = remember(tracks) { tracks.filter { it.isFavorite } }
    val currentTrackList = if (activeTab == 4) favoriteTracks else tracks

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_screen_root")
    ) {
        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = DarkSurface,
            contentColor = SpotifyGreen,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (activeTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = SpotifyGreen,
                        height = 3.dp
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { onTabChange(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == index) TextPrimary else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("music_tab_$index")
                )
            }
        }

        // Permission banner if needed
        if (!stats.hasStoragePermission && stats.permissionPermanentlyDenied) {
            PermissionPromptCard(
                onRequestPermission = onRequestPermission,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Header controls for Tracks & Favorites
        if (activeTab == 0 || activeTab == 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentTrackList.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Multi-select Toggle Button
                    IconButton(
                        onClick = { onToggleSelectionMode(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                            contentDescription = "Select Mode",
                            tint = if (isSelectionMode) SpotifyGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sort Button & Menu
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { showSortMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("track_sort_dropdown_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Options",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val sortLabel = when (currentSort) {
                                TrackSort.TITLE_ASC -> "A to Z"
                                TrackSort.TITLE_DESC -> "Z to A"
                                TrackSort.RECENTLY_ADDED -> "Recently Added"
                                TrackSort.DURATION -> "Duration"
                                TrackSort.ARTIST -> "Artist"
                                TrackSort.ALBUM -> "Album"
                            }
                            Text(
                                text = sortLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurfaceContainerHigh)
                        ) {
                            TrackSort.values().forEach { sortOption ->
                                val label = when (sortOption) {
                                    TrackSort.TITLE_ASC -> "Title (A to Z)"
                                    TrackSort.TITLE_DESC -> "Title (Z to A)"
                                    TrackSort.RECENTLY_ADDED -> "Recently Added"
                                    TrackSort.DURATION -> "Duration"
                                    TrackSort.ARTIST -> "Artist"
                                    TrackSort.ALBUM -> "Album"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (sortOption == currentSort) SpotifyGreen else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        onSortChange(sortOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Shuffle All
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen)
                            .clickable {
                                if (currentTrackList.isNotEmpty()) {
                                    val randomTrack = currentTrackList.random()
                                    onTrackClick(randomTrack, currentTrackList.shuffled())
                                }
                            }
                            .testTag("music_shuffle_all_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle All",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Batch Action Bar when Selection Mode is Active
        AnimatedVisibility(visible = isSelectionMode) {
            BatchActionBar(
                selectedCount = selectedTrackIds.size,
                onPlayAll = onPlaySelected,
                onAddToQueue = onAddSelectedToQueue,
                onAddToFavorites = onAddSelectedToFavorites,
                onAddToPlaylist = onAddSelectedToPlaylist,
                onCancel = onClearSelection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Tab Content
        when (activeTab) {
            0, 4 -> {
                // Tracks or Favorites Tab
                if (currentTrackList.isEmpty()) {
                    EmptyMediaView(
                        title = if (activeTab == 4) "No favorite songs yet" else "No audio files detected",
                        subtitle = if (activeTab == 4) "Tap the heart icon on any song to add it to your favorites."
                        else "Place MP3, FLAC, M4A, OGG, or WAV files in local storage and tap Rescan.",
                        buttonText = if (activeTab == 4) "Browse Songs" else "Scan Storage",
                        onButtonClick = if (activeTab == 4) { { onTabChange(0) } } else onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("music_tracks_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentTrackList, key = { it.id }) { track ->
                            TrackItemRow(
                                track = track,
                                isSelected = currentPlayingTrack?.id == track.id,
                                onTrackClick = { onTrackClick(track, currentTrackList) },
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onLongClick = {
                                    onToggleSelectionMode(track.id)
                                },
                                onMoreOptionsClick = {
                                    onTrackOptionsClick(track)
                                },
                                isSelectionMode = isSelectionMode,
                                isChecked = selectedTrackIds.contains(track.id),
                                onCheckedChange = { onToggleTrackSelection(track.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            1 -> {
                // Albums Tab
                if (albums.isEmpty()) {
                    EmptyMediaView(
                        title = "No albums found",
                        subtitle = "Audio albums will appear here automatically when files are found.",
                        buttonText = "Rescan Media",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("albums_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            val isExpanded = expandedAlbumId == album.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedAlbumId = if (isExpanded) null else album.id
                                    }
                                    .testTag("album_card_${album.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkSurfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!album.albumArtUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = album.albumArtUri,
                                                    contentDescription = album.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Album,
                                                    contentDescription = null,
                                                    tint = SpotifyGreen,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${album.artist} • ${album.trackCount} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (album.tracks.isNotEmpty()) {
                                                            onTrackClick(album.tracks.first(), album.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play Album", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            album.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, album.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            2 -> {
                // Artists Tab
                if (artists.isEmpty()) {
                    EmptyMediaView(
                        title = "No artists found",
                        subtitle = "Artists will be indexed automatically from your on-device audio files.",
                        buttonText = "Rescan Media",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(artists, key = { it.name }) { artist ->
                            val isExpanded = expandedArtistName == artist.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedArtistName = if (isExpanded) null else artist.name
                                    }
                                    .testTag("artist_card_${artist.name}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(SpotifyGreen.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${artist.trackCount} tracks • ${artist.albumCount} albums",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (artist.tracks.isNotEmpty()) {
                                                            onTrackClick(artist.tracks.first(), artist.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play All", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            artist.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, artist.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            3 -> {
                // Folders Tab
                if (folders.isEmpty()) {
                    EmptyMediaView(
                        title = "No music folders found",
                        subtitle = "Audio directories on internal storage will appear here.",
                        buttonText = "Rescan Folders",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("music_folders_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { it.name }) { folder ->
                            val isExpanded = expandedFolderPath == folder.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedFolderPath = if (isExpanded) null else folder.name
                                    }
                                    .testTag("folder_card_${folder.name}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SpotifyGreen.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${folder.trackCount} songs in this folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (folder.tracks.isNotEmpty()) {
                                                            onTrackClick(folder.tracks.first(), folder.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play Folder", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            folder.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, folder.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }
        }
    }
}
