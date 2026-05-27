package com.livetvpro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.livetvpro.app.data.local.ThemeManager

@Composable
fun LiveTVProTheme(
    themeManager: ThemeManager,
    content: @Composable () -> Unit,
) {
    val context    = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val useDark = when (themeManager.getThemeMode()) {
        ThemeManager.THEME_DARK  -> true
        ThemeManager.THEME_LIGHT -> false
        else                     -> systemDark
    }

    val colorTheme = themeManager.getColorTheme()
    val amoled     = themeManager.isAmoledMode()

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> when {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content,
    )
}
