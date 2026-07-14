package com.livetvpro.app.ui.categories

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

private const val NUMPAD_RESET_MS = 2000L

@Composable
fun CategoryChannelsRoute(
    categoryId: String?,
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
    searchQuery: String,
    refreshSignal: Int,
    onTitleResolved: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: CategoryChannelsViewModel = hiltViewModel()

    var lastPageType by remember { mutableStateOf<String?>(null) }
    var lastUniqueId by remember { mutableStateOf<String?>(null) }
    var pendingChannelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExternalRedirect by remember { mutableStateOf(false) }

    val redirectLauncher = RedirectHelper.rememberRedirectLauncher(
        cooldownMgr = cooldownManager,
        pageTypeProvider = { lastPageType },
        uniqueIdProvider = { lastUniqueId },
    )

    LaunchedEffect(categoryId) {
        categoryId?.let { id ->
            if (id != viewModel.lastLoadedCategoryId) viewModel.loadChannels(id)
        }
    }

    LaunchedEffect(viewModel.categoryName) {
        if (viewModel.categoryName.isNotEmpty()) onTitleResolved(viewModel.categoryName)
    }

    LaunchedEffect(searchQuery) {
        viewModel.searchChannels(searchQuery)
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) categoryId?.let { viewModel.loadChannels(it) }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                RedirectHelper.executePendingActionOnResume(
                    pendingActionProvider = { pendingChannelAction },
                    clearPendingAction = { pendingChannelAction = null },
                    pendingExternalRedirect = pendingExternalRedirect,
                    clearPendingRedirect = { pendingExternalRedirect = false },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.dismissError()
        }
    }

    fun launchPlayer(channel: Channel, linkIndex: Int) {
        val cacheKey = categoryId
            ?: channel.categoryId.takeIf { it.isNotEmpty() }
            ?: channel.id
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

        if (DeviceUtils.isTvDevice || DeviceUtils.isTablet) {
            PlayerActivity.startWithChannel(
                context, channel, linkIndex,
                categoryId = categoryId,
                selectedGroup = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
            return
        }

        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(
                    context, channel, linkIndex,
                    categoryId = categoryId,
                    selectedGroup = viewModel.currentGroup.value,
                    channelListCacheKey = cacheKey
                )
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(
                    context, channel, linkIndex, channelListCacheKey = cacheKey
                )
            } catch (_: Exception) {
                PlayerActivity.startWithChannel(
                    context, channel, linkIndex,
                    categoryId = categoryId,
                    selectedGroup = viewModel.currentGroup.value,
                    channelListCacheKey = cacheKey
                )
            }
        } else {
            PlayerActivity.startWithChannel(
                context, channel, linkIndex,
                categoryId = categoryId,
                selectedGroup = viewModel.currentGroup.value,
                channelListCacheKey = cacheKey
            )
        }
    }

    val configuration = LocalConfiguration.current
    val spanCount = remember(configuration) {
        context.resources.getInteger(R.integer.grid_column_count)
    }
    var showGroupsDialog by remember { mutableStateOf(false) }
    val bergenSans = FontFamily(Font(R.font.bergen_sans))
    val isTvOrTablet = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet

    var numpadBuffer by remember { mutableStateOf("") }
    val numpadHandler = remember { Handler(Looper.getMainLooper()) }
    val numpadResetRunnable = remember {
        Runnable {
            numpadBuffer = ""
            viewModel.searchChannels("")
        }
    }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { numpadHandler.removeCallbacksAndMessages(null) }
    }
    LaunchedEffect(isTvOrTablet) {
        if (isTvOrTablet) focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isTvOrTablet) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusTarget()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val digit = when (keyEvent.key) {
                                Key.Zero, Key.NumPad0 -> "0"
                                Key.One, Key.NumPad1 -> "1"
                                Key.Two, Key.NumPad2 -> "2"
                                Key.Three, Key.NumPad3 -> "3"
                                Key.Four, Key.NumPad4 -> "4"
                                Key.Five, Key.NumPad5 -> "5"
                                Key.Six, Key.NumPad6 -> "6"
                                Key.Seven, Key.NumPad7 -> "7"
                                Key.Eight, Key.NumPad8 -> "8"
                                Key.Nine, Key.NumPad9 -> "9"
                                else -> null
                            }
                            if (digit != null) {
                                numpadBuffer += digit
                                numpadHandler.removeCallbacks(numpadResetRunnable)
                                numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                viewModel.searchChannels(numpadBuffer)
                                return@onKeyEvent true
                            }
                            if (keyEvent.key == Key.Backspace && numpadBuffer.isNotEmpty()) {
                                numpadBuffer = numpadBuffer.dropLast(1)
                                numpadHandler.removeCallbacks(numpadResetRunnable)
                                if (numpadBuffer.isEmpty()) {
                                    viewModel.searchChannels("")
                                } else {
                                    viewModel.searchChannels(numpadBuffer)
                                    numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                }
                                return@onKeyEvent true
                            }
                            false
                        }
                } else Modifier
            ),
    ) {
        CategoryChannelsScreen(
            viewModel = viewModel,
            spanCount = spanCount,
            isTvDevice = isTvOrTablet,
            onChannelClick = { channel, linkIndex -> launchPlayer(channel, linkIndex) },
            onShowGroupsDialog = { showGroupsDialog = true },
            onChannelInteraction = { channel, navAction ->
                lastPageType = ListenerConfig.PAGE_CHANNELS
                lastUniqueId = channel.id
                pendingChannelAction = navAction

                val result = RedirectHelper.tryRedirect(
                    context     = context,
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
    }

    if (showGroupsDialog) {
        val allGroups = viewModel.categoryGroups.value ?: emptyList()
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, allGroups) {
            if (query.isBlank()) allGroups
            else allGroups.filter { it.contains(query, ignoreCase = true) }
        }
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp

        Dialog(
            onDismissRequest = { showGroupsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(min = 200.dp, max = screenHeight * 0.75f),
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
                                    fontFamily = bergenSans,
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
                                    fontFamily = bergenSans,
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
