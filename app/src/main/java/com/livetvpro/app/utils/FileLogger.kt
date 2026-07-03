package com.livetvpro.app.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors debug logging into a plain-text file under the public Downloads
 * folder, in addition to Logcat, so logs can be pulled off the device
 * without adb (e.g. from a file manager) and shared/attached directly.
 *
 * - On Android 10 (API 29) and above: writes via MediaStore's Downloads
 *   collection. No storage permission is required for this - apps can
 *   always write their own files into MediaStore.
 * - On Android 9 (API 28) and below: writes directly to
 *   /storage/emulated/0/Download/. Requires WRITE_EXTERNAL_STORAGE, which
 *   the app already declares (maxSdkVersion=28) - but it must be granted
 *   at runtime by the caller before startSession() is used, or this will
 *   silently fall back to Logcat-only.
 *
 * Usage:
 *   FileLogger.startSession(context, "PlayerDebug")
 *   FileLogger.d("PlayerDebug", "some message")
 *   FileLogger.e("PlayerDebug", "some error")
 *   FileLogger.close()
 */
object FileLogger {

    private var writer: OutputStreamWriter? = null
    private var fileLabel: String? = null
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun startSession(context: Context, prefix: String = "LiveTVPro_log") {
        if (writer != null) return // session already open
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${prefix}_$stamp.txt"
        try {
            writer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openViaMediaStore(context, fileName)
            } else {
                openViaLegacyFile(fileName)
            }
            fileLabel = fileName
            writer?.let {
                it.write("===== $fileName started $stamp =====\n")
                it.flush()
            }
            Log.i("FileLogger", "Logging to Downloads/$fileName")
        } catch (t: Throwable) {
            Log.w("FileLogger", "Could not open log file in Downloads, falling back to Logcat-only", t)
            writer = null
        }
    }

    private fun openViaMediaStore(context: Context, fileName: String): OutputStreamWriter {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore.insert returned null Uri")
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("openOutputStream returned null")
        return OutputStreamWriter(stream)
    }

    private fun openViaLegacyFile(fileName: String): OutputStreamWriter {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        return OutputStreamWriter(java.io.FileOutputStream(file, true))
    }

    @Synchronized
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeLine("D", tag, msg)
    }

    @Synchronized
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        writeLine("E", tag, msg)
    }

    @Synchronized
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeLine("W", tag, msg)
    }

    private fun writeLine(level: String, tag: String, msg: String) {
        val w = writer ?: return
        try {
            w.write("${timeFmt.format(Date())} $level/$tag: $msg\n")
            w.flush() // flush every line - playback errors can precede a crash
        } catch (t: Throwable) {
            Log.w("FileLogger", "Failed writing to log file", t)
        }
    }

    /** Returns the current session's file name (e.g. to show/share with the user), or null if none open. */
    fun currentFileName(): String? = fileLabel

    @Synchronized
    fun close() {
        try { writer?.write("===== log closed =====\n"); writer?.flush(); writer?.close() } catch (_: Throwable) {}
        writer = null
        fileLabel = null
    }
}
