package com.livetvpro.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.livetvpro.app.R
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

@Composable
fun HomeRoute(
    navController: NavController,
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    searchQuery: String,
    refreshSignal: Int,
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

    // Mirror the old onSearchQuery(query) dispatch from SearchableFragment.
    LaunchedEffect(searchQuery) {
        viewModel.searchCategories(searchQuery)
    }

    // Mirror the old refreshData() dispatch from Refreshable.
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) viewModel.refresh()
    }

    // Mirror the old onResume() pending-redirect-action flush. The Fragment's
    // onResume() fired every time the hosting Activity resumed - e.g.
    // returning from PlayerActivity/WebActivity after a redirect - so this
    // must observe the real Activity lifecycle, not just first composition.
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

    HomeScreen(
        viewModel = viewModel,
        spanCount = spanCount,
        isTvDevice = DeviceUtils.isTvDevice || DeviceUtils.isTablet,
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
        }
    )
}
