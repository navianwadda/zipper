package com.livetvpro.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.livetvpro.app.ui.theme.AppColorTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "live_tv_pro_prefs",
        Context.MODE_PRIVATE,
    )

    companion object {
        const val THEME_AUTO  = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK  = 2

        private const val KEY_THEME_MODE  = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_AMOLED_MODE = "amoled_mode"

        /**
         * Static version — safe to call from Application.attachBaseContext()
         * before Hilt injects the ThemeManager instance.
         */
        fun applyThemeStatic(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                    else        -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }
    }

    // ── Dark / light mode ─────────────────────────────────────────────────────

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, THEME_AUTO)

    /**
     * Persist and apply a new dark/light mode.
     * The activity must call recreate() afterwards so XML layouts re-inflate
     * with the updated night mode.
     */
    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        applyThemeStatic(mode)
    }

    /** Instance wrapper kept for backwards compat. */
    fun applyTheme(mode: Int = getThemeMode()) = applyThemeStatic(mode)

    // ── Colour theme ──────────────────────────────────────────────────────────

    fun getColorTheme(): AppColorTheme =
        AppColorTheme.fromName(
            prefs.getString(KEY_COLOR_THEME, AppColorTheme.Default.name)
                ?: AppColorTheme.Default.name,
        )

    /**
     * Persist a new color theme.
     * Callers MUST call Activity.recreate() so the Compose MaterialTheme
     * and any XML-based colour references are refreshed.
     */
    fun setColorTheme(theme: AppColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
    }

    // ── AMOLED mode ───────────────────────────────────────────────────────────

    fun isAmoledMode(): Boolean = prefs.getBoolean(KEY_AMOLED_MODE, false)

    fun setAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }
}
