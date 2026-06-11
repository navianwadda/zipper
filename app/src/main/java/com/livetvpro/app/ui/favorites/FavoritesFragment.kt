package com.livetvpro.app.ui.favorites

import android.content.DialogInterface
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.ui.theme.AppThemeContent
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavoritesFragment : Fragment() {

    private val viewModel: FavoritesViewModel by viewModels()

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    companion object {
        private const val FAVORITES_CACHE_KEY = "favorites_session"
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
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val spanCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
                AppThemeContent(themeManager) {
                    FavoritesScreen(
                        viewModel = viewModel,
                        spanCount = spanCount,
                        isTvDevice = DeviceUtils.isTvDevice,
                        onChannelClick = { favorite -> handleChannelClick(favorite) },
                        onRemoveFavorite = { favorite -> showRemoveConfirmation(favorite) },
                        onClearAll = { viewModel.clearAll() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            refreshFavoritesCache(favorites)
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

    private fun refreshFavoritesCache(favorites: List<FavoriteChannel>?) {
        val list = try {
            favorites?.map { fav ->
                val live = viewModel.getLiveChannel(fav.id)
                live ?: Channel(
                    id = fav.id,
                    name = fav.name,
                    logoUrl = fav.logoUrl,
                    streamUrl = fav.streamUrl.ifEmpty { fav.links?.firstOrNull()?.url ?: "" },
                    categoryId = fav.categoryId,
                    categoryName = fav.categoryName,
                    links = fav.links
                )
            } ?: emptyList()
        } catch (e: OutOfMemoryError) {
            System.gc()
            emptyList()
        }
        if (list.isNotEmpty()) {
            ChannelListCache.put(FAVORITES_CACHE_KEY, list)
        }
    }

    private fun handleChannelClick(favorite: FavoriteChannel) {
        val liveChannel = viewModel.getLiveChannel(favorite.id)
        val channelToUse = if (liveChannel != null) {
            liveChannel
        } else {
            val resolvedStreamUrl = favorite.streamUrl.ifEmpty {
                favorite.links?.firstOrNull()?.url ?: ""
            }
            Channel(
                id = favorite.id,
                name = favorite.name,
                logoUrl = favorite.logoUrl,
                streamUrl = resolvedStreamUrl,
                categoryId = favorite.categoryId,
                categoryName = favorite.categoryName,
                links = favorite.links
            )
        }

        val links = channelToUse.links
        val playerAction: () -> Unit = when {
            links.isNullOrEmpty() -> {
                if (channelToUse.streamUrl.isNotEmpty()) {
                    { launchPlayer(channelToUse, 0) }
                } else {
                    {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "No stream available for ${favorite.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            links.size > 1 -> {
                { showLinkSelectionDialog(channelToUse, links) }
            }
            else -> {
                { launchPlayer(channelToUse, 0) }
            }
        }

        lastPageType = ListenerConfig.PAGE_FAVORITES
        lastUniqueId = favorite.id
        pendingChannelAction = playerAction

        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_FAVORITES,
            uniqueId    = favorite.id,
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
        } else {
            pendingChannelAction = null
            playerAction()
        }
    }

    private fun showLinkSelectionDialog(
        channel: Channel,
        links: List<com.livetvpro.app.data.models.ChannelLink>
    ) {
        val linkLabels = links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { d, which ->
                launchPlayer(channel, which)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun showRemoveConfirmation(favorite: FavoriteChannel) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Favorite")
            .setMessage("Remove \"${favorite.name}\" from your favorites list?")
            .setPositiveButton("Remove") { _, _ -> viewModel.removeFavorite(favorite.id) }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                channelListCacheKey = FAVORITES_CACHE_KEY
            )
            return
        }

        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())

        if (floatingEnabled) {
            if (!hasPermission) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Overlay permission required for floating player. Opening normally instead.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    channelListCacheKey = FAVORITES_CACHE_KEY
                )
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(
                    requireContext(), channel, linkIndex,
                    channelListCacheKey = FAVORITES_CACHE_KEY
                )
            } catch (e: Exception) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    channelListCacheKey = FAVORITES_CACHE_KEY
                )
            }
        } else {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                channelListCacheKey = FAVORITES_CACHE_KEY
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
