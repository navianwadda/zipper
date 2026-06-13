package com.livetvpro.app.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.livetvpro.app.utils.RedirectHelper

@Composable
fun SupportDialogHost() {
    val state by RedirectHelper.dialogState.collectAsState()
    state?.let {
        SupportDialog(
            durationSeconds = it.durationSeconds,
            onClickHere = it.onClickHere,
            onCancel = it.onCancel,
        )
    }
}
