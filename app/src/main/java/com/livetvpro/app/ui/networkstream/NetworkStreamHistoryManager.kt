package com.livetvpro.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStreamHistoryManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "network_stream_history_prefs"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_NEWEST_FIRST = "sort_newest_first"
        private const val MAX_ENTRIES = 200
    }

    fun addEntry(url: String) {
        if (url.isBlank()) return
        val current = readEntries().toMutableList()
        current.removeAll { it.url == url }
        current.add(0, NetworkStreamHistoryEntry(id = System.currentTimeMillis(), url = url))
        writeEntries(current.take(MAX_ENTRIES))
    }

    fun removeEntry(id: Long) {
        val current = readEntries().filterNot { it.id == id }
        writeEntries(current)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    fun getEntries(newestFirst: Boolean): List<NetworkStreamHistoryEntry> {
        val entries = readEntries()
        return if (newestFirst) entries.sortedByDescending { it.id }
               else entries.sortedBy { it.id }
    }

    fun isNewestFirst(): Boolean = prefs.getBoolean(KEY_NEWEST_FIRST, true)
    fun setNewestFirst(newestFirst: Boolean) = prefs.edit().putBoolean(KEY_NEWEST_FIRST, newestFirst).apply()

    private fun readEntries(): List<NetworkStreamHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NetworkStreamHistoryEntry(id = obj.getLong("id"), url = obj.getString("url"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(entries: List<NetworkStreamHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("url", entry.url)
            array.put(obj)
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }
}
