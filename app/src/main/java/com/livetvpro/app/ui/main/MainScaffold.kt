package com.livetvpro.app.ui.main

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlinx.coroutines.launch

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
    NavTab(R.id.liveEventsFragment, R.string.nav_live,   R.drawable.ic_live_filled,    R.drawable.ic_live_outline),
    NavTab(R.id.homeFragment,       R.string.nav_home,   R.drawable.ic_tv_filled,      R.drawable.ic_tv_outline),
    NavTab(R.id.sportsFragment,     R.string.nav_sports, R.drawable.ic_sports_filled,  R.drawable.ic_sports_outline),
    NavTab(R.id.settingsFragment,   R.string.nav_settings, R.drawable.ic_settings,     R.drawable.ic_settings),
)
private val TV_TABS = listOf(
    NavTab(R.id.liveEventsFragment, R.string.nav_live,      R.drawable.ic_live_filled,   R.drawable.ic_live_outline),
    NavTab(R.id.homeFragment,       R.string.nav_home,      R.drawable.ic_tv_filled,     R.drawable.ic_tv_outline),
    NavTab(R.id.sportsFragment,     R.string.nav_sports,    R.drawable.ic_sports_filled, R.drawable.ic_sports_outline),
    NavTab(R.id.favoritesFragment,  R.string.nav_favorites, R.drawable.ic_star_filled,   R.drawable.ic_star_outline),
    NavTab(R.id.settingsFragment,   R.string.nav_settings,  R.drawable.ic_settings,      R.drawable.ic_settings),
)

