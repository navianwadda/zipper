package com.livetvpro.app.ui.live

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.ui.adapters.EventCategoryChip
import com.livetvpro.app.ui.adapters.LiveEventCard
import com.livetvpro.app.ui.adapters.MarqueeBanner
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LiveEventsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: LiveEventsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private var pendingEventAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>

    private val updateHandler  = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            viewModel.filterEvents(viewModel.pendingStatusFilter, viewModel.pendingCategoryId)
            updateHandler.postDelayed(this, 10_000)
        }
    }

    override fun refreshData() { viewModel.refresh() }
    override fun onSearchQuery(query: String) { viewModel.searchEvents(query) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redirectLauncher = RedirectHelper.registerLauncher(
            fragment         = this,
            cooldownMgr      = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                LiveEventsScreen(
                    viewModel          = viewModel,
                    preferencesManager = preferencesManager,
                    onEventInteraction = { event, playerAction -> handleEventInteraction(event, playerAction) },
                    onBannerClick      = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateHandler.post(updateRunnable)
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider   = { pendingEventAction },
            clearPendingAction      = { pendingEventAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect    = { pendingExternalRedirect = false }
        )
    }

    override fun onPause() {
        super.onPause()
        updateHandler.removeCallbacks(updateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun handleEventInteraction(event: LiveEvent, playerAction: () -> Unit) {
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
                pendingEventAction      = playerAction
                pendingExternalRedirect = true
            }
        } else {
            pendingEventAction = null
        }
    }

    fun showLinkSelectionDialog(event: LiveEvent) {
        val linkLabels = event.links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { d, which ->
                PlayerActivity.startWithEvent(requireContext(), event, which)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }
}

@Composable
fun LiveEventsScreen(
    viewModel: LiveEventsViewModel,
    preferencesManager: PreferencesManager,
    onEventInteraction: (LiveEvent, () -> Unit) -> Unit,
    onBannerClick: (String) -> Unit,
) {
    val events     by viewModel.filteredEvents.observeAsState(emptyList())
    val categories by viewModel.eventCategories.observeAsState(emptyList())
    val isLoading  by viewModel.isLoading.observeAsState(false)

    var selectedStatusFilter by remember { mutableStateOf<EventStatus?>(null) }
    var selectedCategoryId   by remember { mutableStateOf("evt_cat_all") }

    val context = LocalContext.current
    val spanCount = context.resources.getInteger(R.integer.event_span_count)

    LaunchedEffect(Unit) {
        viewModel.loadEventCategories()
    }

    LaunchedEffect(selectedStatusFilter, selectedCategoryId) {
        viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            LazyRow(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                items(categories) { category ->
                    EventCategoryChip(
                        category   = category,
                        isSelected = category.id == selectedCategoryId,
                        onClick    = { selectedCategoryId = category.id },
                    )
                }
            }
        }

        LazyRow(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            val filters = listOf(
                null                 to "All",
                EventStatus.LIVE     to "Live",
                EventStatus.UPCOMING to "Upcoming",
                EventStatus.RECENT   to "Recent",
            )
            items(filters) { (status, label) ->
                FilterChip(
                    selected = selectedStatusFilter == status,
                    onClick  = { selectedStatusFilter = status },
                    label    = { Text(label) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                events.isEmpty() -> Text(
                    text     = "No events available",
                    modifier = Modifier.align(Alignment.Center),
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns  = GridCells.Fixed(spanCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 90.dp),
                ) {
                    items(events) { event ->
                        LiveEventCard(
                            event              = event,
                            preferencesManager = preferencesManager,
                            onInteraction      = { playerAction -> onEventInteraction(event, playerAction) },
                        )
                    }
                }
            }
        }
    }
}
