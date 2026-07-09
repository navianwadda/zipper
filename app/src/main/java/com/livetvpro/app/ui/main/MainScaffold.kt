package com.livetvpro.app.ui.main

import androidx.compose.animation.AnimatedVisibility as ComposeAnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.livetvpro.app.MainActivity
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.appearance.AppearanceScreen
import com.livetvpro.app.ui.categories.CategoryChannelsRoute
import com.livetvpro.app.ui.components.ComposeFloatingNav
import com.livetvpro.app.ui.deviceid.DeviceIdScreen
import com.livetvpro.app.ui.dialogs.SupportDialogHost
import com.livetvpro.app.ui.favorites.FavoritesRoute
import com.livetvpro.app.ui.home.HomeRoute
import com.livetvpro.app.ui.live.LiveEventsRoute
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.ui.networkstream.NetworkStreamHistoryScreen
import com.livetvpro.app.ui.networkstream.NetworkStreamHistoryViewModel
import com.livetvpro.app.ui.networkstream.NetworkStreamRoute
import com.livetvpro.app.ui.playlists.PlaylistsRoute
import com.livetvpro.app.ui.score.CricketScoreScreen
import com.livetvpro.app.ui.score.FootballScoreScreen
import com.livetvpro.app.ui.settings.PlayerLayoutsScreen
import com.livetvpro.app.ui.settings.SettingsActions
import com.livetvpro.app.ui.settings.SettingsScreen
import com.livetvpro.app.ui.sports.SportsRoute
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import java.net.URLDecoder

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

data class NavTab(val route: String, val labelRes: Int, val filledIcon: Int, val outlineIcon: Int)

private val PHONE_TABS = listOf(
    NavTab(Routes.LIVE_EVENTS, R.string.nav_live,     R.drawable.ic_live_filled,   R.drawable.ic_live_outline),
    NavTab(Routes.HOME,        R.string.nav_home,     R.drawable.ic_tv_filled,     R.drawable.ic_tv_outline),
    NavTab(Routes.SPORTS,      R.string.nav_sports,   R.drawable.ic_sports_filled, R.drawable.ic_sports_outline),
    NavTab(Routes.SETTINGS,    R.string.nav_settings, R.drawable.ic_settings,      R.drawable.ic_settings),
)
private val TV_TABS = listOf(
    NavTab(Routes.LIVE_EVENTS, R.string.nav_live,      R.drawable.ic_live_filled,   R.drawable.ic_live_outline),
    NavTab(Routes.HOME,        R.string.nav_home,      R.drawable.ic_tv_filled,     R.drawable.ic_tv_outline),
    NavTab(Routes.SPORTS,      R.string.nav_sports,    R.drawable.ic_sports_filled, R.drawable.ic_sports_outline),
    NavTab(Routes.FAVORITES,   R.string.nav_favorites, R.drawable.ic_star_filled,   R.drawable.ic_star_outline),
    NavTab(Routes.SETTINGS,    R.string.nav_settings,  R.drawable.ic_settings,      R.drawable.ic_settings),
)

