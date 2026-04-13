package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ripple
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

private val exoEnterAnim = fadeIn(tween(150, easing = LinearEasing))
private val exoExitAnim  = fadeOut(tween(150, easing = LinearEasing))

private fun fluidDp(
    width: Dp,
    minWidth: Dp = 280.dp,
    maxWidth: Dp = 960.dp,
    minVal: Dp,
    maxVal: Dp,
): Dp {
    val t = ((width - minWidth) / (maxWidth - minWidth)).coerceIn(0f, 1f)
    return minVal + (maxVal - minVal) * t
}

private fun fluidSp(
    width: Dp,
    minWidth: Dp = 280.dp,
    maxWidth: Dp = 960.dp,
    minVal: Float,
    maxVal: Float,
): TextUnit {
    val t = ((width - minWidth) / (maxWidth - minWidth)).coerceIn(0f, 1f)
    return (minVal + (maxVal - minVal) * t).sp
}

private data class ResponsiveTokens(
    val topIconSize   : Dp,
    val slotIconSize  : Dp,
    val seekIconSize  : Dp,
    val playIconSize  : Dp,
    val iconSpacing   : Dp,
    val rowPaddingH   : Dp,
    val rowPaddingV   : Dp,
    val titleFontSize : TextUnit,
    val timeFontSize  : TextUnit,
    val timeMinWidth  : Dp,
    val hintFontSize  : TextUnit,
)

private fun computeTokens(width: Dp, isTvMode: Boolean): ResponsiveTokens {
    val lo = 280.dp
    val hi = 960.dp
    val tvScale = if (isTvMode) 1.25f else 1f
    return ResponsiveTokens(
        topIconSize   = fluidDp(width, lo, hi, (26 * tvScale).dp, (50 * tvScale).dp),
        slotIconSize  = fluidDp(width, lo, hi, (24 * tvScale).dp, (46 * tvScale).dp),
        seekIconSize  = fluidDp(width, lo, hi, (28 * tvScale).dp, (54 * tvScale).dp),
        playIconSize  = fluidDp(width, lo, hi, (40 * tvScale).dp, (70 * tvScale).dp),
        iconSpacing   = fluidDp(width, lo, hi, 2.dp,  14.dp),
        rowPaddingH   = fluidDp(width, lo, hi, 4.dp,  20.dp),
        rowPaddingV   = fluidDp(width, lo, hi, 2.dp,   8.dp),
        titleFontSize = fluidSp(width, lo, hi, 11f, if (isTvMode) 22f else 18f),
        timeFontSize  = fluidSp(width, lo, hi, 10f, if (isTvMode) 18f else 15f),
        timeMinWidth  = fluidDp(width, lo, hi, 40.dp, if (isTvMode) 80.dp else 68.dp),
        hintFontSize  = fluidSp(width, lo, hi,  8f, 12f),
    )
}