@Composable
fun MainScaffold(
    activity: MainActivity,
    themeManager: ThemeManager,
    listenerManager: NativeListenerManager,
    preferencesManager: PreferencesManager,
    settingsActions: SettingsActions,
    onNavControllerReady: (NavController) -> Unit,
    onDestinationChanged: (destId: Int, title: String, showRefresh: Boolean) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit,
) {
    val isTvOrDesktop = DeviceUtils.isTvDevice || DeviceUtils.isDesktop
    val isTablet      = DeviceUtils.isTablet
    val context       = LocalContext.current

    val primaryColor by themeManager.primaryColorFlow.collectAsState()

    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()

    var navController     by remember { mutableStateOf<NavController?>(null) }
    var currentDestId     by remember { mutableIntStateOf(-1) }
    var toolbarTitle      by remember { mutableStateOf("Live TV Pro") }
    var showRefreshIcon   by remember { mutableStateOf(false) }
    var isSearchActive    by remember { mutableStateOf(false) }
    var searchQuery       by remember { mutableStateOf("") }
    var isTopLevel        by remember { mutableStateOf(true) }

    val topLevelSet = if (isTvOrDesktop || isTablet) TV_TOP_LEVEL else PHONE_TOP_LEVEL
    val tabs        = if (isTvOrDesktop || isTablet) TV_TABS      else PHONE_TABS

    fun resolveTitle(destId: Int): String = when (destId) {
        R.id.homeFragment            -> "Categories"
        R.id.categoryChannelsFragment -> "Channels"
        R.id.liveEventsFragment      -> context.getString(R.string.app_name)
        R.id.favoritesFragment       -> "Favorites"
        R.id.sportsFragment          -> "Sports"
        R.id.settingsFragment        -> "Settings"
        R.id.networkStreamFragment   -> "Network Stream"
        R.id.playlistsFragment       -> "Playlists"
        R.id.cricketScoreFragment    -> "Cricket Score"
        R.id.footballScoreFragment   -> "Football Score"
        R.id.deviceIdFragment        -> "Device ID"
        R.id.appearanceFragment      -> "Appearance"
        else                          -> "Live TV Pro"
    }

    fun resolveShowRefresh(destId: Int) = destId in setOf(
        R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment,
        R.id.categoryChannelsFragment, R.id.playlistsFragment, R.id.favoritesFragment
    )

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

    fun navigateRaw(destId: Int) { navController?.navigate(destId, null, null) }

    fun closeDrawer() { scope.launch { drawerState.close() } }

    fun onDrawerItem(itemId: Int) {
        closeDrawer()
        when (itemId) {
            R.id.floating_player_settings -> settingsActions.onSettingsFloatingPlayer()
            R.id.nav_save_states          -> settingsActions.onSettingsSaveStates()
            R.id.nav_copyright            -> settingsActions.onSettingsCopyright()
            R.id.nav_notice               -> settingsActions.onSettingsNotice()
            R.id.nav_share_app            -> settingsActions.onSettingsShareApp()
            R.id.nav_exit                 -> { scope.launch { drawerState.close() }; activity.window.decorView.postDelayed({ activity.finishAffinity() }, 250) }
            R.id.nav_contact_browser      -> {
                val url = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            R.id.nav_website              -> {
                val url = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            R.id.nav_email_us             -> {
                val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
                if (email != null) context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                        }, "Send Email"
                    )
                )
            }
            R.id.networkStreamFragment,
            R.id.playlistsFragment,
            R.id.cricketScoreFragment,
            R.id.footballScoreFragment,
            R.id.deviceIdFragment         -> navigateRaw(itemId)
            else                          -> navigate(itemId)
        }
    }

    LaunchedEffect(isSearchActive) { onSearchVisibilityChanged(isSearchActive) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTopLevel,
        drawerContent = {
            DrawerContent(
                isTvOrDesktop     = isTvOrDesktop,
                showFloatingPlayer = !isTvOrDesktop,
                onItemClick       = ::onDrawerItem,
                currentDestId     = currentDestId,
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isTvOrDesktop || isTablet) {
                TvTopBar(
                    title         = toolbarTitle,
                    tabs          = tabs,
                    currentDestId = currentDestId,
                    isSearchActive = isSearchActive,
                    searchQuery   = searchQuery,
                    onMenuClick   = { scope.launch { drawerState.open() } },
                    onTabSelected = { navigate(it) },
                    onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) { searchQuery = ""; activity.dispatchSearchQuery("") } },
                    onQueryChange = { q -> searchQuery = q; activity.dispatchSearchQuery(q) },
                    onSearchClose = { isSearchActive = false; searchQuery = ""; activity.dispatchSearchQuery("") },
                )
            } else {
                PhoneTopBar(
                    title          = toolbarTitle,
                    isTopLevel     = isTopLevel,
                    isSearchActive = isSearchActive,
                    searchQuery    = searchQuery,
                    showRefresh    = showRefreshIcon,
                    primaryColor   = Color(primaryColor.takeIf { it != 0 } ?: 0xFF2AABEE.toInt()),
                    onMenuOrBack   = {
                        if (isTopLevel) scope.launch { drawerState.open() }
                        else activity.onBackPressedDispatcher.onBackPressed()
                    },
                    onSearchToggle = { isSearchActive = !isSearchActive; if (!isSearchActive) { searchQuery = ""; activity.dispatchSearchQuery("") } },
                    onQueryChange  = { q -> searchQuery = q; activity.dispatchSearchQuery(q) },
                    onSearchClose  = { isSearchActive = false; searchQuery = ""; activity.dispatchSearchQuery("") },
                    onFavorites    = { navigate(R.id.favoritesFragment) },
                    onRefresh      = { activity.refreshCurrentFragment() },
                )
            }

            val containerId = remember { android.view.View.generateViewId() }

            Box(modifier = Modifier.weight(1f)) {
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
                            onNavControllerReady(nav)
                            navController = nav

                            if (activity.pendingDestinationId != -1) {
                                nav.navigate(activity.pendingDestinationId)
                                activity.pendingDestinationId = -1
                            }

                            nav.addOnDestinationChangedListener { _, destination, _ ->
                                currentDestId   = destination.id
                                toolbarTitle    = resolveTitle(destination.id)
                                showRefreshIcon = resolveShowRefresh(destination.id)
                                isTopLevel      = destination.id in topLevelSet
                                if (isSearchActive) { isSearchActive = false; searchQuery = "" }
                                onDestinationChanged(destination.id, toolbarTitle, showRefreshIcon)
                            }
                        }
                    }
                )
            }

            if (!isTvOrDesktop && !isTablet) {
                AnimatedVisibility(
                    visible = isTopLevel,
                    enter   = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                    exit    = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                ) {
                    ComposeFloatingNav(
                        tabs          = tabs,
                        currentDestId = currentDestId,
                        primaryColor  = Color(primaryColor.takeIf { it != 0 } ?: 0xFF2AABEE.toInt()),
                        onTabSelected = { navigate(it) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
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
    onMenuOrBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onFavorites: () -> Unit,
    onRefresh: () -> Unit,
) {
    val surface   = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
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
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenuOrBack) {
                if (isTopLevel) {
                    Icon(
                        imageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder(
                            defaultWidth = 24.dp, defaultHeight = 24.dp,
                            viewportWidth = 24f, viewportHeight = 24f
                        ).addPath(
                            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                                "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z"
                            ).toNodes(),
                            fill = androidx.compose.ui.graphics.SolidColor(onSurface)
                        ).build(),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = onSurface,
                    )
                }
            }

            AnimatedVisibility(
                visible = !isSearchActive,
                modifier = Modifier.weight(1f),
                enter = fadeIn(tween(120)),
                exit  = fadeOut(tween(80)),
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
                visible = isSearchActive,
                modifier = Modifier.weight(1f),
                enter = fadeIn(tween(120)),
                exit  = fadeOut(tween(80)),
            ) {
                BasicTextField(
                    value        = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine   = true,
                    cursorBrush  = SolidColor(primaryColor),
                    textStyle    = TextStyle(color = onSurface, fontSize = 16.sp, fontFamily = BergenSans),
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

            if (showRefresh && !isSearchActive) {
                IconButton(onClick = onRefresh) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh", tint = onSurface)
                }
            }

            if (isSearchActive) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = onSurface)
                }
            } else {
                IconButton(onClick = onSearchToggle) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = onSurface)
                }
                IconButton(onClick = onFavorites) {
                    Icon(painterResource(R.drawable.ic_star_outline), contentDescription = "Favorites", tint = onSurface)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
private fun TvTopBar(
    title: String,
    tabs: List<NavTab>,
    currentDestId: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    onMenuClick: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
) {
    val surface      = MaterialTheme.colorScheme.surface
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val primary      = MaterialTheme.colorScheme.primary
    val surfaceVar   = MaterialTheme.colorScheme.surfaceVariant
    val outline      = MaterialTheme.colorScheme.outline
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
            IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder(
                        defaultWidth = 24.dp, defaultHeight = 24.dp,
                        viewportWidth = 24f, viewportHeight = 24f
                    ).addPath(
                        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                            "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z"
                        ).toNodes(),
                        fill = androidx.compose.ui.graphics.SolidColor(onSurface)
                    ).build(),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                )
            }

            Spacer(Modifier.width(12.dp))

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
                    value        = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine   = true,
                    cursorBrush  = SolidColor(primary),
                    textStyle    = TextStyle(color = onSurface, fontSize = 15.sp, fontFamily = BergenSans),
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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

