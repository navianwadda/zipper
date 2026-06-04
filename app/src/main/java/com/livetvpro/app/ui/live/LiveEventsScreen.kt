package com.livetvpro.app.ui.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.livetvpro.app.R
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.LiveEvent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val Red = Color(0xFFEF4444)
private val Green = Color(0xFF10B981)

@Composable
fun LiveEventsScreen(
    viewModel: LiveEventsViewModel = hiltViewModel(),
    messageBannerText: String = "",
    messageBannerUrl: String = "",
    spanCount: Int = 2,
    isTvDevice: Boolean = false,
    onEventClick: (LiveEvent, Int) -> Unit,
    onEventInteraction: ((LiveEvent, () -> Unit) -> Boolean)? = null
) {
    val context = LocalContext.current

    val filteredEvents by viewModel.filteredEvents.observeAsState(emptyList())
    val eventCategories by viewModel.eventCategories.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isEmpty by viewModel.isEmpty.observeAsState(false)
    val primaryColor by viewModel.primaryColorFlow.collectAsState()

    var selectedStatusFilter by remember { mutableStateOf<EventStatus?>(null) }
    var selectedCategoryId by remember { mutableStateOf("evt_cat_all") }
    var linkDialogEvent by remember { mutableStateOf<LiveEvent?>(null) }

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.loadEventCategories()
        if (viewModel.filteredEvents.value == null) {
            viewModel.filterEvents(null, "evt_cat_all")
        } else {
            selectedStatusFilter = viewModel.pendingStatusFilter
            selectedCategoryId = viewModel.pendingCategoryId
        }
    }

    LaunchedEffect(selectedStatusFilter, selectedCategoryId) {
        while (true) {
            delay(10_000)
            viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
        }
    }

    if (linkDialogEvent != null) {
        val event = linkDialogEvent!!
        AlertDialog(
            onDismissRequest = { linkDialogEvent = null },
            title = { Text("Multiple Links Available") },
            text = {
                Column {
                    event.links.forEachIndexed { index, link ->
                        Text(
                            text = link.quality,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEventClick(event, index)
                                    linkDialogEvent = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { linkDialogEvent = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (messageBannerText.isNotBlank()) {
            MarqueeBanner(
                text = messageBannerText,
                url = messageBannerUrl,
                context = context
            )
        }

        if (eventCategories.isNotEmpty()) {
            CategoryRow(
                categories = eventCategories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { category ->
                    selectedCategoryId = category.id
                    viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
                }
            )
        }

        StatusFilterChips(
            selected = selectedStatusFilter,
            onFilterSelected = { status ->
                selectedStatusFilter = status
                viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                state = pullToRefreshState,
                enabled = !isTvDevice,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && filteredEvents.isEmpty() -> {}
                    isEmpty || filteredEvents.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No events available",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredEvents, key = { it.id }) { event ->
                                LiveEventCard(
                                    event = event,
                                    primaryColor = Color(primaryColor),
                                    onClick = {
                                        if (event.links.isEmpty()) return@LiveEventCard
                                        val playerAction: () -> Unit = {
                                            if (event.links.size > 1) {
                                                linkDialogEvent = event
                                            } else {
                                                onEventClick(event, 0)
                                            }
                                        }
                                        val redirected = onEventInteraction?.invoke(event, playerAction) ?: false
                                        if (!redirected) playerAction()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && filteredEvents.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun MarqueeBanner(text: String, url: String, context: Context) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        while (true) {
            scrollState.animateScrollTo(
                scrollState.maxValue,
                animationSpec = tween(durationMillis = (text.length * 100).coerceAtLeast(3000))
            )
            scrollState.scrollTo(0)
        }
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .horizontalScroll(scrollState, enabled = false)
            .clickable(enabled = url.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun CategoryRow(
    categories: List<EventCategory>,
    selectedCategoryId: String,
    onCategorySelected: (EventCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(categories, key = { _, c -> c.id }) { _, category ->
            CategoryChip(
                category = category,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: EventCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.08f else 1f,
        animationSpec = tween(120),
        label = "catScale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 2.dp,
                    color = if (isSelected) Red else Color(0xFF5A5A5A),
                    shape = CircleShape
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(category.logoUrl)
                    .crossfade(true)
                    .fallback(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .build(),
                contentDescription = category.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(70.dp)
                .basicMarquee()
        )
    }
}

@Composable
private fun StatusFilterChips(
    selected: EventStatus?,
    onFilterSelected: (EventStatus?) -> Unit
) {
    val filters = listOf(
        null to "All",
        EventStatus.LIVE to "Live",
        EventStatus.UPCOMING to "Upcoming",
        EventStatus.RECENT to "Recent"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (status, label) ->
            val isSelected = selected == status
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onFilterSelected(status) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun LiveEventCard(
    event: LiveEvent,
    primaryColor: Color = Green,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .scale(scale)
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusable()
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                focusedElevation = 8.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Red)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 85.dp)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(event.leagueLogo)
                            .crossfade(true)
                            .fallback(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.league ?: "Unknown League",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val categoryLabel = event.category.ifEmpty {
                        event.eventCategoryName.ifEmpty { "Sports" }
                    }
                    Text(
                        text = categoryLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(Red, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamSection(
                        name = event.team1Name,
                        logoUrl = event.team1Logo,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    )
                    CenterSection(
                        event = event,
                        primaryColor = primaryColor
                    )
                    TeamSection(
                        name = event.team2Name,
                        logoUrl = event.team2Logo,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                }
            }
        }

        if (event.wrapper.isNotEmpty()) {
            WrapperBadge(
                text = event.wrapper,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun TeamSection(name: String, logoUrl: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .fallback(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CenterSection(event: LiveEvent, primaryColor: Color = Green) {
    val apiDateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    val startMillis = remember(event.startTime) {
        try { apiDateFormat.parse(event.startTime)?.time ?: 0L } catch (e: Exception) { 0L }
    }
    val endMillis = remember(event.endTime) {
        if (!event.endTime.isNullOrEmpty()) {
            try { apiDateFormat.parse(event.endTime)?.time ?: Long.MAX_VALUE } catch (e: Exception) { Long.MAX_VALUE }
        } else Long.MAX_VALUE
    }

    val isLiveNow = (currentTime >= startMillis && currentTime <= endMillis) || event.isLive
    val isUpcoming = !isLiveNow && currentTime < startMillis

    val lottieComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_animation))
    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComposition,
        iterations = LottieConstants.IterateForever,
        isPlaying = isLiveNow
    )

    Column(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isLiveNow -> {
                LottieAnimation(
                    composition = lottieComposition,
                    progress = { lottieProgress },
                    modifier = Modifier.size(width = 80.dp, height = 60.dp)
                )
                val elapsed = currentTime - startMillis
                val h = (elapsed / 1000 / 3600).toInt()
                val m = ((elapsed / 1000 / 60) % 60).toInt()
                val s = ((elapsed / 1000) % 60).toInt()
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format("%02d:%02d:%02d", h, m, s),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red
                )
            }
            isUpcoming -> {
                val startDate = try { apiDateFormat.parse(event.startTime) } catch (e: Exception) { null }
                if (startDate != null) {
                    Text(
                        text = timeFormat.format(startDate),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(startDate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val diff = startMillis - currentTime
                    val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                    val hours = ((diff / (1000 * 60 * 60)) % 24).toInt()
                    val minutes = ((diff / (1000 * 60)) % 60).toInt()
                    val seconds = ((diff / 1000) % 60).toInt()
                    val countdownText = when {
                        days > 0 -> String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
                        hours > 0 -> String.format("%02dh %02dm %02ds", hours, minutes, seconds)
                        minutes > 0 -> String.format("%02dm %02ds", minutes, seconds)
                        else -> String.format("%02ds", seconds)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = countdownText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
            else -> {
                val endDate = if (endMillis != Long.MAX_VALUE) {
                    try { apiDateFormat.parse(event.endTime!!) } catch (e: Exception) { null }
                } else {
                    try { apiDateFormat.parse(event.startTime) } catch (e: Exception) { null }
                }
                if (endDate != null) {
                    Text(
                        text = timeFormat.format(endDate),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(endDate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ended",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun WrapperBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 100.dp, height = 20.dp)
            .offset(x = (-38).dp)
            .graphicsLayer { rotationZ = 45f }
            .background(Red)
            .padding(start = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}
