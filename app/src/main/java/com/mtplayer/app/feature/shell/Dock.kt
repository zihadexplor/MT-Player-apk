package com.mtplayer.app.feature.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtplayer.app.designsystem.components.hairline
import com.mtplayer.app.designsystem.components.reducedMotion
import com.mtplayer.app.designsystem.theme.MtColors

/** One destination of the dock / rail. */
data class DockItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Floating bottom dock (phones). The mini player, when present, sits inside the same rounded
 * container above the tabs, so player and navigation read as one piece of chrome.
 */
@Composable
fun Dock(
    items: List<DockItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .shadow(if (MtColors.current.isDark) 10.dp else 6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(1.dp, hairline(), shape),
    ) {
        if (top != null) {
            top()
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(hairline()))
        }
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                DockTab(item, selected = item.route == selectedRoute, onClick = { if (item.route != selectedRoute) onSelect(item.route) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DockTab(item: DockItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tint by animateColorAsState(if (selected) scheme.primary else scheme.onSurfaceVariant, label = "dockTint")
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "dockPill")
    val pop by animateFloatAsState(
        if (selected && !reducedMotion()) 1.06f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "dockPop",
    )
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (focused) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = ripple(bounded = true), role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(56.dp).height(30.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.primary.copy(alpha = 0.16f * pillAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
