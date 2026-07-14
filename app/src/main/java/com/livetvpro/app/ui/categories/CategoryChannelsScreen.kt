package com.livetvpro.app.ui.categories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.ui.player.dialogs.LinkSelectionDialog
import com.livetvpro.app.ui.player.dialogs.toLinkItem
import kotlinx.coroutines.delay

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val CardLogoBg = Color(0x80000000)

@Composable
fun CategoryChannelsScreen(
    viewModel: CategoryChannelsViewModel,
    spanCount: Int = 3,
    isTvDevice: Boolean = false,
    onChannelClick: (Channel, Int) -> Unit,
    onChannelInteraction: ((Channel, () -> Unit) -> Boolean)? = null,
    onShowGroupsDialog: (() -> Unit)? = null
) {
    val channels: LazyPagingItems<Channel> = viewModel.channelsPaged.collectAsLazyPagingItems()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val categoryGroups by viewModel.categoryGroups.observeAsState(emptyList())
    val currentGroup by viewModel.currentGroup.observeAsState("All")

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    var linkDialogChannel by remember { mutableStateOf<Channel?>(null) }
    var favoriteDialogChannel by remember { mutableStateOf<Channel?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshChannels()
            delay(1_000)
            isRefreshing = false
        }
    }

    val hasGroups = categoryGroups.isNotEmpty()

    if (linkDialogChannel != null) {
        val channel = linkDialogChannel!!
        val links = channel.links.orEmpty()
        LinkSelectionDialog(
            links = links.map { it.toLinkItem() },
            onLinkSelected = { _, index ->
                onChannelClick(channel, index)
            },
            onDismiss = { linkDialogChannel = null },
        )
    }

    favoriteDialogChannel?.let { channel ->
        val isFav = viewModel.isFavorite(channel.id)
        AlertDialog(
            onDismissRequest = { favoriteDialogChannel = null },
            title = {
                Text(
                    text = if (isFav) "Remove from Favorites?" else "Add to Favorites?",
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isFav) "Remove \"${channel.name}\" from favorites?"
                           else "Add \"${channel.name}\" to favorites?",
                    fontFamily = BergenSans
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleFavorite(channel)
                        favoriteDialogChannel = null
                    },
                    colors = if (isFav) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Text(
                        text = if (isFav) "Remove" else "Add",
                        fontFamily = BergenSans
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { favoriteDialogChannel = null }) {
                    Text("Cancel", fontFamily = BergenSans)
                }
            }
        )
    }

    if (isTvDevice) {
        LaunchedEffect(channels.itemCount) {
            if (channels.itemCount > 0) gridState.scrollToItem(0)
        }
    }

    val isEmpty = channels.itemCount == 0 &&
        channels.loadState.refresh is LoadState.NotLoading

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (hasGroups) {
            val selectedIndex = categoryGroups.indexOf(currentGroup).coerceAtLeast(0)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onShowGroupsDialog != null) {
                    IconButton(onClick = onShowGroupsDialog) {
                        Icon(
                            painter = painterResource(R.drawable.ic_list),
                            contentDescription = "Search Groups",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    categoryGroups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectGroup(group) },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    text = group,
                                    fontFamily = BergenSans,
                                    fontSize = 14.sp,
                                    color = if (index == selectedIndex)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (!isTvDevice) isRefreshing = true },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && channels.itemCount == 0 -> {}

                    isEmpty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No channels available",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = BergenSans,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    }

                    else -> {
                        val navBarBottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()

                        val bottomPadding = maxOf(
                            navBarBottom
                                + dimensionResource(R.dimen.nav_bottom_margin)
                                + dimensionResource(R.dimen.nav_height),
                            90.dp
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 4.dp,
                                top = 4.dp,
                                end = 4.dp,
                                bottom = bottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = channels.itemCount,
                                key = channels.itemKey { it.id }
                            ) { index ->
                                val channel = channels[index] ?: return@items
                                ChannelCard(
                                    channel = channel,
                                    isFavorite = viewModel.isFavorite(channel.id),
                                    onClick = {
                                        val action: () -> Unit = {
                                            if ((channel.links?.size ?: 0) > 1) {
                                                linkDialogChannel = channel
                                            } else {
                                                onChannelClick(channel, 0)
                                            }
                                        }
                                        val redirected =
                                            onChannelInteraction?.invoke(channel, action) ?: false
                                        if (!redirected) action()
                                    },
                                    onLongClick = { favoriteDialogChannel = channel }
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && channels.itemCount == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(CardLogoBg)
                ) {
                    val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
                        androidx.compose.ui.platform.LocalContext.current
                    )
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model = channel.logoUrl.takeIf { it.isNotBlank() },
                        contentDescription = "Channel logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(appIconRes)
                            .error(appIconRes)
                            .fallback(appIconRes)
                    }
                }

                if (isFavorite) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star_filled),
                        contentDescription = "Favorite indicator",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(4.dp)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            Text(
                text = channel.name,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}
