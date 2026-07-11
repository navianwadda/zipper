package com.livetvpro.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.appearance.MultiChoiceSegmentedButton
import com.livetvpro.app.ui.appearance.PreferenceCard
import com.livetvpro.app.ui.appearance.PreferenceDivider
import com.livetvpro.app.ui.appearance.PreferenceSectionHeader
import com.livetvpro.app.ui.appearance.SwitchPreferenceRow
import com.livetvpro.app.ui.player.compose.PlayerControlsContent

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private const val ASPECT_RATIO_DEFAULT = -1

@Composable
fun PlayerLayoutsScreen(preferencesManager: PreferencesManager) {

    var rememberAspectRatio by remember { mutableStateOf(preferencesManager.isRememberAspectRatioEnabled()) }
    var forceLowestQuality  by remember { mutableStateOf(preferencesManager.isForceLowestQualityEnabled()) }
    var volumeBoosting  by remember { mutableStateOf(preferencesManager.isVolumeBoostingEnabled()) }
    var layoutMode  by remember { mutableIntStateOf(preferencesManager.getLayoutMode()) }
    var resizeMode by remember { mutableIntStateOf(preferencesManager.getSavedAspectRatio()) }

    var previewIsPlaying by remember { mutableStateOf(true) }
    var previewPosition by remember { mutableLongStateOf(64_000L) }
    val previewDuration = 596_000L
    val previewBuffered = 240_000L

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item { PreferenceSectionHeader(title = "Live Preview") }

        item {
            PreferenceCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A1A1A)),
                ) {
                    PlayerControlsContent(
                        isPlaying             = previewIsPlaying,
                        isMuted               = false,
                        currentPosition       = previewPosition,
                        duration              = previewDuration,
                        bufferedPosition      = previewBuffered,
                        channelName           = "Preview",
                        showPipButton         = true,
                        showAspectRatioButton = true,
                        isLandscape           = false,
                        isTvMode              = false,
                        layoutMode    = layoutMode,
                        isNetworkStream       = false,
                        onBackClick           = {},
                        onPipClick            = {},
                        onSettingsClick       = {},
                        onMuteClick           = {},
                        onLockClick           = {},
                        onPlayPauseClick      = { previewIsPlaying = !previewIsPlaying },
                        onSeek                = { previewPosition = it.coerceIn(0, previewDuration) },
                        onRewindClick         = { previewPosition = (previewPosition - 10_000).coerceAtLeast(0) },
                        onForwardClick        = { previewPosition = (previewPosition + 10_000).coerceAtMost(previewDuration) },
                        onAspectRatioClick    = {},
                        onFullscreenClick     = {},
                        onChannelListClick    = {},
                        isChannelListAvailable = false,
                        onInteraction         = {},
                    )
                }
                Text(
                    text     = "This is the app's real control layout, always visible here. Changes below apply instantly.",
                    style    = MaterialTheme.typography.bodySmall,
                    fontFamily = BergenSans,
                    color    = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                )
            }
        }

        item { PreferenceSectionHeader(title = "Player Layouts") }

        item {
            val centerModeOptions = listOf(
                PreferencesManager.LAYOUT_MODE_SEEKS_ONLY     to ("Seeks Only" to "Show rewind and forward buttons"),
                PreferencesManager.LAYOUT_MODE_SEEKS_AND_NAV  to ("Seeks & Navigation" to "Show seek and channel navigation buttons together"),
                PreferencesManager.LAYOUT_MODE_NAV_ONLY       to ("Navigation Only" to "Show previous/next channel buttons only"),
                PreferencesManager.LAYOUT_MODE_SEEKS_EDGE_NAV to ("Seeks & Edge Navigation" to "Keep seeks centered at the bottom; place previous/next channel buttons at the middle-left and middle-right edges"),
            )
            PreferenceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(vertical = 4.dp),
                ) {
                    centerModeOptions.forEach { (mode, labelPair) ->
                        val (label, summary) = labelPair
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = layoutMode == mode,
                                    onClick  = {
                                        layoutMode = mode
                                        preferencesManager.setLayoutMode(mode)
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = layoutMode == mode, onClick = null)
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(text = label, style = MaterialTheme.typography.bodyLarge, fontFamily = BergenSans)
                                    Text(
                                        text  = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = BergenSans,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { PreferenceSectionHeader(title = "Aspect Ratio") }

        item {
            PreferenceCard {
                SwitchPreferenceRow(
                    title   = "Remember Aspect Ratio",
                    summary = "Keep the aspect ratio you pick during playback for next time",
                    checked = rememberAspectRatio,
                    onCheckedChange = {
                        rememberAspectRatio = it
                        preferencesManager.setRememberAspectRatioEnabled(it)
                    },
                )

                PreferenceDivider()

                Column(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .alpha(if (rememberAspectRatio) 1f else 0.4f),
                ) {
                    Text(
                        text     = "Default Aspect Ratio",
                        style    = MaterialTheme.typography.labelMedium,
                        fontFamily = BergenSans,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
                    )
                    val resizeOptions = listOf(
                        ASPECT_RATIO_DEFAULT                           to "Default",
                        AspectRatioFrameLayout.RESIZE_MODE_FIT         to "Fit",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM        to "Zoom",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL        to "Fill",
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Fixed Width",
                    )
                    MultiChoiceSegmentedButton(
                        choices = resizeOptions.map { it.second },
                        selectedIndices = listOf(
                            resizeOptions.indexOfFirst { it.first == resizeMode }.coerceAtLeast(0)
                        ),
                        onClick = { index ->
                            if (rememberAspectRatio) {
                                val mode = resizeOptions[index].first
                                resizeMode = mode
                                preferencesManager.setSavedAspectRatio(mode)
                                preferencesManager.setSavedAspectRatioPortrait(mode)
                            }
                        },
                    )
                    Text(
                        text     = "\"Default\" leaves this exactly as before \u2014 no fixed ratio is forced; " +
                            "it just uses the app's normal behavior.",
                        style    = MaterialTheme.typography.bodySmall,
                        fontFamily = BergenSans,
                        color    = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }
        }

        item { PreferenceSectionHeader(title = "Quality") }

        item {
            PreferenceCard {
                SwitchPreferenceRow(
                    title   = "Force Lowest Quality",
                    summary = "Always play the lowest available quality to save data",
                    checked = forceLowestQuality,
                    onCheckedChange = {
                        forceLowestQuality = it
                        preferencesManager.setForceLowestQualityEnabled(it)
                    },
                )
            }
        }

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
    }
}