@Composable
private fun DrawerContent(
    isTvOrDesktop: Boolean,
    showFloatingPlayer: Boolean,
    currentDestId: Int,
    onItemClick: (Int) -> Unit,
) {
    val primary    = MaterialTheme.colorScheme.primary
    val onSurface  = MaterialTheme.colorScheme.onSurface
    val surface    = MaterialTheme.colorScheme.surface
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant

    ModalDrawerSheet(
        drawerContainerColor = surface,
        modifier = Modifier.width(260.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 16.dp)
        ) {
            Column {
                Icon(
                    painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Live TV Pro", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BergenSans)
                Text("Watch Live TV Anywhere", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = BergenSans)
            }
        }

        Spacer(Modifier.height(8.dp))

        data class DrawerItem(val id: Int, val icon: Int, val label: String)
        val items = buildList {
            add(DrawerItem(R.id.networkStreamFragment, R.drawable.ic_network_stream, "Network Stream"))
            add(DrawerItem(R.id.playlistsFragment,     R.drawable.ic_playlist,       "Playlists"))
            if (showFloatingPlayer)
                add(DrawerItem(R.id.floating_player_settings, R.drawable.ic_pip,    "Floating Player"))
            add(DrawerItem(R.id.cricketScoreFragment,  R.drawable.ic_cricket,        "Cricket Score"))
            add(DrawerItem(R.id.footballScoreFragment, R.drawable.ic_football,       "Football Score"))
            add(DrawerItem(R.id.nav_save_states,       R.drawable.ic_save_states,    "Save States"))
            add(DrawerItem(R.id.nav_notice,            R.drawable.ic_error_outline,  "Notice"))
            add(DrawerItem(R.id.nav_contact_browser,   R.drawable.ic_contact,        "Contact"))
            add(DrawerItem(R.id.nav_website,           R.drawable.ic_website,        "Website"))
            add(DrawerItem(R.id.nav_email_us,          R.drawable.ic_email,          "Email Us"))
            add(DrawerItem(R.id.nav_copyright,         R.drawable.ic_copyright,      "Copyright"))
            if (!isTvOrDesktop)
                add(DrawerItem(R.id.nav_share_app,     R.drawable.ic_share,          "Share App"))
            add(DrawerItem(R.id.deviceIdFragment,      R.drawable.ic_device_id,      "Device ID"))
            add(DrawerItem(R.id.nav_exit,              R.drawable.ic_exit,           "Exit"))
        }

        items.forEach { item ->
            val isSelected = item.id == currentDestId
            NavigationDrawerItem(
                icon   = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label  = { Text(item.label, fontFamily = BergenSans, fontSize = 14.sp) },
                selected = isSelected,
                onClick = { onItemClick(item.id) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor   = primary.copy(alpha = 0.12f),
                    unselectedContainerColor = Color.Transparent,
                    selectedIconColor        = primary,
                    unselectedIconColor      = onSurface.copy(alpha = 0.7f),
                    selectedTextColor        = primary,
                    unselectedTextColor      = onSurface,
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}
