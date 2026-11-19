package com.mtplayer.app.feature.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.data.media.LibraryIndex
import com.mtplayer.app.data.media.SongSort
import com.mtplayer.app.data.media.sortedBy
import com.mtplayer.app.designsystem.components.ChoiceSheet
import com.mtplayer.app.designsystem.components.HeaderAction
import com.mtplayer.app.designsystem.components.MediaRow
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.PillTabs
import com.mtplayer.app.designsystem.components.TileCard
import com.mtplayer.app.feature.common.CountLine
import com.mtplayer.app.feature.common.PlayShuffleRow
import com.mtplayer.app.feature.common.TopLevelBar
import com.mtplayer.app.feature.common.favoriteSongIds
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.common.songItems
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.PlaylistNameDialog
import kotlinx.coroutines.launch

private enum class MusicTab(val label: Int) {
    SONGS(R.string.tab_songs), ALBUMS(R.string.tab_albums), ARTISTS(R.string.tab_artists),
    PLAYLISTS(R.string.tab_playlists), GENRES(R.string.tab_genres), FOLDERS(R.string.tab_folders),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val tabs = MusicTab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val index = rememberIndex()
    val vm = LocalAppViewModel.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sortOpen by remember { mutableStateOf(false) }
    var newPlaylist by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopLevelBar(stringResource(R.string.tab_music), overline = stringResource(R.string.overline_music)) {
            when (tabs[pager.currentPage]) {
                MusicTab.SONGS -> HeaderAction(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort), onClick = { sortOpen = true })
                MusicTab.PLAYLISTS -> HeaderAction(Icons.Rounded.Add, stringResource(R.string.new_playlist), onClick = { newPlaylist = true })
                else -> {}
            }
        }
        PillTabs(
            labels = tabs.map { stringResource(it.label) },
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.padding(bottom = 4.dp),
        )
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { tabs[it].name }) { page ->
            when (tabs[page]) {
                MusicTab.SONGS -> SongsTab(index, settings.songSort)
                MusicTab.ALBUMS -> AlbumsTab(index)
                MusicTab.ARTISTS -> ArtistsTab(index)
                MusicTab.PLAYLISTS -> PlaylistsTab(index, onCreate = { newPlaylist = true })
                MusicTab.GENRES -> GenresTab(index)
                MusicTab.FOLDERS -> FoldersTab(index)
            }
        }
    }

    if (sortOpen) {
        ChoiceSheet(
            title = stringResource(R.string.sort_by),
            options = SongSort.entries,
            selected = settings.songSort,
            label = { stringResource(sortLabel(it)) },
            onSelect = { s -> vm.updateSettings { it.copy(songSort = s) } },
            onDismiss = { sortOpen = false },
        )
    }
    if (newPlaylist) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            initial = "",
            ignoreId = null,
            onConfirm = { vm.createPlaylist(it) },
            onDismiss = { newPlaylist = false },
        )
    }
}

fun sortLabel(s: SongSort) = when (s) {
    SongSort.TITLE -> R.string.sort_title
    SongSort.ARTIST -> R.string.sort_artist
    SongSort.ALBUM -> R.string.sort_album
    SongSort.DATE_ADDED -> R.string.sort_date_added
    SongSort.DURATION -> R.string.sort_duration
}

@Composable
private fun SongsTab(index: LibraryIndex, sort: SongSort) {
    val songs = remember(index, sort) { index.songs.sortedBy(sort) }
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    if (songs.isEmpty()) {
        MessageState(Icons.Rounded.MusicNote, stringResource(R.string.no_songs_title), stringResource(R.string.no_songs_body))
        return
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

@Composable
private fun AlbumsTab(index: LibraryIndex) {
    val nav = LocalNavigator.current
    if (index.albums.isEmpty()) {
        MessageState(Icons.Rounded.Album, stringResource(R.string.no_albums_title), stringResource(R.string.no_songs_body)); return
    }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
        items(index.albums, key = { it.id }) { a ->
            TileCard(a.title, a.artist, a.artworkUri, onClick = { nav.album(a.id) }, fallbackIcon = Icons.Rounded.Album)
        }
    }
}

@Composable
private fun ArtistsTab(index: LibraryIndex) {
    val nav = LocalNavigator.current
    if (index.artists.isEmpty()) {
        MessageState(Icons.Rounded.Person, stringResource(R.string.no_artists_title), stringResource(R.string.no_songs_body)); return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(index.artists, key = { it.name }) { a ->
            MediaRow(
                title = a.name,
                subtitle = pluralStringResource(R.plurals.album_count, a.albumCount, a.albumCount) + " · " +
                    pluralStringResource(R.plurals.song_count, a.songs.size, a.songs.size),
                artwork = a.artworkUri,
                artworkShape = androidx.compose.foundation.shape.CircleShape,
                fallbackIcon = Icons.Rounded.Person,
                onClick = { nav.artist(a.name) },
            )
        }
    }
}

@Composable
private fun GenresTab(index: LibraryIndex) {
    val nav = LocalNavigator.current
    if (index.genres.isEmpty()) {
        MessageState(Icons.Rounded.Category, stringResource(R.string.no_genres_title), stringResource(R.string.no_genres_body)); return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(index.genres, key = { it.name }) { g ->
            MediaRow(g.name, pluralStringResource(R.plurals.song_count, g.songs.size, g.songs.size),
                artwork = g.songs.firstOrNull()?.artworkUri, fallbackIcon = Icons.Rounded.Category, onClick = { nav.genre(g.name) })
        }
    }
}

@Composable
private fun FoldersTab(index: LibraryIndex) {
    val nav = LocalNavigator.current
    if (index.musicFolders.isEmpty()) {
        MessageState(Icons.Rounded.Folder, stringResource(R.string.no_folders_title), stringResource(R.string.no_songs_body)); return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(index.musicFolders, key = { it.path }) { f ->
            MediaRow(f.name, pluralStringResource(R.plurals.song_count, f.songs.size, f.songs.size) + " · " + f.path,
                artwork = null, fallbackIcon = Icons.Rounded.Folder, onClick = { nav.folder(f.path) })
        }
    }
}

@Composable
private fun PlaylistsTab(index: LibraryIndex, onCreate: () -> Unit) {
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    if (playlists.isEmpty()) {
        MessageState(
            Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.no_playlists_title), stringResource(R.string.no_playlists_body),
            actionLabel = stringResource(R.string.new_playlist), onAction = onCreate,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(playlists, key = { it.id }) { p ->
            MediaRow(
                title = p.name,
                subtitle = pluralStringResource(R.plurals.song_count, p.songCount, p.songCount),
                artwork = p.firstSongId?.let { index.songById[it]?.artworkUri },
                fallbackIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { nav.playlist(p.id) },
            )
        }
    }
}
