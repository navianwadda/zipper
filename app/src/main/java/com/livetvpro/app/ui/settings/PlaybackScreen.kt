package com.livetvpro.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.appearance.PreferenceCard
import com.livetvpro.app.ui.appearance.PreferenceSectionHeader
import com.livetvpro.app.ui.appearance.SwitchPreferenceRow

@Composable
fun PlaybackScreen(preferencesManager: PreferencesManager) {

    var volumeBoosting by remember { mutableStateOf(preferencesManager.isVolumeBoostingEnabled()) }
    var autoSwitchStream by remember { mutableStateOf(preferencesManager.isAutoSwitchStreamEnabled()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item { PreferenceSectionHeader(title = "Volume") }

        item {
            PreferenceCard {
                SwitchPreferenceRow(
                    title   = "Allow volume boosting",
                    summary = "Boost playback volume beyond the device's normal maximum",
                    checked = volumeBoosting,
                    onCheckedChange = {
                        volumeBoosting = it
                        preferencesManager.setVolumeBoostingEnabled(it)
                    },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item { PreferenceSectionHeader(title = "Streaming") }

        item {
            PreferenceCard {
                SwitchPreferenceRow(
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
    }
}
