package com.mtplayer.app.feature.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.data.media.VideoSort
import com.mtplayer.app.data.media.sortedBy
import com.mtplayer.app.data.prefs.VideoLayout
import com.mtplayer.app.designsystem.components.ChoiceSheet
import com.mtplayer.app.designsystem.components.HeaderAction
import com.mtplayer.app.designsystem.components.MediaRow
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.SectionHeader
import com.mtplayer.app.designsystem.components.VideoCard
import com.mtplayer.app.designsystem.components.VideoListRow
import com.mtplayer.app.feature.common.CountLine
import com.mtplayer.app.feature.common.TopLevelBar
import com.mtplayer.app.feature.common.rememberIndex
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalMediaActions
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.feature.shell.openVideo
import com.mtplayer.app.ui.image.VideoThumb

@Composable
fun VideosScreen() {
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val index = rememberIndex()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val videos = remember(index, settings.videoSort) { index.videos.sortedBy(settings.videoSort) }

    Column(Modifier.fillMaxSize()) {
        TopLevelBar(stringResource(R.string.tab_videos), overline = stringResource(R.string.overline_videos)) {
            HeaderAction(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort), onClick = { sortOpen = true })
            val grid = settings.videoLayout == VideoLayout.GRID
            HeaderAction(
                if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                stringResource(if (grid) R.string.show_as_list else R.string.show_as_grid),
                onClick = { vm.updateSettings { it.copy(videoLayout = if (grid) VideoLayout.LIST else VideoLayout.GRID) } },
            )
        }
        if (index.videos.isEmpty()) {
            MessageState(Icons.Rounded.Movie, stringResource(R.string.no_videos_title), stringResource(R.string.no_videos_body),
                actionLabel = stringResource(R.string.rescan), onAction = vm::refreshLibrary)
            return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.all_videos), !showFolders) { showFolders = false }
            Chip(stringResource(R.string.tab_folders), showFolders) { showFolders = true }
        }
        if (showFolders) {
            LazyVerticalGrid(GridCells.Fixed(1), Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(index.videoFolders, key = { it.path }) { f ->
                    MediaRow(
                        title = f.name,
                        subtitle = pluralStringResource(R.plurals.video_count, f.videos.size, f.videos.size) + " · " + f.path,
                        artwork = f.videos.firstOrNull()?.let { VideoThumb(it.id, it.uri) },
                        fallbackIcon = Icons.Rounded.Folder,
                        onClick = { nav.videoFolder(f.path) },
                    )
                }
            }
        } else {
            VideoCollection(
                videos = videos,
                layout = settings.videoLayout,
                header = {
                    if (continueWatching.isNotEmpty()) {
                        item(key = "cw_h", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.continue_watching)) }
                        item(key = "cw", span = { GridItemSpan(maxLineSpan) }) { ContinueRow(continueWatching.map { it.video }) }
                    }
                    item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
                        CountLine(pluralStringResource(R.plurals.video_count, videos.size, videos.size))
                    }
                },
            )
        }
    }

    if (sortOpen) {
        ChoiceSheet(
            title = stringResource(R.string.sort_by),
            options = VideoSort.entries,
            selected = settings.videoSort,
            label = { stringResource(videoSortLabel(it)) },
            onSelect = { s -> vm.updateSettings { it.copy(videoSort = s) } },
            onDismiss = { sortOpen = false },
        )
    }
}

fun videoSortLabel(s: VideoSort) = when (s) {
    VideoSort.DATE_ADDED -> R.string.sort_date_added
    VideoSort.TITLE -> R.string.sort_title
    VideoSort.DURATION -> R.string.sort_duration
    VideoSort.SIZE -> R.string.sort_size
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun ContinueRow(videos: List<Video>) {
    val vm = LocalAppViewModel.current
    val actions = LocalMediaActions.current
    val context = LocalContext.current
    val progress by vm.videoProgress.collectAsStateWithLifecycle()
    val favs by vm.favoriteVideoIds.collectAsStateWithLifecycle()
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(videos, key = { it.id }) { v ->
            VideoCard(v, progress[v.id], v.id in favs, onClick = { openVideo(context, v, videos) }, onMore = { actions.openVideo(v) }, modifier = Modifier.width(220.dp))
        }
    }
}

/**
 * One lazy grid for both layouts (grid = adaptive columns, list = one column) so headers,
 * scroll position and item keys behave identically when toggling. This replaces 1.x's nested
 * scroll containers that broke the grid view.
 */
@Composable
fun VideoCollection(videos: List<Video>, layout: VideoLayout, header: LazyGridScope.() -> Unit = {}) {
    val vm = LocalAppViewModel.current
    val actions = LocalMediaActions.current
    val context = LocalContext.current
    val progress by vm.videoProgress.collectAsStateWithLifecycle()
    val favs by vm.favoriteVideoIds.collectAsStateWithLifecycle()
    val columns = if (layout == VideoLayout.GRID) GridCells.Adaptive(168.dp) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (layout == VideoLayout.GRID) 10.dp else 0.dp, end = if (layout == VideoLayout.GRID) 10.dp else 0.dp, bottom = 16.dp),
    ) {
        header()
        items(videos, key = { it.id }, contentType = { layout }) { v ->
            if (layout == VideoLayout.GRID) {
                VideoCard(v, progress[v.id], v.id in favs, onClick = { openVideo(context, v, videos) }, onMore = { actions.openVideo(v) })
            } else {
                VideoListRow(v, progress[v.id], v.id in favs, onClick = { openVideo(context, v, videos) }, onMore = { actions.openVideo(v) })
            }
        }
    }
}