class PlayerControlsState(
    initialVisible: Boolean = true,
    val autoHideDelay: Long = 5000L,
) {
    var isVisible by mutableStateOf(initialVisible)
        private set

    var isLocked by mutableStateOf(false)

    var isLockOverlayVisible by mutableStateOf(false)
        private set

    private var hideJob: Job? = null
    private var lockOverlayHideJob: Job? = null

    fun show(coroutineScope: CoroutineScope) {
        hideJob?.cancel()
        isVisible = true
        if (!isLocked) {
            hideJob = coroutineScope.launch {
                delay(autoHideDelay)
                isVisible = false
            }
        }
    }

    fun showPersistent() {
        hideJob?.cancel()
        isVisible = true
    }

    fun hide() {
        if (!isLocked) {
            hideJob?.cancel()
            isVisible = false
        }
    }

    fun toggle(coroutineScope: CoroutineScope) {
        if (isLocked) toggleLockOverlay(coroutineScope)
        else if (isVisible) hide() else show(coroutineScope)
    }

    fun lock() {
        isLocked = true
        hideJob?.cancel()
        isVisible = false
        isLockOverlayVisible = false
    }

    fun unlock(coroutineScope: CoroutineScope) {
        lockOverlayHideJob?.cancel()
        isLocked = false
        isLockOverlayVisible = false
        show(coroutineScope)
    }

    private fun toggleLockOverlay(coroutineScope: CoroutineScope) {
        lockOverlayHideJob?.cancel()
        isLockOverlayVisible = !isLockOverlayVisible
        if (isLockOverlayVisible) {
            lockOverlayHideJob = coroutineScope.launch {
                delay(autoHideDelay)
                isLockOverlayVisible = false
            }
        }
    }
}

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    state: PlayerControlsState = remember { PlayerControlsState() },
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    channelName: String,
    showPipButton: Boolean,
    showAspectRatioButton: Boolean,
    isLandscape: Boolean,
    isTvMode: Boolean = false,
    centerControlsMode: Int = 0,
    isNetworkStream: Boolean = false,
    onBackClick: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMuteClick: () -> Unit,
    onLockClick: (Boolean) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onAspectRatioClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onChannelListClick: () -> Unit = {},
    isChannelListAvailable: Boolean = false,
    onVolumeSwipe: (Int) -> Unit = {},
    onBrightnessSwipe: (Int) -> Unit = {},
    initialVolume: Int = 100,
    initialBrightness: Int = 0,
) {
    val scope = rememberCoroutineScope()

    var gestureVolume     by remember { mutableIntStateOf(initialVolume) }
    var gestureBrightness by remember { mutableIntStateOf(initialBrightness) }
    var showVolumeOsd     by remember { mutableStateOf(false) }
    var showBrightnessOsd by remember { mutableStateOf(false) }

    var isTvFocusWithinControls    by remember { mutableStateOf(false) }
    var isMouseHoverWithinControls by remember { mutableStateOf(false) }

    val shouldKeepControlsOpen = isTvFocusWithinControls || isMouseHoverWithinControls

    LaunchedEffect(shouldKeepControlsOpen) {
        if (shouldKeepControlsOpen) {
            state.showPersistent()
        } else {
            if (state.isVisible && !state.isLocked) {
                state.show(scope)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        if (!isTvMode) {
            GestureOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("mouse-reveal") {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val isMouse = event.changes.any { it.type == PointerType.Mouse }
                                if (isMouse && (event.type == PointerEventType.Move ||
                                    event.type == PointerEventType.Enter)
                                ) {
                                    state.show(scope)
                                }
                            }
                        }
                    },
                gestureState = GestureState(
                    volumePercent     = gestureVolume,
                    brightnessPercent = gestureBrightness,
                ),
                isLocked            = state.isLocked,
                onVolumeChange      = { v -> gestureVolume = v; onVolumeSwipe(v) },
                onBrightnessChange  = { b -> gestureBrightness = b; onBrightnessSwipe(b) },
                onTap               = { state.show(scope) },
                onShowVolumeOsd     = { show -> showVolumeOsd = show },
                onShowBrightnessOsd = { show -> showBrightnessOsd = show },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("tap") { detectTapGestures(onTap = { state.show(scope) }) }
                    .pointerInput("mouse-reveal") {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Move ||
                                    event.type == PointerEventType.Enter
                                ) {
                                    state.show(scope)
                                }
                            }
                        }
                    }
            )
        }

        if (state.isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("lock-tap") { detectTapGestures(onTap = { state.toggle(scope) }) }
                    .pointerInput("lock-mouse-reveal") {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if ((event.type == PointerEventType.Move ||
                                     event.type == PointerEventType.Enter) &&
                                    !state.isLockOverlayVisible
                                ) { state.toggle(scope) }
                            }
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = state.isVisible && !state.isLocked,
            enter   = exoEnterAnim,
            exit    = exoExitAnim,
        ) {
            PlayerControlsContent(
                isPlaying              = isPlaying,
                isMuted                = isMuted,
                currentPosition        = currentPosition,
                duration               = duration,
                bufferedPosition       = bufferedPosition,
                channelName            = channelName,
                showPipButton          = showPipButton,
                showAspectRatioButton  = showAspectRatioButton,
                isLandscape            = isLandscape,
                isTvMode               = isTvMode,
                centerControlsMode     = centerControlsMode,
                isNetworkStream        = isNetworkStream,
                onBackClick            = onBackClick,
                onPipClick             = onPipClick,
                onSettingsClick        = onSettingsClick,
                onMuteClick            = onMuteClick,
                onLockClick            = { state.lock(); onLockClick(true) },
                onPlayPauseClick       = onPlayPauseClick,
                onSeek                 = onSeek,
                onRewindClick          = onRewindClick,
                onForwardClick         = onForwardClick,
                onPrevClick            = onPrevClick,
                onNextClick            = onNextClick,
                onAspectRatioClick     = onAspectRatioClick,
                onFullscreenClick      = onFullscreenClick,
                onChannelListClick     = onChannelListClick,
                isChannelListAvailable = isChannelListAvailable,
                onInteraction          = { state.show(scope) },
                onToggle               = { state.toggle(scope) },
                onTvFocusWithinControls    = { isTvFocusWithinControls = it },
                onMouseHoverWithinControls = { if (!isTvMode) isMouseHoverWithinControls = it },
            )
        }

        AnimatedVisibility(
            visible = state.isLockOverlayVisible,
            enter   = exoEnterAnim,
            exit    = exoExitAnim,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput("lock-overlay-dismiss") {
                        detectTapGestures(onTap = { state.toggle(scope) })
                    }
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val btnSize = fluidDp(maxWidth, minVal = 34.dp, maxVal = 56.dp)
                    val btnPad  = fluidDp(maxWidth, minVal = 4.dp,  maxVal = 14.dp)

                    val lockInteractionSource = remember { MutableInteractionSource() }
                    var isUnlockFocused       by remember { mutableStateOf(false) }
                    val isUnlockHovered       by lockInteractionSource.collectIsHoveredAsState()
                    val isUnlockHighlighted   = isUnlockFocused || isUnlockHovered
                    val unlockFocusRequester  = remember { FocusRequester() }

                    LaunchedEffect(Unit) { runCatching { unlockFocusRequester.requestFocus() } }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = btnPad, top = btnPad)
                            .size(btnSize)
                            .hoverable(interactionSource = lockInteractionSource)
                            .focusRequester(unlockFocusRequester)
                            .focusable(interactionSource = lockInteractionSource)
                            .onFocusChanged { isUnlockFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) { state.unlock(scope); onLockClick(false); true } else false
                            }
                            .clickable(
                                interactionSource = lockInteractionSource,
                                indication        = ripple(bounded = true, color = Color.White.copy(alpha = 0.25f)),
                                onClick           = { state.unlock(scope); onLockClick(false) },
                            )
                            .then(
                                if (isUnlockHighlighted) Modifier.background(
                                    Color.White.copy(alpha = 0.18f),
                                    androidx.compose.foundation.shape.CircleShape,
                                ) else Modifier
                            )
                    ) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_lock_closed),
                            contentDescription = "Unlock controls",
                            tint               = if (isUnlockHighlighted) Color(0xFFEF4444) else Color.White,
                            modifier           = Modifier.size(btnSize * 0.6f),
                        )
                    }
                }
            }
        }

        VolumeOsd(
            visible  = showVolumeOsd,
            volume   = gestureVolume,
            modifier = Modifier.align(Alignment.Center),
        )
        BrightnessOsd(
            visible    = showBrightnessOsd,
            brightness = gestureBrightness,
            modifier   = Modifier.align(Alignment.Center),
        )
    }
}

