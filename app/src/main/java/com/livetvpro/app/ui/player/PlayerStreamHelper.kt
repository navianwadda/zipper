package com.livetvpro.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
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
                "drmkeyid" -> drmKeyId = value
                "drmkey" -> drmKey = value
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

    fun detectMimeTypeFromUrl(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains("m3u8") || lower.contains("extension=m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") || lower.contains("/dash/") || lower.contains("type=mpd") -> MimeTypes.APPLICATION_MPD
            lower.contains(".ism") || lower.contains(".isml") || lower.contains("manifest(format=mpd") -> MimeTypes.APPLICATION_SS
            lower.contains(".flv") -> "video/x-flv"
            lower.contains(".mp4") || lower.contains(".m4v") || lower.contains(".m4a") -> "video/mp4"
            lower.contains(".ts") || lower.contains("/ts") -> "video/mp2t"
            lower.contains(".mkv") -> "video/x-matroska"
            lower.contains(".webm") -> "video/webm"
            lower.contains(".avi") -> "video/avi"
            lower.startsWith("rtmp://") || lower.startsWith("rtmps://") -> MimeTypes.APPLICATION_RTSP
            lower.startsWith("rtsp://") -> MimeTypes.APPLICATION_RTSP
            else -> null
        }
    }

    suspend fun resolveContentType(url: String, headers: Map<String, String>): String? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.connect()
            val contentType = connection.contentType ?: ""
            connection.disconnect()
            when {
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                contentType.contains("dash+xml", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                contentType.contains("mp4", ignoreCase = true) -> "video/mp4"
                contentType.contains("mp2t", ignoreCase = true) || contentType.contains("mpeg2", ignoreCase = true) -> "video/mp2t"
                contentType.contains("webm", ignoreCase = true) -> "video/webm"
                contentType.contains("matroska", ignoreCase = true) -> "video/x-matroska"
                contentType.contains("flv", ignoreCase = true) -> "video/x-flv"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
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
