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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.databinding.FragmentLiveEventsBinding
import com.livetvpro.app.ui.adapters.EventCategoryAdapter
import com.livetvpro.app.ui.adapters.LiveEventAdapter
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RetryHandler
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LiveEventsFragment : Fragment(), SearchableFragment, Refreshable {
    private var _binding: FragmentLiveEventsBinding? = null
    private var pendingEventAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private val binding get() = _binding!!
    private val viewModel: LiveEventsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private val redirectLauncher =
        RedirectHelper.registerLauncher(
            fragment = this,
            cooldownMgr = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    @Inject lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager

    private var selectedCategoryId: String = "evt_cat_all"
    private var selectedStatusFilter: EventStatus? = null
    private var eventAdapter: LiveEventAdapter? = null
    private var categoryAdapter: EventCategoryAdapter? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            updateHandler.postDelayed(this, 10_000)
        }
    }

    override fun refreshData() {
        viewModel.refresh()
    }

    override fun onSearchQuery(query: String) {
        viewModel.searchEvents(query)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = getEventSpanCount()
        (binding.recyclerViewEvents.layoutManager as? GridLayoutManager)?.spanCount = spanCount
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoryRecycler()
        setupEventRecycler()
        setupStatusFilters()
        setupRetryHandling()
        observeViewModel()
        setupMessageBanner()
        if (com.livetvpro.app.utils.DeviceUtils.isTvDevice) {
            binding.swipeRefresh.isEnabled = false
            setupTvChipNavigation()
        }
        viewModel.loadEventCategories()
        if (viewModel.filteredEvents.value == null) {
            viewModel.filterEvents(null, "evt_cat_all")
        } else {
            selectedStatusFilter = viewModel.pendingStatusFilter
            selectedCategoryId = viewModel.pendingCategoryId
            restoreChipState()
        }
        startDynamicUpdates()
        observeInitialFocus()
    }

    private fun observeInitialFocus() {
        if (!com.livetvpro.app.utils.DeviceUtils.isTvDevice) return
        viewModel.filteredEvents.observe(viewLifecycleOwner) { events ->
            if (events.isNotEmpty()) {
                binding.recyclerViewEvents.post {
                    binding.recyclerViewEvents
                        .findViewHolderForAdapterPosition(0)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }
    }

    private fun setupTvChipNavigation() {
        val chips = listOf(binding.chipAll, binding.chipLive, binding.chipUpcoming, binding.chipRecent)
        chips.forEach { chip ->
            chip.isFocusable = true
            chip.isFocusableInTouchMode = false
            chip.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.08f else 1f).scaleY(if (hasFocus) 1.08f else 1f).setDuration(100).start()
            }
            chip.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        chip.performClick()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupMessageBanner() {
        val message = listenerManager.getMessage()
        if (message.isNotBlank()) {
            binding.tvMessageBanner.text = message
            binding.tvMessageBanner.visibility = View.VISIBLE
            binding.tvMessageBanner.isSelected = true
            val url = listenerManager.getMessageUrl()
            if (url.isNotBlank()) {
                binding.tvMessageBanner.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }

    private fun setupRetryHandling() {
        RetryHandler.setupGlobal(
            lifecycleOwner = viewLifecycleOwner,
            viewModel = viewModel,
            activity = requireActivity() as androidx.appcompat.app.AppCompatActivity,
            contentView = binding.recyclerViewEvents,
            swipeRefresh = binding.swipeRefresh,
            progressBar = binding.progressBar,
            emptyView = binding.emptyView
        )
    }

    private fun startDynamicUpdates() {
        updateHandler.removeCallbacks(updateRunnable)
        updateHandler.post(updateRunnable)
    }

    private fun stopDynamicUpdates() {
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun setupCategoryRecycler() {
        if (categoryAdapter == null) {
            categoryAdapter = EventCategoryAdapter { category ->
                selectedCategoryId = category.id
                viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
            }
        }
        if (binding.categoryRecycler.adapter == null) {
            binding.categoryRecycler.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = categoryAdapter
            }
        }
    }

    private fun getEventSpanCount(): Int {
        return resources.getInteger(R.integer.event_span_count)
    }

    private fun setupEventRecycler() {
        if (eventAdapter == null) {
            eventAdapter = LiveEventAdapter(
                context = requireContext(),
                events = emptyList(),
                preferencesManager = preferencesManager,
                onEventInteraction = { event, playerAction ->
                    lastPageType = ListenerConfig.PAGE_LIVE_EVENTS
                    lastUniqueId = event.id
                    val redirected = RedirectHelper.tryRedirect(
                        fragment    = this@LiveEventsFragment,
                        pageType    = ListenerConfig.PAGE_LIVE_EVENTS,
                        uniqueId    = event.id,
                        cooldownMgr = cooldownManager,
                        listenerMgr = listenerManager,
                        launcher    = redirectLauncher
                    )
                    if (redirected) {
                        if (!listenerManager.isInAppRedirectEnabled()) {
                            pendingEventAction = playerAction
                            pendingExternalRedirect = true
                        }
                    } else {
                        pendingEventAction = null
                    }
                    redirected
                }
            )
        }
        if (binding.recyclerViewEvents.adapter == null) {
            val spanCount = getEventSpanCount()
            binding.recyclerViewEvents.apply {
                layoutManager = GridLayoutManager(context, spanCount)
                adapter = eventAdapter
                itemAnimator = null
            }
        }
    }

    private fun showLinkSelectionDialog(event: LiveEvent) {
        val linkLabels = event.links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { dialog, which ->
                PlayerActivity.startWithEvent(requireContext(), event, which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun setupStatusFilters() {
        val clickListener = View.OnClickListener { view ->
            if ((view as Chip).isChecked) {
                val status = when (view.id) {
                    R.id.chip_all -> null
                    R.id.chip_live -> EventStatus.LIVE
                    R.id.chip_upcoming -> EventStatus.UPCOMING
                    R.id.chip_recent -> EventStatus.RECENT
                    else -> null
                }
                if (status != selectedStatusFilter) {
                    selectedStatusFilter = status
                    viewModel.filterEvents(selectedStatusFilter, selectedCategoryId)
                    updateChipSelection(view)
                }
            }
        }
        binding.chipAll.setOnClickListener(clickListener)
        binding.chipLive.setOnClickListener(clickListener)
        binding.chipUpcoming.setOnClickListener(clickListener)
        binding.chipRecent.setOnClickListener(clickListener)
        binding.chipAll.isChecked = true
        selectedStatusFilter = null
    }

    private fun updateChipSelection(selectedChip: Chip) {
        listOf(binding.chipAll, binding.chipLive, binding.chipUpcoming, binding.chipRecent).forEach {
            it.isChecked = (it == selectedChip)
        }
    }

    private fun observeViewModel() {
        viewModel.eventCategories.observe(viewLifecycleOwner) { categories ->
            binding.categoryRecycler.visibility = View.VISIBLE
            categoryAdapter?.submitList(categories)
        }
        viewModel.filteredEvents.observe(viewLifecycleOwner) { events ->
            eventAdapter?.updateData(events)
            binding.recyclerViewEvents.scrollToPosition(0)
        }
    }

    private fun restoreChipState() {
        val chipToCheck = when (selectedStatusFilter) {
            EventStatus.LIVE -> binding.chipLive
            EventStatus.UPCOMING -> binding.chipUpcoming
            EventStatus.RECENT -> binding.chipRecent
            null -> binding.chipAll
        }
        listOf(binding.chipAll, binding.chipLive, binding.chipUpcoming, binding.chipRecent).forEach {
            it.isChecked = (it == chipToCheck)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val rvState = _binding?.recyclerViewEvents?.layoutManager?.onSaveInstanceState()
        val catState = _binding?.categoryRecycler?.layoutManager?.onSaveInstanceState()
        rvState?.let { outState.putParcelable("rv_events_state", it) }
        catState?.let { outState.putParcelable("rv_cat_state", it) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_events_state")?.let {
            binding.recyclerViewEvents.layoutManager?.onRestoreInstanceState(it)
        }
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_cat_state")?.let {
            binding.categoryRecycler.layoutManager?.onRestoreInstanceState(it)
        }
    }

    override fun onResume() {
        super.onResume()
        startDynamicUpdates()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider = { pendingEventAction },
            clearPendingAction = { pendingEventAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect = { pendingExternalRedirect = false }
        )
    }

    override fun onPause() {
        super.onPause()
        stopDynamicUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopDynamicUpdates()
        _binding = null
    }
}
