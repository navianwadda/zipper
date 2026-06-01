package com.livetvpro.app.ui.sports

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.adapters.ChannelCard
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SportsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: SportsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>

    override fun onSearchQuery(query: String) { viewModel.searchSports(query) }
    override fun refreshData() { viewModel.refresh() }

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
                SportsScreen(
                    viewModel      = viewModel,
                    onChannelClick = { channel -> handleChannelClick(channel) },
                    isFavorite     = { viewModel.isFavorite(it) },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider   = { pendingChannelAction },
            clearPendingAction      = { pendingChannelAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect    = { pendingExternalRedirect = false }
        )
    }

    private fun handleChannelClick(channel: Channel) {
        pendingChannelAction = if (channel.links != null && channel.links.size > 1) {
            { showLinkSelectionDialog(channel) }
        } else {
            { launchPlayer(channel, -1) }
        }
        lastPageType = ListenerConfig.PAGE_SPORTS
        lastUniqueId = channel.id

        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_SPORTS,
            uniqueId    = channel.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )
        when (result) {
            RedirectHelper.RedirectResult.REDIRECTED -> {
                if (!listenerManager.isInAppRedirectEnabled()) pendingExternalRedirect = true
                else pendingChannelAction = null
            }
            RedirectHelper.RedirectResult.NOT_REDIRECTED -> {
                pendingChannelAction?.invoke()
                pendingChannelAction = null
            }
            else -> pendingChannelAction = null
        }
    }

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        val cacheKey = "sports_${channel.id}"
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(requireContext(), channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission   = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (floatingEnabled && hasPermission) {
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
        val links      = channel.links ?: return
        val linkLabels = links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { d, which -> launchPlayer(channel, which); d.dismiss() }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }
}

@Composable
fun SportsScreen(
    viewModel: SportsViewModel,
    onChannelClick: (Channel) -> Unit,
    isFavorite: (String) -> Boolean,
    onFavoriteToggle: (Channel) -> Unit,
) {
    val channels  by viewModel.filteredChannels.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            channels.isEmpty() -> Text(
                text     = "No channels available",
                modifier = Modifier.align(Alignment.Center),
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyVerticalGrid(
                columns  = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 90.dp),
            ) {
                items(channels) { channel ->
                    ChannelCard(
                        channel          = channel,
                        isFavorite       = isFavorite(channel.id),
                        onClick          = { onChannelClick(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel) },
                    )
                }
            }
        }
    }
}
