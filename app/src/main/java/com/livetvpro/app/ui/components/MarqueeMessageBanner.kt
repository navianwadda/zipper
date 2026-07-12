package com.livetvpro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.livetvpro.app.R

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

@Composable
fun MarqueeMessageBanner(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bannerShape = RoundedCornerShape(20.dp)
    val bannerHeight = 30.dp
    val gapWidth = 4.dp
    val bannerColor = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = 0.55f)
        .compositeOver(MaterialTheme.colorScheme.background)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
            .height(bannerHeight)
            .clip(bannerShape)
            .background(bannerColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), bannerShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gapWidth)
                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 60.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = BergenSans,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(gapWidth)
                .background(Brush.horizontalGradient(listOf(bannerColor, bannerColor.copy(alpha = 0f))))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(gapWidth)
                .background(Brush.horizontalGradient(listOf(bannerColor.copy(alpha = 0f), bannerColor)))
        )
    }
}
