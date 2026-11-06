package com.livetvpro.app.ui.player.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livetvpro.app.R
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.LiveEventLink

data class LinkItem(val quality: String, val url: String)

fun LiveEventLink.toLinkItem() = LinkItem(quality = quality, url = url)
fun ChannelLink.toLinkItem() = LinkItem(quality = quality, url = url)

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

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
                .focusProperties { canFocus = false }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 500.dp || maxWidth < 360.dp
            val vMargin = (maxHeight * 0.12f).coerceAtLeast(16.dp)
            val hMargin = (maxWidth * 0.06f).coerceIn(10.dp, 24.dp)
            val innerPadding = if (compact) 14.dp else 24.dp
            val rowPadding = if (compact) 10.dp else 14.dp
            val titleSize = if (compact) 16.sp else 20.sp
            val itemSize = if (compact) 14.sp else 15.sp

            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = hMargin, vertical = vMargin)
                    .heightIn(max = maxHeight - vMargin * 2)
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(top = innerPadding, bottom = 16.dp)) {
                    Text(
                        text = "Multiple Links Available",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = innerPadding, vertical = 4.dp),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        links.forEachIndexed { index, link ->
                            val linkInteraction = remember { MutableInteractionSource() }
                            val linkFocused by linkInteraction.collectIsFocusedAsState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent { e ->
                                        if ((e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                                            && e.type == KeyEventType.KeyUp) {
                                            onLinkSelected(link, index)
                                            onDismiss()
                                            true
                                        } else false
                                    }
                                    .clickable(interactionSource = linkInteraction) {
                                        onLinkSelected(link, index)
                                        onDismiss()
                                    }
                                    .padding(horizontal = innerPadding, vertical = rowPadding),
                            ) {
                                Text(
                                    text = link.quality.ifBlank { "Link ${index + 1}" },
                                    fontFamily = BergenSans,
                                    fontSize = itemSize,
                                    fontWeight = if (linkFocused) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (linkFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (index < links.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        val cancelInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50.dp),
                            interactionSource = cancelInteraction,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(text = "Cancel", fontFamily = BergenSans, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
