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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.designsystem.components.Artwork
import com.mtplayer.app.designsystem.components.ContinueCard
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.SectionHeader
import com.mtplayer.app.designsystem.components.SquareCard
import com.mtplayer.app.designsystem.components.VideoCard
import com.mtplayer.app.designsystem.components.rememberGreeting
import com.mtplayer.app.feature.common.TopLevelBar
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.common.rememberPlaying
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.Routes
import com.mtplayer.app.feature.shell.openVideo
import com.mtplayer.app.ui.image.VideoThumb

private val Gutter = 16.dp
private val RowGap = 12.dp
private val RowPadding = PaddingValues(horizontal = Gutter)

/**
 * Home — one calm page, top to bottom: the greeting top bar, "Jump back in" (last song + last
 * video), then rows built from this device's own history and library. Everything is local.
 */
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
    val playerState by player.state.collectAsStateWithLifecycle()
    val playing = rememberPlaying()
    val fresh = remember(index) {
        (index.songs.sortedByDescending { it.dateAddedSec }.take(10).map { it.dateAddedSec to (it as Any) } +
            index.videos.sortedByDescending { it.dateAddedSec }.take(6).map { it.dateAddedSec to (it as Any) })
            .sortedByDescending { it.first }.take(12).map { it.second }
    }

    Column(Modifier.fillMaxSize()) {
        TopLevelBar(rememberGreeting(), overline = stringResource(R.string.overline_home))
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
        LazyColumn(Modifier.fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            // ---- Jump back in ---------------------------------------------------------------
            item(key = "jump_h", contentType = "header") {
                SectionHeader(stringResource(R.string.jump_back_in), overline = stringResource(R.string.jump_back_in_overline))
            }
            item(key = "jump", contentType = "continue") {
                val current = playerState.current?.takeIf { !it.isVideo && it.songId != null }
                val lastSong: Song? = current?.songId?.let { index.songById[it] } ?: recent.firstOrNull()
                val isCurrent = current != null && lastSong != null && current.songId == lastSong.id
                val ticks = remember(player) { player.positionTicks(500) }.collectAsStateWithLifecycle(0L to 0L)
                val playSong = {
                    when {
                        isCurrent -> player.togglePlay()
                        lastSong != null -> player.playSongs(listOf(lastSong) + recent.filter { it.id != lastSong.id }, 0)
                        else -> nav.top(Routes.MUSIC)
                    }
                }
                val cw = continueWatching.firstOrNull()
                val openCw = { if (cw != null) openVideo(context, cw.video, continueWatching.map { it.video }) else nav.top(Routes.VIDEOS) }
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                    ContinueCard(
                        eyebrow = stringResource(if (isCurrent && playerState.isPlaying) R.string.continue_music_playing else R.string.continue_music_last),
                        title = lastSong?.title ?: stringResource(R.string.continue_nothing_yet),
                        subtitle = lastSong?.artist ?: stringResource(R.string.continue_music_empty),
                        progress = {
                            val d = current?.durationMs ?: 0L
                            if (isCurrent && d > 0) ticks.value.first.toFloat() / d else 0f
                        },
                        playIcon = if (isCurrent && playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        playLabel = stringResource(
                            when {
                                isCurrent && playerState.isPlaying -> R.string.pause
                                lastSong == null -> R.string.open_music
                                else -> R.string.play
                            },
                        ),
                        onClick = { if (isCurrent) nav.player() else playSong() },
                        onPlay = playSong,
                        modifier = Modifier.weight(1f),
                        tag = "home_continue_music",
                    ) {
                        Artwork(lastSong?.artworkUri, contentDescription = null, modifier = Modifier.matchParentSize(),
                            shape = androidx.compose.ui.graphics.RectangleShape, fallbackIcon = Icons.Rounded.MusicNote, iconSize = 36.dp, sizePx = 400)
                    }
                    ContinueCard(
                        eyebrow = stringResource(R.string.continue_video_last),
                        title = cw?.video?.title ?: stringResource(R.string.continue_nothing_yet),
                        subtitle = cw?.video?.folderName ?: stringResource(R.string.continue_video_empty),
                        progress = { cw?.progress ?: 0f },
                        playIcon = if (cw == null) Icons.Rounded.VideoLibrary else Icons.Rounded.PlayArrow,
                        playLabel = stringResource(if (cw == null) R.string.open_videos else R.string.resume),
                        onClick = openCw,
                        onPlay = openCw,
                        modifier = Modifier.weight(1f),
                        tag = "home_continue_video",
                    ) {
                        Artwork(cw?.video?.let { VideoThumb(it.id, it.uri) }, contentDescription = null, modifier = Modifier.matchParentSize(),
                            shape = androidx.compose.ui.graphics.RectangleShape, fallbackIcon = Icons.Rounded.Movie, iconSize = 36.dp)
                    }
                }
            }

            // ---- Keep watching (more in-progress videos) -----------------------------------
            if (continueWatching.size > 1) {
                item(key = "cw_h", contentType = "header") { SectionHeader(stringResource(R.string.keep_watching), overline = stringResource(R.string.overline_videos)) }
                item(key = "cw", contentType = "video_row") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(continueWatching.drop(1), key = { it.video.id }, contentType = { "video" }) { cw ->
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

            // ---- Recently played -------------------------------------------------------------
            if (recent.isNotEmpty()) {
                item(key = "recent_h", contentType = "header") {
                    SectionHeader(stringResource(R.string.recently_played), overline = stringResource(R.string.overline_history),
                        actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.SAVED) })
                }
                item(key = "recent", contentType = "square_row") {
                    val list = recent.take(15)
                    SongRow(list, playing.songId, onPlay = { player.playSongs(recent, recent.indexOf(it)) }, onMore = { actions.openSong(it) })
                }
            }

            // ---- Your playlists ----------------------------------------------------------------
            if (playlists.isNotEmpty()) {
                item(key = "pl_h", contentType = "header") {
                    SectionHeader(stringResource(R.string.your_playlists), overline = stringResource(R.string.overline_library),
                        actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.MUSIC) })
                }
                item(key = "pl", contentType = "square_row") {
                    LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                        items(playlists, key = { it.id }, contentType = { "playlist" }) { p ->
                            val art = p.firstSongId?.let { index.songById[it]?.artworkUri }
                            SquareCard(p.name, pluralStringResource(R.plurals.song_count, p.songCount, p.songCount), art,
                                fallbackIcon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = { nav.playlist(p.id) })
                        }
                    }
                }
            }

            // ---- Most played -------------------------------------------------------------------
            if (most.isNotEmpty()) {
                item(key = "most_h", contentType = "header") {
                    SectionHeader(stringResource(R.string.most_played), overline = stringResource(R.string.overline_favorites))
                }
                item(key = "most", contentType = "square_row") {
                    SongRow(most.take(15), playing.songId, onPlay = { player.playSongs(most, most.indexOf(it)) }, onMore = { actions.openSong(it) })
                }
            }

            // ---- New on this device (songs and videos by date added) ---------------------------
            if (fresh.isNotEmpty()) {
                item(key = "new_h", contentType = "header") {
                    SectionHeader(stringResource(R.string.new_on_device), overline = stringResource(R.string.overline_fresh),
                        actionLabel = stringResource(R.string.see_all), onAction = { nav.top(Routes.MUSIC) })
                }
                item(key = "new", contentType = "square_row") {
                    val songs = remember(fresh) { fresh.filterIsInstance<Song>() }
                    val videos = remember(fresh) { fresh.filterIsInstance<Video>() }
                    LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                        items(fresh, key = { if (it is Song) "s${it.id}" else "v${(it as Video).id}" }, contentType = { if (it is Song) "song" else "video" }) { a ->
                            when (a) {
                                is Song -> SquareCard(a.title, a.artist, a.artworkUri, fallbackIcon = Icons.Rounded.Album,
                                    badgeIcon = Icons.Rounded.MusicNote, highlight = playing.songId == a.id,
                                    onClick = { player.playSongs(songs, songs.indexOf(a)) }, onLongClick = { actions.openSong(a) })
                                is Video -> SquareCard(a.title, a.folderName, VideoThumb(a.id, a.uri), fallbackIcon = Icons.Rounded.Movie,
                                    badgeIcon = Icons.Rounded.PlayArrow,
                                    onClick = { openVideo(context, a, videos) }, onLongClick = { actions.openVideo(a) })
                            }
                        }
                    }
                }
            }

            item(key = "rescan", contentType = "footer") {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = vm::refreshLibrary, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rescan))
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(songs: List<Song>, playingId: Long?, onPlay: (Song) -> Unit, onMore: (Song) -> Unit) {
    LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
        items(songs, key = { it.id }, contentType = { "song" }) { song ->
            SquareCard(song.title, song.artist, song.artworkUri, fallbackIcon = Icons.Rounded.Album,
                badgeIcon = Icons.Rounded.MusicNote, highlight = playingId == song.id,
                onClick = { onPlay(song) }, onLongClick = { onMore(song) })
        }
    }
}
