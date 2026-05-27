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

/**
 * The single Compose theme wrapper for the entire app.
 *
 * Reads ThemeManager for dark-mode mode, color theme, and AMOLED flag.
 * Every ComposeView / setContent block should be wrapped in this.
 *
 * Because ThemeManager reads SharedPreferences synchronously there is
 * no loading delay — the correct colours are applied on the very first
 * composition frame.
 */
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
        else                     -> systemDark   // THEME_AUTO → follow system
    }

    val colorTheme = themeManager.getColorTheme()
    val amoled     = themeManager.isAmoledMode()

    val colorScheme = when {
        // Dynamic colour on Android 12+
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
