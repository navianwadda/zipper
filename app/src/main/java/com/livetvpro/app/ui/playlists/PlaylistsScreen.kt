package com.livetvpro.app.ui.playlists

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.livetvpro.app.data.models.Playlist
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private sealed class PlaylistDialog {
    object None : PlaylistDialog()
    data class Add(val isFile: Boolean, val fileUri: Uri? = null) : PlaylistDialog()
    data class Edit(val playlist: Playlist) : PlaylistDialog()
    data class Delete(val playlist: Playlist) : PlaylistDialog()
}

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    isTvDevice: Boolean = false,
    pendingFileUri: Uri? = null,
    onNavigateToCategory: (playlistId: String, playlistName: String) -> Unit,
    onPickFile: () -> Unit,
    onFileDialogDismissed: () -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val primaryColorInt by viewModel.primaryColorFlow.collectAsState()
    val primaryColor = Color(primaryColorInt)

    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var localPlaylists by remember { mutableStateOf(playlists) }
    var isDraggingActive by remember { mutableStateOf(false) }
    LaunchedEffect(playlists) {
        if (!isDraggingActive) localPlaylists = playlists
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var fabExpanded by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<PlaylistDialog>(PlaylistDialog.None) }

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        localPlaylists = localPlaylists.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    val selectionColors = TextSelectionColors(
        handleColor = primaryColor,
        backgroundColor = primaryColor.copy(alpha = 0.3f)
    )

    LaunchedEffect(error) {
        error?.let {
            onError(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(pendingFileUri) {
        if (pendingFileUri != null) {
            fabExpanded = false
            activeDialog = PlaylistDialog.Add(isFile = true, fileUri = pendingFileUri)
        }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            if (fabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { fabExpanded = false }
                        )
                )
            }

            when {
                isLoading && playlists.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = primaryColor
                    )
                }
                playlists.isEmpty() -> {
                    Text(
                        text = "No Playlists Found",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = BergenSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = navBarPadding + 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(localPlaylists, key = { it.id }) { playlist ->
                            ReorderableItem(reorderableLazyListState, key = playlist.id) { isDragging ->
                                PlaylistCard(
                                    playlist = playlist,
                                    primaryColor = primaryColor,
                                    isDragging = isDragging,
                                    onClick = {
                                        if (!isDragging) onNavigateToCategory(playlist.id, playlist.title)
                                    },
                                    onEditClick = { activeDialog = PlaylistDialog.Edit(playlist) },
                                    onDeleteClick = { activeDialog = PlaylistDialog.Delete(playlist) },
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = { isDraggingActive = true },
                                        onDragStopped = {
                                            isDraggingActive = false
                                            viewModel.persistOrder(localPlaylists)
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (!isTvDevice) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + navBarPadding),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Bottom),
                        exit  = fadeOut(tween(130)) + scaleOut(tween(130)) + shrinkVertically(tween(130), shrinkTowards = Alignment.Bottom)
                    ) {
                        FabOptionPill(
                            label = "Add Playlist URL",
                            iconRes = R.drawable.ic_link,
                            primaryColor = primaryColor,
                            onClick = {
                                fabExpanded = false
                                activeDialog = PlaylistDialog.Add(isFile = false)
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(tween(160)) + scaleIn(tween(160)) + expandVertically(tween(160), expandFrom = Alignment.Bottom),
                        exit  = fadeOut(tween(100)) + scaleOut(tween(100)) + shrinkVertically(tween(100), shrinkTowards = Alignment.Bottom)
                    ) {
                        FabOptionPill(
                            label = "Add Playlist File",
                            iconRes = R.drawable.ic_folder,
                            primaryColor = primaryColor,
                            onClick = {
                                fabExpanded = false
                                onPickFile()
                            }
                        )
                    }

                    val fabRotation by animateFloatAsState(
                        targetValue = if (fabExpanded) 45f else 0f,
                        animationSpec = tween(150),
                        label = "fabRotation"
                    )
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        shape = CircleShape,
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = if (fabExpanded) 0.dp else 6.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Playlist",
                            modifier = Modifier.rotate(fabRotation)
                        )
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { activeDialog = PlaylistDialog.Add(isFile = false) },
                    shape = CircleShape,
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + navBarPadding)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Playlist"
                    )
                }
            }
        }
    }

    when (val dialog = activeDialog) {
        is PlaylistDialog.Add -> {
            AddPlaylistDialog(
                isFile = dialog.isFile,
                fileUri = dialog.fileUri,
                primaryColor = primaryColor,
                onDismiss = {
                    activeDialog = PlaylistDialog.None
                    if (dialog.isFile) onFileDialogDismissed()
                },
                onConfirm = { title, url, isFile, filePath ->
                    if (isFile) {
                        viewModel.addPlaylist(title, "", true, filePath)
                    } else {
                        viewModel.addPlaylist(title, url, false, "")
                    }
                    activeDialog = PlaylistDialog.None
                    if (isFile) onFileDialogDismissed()
                }
            )
        }
        is PlaylistDialog.Edit -> {
            EditPlaylistDialog(
                playlist = dialog.playlist,
                primaryColor = primaryColor,
                onDismiss = { activeDialog = PlaylistDialog.None },
                onConfirm = { updated ->
                    viewModel.updatePlaylist(updated)
                    activeDialog = PlaylistDialog.None
                },
                onDelete = { activeDialog = PlaylistDialog.Delete(dialog.playlist) }
            )
        }
        is PlaylistDialog.Delete -> {
            DeletePlaylistDialog(
                playlist = dialog.playlist,
                primaryColor = primaryColor,
                onDismiss = { activeDialog = PlaylistDialog.None },
                onConfirm = {
                    viewModel.deletePlaylist(dialog.playlist)
                    activeDialog = PlaylistDialog.None
                }
            )
        }
        PlaylistDialog.None -> Unit
    }
}

