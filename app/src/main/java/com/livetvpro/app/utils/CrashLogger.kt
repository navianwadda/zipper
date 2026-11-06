package com.livetvpro.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.livetvpro.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val PREFS_NAME = "crash_logger_prefs"
    private const val KEY_CRASH_REPORT = "last_crash_report"
    private const val KEY_CRASH_PENDING = "crash_pending"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(throwable)
                saveCrash(appContext, report)
                relaunchApp(appContext)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun consumePendingCrash(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CRASH_PENDING, false)) return null
        val report = prefs.getString(KEY_CRASH_REPORT, null)
        prefs.edit()
            .remove(KEY_CRASH_PENDING)
            .remove(KEY_CRASH_REPORT)
            .apply()
        return report
    }

    private fun buildReport(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("=== Crash Report ===")
            appendLine("Time       : $timestamp")
            appendLine("App        : ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android    : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device     : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("====================")
            appendLine()
            append(sw.toString())
        }
    }

    private fun saveCrash(context: Context, report: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CRASH_REPORT, report)
            .putBoolean(KEY_CRASH_PENDING, true)
            .commit()
    }

    private fun relaunchApp(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } ?: return
        context.startActivity(intent)
    }
}