@Composable
fun MainScaffold(
    activity: MainActivity,
    themeManager: ThemeManager,
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
    settingsActions: SettingsActions,
    onNavControllerReady: (NavController) -> Unit,
    onDestinationChanged: (route: String?, title: String, showRefresh: Boolean) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit,
) {
    val isTvOrDesktop = DeviceUtils.isTvDevice || DeviceUtils.isDesktop
    val isTablet      = DeviceUtils.isTablet
    val context       = LocalContext.current

    val primaryColor by themeManager.primaryColorFlow.collectAsState()

    val navController  = rememberNavController()
    var currentRoute   by remember { mutableStateOf<String?>(null) }
    var toolbarTitle   by remember { mutableStateOf("Live TV Pro") }
    val showRefreshIcon = currentRoute in Routes.REFRESH_DESTINATIONS
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery    by remember { mutableStateOf("") }
    var refreshSignal  by remember { mutableIntStateOf(0) }
    var isTopLevel     by remember { mutableStateOf(true) }
    var categoryTitle  by remember { mutableStateOf<String?>(null) }

    val topLevelSet = if (isTvOrDesktop || isTablet) Routes.TV_TOP_LEVEL else Routes.PHONE_TOP_LEVEL
    val tabs        = if (isTvOrDesktop || isTablet) TV_TABS             else PHONE_TABS

    fun resolveTitle(route: String?, resolvedCategoryName: String?): String = when (route) {
        Routes.HOME              -> "Categories"
        Routes.CATEGORY_CHANNELS -> resolvedCategoryName ?: "Channels"
        Routes.LIVE_EVENTS       -> context.getString(R.string.app_name)
        Routes.FAVORITES         -> "Favorites"
        Routes.SPORTS            -> "Sports"
        Routes.SETTINGS          -> "Settings"
        Routes.NETWORK_STREAM    -> "Network Stream"
        Routes.NETWORK_STREAM_HISTORY -> "History"
        Routes.PLAYLISTS         -> "Playlists"
        Routes.CRICKET_SCORE     -> "Cricket Score"
        Routes.FOOTBALL_SCORE    -> "Football Score"
        Routes.DEVICE_ID         -> "Device ID"
        Routes.APPEARANCE        -> "Appearance"
        Routes.PLAYER_LAYOUTS    -> "Player Settings"
        else                     -> "Live TV Pro"
    }

    fun navigate(route: String) {
        if (navController.currentDestination?.route == route) return
        navController.navigate(
            route,
            NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
        )
    }

    fun navigateForward(route: String) {
        if (navController.currentDestination?.route == route) return
        navController.navigate(
            route,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build()
        )
    }

    LaunchedEffect(isSearchActive) { onSearchVisibilityChanged(isSearchActive) }
    LaunchedEffect(navController) { onNavControllerReady(navController) }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route
            currentRoute = route
         
            if (route != Routes.CATEGORY_CHANNELS) categoryTitle = null
            toolbarTitle = resolveTitle(route, categoryTitle)
            isTopLevel   = route in topLevelSet
            if (isSearchActive) { isSearchActive = false; searchQuery = "" }
            onDestinationChanged(route, toolbarTitle, route in Routes.REFRESH_DESTINATIONS)
        }
    }

    LaunchedEffect(categoryTitle, currentRoute) {
        if (currentRoute == Routes.CATEGORY_CHANNELS) {
            toolbarTitle = resolveTitle(currentRoute, categoryTitle)
            onDestinationChanged(currentRoute, toolbarTitle, currentRoute in Routes.REFRESH_DESTINATIONS)
        }
    }

    val historyViewModel: NetworkStreamHistoryViewModel? =
        if (currentRoute == Routes.NETWORK_STREAM_HISTORY) {
            hiltViewModel(navController.getBackStackEntry(Routes.NETWORK_STREAM_HISTORY))
        } else null
    var showHistoryClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isTvOrDesktop || isTablet) {
            TvTopBar(
                title          = toolbarTitle,
                tabs           = tabs,
                currentRoute   = currentRoute,
                isSearchActive = isSearchActive,
                searchQuery    = searchQuery,
                onTabSelected  = { navigate(it) },
                onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" },
                onQueryChange  = { q -> searchQuery = q },
                onSearchClose  = { isSearchActive = false; searchQuery = "" },
                showHistory    = currentRoute == Routes.NETWORK_STREAM,
                onHistory      = { navigateForward(Routes.NETWORK_STREAM_HISTORY) },
                isHistoryRoute       = currentRoute == Routes.NETWORK_STREAM_HISTORY,
                historyNewestFirst   = historyViewModel?.newestFirst ?: true,
                onHistorySortSelect  = { historyViewModel?.updateSortOrder(it) },
                onHistoryClearAll    = { showHistoryClearDialog = true },
            )
        } else {
            PhoneTopBar(
                title          = toolbarTitle,
                isTopLevel     = isTopLevel,
                isSearchActive = isSearchActive,
                searchQuery    = searchQuery,
                showRefresh    = showRefreshIcon,
                primaryColor   = Color(primaryColor.takeIf { it != 0 } ?: 0xFF2AABEE.toInt()),
                onBack         = { activity.onBackPressedDispatcher.onBackPressed() },
                onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" },
                onQueryChange  = { q -> searchQuery = q },
                onSearchClose  = { isSearchActive = false; searchQuery = "" },
                onFavorites    = { navigateForward(Routes.FAVORITES) },
                onRefresh      = { refreshSignal++ },
                showHistory    = currentRoute == Routes.NETWORK_STREAM,
                onHistory      = { navigateForward(Routes.NETWORK_STREAM_HISTORY) },
                isHistoryRoute       = currentRoute == Routes.NETWORK_STREAM_HISTORY,
                historyNewestFirst   = historyViewModel?.newestFirst ?: true,
                onHistorySortSelect  = { historyViewModel?.updateSortOrder(it) },
                onHistoryClearAll    = { showHistoryClearDialog = true },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            SupportDialogHost()

            NavHost(
                navController = navController,
                startDestination = Routes.LIVE_EVENTS,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(300)) { it / 3 } + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(300))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(300)) { it / 3 } + fadeOut(animationSpec = tween(300))
                },
            ) {
                composable(Routes.HOME) {
                    HomeRoute(
                        navController = navController,
                        listenerManager = listenerManager,
                        cooldownManager = cooldownManager,
                        searchQuery = searchQuery,
                        refreshSignal = refreshSignal,
                    )
                }
                composable(
                    Routes.CATEGORY_CHANNELS,
                    arguments = listOf(
                        navArgument(Routes.Args.CATEGORY_ID) { type = NavType.StringType },
                        navArgument(Routes.Args.CATEGORY_NAME) { type = NavType.StringType },
                    )
                ) { backStackEntry ->
                    val rawId = backStackEntry.arguments?.getString(Routes.Args.CATEGORY_ID)
                    val categoryId = rawId?.let { URLDecoder.decode(it, "UTF-8") }
                    CategoryChannelsRoute(
                        categoryId = categoryId,
                        listenerManager = listenerManager,
                        cooldownManager = cooldownManager,
                        preferencesManager = preferencesManager,
                        searchQuery = searchQuery,
                        refreshSignal = refreshSignal,
                        onTitleResolved = { name -> categoryTitle = name },
                    )
                }
                composable(Routes.LIVE_EVENTS) {
                    LiveEventsRoute(
                        listenerManager = listenerManager,
                        cooldownManager = cooldownManager,
                        preferencesManager = preferencesManager,
                        searchQuery = searchQuery,
                        refreshSignal = refreshSignal,
                    )
                }
                composable(Routes.SPORTS) {
                    SportsRoute(
                        listenerManager = listenerManager,
                        cooldownManager = cooldownManager,
                        preferencesManager = preferencesManager,
                        searchQuery = searchQuery,
                        refreshSignal = refreshSignal,
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesRoute(
                        listenerManager = listenerManager,
                        cooldownManager = cooldownManager,
                        preferencesManager = preferencesManager,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        navController = navController,
                        listenerManager = listenerManager,
                        settingsActions = settingsActions,
                    )
                }
                composable(Routes.PLAYLISTS) {
                    PlaylistsRoute(navController = navController)
                }
                composable(Routes.NETWORK_STREAM) {
                    NetworkStreamRoute(preferencesManager = preferencesManager)
                }
                composable(Routes.NETWORK_STREAM_HISTORY) {
                    NetworkStreamHistoryScreen(
                        navController = navController,
                        viewModel     = hiltViewModel(navController.getBackStackEntry(Routes.NETWORK_STREAM_HISTORY)),
                    )
                }
                composable(Routes.CRICKET_SCORE) {
                    CricketScoreScreen(listenerManager = listenerManager)
                }
                composable(Routes.FOOTBALL_SCORE) {
                    FootballScoreScreen(listenerManager = listenerManager)
                }
                composable(Routes.DEVICE_ID) {
                    DeviceIdScreen()
                }
                composable(Routes.APPEARANCE) {
                    AppearanceScreen(themeManager = themeManager)
                }
                composable(Routes.PLAYER_LAYOUTS) {
                    PlayerLayoutsScreen(preferencesManager = preferencesManager)
                }
            }

            if (!isTvOrDesktop && !isTablet) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 16.dp),
                ) {
                    Column {
                        ComposeAnimatedVisibility(
                            visible = isTopLevel,
                            enter   = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                            exit    = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                        ) {
                            ComposeFloatingNav(
                                tabs          = tabs,
                                currentRoute  = currentRoute,
                                primaryColor  = Color(primaryColor.takeIf { it != 0 } ?: 0xFF2AABEE.toInt()),
                                onTabSelected = { navigate(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHistoryClearDialog && historyViewModel != null) {
        AlertDialog(
            onDismissRequest = { showHistoryClearDialog = false },
            icon             = {
                Icon(
                    painter            = painterResource(R.drawable.ic_delete_sweep),
                    contentDescription = null,
                )
            },
            title            = { Text("Clear History") },
            text             = { Text("Are you sure, all your watch history will be deleted.") },
            confirmButton    = {
                TextButton(onClick = {
                    historyViewModel.clearAll()
                    showHistoryClearDialog = false
                }) {
                    Text("Clear All")
                }
            },
            dismissButton    = {
                TextButton(onClick = { showHistoryClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PhoneTopBar(
    title: String,
    isTopLevel: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    showRefresh: Boolean,
    primaryColor: Color,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onFavorites: () -> Unit,
    onRefresh: () -> Unit,
    showHistory: Boolean = false,
    onHistory: () -> Unit = {},
    isHistoryRoute: Boolean = false,
    historyNewestFirst: Boolean = true,
    onHistorySortSelect: (Boolean) -> Unit = {},
    onHistoryClearAll: () -> Unit = {},
) {
    val surface        = MaterialTheme.colorScheme.surface
    val onSurface      = MaterialTheme.colorScheme.onSurface
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSearchActive) {
                IconButton(onClick = onSearchClose) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint               = onSurface,
                    )
                }
            } else if (!isTopLevel) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint               = onSurface,
                    )
                }
            }

            ComposeAnimatedVisibility(
                visible  = !isSearchActive,
                modifier = Modifier.weight(1f),
                enter    = fadeIn(tween(120)),
                exit     = fadeOut(tween(80)),
            ) {
                Text(
                    text       = title,
                    color      = onSurface,
                    fontSize   = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BergenSans,
                    maxLines   = 1,
                    modifier   = Modifier.basicMarquee(),
                )
            }

            ComposeAnimatedVisibility(
                visible  = isSearchActive,
                modifier = Modifier.weight(1f),
                enter    = fadeIn(tween(120)),
                exit     = fadeOut(tween(80)),
            ) {
                BasicTextField(
                    value         = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine    = true,
                    cursorBrush   = SolidColor(primaryColor),
                    textStyle     = TextStyle(color = onSurface, fontSize = 16.sp, fontFamily = BergenSans),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Search…", color = onSurface.copy(alpha = 0.45f), fontSize = 16.sp, fontFamily = BergenSans)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }

            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear", tint = onSurface)
                    }
                }
            } else if (isHistoryRoute) {
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort", tint = onSurface)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Text("Newest First")
                                    RadioButton(selected = historyNewestFirst, onClick = null)
                                }
                            },
                            onClick = { onHistorySortSelect(true); showSortMenu = false },
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Text("Oldest First")
                                    RadioButton(selected = !historyNewestFirst, onClick = null)
                                }
                            },
                            onClick = { onHistorySortSelect(false); showSortMenu = false },
                        )
                    }
                }
                IconButton(onClick = onHistoryClearAll) {
                    Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = "Clear history", tint = onSurface)
                }
            } else {
                if (showRefresh) {
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh", tint = onSurface)
                    }
                }
                if (showHistory) {
                    IconButton(onClick = onHistory) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = "History", tint = onSurface)
                    }
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = onSurface)
                }
                IconButton(onClick = onFavorites) {
                    Icon(painterResource(R.drawable.ic_star_outline), contentDescription = "Favorites", tint = onSurface)
                }
            }
        }
    }
}

