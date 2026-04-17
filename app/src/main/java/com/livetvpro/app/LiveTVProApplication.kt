package com.livetvpro.app

import android.app.Application
import android.util.Log
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.di.DatabaseModule
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

        // FIX: Install a global crash handler FIRST — before anything else runs.
        // Any unhandled exception (Room schema mismatch, Hilt injection failure, etc.)
        // will be written to /storage/emulated/0/Download/livetvpro_db_crash_<timestamp>.txt
        // so you can retrieve the full stack trace without needing ADB logcat.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("LiveTVProCrash", "Uncaught exception on thread ${thread.name}", throwable)
            try {
                DatabaseModule.writeCrashLogToDownloads(applicationContext, throwable)
            } catch (_: Exception) { /* never let the crash handler itself crash */ }
            previousHandler?.uncaughtException(thread, throwable)
        }

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

