package com.livetvpro.app.utils

import android.content.Context
import com.livetvpro.app.R

object AppIconUtils {

    private const val PREFS_NAME = "live_tv_pro_prefs"
    private const val KEY_APP_ICON_BLACK = "app_icon_black"

    fun isBlackIconActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_ICON_BLACK, false)
    }

    fun currentLauncherRoundIcon(context: Context): Int =
        if (isBlackIconActive(context)) R.mipmap.ic_launcher_black_round else R.mipmap.ic_launcher_round

    fun currentLauncherIcon(context: Context): Int =
        if (isBlackIconActive(context)) R.mipmap.ic_launcher_black else R.mipmap.ic_launcher
}
