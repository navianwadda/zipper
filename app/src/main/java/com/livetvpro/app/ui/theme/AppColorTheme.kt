package com.livetvpro.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppColorTheme(
    val displayName: String,
    val isDynamic: Boolean = false,

    val primaryDark:    Color = Color(0xFF2AABEE),
    val secondaryDark:  Color = Color(0xFF1A8AB8),
    val tertiaryDark:   Color = Color(0xFF6FB4E8),
    val backgroundDark: Color = Color(0xFF0D1117),
    val surfaceDark:    Color = Color(0xFF161B22),

    val primaryLight:    Color = Color(0xFF1A8AB8),
    val secondaryLight:  Color = Color(0xFF229ED0),
    val tertiaryLight:   Color = Color(0xFF0D6E9E),
    val backgroundLight: Color = Color(0xFFF0F8FF),
    val surfaceLight:    Color = Color(0xFFFFFFFF),
) {

    Default(
        displayName   = "Default",
        primaryDark   = Color(0xFFE8B5EF), secondaryDark   = Color(0xFFD6C0D6), tertiaryDark   = Color(0xFFF5B7B0),
        backgroundDark = Color(0xFF161217), surfaceDark    = Color(0xFF1F1A1F),
        primaryLight  = Color(0xFF794F81), secondaryLight  = Color(0xFF6A596C), tertiaryLight  = Color(0xFF82524D),
        backgroundLight = Color(0xFFFFF7FB), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Dynamic(
        displayName = "Dynamic",
        isDynamic   = true,

        primaryDark   = Color(0xFFD0BCFF), secondaryDark   = Color(0xFFCCC2DC), tertiaryDark   = Color(0xFFEFB8C8),
        backgroundDark = Color(0xFF1C1B1F), surfaceDark    = Color(0xFF2B2930),
        primaryLight  = Color(0xFF6750A4), secondaryLight  = Color(0xFF625B71), tertiaryLight  = Color(0xFF7D5260),
        backgroundLight = Color(0xFFFFFBFF), surfaceLight   = Color(0xFFFFFFFF),
    ),

    Catppuccin(
        displayName   = "Catppuccin",
        primaryDark   = Color(0xFF9BA8CF), secondaryDark   = Color(0xFFD4A5B8), tertiaryDark   = Color(0xFF8AB8A8),
        backgroundDark = Color(0xFF1E1E2E), surfaceDark    = Color(0xFF24273A),
        primaryLight  = Color(0xFF4C6B9A), secondaryLight  = Color(0xFFB76B8F), tertiaryLight  = Color(0xFF3D7B5F),
        backgroundLight = Color(0xFFEFF1F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Nord(
        displayName   = "Nord",
        primaryDark   = Color(0xFF88C0D0), secondaryDark   = Color(0xFFD8DEE9), tertiaryDark   = Color(0xFFD8A9C4),
        backgroundDark = Color(0xFF2E3440), surfaceDark    = Color(0xFF3B4252),
        primaryLight  = Color(0xFF5E81AC), secondaryLight  = Color(0xFF4C566A), tertiaryLight  = Color(0xFFB48EAD),
        backgroundLight = Color(0xFFECEFF4), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Dracula(
        displayName   = "Dracula",
        primaryDark   = Color(0xFFBD93F9), secondaryDark   = Color(0xFFFF79C6), tertiaryDark   = Color(0xFF8BE9FD),
        backgroundDark = Color(0xFF282A36), surfaceDark    = Color(0xFF363848),
        primaryLight  = Color(0xFF6272A4), secondaryLight  = Color(0xFFFF79C6), tertiaryLight  = Color(0xFF50FA7B),
        backgroundLight = Color(0xFFF8F8F2), surfaceLight  = Color(0xFFFFFFFF),
    ),

    TokyoNight(
        displayName   = "Tokyo Night",
        primaryDark   = Color(0xFF7D9BC1), secondaryDark   = Color(0xFFA89DC9), tertiaryDark   = Color(0xFF8AB4A3),
        backgroundDark = Color(0xFF1A1B26), surfaceDark    = Color(0xFF24283B),
        primaryLight  = Color(0xFF3D5A80), secondaryLight  = Color(0xFF6B5B95), tertiaryLight  = Color(0xFF4A6B5C),
        backgroundLight = Color(0xFFF0F1F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    RosePine(
        displayName   = "Rose Pine",
        primaryDark   = Color(0xFFC4A7E7), secondaryDark   = Color(0xFFEBBCBA), tertiaryDark   = Color(0xFF9CCFD8),
        backgroundDark = Color(0xFF232136), surfaceDark    = Color(0xFF2A2A44),
        primaryLight  = Color(0xFF907AA9), secondaryLight  = Color(0xFFB4637A), tertiaryLight  = Color(0xFF7A9A8A),
        backgroundLight = Color(0xFFFAF4ED), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Kanagawa(
        displayName   = "Kanagawa",
        primaryDark   = Color(0xFF7E9CD8), secondaryDark   = Color(0xFFDCA561), tertiaryDark   = Color(0xFF98BB6C),
        backgroundDark = Color(0xFF1F1F28), surfaceDark    = Color(0xFF2A2A37),
        primaryLight  = Color(0xFF5A7785), secondaryLight  = Color(0xFF8A7A6E), tertiaryLight  = Color(0xFF6A8E7F),
        backgroundLight = Color(0xFFF2ECBC), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Gruvbox(
        displayName   = "Gruvbox",
        primaryDark   = Color(0xFFD89B6A), secondaryDark   = Color(0xFFB0AE8A), tertiaryDark   = Color(0xFF8AAFA8),
        backgroundDark = Color(0xFF282828), surfaceDark    = Color(0xFF3C3836),
        primaryLight  = Color(0xFF9D5B3F), secondaryLight  = Color(0xFF7A7556), tertiaryLight  = Color(0xFF4A7B7C),
        backgroundLight = Color(0xFFFBF1C7), surfaceLight  = Color(0xFFF9F5D7),
    ),

    Lavender(
        displayName   = "Lavender",
        primaryDark   = Color(0xFFCFBCFF), secondaryDark   = Color(0xFFCBC3DA), tertiaryDark   = Color(0xFFF2B8C1),
        backgroundDark = Color(0xFF16121A), surfaceDark    = Color(0xFF211D28),
        primaryLight  = Color(0xFF7C5AB8), secondaryLight  = Color(0xFF635B70), tertiaryLight  = Color(0xFF7E525A),
        backgroundLight = Color(0xFFFCF8FF), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Mocha(
        displayName   = "Mocha",
        primaryDark   = Color(0xFFBCAAA4), secondaryDark   = Color(0xFFA1887F), tertiaryDark   = Color(0xFFD7CCC8),
        backgroundDark = Color(0xFF1A1512), surfaceDark    = Color(0xFF2A2118),
        primaryLight  = Color(0xFF795548), secondaryLight  = Color(0xFF5D4037), tertiaryLight  = Color(0xFF6D4C41),
        backgroundLight = Color(0xFFFFF9F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Midnight(
        displayName   = "Midnight",
        primaryDark   = Color(0xFF90CAF9), secondaryDark   = Color(0xFFB0BEC5), tertiaryDark   = Color(0xFF64B5F6),
        backgroundDark = Color(0xFF0D1117), surfaceDark    = Color(0xFF131920),
        primaryLight  = Color(0xFF0D47A1), secondaryLight  = Color(0xFF455A64), tertiaryLight  = Color(0xFF1565C0),
        backgroundLight = Color(0xFFF5F9FF), surfaceLight  = Color(0xFFFFFFFF),
    ),

    GreenApple(
        displayName   = "Green Apple",
        primaryDark   = Color(0xFF81C784), secondaryDark   = Color(0xFFB0CFB1), tertiaryDark   = Color(0xFF8FD5B7),
        backgroundDark = Color(0xFF0F1A0F), surfaceDark    = Color(0xFF172417),
        primaryLight  = Color(0xFF2E7D32), secondaryLight  = Color(0xFF4A6349), tertiaryLight  = Color(0xFF3D7B5F),
        backgroundLight = Color(0xFFF6FFF6), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Ocean(
        displayName   = "Ocean",
        primaryDark   = Color(0xFF4DD0E1), secondaryDark   = Color(0xFF80DEEA), tertiaryDark   = Color(0xFF26C6DA),
        backgroundDark = Color(0xFF0A1A1C), surfaceDark    = Color(0xFF112224),
        primaryLight  = Color(0xFF006064), secondaryLight  = Color(0xFF00838F), tertiaryLight  = Color(0xFF0097A7),
        backgroundLight = Color(0xFFF0FFFF), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Sapphire(
        displayName   = "Sapphire",
        primaryDark   = Color(0xFF64B5F6), secondaryDark   = Color(0xFF9FA8DA), tertiaryDark   = Color(0xFF4FC3F7),
        backgroundDark = Color(0xFF0D1620), surfaceDark    = Color(0xFF121D2A),
        primaryLight  = Color(0xFF1E88E5), secondaryLight  = Color(0xFF5C6BC0), tertiaryLight  = Color(0xFF0288D1),
        backgroundLight = Color(0xFFF3F8FF), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Sunset(
        displayName   = "Sunset",
        primaryDark   = Color(0xFFFF9E80), secondaryDark   = Color(0xFFFFCC80), tertiaryDark   = Color(0xFFFF8A65),
        backgroundDark = Color(0xFF1A120D), surfaceDark    = Color(0xFF261A11),
        primaryLight  = Color(0xFFE65100), secondaryLight  = Color(0xFFEF6C00), tertiaryLight  = Color(0xFFF4511E),
        backgroundLight = Color(0xFFFFF5F0), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Strawberry(
        displayName   = "Strawberry",
        primaryDark   = Color(0xFFF48FB1), secondaryDark   = Color(0xFFD6B0C1), tertiaryDark   = Color(0xFFF8BBD9),
        backgroundDark = Color(0xFF1A1015), surfaceDark    = Color(0xFF25151F),
        primaryLight  = Color(0xFFD81B60), secondaryLight  = Color(0xFF6B4958), tertiaryLight  = Color(0xFFC2185B),
        backgroundLight = Color(0xFFFFF5F8), surfaceLight  = Color(0xFFFFFFFF),
    ),

    RoseGold(
        displayName   = "Rose Gold",
        primaryDark   = Color(0xFFE8A9B0), secondaryDark   = Color(0xFFDDBFB8), tertiaryDark   = Color(0xFFF5D5D5),
        backgroundDark = Color(0xFF1A1315), surfaceDark    = Color(0xFF251820),
        primaryLight  = Color(0xFFB76E79), secondaryLight  = Color(0xFFAD8075), tertiaryLight  = Color(0xFFD4A5A5),
        backgroundLight = Color(0xFFFFF5F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Violet(
        displayName   = "Violet",
        primaryDark   = Color(0xFFCE93D8), secondaryDark   = Color(0xFFE1BEE7), tertiaryDark   = Color(0xFFBA68C8),
        backgroundDark = Color(0xFF150D1A), surfaceDark    = Color(0xFF1E1226),
        primaryLight  = Color(0xFF6A1B9A), secondaryLight  = Color(0xFF7B1FA2), tertiaryLight  = Color(0xFF8E24AA),
        backgroundLight = Color(0xFFFCF5FF), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Amber(
        displayName   = "Amber",
        primaryDark   = Color(0xFFFFCA28), secondaryDark   = Color(0xFFFFD54F), tertiaryDark   = Color(0xFFFFE082),
        backgroundDark = Color(0xFF1A1508), surfaceDark    = Color(0xFF262009),
        primaryLight  = Color(0xFFFF8F00), secondaryLight  = Color(0xFFFFA000), tertiaryLight  = Color(0xFFFFB300),
        backgroundLight = Color(0xFFFFFBF0), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Doom(
        displayName   = "Doom",
        primaryDark   = Color(0xFFFF6B6B), secondaryDark   = Color(0xFFD6BABA), tertiaryDark   = Color(0xFFFFB4AB),
        backgroundDark = Color(0xFF1A1010), surfaceDark    = Color(0xFF261515),
        primaryLight  = Color(0xFFBB2929), secondaryLight  = Color(0xFF6B5353), tertiaryLight  = Color(0xFF8C4A4A),
        backgroundLight = Color(0xFFFFF8F7), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Forest(
        displayName   = "Forest",
        primaryDark   = Color(0xFF66BB6A), secondaryDark   = Color(0xFF9CCC65), tertiaryDark   = Color(0xFFA5D6A7),
        backgroundDark = Color(0xFF0D1A0D), surfaceDark    = Color(0xFF122212),
        primaryLight  = Color(0xFF1B5E20), secondaryLight  = Color(0xFF33691E), tertiaryLight  = Color(0xFF2E7D32),
        backgroundLight = Color(0xFFF1F8E9), surfaceLight  = Color(0xFFFFFFFF),
    ),

    CottonCandy(
        displayName   = "Cotton Candy",
        primaryDark   = Color(0xFFFFB1D5), secondaryDark   = Color(0xFF9ED0EF), tertiaryDark   = Color(0xFFDEB0E9),
        backgroundDark = Color(0xFF1A1418), surfaceDark    = Color(0xFF251921),
        primaryLight  = Color(0xFFE993C1), secondaryLight  = Color(0xFF70A2C2), tertiaryLight  = Color(0xFF9C68AC),
        backgroundLight = Color(0xFFFFF8FA), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Coral(
        displayName   = "Coral",
        primaryDark   = Color(0xFFFF8A80), secondaryDark   = Color(0xFFFFAB91), tertiaryDark   = Color(0xFFFFCCBC),
        backgroundDark = Color(0xFF1A1010), surfaceDark    = Color(0xFF261515),
        primaryLight  = Color(0xFFFF5252), secondaryLight  = Color(0xFFFF6E40), tertiaryLight  = Color(0xFFFF7043),
        backgroundLight = Color(0xFFFFF5F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Slate(
        displayName   = "Slate",
        primaryDark   = Color(0xFF90A4AE), secondaryDark   = Color(0xFFB0BEC5), tertiaryDark   = Color(0xFFCFD8DC),
        backgroundDark = Color(0xFF151A1C), surfaceDark    = Color(0xFF1E2527),
        primaryLight  = Color(0xFF455A64), secondaryLight  = Color(0xFF546E7A), tertiaryLight  = Color(0xFF607D8B),
        backgroundLight = Color(0xFFF5F7F8), surfaceLight  = Color(0xFFFFFFFF),
    ),

    YinYang(
        displayName   = "Yin Yang",
        primaryDark   = Color(0xFFBDBDBD), secondaryDark   = Color(0xFFE0E0E0), tertiaryDark   = Color(0xFFEEEEEE),
        backgroundDark = Color(0xFF121212), surfaceDark    = Color(0xFF1E1E1E),
        primaryLight  = Color(0xFF424242), secondaryLight  = Color(0xFF616161), tertiaryLight  = Color(0xFF757575),
        backgroundLight = Color(0xFFFAFAFA), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Monochrome(
        displayName   = "Monochrome",
        primaryDark   = Color(0xFFE0E0E0), secondaryDark   = Color(0xFFBDBDBD), tertiaryDark   = Color(0xFF9E9E9E),
        backgroundDark = Color(0xFF0A0A0A), surfaceDark    = Color(0xFF141414),
        primaryLight  = Color(0xFF212121), secondaryLight  = Color(0xFF424242), tertiaryLight  = Color(0xFF616161),
        backgroundLight = Color(0xFFFFFFFF), surfaceLight  = Color(0xFFF5F5F5),
    ),

    Cloudflare(
        displayName   = "Cloudflare",
        primaryDark   = Color(0xFFFFB77C), secondaryDark   = Color(0xFFD6C5AC), tertiaryDark   = Color(0xFFFABD71),
        backgroundDark = Color(0xFF1A1612), surfaceDark    = Color(0xFF261E15),
        primaryLight  = Color(0xFFF6821F), secondaryLight  = Color(0xFF6B5E4C), tertiaryLight  = Color(0xFF855316),
        backgroundLight = Color(0xFFFFFBF7), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Tidal(
        displayName   = "Tidal",
        primaryDark   = Color(0xFF80CBC4), secondaryDark   = Color(0xFFB0CFC9), tertiaryDark   = Color(0xFF4DB6AC),
        backgroundDark = Color(0xFF0F1A18), surfaceDark    = Color(0xFF142420),
        primaryLight  = Color(0xFF00796B), secondaryLight  = Color(0xFF4A635E), tertiaryLight  = Color(0xFF00897B),
        backgroundLight = Color(0xFFF2FFFD), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Yotsuba(
        displayName   = "Yotsuba",
        primaryDark   = Color(0xFFFFAB91), secondaryDark   = Color(0xFFD6C4C2), tertiaryDark   = Color(0xFFFFCCBC),
        backgroundDark = Color(0xFF1A1412), surfaceDark    = Color(0xFF261A15),
        primaryLight  = Color(0xFFFF8A65), secondaryLight  = Color(0xFF6D5D5B), tertiaryLight  = Color(0xFFFF7043),
        backgroundLight = Color(0xFFFFF8F5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    TakoGreen(
        displayName   = "Tako Green",
        primaryDark   = Color(0xFFA5D6A7), secondaryDark   = Color(0xFF90A4AE), tertiaryDark   = Color(0xFF81C784),
        backgroundDark = Color(0xFF121A12), surfaceDark    = Color(0xFF192419),
        primaryLight  = Color(0xFF66BB6A), secondaryLight  = Color(0xFF546E7A), tertiaryLight  = Color(0xFF43A047),
        backgroundLight = Color(0xFFF5FFF5), surfaceLight  = Color(0xFFFFFFFF),
    ),

    Telegram(
        displayName   = "Telegram",
        primaryDark   = Color(0xFF2AABEE), secondaryDark   = Color(0xFF229ED0), tertiaryDark   = Color(0xFF1A7AB8),
        backgroundDark = Color(0xFF17212B), surfaceDark    = Color(0xFF1F2C38),
        primaryLight  = Color(0xFF2AABEE), secondaryLight  = Color(0xFF229ED0), tertiaryLight  = Color(0xFF1A7AB8),
        backgroundLight = Color(0xFFE6EBF0), surfaceLight  = Color(0xFFFFFFFF),
    );

    companion object {
        fun fromName(name: String): AppColorTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
