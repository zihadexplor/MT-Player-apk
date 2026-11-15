package com.mtplayer.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.SectionHeader
import com.mtplayer.app.designsystem.components.TileCard
import com.mtplayer.app.designsystem.components.VideoCard
import com.mtplayer.app.feature.common.TopLevelBar
import com.mtplayer.app.feature.common.favoriteSongIds
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.common.songItems
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.Routes
import com.mtplayer.app.feature.shell.openVideo
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen() {
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    val context = LocalContext.current
    val index = rememberIndex()
    val recent by vm.recentSongs.collectAsStateWithLifecycle()
    val most by vm.mostPlayedSongs.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val videoFavs by vm.favoriteVideoIds.collectAsStateWithLifecycle()
    val favorites = favoriteSongIds()
    val playing = rememberPlaying()
    val recentlyAdded = remember(index) { index.songs.sortedByDescending { it.dateAddedSec }.take(8) }

    Column(Modifier.fillMaxSize()) {
        TopLevelBar(stringResource(R.string.app_name))
        if (index.isEmpty) {
            MessageState(
                icon = Icons.Rounded.LibraryMusic,
                title = stringResource(R.string.empty_library_title),
                body = stringResource(R.string.empty_library_body),
                actionLabel = stringResource(R.string.rescan),
                onAction = vm::refreshLibrary,
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "quick") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickCard(
                        icon = Icons.Rounded.Shuffle,
                        title = stringResource(R.string.shuffle_all),
                        subtitle = pluralStringResource(R.plurals.song_count, index.songs.size, index.songs.size),
                        enabled = index.songs.isNotEmpty(),
                        onClick = { player.playSongs(index.songs, shuffle = true) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickCard(
                        icon = Icons.Rounded.Movie,
                        title = stringResource(R.string.tab_videos),
                        subtitle = pluralStringResource(R.plurals.video_count, index.videos.size, index.videos.size),
                        enabled = true,
                        onClick = { nav.top(Routes.VIDEOS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (continueWatching.isNotEmpty()) {
                item(key = "cw_h") { SectionHeader(stringResource(R.string.continue_watching)) }
                item(key = "cw") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(continueWatching, key = { it.video.id }) { cw ->
                            VideoCard(
                                video = cw.video,
                                progress = cw.progress,
                                favorite = cw.video.id in videoFavs,
                                onClick = { openVideo(context, cw.video, continueWatching.map { it.video }) },
                                onMore = { actions.openVideo(cw.video) },
                                modifier = Modifier.width(220.dp),
                            )
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent_h") { SectionHeader(stringResource(R.string.recently_played), actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.SAVED) }) }
                item(key = "recent") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(recent.take(15), key = { it.id }) { song ->
                            TileCard(song.title, song.artist, song.artworkUri, onClick = { player.playSongs(recent, recent.indexOf(song)) },
                                onLongClick = { actions.openSong(song) }, fallbackIcon = Icons.Rounded.Album, modifier = Modifier.width(140.dp))
                        }
                    }
                }
            }
            if (playlists.isNotEmpty()) {
                item(key = "pl_h") { SectionHeader(stringResource(R.string.tab_playlists), actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.MUSIC) }) }
                item(key = "pl") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            val art = p.firstSongId?.let { index.songById[it]?.artworkUri }
                            TileCard(p.name, pluralStringResource(R.plurals.song_count, p.songCount, p.songCount), art,
                                onClick = { nav.playlist(p.id) }, fallbackIcon = Icons.AutoMirrored.Rounded.QueueMusic, modifier = Modifier.width(140.dp))
                        }
                    }
                }
            }
            if (most.isNotEmpty()) {
                item(key = "most_h") { SectionHeader(stringResource(R.string.most_played)) }
                songItems(most.take(5), playing, favorites, onPlay = { player.playSongs(most, it) }, onMore = { actions.openSong(it) }, keyPrefix = "m")
            }
            if (recentlyAdded.isNotEmpty()) {
                item(key = "added_h") { SectionHeader(stringResource(R.string.recently_added), actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.MUSIC) }) }
                songItems(recentlyAdded, playing, favorites, onPlay = { player.playSongs(recentlyAdded, it) }, onMore = { actions.openSong(it) }, keyPrefix = "a")
            }
            item(key = "rescan") {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                    androidx.compose.material3.TextButton(onClick = vm::refreshLibrary, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rescan))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

