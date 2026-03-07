package com.livetvpro.app.utils

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.livetvpro.app.ui.dialogs.SupportDialog
import com.livetvpro.app.ui.webview.WebActivity

object RedirectHelper {

    private var dialogShowing = false

    fun registerLauncher(
        fragment: Fragment,
        cooldownMgr: RedirectCooldownManager,
        pageTypeProvider: () -> String?,
        uniqueIdProvider: () -> String?
    ): ActivityResultLauncher<Intent> {
        return fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            dialogShowing = false
            if (result.resultCode == WebActivity.RESULT_VALIDATED) {
                Toast.makeText(fragment.requireContext(), "Thank you for your support!", Toast.LENGTH_SHORT).show()
            } else {
                val pageType = pageTypeProvider() ?: return@registerForActivityResult
                cooldownMgr.undoLastFire(pageType, uniqueIdProvider())
            }
        }
    }

    fun tryRedirect(
        fragment: Fragment,
        pageType: String,
        uniqueId: String? = null,
        cooldownMgr: RedirectCooldownManager,
        listenerMgr: NativeListenerManager,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        if (!cooldownMgr.canFire(pageType, uniqueId)) return false
        if (dialogShowing) return false

        listenerMgr.resetSessions()
        val redirected = listenerMgr.onPageInteraction(pageType, uniqueId)
        if (!redirected) return false

        if (listenerMgr.isInAppRedirectEnabled()) {
            dialogShowing = true
            showSupportDialog(fragment, pageType, uniqueId, listenerMgr, cooldownMgr, launcher)
        } else {
            cooldownMgr.recordFired(pageType, uniqueId)
        }
        return true
    }

    fun executePendingActionOnResume(
        pendingActionProvider: () -> (() -> Unit)?,
        clearPendingAction: () -> Unit,
        pendingExternalRedirect: Boolean,
        clearPendingRedirect: () -> Unit
    ) {
        if (pendingExternalRedirect) {
            clearPendingRedirect()
            pendingActionProvider()?.invoke()
            clearPendingAction()
        }
    }

    private fun showSupportDialog(
        fragment: Fragment,
        pageType: String,
        uniqueId: String?,
        listenerMgr: NativeListenerManager,
        cooldownMgr: RedirectCooldownManager,
        launcher: ActivityResultLauncher<Intent>
    ) {
        try {
            val url = listenerMgr.getDirectLinkUrl()
            if (url.isEmpty()) return

            SupportDialog.show(
                context = fragment.requireContext(),
                durationSeconds = listenerMgr.getAdDurationSeconds(),
                onClickHere = {
                    cooldownMgr.recordFired(pageType, uniqueId)
                    val intent = Intent(fragment.requireContext(), WebActivity::class.java).apply {
                        putExtra("extra_url", url)
                        putExtra("extra_duration", listenerMgr.getAdDurationSeconds())
                    }
                    launcher.launch(intent)
                },
                onCancel = {
                    dialogShowing = false
                    cooldownMgr.undoLastFire(pageType, uniqueId)
                }
            )
        } catch (e: Exception) {}
    }
}
