package com.livetvpro.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.livetvpro.app.ui.theme.AppColorTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference
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

    private var activityContextRef: WeakReference<Context>? = null

    fun registerActivityContext(activityContext: Context) {
        activityContextRef = WeakReference(activityContext)
        _primaryColorFlow.value = getPrimaryColor()
    }

    companion object {
        const val THEME_AUTO  = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK  = 2

        private const val KEY_THEME_MODE  = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_AMOLED_MODE = "amoled_mode"

        fun applyThemeStatic(mode: Int) {
            val nightMode = when (mode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                else        -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            } else {
                Handler(Looper.getMainLooper()).post {
                    AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }
    }

    private val _primaryColorFlow = MutableStateFlow(0)
    val primaryColorFlow: StateFlow<Int> = _primaryColorFlow

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<Int> = _themeModeFlow

    private val _colorThemeFlow = MutableStateFlow(getColorTheme())
    val colorThemeFlow: StateFlow<AppColorTheme> = _colorThemeFlow

    private val _amoledFlow = MutableStateFlow(isAmoledMode())
    val amoledFlow: StateFlow<Boolean> = _amoledFlow

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, THEME_AUTO)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
        _primaryColorFlow.value = getPrimaryColor()
        applyThemeStatic(mode)
    }

    fun applyTheme(mode: Int = getThemeMode()) = applyThemeStatic(mode)

    fun getColorTheme(): AppColorTheme =
        AppColorTheme.fromName(
            prefs.getString(KEY_COLOR_THEME, AppColorTheme.Default.name)
                ?: AppColorTheme.Default.name,
        )

    fun setColorTheme(theme: AppColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
        _colorThemeFlow.value = theme
        _primaryColorFlow.value = getPrimaryColor()
    }

    fun isAmoledMode(): Boolean = prefs.getBoolean(KEY_AMOLED_MODE, false)

    fun setAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
        _amoledFlow.value = enabled
        _primaryColorFlow.value = getPrimaryColor()
    }

    fun isDarkMode(activityContext: Context? = null): Boolean {
        return when (getThemeMode()) {
            THEME_DARK  -> true
            THEME_LIGHT -> false
            else -> {
                val nightMode = AppCompatDelegate.getDefaultNightMode()
                when (nightMode) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO  -> false
                    else -> (activityContextRef?.get() ?: activityContext ?: context)
                        .resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
    }

    fun getBackgroundColor(activityContext: Context? = null): Int {
        val theme = getColorTheme()
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = DynamicColors.wrapContextIfAvailable(ctx)
            return MaterialColors.getColor(dynamicContext, android.R.attr.colorBackground, Color.WHITE)
        }
        val resolvedTheme = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        if (isDark && isAmoledMode()) return Color.BLACK
        val color = if (isDark) resolvedTheme.backgroundDark else resolvedTheme.backgroundLight
        return Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
    }

    fun getPrimaryColor(activityContext: Context? = null): Int {
        val theme = getColorTheme()
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = DynamicColors.wrapContextIfAvailable(ctx)
            return MaterialColors.getColor(dynamicContext, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        }
        val resolvedTheme = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        val color = if (isDark) resolvedTheme.primaryDark else resolvedTheme.primaryLight
        return Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
    }

    fun initPrimaryColor() {
        _primaryColorFlow.value = getPrimaryColor()
    }

    fun initPrimaryColor(activityContext: Context) {
        registerActivityContext(activityContext)
    }
}
