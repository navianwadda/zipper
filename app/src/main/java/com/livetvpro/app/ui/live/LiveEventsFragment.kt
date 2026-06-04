package com.livetvpro.app.ui.live

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LiveEventsFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: LiveEventsViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var pendingEventAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    override fun refreshData() {
        viewModel.refresh()
    }

    override fun onSearchQuery(query: String) {
        viewModel.searchEvents(query)
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
                LiveTVProTheme(themeManager) {
                val spanCount = resources.getInteger(R.integer.event_span_count)

                LiveEventsScreen(
                    viewModel = viewModel,
                    messageBannerText = listenerManager.getMessage(),
                    messageBannerUrl = listenerManager.getMessageUrl(),
                    spanCount = spanCount,
                    isTvDevice = DeviceUtils.isTvDevice,
                    onEventClick = { event, linkIndex ->
                        proceedWithPlayer(event, linkIndex)
                    },
                    onEventInteraction = { event, playerAction ->
                        lastPageType = ListenerConfig.PAGE_LIVE_EVENTS
                        lastUniqueId = event.id
                        val result = RedirectHelper.tryRedirect(
                            fragment = this@LiveEventsFragment,
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
            }
        }
    }

    private fun proceedWithPlayer(event: LiveEvent, linkIndex: Int) {
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
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
                PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
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
                FloatingPlayerHelper.launchFloatingPlayerWithEvent(requireContext(), channel, event, linkIndex)
            } catch (e: Exception) {
                PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
            }
        } else {
            PlayerActivity.startWithEvent(requireContext(), event, linkIndex)
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider = { pendingEventAction },
            clearPendingAction = { pendingEventAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect = { pendingExternalRedirect = false }
        )
    }
}
