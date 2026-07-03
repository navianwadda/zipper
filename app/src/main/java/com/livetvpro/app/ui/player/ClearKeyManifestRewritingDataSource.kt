package com.livetvpro.app.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream

/**
 * Wraps another DataSource and, for any request whose URL path ends in
 * ".mpd", buffers the full manifest response and patches its
 * <ContentProtection> blocks so they properly declare ClearKey with a
 * given default_KID.
 *
 * WHY THIS EXISTS:
 * Some DASH packagers (e.g. Broadpeak BkS350, seen on some IPTV CDNs)
 * publish manifests that only ever declare a Widevine ContentProtection
 * element with NO actual pssh/init data, while expecting the real key to
 * be supplied out-of-band via a KID:KEY pair - exactly the format used by
 * Kodi's #KODIPROP inputstream.adaptive.license_key tags.
 *
 * Standard media3 DASH parsing decides whether a track is playable based
 * on the DRM info declared *inside the manifest itself*. If the manifest
 * only mentions Widevine (with no usable data) and never mentions
 * ClearKey, media3's track selector silently excludes every track as
 * DRM-unsupported for a ClearKey DrmSessionManager - no exception is
 * thrown, it just selects zero tracks. Playback sits at STATE_READY
 * forever with a blank screen and no segment requests.
 *
 * Rewriting the manifest to properly advertise ClearKey with the known
 * KID (the same trick Kodi's inputstream.adaptive effectively achieves
 * by ignoring the manifest's declared scheme entirely) fixes this.
 */
class ClearKeyManifestRewritingDataSource(
    private val upstream: DataSource,
    private val clearKeyIdHex: String,
    private val onPatched: ((original: String, patched: String) -> Unit)? = null
) : DataSource by upstream {

    private var patchedBytes: ByteArray? = null
    private var patchedPos = 0
    private var isManifestRequest = false
    private var openUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        openUri = dataSpec.uri
        isManifestRequest = dataSpec.uri.toString().substringBefore('?').endsWith(".mpd", ignoreCase = true)

        val length = upstream.open(dataSpec)
        if (!isManifestRequest) return length

        // Fully buffer the manifest so we can rewrite it before media3 parses it.
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val n = upstream.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        val original = out.toString("UTF-8")
        val patched = try {
            patchManifest(original, clearKeyIdHex)
        } catch (_: Exception) {
            original // if patching fails for any reason, fall back to the untouched manifest
        }
        onPatched?.invoke(original, patched)
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

    override fun getUri(): Uri? = openUri ?: upstream.uri

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

        /** Converts a 32-char hex key ID into standard dashed UUID form. */
        fun hexToUuidString(hex: String): String {
            val h = hex.replace("-", "").lowercase()
            if (h.length != 32) return hex
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-" +
                "${h.substring(16, 20)}-${h.substring(20, 32)}"
        }

        /**
         * Strips any existing <ContentProtection> elements from every
         * <AdaptationSet> that has at least one, and replaces them with a
         * pair that properly declares ClearKey with the given key ID, so
         * media3's DASH parser will select the track and hand it to our
         * ClearKey DrmSessionManager. AdaptationSets with no
         * ContentProtection at all (e.g. plain subtitles) are left alone.
         */
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
