package com.mtplayer.app.feature.detail

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.designsystem.components.ActionSheet
import com.mtplayer.app.designsystem.components.Artwork
import com.mtplayer.app.designsystem.components.ConfirmDialog
import com.mtplayer.app.designsystem.components.HeaderAction
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.SectionHeader
import com.mtplayer.app.designsystem.components.SheetAction
import com.mtplayer.app.designsystem.components.TileCard
import com.mtplayer.app.designsystem.theme.MtType
import com.mtplayer.app.feature.common.DetailBar
import com.mtplayer.app.feature.common.PlayShuffleRow
import com.mtplayer.app.feature.common.favoriteSongIds
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.common.songItems
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.PlaylistNameDialog
import com.mtplayer.app.feature.videos.VideoCollection

@Composable
private fun NotFound(title: String) {
    Column(Modifier.fillMaxSize()) {
        DetailBar(title)
        MessageState(Icons.Rounded.SearchOff, stringResource(R.string.not_found_title), stringResource(R.string.not_found_body))
    }
}

/** Large artwork header used by album/artist/playlist. */
@Composable
private fun CollectionHeader(artwork: Any?, title: String, lines: List<String>, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(artwork, null, Modifier.size(128.dp), shape = MaterialTheme.shapes.large, fallbackIcon = icon, iconSize = 48.dp, sizePx = 384)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MtType.HeroTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            lines.filter { it.isNotBlank() }.forEach {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SongCollectionScreen(
    title: String,
    songs: List<Song>,
    header: @Composable () -> Unit,
    barActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    subtitle: ((Song) -> String)? = null,
    extra: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
) {
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    val favorites = favoriteSongIds()
    val playing = rememberPlaying()
    Column(Modifier.fillMaxSize()) {
        DetailBar(title, barActions)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header") { header(); PlayShuffleRow(songs) }
            if (subtitle != null) {
                songItems(songs, playing, favorites, onPlay = { player.playSongs(songs, it) }, onMore = { actions.openSong(it) }, subtitle = subtitle)
            } else {
                songItems(songs, playing, favorites, onPlay = { player.playSongs(songs, it) }, onMore = { actions.openSong(it) })
            }
            extra?.invoke(this)
        }
    }
}

@Composable
fun AlbumScreen(id: Long) {
    val index = rememberIndex()
    val album = remember(index, id) { index.album(id) } ?: return NotFound(stringResource(R.string.tab_albums))
    SongCollectionScreen(
        title = album.title,
        songs = album.songs,
        subtitle = { s -> (if (s.trackNumber > 0) "${s.trackNumber % 1000}. " else "") + Format.duration(s.durationMs) },
        header = {
            CollectionHeader(album.artworkUri, album.title, listOf(
                album.artist,
                listOfNotNull(album.year.takeIf { it > 0 }?.toString(),
                    pluralStringResource(R.plurals.song_count, album.songs.size, album.songs.size),
                    Format.longDuration(album.durationMs)).joinToString(" · "),
            ), Icons.Rounded.Album)
        },
    )
}

@Composable
fun ArtistScreen(name: String) {
    val index = rememberIndex()
    val nav = LocalNavigator.current
    val artist = remember(index, name) { index.artist(name) } ?: return NotFound(name)
    val albums = remember(index, name) { index.albums.filter { a -> a.songs.any { it.artist == name } } }
    SongCollectionScreen(
        title = artist.name,
        songs = artist.songs,
        header = {
            CollectionHeader(artist.artworkUri, artist.name, listOf(
                pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount) + " · " +
                    pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
            ), Icons.Rounded.Album)
            if (albums.size > 1) {
                SectionHeader(stringResource(R.string.tab_albums))
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(albums, key = { it.id }) { a ->
                        TileCard(a.title, a.year.takeIf { it > 0 }?.toString() ?: a.artist, a.artworkUri, onClick = { nav.album(a.id) },
                            fallbackIcon = Icons.Rounded.Album, modifier = Modifier.width(140.dp))
                    }
                }
                SectionHeader(stringResource(R.string.tab_songs))
            }
        },
    )
}

@Composable
fun GenreScreen(name: String) {
    val index = rememberIndex()
    val genre = remember(index, name) { index.genre(name) } ?: return NotFound(name)
    SongCollectionScreen(genre.name, genre.songs, header = {
        CollectionHeader(genre.songs.firstOrNull()?.artworkUri, genre.name,
            listOf(pluralStringResource(R.plurals.song_count, genre.songs.size, genre.songs.size)), Icons.Rounded.Album)
    })
}

