package com.mtplayer.app.feature.nowplaying

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.repo.Lyrics
import com.mtplayer.app.designsystem.components.ActionSheet
import com.mtplayer.app.designsystem.components.Artwork
import com.mtplayer.app.designsystem.components.ChoiceSheet
import com.mtplayer.app.designsystem.components.HeaderAction
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.Overline
import com.mtplayer.app.designsystem.components.SheetAction
import com.mtplayer.app.designsystem.components.pressScale
import com.mtplayer.app.designsystem.theme.MtColors
import com.mtplayer.app.designsystem.theme.MtType
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.playback.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Sheet { QUEUE, SLEEP, SPEED, MENU }

@Composable
fun NowPlayingScreen() {
    val container = LocalContainer.current
    val player = container.player
    val nav = LocalNavigator.current
    val vm = LocalAppViewModel.current
    val messenger = LocalMessenger.current
    val actions = LocalMediaActions.current
    val state by player.state.collectAsStateWithLifecycle()
    val index = rememberIndex()
    val favorites by vm.favoriteSongIds.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val current = state.current
    val song: Song? = current?.songId?.let { index.songById[it] }

    BackHandler(enabled = showLyrics) { showLyrics = false }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        if (current == null) {
            Column {
                IconButton(onClick = nav::closePlayer, modifier = Modifier.padding(4.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close)) }
                MessageState(Icons.Rounded.MusicNote, stringResource(R.string.nothing_playing_title), stringResource(R.string.nothing_playing_body))
            }
            return@Box
        }
        var reloadKey by remember { mutableStateOf(0) }
        val lyrics by produceState<LyricsLoad>(LyricsLoad.Loading, song?.id, reloadKey) {
            value = LyricsLoad.Loading
            value = song?.let { container.lyrics.load(it) }?.let { LyricsLoad.Loaded(it) } ?: LyricsLoad.None
        }
        val scope = rememberCoroutineScope()
        val linked = stringResource(R.string.lyrics_linked)
        val linkFailed = stringResource(R.string.lyrics_link_failed)
        val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && song != null) scope.launch {
                val loaded = container.lyrics.link(song, uri)
                messenger.show(if (loaded != null) linked else linkFailed)
                reloadKey++
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            Column(Modifier.fillMaxSize()) {
                TopRow(onClose = nav::closePlayer, onMenu = { sheet = Sheet.MENU }, sourceLabel = current.album)
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArtOrLyrics(showLyrics, current.artworkUri, lyrics, player, onLink = { linkLauncher.launch(arrayOf("text/*", "application/octet-stream")) })
                        }
                        Spacer(Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Details(state, song, favorites, onSheet = { sheet = it }, showLyrics = showLyrics, onToggleLyrics = { showLyrics = !showLyrics })
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        ArtOrLyrics(showLyrics, current.artworkUri, lyrics, player, onLink = { linkLauncher.launch(arrayOf("text/*", "application/octet-stream")) })
                    }
                    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                        Details(state, song, favorites, onSheet = { sheet = it }, showLyrics = showLyrics, onToggleLyrics = { showLyrics = !showLyrics })
                    }
                }
            }
        }

        when (sheet) {
            Sheet.QUEUE -> QueueSheet(state, onDismiss = { sheet = null })
            Sheet.SLEEP -> SleepSheet(state, settings.sleepFinishSong, settings.sleepFade, onDismiss = { sheet = null })
            Sheet.SPEED -> ChoiceSheet(
                stringResource(R.string.playback_speed), listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), state.speed,
                { "${it}x" }, { player.setSpeed(it) }, { sheet = null },
            )
            Sheet.MENU -> ActionSheet(
                title = current.title,
                subtitle = current.artist,
                onDismiss = { sheet = null },
                actions = buildList {
                    if (song != null) {
                        add(SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) { actions.addToPlaylist(listOf(song)) })
                        add(SheetAction(Icons.Rounded.Album, stringResource(R.string.go_to_album)) { nav.back(); nav.album(song.albumId) })
                        add(SheetAction(Icons.Rounded.Person, stringResource(R.string.go_to_artist)) { nav.back(); nav.artist(song.artist) })
                        add(SheetAction(Icons.Rounded.Lyrics, stringResource(R.string.link_lyrics)) { linkLauncher.launch(arrayOf("text/*", "application/octet-stream")) })
                        if ((lyrics as? LyricsLoad.Loaded)?.lyrics?.source == Lyrics.Source.LINKED) {
                            add(SheetAction(Icons.Rounded.Close, stringResource(R.string.unlink_lyrics)) { scope.launch { container.lyrics.unlink(song); reloadKey++ } })
                        }
                    }
                    add(SheetAction(Icons.Rounded.Speed, stringResource(R.string.playback_speed)) { sheet = Sheet.SPEED })
                    add(SheetAction(Icons.Rounded.GraphicEq, stringResource(R.string.equalizer)) { nav.equalizer() })
                },
            )
            null -> {}
        }
    }
}

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object None : LyricsLoad
    data class Loaded(val lyrics: Lyrics) : LyricsLoad
}