@Composable
private fun FabOptionPill(
    label: String,
    iconRes: Int,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = primaryColor,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = label,
                tint               = MaterialTheme.colorScheme.onPrimary,
                modifier           = Modifier.size(18.dp)
            )
            Text(
                text       = label,
                fontFamily = BergenSans,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    primaryColor: Color,
    isDragging: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 6.dp else 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = playlist.title,
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text       = playlist.getSource(),
                    fontFamily = BergenSans,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 1,
                    overflow   = TextOverflow.MiddleEllipsis
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector       = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint              = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier          = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector       = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint              = MaterialTheme.colorScheme.error,
                    modifier          = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
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

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun AddPlaylistDialog(
    isFile: Boolean,
    fileUri: Uri?,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, isFile: Boolean, filePath: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url   by remember { mutableStateOf(if (isFile) fileUri?.toString() ?: "" else "") }
    var titleError by remember { mutableStateOf(false) }
    var urlError   by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(fileUri) {
        if (isFile && fileUri != null) {
            queryDisplayName(context, fileUri)?.let { title = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Add Playlist",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistTextField(
                    value        = title,
                    onValueChange = { title = it; titleError = false },
                    label        = "Title",
                    isError      = titleError,
                    primaryColor = primaryColor
                )
                PlaylistTextField(
                    value        = url,
                    onValueChange = { url = it; urlError = false },
                    label        = if (isFile) "File Path" else "URL",
                    isError      = urlError,
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
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
                DialogButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Add",
                    onClick = {
                        titleError = title.isBlank()
                        urlError   = url.isBlank()
                        if (!titleError && !urlError) {
                            onConfirm(title.trim(), url.trim(), isFile, url.trim())
                        }
                    },
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        dismissButton = null,
        containerColor    = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor  = MaterialTheme.colorScheme.onSurface,
        shape             = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Playlist) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(playlist.title) }
    var url   by remember { mutableStateOf(if (playlist.isFile) playlist.filePath else playlist.url) }
    var titleError by remember { mutableStateOf(false) }
    var urlError   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Update Playlist Details",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistTextField(
                    value        = title,
                    onValueChange = { title = it; titleError = false },
                    label        = "Title",
                    isError      = titleError,
                    primaryColor = primaryColor
                )
                PlaylistTextField(
                    value        = url,
                    onValueChange = { url = it; urlError = false },
                    label        = if (playlist.isFile) "File Path" else "URL",
                    isError      = urlError,
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
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
                DialogButton(
                    text = "Delete",
                    onClick = { onDismiss(); onDelete() },
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Update",
                    onClick = {
                        titleError = title.isBlank()
                        urlError   = url.isBlank()
                        if (!titleError && !urlError) {
                            onConfirm(
                                playlist.copy(
                                    title    = title.trim(),
                                    url      = if (!playlist.isFile) url.trim() else playlist.url,
                                    filePath = if (playlist.isFile) url.trim() else playlist.filePath
                                )
                            )
                        }
                    },
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        dismissButton = null,
        containerColor    = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor  = MaterialTheme.colorScheme.onSurface,
        shape             = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlist: Playlist,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Delete Playlist",
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text       = "Are you sure you want to delete \"${playlist.title}\"?",
                fontFamily = BergenSans
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                DialogButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Delete",
                    onClick = onConfirm,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        dismissButton = null,
        containerColor    = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor  = MaterialTheme.colorScheme.onSurface,
        shape             = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun PlaylistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    primaryColor: Color,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        enabled       = enabled,
        isError       = isError,
        label         = { Text(text = label, fontFamily = BergenSans, fontSize = 13.sp) },
        trailingIcon  = {
            if (value.isNotEmpty() && enabled) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector       = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint              = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        textStyle = TextStyle(
            fontFamily = BergenSans,
            fontSize   = 14.sp,
            color      = MaterialTheme.colorScheme.onSurface
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = primaryColor,
            unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
            errorBorderColor        = MaterialTheme.colorScheme.error,
            focusedLabelColor       = primaryColor,
            unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor             = primaryColor,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor  = MaterialTheme.colorScheme.surfaceContainer,
            disabledTextColor       = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledBorderColor     = MaterialTheme.colorScheme.outlineVariant,
        ),
        shape           = RoundedCornerShape(10.dp),
        singleLine      = false,
        maxLines        = 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = ImeAction.Next
        )
    )
}
