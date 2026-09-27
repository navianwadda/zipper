package com.livetvpro.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

private const val HOME_CACHE_KEY = "home_session"

@Composable
fun HomeRoute(
    navController: NavController,
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
    searchQuery: String,
    searchActive: Boolean,
    refreshSignal: Int,
    onSearchQueryChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = hiltViewModel()

    var lastPageType by remember { mutableStateOf<String?>(null) }
    var lastUniqueId by remember { mutableStateOf<String?>(null) }
    var pendingNavAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExternalRedirect by remember { mutableStateOf(false) }

    val redirectLauncher = RedirectHelper.rememberRedirectLauncher(
        cooldownMgr = cooldownManager,
        pageTypeProvider = { lastPageType },
        uniqueIdProvider = { lastUniqueId },
    )

    LaunchedEffect(searchQuery) {
        viewModel.searchCategories(searchQuery)
    }

    LaunchedEffect(searchActive) {
        if (searchActive) viewModel.refreshRecentSearches()
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) viewModel.refresh()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                RedirectHelper.executePendingActionOnResume(
                    pendingActionProvider = { pendingNavAction },
                    clearPendingAction = { pendingNavAction = null },
                    pendingExternalRedirect = pendingExternalRedirect,
                    clearPendingRedirect = { pendingExternalRedirect = false },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    val spanCount = remember(configuration) {
        context.resources.getInteger(R.integer.grid_column_count)
    }

    fun launchPlayer(channel: Channel, linkIndex: Int) {
        val cacheKey = channel.categoryId.takeIf { it.isNotEmpty() } ?: HOME_CACHE_KEY

        if (DeviceUtils.isTvDevice || DeviceUtils.isTablet) {
            PlayerActivity.startWithChannel(
                context, channel, linkIndex,
                categoryId = channel.categoryId.takeIf { it.isNotEmpty() },
                channelListCacheKey = cacheKey
            )
            return
        }

        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

        if (floatingEnabled && hasPermission) {
            try {
                FloatingPlayerHelper.launchFloatingPlayer(
                    context, channel, linkIndex, channelListCacheKey = cacheKey
                )
                return
            } catch (e: Exception) {
                // Fall through to the regular player.
            }
        } else if (floatingEnabled && !hasPermission) {
            android.widget.Toast.makeText(
                context,
                "Overlay permission required for floating player. Opening normally instead.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        PlayerActivity.startWithChannel(
            context, channel, linkIndex,
            categoryId = channel.categoryId.takeIf { it.isNotEmpty() },
            channelListCacheKey = cacheKey
        )
    }

    fun redirectThenRun(channel: Channel, navAction: () -> Unit): Boolean {
        lastPageType = ListenerConfig.PAGE_HOME
        lastUniqueId = channel.id
        pendingNavAction = navAction

        val result = RedirectHelper.tryRedirect(
            context     = context,
            pageType    = ListenerConfig.PAGE_HOME,
            uniqueId    = channel.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )
        if (result == RedirectHelper.RedirectResult.REDIRECTED) {
            if (!listenerManager.isInAppRedirectEnabled()) {
                pendingExternalRedirect = true
            } else {
                pendingNavAction = null
            }
        } else {
            pendingNavAction = null
        }
        return result == RedirectHelper.RedirectResult.REDIRECTED
    }

    HomeScreen(
        viewModel = viewModel,
        spanCount = spanCount,
        isTvDevice = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet,
        searchActive = searchActive,
        searchQuery = searchQuery,
        onCategoryClick = { category ->
            navController.navigate(Routes.categoryChannels(category.id, category.name))
        },
        onCategoryInteraction = { category, navAction ->
            lastPageType = ListenerConfig.PAGE_HOME
            lastUniqueId = category.id
            pendingNavAction = navAction

            val result = RedirectHelper.tryRedirect(
                context     = context,
                pageType    = ListenerConfig.PAGE_HOME,
                uniqueId    = category.id,
                cooldownMgr = cooldownManager,
                listenerMgr = listenerManager,
                launcher    = redirectLauncher
            )
            if (result == RedirectHelper.RedirectResult.REDIRECTED) {
                if (!listenerManager.isInAppRedirectEnabled()) {
                    pendingExternalRedirect = true
                } else {
                    pendingNavAction = null
                }
            } else {
                pendingNavAction = null
            }
            result == RedirectHelper.RedirectResult.REDIRECTED
        },
        onChannelClick = { channel, linkIndex ->
            if (searchQuery.isNotBlank()) preferencesManager.addRecentSearch(searchQuery)
            val action: () -> Unit = { launchPlayer(channel, linkIndex) }
            if (!redirectThenRun(channel, action)) action()
        },
        onContinueWatchingClick = { entry ->
            val action: () -> Unit = { launchPlayer(entry.channel, entry.linkIndex) }
            if (!redirectThenRun(entry.channel, action)) action()
        },
        onRecentSearchClick = { query -> onSearchQueryChange(query) },
    )
}
