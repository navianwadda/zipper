package com.livetvpro.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeListenerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        init {
            System.loadLibrary("native-lib")
        }

        private val k1 = intArrayOf(0xc6,0xc3,0xf8,0xd5,0xc2,0xc3,0xce,0xd5,0xc2,0xc4,0xd3,0xf8,0xce,0xc9,0xf8,0xc6,0xd7,0xd7).map{(it and 0xFF xor 0xA7).toChar()}.joinToString("")
        private val k2 = intArrayOf(0xc6,0xc3,0xf8,0xc3,0xd2,0xd5,0xc6,0xd3,0xce,0xc8,0xc9,0xf8,0xd4,0xc2,0xc4,0xc8,0xc9,0xc3,0xd4).map{(it and 0xFF xor 0xA7).toChar()}.joinToString("")
        val DEFAULT_AD_DURATION_SECONDS = 10L

        const val REMOTE_CONFIG_REDIRECT_MODE_PHONE  = "redirect_mode_phone"
        const val REMOTE_CONFIG_REDIRECT_MODE_TABLET = "redirect_mode_tablet"
        const val REMOTE_CONFIG_REDIRECT_MODE_TV     = "redirect_mode_tv"
        const val REMOTE_CONFIG_REDIRECT_MODE_OTHER  = "redirect_mode_other"

        const val REDIRECT_MODE_DIALOG   = "dialog"
        const val REDIRECT_MODE_DIRECT   = "direct"
        const val REDIRECT_MODE_DISABLED = "disabled"

        const val REMOTE_CONFIG_HIDE_AD_CONTENT = "hide_ad_content"
    }

    private external fun nativeShouldShowLink(pageType: String, uniqueId: String?, maxPerPage: Long, maxTotal: Long): Boolean
    private external fun nativeGetDirectLinkUrl(): String
    private external fun nativeResetSessions()
    private external fun nativeIsConfigValid(): Boolean
    private external fun nativeGetContactUrl(): String
    private external fun nativeGetCricLiveUrl(): String
    private external fun nativeGetFootLiveUrl(): String
    private external fun nativeGetEmailUs(): String
    private external fun nativeGetWebUrl(): String
    private external fun nativeGetMessage(): String
    private external fun nativeGetMessageUrl(): String
    private external fun nativeGetAppVersion(): String
    private external fun nativeGetDownloadUrl(): String

    @Volatile private var directLinkDisabled: Boolean? = null

    fun getDeviceFingerprint(): String {
        return try {
            val raw = "${Build.BOARD}${Build.BRAND}${Build.DEVICE}" +
                      "${Build.HARDWARE}${Build.MANUFACTURER}" +
                      "${Build.MODEL}${Build.PRODUCT}"
            val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun refreshDirectLinkState() {
        directLinkDisabled = try {
            val disabledIds = Firebase.remoteConfig
                .getString(NativeDataRepository.REMOTE_CONFIG_DIRECT_LINK_DISABLED_DEVICES)
            if (disabledIds.isBlank()) false
            else {
                val deviceId = getDeviceFingerprint()
                if (deviceId.isBlank()) false
                else disabledIds.split(",").any { it.trim() == deviceId }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isDirectLinkDisabled(): Boolean {
        return directLinkDisabled ?: try {
            val disabledIds = Firebase.remoteConfig
                .getString(NativeDataRepository.REMOTE_CONFIG_DIRECT_LINK_DISABLED_DEVICES)
            if (disabledIds.isBlank()) false
            else {
                val deviceId = getDeviceFingerprint()
                if (deviceId.isBlank()) false
                else disabledIds.split(",").any { it.trim() == deviceId }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getRedirectMode(): String {
        val key = when {
            DeviceUtils.isTvDevice                        -> REMOTE_CONFIG_REDIRECT_MODE_TV
            DeviceUtils.isPhone || DeviceUtils.isFoldable -> REMOTE_CONFIG_REDIRECT_MODE_PHONE
            DeviceUtils.isTablet                          -> REMOTE_CONFIG_REDIRECT_MODE_TABLET
            else                                          -> REMOTE_CONFIG_REDIRECT_MODE_OTHER
        }
        return try {
            val mode = Firebase.remoteConfig.getString(key).trim().lowercase()
            if (mode.isEmpty()) {
                if (DeviceUtils.isTvDevice) REDIRECT_MODE_DISABLED
                else if (Firebase.remoteConfig.getBoolean(k1)) REDIRECT_MODE_DIALOG
                else REDIRECT_MODE_DIRECT
            } else {
                mode
            }
        } catch (e: Exception) {
            if (DeviceUtils.isTvDevice) REDIRECT_MODE_DISABLED else REDIRECT_MODE_DIRECT
        }
    }

    fun isRedirectEnabled(): Boolean = getRedirectMode() != REDIRECT_MODE_DISABLED

    fun isInAppRedirectEnabled(): Boolean = getRedirectMode() == REDIRECT_MODE_DIALOG

    fun isAdContentHidden(): Boolean {
        return try {
            Firebase.remoteConfig.getBoolean(REMOTE_CONFIG_HIDE_AD_CONTENT)
        } catch (e: Exception) {
            true
        }
    }

    fun getAdDurationSeconds(): Long {
        return try {
            val v = Firebase.remoteConfig.getLong(k2)
            if (v <= 0L) DEFAULT_AD_DURATION_SECONDS else v
        } catch (e: Exception) {
            DEFAULT_AD_DURATION_SECONDS
        }
    }

    fun getDirectLinkUrl(): String {
        return try { nativeGetDirectLinkUrl() } catch (e: Exception) { "" }
    }

    fun onPageInteraction(pageType: String, uniqueId: String? = null, maxPerPage: Long = 0L, maxTotal: Long = 0L): Boolean {
        return try {
            if (isDirectLinkDisabled()) return false
            val shouldShow = nativeShouldShowLink(pageType, uniqueId, maxPerPage, maxTotal)
            if (shouldShow) {
                val url = nativeGetDirectLinkUrl()
                return url.isNotEmpty()
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openDirectLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun resetSessions() {
        try { nativeResetSessions() } catch (e: Exception) { }
    }

    fun isConfigValid(): Boolean {
        return try { nativeIsConfigValid() } catch (e: Exception) { false }
    }

    fun getContactUrl(): String = try { nativeGetContactUrl() } catch (e: Exception) { "" }
    fun getCricLiveUrl(): String = try { nativeGetCricLiveUrl() } catch (e: Exception) { "" }
    fun getFootLiveUrl(): String = try { nativeGetFootLiveUrl() } catch (e: Exception) { "" }
    fun getEmailUs(): String = try { nativeGetEmailUs() } catch (e: Exception) { "" }
    fun getWebUrl(): String = try { nativeGetWebUrl() } catch (e: Exception) { "" }
    fun getMessage(): String = try { nativeGetMessage() } catch (e: Exception) { "" }
    fun getMessageUrl(): String = try { nativeGetMessageUrl() } catch (e: Exception) { "" }
    fun getAppVersion(): String = try { nativeGetAppVersion() } catch (e: Exception) { "" }
    fun getDownloadUrl(): String = try { nativeGetDownloadUrl() } catch (e: Exception) { "" }
}