private val BergenSans = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.bergen_sans)
)

@Composable
private fun PlayerControlsContent(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    channelName: String,
    showPipButton: Boolean,
    showAspectRatioButton: Boolean,
    isLandscape: Boolean,
    isTvMode: Boolean,
    centerControlsMode: Int = 0,
    isNetworkStream: Boolean = false,
    onBackClick: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMuteClick: () -> Unit,
    onLockClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onAspectRatioClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onChannelListClick: () -> Unit,
    isChannelListAvailable: Boolean,
    onInteraction: () -> Unit,
    onToggle: () -> Unit = {},
    onTvFocusWithinControls: (Boolean) -> Unit = {},
    onMouseHoverWithinControls: (Boolean) -> Unit = {},
) {
    val playPauseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTvMode) { delay(160); runCatching { playPauseFocusRequester.requestFocus() } }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { onTvFocusWithinControls(it.hasFocus) }
            .pointerInput("controls-tap-toggle") {
                detectTapGestures(onTap = { onToggle() })
            }
            .pointerInput("controls-hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event   = awaitPointerEvent()
                        val isMouse = event.changes.any { it.type == PointerType.Mouse }
                        if (isMouse) {
                            when (event.type) {
                                PointerEventType.Enter,
                                PointerEventType.Move -> onMouseHoverWithinControls(true)
                                PointerEventType.Exit -> onMouseHoverWithinControls(false)
                                else -> {}
                            }
                        }
                    }
                }
            }
    ) {
        val t = remember(maxWidth, isTvMode) { computeTokens(maxWidth, isTvMode) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * 0.18f)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * 0.34f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = t.rowPaddingH, vertical = t.rowPaddingV),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                PlayerIconButton(
                    onClick            = { onBackClick(); onInteraction() },
                    iconRes            = R.drawable.ic_arrow_back,
                    contentDescription = "Back",
                    size               = t.topIconSize,
                    isTvMode           = isTvMode,
                )
                Text(
                    text       = channelName,
                    color      = Color.White,
                    fontSize   = t.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BergenSans,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .padding(start = t.iconSpacing + 4.dp),
                )
                if (showPipButton) {
                    Spacer(Modifier.width(t.iconSpacing))
                    PlayerIconButton(
                        onClick            = { onPipClick(); onInteraction() },
                        iconRes            = R.drawable.ic_pip,
                        contentDescription = "Picture in Picture",
                        size               = t.topIconSize,
                        isTvMode           = isTvMode,
                    )
                }
                Spacer(Modifier.width(t.iconSpacing))
                PlayerIconButton(
                    onClick            = { onSettingsClick(); onInteraction() },
                    iconRes            = R.drawable.ic_settings,
                    contentDescription = "Settings",
                    size               = t.topIconSize,
                    isTvMode           = isTvMode,
                )
                Spacer(Modifier.width(t.iconSpacing))
                PlayerIconButton(
                    onClick            = { onMuteClick(); onInteraction() },
                    iconRes            = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    size               = t.topIconSize,
                    isTvMode           = isTvMode,
                )
                if (!isTvMode) {
                    Spacer(Modifier.width(t.iconSpacing))
                    PlayerIconButton(
                        onClick            = { onLockClick(); onInteraction() },
                        iconRes            = R.drawable.ic_lock_open,
                        contentDescription = "Lock controls",
                        size               = t.topIconSize,
                        isTvMode           = isTvMode,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = t.rowPaddingH, bottom = t.rowPaddingV),
            ) {
                ExoPlayerTimeBar(
                    currentPosition  = currentPosition,
                    duration         = duration,
                    bufferedPosition = bufferedPosition,
                    onSeek           = { pos -> onSeek(pos); onInteraction() },
                    isTvMode         = isTvMode,
                    tokens           = t,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(bottom = t.iconSpacing),
                )

                val showSeeks = centerControlsMode == 0 || centerControlsMode == 1
                val showNav   = !isNetworkStream && (centerControlsMode == 1 || centerControlsMode == 2)

                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = t.rowPaddingV),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (showAspectRatioButton) {
                        PlayerIconButton(
                            onClick            = { onAspectRatioClick(); onInteraction() },
                            iconRes            = R.drawable.ic_aspect_ratio,
                            contentDescription = "Aspect ratio",
                            size               = t.slotIconSize,
                            isTvMode           = isTvMode,
                        )
                        Spacer(Modifier.width(t.iconSpacing))
                    }
                    if (showNav) {
                        PlayerIconButton(
                            onClick            = { onPrevClick(); onInteraction() },
                            iconRes            = R.drawable.ic_skip_prev_channel,
                            contentDescription = "Previous channel",
                            size               = t.seekIconSize,
                            isTvMode           = isTvMode,
                        )
                        Spacer(Modifier.width(t.iconSpacing))
                    }
                    if (showSeeks) {
                        PlayerIconButton(
                            onClick            = { onRewindClick(); onInteraction() },
                            iconRes            = R.drawable.ic_skip_backward,
                            contentDescription = "Rewind 10 seconds",
                            size               = t.seekIconSize,
                            isTvMode           = isTvMode,
                        )
                        Spacer(Modifier.width(t.iconSpacing))
                    }
                    PlayerIconButton(
                        onClick            = { onPlayPauseClick(); onInteraction() },
                        iconRes            = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size               = t.playIconSize,
                        modifier           = Modifier.focusRequester(playPauseFocusRequester),
                        isTvMode           = isTvMode,
                    )
                    if (showSeeks) {
                        Spacer(Modifier.width(t.iconSpacing))
                        PlayerIconButton(
                            onClick            = { onForwardClick(); onInteraction() },
                            iconRes            = R.drawable.ic_skip_forward,
                            contentDescription = "Forward 10 seconds",
                            size               = t.seekIconSize,
                            isTvMode           = isTvMode,
                        )
                    }
                    if (showNav) {
                        Spacer(Modifier.width(t.iconSpacing))
                        PlayerIconButton(
                            onClick            = { onNextClick(); onInteraction() },
                            iconRes            = R.drawable.ic_skip_next_channel,
                            contentDescription = "Next channel",
                            size               = t.seekIconSize,
                            isTvMode           = isTvMode,
                        )
                    }
                    if ((isLandscape || isTvMode) && isChannelListAvailable) {
                        Spacer(Modifier.width(t.iconSpacing))
                        PlayerIconButton(
                            onClick            = { onChannelListClick(); onInteraction() },
                            iconRes            = R.drawable.ic_list,
                            contentDescription = "Channel list",
                            size               = t.slotIconSize,
                            isTvMode           = isTvMode,
                        )
                    }
                    if (!isTvMode) {
                        Spacer(Modifier.width(t.iconSpacing))
                        PlayerIconButton(
                            onClick            = { onFullscreenClick(); onInteraction() },
                            iconRes            = if (isLandscape) R.drawable.ic_fullscreen_exit
                                                 else R.drawable.ic_fullscreen,
                            contentDescription = "Toggle fullscreen",
                            size               = t.slotIconSize,
                            isTvMode           = isTvMode,
                        )
                    }
                }

                if (isTvMode) TvRemoteHintBar(tokens = t)
            }
        }
    }
}

