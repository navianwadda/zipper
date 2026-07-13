package com.livetvpro.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.main.MainScaffold
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialogContent
import com.livetvpro.app.ui.settings.SettingsActions
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SettingsActions {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager
    @Inject lateinit var dataRepository: com.livetvpro.app.data.repository.NativeDataRepository

    var navController: NavController? = null

    var isSearchVisible      by mutableStateOf(false)
    var toolbarTitle         by mutableStateOf("Live TV Pro")
    var showRefreshIcon      by mutableStateOf(false)

    private var showCopyrightDialog      by mutableStateOf(false)
    private var showNoticeDialog         by mutableStateOf(false)
    private var showOverlayPermDialog    by mutableStateOf(false)
    private var showFloatingPlayerDialog by mutableStateOf(false)

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

        window.decorView.post {
            window.decorView.requestApplyInsets()
        }

        themeManager.registerActivityContext(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val nav = navController ?: return
                val currentRoute = nav.currentDestination?.route ?: return

                val exitConfirmationRoutes = if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop || DeviceUtils.isTablet) {
                    setOf(Routes.HOME, Routes.LIVE_EVENTS, Routes.SPORTS, Routes.FAVORITES)
                } else {
                    setOf(Routes.HOME, Routes.LIVE_EVENTS, Routes.SPORTS)
                }

                when {
                    currentRoute in exitConfirmationRoutes -> {
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

        setContent {
            LiveTVProTheme(themeManager) {
                MainScaffold(
                    activity           = this,
                    themeManager       = themeManager,
                    listenerManager    = listenerManager,
                    cooldownManager    = cooldownManager,
                    preferencesManager = preferencesManager,
                    settingsActions    = this,
                    onNavControllerReady      = { navController = it },
                    onDestinationChanged      = { _, title, refresh ->
                        toolbarTitle    = title
                        showRefreshIcon = refresh
                    },
                    onSearchVisibilityChanged = { isSearchVisible = it },
                )

                if (showCopyrightDialog) {
                    val bergenSans = FontFamily(Font(R.font.bergen_sans))
                    AlertDialog(
                        onDismissRequest = { showCopyrightDialog = false },
                        title = {
                            Text(
                                text = "Copyright",
                                fontFamily = bergenSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        },
                        text = {
                            Text(
                                text = "Live TV Pro does not stream any of the channels included in this application, " +
                                    "all the streaming links are from third party websites available freely on the internet. " +
                                    "We're just giving way to stream and all content is the copyright of their owner.",
                                fontFamily = bergenSans,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showCopyrightDialog = false }) {
                                Text(
                                    text = "OK",
                                    fontFamily = bergenSans,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                    )
                }

                if (showNoticeDialog) {
                    val bergenSans = FontFamily(Font(R.font.bergen_sans))
                    AlertDialog(
                        onDismissRequest = { showNoticeDialog = false },
                        title = {
                            Text(
                                text = "Important Notice",
                                fontFamily = bergenSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        },
                        text = {
                            Text(
                                text = "We do not support gambling. If you see gambling ads on our app or website, " +
                                    "they come from the ad network, not us.\n\n" +
                                    "If you see clickable ads, you can click them, but please don't sign up. " +
                                    "We just need your clicks and impressions. Thanks for your support.",
                                fontFamily = bergenSans,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showNoticeDialog = false }) {
                                Text(
                                    text = "OK",
                                    fontFamily = bergenSans,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                    )
                }

                if (showOverlayPermDialog) {
                    val bergenSans = FontFamily(Font(R.font.bergen_sans))
                    AlertDialog(
                        onDismissRequest = { showOverlayPermDialog = false },
                        title = {
                            Text(
                                text = "Permission Required",
                                fontFamily = bergenSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        },
                        text = {
                            Text(
                                text = "Floating Player requires permission to draw over other apps. Please enable it in the next screen.",
                                fontFamily = bergenSans,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showOverlayPermDialog = false
                                overlayPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                )
                            }) {
                                Text(
                                    text = "Settings",
                                    fontFamily = bergenSans,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayPermDialog = false }) {
                                Text(
                                    text = "Cancel",
                                    fontFamily = bergenSans,
                                    fontWeight = FontWeight.Medium,
                                )
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

        window.decorView.post { handleNotificationIntent(intent) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDark = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        themeManager.notifySystemDarkChanged(isDark)
        themeManager.refreshDynamicColors(this)
    }

    override fun onResume() {
        super.onResume()
        themeManager.refreshDynamicColors(this)
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

    override fun onSettingsFloatingPlayer() {
        if (DeviceUtils.isTvDevice) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermDialog = true
        } else {
            showFloatingPlayerDialog = true
        }
    }
}
