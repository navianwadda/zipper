package com.livetvpro.app.ui.categories

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.databinding.FragmentCategoryChannelsBinding
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.ui.adapters.CategoryGroupDialogAdapter
import com.livetvpro.app.ui.adapters.ChannelAdapter
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RetryHandler
import com.livetvpro.app.utils.Refreshable
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.utils.FloatingPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CategoryChannelsFragment : Fragment(), SearchableFragment, Refreshable {
    private var _binding: FragmentCategoryChannelsBinding? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private val binding get() = _binding!!
    private val viewModel: CategoryChannelsViewModel by viewModels()
    private lateinit var channelAdapter: ChannelAdapter

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private val redirectLauncher by lazy {
        RedirectHelper.registerLauncher(
            fragment = this,
            cooldownMgr = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    }
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    @Inject lateinit var preferencesManager: PreferencesManager

    private var currentCategoryId: String? = null
    private var numpadBuffer = ""
    private val numpadHandler = Handler(Looper.getMainLooper())
    private val numpadResetRunnable = Runnable {
        numpadBuffer = ""
        viewModel.searchChannels("")
    }
    private val NUMPAD_RESET_MS = 2000L

    override fun onSearchQuery(query: String) { viewModel.searchChannels(query) }
    override fun refreshData() { currentCategoryId?.let { viewModel.loadChannels(it) } }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        (binding.recyclerViewChannels.layoutManager as? GridLayoutManager)?.spanCount = columnCount
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentCategoryId = arguments?.getString("categoryId")
        try {
            val toolbarTitle = requireActivity().findViewById<TextView>(R.id.toolbar_title)
            if (viewModel.categoryName.isNotEmpty()) toolbarTitle?.text = viewModel.categoryName
        } catch (_: Exception) {}
        setupTabLayout()
        setupRecyclerView()
        setupRetryHandling()
        observeViewModel()
        binding.groupsIcon.setOnClickListener { showGroupsDialog() }
        if (DeviceUtils.isTvDevice) {
            binding.swipeRefresh.isEnabled = false
            binding.groupsIcon.isFocusable = true
            binding.groupsIcon.isFocusableInTouchMode = false
            binding.groupsIcon.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        showGroupsDialog()
                        true
                    }
                    else -> false
                }
            }
        } else {
            binding.swipeRefresh.isEnabled = true
        }
        if (viewModel.filteredChannels.value.isNullOrEmpty()) {
            currentCategoryId?.let { viewModel.loadChannels(it) }
        }
        if (DeviceUtils.isTvDevice) {
            setupTvNumpadSearch()
        }
    }

    private fun setupTvNumpadSearch() {
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val digit: String? = when (keyCode) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
                KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
                KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
                else -> null
            }
            if (digit != null) {
                numpadBuffer += digit
                numpadHandler.removeCallbacks(numpadResetRunnable)
                numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                viewModel.searchChannels(numpadBuffer)
                binding.recyclerViewChannels.requestFocus()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL && numpadBuffer.isNotEmpty()) {
                numpadBuffer = numpadBuffer.dropLast(1)
                numpadHandler.removeCallbacks(numpadResetRunnable)
                if (numpadBuffer.isEmpty()) {
                    viewModel.searchChannels("")
                } else {
                    viewModel.searchChannels(numpadBuffer)
                    numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter(
            onChannelClick = { channel ->
                pendingChannelAction = if (channel.links != null && channel.links.isNotEmpty() && channel.links.size > 1) {
                    { showLinkSelectionDialog(channel) }
                } else {
                    { launchPlayer(channel, -1) }
                }
                lastPageType = ListenerConfig.PAGE_CHANNELS
                lastUniqueId = channel.id
                val redirected = RedirectHelper.tryRedirect(
                    fragment    = this@CategoryChannelsFragment,
                    pageType    = ListenerConfig.PAGE_CHANNELS,
                    uniqueId    = channel.id,
                    cooldownMgr = cooldownManager,
                    listenerMgr = listenerManager,
                    launcher    = redirectLauncher
                )
                if (redirected) {
                    if (!listenerManager.isInAppRedirectEnabled()) {
                        pendingExternalRedirect = true
                    } else {
                        pendingChannelAction = null
                    }
                } else {
                    pendingChannelAction?.invoke()
                    pendingChannelAction = null
                }
            },
            onFavoriteToggle = { channel ->
                viewModel.toggleFavorite(channel)
                lifecycleScope.launch {
                    delay(50)
                    if (_binding != null) channelAdapter.refreshItem(channel.id)
                }
            },
            isFavorite = { channelId -> viewModel.isFavorite(channelId) }
        )
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        binding.recyclerViewChannels.apply {
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = channelAdapter
            itemAnimator = null
            if (DeviceUtils.isTvDevice) {
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                isFocusable = true
                isFocusableInTouchMode = false
            }
        }
    }

    private fun setupRetryHandling() {
        RetryHandler.setupGlobal(
            lifecycleOwner = viewLifecycleOwner,
            viewModel = viewModel,
            activity = requireActivity() as androidx.appcompat.app.AppCompatActivity,
            contentView = binding.swipeRefresh,
            swipeRefresh = binding.swipeRefresh,
            progressBar = binding.progressBar,
            emptyView = binding.emptyView
        )
    }

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId = currentCategoryId,
                selectedGroup = viewModel.currentGroup.value
            )
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    categoryId = currentCategoryId,
                    selectedGroup = viewModel.currentGroup.value
                )
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(requireContext(), channel, linkIndex)
            } catch (_: Exception) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    categoryId = currentCategoryId,
                    selectedGroup = viewModel.currentGroup.value
                )
            }
        } else {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId = currentCategoryId,
                selectedGroup = viewModel.currentGroup.value
            )
        }
    }

    private fun showLinkSelectionDialog(channel: Channel) {
        val links = channel.links ?: return
        val linkLabels = links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { dialog, which ->
                launchPlayer(channel, which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun showGroupsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_groups, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_view_groups)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_group)
        val clearSearchBtn = dialogView.findViewById<ImageView>(R.id.clear_search_button)
        val closeButton = dialogView.findViewById<ImageView>(R.id.close_button)
        searchEditText.typeface = resources.getFont(R.font.bergen_sans)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        val allGroups = viewModel.categoryGroups.value ?: emptyList()
        var filteredGroups = allGroups.toList()

        val dialogAdapter = CategoryGroupDialogAdapter { groupName ->
            viewModel.selectGroup(groupName)
            val tabIndex = allGroups.indexOf(groupName)
            if (tabIndex >= 0 && tabIndex < binding.tabLayoutGroups.tabCount) {
                binding.tabLayoutGroups.getTabAt(tabIndex)?.select()
            }
            dialog.dismiss()
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = dialogAdapter
        }
        dialogAdapter.submitList(filteredGroups)

        if (DeviceUtils.isTvDevice) {
            searchEditText.isFocusable = false
            searchEditText.isFocusableInTouchMode = false
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            dialog.setOnShowListener {
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                recyclerView.requestFocus()
            }
        } else {
            searchEditText.setOnFocusChangeListener { _, hasFocus ->
                clearSearchBtn.visibility = if (hasFocus) View.VISIBLE else View.GONE
            }
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    filteredGroups = if (query.isEmpty()) allGroups
                    else allGroups.filter { it.contains(query, ignoreCase = true) }
                    dialogAdapter.submitList(filteredGroups)
                }
            })
            clearSearchBtn.setOnClickListener {
                searchEditText.text.clear()
                searchEditText.clearFocus()
            }
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.filteredChannels.observe(viewLifecycleOwner) { channels ->
            channelAdapter.submitList(channels)
            if (viewModel.isLoading.value != true && viewModel.error.value == null) {
                binding.emptyView.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerViewChannels.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
            }
            if (DeviceUtils.isTvDevice && channels.isNotEmpty()) {
                binding.recyclerViewChannels.post {
                    binding.recyclerViewChannels
                        .findViewHolderForAdapterPosition(0)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }
        viewModel.categoryGroups.observe(viewLifecycleOwner) { groups ->
            val hasGroups = groups.isNotEmpty()
            binding.groupsHeader.visibility = if (hasGroups) View.VISIBLE else View.GONE
            binding.headerDivider.visibility = if (hasGroups) View.VISIBLE else View.GONE
            if (hasGroups) updateTabs(groups)
            else binding.tabLayoutGroups.removeAllTabs()
        }
        viewModel.currentGroup.observe(viewLifecycleOwner) { selectedGroup ->
            val groups = viewModel.categoryGroups.value ?: emptyList()
            val tabIndex = groups.indexOf(selectedGroup)
            if (tabIndex >= 0 && tabIndex < binding.tabLayoutGroups.tabCount) {
                binding.tabLayoutGroups.getTabAt(tabIndex)?.select()
            }
        }
    }

    private var suppressTabListener = false

    private fun setupTabLayout() {
        binding.tabLayoutGroups.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (suppressTabListener) return
                tab?.text?.toString()?.let { groupName -> viewModel.selectGroup(groupName) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabs(groups: List<String>) {
        suppressTabListener = true
        binding.tabLayoutGroups.removeAllTabs()
        groups.forEach { groupName ->
            binding.tabLayoutGroups.addTab(binding.tabLayoutGroups.newTab().setText(groupName))
        }
        val currentGroup = viewModel.currentGroup.value ?: "All"
        val restoreIndex = groups.indexOf(currentGroup).coerceAtLeast(0)
        if (binding.tabLayoutGroups.tabCount > 0) binding.tabLayoutGroups.getTabAt(restoreIndex)?.select()
        suppressTabListener = false
        binding.tabLayoutGroups.post {
            for (i in 0 until binding.tabLayoutGroups.tabCount) {
                val tab = binding.tabLayoutGroups.getTabAt(i)
                val tabView = tab?.view
                val tabTextView = tabView?.findViewById<TextView>(android.R.id.text1)
                tabTextView?.typeface = resources.getFont(R.font.bergen_sans)
                if (DeviceUtils.isTvDevice && tabView != null) {
                    tabView.isFocusable = true
                    tabView.isFocusableInTouchMode = false
                    tabView.setOnFocusChangeListener { v, hasFocus ->
                        v.animate().scaleX(if (hasFocus) 1.05f else 1f)
                            .scaleY(if (hasFocus) 1.05f else 1f).setDuration(100).start()
                    }
                    tabView.setOnKeyListener { _, keyCode, event ->
                        if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                tabView.performClick()
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider = { pendingChannelAction },
            clearPendingAction = { pendingChannelAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect = { pendingExternalRedirect = false }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = _binding?.recyclerViewChannels?.layoutManager as? GridLayoutManager
        layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("rv_channels_state", it) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_channels_state")?.let {
            binding.recyclerViewChannels.layoutManager?.onRestoreInstanceState(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        numpadHandler.removeCallbacksAndMessages(null)
        viewModel.dismissError()
        _binding = null
    }
}
