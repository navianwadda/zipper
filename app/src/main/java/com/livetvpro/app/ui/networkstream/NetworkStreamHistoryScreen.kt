package com.livetvpro.app.ui.networkstream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.livetvpro.app.R
import com.livetvpro.app.data.local.NetworkStreamHistoryEntry
import com.livetvpro.app.ui.navigation.Routes

@Composable
fun NetworkStreamHistoryScreen(
    navController: NavController,
) {
    val parentEntry = remember(navController) {
        navController.getBackStackEntry(Routes.NETWORK_STREAM)
    }
    val networkStreamViewModel: NetworkStreamViewModel = hiltViewModel(parentEntry)
    val historyViewModel: NetworkStreamHistoryViewModel = hiltViewModel()

    var showSortMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    painter            = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint               = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text       = "History",
                fontSize   = 19.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.weight(1f).padding(start = 4.dp),
            )
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_sort),
                        contentDescription = "Sort",
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded         = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text("Newest First")
                                RadioButton(
                                    selected = historyViewModel.newestFirst,
                                    onClick  = null,
                                    colors   = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                            }
                        },
                        onClick = {
                            historyViewModel.setNewestFirst(true)
                            showSortMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text("Oldest First")
                                RadioButton(
                                    selected = !historyViewModel.newestFirst,
                                    onClick  = null,
                                    colors   = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                            }
                        },
                        onClick = {
                            historyViewModel.setNewestFirst(false)
                            showSortMenu = false
                        },
                    )
                }
            }
            IconButton(onClick = { showClearDialog = true }) {
                Icon(
                    painter            = painterResource(R.drawable.ic_delete_sweep),
                    contentDescription = "Clear history",
                    tint               = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        LazyColumn(
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarPadding + 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(historyViewModel.entries, key = { it.id }) { entry ->
                HistoryRow(
                    entry    = entry,
                    onClick  = {
                        networkStreamViewModel.streamUrl = entry.url
                        navController.popBackStack()
                    },
                    onDelete = { historyViewModel.removeEntry(entry.id) },
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon             = {
                Icon(
                    painter            = painterResource(R.drawable.ic_delete_sweep),
                    contentDescription = null,
                )
            },
            title            = { Text("Clear History") },
            text             = { Text("Are you sure, all your watch history will be deleted.") },
            confirmButton    = {
                TextButton(onClick = {
                    historyViewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All")
                }
            },
            dismissButton    = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: NetworkStreamHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = entry.url,
            fontSize = 15.sp,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter            = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete entry",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
