package com.livetvpro.app.ui.networkstream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
    viewModel: NetworkStreamHistoryViewModel,
) {
    val parentEntry = remember(navController) {
        navController.getBackStackEntry(Routes.NETWORK_STREAM)
    }
    val networkStreamViewModel: NetworkStreamViewModel = hiltViewModel(parentEntry)

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarPadding + 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(viewModel.entries, key = { it.id }) { entry ->
            HistoryRow(
                entry    = entry,
                onClick  = {
                    networkStreamViewModel.streamUrl = entry.url
                    navController.popBackStack()
                },
                onDelete = { viewModel.removeEntry(entry.id) },
            )
        }
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
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text      = entry.url,
            fontSize  = 14.sp,
            color     = MaterialTheme.colorScheme.onSurface,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier.weight(1f).padding(end = 8.dp),
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
