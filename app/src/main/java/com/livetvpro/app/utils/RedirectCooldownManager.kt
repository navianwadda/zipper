package com.livetvpro.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedirectCooldownManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val listenerManager: NativeListenerManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "redirect_cooldown_prefs"
        private const val REMOTE_CONFIG_KEY = "redirect_cooldown_minutes"
    }

    private val cooldownMs: Long
        get() = Firebase.remoteConfig.getLong(REMOTE_CONFIG_KEY) * 60 * 1000L

    private fun key(pageType: String, uniqueId: String? = null): String {
        return pageType
    }

    fun canFire(pageType: String, uniqueId: String? = null): Boolean {
        val lastFired = prefs.getLong(key(pageType, uniqueId), 0L)
        return System.currentTimeMillis() - lastFired >= cooldownMs
    }

    fun recordFired(pageType: String, uniqueId: String? = null) {
        prefs.edit().putLong(key(pageType, uniqueId), System.currentTimeMillis()).apply()
    }

    fun tryFire(pageType: String, uniqueId: String? = null, action: () -> Boolean): Boolean {
        if (canFire(pageType, uniqueId)) {
            listenerManager.resetSessions()
            val redirected = action()
            if (redirected) recordFired(pageType, uniqueId)
            return redirected
        }
        return false
    }
}
