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
    onBackClick: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMuteClick: () -> Unit,
    onLockClick: (Boolean) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
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
                onVolumeChange      = { v -> gestureVolume = v; onVolumeSwipe(v) },
                onBrightnessChange  = { b -> gestureBrightness = b; onBrightnessSwipe(b) },
                onTap               = { state.toggle(scope) },
                onShowVolumeOsd     = { show -> showVolumeOsd = show },
                onShowBrightnessOsd = { show -> showBrightnessOsd = show },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("tap") {
                        detectTapGestures(onTap = { state.toggle(scope) })
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
                onBackClick            = onBackClick,
                onPipClick             = onPipClick,
                onSettingsClick        = onSettingsClick,
                onMuteClick            = onMuteClick,
                onLockClick            = { state.lock(); onLockClick(true) },
                onPlayPauseClick       = onPlayPauseClick,
                onSeek                 = onSeek,
                onRewindClick          = onRewindClick,
                onForwardClick         = onForwardClick,
                onAspectRatioClick     = onAspectRatioClick,
                onFullscreenClick      = onFullscreenClick,
                onChannelListClick     = onChannelListClick,
                isChannelListAvailable = isChannelListAvailable,
                onInteraction          = { state.show(scope) },
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
                        .focusable(interactionSource = lockInteractionSource)
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
    onBackClick: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMuteClick: () -> Unit,
    onLockClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onChannelListClick: () -> Unit,
    isChannelListAvailable: Boolean,
    onInteraction: () -> Unit,
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

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                PlayerIconButton(
                    onClick            = { onBackClick(); onInteraction() },
                    iconRes            = R.drawable.ic_arrow_back,
                    contentDescription = "Back",
                    size               = 40,
                )
                Text(
                    text       = channelName,
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BergenSans,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                if (showPipButton) {
                    PlayerIconButton(
                        onClick            = { onPipClick(); onInteraction() },
                        iconRes            = R.drawable.ic_pip,
                        contentDescription = "Picture in Picture",
                        size               = 40,
                    )
                }
                PlayerIconButton(
                    onClick            = { onSettingsClick(); onInteraction() },
                    iconRes            = R.drawable.ic_settings,
                    contentDescription = "Settings",
                    size               = 40,
                )
                PlayerIconButton(
                    onClick            = { onMuteClick(); onInteraction() },
                    iconRes            = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    size               = 40,
                )
                if (!isTvMode) {
                    PlayerIconButton(
                        onClick            = { onLockClick(); onInteraction() },
                        iconRes            = R.drawable.ic_lock_open,
                        contentDescription = "Lock controls",
                        size               = 40,
                        modifier           = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 0.dp),
            ) {
                ExoPlayerTimeBar(
                    currentPosition  = currentPosition,
                    duration         = duration,
                    bufferedPosition = bufferedPosition,
                    onSeek           = { pos -> onSeek(pos); onInteraction() },
                    isTvMode         = isTvMode,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (showAspectRatioButton) {
                        PlayerIconButton(
                            onClick            = { onAspectRatioClick(); onInteraction() },
                            iconRes            = R.drawable.ic_aspect_ratio,
                            contentDescription = "Aspect ratio",
                            size               = 40,
                            modifier           = Modifier.padding(end = 12.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.width(52.dp))
                    }

                    PlayerIconButton(
                        onClick            = { onRewindClick(); onInteraction() },
                        iconRes            = R.drawable.ic_skip_backward,
                        contentDescription = "Rewind 10 seconds",
                        size               = 48,
                        modifier           = Modifier.padding(end = 16.dp),
                    )

                    PlayerIconButton(
                        onClick            = { onPlayPauseClick(); onInteraction() },
                        iconRes            = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size               = 64,
                        modifier           = Modifier
                            .padding(end = 16.dp)
                            .focusRequester(playPauseFocusRequester),
                    )

                    PlayerIconButton(
                        onClick            = { onForwardClick(); onInteraction() },
                        iconRes            = R.drawable.ic_skip_forward,
                        contentDescription = "Forward 10 seconds",
                        size               = 48,
                        modifier           = Modifier.padding(end = 12.dp),
                    )

                    if (isTvMode) {
                        if (isChannelListAvailable) {
                            PlayerIconButton(
                                onClick            = { onChannelListClick(); onInteraction() },
                                iconRes            = R.drawable.ic_list,
                                contentDescription = "Channel list",
                                size               = 40,
                            )
                        } else {
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                    } else {
                        if (isLandscape && isChannelListAvailable) {
                            PlayerIconButton(
                                onClick            = { onChannelListClick(); onInteraction() },
                                iconRes            = R.drawable.ic_list,
                                contentDescription = "Channel list",
                                size               = 48,
                                modifier           = Modifier.padding(end = 4.dp),
                            )
                        }
                        PlayerIconButton(
                            onClick            = { onFullscreenClick(); onInteraction() },
                            iconRes            = if (isLandscape) R.drawable.ic_fullscreen_exit
                                                 else R.drawable.ic_fullscreen,
                            contentDescription = "Toggle fullscreen",
                            size               = 40,
                        )
                    }
                }

                if (isTvMode) TvRemoteHintBar()
            }
        }
    }
}

@Composable
private fun TvRemoteHintBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        listOf(
            "◀▶"    to "Seek",
            "▼"     to "Channels",
            "CH+/−" to "Prev / Next",
            "OK"    to "Play / Pause",
            "BACK"  to "Close / Exit",
        ).forEachIndexed { i, (key, label) ->
            if (i > 0) Text("  ·  ", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
            Text(text = key,      color = Color(0xFFEF4444),             fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = BergenSans)
            Text(text = " $label", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = BergenSans)
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
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionCenter) {
                    onClick(); true
                } else false
            }
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
            fontSize  = 14.sp,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = 60.dp),
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
            fontSize  = 14.sp,
            fontFamily= BergenSans,
            modifier  = Modifier.widthIn(min = 60.dp),
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
                        if (newX != dragPosition) {
                            dragPosition = newX
                            change.consume()
                        }
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

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, centerY), end = Offset(size.width, centerY),
            strokeWidth = activeBar, cap = StrokeCap.Round,
        )
        val bufferedWidth = size.width * bufferedProgress
        if (bufferedWidth > 0f) {
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(0f, centerY), end = Offset(bufferedWidth, centerY),
                strokeWidth = activeBar, cap = StrokeCap.Round,
            )
        }
        val currentProgress = if (isDragging) dragPosition else progress
        val playedWidth     = size.width * currentProgress
        if (playedWidth > 0f) {
            drawLine(
                color = Color.White,
                start = Offset(0f, centerY), end = Offset(playedWidth, centerY),
                strokeWidth = activeBar, cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color  = Color.White,
            radius = activeScrubber,
            center = Offset(playedWidth, centerY),
        )
        if (isHovering && !isDragging && duration > 0L) {
            drawCircle(
                color  = Color.White.copy(alpha = 0.45f),
                radius = 4.dp.toPx(),
                center = Offset(hoverX.coerceIn(0f, size.width), centerY),
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val formatter  = StringBuilder()
    val formatter2 = java.util.Formatter(formatter, java.util.Locale.getDefault())
    return androidx.media3.common.util.Util.getStringForTime(formatter, formatter2, timeMs)
}
