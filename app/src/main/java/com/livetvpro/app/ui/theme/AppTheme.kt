package com.livetvpro.app.ui.theme

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.livetvpro.app.data.local.ThemeManager

@Composable
fun LiveTVProTheme(
    themeManager: ThemeManager,
    surfaceColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context          = LocalContext.current
    val activity         = LocalActivity.current
    val colorTheme       by themeManager.colorThemeFlow.collectAsState()
    val amoled           by themeManager.amoledFlow.collectAsState()
    val forcedDark       by themeManager.isDarkFlow.collectAsState(initial = null)
    val isSystemDarkTheme = isSystemInDarkTheme()

    val isDark = forcedDark ?: isSystemDarkTheme

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
            else   -> dynamicLightColorScheme(context)
        }
        isDark && amoled -> colorTheme.getAmoledColorScheme()
        isDark           -> colorTheme.getDarkColorScheme()
        else             -> colorTheme.getLightColorScheme()
    }

    LaunchedEffect(isDark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.WHITE,
                darkScrim  = android.graphics.Color.TRANSPARENT,
            ) { isDark },
        )
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

@Composable
fun AppThemeContent(
    themeManager: ThemeManager,
    content: @Composable () -> Unit,
) {
    val context           = LocalContext.current
    val colorTheme        by themeManager.colorThemeFlow.collectAsState()
    val amoled            by themeManager.amoledFlow.collectAsState()
    val forcedDark        by themeManager.isDarkFlow.collectAsState(initial = null)
    val isSystemDarkTheme  = isSystemInDarkTheme()

    val isDark = forcedDark ?: isSystemDarkTheme

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
            else   -> dynamicLightColorScheme(context)
        }
        isDark && amoled -> colorTheme.getAmoledColorScheme()
        isDark           -> colorTheme.getDarkColorScheme()
        else             -> colorTheme.getLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content,
    )
}
