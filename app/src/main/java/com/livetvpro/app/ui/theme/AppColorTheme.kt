package com.livetvpro.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Color themes for Live TV Pro.
 * Each theme generates proper Material 3 light / dark / AMOLED color schemes.
 */
enum class AppColorTheme(
    val displayName: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val tertiaryLight: Color,
    val tertiaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color,
) {
    Default(
        displayName     = "Default",
        primaryLight    = Color(0xFF794F81),
        primaryDark     = Color(0xFFE8B5EF),
        secondaryLight  = Color(0xFF6A596C),
        secondaryDark   = Color(0xFFD6C0D6),
        tertiaryLight   = Color(0xFF82524D),
        tertiaryDark    = Color(0xFFF5B7B0),
        backgroundLight = Color(0xFFFFF7FB),
        backgroundDark  = Color(0xFF161217),
    ),
    Catppuccin(
        displayName     = "Catppuccin",
        primaryLight    = Color(0xFF4C6B9A),
        primaryDark     = Color(0xFF9BA8CF),
        secondaryLight  = Color(0xFFB76B8F),
        secondaryDark   = Color(0xFFD4A5B8),
        tertiaryLight   = Color(0xFFB8763E),
        tertiaryDark    = Color(0xFF8AB8A8),
        backgroundLight = Color(0xFFEFF1F5),
        backgroundDark  = Color(0xFF1E1E2E),
    ),
    Cloudflare(
        displayName     = "Cloudflare",
        primaryLight    = Color(0xFFF6821F),
        primaryDark     = Color(0xFFFFB77C),
        secondaryLight  = Color(0xFF6B5E4C),
        secondaryDark   = Color(0xFFD6C5AC),
        tertiaryLight   = Color(0xFF855316),
        tertiaryDark    = Color(0xFFFABD71),
        backgroundLight = Color(0xFFFFFBF7),
        backgroundDark  = Color(0xFF1A1612),
    ),
    CottonCandy(
        displayName     = "Cotton Candy",
        primaryLight    = Color(0xFFE993C1),
        primaryDark     = Color(0xFFFFB1D5),
        secondaryLight  = Color(0xFF70A2C2),
        secondaryDark   = Color(0xFF9ED0EF),
        tertiaryLight   = Color(0xFF9C68AC),
        tertiaryDark    = Color(0xFFDEB0E9),
        backgroundLight = Color(0xFFFFF8FA),
        backgroundDark  = Color(0xFF1A1418),
    ),
    Doom(
        displayName     = "Doom",
        primaryLight    = Color(0xFFBB2929),
        primaryDark     = Color(0xFFFF6B6B),
        secondaryLight  = Color(0xFF6B5353),
        secondaryDark   = Color(0xFFD6BABA),
        tertiaryLight   = Color(0xFF8C4A4A),
        tertiaryDark    = Color(0xFFFFB4AB),
        backgroundLight = Color(0xFFFFF8F7),
        backgroundDark  = Color(0xFF1A1010),
    ),
    GreenApple(
        displayName     = "Green Apple",
        primaryLight    = Color(0xFF2E7D32),
        primaryDark     = Color(0xFF81C784),
        secondaryLight  = Color(0xFF4A6349),
        secondaryDark   = Color(0xFFB0CFB1),
        tertiaryLight   = Color(0xFF3D7B5F),
        tertiaryDark    = Color(0xFF8FD5B7),
        backgroundLight = Color(0xFFF6FFF6),
        backgroundDark  = Color(0xFF0F1A0F),
    ),
    Gruvbox(
        displayName     = "Gruvbox",
        primaryLight    = Color(0xFF9D5B3F),
        primaryDark     = Color(0xFFD89B6A),
        secondaryLight  = Color(0xFF7A7556),
        secondaryDark   = Color(0xFFB0AE8A),
        tertiaryLight   = Color(0xFF4A7B7C),
        tertiaryDark    = Color(0xFF8AAFA8),
        backgroundLight = Color(0xFFFBF1C7),
        backgroundDark  = Color(0xFF282828),
    ),
    Kanagawa(
        displayName     = "Kanagawa",
        primaryLight    = Color(0xFF5A7785),
        primaryDark     = Color(0xFF7E9CD8),
        secondaryLight  = Color(0xFF8A7A6E),
        secondaryDark   = Color(0xFFDCA561),
        tertiaryLight   = Color(0xFF6A8E7F),
        tertiaryDark    = Color(0xFF98BB6C),
        backgroundLight = Color(0xFFF2ECBC),
        backgroundDark  = Color(0xFF1F1F28),
    ),
    Lavender(
        displayName     = "Lavender",
        primaryLight    = Color(0xFF7C5AB8),
        primaryDark     = Color(0xFFCFBCFF),
        secondaryLight  = Color(0xFF635B70),
        secondaryDark   = Color(0xFFCBC3DA),
        tertiaryLight   = Color(0xFF7E525A),
        tertiaryDark    = Color(0xFFF2B8C1),
        backgroundLight = Color(0xFFFCF8FF),
        backgroundDark  = Color(0xFF16121A),
    ),
    Midnight(
        displayName     = "Midnight",
        primaryLight    = Color(0xFF0D47A1),
        primaryDark     = Color(0xFF90CAF9),
        secondaryLight  = Color(0xFF455A64),
        secondaryDark   = Color(0xFFB0BEC5),
        tertiaryLight   = Color(0xFF1565C0),
        tertiaryDark    = Color(0xFF64B5F6),
        backgroundLight = Color(0xFFF5F9FF),
        backgroundDark  = Color(0xFF0D1117),
    ),
    Mocha(
        displayName     = "Mocha",
        primaryLight    = Color(0xFF795548),
        primaryDark     = Color(0xFFBCAAA4),
        secondaryLight  = Color(0xFF5D4037),
        secondaryDark   = Color(0xFFA1887F),
        tertiaryLight   = Color(0xFF6D4C41),
        tertiaryDark    = Color(0xFFD7CCC8),
        backgroundLight = Color(0xFFFFF9F5),
        backgroundDark  = Color(0xFF1A1512),
    ),
    Nord(
        displayName     = "Nord",
        primaryLight    = Color(0xFF5E81AC),
        primaryDark     = Color(0xFF88C0D0),
        secondaryLight  = Color(0xFF4C566A),
        secondaryDark   = Color(0xFFD8DEE9),
        tertiaryLight   = Color(0xFFB48EAD),
        tertiaryDark    = Color(0xFFD8A9C4),
        backgroundLight = Color(0xFFECEFF4),
        backgroundDark  = Color(0xFF2E3440),
    ),
    RosePine(
        displayName     = "Rosé Pine",
        primaryLight    = Color(0xFF907AA9),
        primaryDark     = Color(0xFFC4A7E7),
        secondaryLight  = Color(0xFFB4637A),
        secondaryDark   = Color(0xFFEBBCBA),
        tertiaryLight   = Color(0xFF7A9A8A),
        tertiaryDark    = Color(0xFF9CCFD8),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark  = Color(0xFF232136),
    ),
    Strawberry(
        displayName     = "Strawberry",
        primaryLight    = Color(0xFFD81B60),
        primaryDark     = Color(0xFFF48FB1),
        secondaryLight  = Color(0xFF6B4958),
        secondaryDark   = Color(0xFFD6B0C1),
        tertiaryLight   = Color(0xFFC2185B),
        tertiaryDark    = Color(0xFFF8BBD9),
        backgroundLight = Color(0xFFFFF5F8),
        backgroundDark  = Color(0xFF1A1015),
    ),
    Tidal(
        displayName     = "Tidal",
        primaryLight    = Color(0xFF00796B),
        primaryDark     = Color(0xFF80CBC4),
        secondaryLight  = Color(0xFF4A635E),
        secondaryDark   = Color(0xFFB0CFC9),
        tertiaryLight   = Color(0xFF00897B),
        tertiaryDark    = Color(0xFF4DB6AC),
        backgroundLight = Color(0xFFF2FFFD),
        backgroundDark  = Color(0xFF0F1A18),
    ),
    TokyoNight(
        displayName     = "Tokyo Night",
        primaryLight    = Color(0xFF3D5A80),
        primaryDark     = Color(0xFF7D9BC1),
        secondaryLight  = Color(0xFF6B5B95),
        secondaryDark   = Color(0xFFA89DC9),
        tertiaryLight   = Color(0xFF4A6B5C),
        tertiaryDark    = Color(0xFF8AB4A3),
        backgroundLight = Color(0xFFF0F1F5),
        backgroundDark  = Color(0xFF1A1B26),
    ),
    Dracula(
        displayName     = "Dracula",
        primaryLight    = Color(0xFF6272A4),
        primaryDark     = Color(0xFFBD93F9),
        secondaryLight  = Color(0xFF44475A),
        secondaryDark   = Color(0xFFFF79C6),
        tertiaryLight   = Color(0xFF50FA7B),
        tertiaryDark    = Color(0xFF8BE9FD),
        backgroundLight = Color(0xFFF8F8F2),
        backgroundDark  = Color(0xFF282A36),
    ),
    Amber(
        displayName     = "Amber",
        primaryLight    = Color(0xFFFF8F00),
        primaryDark     = Color(0xFFFFCA28),
        secondaryLight  = Color(0xFFFFA000),
        secondaryDark   = Color(0xFFFFD54F),
        tertiaryLight   = Color(0xFFFFB300),
        tertiaryDark    = Color(0xFFFFE082),
        backgroundLight = Color(0xFFFFFBF0),
        backgroundDark  = Color(0xFF1A1508),
    ),
    Ocean(
        displayName     = "Ocean",
        primaryLight    = Color(0xFF006064),
        primaryDark     = Color(0xFF4DD0E1),
        secondaryLight  = Color(0xFF00838F),
        secondaryDark   = Color(0xFF80DEEA),
        tertiaryLight   = Color(0xFF0097A7),
        tertiaryDark    = Color(0xFF26C6DA),
        backgroundLight = Color(0xFFF0FFFF),
        backgroundDark  = Color(0xFF0A1A1C),
    ),
    Sunset(
        displayName     = "Sunset",
        primaryLight    = Color(0xFFE65100),
        primaryDark     = Color(0xFFFF9E80),
        secondaryLight  = Color(0xFFEF6C00),
        secondaryDark   = Color(0xFFFFCC80),
        tertiaryLight   = Color(0xFFF4511E),
        tertiaryDark    = Color(0xFFFF8A65),
        backgroundLight = Color(0xFFFFF5F0),
        backgroundDark  = Color(0xFF1A120D),
    ),
    Violet(
        displayName     = "Violet",
        primaryLight    = Color(0xFF6A1B9A),
        primaryDark     = Color(0xFFCE93D8),
        secondaryLight  = Color(0xFF7B1FA2),
        secondaryDark   = Color(0xFFE1BEE7),
        tertiaryLight   = Color(0xFF8E24AA),
        tertiaryDark    = Color(0xFFBA68C8),
        backgroundLight = Color(0xFFFCF5FF),
        backgroundDark  = Color(0xFF150D1A),
    ),
    YinYang(
        displayName     = "Yin Yang",
        primaryLight    = Color(0xFF424242),
        primaryDark     = Color(0xFFBDBDBD),
        secondaryLight  = Color(0xFF616161),
        secondaryDark   = Color(0xFFE0E0E0),
        tertiaryLight   = Color(0xFF757575),
        tertiaryDark    = Color(0xFFEEEEEE),
        backgroundLight = Color(0xFFFAFAFA),
        backgroundDark  = Color(0xFF121212),
    );

    fun getLightColorScheme(): ColorScheme {
        val bg = backgroundLight
        return lightColorScheme(
            primary                  = primaryLight,
            onPrimary                = Color.White,
            primaryContainer         = primaryLight.copy(alpha = 0.15f).over(Color.White),
            onPrimaryContainer       = primaryLight.darken(0.3f),
            secondary                = secondaryLight,
            onSecondary              = Color.White,
            secondaryContainer       = secondaryLight.copy(alpha = 0.15f).over(Color.White),
            onSecondaryContainer     = secondaryLight.darken(0.3f),
            tertiary                 = tertiaryLight,
            onTertiary               = Color.White,
            tertiaryContainer        = tertiaryLight.copy(alpha = 0.15f).over(Color.White),
            onTertiaryContainer      = tertiaryLight.darken(0.3f),
            background               = bg,
            onBackground             = Color(0xFF1C1B1F),
            surface                  = bg,
            onSurface                = Color(0xFF1C1B1F),
            surfaceVariant           = primaryLight.copy(alpha = 0.08f).over(Color(0xFFF0F0F0)),
            onSurfaceVariant         = Color(0xFF49454F),
            outline                  = Color(0xFF79747E),
            outlineVariant           = Color(0xFFCAC4D0),
            inverseSurface           = backgroundDark,
            inverseOnSurface         = Color(0xFFF4EFF4),
            inversePrimary           = primaryDark,
            surfaceContainerLowest   = bg,
            surfaceContainerLow      = primaryLight.copy(alpha = 0.03f).over(bg),
            surfaceContainer         = primaryLight.copy(alpha = 0.06f).over(bg),
            surfaceContainerHigh     = primaryLight.copy(alpha = 0.08f).over(bg),
            surfaceContainerHighest  = primaryLight.copy(alpha = 0.11f).over(bg),
        )
    }

    fun getDarkColorScheme(): ColorScheme {
        val bg = backgroundDark
        return darkColorScheme(
            primary                  = primaryDark,
            onPrimary                = primaryLight.darken(0.5f),
            primaryContainer         = primaryLight.darken(0.3f),
            onPrimaryContainer       = primaryDark.lighten(0.1f),
            secondary                = secondaryDark,
            onSecondary              = secondaryLight.darken(0.5f),
            secondaryContainer       = secondaryLight.darken(0.3f),
            onSecondaryContainer     = secondaryDark.lighten(0.1f),
            tertiary                 = tertiaryDark,
            onTertiary               = tertiaryLight.darken(0.5f),
            tertiaryContainer        = tertiaryLight.darken(0.3f),
            onTertiaryContainer      = tertiaryDark.lighten(0.1f),
            background               = bg,
            onBackground             = Color(0xFFE6E1E5),
            surface                  = bg,
            onSurface                = Color(0xFFE6E1E5),
            surfaceVariant           = primaryDark.copy(alpha = 0.12f).over(Color(0xFF2A2A2A)),
            onSurfaceVariant         = Color(0xFFCAC4D0),
            outline                  = Color(0xFF938F99),
            outlineVariant           = Color(0xFF49454F),
            inverseSurface           = backgroundLight,
            inverseOnSurface         = Color(0xFF313033),
            inversePrimary           = primaryLight,
            surfaceContainerLowest   = bg.darken(0.2f),
            surfaceContainerLow      = primaryDark.copy(alpha = 0.03f).over(bg),
            surfaceContainer         = primaryDark.copy(alpha = 0.05f).over(bg),
            surfaceContainerHigh     = primaryDark.copy(alpha = 0.08f).over(bg),
            surfaceContainerHighest  = primaryDark.copy(alpha = 0.11f).over(bg),
        )
    }

    fun getAmoledColorScheme(): ColorScheme = getDarkColorScheme().copy(
        background              = Color.Black,
        surface                 = Color.Black,
        surfaceContainerLowest  = Color.Black,
        surfaceContainerLow     = Color(0xFF050505),
        surfaceContainer        = Color(0xFF0D0D0D),
        surfaceContainerHigh    = primaryDark.copy(alpha = 0.05f).over(Color(0xFF151515)),
        surfaceContainerHighest = primaryDark.copy(alpha = 0.08f).over(Color(0xFF1F1F1F)),
    )

    companion object {
        fun fromName(name: String): AppColorTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

// ── Colour helpers ────────────────────────────────────────────────────────────

private fun Color.darken(factor: Float) = Color(
    red   = (red   * (1 - factor)).coerceIn(0f, 1f),
    green = (green * (1 - factor)).coerceIn(0f, 1f),
    blue  = (blue  * (1 - factor)).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.lighten(factor: Float) = Color(
    red   = (red   + (1 - red)   * factor).coerceIn(0f, 1f),
    green = (green + (1 - green) * factor).coerceIn(0f, 1f),
    blue  = (blue  + (1 - blue)  * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

/** Alpha-composite this colour over [background]. */
private fun Color.over(background: Color): Color {
    val fgA = alpha
    val bgA = background.alpha
    val a   = fgA + bgA * (1f - fgA)
    return if (a == 0f) Color.Transparent
    else Color(
        red   = (red   * fgA + background.red   * bgA * (1f - fgA)) / a,
        green = (green * fgA + background.green * bgA * (1f - fgA)) / a,
        blue  = (blue  * fgA + background.blue  * bgA * (1f - fgA)) / a,
        alpha = a,
    )
}