@Composable
private fun TvRemoteHintBar(tokens: ResponsiveTokens) {
    val hints = listOf(
        "◀▶"    to "Seek",
        "▼"     to "Channels",
        "CH+/−" to "Prev / Next",
        "OK"    to "Play / Pause",
        "BACK"  to "Close / Exit",
    )

    @Composable
    fun HintRow(items: List<Pair<String, String>>) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (key, label) ->
                if (i > 0) Text("  ·  ", color = Color.White.copy(alpha = 0.3f), fontSize = tokens.hintFontSize)
                Text(key,       color = Color(0xFFEF4444),               fontSize = tokens.hintFontSize, fontWeight = FontWeight.Bold, fontFamily = BergenSans)
                Text(" $label", color = Color.White.copy(alpha = 0.55f), fontSize = tokens.hintFontSize, fontFamily = BergenSans)
            }
        }
    }

    val split = tokens.hintFontSize.value < 10f

    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.rowPaddingV * 2,
                     start  = tokens.rowPaddingH,
                     end    = tokens.rowPaddingH),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(2.dp),
    ) {
        if (split) { HintRow(hints.take(3)); HintRow(hints.drop(3)) }
        else HintRow(hints)
    }
}

@Composable
internal fun PlayerIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Color.White,
    isTvMode: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isTvMode) Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionCenter) {
                        onClick(); true
                    } else false
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = true, color = Color.White.copy(alpha = 0.25f)),
                onClick           = onClick,
            )
            .then(
                if (isHighlighted) Modifier.background(
                    Color.White.copy(alpha = 0.18f),
                    androidx.compose.foundation.shape.CircleShape,
                ) else Modifier
            )
    ) {
        Icon(
            painter            = painterResource(iconRes),
            contentDescription = contentDescription,
            tint               = if (isHighlighted) Color(0xFFEF4444) else tint,
            modifier           = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun ExoPlayerTimeBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    isTvMode: Boolean = false,
    tokens: ResponsiveTokens,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused && isTvMode) Modifier.border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                ) else Modifier
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = 10_000L
                when (event.key) {
                    Key.DirectionRight -> { onSeek((currentPosition + step).coerceAtMost(duration)); true }
                    Key.DirectionLeft  -> { onSeek((currentPosition - step).coerceAtLeast(0L));     true }
                    else               -> false
                }
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text      = formatTime(currentPosition),
            color     = Color.White,
            fontSize  = tokens.timeFontSize,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = tokens.timeMinWidth),
            textAlign = TextAlign.End,
            maxLines  = 1,
        )
        CustomTimeBar(
            currentPosition  = currentPosition,
            duration         = duration,
            bufferedPosition = bufferedPosition,
            onSeek           = onSeek,
            isFocused        = isFocused,
            modifier         = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(24.dp),
        )
        Text(
            text      = formatTime(duration),
            color     = Color.White,
            fontSize  = tokens.timeFontSize,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = tokens.timeMinWidth),
            textAlign = TextAlign.Start,
            maxLines  = 1,
        )
    }
}

