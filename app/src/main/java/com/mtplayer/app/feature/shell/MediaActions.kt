package com.mtplayer.app.feature.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.data.repo.UserDataRepository
import com.mtplayer.app.designsystem.components.ActionSheet
import com.mtplayer.app.designsystem.components.Artwork
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.SheetAction
import com.mtplayer.app.designsystem.components.TextInputDialog
import com.mtplayer.app.ui.image.VideoThumb
import com.mtplayer.app.video.VideoPlayerActivity

/** Holder for the one song/video sheet that can be open at a time. */
@Stable
class MediaActionsState {
    var song by mutableStateOf<Song?>(null)
    var songExtras by mutableStateOf<List<SheetAction>>(emptyList())
    var video by mutableStateOf<Video?>(null)
    var addToPlaylist by mutableStateOf<List<Song>?>(null)
    var createPlaylistFor by mutableStateOf<List<Song>?>(null)
    var info by mutableStateOf<Pair<String, List<Pair<String, String>>>?>(null)

    fun openSong(song: Song, extras: List<SheetAction> = emptyList()) { this.song = song; songExtras = extras }
    fun openVideo(video: Video) { this.video = video }
    fun addToPlaylist(songs: List<Song>) { addToPlaylist = songs }
}

val LocalMediaActions = staticCompositionLocalOf<MediaActionsState> { error("No MediaActionsState") }

fun openVideo(context: android.content.Context, video: Video, siblings: List<Video>) {
    VideoPlayerActivity.start(context, video.id, siblings.map { it.id })
}

@Composable
fun MediaActionsHost(state: MediaActionsState) {
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val player = LocalContainer.current.player
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val favSongs by vm.favoriteSongIds.collectAsStateWithLifecycle()
    val favVideos by vm.favoriteVideoIds.collectAsStateWithLifecycle()

    val addedToFavorites = stringResource(R.string.added_to_favorites)
    val removedFromFavorites = stringResource(R.string.removed_from_favorites)
    val undo = stringResource(R.string.undo)
    val queuedNext = stringResource(R.string.queued_next)
    val queuedEnd = stringResource(R.string.queued_end)
    val folderHidden = stringResource(R.string.folder_hidden)

    state.song?.let { song ->
        val fav = song.id in favSongs
        ActionSheet(
            title = song.title,
            subtitle = "${song.artist} · ${song.album}",
            header = { Artwork(song.artworkUri, null, Modifier.size(48.dp)) },
            onDismiss = { state.song = null },
            actions = listOf(
                SheetAction(Icons.Rounded.PlayArrow, stringResource(R.string.play)) { player.playSongs(listOf(song)) },
                SheetAction(Icons.Rounded.SkipNext, stringResource(R.string.play_next)) { player.playNext(listOf(song)); messenger.show(queuedNext) },
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.add_to_queue)) { player.addToQueue(listOf(song)); messenger.show(queuedEnd) },
                SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) { state.addToPlaylist(listOf(song)) },
                SheetAction(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (fav) R.string.action_unfavorite else R.string.action_favorite)) {
                    vm.toggleFavorite(MediaType.SONG, song.id) { now ->
                        messenger.show(if (now) addedToFavorites else removedFromFavorites, undo) { vm.setFavorite(MediaType.SONG, song.id, !now) }
                    }
                },
                SheetAction(Icons.Rounded.Album, stringResource(R.string.go_to_album)) { nav.album(song.albumId) },
                SheetAction(Icons.Rounded.Person, stringResource(R.string.go_to_artist)) { nav.artist(song.artist) },
                SheetAction(Icons.Rounded.Folder, stringResource(R.string.go_to_folder)) { nav.folder(song.folderPath) },
                SheetAction(Icons.Rounded.Info, stringResource(R.string.details)) {
                    state.info = song.title to songInfo(song)
                },
            ) + state.songExtras,
        )
    }

    state.video?.let { video ->
        val fav = video.id in favVideos
        ActionSheet(
            title = video.title,
            subtitle = "${video.folderName} · ${Format.duration(video.durationMs)}",
            header = { Artwork(VideoThumb(video.id, video.uri), null, Modifier.size(width = 72.dp, height = 48.dp)) },
            onDismiss = { state.video = null },
            actions = listOf(
                SheetAction(Icons.Rounded.PlayArrow, stringResource(R.string.play)) { openVideo(context, video, listOf(video)) },
                SheetAction(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (fav) R.string.action_unfavorite else R.string.action_favorite)) {
                    vm.toggleFavorite(MediaType.VIDEO, video.id) { now ->
                        messenger.show(if (now) addedToFavorites else removedFromFavorites, undo) { vm.setFavorite(MediaType.VIDEO, video.id, !now) }
                    }
                },
                SheetAction(Icons.Rounded.Folder, stringResource(R.string.open_folder)) { nav.videoFolder(video.folderPath) },
                SheetAction(Icons.Rounded.VisibilityOff, stringResource(R.string.hide_folder)) {
                    vm.hideFolder(video.folderPath, MediaType.VIDEO)
                    messenger.show(folderHidden, undo) { vm.unhideFolder(video.folderPath, MediaType.VIDEO) }
                },
                SheetAction(Icons.Rounded.Info, stringResource(R.string.details)) { state.info = video.title to videoInfo(video) },
            ),
        )
    }

    state.addToPlaylist?.let { songs -> AddToPlaylistSheet(songs, state) }

    state.createPlaylistFor?.let { songs ->
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            initial = "",
            ignoreId = null,
            onConfirm = { name ->
                vm.createPlaylist(name, songs.map { it.id }) { }
                messenger.show(context.getString(R.string.playlist_created, name))
            },
            onDismiss = { state.createPlaylistFor = null },
        )
    }

    state.info?.let { (title, rows) -> InfoSheet(title, rows) { state.info = null } }
}

