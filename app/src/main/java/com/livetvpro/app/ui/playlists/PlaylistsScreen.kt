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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Playlist
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableStateOf<Float?>(null) }
    var draggedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var itemSlotHeightPx by remember { mutableFloatStateOf(0f) }
    var dragBaseList by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var dragStartIndex by remember { mutableStateOf(-1) }

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
                        itemsIndexed(localPlaylists, key = { _, p -> p.id }) { index, playlist ->
                            val isDragging = playlist.id == draggedId

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { coords ->
                                        if (itemSlotHeightPx == 0f && coords.size.height > 0) {
                                            itemSlotHeightPx = coords.size.height.toFloat()
                                        }
                                        if (isDragging && dragStartY == null) {
                                            dragStartY = coords.positionInRoot().y
                                        }
                                    }
                            ) {
                                PlaylistCard(
                                    playlist = playlist,
                                    primaryColor = primaryColor,
                                    isDragging = false,
                                    isHovered = false,
                                    dragOffsetY = 0f,
                                    onClick = {
                                        if (draggedId == null) onNavigateToCategory(playlist.id, playlist.title)
                                    },
                                    onEditClick = { activeDialog = PlaylistDialog.Edit(playlist) },
                                    onDeleteClick = { activeDialog = PlaylistDialog.Delete(playlist) },
                                    onDragStart = {
                                        isDraggingActive = true
                                        draggedId = playlist.id
                                        draggedPlaylist = playlist
                                        dragOffsetY = 0f
                                        dragStartY = null
                                        // Freeze the list and the dragged item's index once, at the
                                        // moment the drag begins. Every subsequent move is computed
                                        // fresh from this fixed baseline plus the raw, uncorrected
                                        // finger offset -- never by mutating dragOffsetY itself.
                                        // That's what keeps the ghost card glued to the finger no
                                        // matter how many rows it crosses.
                                        dragBaseList = localPlaylists
                                        dragStartIndex = localPlaylists.indexOfFirst { it.id == playlist.id }
                                    },
                                    onDrag = { dy ->
                                        dragOffsetY += dy
                                        if (dragStartIndex == -1) return@PlaylistCard
                                        val slotHeight = if (itemSlotHeightPx > 0f) itemSlotHeightPx else 160f
                                        val targetIndex = (dragStartIndex + (dragOffsetY / slotHeight).roundToInt())
                                            .coerceIn(0, dragBaseList.lastIndex)
                                        val currentPosition = localPlaylists.indexOfFirst { it.id == draggedId }
                                        if (targetIndex != currentPosition) {
                                            val reordered = dragBaseList.toMutableList()
                                            val moved = reordered.removeAt(dragStartIndex)
                                            reordered.add(targetIndex, moved)
                                            localPlaylists = reordered
                                        }
                                    },
                                    onDragEnd = {
                                        val finalIndex = localPlaylists.indexOfFirst { it.id == draggedId }
                                        if (finalIndex != -1) {
                                            viewModel.persistOrder(localPlaylists)
                                        }
                                        draggedId = null
                                        draggedPlaylist = null
                                        dragOffsetY = 0f
                                        dragStartY = null
                                        dragBaseList = emptyList()
                                        dragStartIndex = -1
                                        isDraggingActive = false
                                    },
                                    modifier = if (isDragging) Modifier.alpha(0f) else Modifier
                                )
                            }
                        }
                    }

                    val dragging = draggedPlaylist
                    val startY = dragStartY
                    if (dragging != null && startY != null) {
                        PlaylistCard(
                            playlist = dragging,
                            primaryColor = primaryColor,
                            isDragging = true,
                            isHovered = false,
                            dragOffsetY = 0f,
                            onClick = {},
                            onEditClick = {},
                            onDeleteClick = {},
                            onDragStart = {},
                            onDrag = {},
                            onDragEnd = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .offset { IntOffset(0, (startY + dragOffsetY).roundToInt()) }
                                .zIndex(1f)
                        )
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

@Composable
private fun FabOptionPill(
    label: String,
    iconRes: Int,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = label,
                tint               = primaryColor,
                modifier           = Modifier.size(18.dp)
            )
            Text(
                text       = label,
                fontFamily = BergenSans,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    primaryColor: Color,
    isDragging: Boolean,
    isHovered: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.92f else 1f,
        animationSpec = tween(120),
        label = "cardAlpha"
    )

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .then(if (isDragging) Modifier.offset { IntOffset(0, dragOffsetY.roundToInt()) } else Modifier)
            .pointerInput(playlist.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { _, dragAmount -> currentOnDrag(dragAmount.y) },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp),
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
                        urlError   = !isFile && url.isBlank()
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
                    enabled      = !playlist.isFile,
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
                        urlError   = !playlist.isFile && url.isBlank()
                        if (!titleError && !urlError) {
                            onConfirm(
                                playlist.copy(
                                    title = title.trim(),
                                    url   = if (!playlist.isFile) url.trim() else playlist.url
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
