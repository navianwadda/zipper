package com.livetvpro.app.data.repository

import android.content.Context
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.NewExternalEventRow
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.toGroupedLiveEvents
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val listenerManager: com.livetvpro.app.utils.NativeListenerManager
) {
    companion object {
        private val k_dldd = byteArrayOf(0xc3.toByte(),0xce.toByte(),0xd5.toByte(),0xc2.toByte(),0xc4.toByte(),0xd3.toByte(),0xf8.toByte(),0xcb.toByte(),0xce.toByte(),0xc9.toByte(),0xcc.toByte(),0xf8.toByte(),0xc3.toByte(),0xce.toByte(),0xd4.toByte(),0xc6.toByte(),0xc5.toByte(),0xcb.toByte(),0xc2.toByte(),0xc3.toByte(),0xf8.toByte(),0xc3.toByte(),0xc2.toByte(),0xd1.toByte(),0xce.toByte(),0xc4.toByte(),0xc2.toByte(),0xd4.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")
        val REMOTE_CONFIG_DIRECT_LINK_DISABLED_DEVICES: String get() = k_dldd

        private val k_nl = byteArrayOf(0xc9.toByte(),0xc6.toByte(),0xd3.toByte(),0xce.toByte(),0xd1.toByte(),0xc2.toByte(),0x8a.toByte(),0xcb.toByte(),0xce.toByte(),0xc5.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")

        private var q1 = false

        init {
            try {
                System.loadLibrary(k_nl)
                q1 = true
            } catch (e: UnsatisfiedLinkError) {
                q1 = false
            } catch (e: Exception) {
                q1 = false
            }
        }
    }

    private external fun nativeValidateIntegrity(): Boolean
    private external fun nativeGetConfigKey(): String
    private external fun nativeStoreConfigUrl(configUrl: String)
    private external fun nativeGetConfigUrl(): String
    private external fun nativeUpdateConfig(key: String)
    private external fun nativeStoreData(jsonData: String): Boolean
    private external fun nativeGetCategories(): String
    private external fun nativeGetChannels(): String
    private external fun nativeGetLiveEvents(): String
    private external fun nativeIsDataLoaded(): Boolean
    private external fun nativeGetEventCategories(): String
    private external fun nativeGetSports(): String
    private external fun nativeGetExternalLiveEvents(): String

    private val k_dfu = byteArrayOf(0xc3.toByte(),0xc6.toByte(),0xd3.toByte(),0xc6.toByte(),0xf8.toByte(),0xc8.toByte(),0xc5.toByte(),0xcd.toByte(),0xc2.toByte(),0xc4.toByte(),0xd3.toByte(),0xf8.toByte(),0xd2.toByte(),0xd5.toByte(),0xcb.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")
    private val k_rch = byteArrayOf(0xd5.toByte(),0xc2.toByte(),0xc3.toByte(),0xce.toByte(),0xd5.toByte(),0xc2.toByte(),0xc4.toByte(),0xd3.toByte(),0xf8.toByte(),0xc4.toByte(),0xc8.toByte(),0xc8.toByte(),0xcb.toByte(),0xc3.toByte(),0xc8.toByte(),0xd0.toByte(),0xc9.toByte(),0xf8.toByte(),0xcf.toByte(),0xc8.toByte(),0xd2.toByte(),0xd5.toByte(),0xd4.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")
    private val k_dit = byteArrayOf(0xe3.toByte(),0xe2.toByte(),0xe4.toByte(),0xf5.toByte(),0xfe.toByte(),0xf7.toByte(),0xf3.toByte(),0xf8.toByte(),0xee.toByte(),0xf3.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")
    private val k_ss  = byteArrayOf(0xd4.toByte(),0xd7.toByte(),0xc8.toByte(),0xd5.toByte(),0xd3.toByte(),0xd4.toByte(),0xf8.toByte(),0xd4.toByte(),0xcb.toByte(),0xd2.toByte(),0xc0.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")
    private val k_sp  = byteArrayOf(0xf4.toByte(),0xd7.toByte(),0xc8.toByte(),0xd5.toByte(),0xd3.toByte(),0xd4.toByte()).map{(it.toInt() and 0xFF xor 0xA7).toChar()}.joinToString("")

    private fun p1(): Boolean = try { if (!q1) true else nativeValidateIntegrity() } catch (e: Throwable) { true }
    private fun p2(): String  = try { if (!q1) k_dfu else nativeGetConfigKey() } catch (e: Throwable) { k_dfu }
    private fun p3(url: String) { try { if (q1) nativeStoreConfigUrl(url) } catch (e: Throwable) {} }
    private fun p4(): String  = try { if (!q1) "" else nativeGetConfigUrl() } catch (e: Throwable) { "" }
    private fun p5(data: String): Boolean = try { if (!q1) false else nativeStoreData(data) } catch (e: Throwable) { false }
    private fun p6(): String  = try { if (!q1) "[]" else nativeGetCategories() } catch (e: Throwable) { "[]" }
    private fun p7(): String  = try { if (!q1) "[]" else nativeGetChannels() } catch (e: Throwable) { "[]" }
    private fun p8(): String  = try { if (!q1) "[]" else nativeGetLiveEvents() } catch (e: Throwable) { "[]" }
    private fun p9(): Boolean = try { if (!q1) false else nativeIsDataLoaded() } catch (e: Throwable) { false }
    private fun p10(): String = try { if (!q1) "[]" else nativeGetEventCategories() } catch (e: Throwable) { "[]" }
    private fun p11(): String = try { if (!q1) "[]" else nativeGetSports() } catch (e: Throwable) { "[]" }

    private val q2 = Mutex()
    private val remoteConfig = Firebase.remoteConfig
    private var q3: String = ""
    private var q4: String = ""

    init {
        try {
            val cfg = remoteConfigSettings { minimumFetchIntervalInSeconds = if (isDebugBuild()) 0L else 1200L }
            remoteConfig.setConfigSettingsAsync(cfg)
            try {
                remoteConfig.setDefaultsAsync(mapOf(
                    p2()  to "",
                    k_rch to 8L,
                    k_dldd to "",
                    k_dit to ""
                ))
            } catch (e: Exception) {}
        } catch (e: Exception) {}
    }

    suspend fun fetchRemoteConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteConfig.fetchAndActivate().await()
            listenerManager.refreshDirectLinkState()
            val configUrl = remoteConfig.getString(p2())
            val dk = remoteConfig.getString(k_dit)
            if (dk.isNotEmpty() && q1) nativeUpdateConfig(dk)
            if (configUrl.isNotEmpty()) {
                p3(configUrl); q4 = configUrl; return@withContext true
            } else {
                if (q4.isNotEmpty()) { p3(q4); return@withContext true }
                return@withContext false
            }
        } catch (e: Exception) {
            if (q4.isNotEmpty()) { p3(q4); return@withContext true }
            return@withContext false
        }
    }

    suspend fun refreshData(): Boolean = withContext(Dispatchers.IO) {
        q2.withLock {
            try {
                if (!p1()) return@withContext q5()
                val url = p4()
                if (url.isBlank()) return@withContext q5()
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext q5()
                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) return@withContext q5()
                    val ok = p5(body)
                    if (ok) { q3 = body; return@withContext true } else return@withContext q5()
                }
            } catch (e: Exception) { return@withContext q5() }
        }
    }

    private fun q5(): Boolean = try { if (q3.isBlank()) false else p5(q3) } catch (e: Exception) { false }

    fun getCategories(): List<Category> = try {
        val j = p6(); if (j.isEmpty() || j == "[]") emptyList() else gson.fromJson(j, Array<Category>::class.java).toList()
    } catch (e: Exception) { emptyList() }

    fun getChannels(): List<Channel> = try {
        val j = p7(); if (j.isEmpty() || j == "[]") emptyList() else gson.fromJson(j, Array<Channel>::class.java).toList()
    } catch (e: Exception) { emptyList() }

    fun getLiveEvents(): List<LiveEvent> = try {
        val j = p8(); if (j.isEmpty() || j == "[]") emptyList() else gson.fromJson(j, Array<LiveEvent>::class.java).toList()
    } catch (e: Exception) { emptyList() }

    fun getEventCategories(): List<EventCategory> = try {
        val j = p10(); if (j.isEmpty() || j == "[]") emptyList() else gson.fromJson(j, Array<EventCategory>::class.java).toList()
    } catch (e: Exception) { emptyList() }

    fun getSports(): List<Channel> = try {
        val j = p11(); if (j.isEmpty() || j == "[]") emptyList()
        else gson.fromJson(j, Array<Channel>::class.java).toList().map { ch ->
            if (ch.categoryId.isEmpty()) ch.copy(categoryId = k_ss, categoryName = k_sp) else ch
        }
    } catch (e: Exception) { emptyList() }

    fun getExternalLiveEvents(): List<LiveEvent> = try {
        val j = if (q1) nativeGetExternalLiveEvents() else "[]"
        if (j.isEmpty() || j == "[]") emptyList()
        else gson.fromJson(j, Array<NewExternalEventRow>::class.java).toList().toGroupedLiveEvents()
    } catch (e: Exception) { emptyList() }

    fun isDataLoaded(): Boolean {
        if (p9()) return true
        if (q3.isNotBlank()) return p5(q3)
        return false
    }

    private fun isDebugBuild(): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
}
