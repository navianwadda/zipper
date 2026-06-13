package com.livetvpro.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.main.MainScaffold
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialogContent
import com.livetvpro.app.ui.settings.SettingsActions
import com.livetvpro.app.ui.theme.LiveTVProTheme
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

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var dataRepository: com.livetvpro.app.data.repository.NativeDataRepository

    var navController: NavController? = null
    var navHostFragment: androidx.navigation.fragment.NavHostFragment? = null
    var pendingDestinationId: Int = -1

    var isSearchVisible      by mutableStateOf(false)
    var toolbarTitle         by mutableStateOf("Live TV Pro")
    var showRefreshIcon      by mutableStateOf(false)

    private var showCopyrightDialog      by mutableStateOf(false)
    private var showNoticeDialog         by mutableStateOf(false)
    private var showOverlayPermDialog    by mutableStateOf(false)
    private var showSaveStatesDialog     by mutableStateOf(false)
    private var showFloatingPlayerDialog by mutableStateOf(false)

    private var saveStatesRememberAR     by mutableStateOf(false)
    private var saveStatesForceLowestQ   by mutableStateOf(false)
    private var saveStatesCenterMode     by mutableIntStateOf(PreferencesManager.CENTER_MODE_SEEKS_ONLY)

    private var backPressedTime = 0L

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            showFloatingPlayerDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!dataRepository.isDataLoaded()) {
            startActivity(Intent(this, com.livetvpro.app.ui.SplashActivity::class.java))
            finish()
            return
        }

        if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        themeManager.registerActivityContext(this)

        setContent {
            LiveTVProTheme(themeManager) {
                MainScaffold(
                    activity           = this,
                    themeManager       = themeManager,
                    listenerManager    = listenerManager,
                    preferencesManager = preferencesManager,
                    settingsActions    = this,
                    onNavControllerReady      = { navController = it },
                    onNavHostReady            = { navHostFragment = it },
                    onDestinationChanged      = { _, title, refresh ->
                        toolbarTitle    = title
                        showRefreshIcon = refresh
                    },
                    onSearchVisibilityChanged = { isSearchVisible = it },
                )

                if (showCopyrightDialog) {
                    AlertDialog(
                        onDismissRequest = { showCopyrightDialog = false },
                        title = { Text("Copyright") },
                        text  = {
                            Text(
                                "Live TV Pro does not stream any of the channels included in this application, " +
                                "all the streaming links are from third party websites available freely on the internet. " +
                                "We're just giving way to stream and all content is the copyright of their owner."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showCopyrightDialog = false }) {
                                Text("OK")
                            }
                        },
                    )
                }

                if (showNoticeDialog) {
                    AlertDialog(
                        onDismissRequest = { showNoticeDialog = false },
                        title = { Text("Important Notice") },
                        text  = {
                            Text(
                                "We do not support gambling. If you see gambling ads on our app or website, " +
                                "they come from the ad network, not us.\n\n" +
                                "If you see clickable ads, you can click them, but please don't sign up. " +
                                "We just need your clicks and impressions. Thanks for your support."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showNoticeDialog = false }) {
                                Text("OK")
                            }
                        },
                    )
                }

                if (showOverlayPermDialog) {
                    AlertDialog(
                        onDismissRequest = { showOverlayPermDialog = false },
                        title = { Text("Permission Required") },
                        text  = {
                            Text("Floating Player requires permission to draw over other apps. Please enable it in the next screen.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showOverlayPermDialog = false
                                overlayPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                )
                            }) {
                                Text("Settings")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayPermDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                if (showSaveStatesDialog) {
                    val centerModeOptions = listOf(
                        PreferencesManager.CENTER_MODE_SEEKS_ONLY    to "Seeks Only",
                        PreferencesManager.CENTER_MODE_SEEKS_AND_NAV to "Seeks & Navigation",
                        PreferencesManager.CENTER_MODE_NAV_ONLY      to "Navigation Only",
                    )
                    AlertDialog(
                        onDismissRequest = { showSaveStatesDialog = false },
                        title = { Text("Save States") },
                        text  = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Remember Aspect Ratio", style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Switch(
                                        checked         = saveStatesRememberAR,
                                        onCheckedChange = { saveStatesRememberAR = it },
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Force Lowest Quality", style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Switch(
                                        checked         = saveStatesForceLowestQ,
                                        onCheckedChange = { saveStatesForceLowestQ = it },
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text  = "Center Controls",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(modifier = Modifier.selectableGroup()) {
                                    centerModeOptions.forEach { (mode, label) ->
                                        Row(
                                            modifier          = Modifier
                                                .fillMaxWidth()
                                                .selectable(
                                                    selected = saveStatesCenterMode == mode,
                                                    onClick  = { saveStatesCenterMode = mode },
                                                    role     = Role.RadioButton,
                                                )
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = saveStatesCenterMode == mode,
                                                onClick  = null,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(label, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                preferencesManager.setRememberAspectRatioEnabled(saveStatesRememberAR)
                                preferencesManager.setForceLowestQualityEnabled(saveStatesForceLowestQ)
                                preferencesManager.setCenterControlsMode(saveStatesCenterMode)
                                showSaveStatesDialog = false
                            }) {
                                Text("Apply")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSaveStatesDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                if (showFloatingPlayerDialog) {
                    FloatingPlayerDialogContent(
                        preferencesManager = preferencesManager,
                        onDismiss = { showFloatingPlayerDialog = false },
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val nav = navController ?: return
                val currentDestId = nav.currentDestination?.id ?: return

                val topLevel = if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop || DeviceUtils.isTablet) {
                    setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment, R.id.favoritesFragment)
                } else {
                    setOf(R.id.homeFragment, R.id.liveEventsFragment, R.id.sportsFragment)
                }

                when {
                    isSearchVisible -> isSearchVisible = false
                    currentDestId in topLevel -> {
                        val now = System.currentTimeMillis()
                        if (now - backPressedTime < 2000) finishAffinity()
                        else {
                            backPressedTime = now
                            Toast.makeText(this@MainActivity, "Press again to exit", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
                }
            }
        })

        window.decorView.post { handleNotificationIntent(intent) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDark = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        themeManager.notifySystemDarkChanged(isDark)
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
        } catch (_: Exception) {}
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN &&
            ev.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        ) DeviceUtils.notifyTouchDetected()
        return super.dispatchTouchEvent(ev)
    }

    fun navigateTo(destId: Int) {
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

    fun navigateRaw(destId: Int) {
        navController?.navigate(destId, null, null)
    }

    fun refreshCurrentFragment() {
        currentFragment<Refreshable>()?.refreshData()
    }

    fun dispatchSearchQuery(query: String) {
        currentFragment<SearchableFragment>()?.onSearchQuery(query)
    }

    private inline fun <reified T> currentFragment(): T? {
        val nhf = navHostFragment ?: return null
        return nhf.childFragmentManager.primaryNavigationFragment as? T
            ?: nhf.childFragmentManager.fragments.filterIsInstance<T>().firstOrNull()
    }

    override fun onSettingsCopyright() {
        showCopyrightDialog = true
    }

    override fun onSettingsNotice() {
        showNoticeDialog = true
    }

    override fun onSettingsShareApp() {
        try {
            val apk   = java.io.File(packageManager.getApplicationInfo(packageName, 0).sourceDir)
            val name  = getString(R.string.app_name).replace(" ", "_")
            val share = java.io.File(cacheDir, "$name.apk")
            apk.copyTo(share, overwrite = true)
            val uri   = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", share)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share ${getString(R.string.app_name)}"
            ))
        } catch (_: Exception) { Toast.makeText(this, "Unable to share APK", Toast.LENGTH_SHORT).show() }
    }

    override fun onSettingsSaveStates() {
        saveStatesRememberAR   = preferencesManager.isRememberAspectRatioEnabled()
        saveStatesForceLowestQ = preferencesManager.isForceLowestQualityEnabled()
        saveStatesCenterMode   = preferencesManager.getCenterControlsMode()
        showSaveStatesDialog   = true
    }

    override fun onSettingsFloatingPlayer() {
        if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermDialog = true
        } else {
            showFloatingPlayerDialog = true
        }
    }
}

private class CustomTypefaceSpan(private val tf: Typeface) : android.text.style.TypefaceSpan("") {
    override fun updateDrawState(ds: android.text.TextPaint) { ds.typeface = tf }
    override fun updateMeasureState(p: android.text.TextPaint) { p.typeface = tf }
}
