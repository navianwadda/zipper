package com.livetvpro.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
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
        _primaryColorFlow.value = computePrimaryColor()
    }

    companion object {
        const val THEME_AUTO  = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK  = 2

        private const val KEY_THEME_MODE  = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_AMOLED_MODE = "amoled_mode"
    }

    private val _themeModeFlow = MutableStateFlow(
        prefs.getInt(KEY_THEME_MODE, THEME_AUTO)
    )
    val themeModeFlow: StateFlow<Int> = _themeModeFlow

    private val _colorThemeFlow = MutableStateFlow(
        AppColorTheme.fromName(
            prefs.getString(KEY_COLOR_THEME, AppColorTheme.Default.name)
                ?: AppColorTheme.Default.name
        )
    )
    val colorThemeFlow: StateFlow<AppColorTheme> = _colorThemeFlow

    private val _amoledFlow = MutableStateFlow(
        prefs.getBoolean(KEY_AMOLED_MODE, false)
    )
    val amoledFlow: StateFlow<Boolean> = _amoledFlow

    private val _primaryColorFlow = MutableStateFlow(computePrimaryColor())
    val primaryColorFlow: StateFlow<Int> = _primaryColorFlow

    fun getThemeMode(): Int = _themeModeFlow.value

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
        _primaryColorFlow.value = computePrimaryColor()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (mode) {
            THEME_LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else        -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    fun getColorTheme(): AppColorTheme = _colorThemeFlow.value

    fun setColorTheme(theme: AppColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
        _colorThemeFlow.value = theme
        _primaryColorFlow.value = computePrimaryColor()
    }

    fun isAmoledMode(): Boolean = _amoledFlow.value

    fun setAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
        _amoledFlow.value = enabled
        _primaryColorFlow.value = computePrimaryColor()
    }

    fun isDarkMode(activityContext: Context? = null): Boolean {
        return when (_themeModeFlow.value) {
            THEME_DARK  -> true
            THEME_LIGHT -> false
            else -> {
                val ctx = activityContextRef?.get() ?: activityContext
                if (ctx != null) {
                    ctx.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                } else {
                    androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() ==
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                }
            }
        }
    }

    fun getBackgroundColor(activityContext: Context? = null): Int {
        val theme = _colorThemeFlow.value
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = DynamicColors.wrapContextIfAvailable(ctx)
            return MaterialColors.getColor(dynamicContext, android.R.attr.colorBackground, Color.WHITE)
        }
        val resolvedTheme = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        if (isDark && _amoledFlow.value) return Color.BLACK
        val color = if (isDark) resolvedTheme.backgroundDark else resolvedTheme.backgroundLight
        return Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
    }

    fun getSurfaceContainerColor(activityContext: Context? = null): Int {
        val theme = _colorThemeFlow.value
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = DynamicColors.wrapContextIfAvailable(ctx)
            return MaterialColors.getColor(dynamicContext, com.google.android.material.R.attr.colorSurfaceContainer, Color.LTGRAY)
        }
        val resolvedTheme = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        if (isDark && _amoledFlow.value) {
            val primary = resolvedTheme.primaryDark
            val base = androidx.compose.ui.graphics.Color(0xFF0D0D0D)
            return blendOver(primary, 0.05f, base)
        }
        val primary = if (isDark) resolvedTheme.primaryDark else resolvedTheme.primaryLight
        val background = if (isDark) resolvedTheme.backgroundDark else resolvedTheme.backgroundLight
        return blendOver(primary, if (isDark) 0.05f else 0.06f, background)
    }

    private fun blendOver(primary: androidx.compose.ui.graphics.Color, alpha: Float, background: androidx.compose.ui.graphics.Color): Int {
        val fgA = alpha
        val bgA = background.alpha
        val a = fgA + bgA * (1f - fgA)
        val r = (primary.red * fgA + background.red * bgA * (1f - fgA)) / a
        val g = (primary.green * fgA + background.green * bgA * (1f - fgA)) / a
        val b = (primary.blue * fgA + background.blue * bgA * (1f - fgA)) / a
        return Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    fun getOnSurfaceColor(activityContext: Context? = null): Int {
        return if (isDarkMode(activityContext)) Color.WHITE else Color.BLACK
    }

    private fun computePrimaryColor(activityContext: Context? = null): Int {
        val theme = _colorThemeFlow.value
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

    fun getPrimaryColor(activityContext: Context? = null): Int = computePrimaryColor(activityContext)
}
