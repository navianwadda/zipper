package com.livetvpro.app.ui.player

import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.livetvpro.app.data.local.ThemeManager

abstract class BasePlayerActivity : AppCompatActivity() {

    abstract val themeManager: ThemeManager
    abstract val playerContainer: ConstraintLayout

    internal lateinit var windowInsetsController: WindowInsetsControllerCompat

    internal fun setupWindowFlags(isLandscape: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (!isLandscape) {
            window.decorView.setBackgroundColor(themeManager.getBackgroundColor(this))
        }
        val isDark = themeManager.isDarkMode(this)
        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.isAppearanceLightStatusBars = !isDark
        wic.isAppearanceLightNavigationBars = !isDark
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }

    internal fun setupSystemUI(isLandscape: Boolean) {
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!isLandscape) {
            windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

}
