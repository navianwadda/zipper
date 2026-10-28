package com.livetvpro.app.ui.player.dialogs

import android.os.Bundle
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.theme.AppThemeContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val windowOptions = listOf(
    "Disable (1 window)",
    "2 windows",
    "3 windows",
    "4 windows",
    "5 windows",
)

private fun maxWindowsToIndex(max: Int): Int = when (max) {
    1    -> 0; 2 -> 1; 3 -> 2; 4 -> 3; 5 -> 4; else -> 0
}

private fun indexToMaxWindows(index: Int): Int = when (index) {
    0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; else -> 1
}

@AndroidEntryPoint
class FloatingPlayerDialog : DialogFragment() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var themeManager: ThemeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppThemeContent(themeManager = themeManager) {
                FloatingPlayerDialogContent(
                    preferencesManager = preferencesManager,
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    companion object {
        const val TAG = "FloatingPlayerDialog"
        fun newInstance() = FloatingPlayerDialog()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
internal fun FloatingPlayerDialogContent(
    preferencesManager: PreferencesManager,
    onDismiss: () -> Unit,
) {
    var isEnabled by remember { mutableStateOf(preferencesManager.isFloatingPlayerEnabled()) }
    var selectedIndex by remember { mutableStateOf(maxWindowsToIndex(preferencesManager.getMaxFloatingWindows())) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .widthIn(max = 560.dp)
            .layout { measurable, constraints ->
            val maxH = if (constraints.maxHeight == androidx.compose.ui.unit.Constraints.Infinity) constraints.maxHeight
                       else (constraints.maxHeight * 0.9f).toInt()
            val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = maxH))
            layout(p.width, p.height) { p.placeRelative(0, 0) }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Floating Player",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enable this option to use directly floating player for playing streams. " +
                           "You can also select multiple floating Players at once.",
                    fontFamily = BergenSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isEnabled = !isEnabled
                            preferencesManager.setFloatingPlayerEnabled(isEnabled)
                        },
                ) {
                    Text(
                        text = "Floating Player",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            preferencesManager.setFloatingPlayerEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

                    val label: @androidx.compose.runtime.Composable (Modifier) -> Unit = { m ->
                        Text(
                            text = "Multi Floating Window",
                            fontFamily = BergenSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.38f),
                            modifier = m,
                        )
                    }
                    val dropdown: @androidx.compose.runtime.Composable (Modifier) -> Unit = { m ->
                        ExposedDropdownMenuBox(
                        expanded = dropdownExpanded && isEnabled,
                        onExpandedChange = { if (isEnabled) dropdownExpanded = !dropdownExpanded },
                        modifier = m,
                    ) {
                        TextField(
                            value = windowOptions[selectedIndex],
                            onValueChange = {},
                            readOnly = true,
                            enabled = isEnabled,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            colors = TextFieldDefaults.colors(
                                disabledContainerColor  = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor   = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = BergenSans,
                                fontSize = 14.sp,
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded && isEnabled,
                            onDismissRequest = { dropdownExpanded = false },
                        ) {
                            windowOptions.forEachIndexed { idx, option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontFamily = BergenSans,
                                            fontSize = 14.sp,
                                            fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        selectedIndex = idx
                                        preferencesManager.setMaxFloatingWindows(indexToMaxWindows(idx))
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    }

                    if (compact) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            label(Modifier.weight(1f))
                            dropdown(Modifier.width(230.dp))
                        }
                    } else {
                        label(Modifier)
                        Spacer(Modifier.height(8.dp))
                        dropdown(Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(if (compact) 48.dp else 52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Close", fontFamily = BergenSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        },
        confirmButton = {},
    )
}
