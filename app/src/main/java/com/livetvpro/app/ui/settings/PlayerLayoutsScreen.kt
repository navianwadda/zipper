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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.appearance.MultiChoiceSegmentedButton
import com.livetvpro.app.ui.appearance.PreferenceCard
import com.livetvpro.app.ui.appearance.PreferenceDivider
import com.livetvpro.app.ui.appearance.PreferenceSectionHeader
import com.livetvpro.app.ui.appearance.SwitchPreferenceRow
import com.livetvpro.app.ui.player.compose.PlayerControls
import kotlinx.coroutines.delay

private const val PREVIEW_STREAM_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

/**
 * Dedicated page for player playback layout settings (formerly the "Save States" dialog).
 * Includes a live preview that plays real content through the app's actual [PlayerControls],
 * so every change below is reflected instantly in a real player above.
 */
@Composable
fun PlayerLayoutsScreen(preferencesManager: PreferencesManager) {
    val context = LocalContext.current

    var rememberAspectRatio by remember { mutableStateOf(preferencesManager.isRememberAspectRatioEnabled()) }
    var forceLowestQuality  by remember { mutableStateOf(preferencesManager.isForceLowestQualityEnabled()) }
    var centerControlsMode  by remember { mutableIntStateOf(preferencesManager.getCenterControlsMode()) }
    var resizeMode by remember {
        mutableIntStateOf(
            preferencesManager.getSavedAspectRatio().takeIf { it >= 0 }
                ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
        )
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(PREVIEW_STREAM_URL))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(forceLowestQuality) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoBitrate(if (forceLowestQuality) 1 else Int.MAX_VALUE)
            .build()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

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
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setBackgroundColor(android.graphics.Color.BLACK)
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    PlayerControls(
                        isPlaying             = isPlaying,
                        isMuted               = true,
                        currentPosition       = currentPosition,
                        duration              = duration,
                        bufferedPosition      = bufferedPosition,
                        channelName           = "Preview",
                        showPipButton         = false,
                        showAspectRatioButton = false,
                        isLandscape           = false,
                        centerControlsMode    = centerControlsMode,
                        isNetworkStream       = false,
                        onBackClick           = {},
                        onPipClick            = {},
                        onSettingsClick       = {},
                        onMuteClick           = {},
                        onLockClick           = {},
                        onPlayPauseClick      = { if (player.isPlaying) player.pause() else player.play() },
                        onSeek                = { player.seekTo(it) },
                        onRewindClick         = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                        onForwardClick        = { player.seekTo(player.currentPosition + 10_000) },
                        onAspectRatioClick    = {},
                        onFullscreenClick     = {},
                    )
                }
                Text(
                    text     = "This preview uses the real player controls. Any change below applies to it instantly.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                )
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

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text     = "Default Aspect Ratio",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
                    )
                    val resizeOptions = listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT         to "Fit",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM        to "Zoom",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL        to "Fill",
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Fixed Width",
                    )
                    MultiChoiceSegmentedButton(
                        choices = resizeOptions.map { it.second },
                        selectedIndices = listOf(resizeOptions.indexOfFirst { it.first == resizeMode }.coerceAtLeast(0)),
                        onClick = { index ->
                            val mode = resizeOptions[index].first
                            resizeMode = mode
                            preferencesManager.setSavedAspectRatio(mode)
                            preferencesManager.setSavedAspectRatioPortrait(mode)
                        },
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

        item { PreferenceSectionHeader(title = "Center Controls") }

        item {
            val centerModeOptions = listOf(
                PreferencesManager.CENTER_MODE_SEEKS_ONLY    to ("Seeks Only" to "Show rewind and forward buttons"),
                PreferencesManager.CENTER_MODE_SEEKS_AND_NAV to ("Seeks & Navigation" to "Show seek and channel navigation buttons together"),
                PreferencesManager.CENTER_MODE_NAV_ONLY      to ("Navigation Only" to "Show previous/next channel buttons only"),
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
                                    selected = centerControlsMode == mode,
                                    onClick  = {
                                        centerControlsMode = mode
                                        preferencesManager.setCenterControlsMode(mode)
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = centerControlsMode == mode, onClick = null)
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text  = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
