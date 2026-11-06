package com.livetvpro.app.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.livetvpro.app.utils.CrashLogger
import com.livetvpro.app.utils.NativeListenerManager

@Composable
fun CrashLoggerDialogHost(listenerManager: NativeListenerManager) {
    val context = LocalContext.current

    var report by remember {
        mutableStateOf(CrashLogger.consumePendingCrash(context))
    }

    report?.let { crashReport ->
        CrashLoggerDialog(
            report = crashReport,
            supportEmail = listenerManager.getEmailUs(),
            onDismiss = { report = null },
        )
    }
}
