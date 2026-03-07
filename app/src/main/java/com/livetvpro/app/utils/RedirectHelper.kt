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

    enum class RedirectResult {
        REDIRECTED,
        NOT_REDIRECTED
    }

    private var dialogShowing = false
    private var validated = false

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
                validated = true
                cooldownMgr.recordFired(pageTypeProvider() ?: return@registerForActivityResult, uniqueIdProvider())
                Toast.makeText(fragment.requireContext(), "Thank you for your support!", Toast.LENGTH_SHORT).show()
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
    ): RedirectResult {
        if (listenerMgr.isInAppRedirectEnabled()) {
            if (validated) return RedirectResult.NOT_REDIRECTED
            if (dialogShowing) return RedirectResult.REDIRECTED
            showSupportDialog(fragment, pageType, uniqueId, listenerMgr, cooldownMgr, launcher)
            return RedirectResult.REDIRECTED
        }

        val redirected = listenerMgr.onPageInteraction(pageType, uniqueId)
        if (!redirected) return RedirectResult.NOT_REDIRECTED
        cooldownMgr.recordFired(pageType, uniqueId)
        return RedirectResult.REDIRECTED
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

            dialogShowing = true
            SupportDialog.show(
                context = fragment.requireContext(),
                durationSeconds = listenerMgr.getAdDurationSeconds(),
                onClickHere = {
                    dialogShowing = false
                    val intent = Intent(fragment.requireContext(), WebActivity::class.java).apply {
                        putExtra("extra_url", url)
                        putExtra("extra_duration", listenerMgr.getAdDurationSeconds())
                    }
                    launcher.launch(intent)
                },
                onCancel = {
                    dialogShowing = false
                }
            )
        } catch (e: Exception) {
            dialogShowing = false
        }
    }
}
