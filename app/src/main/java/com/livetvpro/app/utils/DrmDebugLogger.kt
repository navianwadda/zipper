package com.livetvpro.app.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * DRM Debug Logger
 *
 * Collects all DRM-related events in memory during a player session and
 * flushes them to a timestamped .txt file in the device Downloads folder
 * when the player is destroyed (session end / app close).
 *
 * Usage:
 *   DrmDebugLogger.startSession("clearkey", "ef34ae91...", "channel_name")
 *   DrmDebugLogger.log(DrmDebugLogger.Stage.PARSE, "parsed keyId=...", isError = false)
 *   DrmDebugLogger.exportToDownloads(context)   // call from onDestroy()
 */
object DrmDebugLogger {

    private const val TAG = "DrmDebug"

    enum class Stage {
        INTENT,         // raw intent / content type detection
        PARSE_URL,      // parseStreamUrl result
        BUILD_URL,      // buildStreamUrl result
        DRM_RESOLVE,    // which DRM branch was chosen
        DRM_CREATE,     // DrmSessionManager creation
        MEDIA_ITEM,     // MediaItem / DrmConfiguration built
        PLAYER_SETUP,   // ExoPlayer created
        PLAYBACK,       // STATE_READY / BUFFERING / ERROR
        EXPORT          // meta — log file written
    }

    data class Entry(
        val timestampMs: Long,
        val stage: Stage,
        val message: String,
        val isError: Boolean
    )

    private val entries = CopyOnWriteArrayList<Entry>()
    private var sessionStartMs = 0L
    private var sessionLabel = "unknown"

    // ──────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────

