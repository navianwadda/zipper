package com.livetvpro.app.ui.player

import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import com.livetvpro.app.data.models.LiveEventLink
import java.util.UUID

data class StreamInfo(
    val url: String,
    val headers: Map<String, String>,
    val drmScheme: String?,
    val drmKeyId: String?,
    val drmKey: String?,
    val drmLicenseUrl: String? = null,
)

object PlayerStreamHelper {

    fun parseStreamUrl(streamUrl: String): StreamInfo {
        val normalizedUrl = streamUrl.replace("%7c", "|", ignoreCase = true)
        val pipeIndex = normalizedUrl.indexOf('|')
        if (pipeIndex == -1) {
            return StreamInfo(normalizedUrl, mapOf(), null, null, null, null)
        }

        val url = normalizedUrl.substring(0, pipeIndex).trim().trimEnd('?')
        val rawParams = normalizedUrl.substring(pipeIndex + 1).trim()

        val parts = buildList {
            for (segment in rawParams.split("|")) {
                val eqIdx = segment.indexOf('=')
                val value = if (eqIdx != -1) segment.substring(eqIdx + 1) else ""
                if (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)) {
                    add(segment)
                } else {
                    addAll(segment.split("&"))
                }
            }
        }

        val headers = mutableMapOf<String, String>()
        var drmScheme: String? = null
        var drmKeyId: String? = null
        var drmKey: String? = null
        var drmLicenseUrl: String? = null

        for (part in parts) {
            val eqIndex = part.indexOf('=')
            if (eqIndex == -1) continue

            val key = part.substring(0, eqIndex).trim()
            val value = part.substring(eqIndex + 1).trim()

            when (key.lowercase()) {
                "drmscheme" -> drmScheme = normalizeDrmScheme(value)
                "drmlicense" -> {
                    if (value.startsWith("http://", ignoreCase = true) ||
                        value.startsWith("https://", ignoreCase = true)) {
                        drmLicenseUrl = value
                    } else if (value.trimStart().startsWith("{")) {
                        drmLicenseUrl = value
                    } else {
                        val colonIndex = value.indexOf(':')
                        if (colonIndex != -1) {
                            drmKeyId = value.substring(0, colonIndex).trim()
                            drmKey = value.substring(colonIndex + 1).trim()
                        }
                    }
                }
                "referer", "referrer" -> headers["Referer"] = value
                "user-agent", "useragent" -> headers["User-Agent"] = value
                "origin" -> headers["Origin"] = value
                "cookie" -> headers["Cookie"] = value
                "x-forwarded-for" -> headers["X-Forwarded-For"] = value
                else -> headers[key] = value
            }
        }

        return StreamInfo(url, headers, drmScheme, drmKeyId, drmKey, drmLicenseUrl)
    }

    fun normalizeDrmScheme(scheme: String): String {
        val lower = scheme.lowercase()
        return when {
            lower.contains("clearkey") || lower == "org.w3.clearkey" -> "clearkey"
            lower.contains("widevine") || lower == "com.widevine.alpha" -> "widevine"
            lower.contains("playready") || lower == "com.microsoft.playready" -> "playready"
            lower.contains("fairplay") -> "fairplay"
            else -> lower
        }
    }

    fun buildStreamUrl(link: LiveEventLink): String {
        var url = link.url
        val params = mutableListOf<String>()

        link.referer?.let { if (it.isNotEmpty()) params.add("referer=$it") }
        link.cookie?.let { if (it.isNotEmpty()) params.add("cookie=$it") }
        link.origin?.let { if (it.isNotEmpty()) params.add("origin=$it") }
        link.userAgent?.let { if (it.isNotEmpty()) params.add("user-agent=$it") }
        link.xForwardedFor?.let { if (it.isNotEmpty()) params.add("x-forwarded-for=$it") }
        link.drmScheme?.let { if (it.isNotEmpty()) params.add("drmScheme=$it") }
        link.drmLicenseUrl?.let { if (it.isNotEmpty()) params.add("drmLicense=$it") }

        if (params.isNotEmpty()) {
            url += "|" + params.joinToString("|")
        }

        return url
    }

    fun buildClearKeyInlineManager(keyIdHex: String, keyHex: String): DefaultDrmSessionManager? {
        return try {
            val keyIdBytes = hexToBytes(keyIdHex)
            val keyBytes = hexToBytes(keyHex)
            if (keyIdBytes.isEmpty() || keyBytes.isEmpty()) return null
            val keyBase64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val kidBase64 = android.util.Base64.encodeToString(keyIdBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(false)
                .build(buildAdaptiveClearKeyCallback(keyBase64, kidBase64))
        } catch (e: Exception) { null }
    }

    fun buildClearKeyJwkManager(jwkJson: String): DefaultDrmSessionManager? {
        return try {
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(false)
                .build(LocalMediaDrmCallback(jwkJson.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) { null }
    }

    fun buildClearKeyServerManager(licenseUrl: String, headers: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val cb = HttpMediaDrmCallback(licenseUrl, factory)
            headers.forEach { (k, v) -> cb.setKeyRequestProperty(k, v) }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(false)
                .build(cb)
        } catch (e: Exception) { null }
    }

    fun buildAdaptiveClearKeyCallback(
        keyBase64: String,
        fallbackKidBase64: String,
    ): androidx.media3.exoplayer.drm.MediaDrmCallback {
        return object : androidx.media3.exoplayer.drm.MediaDrmCallback {
            override fun executeProvisionRequest(
                uuid: UUID,
                request: androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest,
            ): androidx.media3.exoplayer.drm.MediaDrmCallback.Response =
                androidx.media3.exoplayer.drm.MediaDrmCallback.Response(ByteArray(0))

            override fun executeKeyRequest(
                uuid: UUID,
                request: androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest,
            ): androidx.media3.exoplayer.drm.MediaDrmCallback.Response {
                return try {
                    val body = String(request.data, Charsets.UTF_8)
                    val kids = mutableListOf<String>()
                    Regex(""""kids"\s*:\s*\[([^\]]+)]""").find(body)?.let { m ->
                        Regex(""""([A-Za-z0-9+/=_-]+)"""").findAll(m.groupValues[1])
                            .forEach { kids.add(it.groupValues[1]) }
                    }
                    val entries = if (kids.isNotEmpty()) {
                        kids.joinToString(",") { kid -> """{"kty":"oct","k":"$keyBase64","kid":"$kid"}""" }
                    } else {
                        """{"kty":"oct","k":"$keyBase64","kid":"$fallbackKidBase64"}"""
                    }
                    val jwk = """{"keys":[$entries],"type":"temporary"}"""
                    androidx.media3.exoplayer.drm.MediaDrmCallback.Response(jwk.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    val fallback = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$fallbackKidBase64"}],"type":"temporary"}"""
                    androidx.media3.exoplayer.drm.MediaDrmCallback.Response(fallback.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        return try {
            val clean = hex.replace(" ", "").replace("-", "").lowercase()
            if (clean.length % 2 != 0) return ByteArray(0)
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { ByteArray(0) }
    }

    fun toggleMute(player: androidx.media3.exoplayer.ExoPlayer?, isMuted: Boolean): Boolean {
        val newMuted = !isMuted
        player?.volume = if (newMuted) 0f else 1f
        return newMuted
    }
}
