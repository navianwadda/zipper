package com.livetvpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Modifier.marqueeIf(enabled: Boolean, velocity: Dp = 30.dp): Modifier =
    if (enabled) this.clipToBounds().composed {
        val velocityPx = with(androidx.compose.ui.platform.LocalDensity.current) { velocity.toPx() }
        var contentWidthPx by remember { mutableIntStateOf(0) }
        var containerWidthPx by remember { mutableIntStateOf(0) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        val anim = remember { Animatable(0f) }

        LaunchedEffect(contentWidthPx, containerWidthPx, velocityPx) {
            offsetX = 0f
            anim.snapTo(0f)
            val overflow = contentWidthPx - containerWidthPx
            if (overflow <= 0 || velocityPx <= 0f) return@LaunchedEffect
            delay(1200)
            while (true) {
                anim.snapTo(0f)
                offsetX = 0f
                val duration = ((overflow / velocityPx) * 1000f).toInt().coerceAtLeast(1)
                anim.animateTo(-overflow.toFloat(), tween(duration, easing = LinearEasing)) {
                    offsetX = value
                }
                offsetX = 0f
                anim.snapTo(0f)
                delay(1200)
            }
        }

        this
            .then(object : LayoutModifier {
                override fun MeasureScope.measure(
                    measurable: Measurable,
                    constraints: Constraints,
                ): MeasureResult {
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = Constraints.Infinity)
                    )
                    val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
                    val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
                    contentWidthPx = placeable.width
                    containerWidthPx = width
                    return layout(width, height) {
                        placeable.placeWithLayer(0, 0) {
                            translationX = offsetX
                        }
                    }
                }
            })
    } else this
