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

    private val pageClickCounts = mutableMapOf<String, Int>()
    private var totalClickCount = 0

    companion object {
        private const val PREFS_NAME             = "redirect_cooldown_prefs"
        private const val REMOTE_CONFIG_COOLDOWN = "redirect_cooldown_minutes"
        const val REMOTE_CONFIG_MAX_PER_PAGE     = "ad_max_clicks_per_page"
        const val REMOTE_CONFIG_MAX_TOTAL        = "ad_max_total_clicks"
        private const val NO_LIMIT               = 0L
    }

    private val cooldownMs: Long
        get() = Firebase.remoteConfig.getLong(REMOTE_CONFIG_COOLDOWN) * 60 * 1000L

    val maxClicksPerPage: Long
        get() = try { Firebase.remoteConfig.getLong(REMOTE_CONFIG_MAX_PER_PAGE) } catch (e: Exception) { NO_LIMIT }

    val maxTotalClicks: Long
        get() = try { Firebase.remoteConfig.getLong(REMOTE_CONFIG_MAX_TOTAL) } catch (e: Exception) { NO_LIMIT }

    private fun cooldownKey(pageType: String): String = pageType

    private fun isCooldownExpired(pageType: String): Boolean {
        val lastFired = prefs.getLong(cooldownKey(pageType), 0L)
        val cooldown = cooldownMs
        return cooldown <= 0L || System.currentTimeMillis() - lastFired >= cooldown
    }

    private fun recordCooldown(pageType: String) {
        prefs.edit().putLong(cooldownKey(pageType), System.currentTimeMillis()).apply()
    }

    private fun isPageLimitReached(pageType: String): Boolean {
        val max = maxClicksPerPage
        if (max <= NO_LIMIT) return false
        return (pageClickCounts[pageType] ?: 0) >= max
    }

    private fun isTotalLimitReached(): Boolean {
        val max = maxTotalClicks
        if (max <= NO_LIMIT) return false
        return totalClickCount >= max
    }

    fun canFire(pageType: String, uniqueId: String? = null): Boolean {
        if (isTotalLimitReached()) return false
        if (isPageLimitReached(pageType)) return false
        return isCooldownExpired(pageType)
    }

    fun recordFired(pageType: String, uniqueId: String? = null) {
        recordCooldown(pageType)
        pageClickCounts[pageType] = (pageClickCounts[pageType] ?: 0) + 1
        totalClickCount++
    }

    fun undoLastFire(pageType: String, uniqueId: String? = null) {
        val current = pageClickCounts[pageType] ?: 0
        if (current > 0) pageClickCounts[pageType] = current - 1
        if (totalClickCount > 0) totalClickCount--
        prefs.edit().remove(cooldownKey(pageType)).apply()
    }

    fun resetSessionCounts() {
        pageClickCounts.clear()
        totalClickCount = 0
    }
}
