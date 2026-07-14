package com.livetvpro.app.ui.live

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

@Composable
fun LiveEventsRoute(
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
    searchQuery: String,
    refreshSignal: Int,
) {
    val context = LocalContext.current
    val viewModel: LiveEventsViewModel = hiltViewModel()

    var lastPageType by remember { mutableStateOf<String?>(null) }
    var lastUniqueId by remember { mutableStateOf<String?>(null) }
    var pendingEventAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExternalRedirect by remember { mutableStateOf(false) }

    val redirectLauncher = RedirectHelper.rememberRedirectLauncher(
        cooldownMgr = cooldownManager,
        pageTypeProvider = { lastPageType },
        uniqueIdProvider = { lastUniqueId },
    )

    LaunchedEffect(searchQuery) {
        viewModel.searchEvents(searchQuery)
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                RedirectHelper.executePendingActionOnResume(
                    pendingActionProvider = { pendingEventAction },
                    clearPendingAction = { pendingEventAction = null },
                    pendingExternalRedirect = pendingExternalRedirect,
                    clearPendingRedirect = { pendingExternalRedirect = false },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun proceedWithPlayer(event: LiveEvent, linkIndex: Int) {
        if (DeviceUtils.isTvDevice || DeviceUtils.isTablet) {
            PlayerActivity.startWithEvent(context, event, linkIndex)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

        if (floatingEnabled) {
            if (!hasPermission) {
                Toast.makeText(
                    context,
                    "Overlay permission required for floating player. Opening normally instead.",
                    Toast.LENGTH_LONG
                ).show()
                PlayerActivity.startWithEvent(context, event, linkIndex)
                return
            }
            try {
                val channel = Channel(
                    id = event.id,
                    name = "${event.team1Name} vs ${event.team2Name}",
                    logoUrl = event.leagueLogo.ifEmpty { event.team1Logo },
                    categoryName = event.category,
                    links = event.links.map { liveEventLink ->
                        ChannelLink(
                            quality = liveEventLink.quality,
                            url = liveEventLink.url,
                            cookie = liveEventLink.cookie,
                            referer = liveEventLink.referer,
                            origin = liveEventLink.origin,
                            userAgent = liveEventLink.userAgent,
                            xForwardedFor = liveEventLink.xForwardedFor,
                            drmScheme = liveEventLink.drmScheme,
                            drmLicenseUrl = liveEventLink.drmLicenseUrl
                        )
                    }
                )
                FloatingPlayerHelper.launchFloatingPlayerWithEvent(context, channel, event, linkIndex)
            } catch (e: Exception) {
                PlayerActivity.startWithEvent(context, event, linkIndex)
            }
        } else {
            PlayerActivity.startWithEvent(context, event, linkIndex)
        }
    }

    val configuration = LocalConfiguration.current
    val spanCount = remember(configuration) {
        context.resources.getInteger(R.integer.event_span_count)
    }

    LiveEventsScreen(
        viewModel = viewModel,
        messageBannerText = listenerManager.getMessage(),
        messageBannerUrl = listenerManager.getMessageUrl(),
        spanCount = spanCount,
        isTvDevice = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet,
        onEventClick = { event, linkIndex ->
            proceedWithPlayer(event, linkIndex)
        },
        onEventInteraction = { event, playerAction ->
            lastPageType = ListenerConfig.PAGE_LIVE_EVENTS
            lastUniqueId = event.id
            val result = RedirectHelper.tryRedirect(
                context = context,
                pageType = ListenerConfig.PAGE_LIVE_EVENTS,
                uniqueId = event.id,
                cooldownMgr = cooldownManager,
                listenerMgr = listenerManager,
                launcher = redirectLauncher
            )
            
            if (result == RedirectHelper.RedirectResult.REDIRECTED) {
                if (!listenerManager.isInAppRedirectEnabled()) {
                    pendingEventAction = playerAction
                    pendingExternalRedirect = true
                }
            } else {
                pendingEventAction = null
            }
            result == RedirectHelper.RedirectResult.REDIRECTED
        }
    )
}
