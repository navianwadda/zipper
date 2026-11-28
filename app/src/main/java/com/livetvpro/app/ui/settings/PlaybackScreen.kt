package com.livetvpro.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.appearance.MultiChoiceSegmentedButton
import com.livetvpro.app.ui.appearance.PreferenceCard
import com.livetvpro.app.ui.appearance.PreferenceDivider
import com.livetvpro.app.ui.appearance.PreferenceSectionHeader

@Composable
private fun IconSwitchPreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                    text  = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PlaybackScreen(preferencesManager: PreferencesManager) {

    var volumeBoosting by remember { mutableStateOf(preferencesManager.isVolumeBoostingEnabled()) }
    var holdSpeed by remember { mutableStateOf(preferencesManager.isHoldSpeedEnabled()) }
    var autoSwitchStream by remember { mutableStateOf(preferencesManager.isAutoSwitchStreamEnabled()) }
    var decoderMode by remember { mutableStateOf(preferencesManager.getDecoderMode()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item { PreferenceSectionHeader(title = "Playback") }

        item {
            PreferenceCard(contentTopPadding = 0.dp, contentBottomPadding = 0.dp) {
                IconSwitchPreferenceRow(
                        icon    = Icons.Filled.VolumeUp,
                        title   = "Allow volume boosting",
                        summary = "Boost playback volume beyond the device's normal maximum",
                        checked = volumeBoosting,
                        onCheckedChange = {
                            volumeBoosting = it
                            preferencesManager.setVolumeBoostingEnabled(it)
                        },
                )

                PreferenceDivider()

                IconSwitchPreferenceRow(
                        icon    = Icons.Filled.FastForward,
                        title   = "Hold to play at 2x speed",
                        summary = "Press and hold anywhere on the player to play at 2x speed, release to return to normal",
                        checked = holdSpeed,
                        onCheckedChange = {
                            holdSpeed = it
                            preferencesManager.setHoldSpeedEnabled(it)
                        },
                )

                PreferenceDivider()

                IconSwitchPreferenceRow(
                        icon    = Icons.Filled.Autorenew,
                        title   = "Enable auto switching to next stream when any stream fails",
                        summary = "Automatically try the next available link if the current one fails to play",
                        checked = autoSwitchStream,
                        onCheckedChange = {
                            autoSwitchStream = it
                            preferencesManager.setAutoSwitchStreamEnabled(it)
                        },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item { PreferenceSectionHeader(title = "Decoder") }

        item {
            PreferenceCard(contentBottomPadding = 0.dp) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    MultiChoiceSegmentedButton(
                            choices = listOf("Auto", "Hardware only", "Prefer software"),
                            selectedIndices = listOf(when (decoderMode) {
                                PreferencesManager.DECODER_MODE_HARDWARE_ONLY   -> 1
                                PreferencesManager.DECODER_MODE_PREFER_SOFTWARE -> 2
                                else                                            -> 0
                            }),
                            onClick = { uiIndex ->
                                val mode = when (uiIndex) {
                                    1    -> PreferencesManager.DECODER_MODE_HARDWARE_ONLY
                                    2    -> PreferencesManager.DECODER_MODE_PREFER_SOFTWARE
                                    else -> PreferencesManager.DECODER_MODE_AUTO
                                }
                                if (mode != decoderMode) {
                                    decoderMode = mode
                                    preferencesManager.setDecoderMode(mode)
                                }
                            },
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
