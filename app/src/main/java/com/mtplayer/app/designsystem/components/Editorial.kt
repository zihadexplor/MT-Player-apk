package com.mtplayer.app.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtplayer.app.designsystem.theme.MtColors
import com.mtplayer.app.designsystem.theme.MtType

/** Faint fill used by tiles and cards on the canvas. */
@Composable
fun tileFill(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (MtColors.current.isDark) 0.72f else 1f)

/** Hairline border used by tiles, cards and the dock. */
@Composable
fun hairline(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (MtColors.current.isDark) 0.08f else 0.07f)

/** Small accent caps label shown above titles ("PICK UP WHERE YOU LEFT OFF"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = MtType.Overline, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Shared press feedback: a short spring scale-down while pressed. */
@Composable
fun pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && !reducedMotion()) pressed else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    return scale
}

/**
 * Header action tile: a 40dp rounded square with a faint fill and hairline border, inside a
 * 48dp touch target. Shows a focus ring for D-pad and keyboard users.
 */
@Composable
fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = pressScale(interaction, 0.90f)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(if (active) scheme.primary.copy(alpha = 0.18f) else tileFill())
                .border(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> scheme.primary
                        active -> scheme.primary.copy(alpha = 0.5f)
                        else -> hairline()
                    },
                    shape,
                )
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) scheme.primary else scheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

/** Section heading: optional overline, display title and an optional trailing text action. */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    top: Dp = 24.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = top, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            if (overline != null) {
                Overline(overline)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = MtType.SectionTitle, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = scheme.primary, maxLines = 1) }
        }
    }
}

/** Thin rounded progress bar; [progress] is read in the draw phase so live updates skip recomposition. */
@Composable
fun ThinProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxWidth().height(3.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val v = progress().coerceIn(0f, 1f)
            if (v > 0f) drawRoundRect(fill, size = Size(size.width * v, size.height), cornerRadius = r)
        },
    )
}

/**
 * "Jump back in" card: artwork, an accent round play button, eyebrow, title, subtitle and a thin
 * progress bar. The card opens the item; the button plays/pauses/resumes it.
 */
@Composable
fun ContinueCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    progress: () -> Float,
    playIcon: ImageVector,
    playLabel: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    art: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = pressScale(interaction, 0.97f)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(tileFill())
            .border(if (focused) 2.dp else 1.dp, if (focused) scheme.primary else hairline(), shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(14.dp)).background(scheme.onSurface.copy(alpha = 0.05f))) {
            art()
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onPlay)
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(playIcon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(eyebrow.uppercase(), style = MtType.Eyebrow, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        ThinProgress(progress)
    }
}

/** Feed card: square artwork with a small type badge, title and subtitle (Yumeiro-style rows). */
@Composable
fun SquareCard(
    title: String,
    subtitle: String,
    artwork: Any?,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
    badgeIcon: ImageVector? = null,
    highlight: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = pressScale(interaction)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .combinedClickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).then(if (focused) Modifier.border(2.dp, scheme.primary, shape) else Modifier)) {
            Artwork(artwork, contentDescription = null, modifier = Modifier.matchParentSize(), shape = shape, fallbackIcon = fallbackIcon, iconSize = 36.dp, sizePx = 384)
            if (badgeIcon != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).clip(CircleShape).background(MtColors.current.scrimStrong),
                    contentAlignment = Alignment.Center,
                ) { Icon(badgeIcon, contentDescription = null, tint = MtColors.current.onScrim, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (highlight) scheme.primary else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Scrollable pill tabs (replaces the underlined Material tab row). The selected pill uses the accent fill;
 * every pill keeps a 48dp touch target while drawing a 36dp capsule.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(selected) {
        if (selected >= 0) state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(labels.size) { i ->
            val isSelected = i == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val scale = pressScale(interaction, 0.94f)
            val fill by androidx.compose.animation.animateColorAsState(if (isSelected) scheme.primary else tileFill(), label = "pill")
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .androidxSelectable(isSelected, interaction) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .then(if (focused) Modifier.border(2.dp, scheme.onSurface, CircleShape) else if (!isSelected) Modifier.border(1.dp, hairline(), CircleShape) else Modifier)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels[i],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Modifier.androidxSelectable(selected: Boolean, interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
