package com.livetvpro.app.ui.networkstream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.livetvpro.app.R
import org.json.JSONObject

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

@Composable
fun NetworkStreamScreen(
    viewModel: NetworkStreamViewModel = hiltViewModel(),
    onPlay: (streamUrl: String, cookie: String, referer: String, origin: String, drmLicense: String, userAgent: String, drmScheme: String, customHeaders: String) -> Unit
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

    // Custom headers are edited as a list of key/value rows in the UI, but kept in
    // sync with viewModel.customHeaders (a JSON string of {"key":"value"}) so that
    // downstream code (persistence, onPlay callback) needs no changes.
    val customHeaderRows = remember {
        mutableStateListOf<HeaderRow>().apply { addAll(parseHeaderRows(viewModel.customHeaders)) }
    }
    fun syncCustomHeaders() {
        viewModel.customHeaders = serializeHeaderRows(customHeaderRows)
    }
    var showAddHeaderDialog by remember { mutableStateOf(false) }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val selectionColors = TextSelectionColors(
        handleColor = primaryColor,
        backgroundColor = primaryColor.copy(alpha = 0.3f)
    )

    val fabFocusRequester = remember { FocusRequester() }

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
                CustomHeadersSection(
                    headers = customHeaderRows,
                    primaryColor = primaryColor,
                    onAddClick = { showAddHeaderDialog = true },
                    onRemove = { index ->
                        customHeaderRows.removeAt(index)
                        syncCustomHeaders()
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selectedUserAgent != "Custom")
                                Modifier.focusProperties { down = fabFocusRequester }
                            else Modifier
                        ),
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
                        primaryColor = primaryColor,
                        modifier = Modifier.focusProperties { down = fabFocusRequester }
                    )
                }
            }
            FloatingActionButton(
                onClick = {
                    if (streamUrl.isBlank()) return@FloatingActionButton
                    val ua = if (selectedUserAgent == "Custom") customUserAgent else selectedUserAgent
                    viewModel.recordPlayed(streamUrl)
                    onPlay(streamUrl, cookie, referer, origin, drmLicense, ua, selectedDrmScheme, viewModel.customHeaders)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp + navBarPadding)
                    .focusRequester(fabFocusRequester),
                containerColor = primaryColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(28.dp)
                )
            }

            if (showAddHeaderDialog) {
                AddHeaderDialog(
                    primaryColor = primaryColor,
                    onDismiss = { showAddHeaderDialog = false },
                    onConfirm = { key, value ->
                        customHeaderRows.add(HeaderRow(key = key, value = value))
                        syncCustomHeaders()
                        showAddHeaderDialog = false
                    }
                )
            }
        }
    }
}

/**
 * A single custom HTTP header key/value pair.
 */
private data class HeaderRow(val key: String, val value: String)

private fun parseHeaderRows(json: String): List<HeaderRow> {
    if (json.isBlank()) return emptyList()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().map { key ->
            HeaderRow(key = key, value = obj.optString(key, ""))
        }.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun serializeHeaderRows(rows: List<HeaderRow>): String {
    if (rows.isEmpty()) return ""
    val obj = JSONObject()
    rows.forEach { row ->
        if (row.key.isNotBlank()) obj.put(row.key, row.value)
    }
    return if (obj.length() == 0) "" else obj.toString()
}

@Composable
private fun StreamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    primaryColor: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                fontFamily = BergenSans,
                fontSize = 13.sp
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(onClick = {
                    val text = clipboard.getText()?.text
                    if (!text.isNullOrEmpty()) onValueChange(text)
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        )
    )
}

/**
 * Renders the label + list of currently configured custom headers, plus an
 * "Add Header" action. Each header row shows "key: value" with a remove button.
 * Fully keyboard/D-pad (TV) and pointer navigable via focus-aware highlighting,
 * matching the same pattern used by [StreamDropdown]'s option list.
 */
@Composable
private fun CustomHeadersSection(
    headers: List<HeaderRow>,
    primaryColor: Color,
    onAddClick: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Custom Headers",
            fontFamily = BergenSans,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (headers.isEmpty()) {
            Text(
                text = "No custom headers added yet.",
                fontFamily = BergenSans,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            headers.forEachIndexed { index, row ->
                HeaderRowItem(
                    row = row,
                    primaryColor = primaryColor,
                    onRemove = { onRemove(index) }
                )
            }
        }

        AddHeaderButton(primaryColor = primaryColor, onClick = onAddClick)
    }
}

@Composable
private fun HeaderRowItem(
    row: HeaderRow,
    primaryColor: Color,
    onRemove: () -> Unit,
) {
    val removeInteractionSource = remember { MutableInteractionSource() }
    val isRemoveFocused by removeInteractionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.key,
                fontFamily = BergenSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.value.isNotEmpty()) {
                Text(
                    text = row.value,
                    fontFamily = BergenSans,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onRemove,
            interactionSource = removeInteractionSource,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isRemoveFocused) primaryColor.copy(alpha = 0.2f) else Color.Transparent
                )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove header ${row.key}",
                tint = if (isRemoveFocused) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddHeaderButton(
    primaryColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor,
            containerColor = if (isFocused) primaryColor.copy(alpha = 0.12f) else Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = primaryColor
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Add Header",
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

/**
 * Dialog for entering a new custom header's key/value.
 *
 * Validation rule: a header is only accepted when the key is non-blank.
 * - key present, value present -> accepted
 * - key present, value blank   -> accepted (value defaults to empty string)
 * - key blank, value present   -> rejected (shows an error, does not submit)
 * - key blank, value blank     -> rejected (Add button does nothing)
 */
@Composable
private fun AddHeaderDialog(
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Header",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderDialogTextField(
                    value = key,
                    onValueChange = { key = it; keyError = false },
                    label = "Key",
                    isError = keyError,
                    primaryColor = primaryColor
                )
                HeaderDialogTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Value",
                    isError = false,
                    primaryColor = primaryColor
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                HeaderDialogButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                HeaderDialogButton(
                    text = "Add",
                    onClick = {
                        val trimmedKey = key.trim()
                        if (trimmedKey.isBlank()) {
                            keyError = true
                        } else {
                            onConfirm(trimmedKey, value.trim())
                        }
                    },
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        dismissButton = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun HeaderDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    primaryColor: Color,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(text = label, fontFamily = BergenSans, fontSize = 13.sp)
        },
        isError = isError,
        supportingText = if (isError) {
            { Text(text = "Key is required", fontFamily = BergenSans, fontSize = 11.sp) }
        } else null,
        singleLine = true,
        textStyle = TextStyle(fontFamily = BergenSans, fontSize = 14.sp),
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
}

@Composable
private fun HeaderDialogButton(
    text: String,
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) primaryColor else primaryColor.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)) else null
    ) {
        Text(
            text = text,
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
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
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val firstOptionFocusRequester = remember { FocusRequester() }
            LaunchedEffect(expanded) {
                if (expanded) firstOptionFocusRequester.requestFocus()
            }
            options.forEachIndexed { index, option ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                color = if (option == selected) primaryColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    interactionSource = interactionSource,
                    modifier = if (index == 0) Modifier.focusRequester(firstOptionFocusRequester) else Modifier,
                )
            }
        }
    }
}
