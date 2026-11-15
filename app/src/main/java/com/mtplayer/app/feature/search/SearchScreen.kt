package com.mtplayer.app.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.media.SearchResults
import com.mtplayer.app.designsystem.components.MediaRow
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.SectionHeader
import com.mtplayer.app.feature.common.favoriteSongIds
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.common.songItems
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.openVideo
import com.mtplayer.app.ui.image.VideoThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Local-only search over the in-memory library index (no network). */
@Composable
fun SearchScreen() {
    val nav = LocalNavigator.current
    val player = LocalContainer.current.player
    val actions = LocalMediaActions.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val index = rememberIndex()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results by produceState(SearchResults.EMPTY, query, index) {
        if (query.isBlank()) { value = SearchResults.EMPTY; return@produceState }
        delay(150) // debounce typing
        value = withContext(Dispatchers.Default) { index.search(query) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        when {
            query.isBlank() -> MessageState(Icons.Rounded.Search, stringResource(R.string.search_idle_title), stringResource(R.string.search_idle_body))
            results.isEmpty -> MessageState(Icons.Rounded.SearchOff, stringResource(R.string.search_none_title), stringResource(R.string.search_none_body, query))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (results.songs.isNotEmpty()) {
                    item(key = "sh") { SectionHeader(stringResource(R.string.tab_songs)) }
                    songItems(results.songs, rememberPlaying(), favoriteSongIds(),
                        onPlay = { player.playSongs(results.songs, it) }, onMore = { actions.openSong(it) })
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "arh") { SectionHeader(stringResource(R.string.tab_artists)) }
                    items(results.artists, key = { "ar" + it.name }) { a ->
                        MediaRow(a.name, pluralStringResource(R.plurals.song_count, a.songs.size, a.songs.size), a.artworkUri,
                            onClick = { nav.artist(a.name) }, artworkShape = CircleShape, fallbackIcon = Icons.Rounded.Person)
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "alh") { SectionHeader(stringResource(R.string.tab_albums)) }
                    items(results.albums, key = { "al" + it.id }) { a ->
                        MediaRow(a.title, a.artist, a.artworkUri, onClick = { nav.album(a.id) }, fallbackIcon = Icons.Rounded.Album)
                    }
                }
                if (results.videos.isNotEmpty()) {
                    item(key = "vh") { SectionHeader(stringResource(R.string.tab_videos)) }
                    items(results.videos, key = { "v" + it.id }) { v ->
                        MediaRow(v.title, "${v.folderName} · ${Format.duration(v.durationMs)}", VideoThumb(v.id, v.uri),
                            onClick = { openVideo(context, v, results.videos) }, onMore = { actions.openVideo(v) }, fallbackIcon = Icons.Rounded.Movie)
                    }
                }
            }
        }
    }
}
