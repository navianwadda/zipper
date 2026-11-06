package com.livetvpro.app.debug

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val log = throwable.stackTraceToString()
        val intent = Intent(context, CrashActivity::class.java).apply {
            putExtra("crash_log", log)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        exitProcess(1)
    }
}