@Composable
private fun songInfo(song: Song): List<Pair<String, String>> = listOfNotNull(
    stringResource(R.string.info_artist) to song.artist,
    stringResource(R.string.info_album) to song.album,
    song.genre.takeIf { it.isNotBlank() }?.let { stringResource(R.string.info_genre) to it },
    song.year.takeIf { it > 0 }?.let { stringResource(R.string.info_year) to it.toString() },
    stringResource(R.string.info_duration) to Format.duration(song.durationMs),
    stringResource(R.string.info_format) to song.mimeType.substringAfter('/').uppercase(),
    stringResource(R.string.info_size) to Format.size(song.sizeBytes),
    stringResource(R.string.info_folder) to song.folderPath,
)

@Composable
private fun videoInfo(video: Video): List<Pair<String, String>> = listOfNotNull(
    stringResource(R.string.info_duration) to Format.duration(video.durationMs),
    (video.width > 0).takeIf { it }?.let { stringResource(R.string.info_resolution) to "${video.width} × ${video.height}" },
    stringResource(R.string.info_format) to video.mimeType.substringAfter('/').uppercase(),
    stringResource(R.string.info_size) to Format.size(video.sizeBytes),
    stringResource(R.string.info_folder) to video.folderPath,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(title: String, rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            rows.forEach { (k, v) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(songs: List<Song>, state: MediaActionsState) {
    val vm = LocalAppViewModel.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { state.addToPlaylist = null }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                pluralStringResource(R.plurals.add_n_songs_to, songs.size, songs.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                item {
                    SheetRow(Icons.Rounded.Add, stringResource(R.string.new_playlist), null) {
                        state.addToPlaylist = null
                        state.createPlaylistFor = songs
                    }
                }
                items(playlists, key = { it.id }) { pl ->
                    SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, pl.name, pluralStringResource(R.plurals.song_count, pl.itemCount, pl.itemCount)) {
                        state.addToPlaylist = null
                        vm.addToPlaylist(pl.id, songs.map { it.id }) { added ->
                            messenger.show(
                                if (added == 0) context.getString(R.string.already_in_playlist, pl.name)
                                else context.resources.getQuantityString(R.plurals.added_to_playlist, added, added, pl.name)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Create/rename dialog with live validation (blank, too long, duplicate). */
@Composable
fun PlaylistNameDialog(title: String, initial: String, ignoreId: Long?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val vm = LocalAppViewModel.current
    val blank = stringResource(R.string.error_name_blank)
    val long = stringResource(R.string.error_name_long)
    val dup = stringResource(R.string.error_name_duplicate)
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    TextInputDialog(
        title = title,
        label = stringResource(R.string.playlist_name),
        initial = initial,
        confirmLabel = stringResource(if (initial.isEmpty()) R.string.create else R.string.save),
        dismissLabel = stringResource(R.string.cancel),
        validate = { name ->
            val trimmed = name.trim()
            when {
                trimmed.isEmpty() -> blank
                trimmed.length > UserDataRepository.MAX_NAME -> long
                playlists.any { it.id != ignoreId && it.name.equals(trimmed, ignoreCase = true) } -> dup
                else -> null
            }
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
