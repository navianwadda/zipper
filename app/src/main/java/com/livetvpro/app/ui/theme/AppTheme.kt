package com.livetvpro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
    val systemDark = isSystemInDarkTheme()

    val themeMode  by themeManager.themeModeFlow.collectAsState()
    val colorTheme by themeManager.colorThemeFlow.collectAsState()
    val amoled     by themeManager.amoledFlow.collectAsState()

    val useDark = when (themeMode) {
        ThemeManager.THEME_DARK  -> true
        ThemeManager.THEME_LIGHT -> false
        else                     -> systemDark
    }

    val colorScheme = when {
        colorTheme == AppColorTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> when {
            useDark && amoled -> dynamicDarkColorScheme(context).copy(
                background             = Color.Black,
                surface                = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow    = Color(0xFF050505),
                surfaceContainer       = Color(0xFF0D0D0D),
            )
            useDark  -> dynamicDarkColorScheme(context)
            else     -> dynamicLightColorScheme(context)
        }
        useDark && amoled -> colorTheme.getAmoledColorScheme()
        useDark           -> colorTheme.getDarkColorScheme()
        else              -> colorTheme.getLightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val wic = WindowCompat.getInsetsController(window, view)
            wic.isAppearanceLightStatusBars = !useDark
            wic.isAppearanceLightNavigationBars = !useDark
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