@Composable
private fun TvTopBar(
    title: String,
    tabs: List<NavTab>,
    currentRoute: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    onTabSelected: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    showHistory: Boolean = false,
    onHistory: () -> Unit = {},
    isHistoryRoute: Boolean = false,
    historyNewestFirst: Boolean = true,
    onHistorySortSelect: (Boolean) -> Unit = {},
    onHistoryClearAll: () -> Unit = {},
) {
    val surface        = MaterialTheme.colorScheme.surface
    val onSurface      = MaterialTheme.colorScheme.onSurface
    val primary        = MaterialTheme.colorScheme.primary
    val surfaceVar     = MaterialTheme.colorScheme.surfaceVariant
    val outline        = MaterialTheme.colorScheme.outline
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeRouteForTab = if (currentRoute == Routes.CATEGORY_CHANNELS) Routes.HOME else currentRoute
                tabs.forEach { tab ->
                    TvTabChip(
                        tab       = tab,
                        selected  = tab.route == activeRouteForTab,
                        primary   = primary,
                        onSurface = onSurface,
                        onClick   = { onTabSelected(tab.route) },
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }

            ComposeAnimatedVisibility(isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                BasicTextField(
                    value         = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine    = true,
                    cursorBrush   = SolidColor(primary),
                    textStyle     = TextStyle(color = onSurface, fontSize = 15.sp, fontFamily = BergenSans),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(surfaceVar)
                                .border(1.dp, outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) Text("Search…", color = onSurface.copy(alpha = 0.45f), fontSize = 15.sp, fontFamily = BergenSans)
                            inner()
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }

            IconButton(onClick = { if (isSearchActive) onSearchClose() else onSearchToggle() }) {
                Icon(
                    painterResource(if (isSearchActive) R.drawable.ic_close else R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = onSurface,
                )
            }

            if (showHistory) {
                IconButton(onClick = onHistory) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = "History", tint = onSurface)
                }
            }

            if (isHistoryRoute) {
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort", tint = onSurface)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Text("Newest First")
                                    RadioButton(selected = historyNewestFirst, onClick = null)
                                }
                            },
                            onClick = { onHistorySortSelect(true); showSortMenu = false },
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Text("Oldest First")
                                    RadioButton(selected = !historyNewestFirst, onClick = null)
                                }
                            },
                            onClick = { onHistorySortSelect(false); showSortMenu = false },
                        )
                    }
                }
                IconButton(onClick = onHistoryClearAll) {
                    Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = "Clear history", tint = onSurface)
                }
            }

            Text(
                text       = stringResource(R.string.app_name),
                color      = onSurface.copy(alpha = 0.75f),
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BergenSans,
                maxLines   = 1,
                modifier   = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun TvTabChip(
    tab: NavTab,
    selected: Boolean,
    primary: Color,
    onSurface: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(120), label = "tab")

    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) primary.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (focused) primary else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp)
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = stringResource(tab.labelRes),
            color      = if (selected) primary else onSurface.copy(alpha = 0.7f),
            fontSize   = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = BergenSans,
        )
    }
}
