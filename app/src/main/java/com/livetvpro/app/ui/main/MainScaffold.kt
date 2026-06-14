package com.livetvpro.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.livetvpro.app.MainActivity
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.settings.SettingsActions
import com.livetvpro.app.ui.components.ComposeFloatingNav
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.dialogs.SupportDialogHost

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val PHONE_TOP_LEVEL = setOf(
    R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.settingsFragment
)
private val TV_TOP_LEVEL = setOf(
    R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.favoritesFragment, R.id.settingsFragment
)
private val DRAWER_FRAGMENTS = setOf(
    R.id.networkStreamFragment, R.id.playlistsFragment,
    R.id.cricketScoreFragment, R.id.footballScoreFragment, R.id.deviceIdFragment
)

data class NavTab(val destId: Int, val labelRes: Int, val filledIcon: Int, val outlineIcon: Int)

private val PHONE_TABS = listOf(
    NavTab(R.id.liveEventsFragment, R.string.nav_live,     R.drawable.ic_live_filled,   R.drawable.ic_live_outline),
    NavTab(R.id.homeFragment,       R.string.nav_home,     R.drawable.ic_tv_filled,     R.drawable.ic_tv_outline),
    NavTab(R.id.sportsFragment,     R.string.nav_sports,   R.drawable.ic_sports_filled, R.drawable.ic_sports_outline),
    NavTab(R.id.settingsFragment,   R.string.nav_settings, R.drawable.ic_settings,      R.drawable.ic_settings),
)
private val TV_TABS = listOf(
    NavTab(R.id.liveEventsFragment, R.string.nav_live,      R.drawable.ic_live_filled,   R.drawable.ic_live_outline),
    NavTab(R.id.homeFragment,       R.string.nav_home,      R.drawable.ic_tv_filled,     R.drawable.ic_tv_outline),
    NavTab(R.id.sportsFragment,     R.string.nav_sports,    R.drawable.ic_sports_filled, R.drawable.ic_sports_outline),
    NavTab(R.id.favoritesFragment,  R.string.nav_favorites, R.drawable.ic_star_filled,   R.drawable.ic_star_outline),
    NavTab(R.id.settingsFragment,   R.string.nav_settings,  R.drawable.ic_settings,      R.drawable.ic_settings),
)

// Fragments where the refresh button should be visible
private val REFRESH_DESTINATIONS = setOf(
    R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment,
    R.id.categoryChannelsFragment, R.id.playlistsFragment, R.id.favoritesFragment
)

