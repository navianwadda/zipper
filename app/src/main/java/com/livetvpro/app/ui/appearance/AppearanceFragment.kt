package com.livetvpro.app.ui.appearance

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.theme.AppColorTheme
import com.livetvpro.app.ui.theme.AppThemeContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceFragment : Fragment() {

    @Inject lateinit var themeManager: ThemeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppThemeContent(themeManager) {
                AppearanceScreen(themeManager = themeManager)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(themeManager: ThemeManager) {
    val themeMode  by themeManager.themeModeFlow.collectAsState()
    val colorTheme by themeManager.colorThemeFlow.collectAsState()
    val amoledMode by themeManager.amoledFlow.collectAsState()
    val isDarkMode by themeManager.resolvedIsDarkFlow.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {

            item { PreferenceSectionHeader(title = "Theme") }

            item {
                PreferenceCard {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        MultiChoiceSegmentedButton(
                            choices = listOf("Dark", "Light", "System"),
                            selectedIndices = listOf(when (themeMode) {
                                ThemeManager.THEME_DARK  -> 0
                                ThemeManager.THEME_LIGHT -> 1
                                else                     -> 2
                            }),
                            onClick = { uiIndex ->
                                val mode = when (uiIndex) {
                                    0    -> ThemeManager.THEME_DARK
                                    1    -> ThemeManager.THEME_LIGHT
                                    else -> ThemeManager.THEME_AUTO
                                }
                                if (mode != themeMode) {
                                    themeManager.setThemeMode(mode)
                                }
                            },
                        )
                    }

                    PreferenceDivider()

                    ThemePicker(
                        currentTheme    = colorTheme,
                        isDarkMode      = isDarkMode,
                        onThemeSelected = { chosen ->
                            if (chosen != colorTheme) {
                                themeManager.setColorTheme(chosen)
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    PreferenceDivider()

                    SwitchPreferenceRow(
                        title   = "AMOLED Black Mode",
                        summary = "Use pure black background for dark themes",
                        checked = amoledMode,
                        enabled = isDarkMode,
                        onCheckedChange = { newValue ->
                            themeManager.setAmoledMode(newValue)
                        },
                    )
                }
            }

        item { PreferenceSectionHeader(title = "App Icon") }

        item {
            val context = LocalContext.current
            val blackActive = remember { mutableStateOf(isBlackIconActive(context)) }
            PreferenceCard {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text     = "Choose App Icon",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp, top = 4.dp),
                    )
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIconOption(
                            label      = "Red (Default)",
                            iconColor  = Color(0xFFEF4444),
                            isSelected = !blackActive.value,
                            onClick    = {
                                setAppIcon(context, useBlack = false)
                                blackActive.value = false
                            },
                            modifier   = Modifier.weight(1f),
                        )
                        AppIconOption(
                            label      = "Black",
                            iconColor  = Color(0xFF121212),
                            isSelected = blackActive.value,
                            onClick    = {
                                setAppIcon(context, useBlack = true)
                                blackActive.value = true
                            },
                            modifier   = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = "Restart the app after changing the icon",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
                    )
                }
            }
        }

    }
}


@Composable
fun ThemePicker(
    currentTheme: AppColorTheme,
    isDarkMode: Boolean,
    onThemeSelected: (AppColorTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val themes = AppColorTheme.entries.filter { it != AppColorTheme.Dynamic || isDynamicAvailable }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = themes.indexOf(currentTheme)
        if (index >= 0) listState.animateScrollToItem(maxOf(0, index - 1))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text     = "App Theme",
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )

        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(themes) { theme ->
                ThemePreviewCard(
                    theme      = theme,
                    isSelected = theme == currentTheme,
                    isDarkMode = isDarkMode,
                    onClick    = { onThemeSelected(theme) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}



@Composable
fun AppIconOption(
    label: String,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionColor = MaterialTheme.colorScheme.primary
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation    = if (isSelected) 8.dp else 2.dp,
                    shape        = RoundedCornerShape(16.dp),
                    spotColor    = if (isSelected) selectionColor.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f),
                    ambientColor = if (isSelected) selectionColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(iconColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) selectionColor else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(64.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface,
            textAlign  = TextAlign.Center,
        )
    }
}

private fun isBlackIconActive(context: android.content.Context): Boolean {
    val pm         = context.packageManager
    val blackAlias = android.content.ComponentName(context, "${context.packageName}.BlackIcon")
    return try {
        pm.getComponentEnabledSetting(blackAlias) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (e: Exception) { false }
}

private fun setAppIcon(context: android.content.Context, useBlack: Boolean) {
    val pm         = context.packageManager
    val pkg        = context.packageName
    val redAlias   = android.content.ComponentName(context, "$pkg.RedIcon")
    val blackAlias = android.content.ComponentName(context, "$pkg.BlackIcon")
    val enable     = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val disable    = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val noKill     = android.content.pm.PackageManager.DONT_KILL_APP
    pm.setComponentEnabledSetting(if (useBlack) blackAlias else redAlias,  enable,  noKill)
    pm.setComponentEnabledSetting(if (useBlack) redAlias   else blackAlias, disable, noKill)
}


@Composable
fun ThemePreviewCard(
    theme: AppColorTheme,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme    = if (isDarkMode) theme.getDarkColorScheme() else theme.getLightColorScheme()
    val selectionColor = MaterialTheme.colorScheme.primary
    val borderWidth    = if (isSelected) 3.dp else 1.dp
    val borderColor    = if (isSelected) selectionColor else Color.Transparent
    val elevation      = if (isSelected) 8.dp else 2.dp

    Column(
        modifier            = modifier
            .width(100.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 140.dp)
                .shadow(
                    elevation    = elevation,
                    shape        = RoundedCornerShape(12.dp),
                    ambientColor = if (isSelected) selectionColor.copy(alpha = 0.3f)
                                   else Color.Black.copy(alpha = 0.2f),
                    spotColor    = if (isSelected) selectionColor.copy(alpha = 0.3f)
                                   else Color.Black.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surface)
                .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(12.dp)),
        ) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(if (isSelected) 3.dp else 1.dp)
                    .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp))
                    .background(colorScheme.background)
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    color    = colorScheme.surfaceVariant,
                    shape    = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary),
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colorScheme.tertiary),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorScheme.secondary),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text       = theme.displayName,
            style      = MaterialTheme.typography.bodySmall,
            fontSize   = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface,
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.fillMaxWidth(),
        )
    }
}


@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier  = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(28.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            content()
        }
    }
}


@Composable
fun PreferenceDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}


@Composable
fun PreferenceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    )
}


@Composable
fun MultiChoiceSegmentedButton(
    choices: List<String>,
    selectedIndices: List<Int>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = selectedIndices.contains(index),
                onClick  = { onClick(index) },
                shape    = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
            ) {
                Text(text = choice)
            }
        }
    }
}


@Composable
fun SwitchPreferenceRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text  = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
