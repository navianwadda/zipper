package com.livetvpro.app.ui.categories

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.bundleOf
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
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CategoryChannelsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: CategoryChannelsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var themeManager: ThemeManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    private var currentCategoryId: String? = null

    private var numpadBuffer = ""
    private val numpadHandler = Handler(Looper.getMainLooper())
    private val numpadResetRunnable = Runnable {
        numpadBuffer = ""
        viewModel.searchChannels("")
    }
    private val NUMPAD_RESET_MS = 2000L

    override fun refreshData() {
        currentCategoryId?.let { viewModel.loadChannels(it) }
    }

    override fun onSearchQuery(query: String) {
        viewModel.searchChannels(query)
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
    ): View {
        currentCategoryId = arguments?.getString("categoryId")

        try {
            val toolbarTitle = requireActivity().findViewById<TextView>(R.id.toolbar_title)
            if (viewModel.categoryName.isNotEmpty()) toolbarTitle?.text = viewModel.categoryName
        } catch (_: Exception) {}

        currentCategoryId?.let { id ->
            if (id != viewModel.lastLoadedCategoryId) viewModel.loadChannels(id)
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val spanCount = resources.getInteger(R.integer.grid_column_count)
                var showGroupsDialog by remember { mutableStateOf(false) }
                val BergenSans = FontFamily(Font(R.font.bergen_sans))

                LiveTVProTheme(themeManager) {
                    CategoryChannelsScreen(
                        viewModel = viewModel,
                        spanCount = spanCount,
                        isTvDevice = DeviceUtils.isTvDevice,
                        onChannelClick = { channel ->
                            if (channel.links != null && channel.links.size > 1) {
                                showLinkSelectionDialog(channel)
                            } else {
                                launchPlayer(channel, -1)
                            }
                        },
                        onChannelLongClick = { channel -> showFavoriteDialog(channel) },
                        onShowGroupsDialog = { showGroupsDialog = true },
                        onChannelInteraction = { channel, navAction ->
                            val action: () -> Unit =
                                if (channel.links != null && channel.links.size > 1) {
                                    { showLinkSelectionDialog(channel) }
                                } else {
                                    navAction
                                }
                            lastPageType = ListenerConfig.PAGE_CHANNELS
                            lastUniqueId = channel.id
                            pendingChannelAction = action

                            val result = RedirectHelper.tryRedirect(
                                fragment    = this@CategoryChannelsFragment,
                                pageType    = ListenerConfig.PAGE_CHANNELS,
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
                            } else {
                                pendingChannelAction = null
                            }
                            result == RedirectHelper.RedirectResult.REDIRECTED
                        }
                    )

                    if (showGroupsDialog) {
                        val allGroups = viewModel.categoryGroups.value ?: emptyList()
                        var query by remember { mutableStateOf("") }
                        val filtered = remember(query, allGroups) {
                            if (query.isBlank()) allGroups
                            else allGroups.filter { it.contains(query, ignoreCase = true) }
                        }

                        Dialog(
                            onDismissRequest = { showGroupsDialog = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxSize(0.80f),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = query,
                                            onValueChange = { query = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = {
                                                Text(
                                                    text = "Search Groups",
                                                    fontFamily = BergenSans,
                                                    fontSize = 16.sp
                                                )
                                            },
                                            singleLine = true,
                                            trailingIcon = {
                                                if (query.isNotEmpty()) {
                                                    IconButton(onClick = { query = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = "Clear"
                                                        )
                                                    }
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {})
                                        )
                                        IconButton(onClick = { showGroupsDialog = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close"
                                            )
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.fillMaxWidth())

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filtered, key = { it }) { groupName ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.selectGroup(groupName)
                                                        showGroupsDialog = false
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_playlist),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = groupName,
                                                    fontFamily = BergenSans,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                numpadHandler.removeCallbacks(numpadResetRunnable)
                numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                viewModel.searchChannels(numpadBuffer)
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

    private fun launchPlayer(channel: Channel, linkIndex: Int) {
        val cacheKey = currentCategoryId
            ?: channel.categoryId.takeIf { it.isNotEmpty() }
            ?: channel.id
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())

        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId = currentCategoryId,
                selectedGroup = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
            return
        }

        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    categoryId = currentCategoryId,
                    selectedGroup = viewModel.currentGroup.value,
                    channelListCacheKey = cacheKey
                )
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(
                    requireContext(), channel, linkIndex, channelListCacheKey = cacheKey
                )
            } catch (_: Exception) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    categoryId = currentCategoryId,
                    selectedGroup = viewModel.currentGroup.value,
                    channelListCacheKey = cacheKey
                )
            }
        } else {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId = currentCategoryId,
                selectedGroup = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
        }
    }

    private fun showLinkSelectionDialog(channel: Channel) {
        val links = channel.links?.takeIf { it.isNotEmpty() } ?: return
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
        numpadHandler.removeCallbacksAndMessages(null)
        viewModel.dismissError()
    }
}
