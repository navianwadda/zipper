package com.livetvpro.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.security.ProviderInstaller
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LiveTVProApplication : Application() {

    @Inject lateinit var dataRepository: NativeDataRepository
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        init {
            try {
                System.loadLibrary("native-lib")
            } catch (e: UnsatisfiedLinkError) {
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val prefs = base.getSharedPreferences("live_tv_pro_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", ThemeManager.THEME_AUTO)
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            ThemeManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeManager.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
            else                     -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    override fun onCreate() {
        super.onCreate()
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
        }
        DeviceUtils.init(this)
        FloatingPlayerManager.initialize(preferencesManager)

        applicationScope.launch {
            try {
                dataRepository.fetchRemoteConfig()
            } catch (e: Exception) {
            }
        }
    }
}