@Composable
private fun CustomTimeBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isDragging   by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var isHovering   by remember { mutableStateOf(false) }
    var hoverX       by remember { mutableFloatStateOf(0f) }

    val progress = if (duration > 0)
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    else 0f

    val bufferedProgress = if (duration > 0)
        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    else 0f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput("seek") {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging   = true
                    dragPosition = (down.position.x / size.width).coerceIn(0f, 1f)
                    down.consume()
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            isDragging = false
                            onSeek((dragPosition * duration).toLong())
                            break
                        }
                        val newX = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (newX != dragPosition) { dragPosition = newX; change.consume() }
                    }
                }
            }
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovering = true
                            PointerEventType.Exit  -> isHovering = false
                            PointerEventType.Move  -> {
                                isHovering = true
                                hoverX = event.changes.firstOrNull()?.position?.x ?: hoverX
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        val barHeight            = 4.dp.toPx()
        val barHeightActive      = 6.dp.toPx()
        val scrubberRadius       = 6.dp.toPx()
        val scrubberRadiusActive = 9.dp.toPx()
        val centerY              = size.height / 2f

        val active         = isHovering || isDragging || isFocused
        val activeBar      = if (active) barHeightActive      else barHeight
        val activeScrubber = if (active) scrubberRadiusActive else scrubberRadius

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, centerY), Offset(size.width, centerY), activeBar, StrokeCap.Round)
        val bw = size.width * bufferedProgress
        if (bw > 0f) drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, centerY), Offset(bw, centerY), activeBar, StrokeCap.Round)
        val cp = if (isDragging) dragPosition else progress
        val pw = size.width * cp
        if (pw > 0f) drawLine(Color.White, Offset(0f, centerY), Offset(pw, centerY), activeBar, StrokeCap.Round)
        drawCircle(Color.White, activeScrubber, Offset(pw, centerY))
        if (isHovering && !isDragging && duration > 0L) {
            drawCircle(Color.White.copy(alpha = 0.45f), 4.dp.toPx(), Offset(hoverX.coerceIn(0f, size.width), centerY))
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val formatter  = StringBuilder()
    val formatter2 = java.util.Formatter(formatter, java.util.Locale.getDefault())
    return androidx.media3.common.util.Util.getStringForTime(formatter, formatter2, timeMs)
}
