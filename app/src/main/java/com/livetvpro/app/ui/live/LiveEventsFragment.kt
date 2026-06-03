package com.livetvpro.app.ui.live

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class LiveEventsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: LiveEventsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    private var searchQuery = mutableStateOf("")
    private var pendingEventAction: (() -> Unit)? = null
    private var pendingExternalRedirect = false

    override fun refreshData() { viewModel.refresh() }

    override fun onSearchQuery(query: String) {
        searchQuery.value = query
        viewModel.searchEvents(query)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redirectLauncher = RedirectHelper.registerLauncher(
            fragment = this,
            cooldownMgr = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            LiveTVProTheme(themeManager) {
                LiveEventsScreen(
                    viewModel          = viewModel,
                    listenerManager    = listenerManager,
                    onEventClick       = { event -> launchEvent(event) },
                    onRefresh          = { viewModel.refresh() },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadEventCategories()
        if (viewModel.filteredEvents.value == null) {
            viewModel.filterEvents(null, "evt_cat_all")
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider  = { pendingEventAction },
            clearPendingAction     = { pendingEventAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect   = { pendingExternalRedirect = false }
        )
    }

    private fun launchEvent(event: LiveEvent) {
        if (event.links.isEmpty()) {
            android.widget.Toast.makeText(
                requireContext(), "No streams available", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val playerAction: () -> Unit = if (event.links.size > 1) {
            { }
        } else {
            { proceedWithPlayer(event, 0) }
        }

        lastPageType = ListenerConfig.PAGE_LIVE_EVENTS
        lastUniqueId = event.id

        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_LIVE_EVENTS,
            uniqueId    = event.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )

        if (result == RedirectHelper.RedirectResult.REDIRECTED) {
            if (!listenerManager.isInAppRedirectEnabled()) {
                pendingEventAction = playerAction
                pendingExternalRedirect = true
            }
        } else {
            playerAction()
        }
    }

    private fun proceedWithPlayer(event: LiveEvent, linkIndex: Int) {
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission   = FloatingPlayerHelper.hasOverlayPermission(requireContext())

        if (floatingEnabled && hasPermission) {
            try {
                val channel = Channel(
                    id           = event.id,
                    name         = "${event.team1Name} vs ${event.team2Name}",
                    logoUrl      = event.leagueLogo.ifEmpty { event.team1Logo },
                    categoryName = event.category,
                    links        = event.links.map { l ->
                        ChannelLink(
                            quality        = l.quality,
                            url            = l.url,
                            cookie         = l.cookie,
                            referer        = l.referer,
                            origin         = l.origin,
                            userAgent      = l.userAgent,
                            xForwardedFor  = l.xForwardedFor,
                            drmScheme      = l.drmScheme,
                            drmLicenseUrl  = l.drmLicenseUrl
                        )
                    }
                )
                FloatingPlayerHelper.launchFloatingPlayerWithEvent(requireContext(), channel, event, linkIndex)
            } catch (e: Exception) {
                PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
            }
        } else {
            PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEventsScreen(
    viewModel:       LiveEventsViewModel,
    listenerManager: NativeListenerManager,
    onEventClick:    (LiveEvent) -> Unit,
    onRefresh:       () -> Unit,
) {
    val events      by viewModel.filteredEvents.observeAsState(emptyList())
    val categories  by viewModel.eventCategories.observeAsState(emptyList())
    val isLoading   by viewModel.isLoading.observeAsState(false)
    val error       by viewModel.error.observeAsState(null)
    val isEmpty     by viewModel.isEmpty.observeAsState(false)

    var selectedStatus   by remember { mutableStateOf<EventStatus?>(null) }
    var selectedCategory by remember { mutableStateOf("evt_cat_all") }
    var linkDialogEvent  by remember { mutableStateOf<LiveEvent?>(null) }

    val message    = remember { listenerManager.getMessage() }
    val messageUrl = remember { listenerManager.getMessageUrl() }
    val context    = LocalContext.current

    linkDialogEvent?.let { event ->
        LinkSelectionDialog(
            event   = event,
            onSelect = { index ->
                linkDialogEvent = null
                onEventClick(event)
            },
            onDismiss = { linkDialogEvent = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (message.isNotBlank()) {
            MarqueeBanner(
                text     = message,
                onClick  = if (messageUrl.isNotBlank()) {
                    { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(messageUrl))) }
                } else null,
            )
        }

        if (categories.isNotEmpty()) {
            CategoryRow(
                categories       = categories,
                selectedCategory = selectedCategory,
                onCategorySelect = { id ->
                    selectedCategory = id
                    viewModel.filterEvents(selectedStatus, id)
                }
            )
        }

        StatusFilterRow(
            selectedStatus = selectedStatus,
            onStatusSelect = { status ->
                selectedStatus = status
                viewModel.filterEvents(status, selectedCategory)
            }
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh    = onRefresh,
            modifier     = Modifier.fillMaxSize(),
        ) {
            when {
                error != null -> {
                    ErrorView(
                        message = error ?: "Error",
                        onRetry = { viewModel.retry() }
                    )
                }
                isLoading && events.isNullOrEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                isEmpty || events.isNullOrEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = "No events available",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns             = GridCells.Fixed(1),
                        contentPadding      = PaddingValues(bottom = 90.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier            = Modifier.fillMaxSize(),
                    ) {
                        items(events ?: emptyList(), key = { it.id }) { event ->
                            LiveEventCard(
                                event   = event,
                                onClick = {
                                    if (event.links.size > 1) linkDialogEvent = event
                                    else onEventClick(event)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarqueeBanner(text: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text     = text,
            color    = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryRow(
    categories:       List<EventCategory>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
) {
    val allCategory = EventCategory(id = "evt_cat_all", name = "All")
    val seen        = mutableSetOf("evt_cat_all")
    val allItems    = listOf(allCategory) + categories.filter { seen.add(it.id) }

    LazyRow(
        contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(allItems, key = { it.id }) { category ->
            val isSelected = category.id == selectedCategory
            CategoryChip(
                category   = category,
                isSelected = isSelected,
                onClick    = { onCategorySelect(category.id) }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CategoryChip(
    category:   EventCategory,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (category.logoUrl.isNotBlank()) {
                GlideImage(
                    model             = category.logoUrl,
                    contentDescription = category.name,
                    contentScale      = ContentScale.Fit,
                    modifier          = Modifier.size(48.dp),
                    failure           = placeholder(R.mipmap.ic_launcher_round),
                )
            } else {
                Text(
                    text      = category.name.take(2).uppercase(),
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize  = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text       = category.name,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface,
            fontSize   = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.width(70.dp),
        )
    }
}

@Composable
private fun StatusFilterRow(
    selectedStatus: EventStatus?,
    onStatusSelect: (EventStatus?) -> Unit,
) {
    val filters = listOf(
        null            to "All",
        EventStatus.LIVE     to "Live",
        EventStatus.UPCOMING to "Upcoming",
        EventStatus.RECENT   to "Recent",
    )
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(filters) { (status, label) ->
            FilterChip(
                selected = selectedStatus == status,
                onClick  = { onStatusSelect(status) },
                label    = { Text(label) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor  = MaterialTheme.colorScheme.primary,
                    selectedLabelColor      = Color.White,
                ),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LiveEventCard(event: LiveEvent, onClick: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(event.id) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() { tick++; handler.postDelayed(this, 1000) }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacks(runnable) }
    }

    val currentTime  = System.currentTimeMillis()
    val timerState   = remember(tick, event) { computeTimerState(event, currentTime) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val liveColor    = Color(0xFFEF4444)
    val endedColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, liveColor),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.weight(1f),
                    ) {
                        GlideImage(
                            model             = event.leagueLogo,
                            contentDescription = event.league,
                            contentScale      = ContentScale.Fit,
                            modifier          = Modifier.size(28.dp),
                            failure           = placeholder(R.mipmap.ic_launcher_round),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = event.league ?: "Unknown League",
                            color      = MaterialTheme.colorScheme.onSurface,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f),
                        )
                    }
                    val tagText = event.category.ifEmpty {
                        event.eventCategoryName.ifEmpty { "Sports" }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text       = tagText,
                            color      = MaterialTheme.colorScheme.primary,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(5.dp))

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min),
                ) {
                    TeamSection(
                        name    = event.team1Name,
                        logoUrl = event.team1Logo,
                        modifier = Modifier.weight(1f),
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier            = Modifier
                            .width(100.dp)
                            .padding(horizontal = 4.dp),
                    ) {
                        when (timerState) {
                            is TimerState.Live -> {
                                LivePulse()
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text       = timerState.elapsed,
                                    color      = liveColor,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                )
                            }
                            is TimerState.Upcoming -> {
                                Text(
                                    text       = timerState.time,
                                    color      = primaryColor,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                )
                                Text(
                                    text      = timerState.date,
                                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize  = 11.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text       = timerState.countdown,
                                    color      = primaryColor,
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                )
                            }
                            is TimerState.Ended -> {
                                Text(
                                    text       = timerState.time,
                                    color      = endedColor,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                )
                                Text(
                                    text      = timerState.date,
                                    color     = endedColor,
                                    fontSize  = 11.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text       = "Ended",
                                    color      = endedColor,
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                )
                            }
                            TimerState.Unknown -> {
                                Text(
                                    text      = "Unknown",
                                    color     = endedColor,
                                    fontSize  = 11.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    TeamSection(
                        name     = event.team2Name,
                        logoUrl  = event.team2Logo,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (event.wrapper.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text       = event.wrapper,
                        color      = Color.White,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TeamSection(name: String, logoUrl: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier,
    ) {
        GlideImage(
            model             = logoUrl,
            contentDescription = name,
            contentScale      = ContentScale.Fit,
            modifier          = Modifier
                .size(48.dp)
                .clip(CircleShape),
            failure           = placeholder(R.mipmap.ic_launcher_round),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = name,
            color      = MaterialTheme.colorScheme.onSurface,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LivePulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 0.8f,
        targetValue    = 1.2f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size((8 * scale).dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text       = "LIVE",
            color      = Color(0xFFEF4444),
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LinkSelectionDialog(
    event:     LiveEvent,
    onSelect:  (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Multiple Links Available") },
        text             = {
            Column {
                event.links.forEachIndexed { index, link ->
                    Text(
                        text     = link.quality.ifEmpty { "Link ${index + 1}" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        color    = MaterialTheme.colorScheme.onSurface,
                        style    = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton    = {},
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private sealed class TimerState {
    data class Live(val elapsed: String) : TimerState()
    data class Upcoming(val time: String, val date: String, val countdown: String) : TimerState()
    data class Ended(val time: String, val date: String) : TimerState()
    data object Unknown : TimerState()
}

private fun computeTimerState(event: LiveEvent, currentTime: Long): TimerState {
    val apiFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
    return try {
        val startDate       = apiFormat.parse(event.startTime) ?: return TimerState.Unknown
        val startMillis     = startDate.time
        val endMillis       = event.endTime?.let {
            try { apiFormat.parse(it)?.time } catch (e: Exception) { null }
        } ?: Long.MAX_VALUE

        when {
            (currentTime >= startMillis && currentTime <= endMillis) || event.isLive -> {
                val elapsedMs = currentTime - startMillis
                val h = elapsedMs / 1000 / 3600
                val m = (elapsedMs / 1000 / 60) % 60
                val s = (elapsedMs / 1000) % 60
                TimerState.Live(String.format("%02d:%02d:%02d", h, m, s))
            }
            currentTime < startMillis -> {
                val diff = startMillis - currentTime
                val days = diff / (1000 * 60 * 60 * 24)
                val h    = (diff / (1000 * 60 * 60)) % 24
                val m    = (diff / (1000 * 60)) % 60
                val s    = (diff / 1000) % 60
                val countdown = when {
                    days > 0 -> String.format("%dd %02dh %02dm %02ds", days, h, m, s)
                    h > 0    -> String.format("%02dh %02dm %02ds", h, m, s)
                    m > 0    -> String.format("%02dm %02ds", m, s)
                    else     -> String.format("%02ds", s)
                }
                TimerState.Upcoming(
                    time      = timeFormat.format(startDate),
                    date      = dateFormat.format(startDate),
                    countdown = countdown,
                )
            }
            else -> {
                val endDate = if (endMillis != Long.MAX_VALUE && event.endTime != null) {
                    try { apiFormat.parse(event.endTime) } catch (e: Exception) { startDate }
                } else startDate
                TimerState.Ended(
                    time = timeFormat.format(endDate ?: startDate),
                    date = dateFormat.format(endDate ?: startDate),
                )
            }
        }
    } catch (e: Exception) {
        TimerState.Unknown
    }
}
