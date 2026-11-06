package com.livetvpro.app.debug

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    private lateinit var context: Context
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val log = throwable.stackTraceToString()
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("crash_log", log)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
            Thread.sleep(500)
        } catch (_: Throwable) {
            previousHandler?.uncaughtException(thread, throwable)
        } finally {
            exitProcess(1)
        }
    }
}
