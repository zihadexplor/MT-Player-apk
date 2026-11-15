package com.mtplayer.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.designsystem.components.MediaRow
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator

/** Header for the four top-level destinations, with Search and Settings entry points. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNavigator.current
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            actions()
            IconButton(onClick = nav::search) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
            IconButton(onClick = nav::settings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        },
        windowInsets = WindowInsets.statusBars,
        colors = barColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNavigator.current
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        },
        actions = { actions() },
        windowInsets = WindowInsets.statusBars,
        colors = barColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** "Play" + "Shuffle" pair shown above song collections. */
@Composable
fun PlayShuffleRow(songs: List<Song>, modifier: Modifier = Modifier) {
    val player = LocalContainer.current.player
    if (songs.isEmpty()) return
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { player.playSongs(songs) }, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.play))
        }
        FilledTonalButton(onClick = { player.playSongs(songs, shuffle = true) }, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.shuffle))
        }
    }
}

/** Live "what's playing" info used to highlight rows. */
data class Playing(val songId: Long?, val isPlaying: Boolean)

@Composable
fun rememberPlaying(): Playing {
    val state by LocalContainer.current.player.state.collectAsStateWithLifecycle()
    return Playing(state.current?.songId, state.isPlaying)
}

/**
 * Song rows for a LazyColumn. Tapping a row plays [songs] starting there; the overflow opens the
 * shared song action sheet. [trailing] can show e.g. track numbers.
 */
fun LazyListScope.songItems(
    songs: List<Song>,
    playing: Playing,
    favorites: Set<Long>,
    onPlay: (Int) -> Unit,
    onMore: (Song) -> Unit,
    keyPrefix: String = "s",
    subtitle: (Song) -> String = { "${it.artist} · ${Format.duration(it.durationMs)}" },
) {
    items(songs.size, key = { "$keyPrefix${songs[it].id}_$it" }, contentType = { "song" }) { i ->
        val song = songs[i]
        val active = song.id == playing.songId
        MediaRow(
            title = song.title,
            subtitle = subtitle(song),
            artwork = song.artworkUri,
            onClick = { onPlay(i) },
            active = active,
            playing = active && playing.isPlaying,
            favorite = song.id in favorites,
            onMore = { onMore(song) },
        )
    }
}

/** Convenience wrapper wiring [songItems] to the player and action sheet. */
@Composable
fun songListHandlers(songs: List<Song>): Pair<(Int) -> Unit, (Song) -> Unit> {
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    return Pair({ i: Int -> player.playSongs(songs, i) }, { s: Song -> actions.openSong(s) })
}

@Composable
fun favoriteSongIds(): Set<Long> {
    val ids by LocalAppViewModel.current.favoriteSongIds.collectAsStateWithLifecycle()
    return ids
}

@Composable
fun CountLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
fun rememberIndex(): com.mtplayer.app.data.media.LibraryIndex {
    val state by LocalAppViewModel.current.library.collectAsStateWithLifecycle()
    return (state as? com.mtplayer.app.data.media.LibraryState.Ready)?.index ?: com.mtplayer.app.data.media.LibraryIndex.EMPTY
}
