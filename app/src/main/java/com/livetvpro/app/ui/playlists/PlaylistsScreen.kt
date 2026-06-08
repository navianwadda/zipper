package com.livetvpro.app.ui.playlists

import android.net.Uri
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()

    var fabExpanded by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<PlaylistDialog>(PlaylistDialog.None) }

    val selectionColors = TextSelectionColors(
        handleColor = primaryColor,
        backgroundColor = primaryColor.copy(alpha = 0.3f)
    )

    // Surface any ViewModel errors to the fragment as Toasts
    LaunchedEffect(error) {
        error?.let {
            onError(it)
            viewModel.clearError()
        }
    }

    // When the fragment picks a file, open the Add dialog immediately
    LaunchedEffect(pendingFileUri) {
        if (pendingFileUri != null) {
            fabExpanded = false
            activeDialog = PlaylistDialog.Add(isFile = true, fileUri = pendingFileUri)
        }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            // Dim scrim when speed-dial is open
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f))
                        .clickable(onClick = { fabExpanded = false })
                )
            }

            // Content
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
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                primaryColor = primaryColor,
                                onClick = { onNavigateToCategory(playlist.id, playlist.title) },
                                onEditClick = { activeDialog = PlaylistDialog.Edit(playlist) },
                                onDeleteClick = { activeDialog = PlaylistDialog.Delete(playlist) }
                            )
                        }
                    }
                }
            }

            // FABs
            if (!isTvDevice) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + navBarPadding),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Speed-dial: Add via URL
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Bottom),
                        exit  = fadeOut(tween(130)) + scaleOut(tween(130)) + shrinkVertically(tween(130), shrinkTowards = Alignment.Bottom)
                    ) {
                        FabOptionRow(
                            label = "Add Playlist URL",
                            iconRes = R.drawable.ic_link,
                            primaryColor = primaryColor,
                            onClick = {
                                fabExpanded = false
                                activeDialog = PlaylistDialog.Add(isFile = false)
                            }
                        )
                    }

                    // Speed-dial: Add via File
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(tween(160)) + scaleIn(tween(160)) + expandVertically(tween(160), expandFrom = Alignment.Bottom),
                        exit  = fadeOut(tween(100)) + scaleOut(tween(100)) + shrinkVertically(tween(100), shrinkTowards = Alignment.Bottom)
                    ) {
                        FabOptionRow(
                            label = "Add Playlist File",
                            iconRes = R.drawable.ic_folder,
                            primaryColor = primaryColor,
                            onClick = {
                                fabExpanded = false
                                onPickFile()
                            }
                        )
                    }

                    // Main FAB
                    val fabRotation by animateFloatAsState(
                        targetValue = if (fabExpanded) 45f else 0f,
                        animationSpec = tween(220),
                        label = "fabRotation"
                    )
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        shape = CircleShape,
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Playlist",
                            modifier = Modifier.rotate(fabRotation)
                        )
                    }
                }
            } else {
                // TV: single FAB at bottom-end
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

    // Active dialog rendering
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
                    if (isFile && dialog.fileUri != null) {
                        viewModel.addPlaylist(title, "", true, dialog.fileUri.toString())
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

// ─── Speed-dial option row ───────────────────────────────────────────────────

@Composable
private fun FabOptionRow(
    label: String,
    iconRes: Int,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor   = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text       = label,
                fontFamily = BergenSans,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(
            onClick        = onClick,
            shape          = CircleShape,
            containerColor = primaryColor,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            elevation      = FloatingActionButtonDefaults.elevation()
        ) {
            Icon(
                painter           = painterResource(iconRes),
                contentDescription = label,
                modifier          = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Playlist card ───────────────────────────────────────────────────────────

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    primaryColor: Color,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor   = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border    = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier          = Modifier
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

// ─── Dialogs ─────────────────────────────────────────────────────────────────

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
                    enabled      = !isFile,
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                titleError = title.isBlank()
                urlError   = !isFile && url.isBlank()
                if (!titleError && !urlError) {
                    onConfirm(title.trim(), url.trim(), isFile, url.trim())
                }
            }) {
                Text("Add", fontFamily = BergenSans, color = primaryColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontFamily = BergenSans,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
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
                    enabled      = !playlist.isFile,
                    primaryColor = primaryColor,
                    keyboardType = KeyboardType.Uri
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                titleError = title.isBlank()
                urlError   = !playlist.isFile && url.isBlank()
                if (!titleError && !urlError) {
                    onConfirm(
                        playlist.copy(
                            title = title.trim(),
                            url   = if (!playlist.isFile) url.trim() else playlist.url
                        )
                    )
                }
            }) {
                Text("Update", fontFamily = BergenSans, color = primaryColor)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDismiss(); onDelete() }) {
                    Text(
                        "Delete",
                        fontFamily = BergenSans,
                        color      = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        fontFamily = BergenSans,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
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
            TextButton(onClick = onConfirm) {
                Text("Delete", fontFamily = BergenSans, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontFamily = BergenSans,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor    = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor  = MaterialTheme.colorScheme.onSurface,
        shape             = RoundedCornerShape(16.dp)
    )
}

// ─── Shared text field ────────────────────────────────────────────────────────

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
        singleLine      = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = ImeAction.Next
        )
    )
}
