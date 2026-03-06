package com.livetvpro.app.ui.sports

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.data.repository.CategoryRepository
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.databinding.FragmentSportsBinding
import com.livetvpro.app.ui.adapters.ChannelAdapter
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RetryViewModel
import com.livetvpro.app.utils.RetryHandler
import com.livetvpro.app.utils.Refreshable
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.utils.FloatingPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import com.livetvpro.app.utils.DeviceUtils
import javax.inject.Inject

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val favoritesRepository: FavoritesRepository
) : RetryViewModel() {
    private val _channels = MutableLiveData<List<Channel>>()
    private val _filteredChannels = MutableLiveData<List<Channel>>()
    val filteredChannels: LiveData<List<Channel>> = _filteredChannels
    private val _favoriteStatusCache = MutableStateFlow<Set<String>>(emptySet())
    private var currentQuery: String = ""

    init {
        loadData()
        loadFavoriteCache()
    }

    private fun loadFavoriteCache() {
        viewModelScope.launch {
            favoritesRepository.getFavoritesFlow().collect { favorites ->
                _favoriteStatusCache.value = favorites.map { it.id }.toSet()
            }
        }
    }

    override fun loadData() {
        viewModelScope.launch {
            repository.getSports()
                .onStart { startLoading() }
                .catch { e ->
                    _channels.value = emptyList()
                    applyFilter()
                    finishLoading(dataIsEmpty = true, error = e)
                }
                .collect { sports ->
                    _channels.value = sports
                    applyFilter()
                    finishLoading(dataIsEmpty = sports.isEmpty())
                }
        }
    }

    override fun onResume() {
    }

    fun searchSports(query: String) {
        currentQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val channels = _channels.value ?: emptyList()
        _filteredChannels.value = if (currentQuery.isBlank()) channels
        else channels.filter { it.name.contains(currentQuery, ignoreCase = true) }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val favoriteLinks = channel.links?.map { channelLink ->
                ChannelLink(
                    quality = channelLink.quality,
                    url = channelLink.url,
                    cookie = channelLink.cookie,
                    referer = channelLink.referer,
                    origin = channelLink.origin,
                    userAgent = channelLink.userAgent,
                    drmScheme = channelLink.drmScheme,
                    drmLicenseUrl = channelLink.drmLicenseUrl
                )
            }
            val streamUrlToSave = when {
                channel.streamUrl.isNotEmpty() -> channel.streamUrl
                !favoriteLinks.isNullOrEmpty() -> buildStreamUrlFromLink(favoriteLinks.first())
                else -> ""
            }
            val favoriteChannel = FavoriteChannel(
                id = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = streamUrlToSave,
                categoryId = channel.categoryId,
                categoryName = "Sports",
                links = favoriteLinks
            )
            if (favoritesRepository.isFavorite(channel.id)) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                favoritesRepository.addFavorite(favoriteChannel)
            }
        }
    }

    private fun buildStreamUrlFromLink(link: ChannelLink): String {
        val parts = mutableListOf(link.url)
        link.referer?.let { if (it.isNotEmpty()) parts.add("referer=$it") }
        link.cookie?.let { if (it.isNotEmpty()) parts.add("cookie=$it") }
        link.origin?.let { if (it.isNotEmpty()) parts.add("origin=$it") }
        link.userAgent?.let { if (it.isNotEmpty()) parts.add("User-Agent=$it") }
        link.drmScheme?.let { if (it.isNotEmpty()) parts.add("drmScheme=$it") }
        link.drmLicenseUrl?.let { if (it.isNotEmpty()) parts.add("drmLicense=$it") }
        return if (parts.size > 1) parts.joinToString("|") else parts[0]
    }

    fun isFavorite(channelId: String): Boolean = _favoriteStatusCache.value.contains(channelId)
}

@AndroidEntryPoint
class SportsFragment : Fragment(), SearchableFragment, Refreshable {
    private var _binding: FragmentSportsBinding? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private val binding get() = _binding!!
    private val viewModel: SportsViewModel by viewModels()
    private lateinit var channelAdapter: ChannelAdapter

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var preferencesManager: PreferencesManager


    }

    override fun onSearchQuery(query: String) { viewModel.searchSports(query) }
    override fun refreshData() { viewModel.refresh() }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
        (binding.recyclerViewChannels.layoutManager as? GridLayoutManager)?.spanCount = columnCount
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupRetryHandling()
        if (DeviceUtils.isTvDevice) {
            binding.swipeRefresh.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        pendingChannelAction?.invoke()
        pendingChannelAction = null
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
                val redirected = RedirectHelper.tryRedirect(
                    fragment    = this@SportsFragment,
                    pageType    = ListenerConfig.PAGE_SPORTS,
                    uniqueId    = channel.id,
                    cooldownMgr = cooldownManager,
                    listenerMgr = listenerManager
                )
                if (!redirected) {
                    pendingChannelAction?.invoke()
                    pendingChannelAction = null
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
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true)
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(requireContext(), channel, linkIndex, isSports = true)
            } catch (e: Exception) {
                PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true)
            }
        } else {
            PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
