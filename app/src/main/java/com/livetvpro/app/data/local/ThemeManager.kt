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

    companion object {
        const val THEME_AUTO  = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK  = 2

        private const val KEY_THEME_MODE  = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_AMOLED_MODE = "amoled_mode"
    }

    private val fallbackAccent = Color.parseColor("#FFB300")

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

    private val _resolvedIsDarkFlow = MutableStateFlow(resolveIsDark(context))
    val resolvedIsDarkFlow: StateFlow<Boolean> = _resolvedIsDarkFlow

    private val _primaryColorFlow = MutableStateFlow(computePrimaryColor(context))
    val primaryColorFlow: StateFlow<Int> = _primaryColorFlow

    private val _dynamicColorVersionFlow = MutableStateFlow(0)
    val dynamicColorVersionFlow: StateFlow<Int> = _dynamicColorVersionFlow

    private fun resolveIsDark(ctx: Context): Boolean {
        val mode = prefs.getInt(KEY_THEME_MODE, THEME_AUTO)
        val result = when (mode) {
            THEME_DARK  -> true
            THEME_LIGHT -> false
            else -> (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        ThemeDebugLog.log(
            "resolveIsDark",
            "storedMode=$mode (0=auto,1=light,2=dark) colorTheme=${_colorThemeFlow.value} " +
                "ambientUiMode=${ctx.resources.configuration.uiMode} result=$result",
        )
        return result
    }

    fun resolveIsDarkForResources(resources: android.content.res.Resources): Boolean {
        return when (prefs.getInt(KEY_THEME_MODE, THEME_AUTO)) {
            THEME_DARK  -> true
            THEME_LIGHT -> false
            else -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun registerActivityContext(activityContext: Context) {
        activityContextRef = WeakReference(activityContext)
        _resolvedIsDarkFlow.value = resolveIsDark(activityContext)
        _primaryColorFlow.value = computePrimaryColor(activityContext)
    }

    fun notifySystemDarkChanged(isDark: Boolean) {
        if (_themeModeFlow.value == THEME_AUTO) {
            _resolvedIsDarkFlow.value = isDark
            _primaryColorFlow.value = computePrimaryColor()
        }
    }

    fun refreshDynamicColors(activityContext: Context? = null) {
        _dynamicColorVersionFlow.value++
        _primaryColorFlow.value = computePrimaryColor(activityContext)
    }

    fun getThemeMode(): Int = _themeModeFlow.value

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
        _resolvedIsDarkFlow.value = resolveIsDark(activityContextRef?.get() ?: context)
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
        return resolveIsDark(activityContext ?: activityContextRef?.get() ?: context)
    }

    private fun dynamicContextFor(ctx: Context, isDark: Boolean): Context {
        val nightBit = if (isDark) android.content.res.Configuration.UI_MODE_NIGHT_YES
                       else android.content.res.Configuration.UI_MODE_NIGHT_NO
        val overrideConfig = android.content.res.Configuration(ctx.resources.configuration).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBit
        }
        val configuredContext = ctx.createConfigurationContext(overrideConfig)

        val themeResId = try {
            if (ctx is android.app.Activity) {
                ctx.packageManager.getActivityInfo(ctx.componentName, 0).themeResource
                    .takeIf { it != 0 } ?: ctx.applicationInfo.theme
            } else {
                ctx.applicationInfo.theme
            }
        } catch (e: Exception) {
            0
        }
        val themedContext = if (themeResId != 0) {
            android.view.ContextThemeWrapper(configuredContext, themeResId)
        } else {
            configuredContext
        }

        val wrapped = DynamicColors.wrapContextIfAvailable(themedContext)
        val wrapSucceeded = wrapped !== themedContext
        val originalNightBit = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val forcedNightBit = wrapped.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        ThemeDebugLog.log(
            "dynamicContextFor",
            "isDark=$isDark isDynamicColorAvailable=${DynamicColors.isDynamicColorAvailable()} " +
                "themeResId=$themeResId wrapSucceeded=$wrapSucceeded originalNightBit=$originalNightBit " +
                "forcedNightBitOnWrapped=$forcedNightBit sdk=${Build.VERSION.SDK_INT}",
        )
        return wrapped
    }

    fun getBackgroundColor(activityContext: Context? = null): Int {
        val theme  = _colorThemeFlow.value
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = dynamicContextFor(ctx, isDark)
            val resolved = MaterialColors.getColor(dynamicContext, android.R.attr.colorBackground, Color.WHITE)
            ThemeDebugLog.log(
                "getBackgroundColor",
                "theme=Dynamic isDark=$isDark resolvedHex=${Integer.toHexString(resolved)} " +
                    "usedFallback=${resolved == Color.WHITE}",
            )
            return resolved
        }
        val resolved = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        if (isDark && _amoledFlow.value) {
            ThemeDebugLog.log("getBackgroundColor", "theme=$theme isDark=$isDark amoled=true -> BLACK")
            return Color.BLACK
        }
        val color = if (isDark) resolved.backgroundDark else resolved.backgroundLight
        val result = Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
        ThemeDebugLog.log(
            "getBackgroundColor",
            "theme=$theme isDark=$isDark resolvedHex=${Integer.toHexString(result)}",
        )
        return result
    }

    fun getSurfaceContainerColor(activityContext: Context? = null): Int {
        val theme  = _colorThemeFlow.value
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = dynamicContextFor(ctx, isDark)
            return MaterialColors.getColor(dynamicContext, com.google.android.material.R.attr.colorSurfaceContainer, Color.LTGRAY)
        }
        val resolved = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        if (isDark && _amoledFlow.value) {
            return blendOver(resolved.primaryDark, 0.05f, androidx.compose.ui.graphics.Color(0xFF0D0D0D))
        }
        val primary    = if (isDark) resolved.primaryDark else resolved.primaryLight
        val background = if (isDark) resolved.backgroundDark else resolved.backgroundLight
        return blendOver(primary, if (isDark) 0.05f else 0.06f, background)
    }

    private fun blendOver(
        primary: androidx.compose.ui.graphics.Color,
        alpha: Float,
        background: androidx.compose.ui.graphics.Color,
    ): Int {
        val fgA = alpha
        val bgA = background.alpha
        val a   = fgA + bgA * (1f - fgA)
        val r   = (primary.red   * fgA + background.red   * bgA * (1f - fgA)) / a
        val g   = (primary.green * fgA + background.green * bgA * (1f - fgA)) / a
        val b   = (primary.blue  * fgA + background.blue  * bgA * (1f - fgA)) / a
        return Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    fun getOnSurfaceColor(activityContext: Context? = null): Int =
        if (isDarkMode(activityContext)) Color.WHITE else Color.BLACK

    private fun computePrimaryColor(activityContext: Context? = null): Int {
        val theme  = _colorThemeFlow.value
        val isDark = isDarkMode(activityContext)
        if (theme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctx = activityContext ?: activityContextRef?.get() ?: context
            val dynamicContext = dynamicContextFor(ctx, isDark)
            return MaterialColors.getColor(dynamicContext, androidx.appcompat.R.attr.colorPrimary, fallbackAccent)
        }
        val resolved = if (theme == AppColorTheme.Dynamic) AppColorTheme.Default else theme
        val color = if (isDark) resolved.primaryDark else resolved.primaryLight
        return Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
    }

    fun getPrimaryColor(activityContext: Context? = null): Int = computePrimaryColor(activityContext)
}
