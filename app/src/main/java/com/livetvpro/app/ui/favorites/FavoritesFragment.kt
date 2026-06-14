package com.livetvpro.app.ui.favorites

import android.content.DialogInterface
import android.content.Intent
import android.view.KeyEvent
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
                val configuration = LocalConfiguration.current
                val context = LocalContext.current
                val spanCount = remember(configuration) {
                    context.resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
                }
                AppThemeContent(themeManager) {
                    FavoritesScreen(
                        viewModel = viewModel,
                        spanCount = spanCount,
                        isTvDevice = DeviceUtils.isTvDevice,
                        onChannelClick = { favorite, linkIndex -> handleChannelClick(favorite, linkIndex) },
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
            viewModel.onFavoritesChanged(favorites ?: emptyList())
        }
        if (DeviceUtils.isTvDevice) setupTvNumpadSearch(view)
    }

    private fun setupTvNumpadSearch(rootView: View) {
        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        var numpadBuffer = ""
        val numpadHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val numpadResetRunnable = Runnable {
            numpadBuffer = ""
            viewModel.searchFavorites("")
        }
        rootView.setOnKeyListener { _, keyCode, event ->
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
                numpadHandler.removeCallbacksAndMessages(null)
                numpadHandler.postDelayed(numpadResetRunnable, 2000L)
                viewModel.searchFavorites(numpadBuffer)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL && numpadBuffer.isNotEmpty()) {
                numpadBuffer = numpadBuffer.dropLast(1)
                numpadHandler.removeCallbacksAndMessages(null)
                if (numpadBuffer.isEmpty()) {
                    viewModel.searchFavorites("")
                } else {
                    viewModel.searchFavorites(numpadBuffer)
                    numpadHandler.postDelayed(numpadResetRunnable, 2000L)
                }
                return@setOnKeyListener true
            }
            false
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

    private fun handleChannelClick(favorite: FavoriteChannel, linkIndex: Int) {
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

        if (channelToUse.streamUrl.isEmpty() && channelToUse.links.isNullOrEmpty()) {
            android.widget.Toast.makeText(
                requireContext(),
                "No stream available for ${favorite.name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val playerAction: () -> Unit = { launchPlayer(channelToUse, linkIndex) }

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
