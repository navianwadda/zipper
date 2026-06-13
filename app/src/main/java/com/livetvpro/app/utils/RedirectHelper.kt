package com.livetvpro.app.utils

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.livetvpro.app.ui.webview.WebActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RedirectHelper {

    enum class RedirectResult {
        REDIRECTED,
        NOT_REDIRECTED
    }

    data class SupportDialogState(
        val durationSeconds: Long,
        val onClickHere: () -> Unit,
        val onCancel: () -> Unit,
    )

    private val _dialogState = MutableStateFlow<SupportDialogState?>(null)
    val dialogState: StateFlow<SupportDialogState?> = _dialogState.asStateFlow()

    private var dialogShowing = false
    private var pendingPostDialogAction: (() -> Unit)? = null

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
            val action = pendingPostDialogAction
            pendingPostDialogAction = null
            if (result.resultCode == WebActivity.RESULT_VALIDATED) {
                cooldownMgr.recordFired(pageTypeProvider() ?: return@registerForActivityResult, uniqueIdProvider())
                Toast.makeText(fragment.requireContext(), "Thank you for your support!", Toast.LENGTH_SHORT).show()
                action?.invoke()
            } else {
                cooldownMgr.undoLastFire(pageTypeProvider() ?: return@registerForActivityResult, uniqueIdProvider())
            }
        }
    }

    fun tryRedirect(
        fragment: Fragment,
        pageType: String,
        uniqueId: String? = null,
        cooldownMgr: RedirectCooldownManager,
        listenerMgr: NativeListenerManager,
        launcher: ActivityResultLauncher<Intent>,
        onAfterDialog: (() -> Unit)? = null
    ): RedirectResult {
        if (!listenerMgr.isConfigValid()) return RedirectResult.NOT_REDIRECTED
        if (!listenerMgr.isRedirectEnabled()) return RedirectResult.NOT_REDIRECTED
        if (!cooldownMgr.canFire(pageType, uniqueId)) return RedirectResult.NOT_REDIRECTED
        if (!listenerMgr.onPageInteraction(pageType, uniqueId, cooldownMgr.maxClicksPerPage, cooldownMgr.maxTotalClicks)) return RedirectResult.NOT_REDIRECTED

        if (listenerMgr.isInAppRedirectEnabled()) {
            if (dialogShowing) return RedirectResult.REDIRECTED
            showSupportDialog(fragment, pageType, uniqueId, listenerMgr, cooldownMgr, launcher, onAfterDialog)
            return RedirectResult.REDIRECTED
        }

        val url = listenerMgr.getDirectLinkUrl()
        if (url.isEmpty()) return RedirectResult.NOT_REDIRECTED
        cooldownMgr.recordFired(pageType, uniqueId)
        listenerMgr.openDirectLink(url)
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

    fun dismissDialog() {
        _dialogState.value = null
    }

    private fun showSupportDialog(
        fragment: Fragment,
        pageType: String,
        uniqueId: String?,
        listenerMgr: NativeListenerManager,
        cooldownMgr: RedirectCooldownManager,
        launcher: ActivityResultLauncher<Intent>,
        onAfterDialog: (() -> Unit)? = null
    ) {
        val url = listenerMgr.getDirectLinkUrl()
        if (url.isEmpty()) {
            onAfterDialog?.invoke()
            return
        }

        dialogShowing = true
        _dialogState.value = SupportDialogState(
            durationSeconds = listenerMgr.getAdDurationSeconds(),
            onClickHere = {
                dialogShowing = false
                _dialogState.value = null
                val intent = Intent(fragment.requireContext(), WebActivity::class.java).apply {
                    putExtra("extra_url", url)
                    putExtra("extra_duration", listenerMgr.getAdDurationSeconds())
                }
                pendingPostDialogAction = onAfterDialog
                launcher.launch(intent)
            },
            onCancel = {
                dialogShowing = false
                _dialogState.value = null
                cooldownMgr.undoLastFire(pageType, uniqueId)
            }
        )
    }
}
