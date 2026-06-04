package com.livetvpro.app.ui.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.LiveEvent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

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

    var selectedStatusFilter by remember { mutableStateOf<EventStatus?>(EventStatus.LIVE) }
    var selectedCategoryId by remember { mutableStateOf("evt_cat_all") }
    var linkDialogEvent by remember { mutableStateOf<LiveEvent?>(null) }

    val gridState = rememberLazyGridState()
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

    if (isTvDevice) {
        LaunchedEffect(filteredEvents) {
            if (filteredEvents.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        }
    }

    if (linkDialogEvent != null) {
        val event = linkDialogEvent!!
        AlertDialog(
            onDismissRequest = { linkDialogEvent = null },
            title = {
                Text(
                    text = "Multiple Links Available",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = BergenSans
                )
            },
            text = {
                Column {
                    event.links.forEachIndexed { index, link ->
                        Text(
                            text = link.quality,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = BergenSans,
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
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { linkDialogEvent = null }) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = BergenSans
                    )
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
            isTvDevice = isTvDevice,
            onFilterSelected = { status ->
                selectedStatusFilter = status
                viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { if (!isTvDevice) viewModel.refresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && filteredEvents.isEmpty() -> {}
                    isEmpty || filteredEvents.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No events available",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = BergenSans,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            state = gridState,
                            contentPadding = PaddingValues(
                                top = 4.dp,
                                bottom = navBarBottom + dimensionResource(R.dimen.nav_bottom_margin) + dimensionResource(R.dimen.nav_height)
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredEvents, key = { it.id }) { event ->
                                LiveEventCard(
                                    event = event,
                                    primaryColor = Color(primaryColor),
                                    onClick = {
                                        if (event.links.isEmpty()) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No streams available for this event",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            return@LiveEventCard
                                        }
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
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MarqueeBanner(text: String, url: String, context: Context) {
    val bannerShape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp)
            .background(colorResource(R.color.message_banner_bg), bannerShape)
            .border(1.5.dp, colorResource(R.color.message_banner_stroke), bannerShape)
            .clickable(enabled = url.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 60.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = BergenSans,
            color = colorResource(R.color.message_banner_text),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
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
    val liveRed = MaterialTheme.colorScheme.error
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
                    width = 3.dp,
                    color = if (isSelected) liveRed else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        ) {
            @OptIn(ExperimentalGlideComposeApi::class)
            GlideImage(
                model = category.logoUrl,
                contentDescription = category.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            ) {
                it.diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.mipmap.ic_launcher_round)
                    .fallback(R.mipmap.ic_launcher_round)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
            color = if (isSelected) liveRed else MaterialTheme.colorScheme.onBackground,
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
    isTvDevice: Boolean,
    onFilterSelected: (EventStatus?) -> Unit
) {
    val liveRed = MaterialTheme.colorScheme.error
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
            var hasFocus by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isTvDevice && hasFocus) 1.08f else 1f,
                animationSpec = tween(100),
                label = "chipScale"
            )
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onFilterSelected(status) },
                label = {
                    Text(
                        text = label,
                        fontFamily = BergenSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = liveRed
                        )
                    }
                } else null,
                modifier = Modifier
                    .scale(scale)
                    .onFocusChanged { hasFocus = it.hasFocus },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colorResource(R.color.chip_background_normal),
                    labelColor = colorResource(R.color.chip_text_normal),
                    selectedContainerColor = colorResource(R.color.chip_background_selected),
                    selectedLabelColor = liveRed
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colorResource(R.color.chip_stroke_normal),
                    selectedBorderColor = colorResource(R.color.chip_stroke_selected),
                    borderWidth = 1.5.dp,
                    selectedBorderWidth = 1.5.dp
                )
            )
        }
    }
}

@Composable
fun LiveEventCard(
    event: LiveEvent,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val liveRed = MaterialTheme.colorScheme.error
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )
    val density = LocalDensity.current
    val elevationDp = with(density) { if (hasFocus) 8f.toDp() else 0f.toDp() }

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .scale(scale)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusable()
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                focusedElevation = elevationDp
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = liveRed.copy(alpha = 0.6f)
            )
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
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model = event.leagueLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .error(R.mipmap.ic_launcher_round)
                            .fallback(R.mipmap.ic_launcher_round)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.league ?: "Unknown League",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
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
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .background(liveRed, RoundedCornerShape(4.dp))
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
                    .offset(x = (-38).dp)
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
        @OptIn(ExperimentalGlideComposeApi::class)
        GlideImage(
            model = logoUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        ) {
            it.diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.mipmap.ic_launcher_round)
                .fallback(R.mipmap.ic_launcher_round)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CenterSection(event: LiveEvent, primaryColor: Color = MaterialTheme.colorScheme.primary) {
    val liveRed = MaterialTheme.colorScheme.error
    val endedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    val apiDateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeFormat = remember {
        SimpleDateFormat("hh:mm a", Locale.US).apply {
            val symbols = dateFormatSymbols
            symbols.amPmStrings = arrayOf("AM", "PM")
            dateFormatSymbols = symbols
        }
    }
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    val parseResult = remember(event.startTime, event.endTime) {
        try {
            val startDate = apiDateFormat.parse(event.startTime)
            val startMillis = startDate?.time ?: 0L
            val endMillis = if (!event.endTime.isNullOrEmpty()) {
                try { apiDateFormat.parse(event.endTime)?.time ?: Long.MAX_VALUE } catch (e: Exception) { Long.MAX_VALUE }
            } else Long.MAX_VALUE
            Triple(startDate, startMillis, endMillis)
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (parseResult == null) {
            Text(
                text = "Unknown",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = BergenSans,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            return@Column
        }

        val (startDate, startMillis, endMillis) = parseResult
        val isLiveNow = (currentTime >= startMillis && currentTime <= endMillis) || event.isLive
        val isUpcoming = !isLiveNow && currentTime < startMillis

        val lottieComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_animation))
        val lottieProgress by animateLottieCompositionAsState(
            composition = lottieComposition,
            iterations = LottieConstants.IterateForever,
            isPlaying = isLiveNow
        )

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
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BergenSans,
                    color = liveRed
                )
            }
            isUpcoming -> {
                if (startDate != null) {
                    Text(
                        text = timeFormat.format(startDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(startDate),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = BergenSans,
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
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
                        color = liveRed
                    )
                }
            }
            else -> {
                val endDate = if (endMillis != Long.MAX_VALUE) {
                    try { apiDateFormat.parse(event.endTime!!) } catch (e: Exception) { null }
                } else {
                    startDate
                }
                if (endDate != null) {
                    Text(
                        text = timeFormat.format(endDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BergenSans,
                        color = endedColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(endDate),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = BergenSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ended",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BergenSans,
                    color = endedColor
                )
            }
        }
    }
}

@Composable
private fun WrapperBadge(text: String, modifier: Modifier = Modifier) {
    val liveRed = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .size(width = 100.dp, height = 20.dp)
            .graphicsLayer { rotationZ = 45f }
            .background(liveRed)
            .padding(start = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1
        )
    }
}
