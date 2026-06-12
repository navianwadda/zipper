package com.livetvpro.app.ui.player.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.livetvpro.app.R
import com.livetvpro.app.utils.DeviceUtils
import kotlinx.coroutines.launch
import timber.log.Timber

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val ColorPrimary = Color(0xFFE53935)

@Composable
fun PlayerSettingsDialog(
    player: ExoPlayer,
    onDismiss: () -> Unit,
) {
    var videoTracks by remember { mutableStateOf(listOf<TrackUiModel.Video>()) }
    var audioTracks by remember { mutableStateOf(listOf<TrackUiModel.Audio>()) }
    var textTracks  by remember { mutableStateOf(listOf<TrackUiModel.Text>()) }

    var isVideoNone by remember { mutableStateOf(false) }
    var isAudioNone by remember { mutableStateOf(false) }
    var isTextNone  by remember { mutableStateOf(false) }
    var isVideoAuto by remember { mutableStateOf(true) }
    var isAudioAuto by remember { mutableStateOf(true) }
    var isTextAuto  by remember { mutableStateOf(true) }

    var selectedVideoQualities by remember { mutableStateOf(setOf<TrackUiModel.Video>()) }
    var selectedAudio by remember { mutableStateOf<TrackUiModel.Audio?>(null) }
    var selectedText  by remember { mutableStateOf<TrackUiModel.Text?>(null) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    fun loadTracks() {
        try {
            val params = player.trackSelectionParameters

            val disabledVideo = params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
            val hasVideoOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_VIDEO && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isVideoNone = disabledVideo
            isVideoAuto = !disabledVideo && !hasVideoOver

            val disabledAudio = params.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
            val hasAudioOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_AUDIO && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isAudioNone = disabledAudio
            isAudioAuto = !disabledAudio && !hasAudioOver

            val disabledText = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val hasTextOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_TEXT && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isTextNone = disabledText
            isTextAuto = !disabledText && !hasTextOver

            videoTracks = PlayerTrackMapper.videoTracks(player)
            audioTracks = PlayerTrackMapper.audioTracks(player)
            textTracks  = PlayerTrackMapper.textTracks(player)

            selectedVideoQualities = if (!isVideoAuto && !isVideoNone)
                videoTracks.filter { it.isSelected }.toSet() else emptySet()
            selectedAudio = if (!isAudioAuto && !isAudioNone) audioTracks.firstOrNull { it.isSelected } else null
            selectedText  = if (!isTextAuto  && !isTextNone)  textTracks.firstOrNull  { it.isSelected } else null
            selectedSpeed = player.playbackParameters.speed
        } catch (e: Exception) {
            Timber.e(e, "Error loading tracks")
        }
    }

    LaunchedEffect(Unit) {
        loadTracks()
        if (videoTracks.isEmpty() && audioTracks.isEmpty() && textTracks.isEmpty()) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.groups.isNotEmpty()) {
                        player.removeListener(this)
                        loadTracks()
                    }
                }
            }
            player.addListener(listener)
        }
    }

    fun applySelections() {
        try {
            TrackSelectionApplier.applyMultipleVideo(
                player       = player,
                videoTracks  = if (isVideoAuto || isVideoNone) emptyList() else selectedVideoQualities.toList(),
                audio        = selectedAudio,
                text         = selectedText,
                disableVideo = isVideoNone,
                disableAudio = isAudioNone,
                disableText  = isTextNone,
            )
            player.setPlaybackSpeed(selectedSpeed)
        } catch (e: Exception) {
            Timber.e(e, "Error applying selections")
        }
    }
    val tabs = remember(videoTracks.size, audioTracks.size, textTracks.size) {
        buildList {
            if (videoTracks.isNotEmpty()) add("Video")
            if (audioTracks.isNotEmpty()) add("Audio")
            if (textTracks.isNotEmpty())  add("Text")
            add("Speed")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PlayerSettingsContent(
            tabs = tabs,
            videoTracks = videoTracks,
            audioTracks = audioTracks,
            textTracks  = textTracks,
            isVideoNone = isVideoNone, isVideoAuto = isVideoAuto,
            isAudioNone = isAudioNone, isAudioAuto = isAudioAuto,
            isTextNone  = isTextNone,  isTextAuto  = isTextAuto,
            selectedVideoQualities = selectedVideoQualities,
            selectedAudio = selectedAudio,
            selectedText  = selectedText,
            selectedSpeed = selectedSpeed,
            onVideoTrackSelected = { track ->
                when (track.groupIndex) {
                    -1 -> { selectedVideoQualities = emptySet(); isVideoAuto = true;  isVideoNone = false }
                    -2 -> { selectedVideoQualities = emptySet(); isVideoAuto = false; isVideoNone = true  }
                    else -> {
                        isVideoAuto = false; isVideoNone = false
                        val existing = selectedVideoQualities.find {
                            it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex
                        }
                        selectedVideoQualities = if (existing != null)
                            selectedVideoQualities - existing
                        else
                            selectedVideoQualities + track
                        if (selectedVideoQualities.isEmpty()) isVideoAuto = true
                    }
                }
            },
            onAudioTrackSelected = { track ->
                when (track.groupIndex) {
                    -1 -> { selectedAudio = null; isAudioNone = false; isAudioAuto = true  }
                    -2 -> { selectedAudio = null; isAudioNone = true;  isAudioAuto = false }
                    else -> { selectedAudio = track; isAudioNone = false; isAudioAuto = false }
                }
            },
            onTextTrackSelected = { track ->
                when (track.groupIndex) {
                    -1 -> { selectedText = null; isTextNone = false; isTextAuto = true  }
                    -2 -> { selectedText = null; isTextNone = true;  isTextAuto = false }
                    else -> { selectedText = track; isTextNone = false; isTextAuto = false }
                }
            },
            onSpeedSelected = { speed -> selectedSpeed = speed },
            onApply = { applySelections(); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun PlayerSettingsContent(
    tabs: List<String>,
    videoTracks: List<TrackUiModel.Video>,
    audioTracks: List<TrackUiModel.Audio>,
    textTracks:  List<TrackUiModel.Text>,
    isVideoNone: Boolean, isVideoAuto: Boolean,
    isAudioNone: Boolean, isAudioAuto: Boolean,
    isTextNone:  Boolean, isTextAuto:  Boolean,
    selectedVideoQualities: Set<TrackUiModel.Video>,
    selectedAudio: TrackUiModel.Audio?,
    selectedText:  TrackUiModel.Text?,
    selectedSpeed: Float,
    onVideoTrackSelected: (TrackUiModel.Video) -> Unit,
    onAudioTrackSelected: (TrackUiModel.Audio) -> Unit,
    onTextTrackSelected:  (TrackUiModel.Text)  -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onApply:   () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp  = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val dialogMaxWidth  = (screenWidthDp  * 0.88f).coerceAtMost(520.dp)
    val dialogMaxHeight = (screenHeightDp * 0.82f).coerceAtMost(560.dp)

    Box(
        modifier = Modifier
            .widthIn(max = dialogMaxWidth)
            .heightIn(max = dialogMaxHeight)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column {
            androidx.compose.material3.TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                fontFamily = BergenSans,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val tabLabel = tabs.getOrNull(selectedTab) ?: "Speed"
                when (tabLabel) {
                    "Video" -> {
                        val useRadio = videoTracks.size == 1
                        val items = buildList {
                            add(TrackUiModel.Video(-1, -1, 0, 0, 0, isSelected = isVideoAuto, isRadio = true))
                            add(TrackUiModel.Video(-2, -2, 0, 0, 0, isSelected = isVideoNone, isRadio = true))
                            addAll(videoTracks.map { t ->
                                val checked = selectedVideoQualities.any {
                                    it.groupIndex == t.groupIndex && it.trackIndex == t.trackIndex
                                }
                                t.copy(isSelected = !isVideoAuto && !isVideoNone && checked, isRadio = useRadio)
                            })
                        }
                        TrackList(items = items, onClick = { onVideoTrackSelected(it as TrackUiModel.Video) })
                    }
                    "Audio" -> {
                        val items = buildList {
                            add(TrackUiModel.Audio(-1, -1, "Auto", 0, 0, "", isSelected = isAudioAuto))
                            add(TrackUiModel.Audio(-2, -2, "None", 0, 0, "", isSelected = isAudioNone))
                            addAll(audioTracks.map { t ->
                                t.copy(isSelected = !isAudioAuto && !isAudioNone &&
                                    selectedAudio?.groupIndex == t.groupIndex &&
                                    selectedAudio?.trackIndex == t.trackIndex)
                            })
                        }
                        TrackList(items = items, onClick = { onAudioTrackSelected(it as TrackUiModel.Audio) })
                    }
                    "Text" -> {
                        val items = buildList {
                            add(TrackUiModel.Text(-1, -1, "Auto", isSelected = isTextAuto))
                            add(TrackUiModel.Text(-2, -2, "None", isSelected = isTextNone))
                            addAll(textTracks.map { t ->
                                t.copy(isSelected = !isTextAuto && !isTextNone &&
                                    selectedText?.groupIndex == t.groupIndex &&
                                    selectedText?.trackIndex == t.trackIndex)
                            })
                        }
                        TrackList(items = items, onClick = { onTextTrackSelected(it as TrackUiModel.Text) })
                    }
                    else -> {
                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                            .map { TrackUiModel.Speed(it, isSelected = it == selectedSpeed) }
                        TrackList(items = speeds, onClick = { onSpeedSelected((it as TrackUiModel.Speed).speed) })
                    }
                }
            }

            Divider()
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontFamily = BergenSans)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onApply,
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorPrimary),
                ) {
                    Text("Apply", fontFamily = BergenSans, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    items: List<TrackUiModel>,
    onClick: (TrackUiModel) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items) { item ->
            TrackRow(item = item, onClick = { onClick(item) })
        }
    }
}

private fun formatBitrate(bps: Int): String {
    val mbps = bps / 1_000_000.0
    return if (mbps >= 0.1) "%.2f Mbps".format(mbps)
    else "%.0f Kbps".format(bps / 1_000.0)
}

private fun channelLayout(channels: Int, mimeType: String): String {
    val mime = mimeType.lowercase()
    val isAtmos  = mime.contains("atmos") || mime.contains("ec3") || mime.contains("eac3")
    val isSurround = channels >= 6
    return when {
        channels <= 0   -> ""
        channels == 1   -> "Mono"
        channels == 2   -> "Stereo"
        isSurround && isAtmos -> "${channels - 1}.1 Dolby Atmos"
        isSurround      -> "${channels - 1}.1 Surround"
        else            -> "$channels ch"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    item: TrackUiModel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOutline = MaterialTheme.colorScheme.outline

    val label = when (item) {
        is TrackUiModel.Video -> when (item.groupIndex) {
            -1   -> "Auto"
            -2   -> "None"
            else -> buildString {
                if (item.width > 0 && item.height > 0) append("${item.width} × ${item.height}")
                else append("Track ${item.trackIndex + 1}")
                if (item.bitrate > 0) append(",  ${formatBitrate(item.bitrate)}")
            }
        }
        is TrackUiModel.Audio -> when (item.groupIndex) {
            -1   -> "Auto"
            -2   -> "None"
            else -> buildString {
                append(item.language.ifBlank { "Track ${item.trackIndex + 1}" })
                val layout = channelLayout(item.channels, item.mimeType)
                if (layout.isNotEmpty()) append(", $layout")
                if (item.bitrate > 0) append(",  ${formatBitrate(item.bitrate)}")
            }
        }
        is TrackUiModel.Text  -> when (item.groupIndex) {
            -1   -> "Auto"
            -2   -> "None"
            else -> item.language.ifBlank { "Track ${item.trackIndex?.plus(1) ?: 1}" }
        }
        is TrackUiModel.Speed -> if (item.speed == 1.0f) "Normal (1×)" else "${item.speed}×"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            AnimatedTrackIndicator(
                isSelected = item.isSelected,
                isRadio = item.isRadio,
                unselectedColor = colorOutline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = BergenSans,
            fontSize = 14.sp,
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (item.isSelected) ColorPrimary else colorOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedTrackIndicator(isSelected: Boolean, isRadio: Boolean, unselectedColor: Color) {
    if (isRadio) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor   = ColorPrimary,
                unselectedColor = unselectedColor,
            ),
        )
    } else {
        val progress = remember { Animatable(if (isSelected) 1f else 0f) }
        LaunchedEffect(isSelected) {
            progress.animateTo(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(),
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .drawBehind {
                    drawCheckbox(progress.value, isSelected, ColorPrimary, unselectedColor)
                },
        )
    }
}

private fun DrawScope.drawCheckbox(progress: Float, isChecked: Boolean, primary: Color, gray: Color) {
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val corner = CornerRadius(4.dp.toPx())
    val bgColor = if (isChecked) primary.copy(alpha = progress) else gray.copy(alpha = 0.3f)
    drawRoundRect(color = bgColor, cornerRadius = corner, style = Stroke(width = 2.dp.toPx()))
    if (progress > 0f) {
        val checkPath = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.5f)
            lineTo(size.width * 0.45f, size.height * 0.72f)
            lineTo(size.width * 0.8f, size.height * 0.28f)
        }
        val pm = PathMeasure().apply { setPath(checkPath, false) }
        val partial = Path()
        pm.getSegment(0f, pm.length * progress, partial, true)
        drawPath(partial, primary, style = stroke)
    }
}
