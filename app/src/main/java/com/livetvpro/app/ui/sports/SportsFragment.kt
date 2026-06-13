package com.livetvpro.app.ui.sports

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.ui.theme.AppThemeContent
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
    @Inject lateinit var themeManager: ThemeManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    override fun onSearchQuery(query: String) { viewModel.searchSports(query) }
    override fun refreshData() { viewModel.refresh() }

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
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val spanCount = resources.getInteger(R.integer.grid_column_count)
                AppThemeContent(themeManager) {
                    SportsScreen(
                        viewModel = viewModel,
                        spanCount = spanCount,
                        isTvDevice = DeviceUtils.isTvDevice,
                        onChannelClick = { channel, linkIndex ->
                            launchPlayer(channel, linkIndex)
                        },
                        onChannelLongClick = { channel -> showFavoriteDialog(channel) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (DeviceUtils.isTvDevice) setupTvNumpadSearch(view)
    }

    private fun setupTvNumpadSearch(rootView: View) {
        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        var numpadBuffer = ""
        val numpadHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val numpadResetRunnable = Runnable {
            numpadBuffer = ""
            viewModel.searchSports("")
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
                viewModel.searchSports(numpadBuffer)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL && numpadBuffer.isNotEmpty()) {
                numpadBuffer = numpadBuffer.dropLast(1)
                numpadHandler.removeCallbacksAndMessages(null)
                if (numpadBuffer.isEmpty()) {
                    viewModel.searchSports("")
                } else {
                    viewModel.searchSports(numpadBuffer)
                    numpadHandler.postDelayed(numpadResetRunnable, 2000L)
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        val channelList = viewModel.filteredChannels.value ?: emptyList()
        val cacheKey = "sports_${channel.id}"
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

    private fun showFavoriteDialog(channel: Channel) {
        val isFav = viewModel.isFavorite(channel.id)
        val title = if (isFav) "Remove from Favorites?" else "Add to Favorites?"
        val message = if (isFav) "Remove \"${channel.name}\" from favorites?" else "Add \"${channel.name}\" to favorites?"
        val posBtnLabel = if (isFav) "Remove" else "Add"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(posBtnLabel) { dialog, _ ->
                viewModel.toggleFavorite(channel)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.dismissError()
    }
}