@Composable
fun MainScaffold(
    activity: MainActivity,
    themeManager: ThemeManager,
    listenerManager: NativeListenerManager,
    preferencesManager: PreferencesManager,
    settingsActions: SettingsActions,
    onNavControllerReady: (NavController) -> Unit,
    onNavHostReady: (NavHostFragment) -> Unit = {},
    onDestinationChanged: (destId: Int, title: String, showRefresh: Boolean) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit,
) {
    val isTvOrDesktop = DeviceUtils.isTvDevice || DeviceUtils.isDesktop
    val isTablet      = DeviceUtils.isTablet
    val context       = LocalContext.current

    val primaryColor by themeManager.primaryColorFlow.collectAsState()

    var navController   by remember { mutableStateOf<NavController?>(null) }
    var currentDestId   by remember { mutableIntStateOf(-1) }
    var toolbarTitle    by remember { mutableStateOf("Live TV Pro") }
    // FIX: single source of truth for refresh visibility, derived from currentDestId
    val showRefreshIcon  = currentDestId in REFRESH_DESTINATIONS
    var isSearchActive  by remember { mutableStateOf(false) }
    var searchQuery     by remember { mutableStateOf("") }
    var isTopLevel      by remember { mutableStateOf(true) }

    val topLevelSet = if (isTvOrDesktop || isTablet) TV_TOP_LEVEL else PHONE_TOP_LEVEL
    val tabs        = if (isTvOrDesktop || isTablet) TV_TABS      else PHONE_TABS

    fun resolveTitle(destId: Int): String = when (destId) {
        R.id.homeFragment             -> "Categories"
        R.id.categoryChannelsFragment -> "Channels"
        R.id.liveEventsFragment       -> context.getString(R.string.app_name)
        R.id.favoritesFragment        -> "Favorites"
        R.id.sportsFragment           -> "Sports"
        R.id.settingsFragment         -> "Settings"
        R.id.networkStreamFragment    -> "Network Stream"
        R.id.playlistsFragment        -> "Playlists"
        R.id.cricketScoreFragment     -> "Cricket Score"
        R.id.footballScoreFragment    -> "Football Score"
        R.id.deviceIdFragment         -> "Device ID"
        R.id.appearanceFragment       -> "Appearance"
        else                           -> "Live TV Pro"
    }

    fun navigate(destId: Int) {
        val nav = navController ?: return
        if (nav.currentDestination?.id == destId) return
        nav.navigate(
            destId, null,
            NavOptions.Builder()
                .setPopUpTo(nav.graph.startDestinationId, false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
        )
    }

    LaunchedEffect(isSearchActive) { onSearchVisibilityChanged(isSearchActive) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isTvOrDesktop || isTablet) {
            TvTopBar(
                title          = toolbarTitle,
                tabs           = tabs,
                currentDestId  = currentDestId,
                isSearchActive = isSearchActive,
                searchQuery    = searchQuery,
                onTabSelected  = { navigate(it) },
                onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) { searchQuery = ""; activity.dispatchSearchQuery("") } },
                onQueryChange  = { q -> searchQuery = q; activity.dispatchSearchQuery(q) },
                onSearchClose  = { isSearchActive = false; searchQuery = ""; activity.dispatchSearchQuery("") },
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
                onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) { searchQuery = ""; activity.dispatchSearchQuery("") } },
                onQueryChange  = { q -> searchQuery = q; activity.dispatchSearchQuery(q) },
                onSearchClose  = { isSearchActive = false; searchQuery = ""; activity.dispatchSearchQuery("") },
                onFavorites    = { navigate(R.id.favoritesFragment) },
                onRefresh      = { activity.refreshCurrentFragment() },
            )
        }

        val containerId = remember { android.view.View.generateViewId() }

        Box(modifier = Modifier.weight(1f)) {
            SupportDialogHost()

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                update = {
                    val fm = activity.supportFragmentManager
                    if (fm.findFragmentById(containerId) == null) {
                        val navHost = NavHostFragment.create(R.navigation.nav_graph)
                        fm.beginTransaction()
                            .replace(containerId, navHost)
                            .setPrimaryNavigationFragment(navHost)
                            .commit()

                        fm.executePendingTransactions()

                        val nav = navHost.navController

                        // FIX: navigate to liveEventsFragment as the first screen,
                        // keeping homeFragment as the graph root so back-stack pop works.
                        nav.navigate(
                            R.id.liveEventsFragment, null,
                            NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .build()
                        )

                        onNavControllerReady(nav)
                        onNavHostReady(navHost)
                        navController = nav

                        if (activity.pendingDestinationId != -1) {
                            nav.navigate(activity.pendingDestinationId)
                            activity.pendingDestinationId = -1
                        }

                        nav.addOnDestinationChangedListener { _, destination, _ ->
                            currentDestId = destination.id
                            toolbarTitle  = resolveTitle(destination.id)
                            isTopLevel    = destination.id in topLevelSet
                            if (isSearchActive) { isSearchActive = false; searchQuery = "" }
                            // showRefreshIcon is now derived state — no assignment needed here
                            onDestinationChanged(destination.id, toolbarTitle, destination.id in REFRESH_DESTINATIONS)
                        }
                    }
                }
            )

            if (!isTvOrDesktop && !isTablet) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 16.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isTopLevel,
                        enter   = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                        exit    = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                    ) {
                        ComposeFloatingNav(
                            tabs          = tabs,
                            currentDestId = currentDestId,
                            primaryColor  = Color(primaryColor.takeIf { it != 0 } ?: 0xFF2AABEE.toInt()),
                            onTabSelected = { navigate(it) },
                        )
                    }
                }
            }
        }
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
                        painter           = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint              = onSurface,
                    )
                }
            } else if (!isTopLevel) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint              = onSurface,
                    )
                }
            }

            AnimatedVisibility(
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

            AnimatedVisibility(
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
            } else {
                if (showRefresh) {
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh", tint = onSurface)
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
    currentDestId: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    onTabSelected: (Int) -> Unit,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
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
                val activeDestForTab = if (currentDestId == R.id.categoryChannelsFragment) R.id.homeFragment else currentDestId
                tabs.forEach { tab ->
                    TvTabChip(
                        tab       = tab,
                        selected  = tab.destId == activeDestForTab,
                        primary   = primary,
                        onSurface = onSurface,
                        onClick   = { onTabSelected(tab.destId) },
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }

            AnimatedVisibility(isSearchActive, enter = fadeIn(), exit = fadeOut()) {
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
