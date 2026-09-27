package com.livetvpro.app.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.repository.WatchHistoryEntry
import kotlinx.coroutines.delay

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val CardLogoBg = Color(0x80000000)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    spanCount: Int = 3,
    isTvDevice: Boolean = false,
    searchActive: Boolean = false,
    searchQuery: String = "",
    onCategoryClick: (Category) -> Unit,
    onCategoryInteraction: ((Category, () -> Unit) -> Boolean)? = null,
    onChannelClick: (Channel, Int) -> Unit = { _, _ -> },
    onContinueWatchingClick: (WatchHistoryEntry) -> Unit = {},
    onRecentSearchClick: (String) -> Unit = {},
) {
    val filteredCategories by viewModel.filteredCategories.observeAsState(emptyList())
    val searchResults by viewModel.searchResults.observeAsState(emptyList())
    val recentSearches by viewModel.recentSearches.observeAsState(emptyList())
    val continueWatching by viewModel.continueWatching.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isEmpty by viewModel.isEmpty.observeAsState(false)
    val primaryColor by viewModel.primaryColorFlow.collectAsState()

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val isQueryActive = searchQuery.isNotBlank()

    // Only surface the empty state once results have had a moment to arrive,
    // so the first keystroke doesn't flash "No results".
    var showNoResults by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery, searchResults.isEmpty(), filteredCategories.isEmpty()) {
        showNoResults = false
        if (isQueryActive && searchResults.isEmpty() && filteredCategories.isEmpty()) {
            delay(250)
            showNoResults = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            viewModel.refreshSilent()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            delay(1_000)
            isRefreshing = false
        }
    }

    if (isTvDevice) {
        LaunchedEffect(filteredCategories, searchResults) {
            if (filteredCategories.isNotEmpty() || searchResults.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { if (!isTvDevice) isRefreshing = true },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && !isQueryActive && filteredCategories.isEmpty() -> {}

                !isQueryActive && (isEmpty || filteredCategories.isEmpty()) -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No categories available",
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
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!isQueryActive && searchActive && recentSearches.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(
                                    title = "Recent searches",
                                    actionLabel = "Clear",
                                    onAction = { viewModel.clearRecentSearches() }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                RecentSearchesRow(
                                    searches = recentSearches,
                                    onSearchClick = onRecentSearchClick
                                )
                            }
                        }

                        if (isQueryActive) {
                            if (searchResults.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(
                                        title = "Channels",
                                        subtitle = searchResults.size.toString()
                                    )
                                }
                                items(
                                    searchResults,
                                    key = { "search_${it.id}" },
                                    span = { GridItemSpan(maxLineSpan) }
                                ) { channel ->
                                    ChannelResultRow(
                                        channel = channel,
                                        onClick = { onChannelClick(channel, -1) }
                                    )
                                }
                            }

                            if (filteredCategories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(
                                        title = "Categories",
                                        subtitle = filteredCategories.size.toString()
                                    )
                                }
                                items(filteredCategories, key = { "cat_${it.id}" }) { category ->
                                    CategoryCard(
                                        category = category,
                                        primaryColor = Color(primaryColor),
                                        isTvDevice = isTvDevice,
                                        onClick = {
                                            val navAction: () -> Unit = { onCategoryClick(category) }
                                            val redirected =
                                                onCategoryInteraction?.invoke(category, navAction) ?: false
                                            if (!redirected) navAction()
                                        }
                                    )
                                }
                            }

                            if (showNoResults) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    NoSearchResults(query = searchQuery)
                                }
                            }
                        } else {
                            if (continueWatching.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(
                                        title = "Continue watching",
                                        actionLabel = "Clear",
                                        onAction = { viewModel.clearContinueWatching() }
                                    )
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ContinueWatchingRail(
                                        entries = continueWatching,
                                        onEntryClick = onContinueWatchingClick
                                    )
                                }
                            }

                            items(filteredCategories, key = { it.id }) { category ->
                                CategoryCard(
                                    category = category,
                                    primaryColor = Color(primaryColor),
                                    isTvDevice = isTvDevice,
                                    onClick = {
                                        val navAction: () -> Unit = { onCategoryClick(category) }
                                        val redirected =
                                            onCategoryInteraction?.invoke(category, navAction) ?: false
                                        if (!redirected) navAction()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && !isQueryActive && filteredCategories.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontFamily = BergenSans,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    fontFamily = BergenSans,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RecentSearchesRow(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        searches.forEach { query ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onSearchClick(query) }
            ) {
                Text(
                    text = query,
                    fontFamily = BergenSans,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelResultRow(
    channel: Channel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.01f else 1f,
        animationSpec = tween(120),
        label = "resultScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelResultLogo(channel = channel)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = channel.categoryName.ifBlank { channel.groupTitle }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = BergenSans,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ChannelResultLogo(channel: Channel) {
    val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
        androidx.compose.ui.platform.LocalContext.current
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(CardLogoBg)
    ) {
        GlideImage(
            model = channel.logoUrl.takeIf { it.isNotBlank() },
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        ) {
            it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(96, 96)
                .placeholder(appIconRes)
                .error(appIconRes)
                .fallback(appIconRes)
        }
    }
}

@Composable
private fun NoSearchResults(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No results for \u201C$query\u201D",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = BergenSans,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try a different channel or category name",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = BergenSans,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ContinueWatchingRail(
    entries: List<WatchHistoryEntry>,
    onEntryClick: (WatchHistoryEntry) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            count = entries.size,
            key = { "watch_${entries[it].channel.id}" }
        ) { index ->
            ContinueWatchingCard(
                entry = entries[index],
                onClick = { onEntryClick(entries[index]) }
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        animationSpec = tween(120),
        label = "watchScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(120.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(CardLogoBg)
            ) {
                ContinueWatchingLogo(entry = entry)
            }
            Text(
                text = entry.channel.name,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
            if (entry.watchedAt > 0) {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(entry.watchedAt).toString(),
                    fontFamily = BergenSans,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ContinueWatchingLogo(entry: WatchHistoryEntry) {
    val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
        androidx.compose.ui.platform.LocalContext.current
    )
    GlideImage(
        model = entry.channel.logoUrl.takeIf { it.isNotBlank() },
        contentDescription = entry.channel.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    ) {
        it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .override(120, 120)
            .placeholder(appIconRes)
            .error(appIconRes)
            .fallback(appIconRes)
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    isTvDevice: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale),
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
                        model = category.iconUrl?.takeIf { it.isNotBlank() },
                        contentDescription = "Category icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .override(160, 160)
                            .placeholder(appIconRes)
                            .error(appIconRes)
                            .fallback(appIconRes)
                    }
                }
            }

            Text(
                text = category.name,
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
                    .basicMarquee(iterations = if (!isTvDevice || hasFocus) Int.MAX_VALUE else 0)
            )
        }
    }
}
