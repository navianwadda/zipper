package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import kotlin.math.abs

data class GestureState(
    val volumePercent: Int = 50,
    val brightnessPercent: Int = 0,
)

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    gestureState: GestureState = remember { GestureState() },
    pixelsPerPercent: Float = 8f,
    isLocked: Boolean = false,
    maxVolumePercent: Int = 100,
    holdSpeedEnabled: Boolean = false,
    onHoldSpeedChange: (Boolean) -> Unit = {},
    onVolumeChange: (Int) -> Unit = {},
    onBrightnessChange: (Int) -> Unit = {},
    onTap: () -> Unit = {},
    onShowVolumeOsd: (Boolean) -> Unit = {},
    onShowBrightnessOsd: (Boolean) -> Unit = {},
) {
    var currentVolume     by remember { mutableIntStateOf(gestureState.volumePercent) }
    var brightnessFloat   by remember { mutableFloatStateOf(gestureState.brightnessPercent.toFloat()) }
    var volumeAccum       by remember { mutableFloatStateOf(0f) }
    var brightnessAccum   by remember { mutableFloatStateOf(0f) }
    var totalWidth        by remember { mutableFloatStateOf(0f) }
    val holdEnabled       by rememberUpdatedState(holdSpeedEnabled)
    val holdCallback      by rememberUpdatedState(onHoldSpeedChange)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> totalWidth = size.width.toFloat() }
            .pointerInput(pixelsPerPercent) {
                val slopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down      = awaitFirstDown(requireUnconsumed = false)
                    val zone      = gestureZone(down.position.x, totalWidth)
                    var totalDy   = 0f
                    var committed = false

                    if (zone == GestureZone.VOLUME)     volumeAccum     = 0f
                    if (zone == GestureZone.BRIGHTNESS) brightnessAccum = 0f

                    if (holdEnabled && !isLocked) {
                        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var done = false
                            while (true) {
                                val event  = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { done = true; break }
                                if (!change.pressed) { onTap(); done = true; break }
                                totalDy += -(change.position.y - change.previousPosition.y)
                                if (abs(totalDy) > slopPx) { committed = true; break }
                            }
                            done
                        }
                        if (finished == true) return@awaitEachGesture
                        if (finished == null) {
                            holdCallback(true)
                            try {
                                while (true) {
                                    val event  = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            } finally {
                                holdCallback(false)
                            }
                            return@awaitEachGesture
                        }
                    }

                    while (true) {
                        val event  = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (!committed) {
                                onTap()
                            } else {
                                onShowVolumeOsd(false)
                                onShowBrightnessOsd(false)
                            }
                            break
                        }

                        val dy = -(change.position.y - change.previousPosition.y)
                        totalDy += dy
                        if (!committed && abs(totalDy) > slopPx) committed = true

                        if (committed) {
                            change.consume()
                            if (!isLocked) {
                                when (zone) {
                                    GestureZone.VOLUME -> {
                                        volumeAccum += dy
                                        val steps = (volumeAccum / pixelsPerPercent).toInt()
                                        if (steps != 0) {
                                            volumeAccum   -= steps * pixelsPerPercent
                                            currentVolume  = (currentVolume + steps).coerceIn(0, maxVolumePercent)
                                            onVolumeChange(currentVolume)
                                            onShowVolumeOsd(true)
                                        }
                                    }
                                    GestureZone.BRIGHTNESS -> {
                                        brightnessAccum += dy
                                        val steps = (brightnessAccum / pixelsPerPercent).toInt()
                                        if (steps != 0) {
                                            brightnessAccum -= steps * pixelsPerPercent
                                            brightnessFloat  = (brightnessFloat + steps).coerceIn(0f, 100f)
                                            onBrightnessChange(brightnessFloat.toInt())
                                            onShowBrightnessOsd(true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    )
}

@Composable
fun VolumeOsd(visible: Boolean, volume: Int, modifier: Modifier = Modifier) {
    val isBoosted = volume > 100
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
        exit     = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        OsdCard {
            Icon(
                painter            = painterResource(if (volume == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
                contentDescription = "Volume",
                tint               = if (isBoosted) Color(0xFFFF9800) else Color.White,
                modifier           = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text       = "$volume%",
                color      = if (isBoosted) Color(0xFFFF9800) else Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun HoldSpeedOsd(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(120)),
        exit     = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text       = "2x",
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 14.dp, height = 10.dp)) {
                val tw = size.width * 0.5f
                for (i in 0..1) {
                    val x = i * tw
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + tw, size.height / 2f)
                        lineTo(x, size.height)
                        close()
                    }
                    drawPath(path, Color.White)
                }
            }
        }
    }
}

@Composable
fun BrightnessOsd(visible: Boolean, brightness: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
        exit     = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        OsdCard {
            Icon(
                painter = painterResource(when {
                    brightness == 0  -> R.drawable.ic_brightness_auto
                    brightness <= 30 -> R.drawable.ic_brightness_low
                    brightness <= 60 -> R.drawable.ic_brightness_medium
                    else             -> R.drawable.ic_brightness_high
                }),
                contentDescription = "Brightness",
                tint               = Color.White,
                modifier           = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text       = if (brightness == 0) "Auto" else "$brightness%",
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OsdCard(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF1F1F1F).copy(alpha = 0.82f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) { content() }
}

private enum class GestureZone { BRIGHTNESS, VOLUME }

private fun gestureZone(x: Float, totalWidth: Float): GestureZone {
    return if (x < totalWidth / 2f) GestureZone.BRIGHTNESS else GestureZone.VOLUME
}
