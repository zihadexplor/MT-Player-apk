package com.mtplayer.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import com.mtplayer.app.designsystem.theme.MtType
import com.mtplayer.app.designsystem.components.Overline
import com.mtplayer.app.designsystem.components.HeaderAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
/**
 * Top bar of the main tabs: an accent overline above a large display title, with the page's
 * actions and Search / Settings as separate header tiles. Uses a compact title under 380dp.
 */
@Composable
fun TopLevelBar(title: String, overline: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNavigator.current
    val compact = LocalConfiguration.current.screenWidthDp < 380
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            if (overline != null) {
                Overline(overline)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                title,
                style = if (compact) MtType.PageTitleCompact else MtType.PageTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
        HeaderAction(Icons.Rounded.Search, stringResource(R.string.search), onClick = nav::search)
        HeaderAction(Icons.Rounded.Settings, stringResource(R.string.settings), onClick = nav::settings)
    }
}

/** Top bar of sub-pages: a back tile, the title and optional header tiles. */
@Composable
fun DetailBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNavigator.current
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onClick = nav::back)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

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
