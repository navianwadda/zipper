package com.livetvpro.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.livetvpro.app.R

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val ColorAccent = Color(0xFFEF4444)
private val ColorSurface = Color(0xFF0D0D0D)
private val ColorDivider = Color(0x1AFFFFFF)
private val ColorOnSurface = Color(0xFFFFFFFF)
private val ColorOnSurfaceMuted = Color(0xCCFFFFFF)
private val ColorOutline = Color(0x33FFFFFF)

@Composable
fun SupportDialog(
    durationSeconds: Long,
    onClickHere: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        SupportDialogContent(
            durationSeconds = durationSeconds,
            onClickHere = onClickHere,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun SupportDialogContent(
    durationSeconds: Long,
    onClickHere: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ColorSurface)
    ) {
        Column {
            // Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "We Need Your Support",
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorOnSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = ColorDivider,
                thickness = 1.dp,
            )

            // Steps
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "1. Click the button below",
                    "2. Wait for the page to load",
                    "3. Check out the ads page for $durationSeconds seconds",
                    "4. After $durationSeconds seconds ads will be closed automatically",
                ).forEach { step ->
                    Text(
                        text = step,
                        fontFamily = BergenSans,
                        fontSize = 14.sp,
                        color = ColorOnSurfaceMuted,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = ColorDivider,
                thickness = 1.dp,
            )

            // Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ColorOnSurfaceMuted,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorOutline),
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = BergenSans,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.padding(horizontal = 4.dp))

                TextButton(
                    onClick = onClickHere,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = ColorAccent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Click Here",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
