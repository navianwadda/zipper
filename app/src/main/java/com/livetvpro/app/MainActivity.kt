package com.livetvpro.app
import com.livetvpro.app.ui.settings.SettingsActions

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import android.widget.ImageView
import android.widget.TextView
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.databinding.ActivityMainBinding
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

interface SearchableFragment {
    fun onSearchQuery(query: String)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SettingsActions {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var dataRepository: com.livetvpro.app.data.repository.NativeDataRepository

    private var drawerToggle: ActionBarDrawerToggle? = null
    private var isSearchVisible = false
    private var showRefreshIcon = false
    private var backPressedTime = 0L



    private var phoneToolbar: com.google.android.material.appbar.MaterialToolbar? = null
    private var phoneToolbarTitle: android.widget.TextView? = null
    private var phoneBtnSearch: android.widget.ImageButton? = null
    private var phoneBtnFavorites: android.widget.ImageButton? = null
    private var phoneSearchView: androidx.appcompat.widget.SearchView? = null
    private var phoneBtnSearchClear: android.widget.ImageButton? = null

    companion object {
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            FloatingPlayerDialog.newInstance().show(supportFragmentManager, FloatingPlayerDialog.TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeManager.applyTheme()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyBergenSansToNavigationMenu()

        if (!dataRepository.isDataLoaded()) {
            startActivity(Intent(this, com.livetvpro.app.ui.SplashActivity::class.java))
            finish()
            return
        }

        if (DeviceUtils.isTvDevice) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setupTvNavigation()
        } else {
            handleStatusBarForOrientation()
            setupToolbar()
            setupDrawer()
            setupNavigation()
            setupSearch()
        }

        binding.root.post { handleNotificationIntent(intent) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val drawerLayout = binding.root.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentDestId = navHostFragment?.navController?.currentDestination?.id
                val topLevelDestinations = if (DeviceUtils.isTvDevice) {
                    setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.favoritesFragment)
                } else {
                    setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment)
                }
                val isTopLevel = currentDestId in topLevelDestinations

                when {
                    drawerLayout?.isDrawerOpen(GravityCompat.START) == true ->
                        drawerLayout.closeDrawer(GravityCompat.START)
                    DeviceUtils.isTvDevice && isSearchVisible -> hideTvSearch()
                    !DeviceUtils.isTvDevice && isSearchVisible -> hideSearch()
                    isTopLevel -> {
                        val now = System.currentTimeMillis()
                        if (now - backPressedTime < 2000) {
                            finishAffinity()
                        } else {
                            backPressedTime = now
                            if (DeviceUtils.isTvDevice) {
                                com.google.android.material.snackbar.Snackbar
                                    .make(binding.root, "Press Back again to exit", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    .show()
                            } else {
                                Toast.makeText(this@MainActivity, "Press again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun applyBergenSansToNavigationMenu() {
        val bergenSans = ResourcesCompat.getFont(this, R.font.bergen_sans) ?: return
        val navigationView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_view) ?: return
        val menu = navigationView.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spannable = SpannableString(item.title)
            spannable.setSpan(CustomTypefaceSpan(bergenSans), 0, spannable.length, 0)
            item.title = spannable
            val subMenu = item.subMenu
            if (subMenu != null) {
                for (j in 0 until subMenu.size()) {
                    val subItem = subMenu.getItem(j)
                    val subSpannable = SpannableString(subItem.title)
                    subSpannable.setSpan(CustomTypefaceSpan(bergenSans), 0, subSpannable.length, 0)
                    subItem.title = subSpannable
                }
            }
        }
    }

    private fun handleStatusBarForOrientation() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.background_dark)

        windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        windowInsetsController.isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }

        applyInsets()
    }

    private fun applyInsets() {
        val root = binding.root
        val appBarLayout = root.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar_layout)
        val bottomNavigation = root.findViewById<android.widget.LinearLayout>(R.id.bottom_navigation)
        val navigationView = root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_view)
        val headerView = navigationView?.getHeaderView(0)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            appBarLayout?.setPadding(0, statusBars.top, 0, 0)