@Composable
private fun TopRow(onClose: () -> Unit, onMenu: () -> Unit, sourceLabel: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderAction(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close), onClose)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline(stringResource(R.string.now_playing))
            if (sourceLabel.isNotBlank()) Text(
                sourceLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderAction(Icons.Rounded.MoreVert, stringResource(R.string.more_options), onMenu)
    }
}

@Composable
private fun ArtOrLyrics(showLyrics: Boolean, artwork: Any?, lyrics: LyricsLoad, player: com.mtplayer.app.playback.PlayerConnection, onLink: () -> Unit) {
    Crossfade(showLyrics, label = "art_lyrics") { lyricsMode ->
        if (!lyricsMode) {
            Artwork(
                artwork, null,
                Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                shape = MaterialTheme.shapes.extraLarge, iconSize = 96.dp, sizePx = 1024,
            )
        } else {
            LyricsPanel(lyrics, player, onLink)
        }
    }
}

@Composable
private fun LyricsPanel(load: LyricsLoad, player: com.mtplayer.app.playback.PlayerConnection, onLink: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (load) {
        LyricsLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LyricsLoad.None -> MessageState(
            Icons.Rounded.Lyrics, stringResource(R.string.no_lyrics_title), stringResource(R.string.no_lyrics_body),
            actionLabel = stringResource(R.string.link_lyrics), onAction = onLink,
        )
        is LyricsLoad.Loaded -> {
            val lyrics = load.lyrics
            if (!lyrics.isSynced) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
                    item { Text(lyrics.plain, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.3f) }
                }
            } else {
                val ticks by remember(player) { player.positionTicks(200) }.collectAsStateWithLifecycle(0L to 0L)
                val active = lyrics.activeIndex(ticks.first)
                val listState = rememberLazyListState()
                LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 120.dp)) {
                    itemsIndexed(lyrics.lines, key = { i, l -> "$i${l.timeMs}" }) { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (i == active) FontWeight.Bold else FontWeight.Medium),
                            color = if (i == active) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { player.seekTo(line.timeMs) }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(
    state: PlayerUiState,
    song: Song?,
    favorites: Set<Long>,
    onSheet: (Sheet) -> Unit,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
) {
    val player = LocalContainer.current.player
    val vm = LocalAppViewModel.current
    val messenger = LocalMessenger.current
    val current = state.current ?: return
    val scheme = MaterialTheme.colorScheme
    val fav = song != null && song.id in favorites
    val added = stringResource(R.string.added_to_favorites)
    val removed = stringResource(R.string.removed_from_favorites)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(current.title, style = MtType.HeroTitle, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current.artist, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (song != null) {
            IconButton(onClick = { vm.toggleFavorite(MediaType.SONG, song.id) { now -> messenger.show(if (now) added else removed) } }) {
                Icon(
                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (fav) R.string.action_unfavorite else R.string.action_favorite),
                    tint = if (fav) MtColors.current.favorite else scheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SeekBar(current.durationMs, player)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val shuffleLabel = stringResource(R.string.action_shuffle)
        val on = stringResource(R.string.on); val off = stringResource(R.string.off)
        IconButton(onClick = { player.setShuffle(!state.shuffle) }, modifier = Modifier.semantics { stateDescription = if (state.shuffle) on else off }) {
            Icon(Icons.Rounded.Shuffle, shuffleLabel, tint = if (state.shuffle) scheme.primary else scheme.onSurfaceVariant)
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(36.dp), tint = scheme.onSurface)
        }
        val playInteraction = remember { MutableInteractionSource() }
        val playScale = pressScale(playInteraction, 0.92f)
        FilledIconButton(
            onClick = player::togglePlay,
            modifier = Modifier.size(76.dp).graphicsLayer { scaleX = playScale; scaleY = playScale },
            interactionSource = playInteraction,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
        ) {
            if (state.buffering && !state.isPlaying) CircularProgressIndicator(Modifier.size(28.dp), color = scheme.onPrimary, strokeWidth = 3.dp)
            else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.isPlaying) R.string.pause else R.string.play), Modifier.size(40.dp))
        }
        IconButton(onClick = player::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(36.dp),
                tint = if (state.hasNext) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f))
        }
        val repeatState = stringResource(when (state.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.repeat_one
            Player.REPEAT_MODE_ALL -> R.string.repeat_all
            else -> R.string.repeat_off
        })
        IconButton(onClick = player::cycleRepeat, modifier = Modifier.semantics { stateDescription = repeatState }) {
            Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(R.string.action_repeat),
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) scheme.primary else scheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BottomAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue)) { onSheet(Sheet.QUEUE) }
        BottomAction(Icons.Rounded.Lyrics, stringResource(R.string.lyrics), selected = showLyrics, enabled = song != null, onClick = onToggleLyrics)
        SleepAction(state.sleepEndAt) { onSheet(Sheet.SLEEP) }
        BottomAction(Icons.Rounded.Speed, "${state.speed}x", selected = state.speed != 1f) { onSheet(Sheet.SPEED) }
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        selected -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Column(
        Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun SleepAction(endAt: Long, onClick: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) { while (endAt > 0) { now = System.currentTimeMillis(); delay(1_000) } }
    val remaining = (endAt - now).coerceAtLeast(0)
    val label = if (endAt > 0) Format.duration(remaining) else stringResource(R.string.sleep_timer)
    BottomAction(Icons.Rounded.Bedtime, label, selected = endAt > 0, onClick = onClick)
}

