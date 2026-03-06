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
        private const val PREFS_NAME             = "redirect_cooldown_prefs"
        private const val REMOTE_CONFIG_COOLDOWN = "redirect_cooldown_minutes"
        const val REMOTE_CONFIG_MAX_PER_PAGE     = "ad_max_clicks_per_page"
        const val REMOTE_CONFIG_MAX_TOTAL        = "ad_max_total_clicks"
        private const val NO_LIMIT               = 0L
        private const val KEY_TOTAL_CLICKS       = "total_click_count"
        private const val KEY_PAGE_CLICKS_PREFIX = "page_clicks_"
    }

    private val cooldownMs: Long
        get() = Firebase.remoteConfig.getLong(REMOTE_CONFIG_COOLDOWN) * 60 * 1000L

    val maxClicksPerPage: Long
        get() = try { Firebase.remoteConfig.getLong(REMOTE_CONFIG_MAX_PER_PAGE) } catch (e: Exception) { NO_LIMIT }

    val maxTotalClicks: Long
        get() = try { Firebase.remoteConfig.getLong(REMOTE_CONFIG_MAX_TOTAL) } catch (e: Exception) { NO_LIMIT }

    private fun cooldownKey(pageType: String): String = pageType

    private fun pageClickKey(pageType: String): String = KEY_PAGE_CLICKS_PREFIX + pageType

    private fun isCooldownExpired(pageType: String): Boolean {
        val lastFired = prefs.getLong(cooldownKey(pageType), 0L)
        val cooldown = cooldownMs
        return cooldown <= 0L || System.currentTimeMillis() - lastFired >= cooldown
    }

    private fun recordCooldown(pageType: String) {
        prefs.edit().putLong(cooldownKey(pageType), System.currentTimeMillis()).apply()
    }

    private fun getPageClickCount(pageType: String): Int =
        prefs.getInt(pageClickKey(pageType), 0)

    private fun getTotalClickCount(): Int =
        prefs.getInt(KEY_TOTAL_CLICKS, 0)

    private fun isPageLimitReached(pageType: String): Boolean {
        val max = maxClicksPerPage
        if (max <= NO_LIMIT) return false
        return getPageClickCount(pageType) >= max
    }

    private fun isTotalLimitReached(): Boolean {
        val max = maxTotalClicks
        if (max <= NO_LIMIT) return false
        return getTotalClickCount() >= max
    }

    fun canFire(pageType: String, uniqueId: String? = null): Boolean {
        if (isTotalLimitReached()) return false
        if (isPageLimitReached(pageType)) return false
        return isCooldownExpired(pageType)
    }

    fun recordFired(pageType: String, uniqueId: String? = null) {
        recordCooldown(pageType)
        val newPage = getPageClickCount(pageType) + 1
        val newTotal = getTotalClickCount() + 1
        prefs.edit()
            .putInt(pageClickKey(pageType), newPage)
            .putInt(KEY_TOTAL_CLICKS, newTotal)
            .apply()
    }

    fun undoLastFire(pageType: String, uniqueId: String? = null) {
        val currentPage = getPageClickCount(pageType)
        val currentTotal = getTotalClickCount()
        prefs.edit()
            .putInt(pageClickKey(pageType), if (currentPage > 0) currentPage - 1 else 0)
            .putInt(KEY_TOTAL_CLICKS, if (currentTotal > 0) currentTotal - 1 else 0)
            .remove(cooldownKey(pageType))
            .apply()
    }

    fun resetSessionCounts() {
        prefs.edit()
            .putInt(KEY_TOTAL_CLICKS, 0)
            .apply()
        val allKeys = prefs.all.keys.filter { it.startsWith(KEY_PAGE_CLICKS_PREFIX) }
        val editor = prefs.edit()
        allKeys.forEach { editor.remove(it) }
        editor.apply()
    }
}
