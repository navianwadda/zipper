package com.livetvpro.app.ui.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SupportDialog {

    const val TAG = "SupportDialog"

    fun show(
        context: Context,
        durationSeconds: Long,
        onClickHere: () -> Unit,
        onCancel: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("We Need Your Support")
            .setMessage(
                "1. Click the button below\n" +
                "2. Wait for the page to load\n" +
                "3. Check out the ads page for $durationSeconds seconds\n" +
                "4. After $durationSeconds seconds ads will be closed automatically"
            )
            .setPositiveButton("Click Here") { _, _ -> onClickHere() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }
}
