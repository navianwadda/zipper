package com.livetvpro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun AppColorTheme.toDarkColorScheme(amoled: Boolean) = darkColorScheme(
    primary              = primaryDark,
    onPrimary            = Color.Black,
    primaryContainer     = primaryDark.copy(alpha = 0.22f),
    onPrimaryContainer   = primaryDark,
    secondary            = secondaryDark,
    onSecondary          = Color.Black,
    secondaryContainer   = secondaryDark.copy(alpha = 0.22f),
    onSecondaryContainer = secondaryDark,
    tertiary             = tertiaryDark,
    onTertiary           = Color.Black,
    tertiaryContainer    = tertiaryDark.copy(alpha = 0.22f),
    onTertiaryContainer  = tertiaryDark,

    background           = if (amoled) Color.Black            else backgroundDark,
    onBackground         = Color(0xFFE6E1E5),
    surface              = if (amoled) Color(0xFF0A0A0A)       else surfaceDark,
    onSurface            = Color(0xFFE6E1E5),
    surfaceVariant       = primaryDark.copy(alpha = 0.10f),
    onSurfaceVariant     = Color(0xFFCAC4D0),
    surfaceContainerLowest  = if (amoled) Color.Black          else backgroundDark,
    surfaceContainerLow     = if (amoled) Color(0xFF050505)    else surfaceDark,
    surfaceContainer        = primaryDark.copy(alpha = 0.06f),
    surfaceContainerHigh    = primaryDark.copy(alpha = 0.09f),
    surfaceContainerHighest = primaryDark.copy(alpha = 0.12f),
    outline              = secondaryDark.copy(alpha = 0.40f),
    outlineVariant       = primaryDark.copy(alpha = 0.20f),
    inverseSurface       = backgroundLight,
    inverseOnSurface     = Color(0xFF313033),
    inversePrimary       = primaryLight,
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),
)

private fun AppColorTheme.toLightColorScheme() = lightColorScheme(
    primary              = primaryLight,
    onPrimary            = Color.White,
    primaryContainer     = primaryLight.copy(alpha = 0.15f),
    onPrimaryContainer   = primaryLight,
    secondary            = secondaryLight,
    onSecondary          = Color.White,
    secondaryContainer   = secondaryLight.copy(alpha = 0.15f),
    onSecondaryContainer = secondaryLight,
    tertiary             = tertiaryLight,
    onTertiary           = Color.White,
    tertiaryContainer    = tertiaryLight.copy(alpha = 0.15f),
    onTertiaryContainer  = tertiaryLight,
    background           = backgroundLight,
    onBackground         = Color(0xFF1C1B1F),
    surface              = surfaceLight,
    onSurface            = Color(0xFF1C1B1F),
    surfaceVariant       = primaryLight.copy(alpha = 0.08f),
    onSurfaceVariant     = Color(0xFF49454F),
    surfaceContainerLowest  = backgroundLight,
    surfaceContainerLow     = surfaceLight,
    surfaceContainer        = primaryLight.copy(alpha = 0.05f),
    surfaceContainerHigh    = primaryLight.copy(alpha = 0.08f),
    surfaceContainerHighest = primaryLight.copy(alpha = 0.11f),
    outline              = secondaryLight.copy(alpha = 0.50f),
    outlineVariant       = primaryLight.copy(alpha = 0.22f),
    inverseSurface       = backgroundDark,
    inverseOnSurface     = Color(0xFFF4EFF4),
    inversePrimary       = primaryDark,
    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF93000A),
)

@Composable
fun AppTheme(
    colorTheme: AppColorTheme = AppColorTheme.Dynamic,
    darkMode: Int = 0,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        1    -> false
        2    -> true
        else -> systemDark
    }

    val context = LocalContext.current

    val colorScheme = when {

        colorTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when {
                useDark && amoledMode -> {

                    dynamicDarkColorScheme(context).copy(
                        background              = Color.Black,
                        surface                 = Color(0xFF0A0A0A),
                        surfaceContainerLowest  = Color.Black,
                        surfaceContainerLow     = Color(0xFF050505),
                        surfaceContainer        = Color(0xFF0F0F0F),
                        surfaceContainerHigh    = Color(0xFF141414),
                        surfaceContainerHighest = Color(0xFF1A1A1A),
                    )
                }
                useDark -> dynamicDarkColorScheme(context)
                else    -> dynamicLightColorScheme(context)
            }
        }

        colorTheme.isDynamic -> {
            if (useDark) colorTheme.toDarkColorScheme(amoledMode)
            else         colorTheme.toLightColorScheme()
        }

        useDark -> colorTheme.toDarkColorScheme(amoledMode)
        else    -> colorTheme.toLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
