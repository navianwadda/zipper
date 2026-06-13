package com.livetvpro.app.ui.player.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import com.livetvpro.app.data.models.LiveEventLink

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val ColorAccent = Color(0xFFE53935)

@Composable
fun LinkSelectionDialog(
    links: List<LiveEventLink>,
    currentLink: String?,
    onLinkSelected: (LiveEventLink) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Multiple Links Available",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                links.forEachIndexed { index, link ->
                    val isSelected = link.url == currentLink

                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLinkSelected(link)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                    ) {
                        Text(
                            text = link.quality.ifBlank { "Link ${index + 1}" },
                            fontFamily = BergenSans,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ColorAccent
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )

                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                          else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (isSelected) ColorAccent
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Medium,
                    color = ColorAccent,
                )
            }
        },
    )
}
