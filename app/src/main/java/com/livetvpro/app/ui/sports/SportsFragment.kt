package com.livetvpro.app.ui.sports

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.databinding.FragmentSportsBinding
import com.livetvpro.app.ui.adapters.ChannelAdapter
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import com.livetvpro.app.utils.RetryHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SportsFragment : Fragment(), SearchableFragment, Refreshable {
    private var _binding: FragmentSportsBinding? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private val binding get() = _binding!!
    private val viewModel: SportsViewModel by viewModels()
    private lateinit var channelAdapter: ChannelAdapter

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    @Inject lateinit var preferencesManager: PreferencesManager

    private var savedScrollState: android.os.Parcelable? = null
    private var lastSubmittedChannels: List<com.livetvpro.app.data.models.Channel>? = null

    override fun onSearchQuery(query: String) {
        if (query.isBlank() && viewModel.currentQuery.isNotBlank()) {
            savedScrollState = binding.recyclerViewChannels.layoutManager?.onSaveInstanceState()
        } else if (query.isNotBlank() && viewModel.currentQuery.isBlank()) {
            savedScrollState = binding.recyclerViewChannels.layoutManager?.onSaveInstanceState()
        }
        viewModel.searchSports(query)
    }
    override fun refreshData() { viewModel.refresh() }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
        (binding.recyclerViewChannels.layoutManager as? GridLayoutManager)?.spanCount = columnCount
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewChannels) { v, insets ->
            val navBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val bottom = navBars.bottom + v.resources.getDimensionPixelSize(com.livetvpro.app.R.dimen.nav_bottom_margin) + v.resources.getDimensionPixelSize(com.livetvpro.app.R.dimen.nav_height)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }
        setupRecyclerView()
        setupRetryHandling()
        if (DeviceUtils.isTvDevice) {
            binding.swipeRefresh.isEnabled = false
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
        layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("rv_sports_state", it) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_sports_state")?.let {
            binding.recyclerViewChannels.layoutManager?.onRestoreInstanceState(it)
        }
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter(
            onChannelClick = { channel ->
                pendingChannelAction = if (channel.links != null && channel.links.size > 1) {
                    { showLinkSelectionDialog(channel) }
                } else {
                    { launchPlayer(channel, -1) }
                }
                lastPageType = ListenerConfig.PAGE_SPORTS
                lastUniqueId = channel.id
                val result = RedirectHelper.tryRedirect(
                    fragment    = this@SportsFragment,
                    pageType    = ListenerConfig.PAGE_SPORTS,
                    uniqueId    = channel.id,
                    cooldownMgr = cooldownManager,
                    listenerMgr = listenerManager,
                    launcher    = redirectLauncher
                )
                if (result == RedirectHelper.RedirectResult.REDIRECTED) {
                    if (!listenerManager.isInAppRedirectEnabled()) {
                        pendingExternalRedirect = true
                    } else {
                        pendingChannelAction = null
                    }
                } else if (result == RedirectHelper.RedirectResult.NOT_REDIRECTED) {
                    pendingChannelAction?.invoke()
                    pendingChannelAction = null
                } else {
                    pendingChannelAction = null
                }
                MainScope().launch {
                    delay(50)
                    if (_binding != null) channelAdapter.refreshItem(channel.id)
                }
            },
            onFavoriteToggle = { channel ->
                viewModel.toggleFavorite(channel)
                MainScope().launch {
                    delay(50)
                    if (_binding != null) channelAdapter.refreshItem(channel.id)
                }
            },
            isFavorite = { channelId -> viewModel.isFavorite(channelId) }
        )
        val spanCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
        binding.recyclerViewChannels.apply {
            layoutManager = GridLayoutManager(context, spanCount)
            adapter = channelAdapter
            setHasFixedSize(true)
        }
    }

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        val cacheKey = "sports_${channel.id}"
        val channelList = channelAdapter.snapshot().items
        if (channelList.isNotEmpty()) ChannelListCache.put(cacheKey, channelList)
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            } catch (e: Exception) {
                PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            }
        } else {
            PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
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
        viewModel.filteredChannels.observe(viewLifecycleOwner) { channels ->
            val restoreState = if (viewModel.currentQuery.isBlank()) savedScrollState else null
            if (channels !== lastSubmittedChannels) {
                lastSubmittedChannels = channels
                lifecycleScope.launch {
                    channelAdapter.submitData(PagingData.from(channels))
                    if (restoreState != null) {
                        binding.recyclerViewChannels.layoutManager?.onRestoreInstanceState(restoreState)
                        savedScrollState = null
                    }
                }
            } else if (restoreState != null) {
                binding.recyclerViewChannels.layoutManager?.onRestoreInstanceState(restoreState)
                savedScrollState = null
            }
            if (viewModel.isLoading.value != true && viewModel.error.value == null && channelAdapter.itemCount == 0) {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lastSubmittedChannels = null
        _binding = null
    }
}
