package com.livetvpro.app.ui.networkstream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.livetvpro.app.R

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

@Composable
fun NetworkStreamScreen(
    viewModel: NetworkStreamViewModel = hiltViewModel(),
    onPlay: (streamUrl: String, cookie: String, referer: String, origin: String, drmLicense: String, userAgent: String, drmScheme: String) -> Unit
) {
    val primaryColorInt by viewModel.primaryColorFlow.collectAsState()
    val primaryColor = Color(primaryColorInt)

    val userAgentOptions = listOf("Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)", "iPhone", "Nokia", "Custom")
    val drmSchemeOptions = listOf("clearkey", "widevine", "playready")

    var streamUrl by remember { mutableStateOf(viewModel.streamUrl) }
    var cookie by remember { mutableStateOf(viewModel.cookie) }
    var referer by remember { mutableStateOf(viewModel.referer) }
    var origin by remember { mutableStateOf(viewModel.origin) }
    var drmLicense by remember { mutableStateOf(viewModel.drmLicense) }
    var customUserAgent by remember { mutableStateOf(viewModel.customUserAgent) }
    var selectedUserAgent by remember { mutableStateOf(viewModel.selectedUserAgent) }
    var selectedDrmScheme by remember { mutableStateOf(viewModel.selectedDrmScheme) }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val selectionColors = TextSelectionColors(
        handleColor = primaryColor,
        backgroundColor = primaryColor.copy(alpha = 0.3f)
    )

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayFocused by playInteractionSource.collectIsFocusedAsState()

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box(modifier = Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = navBarPadding + 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StreamTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it; viewModel.streamUrl = it },
                    label = stringResource(R.string.media_stream_url),
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
                StreamTextField(
                    value = cookie,
                    onValueChange = { cookie = it; viewModel.cookie = it },
                    label = stringResource(R.string.cookie_value),
                    primaryColor = primaryColor
                )
                StreamTextField(
                    value = referer,
                    onValueChange = { referer = it; viewModel.referer = it },
                    label = stringResource(R.string.referer_value),
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
                StreamTextField(
                    value = origin,
                    onValueChange = { origin = it; viewModel.origin = it },
                    label = stringResource(R.string.origin_value),
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
                StreamTextField(
                    value = drmLicense,
                    onValueChange = { drmLicense = it; viewModel.drmLicense = it },
                    label = stringResource(R.string.drm_license_url),
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamDropdown(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.user_agent),
                        options = userAgentOptions,
                        selected = selectedUserAgent,
                        onSelect = { selectedUserAgent = it; viewModel.selectedUserAgent = it },
                        primaryColor = primaryColor
                    )
                    StreamDropdown(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.drm_scheme),
                        options = drmSchemeOptions,
                        selected = selectedDrmScheme,
                        onSelect = { selectedDrmScheme = it; viewModel.selectedDrmScheme = it },
                        primaryColor = primaryColor
                    )
                }
                AnimatedVisibility(
                    visible = selectedUserAgent == "Custom",
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    StreamTextField(
                        value = customUserAgent,
                        onValueChange = { customUserAgent = it; viewModel.customUserAgent = it },
                        label = stringResource(R.string.custom_user_agent),
                        primaryColor = primaryColor
                    )
                }
            }
            FloatingActionButton(
                onClick = {
                    if (streamUrl.isBlank()) return@FloatingActionButton
                    val ua = if (selectedUserAgent == "Custom") customUserAgent else selectedUserAgent
                    viewModel.recordPlayed(streamUrl)
                    onPlay(streamUrl, cookie, referer, origin, drmLicense, ua, selectedDrmScheme)
                },
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp + navBarPadding)
                    .border(
                        width = if (isPlayFocused) 3.dp else 0.dp,
                        color = if (isPlayFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    ),
                containerColor = if (isPlayFocused) primaryColor.copy(alpha = 0.85f) else primaryColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun StreamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    primaryColor: Color,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val fieldInteractionSource = remember { MutableInteractionSource() }
    val isFieldFocused by fieldInteractionSource.collectIsFocusedAsState()

    val trailingInteractionSource = remember { MutableInteractionSource() }
    val isTrailingFocused by trailingInteractionSource.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = fieldInteractionSource,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFieldFocused) 2.dp else 0.dp,
                color = if (isFieldFocused) primaryColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        label = {
            Text(
                text = label,
                fontFamily = BergenSans,
                fontSize = 13.sp
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    interactionSource = trailingInteractionSource
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = if (isTrailingFocused) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val text = clipboard.getText()?.text
                        if (!text.isNullOrEmpty()) onValueChange(text)
                    },
                    interactionSource = trailingInteractionSource
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = if (isTrailingFocused) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        textStyle = TextStyle(
            fontFamily = BergenSans,
            fontSize = 14.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = primaryColor,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = primaryColor,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamDropdown(
    modifier: Modifier = Modifier,
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    primaryColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    val fieldInteractionSource = remember { MutableInteractionSource() }
    val isFieldFocused by fieldInteractionSource.collectIsFocusedAsState()

    val fieldFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    if (expanded) {
        LaunchedEffect(expanded) {
            firstItemFocusRequester.requestFocus()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            interactionSource = fieldInteractionSource,
            label = {
                Text(
                    text = label,
                    fontFamily = BergenSans,
                    fontSize = 13.sp
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            textStyle = TextStyle(
                fontFamily = BergenSans,
                fontSize = 14.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = primaryColor,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = primaryColor,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTrailingIconColor = primaryColor,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .focusRequester(fieldFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                    ) {
                        expanded = true
                        true
                    } else {
                        false
                    }
                }
                .border(
                    width = if (isFieldFocused) 2.dp else 0.dp,
                    color = if (isFieldFocused) primaryColor else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                fieldFocusRequester.requestFocus()
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            options.forEachIndexed { index, option ->
                val itemInteractionSource = remember { MutableInteractionSource() }
                val isItemFocused by itemInteractionSource.collectIsFocusedAsState()

                DropdownMenuItem(
                    interactionSource = itemInteractionSource,
                    modifier = Modifier
                        .let { if (index == 0) it.focusRequester(firstItemFocusRequester) else it }
                        .background(
                            if (isItemFocused) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                        ),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = option == selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = primaryColor,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = option,
                                fontFamily = BergenSans,
                                fontSize = 14.sp,
                                color = if (option == selected || isItemFocused) primaryColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                        fieldFocusRequester.requestFocus()
                    }
                )
            }
        }
    }
}
