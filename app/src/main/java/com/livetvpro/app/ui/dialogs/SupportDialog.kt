package com.livetvpro.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.livetvpro.app.R

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

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
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
    ) {
        Column {
            Text(
                text = "We Need Your Support",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

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
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Text(text = "Cancel", fontFamily = BergenSans, fontSize = 14.sp)
                }

                Spacer(Modifier.padding(horizontal = 4.dp))

                TextButton(
                    onClick = onClickHere,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color(0xFFEF4444),
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
