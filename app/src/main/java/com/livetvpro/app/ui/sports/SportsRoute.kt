package com.livetvpro.app.ui.sports

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

private const val NUMPAD_RESET_MS = 2000L

@Composable
fun SportsRoute(
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
    searchQuery: String,
    refreshSignal: Int,
) {
    val context = LocalContext.current
    val viewModel: SportsViewModel = hiltViewModel()

    var lastPageType by remember { mutableStateOf<String?>(null) }
    var lastUniqueId by remember { mutableStateOf<String?>(null) }
    var pendingChannelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExternalRedirect by remember { mutableStateOf(false) }

    val redirectLauncher = RedirectHelper.rememberRedirectLauncher(
        cooldownMgr = cooldownManager,
        pageTypeProvider = { lastPageType },
        uniqueIdProvider = { lastUniqueId },
    )

    LaunchedEffect(searchQuery) {
        viewModel.searchSports(searchQuery)
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
        onDispose { viewModel.dismissError() }
    }

    fun launchPlayer(channel: Channel, linkIndex: Int) {
        val channelList = viewModel.filteredChannels.value ?: emptyList()
        val cacheKey = "sports_${channel.id}"
        if (channelList.isNotEmpty()) ChannelListCache.put(cacheKey, channelList)

        if (DeviceUtils.isTvDevice || DeviceUtils.isTablet) {
            PlayerActivity.startWithChannel(context, channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)
        if (floatingEnabled) {
            if (!hasPermission) {
                PlayerActivity.startWithChannel(context, channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(context, channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            } catch (e: Exception) {
                PlayerActivity.startWithChannel(context, channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
            }
        } else {
            PlayerActivity.startWithChannel(context, channel, linkIndex, isSports = true, channelListCacheKey = cacheKey)
        }
    }

    val configuration = LocalConfiguration.current
    val spanCount = remember(configuration) {
        context.resources.getInteger(R.integer.grid_column_count)
    }
    val isTvOrTablet = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet
    var numpadBuffer by remember { mutableStateOf("") }
    val numpadHandler = remember { Handler(Looper.getMainLooper()) }
    val numpadResetRunnable = remember {
        Runnable {
            numpadBuffer = ""
            viewModel.searchSports("")
        }
    }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { numpadHandler.removeCallbacksAndMessages(null) }
    }
    LaunchedEffect(isTvOrTablet) {
        if (isTvOrTablet) focusRequester.requestFocus()
    }

    Box(
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
                                numpadHandler.removeCallbacksAndMessages(null)
                                numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                viewModel.searchSports(numpadBuffer)
                                return@onKeyEvent true
                            }
                            if (keyEvent.key == Key.Backspace && numpadBuffer.isNotEmpty()) {
                                numpadBuffer = numpadBuffer.dropLast(1)
                                numpadHandler.removeCallbacksAndMessages(null)
                                if (numpadBuffer.isEmpty()) {
                                    viewModel.searchSports("")
                                } else {
                                    viewModel.searchSports(numpadBuffer)
                                    numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                }
                                return@onKeyEvent true
                            }
                            false
                        }
                } else Modifier
            )
    ) {
        SportsScreen(
            viewModel = viewModel,
            spanCount = spanCount,
            isTvDevice = isTvOrTablet,
            onChannelClick = { channel, linkIndex -> launchPlayer(channel, linkIndex) },
            onChannelInteraction = { channel, action ->
                lastPageType = ListenerConfig.PAGE_SPORTS
                lastUniqueId = channel.id
                pendingChannelAction = action

                val result = RedirectHelper.tryRedirect(
                    context     = context,
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
                } else {
                    pendingChannelAction = null
                }
                result == RedirectHelper.RedirectResult.REDIRECTED
            },
        )
    }
}
