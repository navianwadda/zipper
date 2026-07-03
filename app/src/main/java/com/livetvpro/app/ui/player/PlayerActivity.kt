package com.livetvpro.app.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream

/**
 * Wraps another DataSource and, for requests whose URL path ends in ".mpd",
 * rewrites the manifest's <ContentProtection> elements to properly declare
 * ClearKey with the given key ID.
 *
 * Needed because this CDN's DASH manifests only ever declare Widevine (with
 * no actual pssh/init data) and never mention ClearKey at all, even though
 * the real content is meant to be decrypted with a KID:KEY pair supplied
 * out-of-band (via the playlist's #KODIPROP tags). media3's DASH track
 * selection decides whether a track is playable based on the DRM info
 * declared *inside the manifest*, so without this patch it silently
 * selects zero tracks - no error, just a blank screen.
 */
class ClearKeyManifestRewritingDataSource(
    private val upstream: DataSource,
    private val clearKeyIdHex: String
) : DataSource by upstream {

    private var patchedBytes: ByteArray? = null
    private var patchedPos = 0
    private var isManifestRequest = false

    override fun open(dataSpec: DataSpec): Long {
        isManifestRequest = dataSpec.uri.toString().substringBefore('?').endsWith(".mpd", ignoreCase = true)

        val length = upstream.open(dataSpec)
        if (!isManifestRequest) return length

        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val n = upstream.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        val patched = try {
            patchManifest(out.toString("UTF-8"), clearKeyIdHex)
        } catch (_: Exception) {
            out.toString("UTF-8")
        }
        val bytes = patched.toByteArray(Charsets.UTF_8)
        patchedBytes = bytes
        patchedPos = 0
        return bytes.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isManifestRequest) return upstream.read(buffer, offset, length)
        val bytes = patchedBytes ?: return C.RESULT_END_OF_INPUT
        if (patchedPos >= bytes.size) return C.RESULT_END_OF_INPUT
        val toCopy = minOf(length, bytes.size - patchedPos)
        System.arraycopy(bytes, patchedPos, buffer, offset, toCopy)
        patchedPos += toCopy
        return toCopy
    }

    override fun close() {
        patchedBytes = null
        patchedPos = 0
        upstream.close()
    }

    companion object {
        private val ADAPTATION_SET_REGEX =
            Regex("<AdaptationSet.*?</AdaptationSet>", RegexOption.DOT_MATCHES_ALL)
        private val CONTENT_PROTECTION_REGEX =
            Regex("<ContentProtection.*?(?:/>|</ContentProtection>)", RegexOption.DOT_MATCHES_ALL)
        private const val CLEARKEY_SCHEME_URI = "urn:uuid:e2719d58-a985-b3c9-781a-b030af78d30e"

        private fun hexToUuidString(hex: String): String {
            val h = hex.replace("-", "").lowercase()
            if (h.length != 32) return hex
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-" +
                "${h.substring(16, 20)}-${h.substring(20, 32)}"
        }

        fun patchManifest(xml: String, keyIdHex: String): String {
            val kidUuid = hexToUuidString(keyIdHex)
            val clearKeyBlock =
                "<ContentProtection xmlns:cenc=\"urn:mpeg:cenc:2013\" " +
                    "schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\" " +
                    "cenc:default_KID=\"$kidUuid\" value=\"cenc\"/>" +
                    "<ContentProtection schemeIdUri=\"$CLEARKEY_SCHEME_URI\" value=\"ClearKey1.0\"/>"

            if (!ADAPTATION_SET_REGEX.containsMatchIn(xml)) return xml

            return ADAPTATION_SET_REGEX.replace(xml) { match ->
                val block = match.value
                if (!CONTENT_PROTECTION_REGEX.containsMatchIn(block)) {
                    block
                } else {
                    val stripped = CONTENT_PROTECTION_REGEX.replace(block, "")
                    stripped.replaceFirst("</AdaptationSet>", "$clearKeyBlock</AdaptationSet>")
                }
            }
        }
    }
}
