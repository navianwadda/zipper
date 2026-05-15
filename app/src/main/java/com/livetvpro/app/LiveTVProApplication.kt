package com.livetvpro.app

import android.app.Application
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        setupCrashLogger()
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
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

    private fun setupCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logDir = File("/storage/emulated/0/Download/CrashLogs")
                logDir.mkdirs()
                val logFile = File(logDir, "crash_$timestamp.txt")

                logFile.writeText(buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("App Version: ${packageManager.getPackageInfo(packageName, 0).versionName}")
                    appendLine("====================")
                    appendLine()
                    appendLine(stackTrace)
                })
            } catch (e: Exception) {
                // ignore logging failure
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
