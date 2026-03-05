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
        const val REMOTE_CONFIG_DIRECT_LINK_DISABLED_DEVICES = "direct_link_disabled_devices"

        private var isNativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("native-lib")
                isNativeLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                isNativeLibraryLoaded = false
            } catch (e: Exception) {
                isNativeLibraryLoaded = false
            }
        }
    }

    private external fun nativeValidateIntegrity(): Boolean
    private external fun nativeGetConfigKey(): String
    private external fun nativeStoreConfigUrl(configUrl: String)
    private external fun nativeGetConfigUrl(): String
    private external fun nativeStoreData(jsonData: String): Boolean
    private external fun nativeGetCategories(): String
    private external fun nativeGetChannels(): String
    private external fun nativeGetLiveEvents(): String
    private external fun nativeIsDataLoaded(): Boolean
    private external fun nativeGetEventCategories(): String
    private external fun nativeGetSports(): String
    private external fun nativeGetExternalLiveEvents(): String

    private fun checkIntegrityAndEnabled(): Boolean {
        return try {
            if (!isNativeLibraryLoaded) return true
            nativeValidateIntegrity()
        } catch (error: Throwable) {
            true
        }
    }

    private fun getRemoteConfigKey(): String {
        return try {
            if (!isNativeLibraryLoaded) return "data_file_url"
            nativeGetConfigKey()
        } catch (error: Throwable) {
            "data_file_url"
        }
    }

    private fun storeConfigUrl(url: String) {
        try {
            if (!isNativeLibraryLoaded) return
            nativeStoreConfigUrl(url)
        } catch (error: Throwable) {
        }
    }

    private fun retrieveDataUrl(): String {
        return try {
            if (!isNativeLibraryLoaded) return ""
            nativeGetConfigUrl()
        } catch (error: Throwable) {
            ""
        }
    }

    private fun storeJsonData(jsonData: String): Boolean {
        return try {
            if (!isNativeLibraryLoaded) return false
            nativeStoreData(jsonData)
        } catch (error: Throwable) {
            false
        }
    }

    private fun retrieveCategoriesJson(): String {
        return try {
            if (!isNativeLibraryLoaded) return "[]"
            nativeGetCategories()
        } catch (error: Throwable) {
            "[]"
        }
    }

    private fun retrieveChannelsJson(): String {
        return try {
            if (!isNativeLibraryLoaded) return "[]"
            nativeGetChannels()
        } catch (error: Throwable) {
            "[]"
        }
    }

    private fun retrieveLiveEventsJson(): String {
        return try {
            if (!isNativeLibraryLoaded) return "[]"
            nativeGetLiveEvents()
        } catch (error: Throwable) {
            "[]"
        }
    }

    private fun checkDataLoaded(): Boolean {
        return try {
            if (!isNativeLibraryLoaded) return false
            nativeIsDataLoaded()
        } catch (error: Throwable) {
            false
        }
    }

    private fun retrieveEventCategoriesJson(): String {
        return try {
            if (!isNativeLibraryLoaded) return "[]"
            nativeGetEventCategories()
        } catch (error: Throwable) {
            "[]"
        }
    }

    private fun retrieveSportsJson(): String {
        return try {
            if (!isNativeLibraryLoaded) return "[]"
            nativeGetSports()
        } catch (error: Throwable) {
            "[]"
        }
    }

    private val refreshMutex = Mutex()
    private val remoteConfig = Firebase.remoteConfig

    private var inMemoryJson: String = ""
    private var inMemoryConfigUrl: String = ""

    init {
        try {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (isDebugBuild()) 0L else 1200L
            }
            remoteConfig.setConfigSettingsAsync(configSettings)

            try {
                val configKey = getRemoteConfigKey()
                remoteConfig.setDefaultsAsync(mapOf(
                    configKey to "",
                    "redirect_cooldown_hours" to 8L,
                    REMOTE_CONFIG_DIRECT_LINK_DISABLED_DEVICES to ""
                ))
            } catch (error: Exception) {
            }
        } catch (error: Exception) {
        }
    }

    suspend fun fetchRemoteConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteConfig.fetchAndActivate().await()
            listenerManager.refreshDirectLinkState()
            val configKey = getRemoteConfigKey()
            val configUrl = remoteConfig.getString(configKey)

            if (configUrl.isNotEmpty()) {
                storeConfigUrl(configUrl)
                inMemoryConfigUrl = configUrl
                return@withContext true
            } else {
                if (inMemoryConfigUrl.isNotEmpty()) {
                    storeConfigUrl(inMemoryConfigUrl)
                    return@withContext true
                }
                return@withContext false
            }
        } catch (error: Exception) {
            if (inMemoryConfigUrl.isNotEmpty()) {
                storeConfigUrl(inMemoryConfigUrl)
                return@withContext true
            }
            return@withContext false
        }
    }

    suspend fun refreshData(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            try {
                if (!checkIntegrityAndEnabled()) {
                    return@withContext restoreFromCache()
                }

                val dataUrl = retrieveDataUrl()
                if (dataUrl.isBlank()) {
                    return@withContext restoreFromCache()
                }

                val request = Request.Builder().url(dataUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext restoreFromCache()
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrBlank()) {
                        return@withContext restoreFromCache()
                    }

                    val success = storeJsonData(responseBody)
                    if (success) {
                        inMemoryJson = responseBody
                        return@withContext true
                    } else {
                        return@withContext restoreFromCache()
                    }
                }
            } catch (error: Exception) {
                return@withContext restoreFromCache()
            }
        }
    }

    private fun restoreFromCache(): Boolean {
        return try {
            if (inMemoryJson.isBlank()) return false
            storeJsonData(inMemoryJson)
        } catch (e: Exception) {
            false
        }
    }

    fun getCategories(): List<Category> {
        return try {
            val json = retrieveCategoriesJson()
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<Category>::class.java).toList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun getChannels(): List<Channel> {
        return try {
            val json = retrieveChannelsJson()
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<Channel>::class.java).toList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun getLiveEvents(): List<LiveEvent> {
        return try {
            val json = retrieveLiveEventsJson()
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<LiveEvent>::class.java).toList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun getEventCategories(): List<EventCategory> {
        return try {
            val json = retrieveEventCategoriesJson()
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<EventCategory>::class.java).toList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun getSports(): List<Channel> {
        return try {
            val json = retrieveSportsJson()
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<Channel>::class.java).toList().map { channel ->
                if (channel.categoryId.isEmpty()) {
                    channel.copy(categoryId = "sports_slug", categoryName = "Sports")
                } else {
                    channel
                }
            }
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun getExternalLiveEvents(): List<LiveEvent> {
        return try {
            val json = if (isNativeLibraryLoaded) nativeGetExternalLiveEvents() else "[]"
            if (json.isEmpty() || json == "[]") return emptyList()
            gson.fromJson(json, Array<NewExternalEventRow>::class.java).toList().toGroupedLiveEvents()
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun isDataLoaded(): Boolean {
        if (checkDataLoaded()) return true
        if (inMemoryJson.isNotBlank()) {
            return storeJsonData(inMemoryJson)
        }
        return false
    }

    private fun isDebugBuild(): Boolean {
        return context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
