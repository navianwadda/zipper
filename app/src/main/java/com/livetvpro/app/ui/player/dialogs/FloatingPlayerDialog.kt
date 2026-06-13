package com.livetvpro.app.ui.player.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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
private val ColorAccent = Color(0xFFE53935)

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

    AlertDialog(
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enable this option to use directly floating player for playing streams. " +
                           "You can also select multiple floating Players at once.",
                    fontFamily = BergenSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
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
                            checkedThumbColor   = Color.White,
                            checkedTrackColor   = ColorAccent,
                            uncheckedThumbColor = Color.White,
                        ),
                    )
                }

                if (isEnabled) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Multi Floating Window",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextField(
                            value = windowOptions[selectedIndex],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor   = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor   = ColorAccent,
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
                            expanded = dropdownExpanded,
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Medium,
                    color = ColorAccent,
                )
            }
        },
    )
}
