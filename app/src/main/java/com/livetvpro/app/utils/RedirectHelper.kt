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

    fun registerLauncher(
        fragment: Fragment,
        cooldownMgr: RedirectCooldownManager,
        pageTypeProvider: () -> String?,
        uniqueIdProvider: () -> String?
    ): ActivityResultLauncher<Intent> {
        return fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == WebActivity.RESULT_VALIDATED) {
                val pageType = pageTypeProvider() ?: return@registerForActivityResult
                cooldownMgr.recordFired(pageType, uniqueIdProvider())
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
    ): Boolean {
        if (!cooldownMgr.canFire(pageType, uniqueId)) return false

        listenerMgr.resetSessions()
        val redirected = listenerMgr.onPageInteraction(pageType, uniqueId)
        if (!redirected) return false

        if (listenerMgr.isInAppRedirectEnabled()) {
            showSupportDialog(fragment, pageType, uniqueId, listenerMgr, launcher)
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
        launcher: ActivityResultLauncher<Intent>
    ) {
        try {
            val fm = fragment.parentFragmentManager
            if (fm.findFragmentByTag(SupportDialog.TAG) != null) return

            val url = listenerMgr.getDirectLinkUrl()
            if (url.isEmpty()) return

            SupportDialog.newInstance().apply {
                this.url = url
                this.durationSeconds = listenerMgr.getAdDurationSeconds()
                onClickHere = {
                    val intent = Intent(fragment.requireContext(), WebActivity::class.java).apply {
                        putExtra("extra_url", url)
                        putExtra("extra_duration", listenerMgr.getAdDurationSeconds())
                    }
                    launcher.launch(intent)
                }
                onCancel = {}
            }.show(fm, SupportDialog.TAG)
        } catch (e: Exception) {}
    }
}
