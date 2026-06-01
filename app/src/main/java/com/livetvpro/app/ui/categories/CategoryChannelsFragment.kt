package com.livetvpro.app.ui.categories

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.adapters.CategoryGroupDialogAdapter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CategoryChannelsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: CategoryChannelsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var currentCategoryId: String? = null

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>

    private var numpadBuffer = ""
    private val numpadHandler = Handler(Looper.getMainLooper())
    private val numpadResetRunnable = Runnable { numpadBuffer = ""; viewModel.searchChannels("") }
    private val NUMPAD_RESET_MS = 2000L

    override fun onSearchQuery(query: String) { viewModel.searchChannels(query) }
    override fun refreshData() { currentCategoryId?.let { viewModel.loadChannels(it) } }

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
    ): View {
        currentCategoryId = arguments?.getString("categoryId")
        currentCategoryId?.let { id ->
            if (id != viewModel.lastLoadedCategoryId) viewModel.loadChannels(id)
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    CategoryChannelsScreen(
                        viewModel        = viewModel,
                        onChannelClick   = { channel -> handleChannelClick(channel) },
                        isFavorite       = { viewModel.isFavorite(it) },
                        onFavoriteToggle = { channel ->
                            viewModel.toggleFavorite(channel)
                            lifecycleScope.launch { delay(50); viewModel.refreshFavoriteState(channel.id) }
                        },
                        onGroupsIconClick = { showGroupsDialog() },
                    )
                }
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

    override fun onDestroyView() {
        super.onDestroyView()
        numpadHandler.removeCallbacksAndMessages(null)
        viewModel.dismissError()
    }

    private fun handleChannelClick(channel: Channel) {
        pendingChannelAction = if (channel.links != null && channel.links.size > 1) {
            { showLinkSelectionDialog(channel) }
        } else {
            { launchPlayer(channel, -1) }
        }
        lastPageType = ListenerConfig.PAGE_CHANNELS
        lastUniqueId = channel.id

        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_CHANNELS,
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
        val cacheKey = currentCategoryId ?: channel.categoryId.takeIf { it.isNotEmpty() } ?: channel.id
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId          = currentCategoryId,
                selectedGroup       = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission   = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (floatingEnabled && hasPermission) {
            try {
                FloatingPlayerHelper.launchFloatingPlayer(requireContext(), channel, linkIndex, channelListCacheKey = cacheKey)
            } catch (_: Exception) {
                PlayerActivity.startWithChannel(
                    requireContext(), channel, linkIndex,
                    categoryId          = currentCategoryId,
                    selectedGroup       = viewModel.currentGroup.value,
                    channelListCacheKey = cacheKey
                )
            }
        } else {
            PlayerActivity.startWithChannel(
                requireContext(), channel, linkIndex,
                categoryId          = currentCategoryId,
                selectedGroup       = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
        }
    }

    private fun showLinkSelectionDialog(channel: Channel) {
        val links      = channel.links?.takeIf { it.isNotEmpty() } ?: return
        val linkLabels = links.map { it.quality }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { d, which -> launchPlayer(channel, which); d.dismiss() }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun showGroupsDialog() {
        val dialogView    = layoutInflater.inflate(R.layout.dialog_category_groups, null)
        val recyclerView  = dialogView.findViewById<RecyclerView>(R.id.recycler_view_groups)
        val searchEdit    = dialogView.findViewById<EditText>(R.id.search_group)
        val clearBtn      = dialogView.findViewById<ImageView>(R.id.clear_search_button)
        val closeBtn      = dialogView.findViewById<ImageView>(R.id.close_button)
        searchEdit.typeface = resources.getFont(R.font.bergen_sans)

        val dialog    = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        val allGroups = viewModel.categoryGroups.value ?: emptyList()
        var filtered  = allGroups.toList()

        val dialogAdapter = CategoryGroupDialogAdapter { groupName ->
            viewModel.selectGroup(groupName)
            dialog.dismiss()
        }
        recyclerView.apply { layoutManager = LinearLayoutManager(context); adapter = dialogAdapter }
        dialogAdapter.submitList(filtered)

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        if (DeviceUtils.isTvDevice) {
            searchEdit.isFocusable = false
            searchEdit.isFocusableInTouchMode = false
            dialog.setOnShowListener { imm.hideSoftInputFromWindow(searchEdit.windowToken, 0); recyclerView.requestFocus() }
        } else {
            searchEdit.setOnFocusChangeListener { v, hasFocus ->
                clearBtn.visibility = if (hasFocus) View.VISIBLE else View.GONE
                if (!hasFocus) imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            searchEdit.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val q = s.toString().trim()
                    filtered = if (q.isEmpty()) allGroups else allGroups.filter { it.contains(q, ignoreCase = true) }
                    dialogAdapter.submitList(filtered)
                }
            })
            clearBtn.setOnClickListener { searchEdit.text.clear(); searchEdit.clearFocus() }
            dialog.setOnShowListener { recyclerView.requestFocus(); imm.hideSoftInputFromWindow(searchEdit.windowToken, 0) }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.apply {
            val dm     = requireContext().resources.displayMetrics
            val height = (dm.heightPixels * 0.80f).toInt()
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, height)
        }
    }
}

@Composable
fun CategoryChannelsScreen(
    viewModel: CategoryChannelsViewModel,
    onChannelClick: (Channel) -> Unit,
    isFavorite: (String) -> Boolean,
    onFavoriteToggle: (Channel) -> Unit,
    onGroupsIconClick: () -> Unit,
) {
    val channels  by viewModel.channels.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val groups    by viewModel.categoryGroups.observeAsState(emptyList())
    val currentGroup by viewModel.currentGroup.observeAsState()

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2

    val selectedTabIndex = remember(groups, currentGroup) {
        groups.indexOf(currentGroup).coerceAtLeast(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (groups.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier         = Modifier.fillMaxWidth(),
                edgePadding      = 0.dp,
            ) {
                groups.forEachIndexed { index, group ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick  = { viewModel.selectGroup(group) },
                        text     = { Text(group) },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
        }

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
                        .padding(4.dp)
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
}
