package com.livetvpro.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "live_tv_pro_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_FLOATING_PLAYER_ENABLED = "floating_player_enabled"
        private const val KEY_FLOATING_PLAYER_AUTO_START = "floating_player_auto_start"
        private const val KEY_MAX_FLOATING_WINDOWS = "max_floating_windows"
        private const val KEY_FLOATING_PLAYER_WIDTH = "floating_player_width"
        private const val KEY_FLOATING_PLAYER_HEIGHT = "floating_player_height"
        private const val KEY_FLOATING_PLAYER_X = "floating_player_x"
        private const val KEY_FLOATING_PLAYER_Y = "floating_player_y"
        private const val KEY_REMEMBER_ASPECT_RATIO = "remember_aspect_ratio"
        private const val KEY_SAVED_ASPECT_RATIO = "saved_aspect_ratio"
        private const val KEY_SAVED_ASPECT_RATIO_PORTRAIT = "saved_aspect_ratio_portrait"
        private const val KEY_FORCE_LOWEST_QUALITY = "force_lowest_quality"
        private const val KEY_LAYOUT_MODE = "layout_mode"

        const val LAYOUT_MODE_SEEKS_ONLY       = 0
        const val LAYOUT_MODE_SEEKS_AND_NAV    = 1
        const val LAYOUT_MODE_NAV_ONLY         = 2
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    fun setFirstLaunchComplete() = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    fun getLastUpdate(): Long = prefs.getLong(KEY_LAST_UPDATE, 0L)
    fun setLastUpdate(timestamp: Long) = prefs.edit().putLong(KEY_LAST_UPDATE, timestamp).apply()

    fun isFloatingPlayerEnabled(): Boolean = prefs.getBoolean(KEY_FLOATING_PLAYER_ENABLED, false)
    fun setFloatingPlayerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_FLOATING_PLAYER_ENABLED, enabled).apply()

    fun isFloatingPlayerAutoStart(): Boolean = prefs.getBoolean(KEY_FLOATING_PLAYER_AUTO_START, false)
    fun setFloatingPlayerAutoStart(autoStart: Boolean) = prefs.edit().putBoolean(KEY_FLOATING_PLAYER_AUTO_START, autoStart).apply()

    fun getMaxFloatingWindows(): Int = prefs.getInt(KEY_MAX_FLOATING_WINDOWS, 1)
    fun setMaxFloatingWindows(max: Int) = prefs.edit().putInt(KEY_MAX_FLOATING_WINDOWS, max).apply()

    fun getFloatingPlayerWidth(): Int = prefs.getInt(KEY_FLOATING_PLAYER_WIDTH, 0)
    fun setFloatingPlayerWidth(width: Int) = prefs.edit().putInt(KEY_FLOATING_PLAYER_WIDTH, width).apply()

    fun getFloatingPlayerHeight(): Int = prefs.getInt(KEY_FLOATING_PLAYER_HEIGHT, 0)
    fun setFloatingPlayerHeight(height: Int) = prefs.edit().putInt(KEY_FLOATING_PLAYER_HEIGHT, height).apply()

    fun getFloatingPlayerX(): Int = prefs.getInt(KEY_FLOATING_PLAYER_X, Int.MIN_VALUE)
    fun setFloatingPlayerX(x: Int) = prefs.edit().putInt(KEY_FLOATING_PLAYER_X, x).apply()

    fun getFloatingPlayerY(): Int = prefs.getInt(KEY_FLOATING_PLAYER_Y, Int.MIN_VALUE)
    fun setFloatingPlayerY(y: Int) = prefs.edit().putInt(KEY_FLOATING_PLAYER_Y, y).apply()

    fun getFloatingPlayerXRatio(): Float = java.lang.Float.intBitsToFloat(prefs.getInt(KEY_FLOATING_PLAYER_X + "_ratio", java.lang.Float.floatToIntBits(-1f)))
    fun setFloatingPlayerXRatio(ratio: Float) = prefs.edit().putInt(KEY_FLOATING_PLAYER_X + "_ratio", java.lang.Float.floatToIntBits(ratio)).apply()

    fun getFloatingPlayerYRatio(): Float = java.lang.Float.intBitsToFloat(prefs.getInt(KEY_FLOATING_PLAYER_Y + "_ratio", java.lang.Float.floatToIntBits(-1f)))
    fun setFloatingPlayerYRatio(ratio: Float) = prefs.edit().putInt(KEY_FLOATING_PLAYER_Y + "_ratio", java.lang.Float.floatToIntBits(ratio)).apply()

    fun clearAll() = prefs.edit().clear().apply()

    fun isRememberAspectRatioEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ASPECT_RATIO, false)
    fun setRememberAspectRatioEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_REMEMBER_ASPECT_RATIO, enabled).apply()

    fun getSavedAspectRatio(): Int = prefs.getInt(KEY_SAVED_ASPECT_RATIO, -1)
    fun setSavedAspectRatio(resizeMode: Int) = prefs.edit().putInt(KEY_SAVED_ASPECT_RATIO, resizeMode).apply()

    fun getSavedAspectRatioPortrait(): Int = prefs.getInt(KEY_SAVED_ASPECT_RATIO_PORTRAIT, -1)
    fun setSavedAspectRatioPortrait(resizeMode: Int) = prefs.edit().putInt(KEY_SAVED_ASPECT_RATIO_PORTRAIT, resizeMode).apply()

    fun isForceLowestQualityEnabled(): Boolean = prefs.getBoolean(KEY_FORCE_LOWEST_QUALITY, false)
    fun setForceLowestQualityEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_FORCE_LOWEST_QUALITY, enabled).apply()

    fun getLayoutMode(): Int = prefs.getInt(KEY_LAYOUT_MODE, LAYOUT_MODE_SEEKS_ONLY)
    fun setLayoutMode(mode: Int) = prefs.edit().putInt(KEY_LAYOUT_MODE, mode).apply()
}