    /**
     * Call once when the player starts (e.g. top of setupPlayer or parseIntent).
     */
    fun startSession(contentType: String, contentName: String, streamUrl: String) {
        entries.clear()
        sessionStartMs = System.currentTimeMillis()
        sessionLabel = contentName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40)
        log(Stage.INTENT, "=== SESSION START ===")
        log(Stage.INTENT, "ContentType : $contentType")
        log(Stage.INTENT, "ContentName : $contentName")
        log(Stage.INTENT, "StreamURL   : $streamUrl")
        log(Stage.INTENT, "Device      : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        log(Stage.INTENT, "Time        : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(sessionStartMs))}")
    }

    fun log(stage: Stage, message: String, isError: Boolean = false) {
        val entry = Entry(System.currentTimeMillis(), stage, message, isError)
        entries.add(entry)
        if (isError) {
            Log.e(TAG, "[$stage] $message")
        } else {
            Log.d(TAG, "[$stage] $message")
        }
    }

    fun logIntent(
        action: String?,
        contentType: String,
        rawStreamUrl: String,
        drmSchemeRaw: String?,
        drmLicenseRaw: String?
    ) {
        log(Stage.INTENT, "Action       : $action")
        log(Stage.INTENT, "ContentType  : $contentType")
        log(Stage.INTENT, "RawStreamUrl : $rawStreamUrl")
        log(Stage.INTENT, "DrmScheme(raw): $drmSchemeRaw")
        log(Stage.INTENT, "DrmLicense(raw): $drmLicenseRaw")
        val hasPipe = rawStreamUrl.contains('|')
        val hasPercentPipe = rawStreamUrl.contains("%7C", ignoreCase = true)
        log(Stage.INTENT, "HasPipeSep   : $hasPipe  HasUrlEncodedPipe: $hasPercentPipe",
            isError = hasPercentPipe && !hasPipe)
        if (hasPercentPipe && !hasPipe) {
            log(Stage.INTENT,
                "BUG DETECTED: ACTION_VIEW encoded '|' as '%7C' — DRM params will be LOST. " +
                "Fix: decode Uri.toString() with Uri.decode() before parsing.",
                isError = true)
        }
    }

    fun logUrlParse(
        rawUrl: String,
        parsedUrl: String,
        headers: Map<String, String>,
        drmScheme: String?,
        drmKeyId: String?,
        drmKey: String?,
        drmLicenseUrl: String?
    ) {
        log(Stage.PARSE_URL, "Raw          : $rawUrl")
        log(Stage.PARSE_URL, "Parsed URL   : $parsedUrl")
        log(Stage.PARSE_URL, "Headers      : $headers")
        log(Stage.PARSE_URL, "drmScheme    : $drmScheme")
        log(Stage.PARSE_URL, "drmKeyId     : $drmKeyId")
        log(Stage.PARSE_URL, "drmKey       : $drmKey")
        log(Stage.PARSE_URL, "drmLicenseUrl: $drmLicenseUrl")

        // Detect common parse bugs
        if (drmScheme == null && rawUrl.contains("drmScheme", ignoreCase = true)) {
            log(Stage.PARSE_URL,
                "BUG: 'drmScheme' present in raw URL but was not parsed — pipe splitting likely failed.",
                isError = true)
        }
        if (drmKeyId == null && rawUrl.contains("drmLicense", ignoreCase = true)) {
            log(Stage.PARSE_URL,
                "BUG: 'drmLicense' present in raw URL but drmKeyId is null — colon split or pipe split failed.",
                isError = true)
        }
        if (drmScheme == "clearkey" && drmKeyId == null && drmLicenseUrl == null) {
            log(Stage.PARSE_URL,
                "BUG: drmScheme=clearkey but no key material found. ClearKey DRM manager will NOT be created.",
                isError = true)
        }
    }

    fun logDrmResolve(
        scheme: String?,
        keyId: String?,
        key: String?,
        licenseUrl: String?,
        chosenBranch: String
    ) {
        log(Stage.DRM_RESOLVE, "scheme       : $scheme")
        log(Stage.DRM_RESOLVE, "keyId        : $keyId")
        log(Stage.DRM_RESOLVE, "key          : $key")
        log(Stage.DRM_RESOLVE, "licenseUrl   : $licenseUrl")
        log(Stage.DRM_RESOLVE, "ChosenBranch : $chosenBranch")

        if (scheme == "clearkey" && chosenBranch == "none") {
            log(Stage.DRM_RESOLVE,
                "BUG: scheme=clearkey but no DRM manager will be created — all key material is null/empty.",
                isError = true)
        }
    }

    fun logDrmCreate(managerType: String, success: Boolean, error: Throwable? = null) {
        if (success) {
            log(Stage.DRM_CREATE, "Created $managerType DrmSessionManager — OK")
        } else {
            log(Stage.DRM_CREATE, "FAILED to create $managerType DrmSessionManager: ${error?.message}", isError = true)
            log(Stage.DRM_CREATE, "Stacktrace: ${error?.stackTraceToString()}", isError = true)
        }
    }

    fun logMediaItem(uri: String, mimeType: String?, drmConfigured: Boolean, drmUuid: String?) {
        log(Stage.MEDIA_ITEM, "URI          : $uri")
        log(Stage.MEDIA_ITEM, "MimeType     : $mimeType")
        log(Stage.MEDIA_ITEM, "DrmConfigured: $drmConfigured")
        log(Stage.MEDIA_ITEM, "DrmUUID      : $drmUuid")
        if (!drmConfigured) {
            log(Stage.MEDIA_ITEM, "NOTE: No DRM set on MediaItem — clearkey uses DrmSessionManagerProvider instead (correct)")
        }
    }

    fun logPlaybackState(state: Int, isError: Boolean = false, errorCode: Int = 0, errorMsg: String? = null) {
        val stateName = when (state) {
            1 -> "STATE_IDLE"
            2 -> "STATE_BUFFERING"
            3 -> "STATE_READY"
            4 -> "STATE_ENDED"
            else -> "STATE_UNKNOWN($state)"
        }
        if (isError) {
            log(Stage.PLAYBACK, "ERROR — code=$errorCode msg=$errorMsg", isError = true)
            when (errorCode) {
                2005 -> log(Stage.PLAYBACK, "ERROR_CODE_DRM_PROVISIONING_FAILED — DRM key exchange failed. Check keyId/key hex values.", isError = true)
                2004 -> log(Stage.PLAYBACK, "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED — License server returned error. Check URL + headers.", isError = true)
                2006 -> log(Stage.PLAYBACK, "ERROR_CODE_DRM_CONTENT_ERROR — Encrypted content can't be decrypted. Key ID may not match manifest.", isError = true)
                2007 -> log(Stage.PLAYBACK, "ERROR_CODE_DRM_SYSTEM_ERROR — DRM subsystem crash. Check FrameworkMediaDrm availability.", isError = true)
                2001 -> log(Stage.PLAYBACK, "ERROR_CODE_DRM_SCHEME_UNSUPPORTED — This DRM scheme is not supported on this device.", isError = true)
                3003 -> log(Stage.PLAYBACK, "ERROR_CODE_PARSING_MANIFEST_MALFORMED — MPD/M3U8 parse failed. Check URL and DRM PSSH boxes.", isError = true)
            }
        } else {
            log(Stage.PLAYBACK, "State: $stateName")
        }
    }

    // ──────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────

    /**
     * Writes the full log to Downloads/DrmDebug_<label>_<timestamp>.txt
     * Call from PlayerActivity.onDestroy() or FloatingPlayerActivity.onDestroy().
     */
    fun exportToDownloads(context: Context) {
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "DrmDebug_${sessionLabel}_$ts.txt"

            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage — use app-specific external files (no permission needed)
                // or fall back to Downloads via MediaStore
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }

            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)

            val sb = StringBuilder()
            sb.appendLine("╔══════════════════════════════════════════════════════════╗")
            sb.appendLine("║           LiveTVPro — DRM Debug Session Log              ║")
            sb.appendLine("╚══════════════════════════════════════════════════════════╝")
            sb.appendLine("File        : $fileName")
            sb.appendLine("Session     : $sessionLabel")
            sb.appendLine("Exported at : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            sb.appendLine()

            // Error summary at top
            val errors = entries.filter { it.isError }
            if (errors.isNotEmpty()) {
                sb.appendLine("══════════════════ ERROR SUMMARY (${errors.size}) ══════════════════")
                errors.forEach { e ->
                    val relMs = e.timestampMs - sessionStartMs
                    sb.appendLine("  [+${relMs}ms][${e.stage}] ❌ ${e.message}")
                }
                sb.appendLine()
            } else {
                sb.appendLine("══════════════════ ERROR SUMMARY: no errors ══════════════")
                sb.appendLine()
            }

            // Full timeline
            sb.appendLine("══════════════════ FULL TIMELINE ═══════════════════════════")
            entries.forEach { e ->
                val relMs = e.timestampMs - sessionStartMs
                val marker = if (e.isError) "❌" else "  "
                sb.appendLine("$marker [+${relMs}ms][${e.stage}] ${e.message}")
            }

            file.writeText(sb.toString())

            log(Stage.EXPORT, "Log saved → ${file.absolutePath}")
            Log.i(TAG, "DRM debug log saved: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export DRM debug log: ${e.message}", e)
        }
    }
}
