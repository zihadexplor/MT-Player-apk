package com.mtplayer.app.feature.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.feature.common.DetailBar
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer

@Composable
fun EqualizerScreen() {
    val vm = LocalAppViewModel.current
    val effects = LocalContainer.current.audioEffects
    val info by effects.info.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        DetailBar(stringResource(R.string.equalizer))
        if (!info.available) {
            MessageState(Icons.Rounded.GraphicEq, stringResource(R.string.eq_unavailable_title), stringResource(R.string.eq_unavailable_body))
            return@Column
        }
        val levels = when {
            s.eqBands.size == info.bandFrequenciesHz.size -> s.eqBands
            s.eqPreset in info.presetLevels.indices -> info.presetLevels[s.eqPreset]
            else -> List(info.bandFrequenciesHz.size) { 0 }
        }
        val dragging = remember { mutableStateMapOf<Int, Float>() }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { vm.updateSettings { it.copy(eqEnabled = !s.eqEnabled) } }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.eq_enable), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = s.eqEnabled, onCheckedChange = null)
            }
            if (info.presets.isNotEmpty()) {
                Text(stringResource(R.string.eq_presets), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(info.presets) { i, name ->
                        FilterChip(
                            selected = s.eqPreset == i && s.eqBands.isEmpty(),
                            enabled = s.eqEnabled,
                            onClick = { vm.updateSettings { it.copy(eqPreset = i, eqBands = emptyList()) } },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }
            Text(stringResource(R.string.eq_bands), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            info.bandFrequenciesHz.forEachIndexed { band, hz ->
                val value = dragging[band] ?: levels.getOrElse(band) { 0 }.toFloat()
                val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                    Slider(
                        value = value,
                        onValueChange = { dragging[band] = it },
                        onValueChangeFinished = {
                            val newLevels = levels.toMutableList().also { l -> l[band] = (dragging[band] ?: value).toInt() }
                            dragging.remove(band)
                            vm.updateSettings { it.copy(eqBands = newLevels, eqPreset = -1) }
                        },
                        valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                        enabled = s.eqEnabled,
                        modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("%+.1f dB".format(value / 100f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                }
            }
            if (info.bassBoostSupported) StrengthSlider(stringResource(R.string.bass_boost), s.bassBoost, s.eqEnabled) { v -> vm.updateSettings { it.copy(bassBoost = v) } }
            if (info.virtualizerSupported) StrengthSlider(stringResource(R.string.virtualizer), s.virtualizer, s.eqEnabled) { v -> vm.updateSettings { it.copy(virtualizer = v) } }
            TextButton(
                onClick = { vm.updateSettings { it.copy(eqBands = emptyList(), eqPreset = -1, bassBoost = 0, virtualizer = 0) } },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.reset)) }
        }
    }
}

@Composable
private fun StrengthSlider(title: String, value: Int, enabled: Boolean, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("$title · ${(local / 10).toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local.toInt()) },
            valueRange = 0f..1000f, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}
