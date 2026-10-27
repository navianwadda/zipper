package com.livetvpro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
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
    val fadeWidth = 22.dp
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
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_campaign),
                contentDescription = "Announcement",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 10.dp, end = 0.dp)
                    .size(18.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE, velocity = 60.dp)
                        .padding(start = 8.dp),
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
                        .width(fadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                0f to bannerColor,
                                0.3f to bannerColor.copy(alpha = 0.85f),
                                0.65f to bannerColor.copy(alpha = 0.35f),
                                1f to bannerColor.copy(alpha = 0f),
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(fadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                0f to bannerColor.copy(alpha = 0f),
                                0.35f to bannerColor.copy(alpha = 0.35f),
                                0.7f to bannerColor.copy(alpha = 0.85f),
                                1f to bannerColor,
                            )
                        )
                )
            }
        }
    }
}
