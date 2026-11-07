package com.livetvpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Modifier.safeMarquee(
    iterations: Int = Int.MAX_VALUE,
    velocity: Dp = 30.dp,
    initialDelayMillis: Int = 1200,
    repeatDelayMillis: Int = 1200,
): Modifier = composed {
    if (iterations <= 0) return@composed this
    var contentWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val velocityPx = with(LocalDensity.current) { velocity.toPx() }
    val overflow = contentWidth - containerWidth

    LaunchedEffect(overflow, iterations, velocityPx) {
        offset.snapTo(0f)
        if (overflow <= 0 || velocityPx <= 0f) return@LaunchedEffect
        delay(initialDelayMillis.toLong())
        var count = 0
        while (count < iterations) {
            val duration = ((overflow / velocityPx) * 1000f).toInt().coerceAtLeast(1)
            offset.animateTo(-overflow.toFloat(), tween(duration, easing = LinearEasing))
            delay(repeatDelayMillis.toLong())
            offset.snapTo(0f)
            delay(repeatDelayMillis.toLong())
            count++
        }
    }

    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
            val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
            if (contentWidth != placeable.width) contentWidth = placeable.width
            if (containerWidth != width) containerWidth = width
            layout(width, height) {
                placeable.placeWithLayer(0, 0) { translationX = offset.value }
            }
        }
}
