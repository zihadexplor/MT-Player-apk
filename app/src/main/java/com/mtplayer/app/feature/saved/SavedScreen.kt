package com.mtplayer.app.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.prefs.VideoLayout
import com.mtplayer.app.designsystem.components.ConfirmDialog
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.feature.common.CountLine
import com.mtplayer.app.feature.common.PlayShuffleRow
import com.mtplayer.app.feature.common.TopLevelBar
import com.mtplayer.app.feature.common.favoriteSongIds
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.common.songItems
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.videos.VideoCollection

private enum class SavedTab(val label: Int) {
    FAV_SONGS(R.string.saved_fav_songs), FAV_VIDEOS(R.string.saved_fav_videos),
    RECENT(R.string.recently_played), MOST(R.string.most_played), WATCHED(R.string.watch_history),
}

@Composable
fun SavedScreen() {
    val vm = LocalAppViewModel.current
    val messenger = LocalMessenger.current
    var tab by rememberSaveable { mutableStateOf(SavedTab.FAV_SONGS) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val favSongs by vm.favoriteSongs.collectAsStateWithLifecycle()
    val favVideos by vm.favoriteVideos.collectAsStateWithLifecycle()
    val recent by vm.recentSongs.collectAsStateWithLifecycle()
    val most by vm.mostPlayedSongs.collectAsStateWithLifecycle()
    val watched by vm.recentVideos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cleared = stringResource(R.string.history_cleared)

    Column(Modifier.fillMaxSize()) {
        TopLevelBar(stringResource(R.string.tab_saved)) {
            if (tab in setOf(SavedTab.RECENT, SavedTab.MOST, SavedTab.WATCHED)) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history)) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SavedTab.entries, key = { it.name }) { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(stringResource(t.label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
        when (tab) {
            SavedTab.FAV_SONGS -> SongCollection(favSongs, Icons.Rounded.FavoriteBorder, R.string.no_fav_songs_title, R.string.no_fav_songs_body)
            SavedTab.RECENT -> SongCollection(recent, Icons.Rounded.History, R.string.no_history_title, R.string.no_history_body)
            SavedTab.MOST -> SongCollection(most, Icons.AutoMirrored.Rounded.TrendingUp, R.string.no_history_title, R.string.no_history_body)
            SavedTab.FAV_VIDEOS -> if (favVideos.isEmpty()) {
                MessageState(Icons.Rounded.FavoriteBorder, stringResource(R.string.no_fav_videos_title), stringResource(R.string.no_fav_videos_body))
            } else VideoCollection(favVideos, settings.videoLayout)
            SavedTab.WATCHED -> if (watched.isEmpty()) {
                MessageState(Icons.Rounded.VideoLibrary, stringResource(R.string.no_watch_history_title), stringResource(R.string.no_history_body))
            } else VideoCollection(watched, VideoLayout.LIST)
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_q),
            body = stringResource(R.string.clear_history_body),
            confirmLabel = stringResource(R.string.clear),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = { vm.clearHistory { messenger.show(cleared) } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SongCollection(songs: List<Song>, icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, body: Int) {
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    if (songs.isEmpty()) {
        MessageState(icon, stringResource(title), stringResource(body)); return
    }
    val playing = rememberPlaying()
    val favIds = favoriteSongIds()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "head") {
            PlayShuffleRow(songs)
            CountLine(pluralStringResource(R.plurals.song_count, songs.size, songs.size))
        }
        songItems(songs, playing, favIds, onPlay = { player.playSongs(songs, it) }, onMore = { actions.openSong(it) })
    }
}
