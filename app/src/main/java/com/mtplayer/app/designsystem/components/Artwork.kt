package com.mtplayer.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Artwork with a themed fallback tile. The fallback is drawn underneath and stays visible
 * until (and unless) the image loads, so missing art never shows a blank or broken box.
 */
@Composable
fun Artwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    iconSize: Dp = 24.dp,
    sizePx: Int? = null,
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest))
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(fallbackIcon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
        if (model != null) {
            val context = LocalContext.current
            val request = remember(model, sizePx) {
                ImageRequest.Builder(context)
                    .data(model)
                    .apply { if (sizePx != null) size(sizePx) }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SquareArtwork(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.MusicNote) {
    Artwork(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        fallbackIcon = icon,
        iconSize = 40.dp,
    )
}
