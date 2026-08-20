package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar

private val exoEnterAnim = fadeIn(tween(150, easing = LinearEasing))
private val exoExitAnim  = fadeOut(tween(150, easing = LinearEasing))

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
    supportsPointerInput: Boolean = true,
    layoutMode: Int = 0,
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
    volumeBoostEnabled: Boolean = false,
    showCastButton: Boolean = false,
    isCastConnected: Boolean = false,
    onCastClick: () -> Unit = {},
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

        if (supportsPointerInput) {
            GestureOverlay(
                modifier            = Modifier
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
                gestureState        = GestureState(
                    volumePercent     = gestureVolume,
                    brightnessPercent = gestureBrightness,
                ),
                isLocked            = state.isLocked,
                maxVolumePercent    = if (volumeBoostEnabled) 200 else 100,
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
                    .pointerInput("tap") {
                        detectTapGestures(onTap = { state.show(scope) })
                    }
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
                    .pointerInput("lock-tap") {
                        detectTapGestures(onTap = { state.toggle(scope) })
                    }
                    .pointerInput("lock-mouse-reveal") {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if ((event.type == PointerEventType.Move ||
                                     event.type == PointerEventType.Enter) &&
                                    !state.isLockOverlayVisible
                                ) {
                                    state.toggle(scope)
                                }
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
                supportsPointerInput   = supportsPointerInput,
                layoutMode     = layoutMode,
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
                showCastButton         = showCastButton,
                isCastConnected        = isCastConnected,
                onCastClick            = onCastClick,
                onInteraction          = { state.show(scope) },
                onToggle               = { state.toggle(scope) },
                onTvFocusWithinControls    = { isTvFocusWithinControls = it },
                onMouseHoverWithinControls = { if (supportsPointerInput) isMouseHoverWithinControls = it },
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
                val lockInteractionSource = remember { MutableInteractionSource() }
                var isUnlockFocused     by remember { mutableStateOf(false) }
                val isUnlockHovered     by lockInteractionSource.collectIsHoveredAsState()
                val isUnlockHighlighted = isUnlockFocused || isUnlockHovered
                val unlockFocusRequester= remember { FocusRequester() }

                LaunchedEffect(Unit) { runCatching { unlockFocusRequester.requestFocus() } }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                        .size(40.dp)
                        .hoverable(interactionSource = lockInteractionSource)
                        .focusRequester(unlockFocusRequester)
                        .onFocusChanged { isUnlockFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                state.unlock(scope); onLockClick(false); true
                            } else false
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
                        modifier           = Modifier.size(24.dp),
                    )
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
internal fun PlayerControlsContent(
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
    supportsPointerInput: Boolean = true,
    layoutMode: Int = 0,
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
    showCastButton: Boolean = false,
    isCastConnected: Boolean = false,
    onCastClick: () -> Unit = {},
    onInteraction: () -> Unit,
    onToggle: () -> Unit = {},
    onTvFocusWithinControls: (Boolean) -> Unit = {},
    onMouseHoverWithinControls: (Boolean) -> Unit = {},
) {
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTvMode) {
            delay(160)
            runCatching { playPauseFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { onTvFocusWithinControls(it.hasFocus) }
            .pointerInput("controls-tap-toggle") {
                detectTapGestures(onTap = { onToggle() })
            }
            .pointerInput("controls-hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        if (!isNetworkStream && layoutMode == 3) {
            PlayerIconButton(
                onClick            = { onPrevClick(); onInteraction() },
                iconRes            = R.drawable.ic_skip_prev_channel,
                contentDescription = "Previous channel",
                size               = if (isTvMode || isLandscape) 48 else 40,
                modifier           = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
                isTvMode           = isTvMode,
            )
            PlayerIconButton(
                onClick            = { onNextClick(); onInteraction() },
                iconRes            = R.drawable.ic_skip_next_channel,
                contentDescription = "Next channel",
                size               = if (isTvMode || isLandscape) 48 else 40,
                modifier           = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                isTvMode           = isTvMode,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 4.dp),
            ) {
                val isCompact = maxWidth < 360.dp
                val topIconSize = when {
                    isTvMode    -> 44
                    isLandscape -> 40
                    isCompact   -> 32
                    else        -> 40
                }
                val topTitleSize = when {
                    isTvMode    -> 18.sp
                    isLandscape -> 16.sp
                    isCompact   -> 13.sp
                    else        -> 16.sp
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    PlayerIconButton(
                        onClick            = { onBackClick(); onInteraction() },
                        iconRes            = R.drawable.ic_arrow_back,
                        contentDescription = "Back",
                        size               = topIconSize,
                        isTvMode           = isTvMode,
                    )
                    Text(
                        text       = channelName,
                        color      = Color.White,
                        fontSize   = topTitleSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    if (showCastButton) {
                        PlayerIconButton(
                            onClick            = { onCastClick(); onInteraction() },
                            iconRes            = R.drawable.ic_cast,
                            contentDescription = if (isCastConnected) "Casting to device" else "Cast to device",
                            size               = topIconSize,
                            tint               = if (isCastConnected) Color(0xFF2AABEE) else Color.White,
                            isTvMode           = isTvMode,
                        )
                    }
                    if (showPipButton) {
                        PlayerIconButton(
                            onClick            = { onPipClick(); onInteraction() },
                            iconRes            = R.drawable.ic_pip,
                            contentDescription = "Picture in Picture",
                            size               = topIconSize,
                            isTvMode           = isTvMode,
                        )
                    }
                    PlayerIconButton(
                        onClick            = { onSettingsClick(); onInteraction() },
                        iconRes            = R.drawable.ic_settings,
                        contentDescription = "Settings",
                        size               = topIconSize,
                        isTvMode           = isTvMode,
                    )
                    PlayerIconButton(
                        onClick            = { onMuteClick(); onInteraction() },
                        iconRes            = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        size               = topIconSize,
                        isTvMode           = isTvMode,
                    )
                    if (supportsPointerInput) {
                        PlayerIconButton(
                            onClick            = { onLockClick(); onInteraction() },
                            iconRes            = R.drawable.ic_lock_open,
                            contentDescription = "Lock controls",
                            size               = topIconSize,
                            modifier           = Modifier.padding(start = 4.dp),
                            isTvMode           = isTvMode,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 0.dp),
            ) {
                val isCompactBottom = maxWidth < 360.dp
                Column(modifier = Modifier.fillMaxWidth()) {
                ExoPlayerTimeBar(
                    currentPosition  = currentPosition,
                    duration         = duration,
                    bufferedPosition = bufferedPosition,
                    onSeek           = { pos -> onSeek(pos); onInteraction() },
                    isTvMode         = isTvMode,
                    isCompact        = isCompactBottom,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )

                val showSeeks   = layoutMode == 0 || layoutMode == 1 || layoutMode == 3
                val showNavInline = !isNetworkStream && (layoutMode == 1 || layoutMode == 2)

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    val isCompact = maxWidth < 360.dp
                    val slotSize = when {
                        isTvMode    -> 44
                        isLandscape -> 40
                        isCompact   -> 32
                        else        -> 36
                    }
                    val seekSize = when {
                        isTvMode    -> 48
                        isLandscape -> 48
                        isCompact   -> 36
                        else        -> 40
                    }
                    val playSize = when {
                        isTvMode    -> 64
                        isLandscape -> 64
                        isCompact   -> 48
                        else        -> 56
                    }
                    val spacing = when {
                        isLandscape -> 8.dp
                        isCompact   -> 4.dp
                        else        -> 6.dp
                    }

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (showAspectRatioButton) {
                            PlayerIconButton(
                                onClick            = { onAspectRatioClick(); onInteraction() },
                                iconRes            = R.drawable.ic_aspect_ratio,
                                contentDescription = "Aspect ratio",
                                size               = slotSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        if (showNavInline) {
                            PlayerIconButton(
                                onClick            = { onPrevClick(); onInteraction() },
                                iconRes            = R.drawable.ic_skip_prev_channel,
                                contentDescription = "Previous channel",
                                size               = seekSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        if (showSeeks) {
                            PlayerIconButton(
                                onClick            = { onRewindClick(); onInteraction() },
                                iconRes            = R.drawable.ic_skip_backward,
                                contentDescription = "Rewind 10 seconds",
                                size               = seekSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        PlayerIconButton(
                            onClick            = { onPlayPauseClick(); onInteraction() },
                            iconRes            = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            size               = playSize,
                            modifier           = Modifier
                                .padding(end = spacing)
                                .focusRequester(playPauseFocusRequester),
                            isTvMode           = isTvMode,
                        )

                        if (showSeeks) {
                            PlayerIconButton(
                                onClick            = { onForwardClick(); onInteraction() },
                                iconRes            = R.drawable.ic_skip_forward,
                                contentDescription = "Forward 10 seconds",
                                size               = seekSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        if (showNavInline) {
                            PlayerIconButton(
                                onClick            = { onNextClick(); onInteraction() },
                                iconRes            = R.drawable.ic_skip_next_channel,
                                contentDescription = "Next channel",
                                size               = seekSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        if ((isLandscape || isTvMode) && isChannelListAvailable) {
                            PlayerIconButton(
                                onClick            = { onChannelListClick(); onInteraction() },
                                iconRes            = R.drawable.ic_list,
                                contentDescription = "Channel list",
                                size               = slotSize,
                                modifier           = Modifier.padding(end = spacing),
                                isTvMode           = isTvMode,
                            )
                        }

                        if (supportsPointerInput) {
                            PlayerIconButton(
                                onClick            = { onFullscreenClick(); onInteraction() },
                                iconRes            = if (isLandscape) R.drawable.ic_fullscreen_exit
                                                     else R.drawable.ic_fullscreen,
                                contentDescription = "Toggle fullscreen",
                                size               = slotSize,
                                isTvMode           = isTvMode,
                            )
                        }
                    }
                }

                if (isTvMode) TvRemoteHintBar(isCompact = isCompactBottom)
                }
            }
        }
    }
}

@Composable
private fun TvRemoteHintBar(isCompact: Boolean = false) {
    val hints = listOf(
        "◀▶"    to "Seek",
        "▼"     to "Channels",
        "CH+/−" to "Prev / Next",
        "OK"    to "Play / Pause",
        "BACK"  to "Close / Exit",
    )
    val hintFontSize = if (isCompact) 9.sp else 10.sp

    @Composable
    fun HintRow(items: List<Pair<String, String>>) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (key, label) ->
                if (i > 0) Text("  ·  ", color = Color.White.copy(alpha = 0.3f), fontSize = hintFontSize)
                Text(text = key,       color = Color(0xFFEF4444),              fontSize = hintFontSize, fontWeight = FontWeight.Bold, fontFamily = BergenSans)
                Text(text = " $label", color = Color.White.copy(alpha = 0.55f), fontSize = hintFontSize, fontFamily = BergenSans)
            }
        }
    }

    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(2.dp),
    ) {
        if (isCompact) {
            HintRow(hints.take(3))
            HintRow(hints.drop(3))
        } else {
            HintRow(hints)
        }
    }
}

@Composable
internal fun PlayerIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
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
            .size(size.dp)
            .hoverable(interactionSource = interactionSource)
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
            modifier           = Modifier.size((size * 0.6f).toInt().dp),
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
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayedPosition = scrubPositionMs ?: currentPosition

    val timeFontSize = when {
        isTvMode   -> 16.sp
        isCompact  -> 11.sp
        else       -> 14.sp
    }
    val timeMinWidth = when {
        isTvMode   -> 68.dp
        isCompact  -> 48.dp
        else       -> 60.dp
    }

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
            text      = formatTime(displayedPosition),
            color     = Color.White,
            fontSize  = timeFontSize,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = timeMinWidth),
            textAlign = TextAlign.End,
            maxLines  = 1,
        )
        CustomTimeBar(
            currentPosition  = currentPosition,
            duration         = duration,
            bufferedPosition = bufferedPosition,
            onSeek           = onSeek,
            onScrub          = { scrubPositionMs = it },
            isFocused        = isFocused,
            modifier         = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(24.dp),
        )
        Text(
            text      = formatTime(duration),
            color     = Color.White,
            fontSize  = timeFontSize,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = timeMinWidth),
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
    onScrub: (Long?) -> Unit = {},
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var hoverX by remember { mutableStateOf<Float?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                HoverTimeBar(context).apply {
                    isFocusable = false
                    onHover = { x -> hoverX = x }
                    addListener(object : TimeBar.OnScrubListener {
                        override fun onScrubStart(timeBar: TimeBar, position: Long) {
                            isDragging = true
                            onScrub(position)
                        }

                        override fun onScrubMove(timeBar: TimeBar, position: Long) {
                            onScrub(position)
                        }

                        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                            isDragging = false
                            onScrub(null)
                            if (!canceled) onSeek(position)
                        }
                    })
                }
            },
            update = { timeBar ->
                timeBar.setDuration(duration.coerceAtLeast(0L))
                timeBar.setBufferedPosition(bufferedPosition.coerceIn(0L, duration.coerceAtLeast(0L)))
                if (!isDragging) {
                    timeBar.setPosition(currentPosition.coerceIn(0L, duration.coerceAtLeast(0L)))
                }
            },
        )

        val previewX = hoverX
        if (previewX != null && !isDragging && duration > 0L) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = 4.dp.toPx(),
                    center = Offset(previewX.coerceIn(0f, size.width), size.height / 2f),
                )
            }
        }
    }
}

private class HoverTimeBar(context: Context) : DefaultTimeBar(context) {
    var onHover: (Float?) -> Unit = {}

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> onHover(event.x)
            MotionEvent.ACTION_HOVER_EXIT -> onHover(null)
        }
        return super.onHoverEvent(event)
    }
}

private fun formatTime(timeMs: Long): String {
    val formatter  = StringBuilder()
    val formatter2 = java.util.Formatter(formatter, java.util.Locale.getDefault())
    return androidx.media3.common.util.Util.getStringForTime(formatter, formatter2, timeMs)
}