            bottomNavigation?.let { nav ->
                val lp = nav.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                if (lp != null) {
                    lp.bottomMargin = navBars.bottom + nav.resources.getDimensionPixelSize(R.dimen.nav_bottom_margin)
                    nav.layoutParams = lp
                }
            }

            headerView?.setPadding(
                headerView.paddingLeft,
                statusBars.top,
                headerView.paddingRight,
                headerView.paddingBottom
            )

            navigationView?.setPadding(0, 0, 0, navBars.bottom)

            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
        if (!DeviceUtils.isTvDevice) {
            if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && isSearchVisible) {
                hideSearch()
            }
            handleStatusBarForOrientation()
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            navHostFragment?.childFragmentManager?.fragments?.firstOrNull()?.onConfigurationChanged(newConfig)
        }
    }

    private fun setupTvNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        val drawerLayout = binding.root.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        val navigationView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_view)

        navigationView?.isFocusable = true
        navigationView?.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS

        navigationView?.menu?.findItem(R.id.floating_player_settings)?.isVisible = false
        navigationView?.menu?.findItem(R.id.nav_share_app)?.isVisible = false

        val tvToolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tvToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, tvToolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        ).apply {
            isDrawerIndicatorEnabled = true
            syncState()
        }
        drawerToggle?.let { drawerLayout?.addDrawerListener(it) }

        drawerLayout?.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                navigationView?.requestFocus()
            }
            override fun onDrawerClosed(drawerView: android.view.View) {
                binding.root.findViewById<android.widget.TextView>(R.id.tv_tab_live)?.requestFocus()
            }
        })

        val tabDestinations = mapOf(
            R.id.tv_tab_live      to R.id.liveEventsFragment,
            R.id.tv_tab_home      to R.id.homeFragment,
            R.id.tv_tab_sports    to R.id.sportsFragment,
            R.id.tv_tab_favorites to R.id.favoritesFragment
        )

        fun selectTab(selectedDestId: Int) {
            tabDestinations.forEach { (viewId, destId) ->
                val tab = binding.root.findViewById<android.widget.TextView>(viewId) ?: return@forEach
                tab.isSelected = destId == selectedDestId
            }
        }

        fun navigate(destinationId: Int) {
            val currentId = navController.currentDestination?.id ?: return
            if (currentId == destinationId) return
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            navController.navigate(destinationId, null, navOptions)
        }

        tabDestinations.forEach { (viewId, destId) ->
            val tab = binding.root.findViewById<android.widget.TextView>(viewId)
            tab?.setOnClickListener { navigate(destId) }
            tab?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) navigate(destId)
            }
            tab?.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        navigate(destId)
                        true
                    }
                    else -> false
                }
            }
        }

        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.liveEventsFragment,
            R.id.sportsFragment,
            R.id.favoritesFragment
        )

        navigationView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.floating_player_settings -> {
                    onSettingsFloatingPlayer()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_save_states -> {
                    onSettingsSaveStates()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_copyright -> {
                    onSettingsCopyright()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_notice -> {
                    onSettingsNotice()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_exit -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    drawerLayout?.postDelayed({ finishAffinity() }, 250)
                    false
                }
                R.id.nav_share_app -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    onSettingsShareApp()
                    false
                }
                R.id.nav_contact_browser -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val contactUrl = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
                    if (contactUrl != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contactUrl)))
                    false
                }
                R.id.nav_website -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val webUrl = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
                    if (webUrl != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                    false
                }
                R.id.nav_email_us -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
                    if (email != null) {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                        }
                        startActivity(Intent.createChooser(intent, "Send Email"))
                    }
                    false
                }
                R.id.networkStreamFragment, R.id.playlistsFragment,
                R.id.cricketScoreFragment, R.id.footballScoreFragment,
                R.id.deviceIdFragment -> {
                    val destId = menuItem.itemId
                    navigateAfterDrawerClose(drawerLayout, GravityCompat.START) {
                        navController.navigate(destId, null, null)
                    }
                    true
                }
                else -> {
                    if (menuItem.itemId in topLevelDestinations) navigate(menuItem.itemId)
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }

        val drawerFragments = setOf(
            R.id.networkStreamFragment, R.id.playlistsFragment,
            R.id.cricketScoreFragment, R.id.footballScoreFragment,
            R.id.deviceIdFragment
        )

        val globalErrorOverlay = binding.root.findViewById<android.view.View>(R.id.global_error_overlay)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            globalErrorOverlay?.visibility = android.view.View.GONE

            val activeDestId = when (destination.id) {
                R.id.categoryChannelsFragment -> R.id.homeFragment
                else -> destination.id
            }
            selectTab(activeDestId)

            if (destination.id !in drawerFragments) {
                navigationView?.checkedItem?.isChecked = false
            }

            val isTopLevel = destination.id in topLevelDestinations
            if (isTopLevel) {
                drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                drawerToggle?.isDrawerIndicatorEnabled = true
                animateNavigationIcon(0f)
                tvToolbar?.setNavigationOnClickListener {
                    if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        drawerLayout?.openDrawer(GravityCompat.START)
                    }
                }
            } else {
                drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerToggle?.isDrawerIndicatorEnabled = true
                animateNavigationIcon(1f)
                tvToolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            }

            if (isSearchVisible) hideTvSearch()
        }

        binding.root.post { selectTab(navController.currentDestination?.id ?: navController.graph.startDestinationId) }

        binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search)
            ?.setOnClickListener { if (isSearchVisible) hideTvSearch() else showTvSearch() }

        val tvSearchView = binding.root.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
        val tvClearBtn = binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search_clear)
        tvClearBtn?.visibility = View.GONE

        tvSearchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { query ->
                    val nhf = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val frag = nhf?.childFragmentManager?.fragments?.firstOrNull()
                    if (frag is SearchableFragment) frag.onSearchQuery(query)
                }
                return true
            }
        })

        tvClearBtn?.setOnClickListener { hideTvSearch() }
    }

    private fun animateTvSearchWeight(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        val tvSearchBar = binding.root.findViewById<View>(R.id.tv_search_bar) ?: return
        val lp = tvSearchBar.layoutParams as? android.widget.LinearLayout.LayoutParams ?: return
        ValueAnimator.ofFloat(from, to).apply {
            duration = 220
            interpolator = if (to > from) android.view.animation.DecelerateInterpolator()
            else android.view.animation.AccelerateInterpolator()
            addUpdateListener { va ->
                lp.weight = va.animatedValue as Float
                tvSearchBar.layoutParams = lp
            }
            onEnd?.let {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { it() }
                })
            }
            start()
        }
    }

    private fun showTvSearch() {
        isSearchVisible = true
        val tvSearchBar = binding.root.findViewById<View>(R.id.tv_search_bar) ?: return
        val lp = tvSearchBar.layoutParams as? android.widget.LinearLayout.LayoutParams ?: return
        lp.weight = 0f
        tvSearchBar.layoutParams = lp
        tvSearchBar.visibility = View.VISIBLE
        binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search_clear)?.visibility = View.VISIBLE
        animateTvSearchWeight(0f, 2f)
        val searchView = binding.root.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
        searchView?.post {
            searchView.isIconified = false
            searchView.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchView.windowToken, 0)
        }
    }

    private fun hideTvSearch() {
        isSearchVisible = false
        val searchView = binding.root.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
        searchView?.setQuery("", false)
        searchView?.clearFocus()
        binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search_clear)?.visibility = View.GONE
        animateTvSearchWeight(2f, 0f) {
            binding.root.findViewById<View>(R.id.tv_search_bar)?.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar) ?: return
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (DeviceUtils.isTvDevice) return false
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (DeviceUtils.isTvDevice) return false
        menu.findItem(R.id.action_refresh)?.isVisible = showRefreshIcon
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is Refreshable) currentFragment.refreshData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupDrawer() {
        val drawerLayout = binding.root.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout) ?: return
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar) ?: return
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        ).apply {
            isDrawerIndicatorEnabled = true
            isDrawerSlideAnimationEnabled = true
            syncState()
        }
        drawerToggle?.let { drawerLayout.addDrawerListener(it) }

        val navigationView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_view) ?: return
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        val drawerLayout = binding.root.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        val navigationView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_view)
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val toolbarTitle = binding.root.findViewById<android.widget.TextView>(R.id.toolbar_title)
        val bottomNavigation = binding.root.findViewById<android.widget.LinearLayout>(R.id.bottom_navigation)
        val navItemLive = binding.root.findViewById<android.widget.LinearLayout>(R.id.nav_item_live)
        val navItemHome = binding.root.findViewById<android.widget.LinearLayout>(R.id.nav_item_home)
        val navItemSports = binding.root.findViewById<android.widget.LinearLayout>(R.id.nav_item_sports)
        val navItemSettings = binding.root.findViewById<android.widget.LinearLayout>(R.id.nav_item_settings)

        val navIconLive = binding.root.findViewById<android.widget.ImageView>(R.id.nav_icon_live)
        val navIconHome = binding.root.findViewById<android.widget.ImageView>(R.id.nav_icon_home)
        val navIconSports = binding.root.findViewById<android.widget.ImageView>(R.id.nav_icon_sports)
        val navIconSettings = binding.root.findViewById<android.widget.ImageView>(R.id.nav_icon_settings)

        val navLabelLive = binding.root.findViewById<android.widget.TextView>(R.id.nav_label_live)
        val navLabelHome = binding.root.findViewById<android.widget.TextView>(R.id.nav_label_home)
        val navLabelSports = binding.root.findViewById<android.widget.TextView>(R.id.nav_label_sports)
        val navLabelSettings = binding.root.findViewById<android.widget.TextView>(R.id.nav_label_settings)

        val navItems = mapOf(
            R.id.liveEventsFragment to Triple(navItemLive, navIconLive, navLabelLive),
            R.id.homeFragment to Triple(navItemHome, navIconHome, navLabelHome),
            R.id.sportsFragment to Triple(navItemSports, navIconSports, navLabelSports),
            R.id.settingsFragment to Triple(navItemSettings, navIconSettings, navLabelSettings)
        )

        val navFilledIcons = mapOf(
            R.id.liveEventsFragment to R.drawable.ic_live_filled,
            R.id.homeFragment to R.drawable.ic_tv_filled,
            R.id.sportsFragment to R.drawable.ic_sports_filled,
            R.id.settingsFragment to R.drawable.ic_settings
        )

        val navOutlineIcons = mapOf(
            R.id.liveEventsFragment to R.drawable.ic_live_outline,
            R.id.homeFragment to R.drawable.ic_tv_outline,
            R.id.sportsFragment to R.drawable.ic_sports_outline,
            R.id.settingsFragment to R.drawable.ic_settings
        )

        val floatingNav = bottomNavigation as? com.livetvpro.app.ui.components.FloatingNavView
        floatingNav?.setTabCount(navItems.size)

        val tabIndexMap = mapOf(
            R.id.liveEventsFragment to 0,
            R.id.homeFragment to 1,
            R.id.sportsFragment to 2,
            R.id.settingsFragment to 3
        )
        var firstSelection = true

        fun updateNavSelection(selectedId: Int) {
            for ((destId, triple) in navItems) {
                val (_, icon, label) = triple
                val isSelected = destId == selectedId
                val activeColor = android.graphics.Color.parseColor("#2AABEE")
                val inactiveColor = android.graphics.Color.parseColor("#6B7280")
                icon.setImageResource(if (isSelected) navFilledIcons[destId]!! else navOutlineIcons[destId]!!)
                icon.setColorFilter(if (isSelected) activeColor else inactiveColor, android.graphics.PorterDuff.Mode.SRC_IN)
                label.setTextColor(if (isSelected) activeColor else inactiveColor)
            }
            tabIndexMap[selectedId]?.let { index ->
                floatingNav?.selectTab(index, animate = !firstSelection)
                firstSelection = false
            }
        }

        val btnSearch = binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search)
        val btnFavorites = binding.root.findViewById<android.widget.ImageButton>(R.id.btn_favorites)
        val searchView = binding.root.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
        val btnSearchClear = binding.root.findViewById<android.widget.ImageButton>(R.id.btn_search_clear)

        val topLevelDestinations = setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.settingsFragment)
        val graphStartDestinationId = navController.graph.startDestinationId

        val navigateTopLevel = fun(destinationId: Int) {
            val currentId = navController.currentDestination?.id ?: graphStartDestinationId
            if (currentId != destinationId) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, false, saveState = true)
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .build()
                navController.navigate(destinationId, null, navOptions)
            }
        }

        navigationView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.floating_player_settings -> {
                    onSettingsFloatingPlayer()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_save_states -> {
                    onSettingsSaveStates()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_copyright -> {
                    onSettingsCopyright()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_notice -> {
                    onSettingsNotice()
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_exit -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    drawerLayout?.postDelayed({ finishAffinity() }, 250)
                    false
                }
                R.id.nav_share_app -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    onSettingsShareApp()
                    false
                }
                R.id.nav_contact_browser -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val contactUrl = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
                    if (contactUrl != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contactUrl)))
                    false
                }
                R.id.nav_website -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val webUrl = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
                    if (webUrl != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                    false
                }
                R.id.nav_email_us -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
                    if (email != null) {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                        }
                        startActivity(Intent.createChooser(intent, "Send Email"))
                    }
                    false
                }
                R.id.networkStreamFragment, R.id.playlistsFragment,
                R.id.cricketScoreFragment, R.id.footballScoreFragment,
                R.id.deviceIdFragment -> {
                    val destId = menuItem.itemId
                    navigateAfterDrawerClose(drawerLayout, GravityCompat.START) {
                        navController.navigate(destId, null, null)
                    }
                    true
                }
                else -> {
                    if (menuItem.itemId in topLevelDestinations) navigateTopLevel(menuItem.itemId)
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }

        navItemLive?.setOnClickListener { navigateTopLevel(R.id.liveEventsFragment) }
        navItemHome?.setOnClickListener { navigateTopLevel(R.id.homeFragment) }
        navItemSports?.setOnClickListener { navigateTopLevel(R.id.sportsFragment) }
        navItemSettings?.setOnClickListener { navigateTopLevel(R.id.settingsFragment) }

        val drawerFragments2 = setOf(
            R.id.networkStreamFragment, R.id.playlistsFragment,
            R.id.cricketScoreFragment, R.id.footballScoreFragment,
            R.id.deviceIdFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id !in drawerFragments2) {
                navigationView?.checkedItem?.isChecked = false
            }
            toolbarTitle?.text = when (destination.id) {
                R.id.homeFragment -> "Categories"
                R.id.categoryChannelsFragment -> "Channels"
                R.id.liveEventsFragment -> getString(R.string.app_name)
                R.id.favoritesFragment -> "Favorites"
                R.id.sportsFragment -> "Sports"
                R.id.networkStreamFragment -> "Network Stream"
                R.id.playlistsFragment -> "Playlists"
                R.id.cricketScoreFragment -> "Cricket Score"
                R.id.footballScoreFragment -> "Football Score"
                R.id.deviceIdFragment -> "Device ID"
                else -> "Live TV Pro"
            }
            showRefreshIcon = when (destination.id) {
                R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment,
                R.id.categoryChannelsFragment, R.id.playlistsFragment, R.id.favoritesFragment -> true
                else -> false
            }
            invalidateOptionsMenu()

            val isTopLevel = destination.id in topLevelDestinations
            val isNetworkStream = destination.id == R.id.networkStreamFragment

            if (isNetworkStream) {
                btnSearch?.visibility = View.GONE
                btnFavorites?.visibility = View.GONE
            } else {
                btnSearch?.visibility = View.VISIBLE
                btnFavorites?.visibility = View.VISIBLE
            }

            if (isTopLevel) {
                updateNavSelection(destination.id)
                bottomNavigation?.let { nav ->
                    if (nav.translationY != 0f || nav.visibility != View.VISIBLE) {
                        nav.visibility = View.VISIBLE
                        nav.animate().translationY(0f).setDuration(220)
                            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    }
                }
                drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                if (!isSearchVisible) {
                    animateNavigationIcon(0f)
                    toolbar?.setNavigationOnClickListener {
                        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
                            drawerLayout.closeDrawer(GravityCompat.START)
                        } else {
                            drawerLayout?.openDrawer(GravityCompat.START)
                        }
                    }
                }
            } else {
                bottomNavigation?.let { nav ->
                    nav.animate().translationY(nav.height.toFloat() + 80f).setDuration(180)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction { nav.visibility = View.GONE }.start()
                }
                drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                if (!isSearchVisible) {
                    animateNavigationIcon(1f)
                    toolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
                }
            }

            if (isSearchVisible) hideSearch()
        }

        btnFavorites?.setOnClickListener {
            val currentId = navController.currentDestination?.id ?: graphStartDestinationId
            if (currentId != R.id.favoritesFragment) {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
                navController.navigate(R.id.favoritesFragment, null, navOptions)
            }
        }

        phoneToolbar = toolbar
        phoneToolbarTitle = toolbarTitle
        phoneBtnSearch = btnSearch
        phoneBtnFavorites = btnFavorites
        phoneSearchView = searchView
        phoneBtnSearchClear = btnSearchClear
    }

    override fun onSettingsCopyright() {
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setTitle("Copyright")
            .setMessage("Live TV Pro does not stream any of the channels included in this application, all the streaming links are from third party websites available freely on the internet. We're just giving way to stream and all content is the copyright of their owner.")
            .setPositiveButton("OK", null)
            .show()
        applyGlassMorphism(dialog)
        val dm = resources.displayMetrics
        val swDp = resources.configuration.smallestScreenWidthDp
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dialogWidth = when {
            DeviceUtils.isTvDevice -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 720            -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 600            -> (dm.widthPixels * 0.55f).toInt()
            isLandscape            -> (dm.widthPixels * 0.55f).toInt()
            else                   -> (dm.widthPixels * 0.88f).toInt()
        }
        dialog.window?.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    override fun onSettingsNotice() {
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setTitle("Important Notice")
            .setMessage(
                "We do not support gambling. If you see gambling ads on our app or website, they come from the ad network, not us. We've tried to block these ads, but some may still appear.\n\n" +
                "If you see clickable ads, you can click them, but please don't sign up. We just need your clicks and impressions. Thanks for your support."
            )
            .setPositiveButton("OK", null)
            .show()
        applyGlassMorphism(dialog)
        val dm = resources.displayMetrics
        val swDp = resources.configuration.smallestScreenWidthDp
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dialogWidth = when {
            DeviceUtils.isTvDevice -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 720            -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 600            -> (dm.widthPixels * 0.55f).toInt()
            isLandscape            -> (dm.widthPixels * 0.55f).toInt()
            else                   -> (dm.widthPixels * 0.88f).toInt()
        }
        dialog.window?.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    override fun onSettingsShareApp() {
        try {
            val apkPath = packageManager.getApplicationInfo(packageName, 0).sourceDir
            val apkFile = java.io.File(apkPath)
            val appName = getString(R.string.app_name).replace(" ", "_")
            val shareFile = java.io.File(cacheDir, "$appName.apk")
            apkFile.copyTo(shareFile, overwrite = true)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${getString(R.string.app_name)}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share APK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSettingsSaveStates() {
        val view = layoutInflater.inflate(R.layout.dialog_save_states, null)
        val switchAspectRatio = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_remember_aspect_ratio)
        val switchLowestQuality = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_force_lowest_quality)
        val rgCenterControls = view.findViewById<android.widget.RadioGroup>(R.id.rg_center_controls)
        val rbSeeksOnly = view.findViewById<android.widget.RadioButton>(R.id.rb_seeks_only)
        val rbSeeksAndNav = view.findViewById<android.widget.RadioButton>(R.id.rb_seeks_and_nav)
        val rbNavOnly = view.findViewById<android.widget.RadioButton>(R.id.rb_nav_only)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_states_cancel)
        val btnApply = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_states_apply)

        switchAspectRatio.isChecked = preferencesManager.isRememberAspectRatioEnabled()
        switchLowestQuality.isChecked = preferencesManager.isForceLowestQualityEnabled()

        when (preferencesManager.getCenterControlsMode()) {
            com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_SEEKS_AND_NAV -> rbSeeksAndNav.isChecked = true
            com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_NAV_ONLY      -> rbNavOnly.isChecked = true
            else                                                                        -> rbSeeksOnly.isChecked = true
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setView(view)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnApply.setOnClickListener {
            preferencesManager.setRememberAspectRatioEnabled(switchAspectRatio.isChecked)
            preferencesManager.setForceLowestQualityEnabled(switchLowestQuality.isChecked)
            val mode = when (rgCenterControls.checkedRadioButtonId) {
                R.id.rb_seeks_and_nav -> com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_SEEKS_AND_NAV
                R.id.rb_nav_only      -> com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_NAV_ONLY
                else                  -> com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_SEEKS_ONLY
            }
            preferencesManager.setCenterControlsMode(mode)
            dialog.dismiss()
        }

        dialog.show()
        applyGlassMorphism(dialog)

        val dm = resources.displayMetrics
        val swDp = resources.configuration.smallestScreenWidthDp
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dialogWidth = when {
            DeviceUtils.isTvDevice -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 720            -> (dm.widthPixels * 0.45f).toInt()
            swDp >= 600            -> (dm.widthPixels * 0.55f).toInt()
            isLandscape            -> (dm.widthPixels * 0.55f).toInt()
            else                   -> (dm.widthPixels * 0.88f).toInt()
        }
        val maxHeight = (dm.heightPixels * 0.85f).toInt()
        dialog.window?.setLayout(dialogWidth, maxHeight.coerceAtMost(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))

        if (DeviceUtils.isTvDevice) {
            btnApply.requestFocus()
        }
    }

    override fun onSettingsFloatingPlayer() {
        if (DeviceUtils.isTvDevice) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
                .setTitle("Permission Required")
                .setMessage("Floating Player requires permission to draw over other apps. Please enable it in the next screen.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            applyGlassMorphism(dialog)
            val dm = resources.displayMetrics
            val swDp = resources.configuration.smallestScreenWidthDp
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val dialogWidth = when {
                swDp >= 720 -> (dm.widthPixels * 0.45f).toInt()
                swDp >= 600 -> (dm.widthPixels * 0.55f).toInt()
                isLandscape -> (dm.widthPixels * 0.55f).toInt()
                else        -> (dm.widthPixels * 0.88f).toInt()
            }
            dialog.window?.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
        } else {
            FloatingPlayerDialog.newInstance().show(supportFragmentManager, FloatingPlayerDialog.TAG)
        }
    }

    private fun setupSearch() {
        phoneBtnSearch?.setOnClickListener { showSearch() }
        phoneSearchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                    if (currentFragment is SearchableFragment) currentFragment.onSearchQuery(it)
                }
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                phoneSearchView?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
                phoneSearchView?.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { query ->
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                    if (currentFragment is SearchableFragment) currentFragment.onSearchQuery(query)
                    phoneBtnSearchClear?.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                }
                return true
            }
        })
        phoneBtnSearchClear?.setOnClickListener { phoneSearchView?.setQuery("", false) }
    }

    private fun showSearch() {
        isSearchVisible = true
        phoneToolbarTitle?.visibility = View.GONE
        phoneBtnSearch?.visibility = View.GONE
        phoneBtnFavorites?.visibility = View.GONE
        phoneSearchView?.visibility = View.VISIBLE
        phoneSearchView?.isIconified = false
        phoneSearchView?.requestFocus()
        animateNavigationIcon(1f)
        phoneToolbar?.setNavigationOnClickListener { hideSearch() }
    }

    private fun hideSearch() {
        isSearchVisible = false
        phoneToolbarTitle?.visibility = View.VISIBLE
        phoneBtnSearch?.visibility = View.VISIBLE
        phoneBtnFavorites?.visibility = View.VISIBLE
        phoneSearchView?.visibility = View.GONE
        phoneBtnSearchClear?.visibility = View.GONE
        phoneSearchView?.setQuery("", false)
        phoneSearchView?.clearFocus()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentId = navHostFragment?.navController?.currentDestination?.id
        val topLevelDestinations = setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.settingsFragment)
        val isTopLevel = currentId in topLevelDestinations

        animateNavigationIcon(if (isTopLevel) 0f else 1f)

        val drawerLayout = binding.root.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        if (isTopLevel) {
            phoneToolbar?.setNavigationOnClickListener {
                if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout?.openDrawer(GravityCompat.START)
                }
            }
        } else {
            phoneToolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
    }

    private fun animateNavigationIcon(endPosition: Float) {
        val startPosition = drawerToggle?.drawerArrowDrawable?.progress ?: 0f
        if (startPosition == endPosition) return
        val animator = ValueAnimator.ofFloat(startPosition, endPosition)
        animator.addUpdateListener { valueAnimator ->
            drawerToggle?.drawerArrowDrawable?.progress = valueAnimator.animatedValue as Float
        }
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.duration = 300
        animator.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val url = intent?.getStringExtra("url") ?: intent?.extras?.getString("url") ?: return
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) { }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerToggle?.isDrawerIndicatorEnabled == true) {
            drawerToggle?.syncState()
        }
    }

    private fun navigateAfterDrawerClose(
        drawerLayout: androidx.drawerlayout.widget.DrawerLayout?,
        gravity: Int,
        action: () -> Unit
    ) {
        if (drawerLayout == null || !drawerLayout.isDrawerOpen(gravity)) {
            action()
            return
        }
        val listener = object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: android.view.View) {
                drawerLayout.removeDrawerListener(this)
                action()
            }
        }
        drawerLayout.addDrawerListener(listener)
        drawerLayout.closeDrawer(gravity)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {

        if (ev.action == android.view.MotionEvent.ACTION_DOWN
            && ev.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        ) {
            DeviceUtils.notifyTouchDetected()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyGlassMorphism(dialog: android.app.Dialog) {
        val window = dialog.window ?: return
        val radius = 28f * resources.displayMetrics.density
        val glassBg = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC0D0D0D.toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
            }
            private val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = 0x33FFFFFF
                strokeWidth = 2f
            }
            private val rectF = android.graphics.RectF()
            override fun draw(canvas: android.graphics.Canvas) {
                rectF.set(bounds)
                canvas.drawRoundRect(rectF, radius, radius, paint)
                canvas.drawRoundRect(rectF, radius, radius, borderPaint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        window.setBackgroundDrawable(glassBg)
    }

}

private class CustomTypefaceSpan(private val typeface: Typeface) : android.text.style.TypefaceSpan("") {
    override fun updateDrawState(ds: android.text.TextPaint) { ds.typeface = typeface }
    override fun updateMeasureState(paint: android.text.TextPaint) { paint.typeface = typeface }
}
