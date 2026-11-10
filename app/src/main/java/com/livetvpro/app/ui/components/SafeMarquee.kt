package com.livetvpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SafeMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current,
    iterations: Int = Int.MAX_VALUE,
    velocity: Dp = 30.dp,
    initialDelayMillis: Int = 1200,
    repeatDelayMillis: Int = 1200,
) {
    var cyclePx by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val velocityPx = with(LocalDensity.current) { velocity.toPx() }

    LaunchedEffect(cyclePx, iterations, velocityPx) {
        offset.snapTo(0f)
        if (cyclePx <= 0 || iterations <= 0 || velocityPx <= 0f) return@LaunchedEffect
        delay(initialDelayMillis.toLong())
        val duration = ((cyclePx / velocityPx) * 1000f).toInt().coerceAtLeast(1)
        var count = 0
        while (count < iterations) {
            offset.snapTo(0f)
            offset.animateTo(-cyclePx.toFloat(), tween(duration, easing = LinearEasing))
            offset.snapTo(0f)
            count++
            if (count < iterations) delay(repeatDelayMillis.toLong())
        }
        offset.snapTo(0f)
    }

    val content: @Composable () -> Unit = {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = false,
            maxLines = 1,
            style = style,
        )
    }

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val animate = iterations > 0
        val textMaxWidth = if (animate) Constraints.Infinity else constraints.maxWidth
        val textConstraints = Constraints(
            minWidth = constraints.minWidth.coerceAtMost(textMaxWidth),
            maxWidth = textMaxWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )
        val first = subcompose("first", content).first().measure(textConstraints)
        val containerWidth = first.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = first.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        val overflowing = animate && first.width > containerWidth

        if (overflowing) {
            val gap = (containerWidth / 3).coerceAtLeast(24.dp.roundToPx())
            val cycle = first.width + gap
            if (cyclePx != cycle) cyclePx = cycle
            val second = subcompose("second", content).first().measure(textConstraints)
            layout(containerWidth, height) {
                first.placeWithLayer(0, 0) { translationX = offset.value }
                second.placeWithLayer(cycle, 0) { translationX = offset.value }
            }
        } else {
            if (cyclePx != 0) cyclePx = 0
            layout(containerWidth, height) {
                first.place(0, 0)
            }
        }
    }
}
