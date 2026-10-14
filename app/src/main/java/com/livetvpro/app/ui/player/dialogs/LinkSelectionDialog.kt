package com.livetvpro.app.ui.player.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.LiveEventLink

data class LinkItem(val quality: String, val url: String)

fun LiveEventLink.toLinkItem() = LinkItem(quality = quality, url = url)
fun ChannelLink.toLinkItem() = LinkItem(quality = quality, url = url)

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val ColorAccent = Color(0xFFE53935)

@Composable
fun LinkSelectionDialog(
    links: List<LinkItem>,
    onLinkSelected: (LinkItem, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 420.dp || maxWidth < 360.dp
            val outerMargin = if (compact) 12.dp else 24.dp
            val innerPadding = if (compact) 16.dp else 24.dp
            val rowPadding = if (compact) 10.dp else 14.dp
            val titleSize = if (compact) 17.sp else 20.sp
            val itemSize = if (compact) 14.sp else 15.sp

            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = outerMargin, vertical = outerMargin)
                    .heightIn(max = maxHeight - outerMargin * 2)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(top = innerPadding, bottom = 8.dp)) {
                    Text(
                        text = "Multiple Links Available",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = innerPadding),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(top = 12.dp)
                            .padding(horizontal = innerPadding)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        links.forEachIndexed { index, link ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    thickness = 0.5.dp,
                                )
                            }
                            Text(
                                text = link.quality.ifBlank { "Link ${index + 1}" },
                                fontFamily = BergenSans,
                                fontSize = itemSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLinkSelected(link, index)
                                        onDismiss()
                                    }
                                    .padding(vertical = rowPadding),
                            )
                        }
                    }
                    val glassShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerPadding)
                            .padding(top = 8.dp, bottom = 8.dp)
                            .height(if (compact) 44.dp else 52.dp)
                            .clip(glassShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.06f),
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        Color.White.copy(alpha = 0.10f),
                                    )
                                ),
                                shape = glassShape,
                            )
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = BergenSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 15.sp else 17.sp,
                            textAlign = TextAlign.Center,
                            color = ColorAccent,
                        )
                    }
                }
            }
        }
    }
}
