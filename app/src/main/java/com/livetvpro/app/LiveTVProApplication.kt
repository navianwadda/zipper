package com.livetvpro.app

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        DeviceUtils.init(this)
        FloatingPlayerManager.initialize(preferencesManager)
        themeManager.applyTheme()

        applicationScope.launch {
            try {
                dataRepository.fetchRemoteConfig()
            } catch (e: Exception) {
            }
        }
    }
}