@Composable
fun FolderScreen(path: String) {
    val index = rememberIndex()
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val messenger = LocalMessenger.current
    val folder = remember(index, path) { index.musicFolder(path) } ?: return NotFound(path.substringAfterLast('/'))
    val hidden = stringResource(R.string.folder_hidden)
    val undo = stringResource(R.string.undo)
    SongCollectionScreen(
        title = folder.name,
        songs = folder.songs,
        barActions = {
            HeaderAction(Icons.Rounded.VisibilityOff, stringResource(R.string.hide_folder), onClick = {
                vm.hideFolder(folder.path, MediaType.SONG)
                messenger.show(hidden, undo) { vm.unhideFolder(folder.path, MediaType.SONG) }
                nav.back()
            })
        },
        header = {
            CollectionHeader(null, folder.name, listOf(folder.path, pluralStringResource(R.plurals.song_count, folder.songs.size, folder.songs.size)), Icons.Rounded.Folder)
        },
    )
}

@Composable
fun VideoFolderScreen(path: String) {
    val index = rememberIndex()
    val vm = LocalAppViewModel.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val folder = remember(index, path) { index.videoFolder(path) } ?: return NotFound(path.substringAfterLast('/'))
    Column(Modifier.fillMaxSize()) {
        DetailBar(folder.name)
        VideoCollection(folder.videos, settings.videoLayout)
    }
}

@Composable
fun PlaylistScreen(id: Long) {
    val container = LocalContainer.current
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val messenger = LocalMessenger.current
    val actions = LocalMediaActions.current
    val context = LocalContext.current
    val index = rememberIndex()
    val playlistFlow = remember(id) { container.userData.playlist(id) }
    val idsFlow = remember(id) { container.userData.playlistSongIds(id) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(initialValue = null)
    val ids by idsFlow.collectAsStateWithLifecycle(initialValue = null)
    var menu by remember { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val favorites = favoriteSongIds()
    val playing = rememberPlaying()

    val p = playlist
    val songIds = ids
    if (songIds == null) return Column(Modifier.fillMaxSize()) { DetailBar("") }
    if (p == null) return NotFound(stringResource(R.string.tab_playlists))
    val songs = remember(songIds, index) { songIds.mapNotNull { index.songById[it] } }
    val missing = songIds.size - songs.size
    val removed = stringResource(R.string.removed_from_playlist)
    val undo = stringResource(R.string.undo)

    Column(Modifier.fillMaxSize()) {
        DetailBar(p.name) {
            HeaderAction(Icons.Rounded.MoreVert, stringResource(R.string.more_options_for, p.name), onClick = { menu = true })
        }
        if (songs.isEmpty()) {
            MessageState(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.empty_playlist_title),
                stringResource(if (missing > 0) R.string.empty_playlist_missing else R.string.empty_playlist_body))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "header") {
                    CollectionHeader(songs.first().artworkUri, p.name, listOf(
                        pluralStringResource(R.plurals.song_count, songs.size, songs.size) + " · " + Format.longDuration(songs.sumOf { it.durationMs }),
                        if (missing > 0) pluralStringResource(R.plurals.unavailable_count, missing, missing) else "",
                    ), Icons.AutoMirrored.Rounded.QueueMusic)
                    PlayShuffleRow(songs)
                }
                songItems(songs, playing, favorites, onPlay = { container.player.playSongs(songs, it) }, onMore = { song ->
                    val pos = songIds.indexOf(song.id)
                    actions.openSong(song, listOf(
                        SheetAction(Icons.Rounded.ArrowUpward, context.getString(R.string.move_up), enabled = pos > 0) { vm.reorderPlaylist(id, pos, pos - 1) },
                        SheetAction(Icons.Rounded.ArrowDownward, context.getString(R.string.move_down), enabled = pos in 0 until songIds.lastIndex) { vm.reorderPlaylist(id, pos, pos + 1) },
                        SheetAction(Icons.Rounded.RemoveCircleOutline, context.getString(R.string.remove_from_playlist), destructive = true) {
                            vm.removeFromPlaylist(id, song.id)
                            messenger.show(removed, undo) { vm.addToPlaylist(id, listOf(song.id)) { _ -> if (pos >= 0) vm.reorderPlaylist(id, songIds.size - 1, pos) } }
                        },
                    ))
                })
            }
        }
    }

    if (menu) {
        ActionSheet(
            title = p.name,
            subtitle = null,
            onDismiss = { menu = false },
            actions = listOf(
                SheetAction(Icons.Rounded.Edit, stringResource(R.string.rename)) { rename = true },
                SheetAction(Icons.Rounded.Delete, stringResource(R.string.delete_playlist), destructive = true) { confirmDelete = true },
            ),
        )
    }
    if (rename) {
        PlaylistNameDialog(stringResource(R.string.rename_playlist), p.name, ignoreId = id, onConfirm = { vm.renamePlaylist(id, it) }, onDismiss = { rename = false })
    }
    if (confirmDelete) {
        val deleted = stringResource(R.string.playlist_deleted, p.name)
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_q, p.name),
            body = stringResource(R.string.delete_playlist_body),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                val name = p.name
                vm.deletePlaylist(id, name) { restored ->
                    messenger.show(deleted, undo) { vm.createPlaylist(name, restored) }
                }
                nav.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
