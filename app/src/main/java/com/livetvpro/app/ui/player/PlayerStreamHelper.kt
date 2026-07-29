package com.livetvpro.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import com.livetvpro.app.data.models.LiveEventLink

data class StreamInfo(
    val url: String,
    val headers: Map<String, String>,
    val drmScheme: String?,
    val drmKeyId: String?,
    val drmKey: String?,
    val drmLicenseUrl: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
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
        val customHeaders = mutableMapOf<String, String>()
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
                else -> customHeaders[key] = value
            }
        }

        return StreamInfo(url, headers + customHeaders, drmScheme, drmKeyId, drmKey, drmLicenseUrl, customHeaders)
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

    fun buildStreamInfoFromLink(link: LiveEventLink): StreamInfo {
        val base = parseStreamUrl(link.url)

        val headers = base.headers.toMutableMap()
        val customHeaders = base.customHeaders.toMutableMap()

        link.referer?.takeIf { it.isNotEmpty() }?.let { headers["Referer"] = it }
        link.cookie?.takeIf { it.isNotEmpty() }?.let { headers["Cookie"] = it }
        link.origin?.takeIf { it.isNotEmpty() }?.let { headers["Origin"] = it }
        link.userAgent?.takeIf { it.isNotEmpty() }?.let { headers["User-Agent"] = it }
        link.xForwardedFor?.takeIf { it.isNotEmpty() }?.let { headers["X-Forwarded-For"] = it }
        link.customHeaders.forEach { (k, v) ->
            if (v.isNotEmpty()) {
                headers[k] = v
                customHeaders[k] = v
            }
        }

        val resolvedDrmScheme = link.drmScheme?.takeIf { it.isNotEmpty() }
            ?.let { normalizeDrmScheme(it) } ?: base.drmScheme

        var resolvedDrmLicenseUrl = base.drmLicenseUrl
        var resolvedDrmKeyId = base.drmKeyId
        var resolvedDrmKey = base.drmKey

        link.drmLicenseUrl?.takeIf { it.isNotEmpty() }?.let { value ->
            when {
                value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> resolvedDrmLicenseUrl = value
                value.trimStart().startsWith("{") -> resolvedDrmLicenseUrl = value
                else -> {
                    val colonIndex = value.indexOf(':')
                    if (colonIndex != -1) {
                        resolvedDrmKeyId = value.substring(0, colonIndex).trim()
                        resolvedDrmKey = value.substring(colonIndex + 1).trim()
                        resolvedDrmLicenseUrl = null
                    } else {
                        resolvedDrmLicenseUrl = value
                    }
                }
            }
        }

        return StreamInfo(
            url = base.url,
            headers = headers,
            drmScheme = resolvedDrmScheme,
            drmKeyId = resolvedDrmKeyId,
            drmKey = resolvedDrmKey,
            drmLicenseUrl = resolvedDrmLicenseUrl,
            customHeaders = customHeaders
        )
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
        link.customHeaders.forEach { (k, v) -> if (v.isNotEmpty()) params.add("$k=$v") }

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
            val headValue = tryHeadContentType(url, headers)
            headValue ?: trySniffByGet(url, headers)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * HttpURLConnection's `instanceFollowRedirects` only follows redirects within the same
     * protocol (http->http or https->https) — it silently refuses to follow a redirect that
     * crosses http<->https (this is a long-standing JDK limitation, not a bug in this code).
     * Many stream redirector endpoints (e.g. play.php?id=... style links) 302 across protocols,
     * so we have to follow Location headers ourselves to reach the real resource before sniffing it.
     */
    private fun openFollowingRedirects(
        url: String,
        headers: Map<String, String>,
        method: String,
        maxRedirects: Int = 5,
    ): java.net.HttpURLConnection {
        var currentUrl = url
        var connection: java.net.HttpURLConnection
        var redirects = 0
        while (true) {
            connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false // we handle redirects manually below
            if (method == "GET") {
                connection.setRequestProperty("Range", "bytes=0-4095")
            }
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.connect()

            val code = connection.responseCode
            val isRedirect = code in intArrayOf(301, 302, 303, 307, 308)
            if (isRedirect && redirects < maxRedirects) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) return connection // nowhere to go, give up as-is
                currentUrl = java.net.URI(currentUrl).resolve(location).toString()
                redirects++
                continue
            }
            return connection
        }
    }

    private fun tryHeadContentType(url: String, headers: Map<String, String>): String? {
        return try {
            val connection = openFollowingRedirects(url, headers, "HEAD")
            val contentType = connection.contentType ?: ""
            connection.disconnect()
            mimeFromContentTypeHeader(contentType)
        } catch (e: Exception) {
            null
        }
    }

    private fun trySniffByGet(url: String, headers: Map<String, String>): String? {
        return try {
            val connection = openFollowingRedirects(url, headers, "GET")

            val headerMime = mimeFromContentTypeHeader(connection.contentType ?: "")
            if (headerMime != null) {
                connection.disconnect()
                return headerMime
            }

            val sample = connection.inputStream.use { readUpTo(it, 4096) }
            connection.disconnect()
            sniffContentSignature(sample)
        } catch (e: Exception) {
            null
        }
    }

    private fun mimeFromContentTypeHeader(contentType: String): String? = when {
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

    private fun readUpTo(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        while (totalRead < maxBytes) {
            val read = input.read(buffer, totalRead, maxBytes - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return if (totalRead == maxBytes) buffer else buffer.copyOf(totalRead)
    }

    private fun sniffContentSignature(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = try { String(bytes, Charsets.US_ASCII) } catch (e: Exception) { "" }
        return when {
            text.startsWith("#EXTM3U") -> MimeTypes.APPLICATION_M3U8
            text.contains("<MPD") -> MimeTypes.APPLICATION_MPD
            text.contains("<smil") || text.contains("SmoothStreamingMedia") -> MimeTypes.APPLICATION_SS
            bytes.size >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte() -> "video/mp4"
            bytes.size >= 188 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() -> "video/mp2t"
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "video/webm"
            text.startsWith("FLV") -> "video/x-flv"
            else -> null
        }
    }

    fun buildClearKeyInlineManager(keyIdHex: String, keyHex: String): DefaultDrmSessionManager? {
        return try {
            val keyIdBytes = hexToBytes(keyIdHex)
            val keyBytes = hexToBytes(keyHex)
            if (keyIdBytes.isEmpty() || keyBytes.isEmpty()) return null
            val keyBase64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val kidBase64 = android.util.Base64.encodeToString(keyIdBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val jwk = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(LocalMediaDrmCallback(jwk.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) { null }
    }

    fun buildClearKeyJwkManager(jwkJson: String): DefaultDrmSessionManager? {
        return try {
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
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
                .setPlayClearSamplesWithoutKeys(true)
                .build(cb)
        } catch (e: Exception) { null }
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
