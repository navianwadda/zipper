package com.livetvpro.app.ui.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.RippleAlpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.livetvpro.app.ui.player.dialogs.LinkItem
import com.livetvpro.app.ui.player.dialogs.LinkSelectionDialog
import com.livetvpro.app.ui.player.dialogs.toLinkItem
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
    val statusCounts by viewModel.statusCounts.observeAsState(StatusCounts())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isEmpty by viewModel.isEmpty.observeAsState(false)
    val primaryColor by viewModel.primaryColorFlow.collectAsState()
    var selectedStatusFilter by remember { mutableStateOf<EventStatus?>(null) }
    var selectedCategoryId by remember { mutableStateOf("evt_cat_all") }
    var linkDialogEvent by remember { mutableStateOf<LiveEvent?>(null) }
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadEventCategories()
        if (viewModel.filteredEvents.value == null) {
            viewModel.filterEvents(null, "evt_cat_all")
        } else {
            selectedStatusFilter = viewModel.pendingStatusFilter
            selectedCategoryId = viewModel.pendingCategoryId
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(selectedStatusFilter, selectedCategoryId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            while (true) {
                viewModel.filterEventsSilent(selectedStatusFilter, selectedCategoryId)
                delay(10_000)
            }
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
        LaunchedEffect(filteredEvents) {
            if (filteredEvents.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        }
    }

    if (linkDialogEvent != null) {
        val event = linkDialogEvent!!
        LinkSelectionDialog(
            links = event.links.map { it.toLinkItem() },
            onLinkSelected = { _, index ->
                onEventClick(event, index)
            },
            onDismiss = { linkDialogEvent = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (messageBannerText.isNotBlank()) {
            com.livetvpro.app.ui.components.MarqueeMessageBanner(
                text = messageBannerText,
                enabled = messageBannerUrl.isNotBlank(),
                onClick = {
                    if (messageBannerUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(messageBannerUrl)))
                    }
                },
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
            counts = statusCounts,
            onFilterSelected = { status ->
                selectedStatusFilter = status
                viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (!isTvDevice) isRefreshing = true },
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
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

private val CategoryTileSize = 64.dp
private val CategoryImagePadding = 2.dp
private val CategoryTileRadius = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
private val StrongWhiteRipple = RippleConfiguration(
    color = Color.White,
    rippleAlpha = RippleAlpha(
        pressedAlpha = 0.45f,
        focusedAlpha = 0.35f,
        draggedAlpha = 0.35f,
        hoveredAlpha = 0.25f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    category: EventCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val liveRed = MaterialTheme.colorScheme.error
    val tileColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val focusBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val tileShape = RoundedCornerShape(CategoryTileRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.08f else 1f,
        animationSpec = tween(120),
        label = "catScale"
    )

    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.35f else 0f,
        animationSpec = tween(if (isPressed) 60 else 350),
        label = "catPress"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides StrongWhiteRipple) {
        Box(
            modifier = Modifier
                .size(CategoryTileSize)
                .clip(tileShape)
                .background(tileColor)
                .then(
                    if (isSelected || hasFocus) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = if (isSelected) liveRed else focusBorder,
                            shape = tileShape
                        )
                    } else Modifier
                )
                .indication(interactionSource, ripple(color = Color.White))
        ) {
            val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
                androidx.compose.ui.platform.LocalContext.current
            )
            @OptIn(ExperimentalGlideComposeApi::class)
            GlideImage(
                model = category.logoUrl.takeIf { it.isNotBlank() },
                contentDescription = category.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CategoryImagePadding)
                    .clip(RoundedCornerShape(CategoryTileRadius - CategoryImagePadding))
            ) {
                it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(256, 256)
                    .placeholder(appIconRes)
                    .error(appIconRes)
                    .fallback(appIconRes)
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = pressAlpha))
            )
        }
        }
        Spacer(modifier = Modifier.height(2.dp))
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
                .width(72.dp)
                .basicMarquee()
        )
    }
}

@Composable
private fun StatusFilterChips(
    selected: EventStatus?,
    isTvDevice: Boolean,
    counts: StatusCounts,
    onFilterSelected: (EventStatus?) -> Unit
) {
    val liveRed = MaterialTheme.colorScheme.error
    val filters = listOf(
        null to ("All" to counts.all),
        EventStatus.LIVE to ("Live" to counts.live),
        EventStatus.UPCOMING to ("Upcoming" to counts.upcoming),
        EventStatus.RECENT to ("Recent" to counts.recent),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (status, labelAndCount) ->
            val (label, count) = labelAndCount
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
                        text = "$label ($count)",
                        fontFamily = BergenSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) liveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedLabelColor = liveRed
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    selectedBorderColor = liveRed,
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
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val liveRed = MaterialTheme.colorScheme.error
    val interactionSource = remember { MutableInteractionSource() }
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .scale(scale)
            .fillMaxWidth()
            .height(125.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasFocus) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
                        androidx.compose.ui.platform.LocalContext.current
                    )
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model = event.leagueLogo?.takeIf { it.isNotBlank() },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .override(96, 96)
                            .placeholder(appIconRes)
                            .error(appIconRes)
                            .fallback(appIconRes)
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

                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
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

            if (event.wrapper.isNotEmpty()) {
                WrapperBadge(
                    text = event.wrapper,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

@Composable
private fun TeamSection(name: String, logoUrl: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
            androidx.compose.ui.platform.LocalContext.current
        )
        @OptIn(ExperimentalGlideComposeApi::class)
        GlideImage(
            model = logoUrl?.takeIf { it.isNotBlank() },
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        ) {
            it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(96, 96)
                .placeholder(appIconRes)
                .error(appIconRes)
                .fallback(appIconRes)
        }
        Spacer(modifier = Modifier.height(3.dp))
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
private fun CenterSection(event: LiveEvent, primaryColor: Color = MaterialTheme.colorScheme.onSurface) {
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
    val cardLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(cardLifecycleOwner) {
        cardLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
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
        verticalArrangement = Arrangement.Top
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
                    modifier = Modifier.size(width = 56.dp, height = 42.dp)
                )
                val elapsed = currentTime - startMillis
                val h = (elapsed / 1000 / 3600).toInt()
                val m = ((elapsed / 1000 / 60) % 60).toInt()
                val s = ((elapsed / 1000) % 60).toInt()
                Spacer(modifier = Modifier.height(1.dp))
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
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.tertiary,
                RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
            color = MaterialTheme.colorScheme.onTertiary,
            maxLines = 1
        )
    }
}
