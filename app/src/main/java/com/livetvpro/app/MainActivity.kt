package com.livetvpro.app

import android.content.DialogInterface
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.main.MainScaffold
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
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

    var isSearchVisible by mutableStateOf(false)
    var toolbarTitle    by mutableStateOf("Live TV Pro")
    var showRefreshIcon by mutableStateOf(false)

    private var backPressedTime = 0L

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            FloatingPlayerDialog.newInstance().show(supportFragmentManager, FloatingPlayerDialog.TAG)
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
                    activity          = this,
                    themeManager      = themeManager,
                    listenerManager   = listenerManager,
                    preferencesManager = preferencesManager,
                    settingsActions   = this,
                    onNavControllerReady = { navController = it },
                    onNavHostReady = { navHostFragment = it },
                    onDestinationChanged = { destId, title, refresh ->
                        toolbarTitle    = title
                        showRefreshIcon = refresh
                    },
                    onSearchVisibilityChanged = { isSearchVisible = it },
                )
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

    private fun applyGlassMorphism(dialog: android.app.Dialog) {
        val window = dialog.window ?: return
        val radius = 28f * resources.displayMetrics.density
        window.setBackgroundDrawable(object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC0D0D0D.toInt()
            }
            private val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = 0x33FFFFFF
                strokeWidth = 2f
            }
            private val rect = android.graphics.RectF()
            override fun draw(c: android.graphics.Canvas) {
                rect.set(bounds)
                c.drawRoundRect(rect, radius, radius, paint)
                c.drawRoundRect(rect, radius, radius, border)
            }
            override fun setAlpha(a: Int) { paint.alpha = a }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        })
    }

    private fun dialogWidth(): Int {
        val dm  = resources.displayMetrics
        val sw  = resources.configuration.smallestScreenWidthDp
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return when {
            DeviceUtils.isTvDevice || DeviceUtils.isDesktop -> (dm.widthPixels * 0.45f).toInt()
            sw >= 720 -> (dm.widthPixels * 0.45f).toInt()
            sw >= 600 -> (dm.widthPixels * 0.55f).toInt()
            land      -> (dm.widthPixels * 0.55f).toInt()
            else      -> (dm.widthPixels * 0.88f).toInt()
        }
    }

    override fun onSettingsCopyright() {
        val d = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setTitle("Copyright")
            .setMessage("Live TV Pro does not stream any of the channels included in this application, all the streaming links are from third party websites available freely on the internet. We're just giving way to stream and all content is the copyright of their owner.")
            .setPositiveButton("OK", null).show()
        applyGlassMorphism(d)
        d.window?.setLayout(dialogWidth(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    override fun onSettingsNotice() {
        val d = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setTitle("Important Notice")
            .setMessage(
                "We do not support gambling. If you see gambling ads on our app or website, they come from the ad network, not us.\n\n" +
                "If you see clickable ads, you can click them, but please don't sign up. We just need your clicks and impressions. Thanks for your support."
            )
            .setPositiveButton("OK", null).show()
        applyGlassMorphism(d)
        d.window?.setLayout(dialogWidth(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
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
        val v      = layoutInflater.inflate(R.layout.dialog_save_states, null)
        val swAR   = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_remember_aspect_ratio)
        val swLQ   = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_force_lowest_quality)
        val rg     = v.findViewById<android.widget.RadioGroup>(R.id.rg_center_controls)
        val btnC   = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_states_cancel)
        val btnA   = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_states_apply)

        swAR.isChecked = preferencesManager.isRememberAspectRatioEnabled()
        swLQ.isChecked = preferencesManager.isForceLowestQualityEnabled()
        when (preferencesManager.getCenterControlsMode()) {
            PreferencesManager.CENTER_MODE_SEEKS_AND_NAV -> v.findViewById<android.widget.RadioButton>(R.id.rb_seeks_and_nav).isChecked = true
            PreferencesManager.CENTER_MODE_NAV_ONLY      -> v.findViewById<android.widget.RadioButton>(R.id.rb_nav_only).isChecked = true
            else                                          -> v.findViewById<android.widget.RadioButton>(R.id.rb_seeks_only).isChecked = true
        }

        val d = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent).setView(v).create()
        btnC.setOnClickListener { d.dismiss() }
        btnA.setOnClickListener {
            preferencesManager.setRememberAspectRatioEnabled(swAR.isChecked)
            preferencesManager.setForceLowestQualityEnabled(swLQ.isChecked)
            preferencesManager.setCenterControlsMode(
                when (rg.checkedRadioButtonId) {
                    R.id.rb_seeks_and_nav -> PreferencesManager.CENTER_MODE_SEEKS_AND_NAV
                    R.id.rb_nav_only      -> PreferencesManager.CENTER_MODE_NAV_ONLY
                    else                  -> PreferencesManager.CENTER_MODE_SEEKS_ONLY
                }
            )
            d.dismiss()
        }
        d.show()
        applyGlassMorphism(d)
        val maxH = (resources.displayMetrics.heightPixels * 0.85f).toInt()
        d.window?.setLayout(dialogWidth(), maxH.coerceAtMost(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop) btnA.requestFocus()
    }

    override fun onSettingsFloatingPlayer() {
        if (DeviceUtils.isTvDevice || DeviceUtils.isDesktop) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val d = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
                .setTitle("Permission Required")
                .setMessage("Floating Player requires permission to draw over other apps. Please enable it in the next screen.")
                .setPositiveButton("Settings") { _, _ ->
                    overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Cancel", null).show()
            applyGlassMorphism(d)
            d.window?.setLayout(dialogWidth(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            d.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
        } else {
            FloatingPlayerDialog.newInstance().show(supportFragmentManager, FloatingPlayerDialog.TAG)
        }
    }
}

private class CustomTypefaceSpan(private val tf: Typeface) : android.text.style.TypefaceSpan("") {
    override fun updateDrawState(ds: android.text.TextPaint) { ds.typeface = tf }
    override fun updateMeasureState(p: android.text.TextPaint) { p.typeface = tf }
}
