package com.livetvpro.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

object ThemeDebugLog {

    private val buffer = StringBuilder()
    private var uri: Uri? = null
    private const val FILE_NAME = "livetvpro_theme_debug.txt"

    @Synchronized
    fun log(tag: String, message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val line = "[$ts] $tag: $message"
        buffer.append(line).append('\n')
        Log.d("ThemeDebugLog", line)
    }

    @Synchronized
    fun flush(context: Context) {
        try {
            val resolver = context.applicationContext.contentResolver
            var target = uri
            if (target == null) {
                target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    Uri.fromFile(java.io.File(dir, FILE_NAME))
                }
                uri = target
            }
            target ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.openOutputStream(target, "wt")?.use { out ->
                    out.write(buffer.toString().toByteArray())
                }
            } else {
                java.io.File(target.path!!).writeText(buffer.toString())
            }
        } catch (e: Exception) {
            Log.e("ThemeDebugLog", "flush failed", e)
        }
    }
}