@Composable
private fun SeekBar(durationMs: Long, player: com.mtplayer.app.playback.PlayerConnection) {
    val ticks by remember(player) { player.positionTicks(250) }.collectAsStateWithLifecycle(0L to 0L)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val position = dragging?.let { (it * duration).toLong() } ?: ticks.first.coerceAtMost(duration)
    val seekLabel = stringResource(R.string.seek)
    Slider(
        value = dragging ?: (ticks.first.toFloat() / duration).coerceIn(0f, 1f),
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { player.seekTo((it * duration).toLong()) }; dragging = null },
        enabled = durationMs > 0,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(Format.duration(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(if (durationMs > 0) Format.duration(durationMs) else "--:--", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    val player = LocalContainer.current.player
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.currentIndex - 1).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(R.string.queue) + " · ${state.queue.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(state.queue, key = { _, e -> "${e.index}_${e.mediaId}" }) { i, entry ->
                val active = i == state.currentIndex
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { player.skipTo(i) }.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(entry.artworkUri, null, Modifier.size(44.dp), sizePx = 128)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { player.move(i, i - 1) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = { player.move(i, i + 1) }, enabled = i < state.queue.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                    }
                    IconButton(onClick = { player.removeAt(i) }, enabled = !active || state.queue.size > 1) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_queue))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(state: PlayerUiState, finishSong: Boolean, fade: Boolean, onDismiss: () -> Unit) {
    val player = LocalContainer.current.player
    val messenger = LocalMessenger.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val cancelled = stringResource(R.string.sleep_cancelled)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(if (finishSong) R.string.sleep_hint_finish else R.string.sleep_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            listOf(5, 15, 30, 45, 60, 90).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        OutlinedButton(
                            onClick = { player.setSleepTimer(m, finishSong, fade); messenger.show(context.getString(R.string.sleep_set, m)); onDismiss() },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.minutes_short, m), textAlign = TextAlign.Center) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (state.sleepEndAt > 0) {
                TextButton(onClick = { player.setSleepTimer(0, finishSong, fade); messenger.show(cancelled); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sleep_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
