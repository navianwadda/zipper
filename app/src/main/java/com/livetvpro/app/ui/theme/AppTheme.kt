package com.livetvpro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.livetvpro.app.data.local.ThemeManager

@Composable
fun LiveTVProTheme(
    themeManager: ThemeManager,
    surfaceColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context    = LocalContext.current

    val themeMode  by themeManager.themeModeFlow.collectAsState()
    val colorTheme by themeManager.colorThemeFlow.collectAsState()
    val amoled     by themeManager.amoledFlow.collectAsState()
    val isDark     by themeManager.isDarkFlow.collectAsState(initial = themeManager.isDarkMode())

    val colorScheme = when {
        colorTheme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> when {
            isDark && amoled -> dynamicDarkColorScheme(context).copy(
                background             = Color.Black,
                surface                = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow    = Color(0xFF050505),
                surfaceContainer       = Color(0xFF0D0D0D),
            )
            isDark -> dynamicDarkColorScheme(context)
            else    -> dynamicLightColorScheme(context)
        }
        isDark && amoled -> colorTheme.getAmoledColorScheme()
        isDark           -> colorTheme.getDarkColorScheme()
        else              -> colorTheme.getLightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = (context as? android.app.Activity)
                ?: generateSequence(view.context) {
                    (it as? android.content.ContextWrapper)?.baseContext
                }.filterIsInstance<android.app.Activity>().firstOrNull()
                ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            val wic = WindowCompat.getInsetsController(window, window.decorView)
            wic.isAppearanceLightStatusBars = !isDark
            window.navigationBarColor = Color.Transparent.toArgb()
            wic.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = surfaceColor ?: MaterialTheme.colorScheme.background,
            content  = content,
        )
    }
}
