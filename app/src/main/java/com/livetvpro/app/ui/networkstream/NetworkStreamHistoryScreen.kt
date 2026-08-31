package com.livetvpro.app.ui.networkstream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.livetvpro.app.R
import com.livetvpro.app.data.local.NetworkStreamHistoryEntry
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.utils.DeviceUtils

@Composable
fun NetworkStreamHistoryScreen(
    navController: NavController,
    viewModel: NetworkStreamHistoryViewModel,
    isTvDevice: Boolean = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet,
) {
    val parentEntry = remember(navController) {
        navController.getBackStackEntry(Routes.NETWORK_STREAM)
    }
    val networkStreamViewModel: NetworkStreamViewModel = hiltViewModel(parentEntry)

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val listState = rememberLazyListState()
    val firstRowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel.newestFirst) {
        listState.scrollToItem(0)
    }

    if (isTvDevice) {
        LaunchedEffect(viewModel.entries) {
            if (viewModel.entries.isNotEmpty()) {
                firstRowFocusRequester.requestFocus()
            }
        }
    }

    LazyColumn(
        state               = listState,
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarPadding + 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(viewModel.entries, key = { _, entry -> entry.id }) { index, entry ->
            HistoryRow(
                entry            = entry,
                isTvDevice       = isTvDevice,
                focusRequester   = if (index == 0) firstRowFocusRequester else null,
                onClick          = {
                    networkStreamViewModel.streamUrl = entry.url
                    networkStreamViewModel.cookie = entry.cookie
                    networkStreamViewModel.referer = entry.referer
                    networkStreamViewModel.origin = entry.origin
                    networkStreamViewModel.drmLicense = entry.drmLicense
                    networkStreamViewModel.selectedUserAgent = entry.selectedUserAgent
                    networkStreamViewModel.customUserAgent = entry.customUserAgent
                    networkStreamViewModel.selectedDrmScheme = entry.drmScheme
                    networkStreamViewModel.customHeaders = entry.customHeaders
                    navController.popBackStack()
                },
                onDelete         = { viewModel.removeEntry(entry.id) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: NetworkStreamHistoryEntry,
    isTvDevice: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowFocused by rowInteractionSource.collectIsFocusedAsState()

    val deleteInteractionSource = remember { MutableInteractionSource() }
    val isDeleteFocused by deleteInteractionSource.collectIsFocusedAsState()

    val highlighted = isTvDevice && (isRowFocused || isDeleteFocused)

    Surface(
        onClick          = onClick,
        interactionSource = rowInteractionSource,
        modifier         = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape            = RoundedCornerShape(14.dp),
        color            = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        border           = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text     = entry.url,
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val extras = buildList {
                    if (entry.cookie.isNotBlank()) add("Cookie")
                    if (entry.referer.isNotBlank()) add("Referer")
                    if (entry.origin.isNotBlank()) add("Origin")
                    if (entry.drmLicense.isNotBlank()) add("DRM: ${entry.drmScheme}")
                    if (entry.selectedUserAgent != "Default") add("UA: ${entry.selectedUserAgent}")
                    if (entry.customHeaders.isNotBlank()) add("Headers")
                }
                if (extras.isNotEmpty()) {
                    Text(
                        text     = extras.joinToString(" • "),
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick           = onDelete,
                interactionSource = deleteInteractionSource,
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete entry",
                    tint               = if (isDeleteFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
