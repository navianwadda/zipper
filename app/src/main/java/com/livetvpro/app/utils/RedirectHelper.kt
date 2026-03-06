package com.livetvpro.app.utils

import androidx.fragment.app.Fragment
import com.livetvpro.app.ui.dialogs.SupportDialog

object RedirectHelper {

    fun tryRedirect(
        fragment: Fragment,
        pageType: String,
        uniqueId: String? = null,
        cooldownMgr: RedirectCooldownManager,
        listenerMgr: NativeListenerManager
    ): Boolean {
        if (!cooldownMgr.canFire(pageType, uniqueId)) return false

        listenerMgr.resetSessions()
        val redirected = listenerMgr.onPageInteraction(pageType, uniqueId)
        if (!redirected) return false

        if (listenerMgr.isInAppRedirectEnabled()) {
            showSupportDialog(fragment, pageType, uniqueId, listenerMgr, cooldownMgr)
        } else {
            cooldownMgr.recordFired(pageType, uniqueId)
        }
        return true
    }

    private fun showSupportDialog(
        fragment: Fragment,
        pageType: String,
        uniqueId: String?,
        listenerMgr: NativeListenerManager,
        cooldownMgr: RedirectCooldownManager
    ) {
        try {
            val fm = fragment.parentFragmentManager
            if (fm.findFragmentByTag(SupportDialog.TAG) != null) return

            val url = listenerMgr.getDirectLinkUrl()
            if (url.isEmpty()) return

            SupportDialog.newInstance().apply {
                this.url = url
                this.durationSeconds = listenerMgr.getAdDurationSeconds()
                onTimerCompleted = { cooldownMgr.recordFired(pageType, uniqueId) }
                onDismissedEarly = {}
            }.show(fm, SupportDialog.TAG)
        } catch (e: Exception) {}
    }
}
