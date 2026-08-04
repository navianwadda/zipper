package com.livetvpro.app.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.livetvpro.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class CastConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class PendingCastMedia(
    val url: String,
    val title: String,
    val mimeType: String?,
    val isLive: Boolean,
    val startPositionMs: Long,
)

private data class CastThemeRes(val lightThemeResId: Int, val darkThemeResId: Int)

object CastManager {

    private val themeMap: Map<String, CastThemeRes> = mapOf(
        "Legacy" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Legacy_Light, darkThemeResId = R.style.Theme_Cast_Legacy),
        "Default" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Default_Light, darkThemeResId = R.style.Theme_Cast_Default),
        "Dynamic" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Dynamic_Light, darkThemeResId = R.style.Theme_Cast_Dynamic),
        "Catppuccin" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Catppuccin_Light, darkThemeResId = R.style.Theme_Cast_Catppuccin),
        "Cloudflare" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Cloudflare_Light, darkThemeResId = R.style.Theme_Cast_Cloudflare),
        "CottonCandy" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_CottonCandy_Light, darkThemeResId = R.style.Theme_Cast_CottonCandy),
        "Doom" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Doom_Light, darkThemeResId = R.style.Theme_Cast_Doom),
        "GreenApple" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_GreenApple_Light, darkThemeResId = R.style.Theme_Cast_GreenApple),
        "Gruvbox" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Gruvbox_Light, darkThemeResId = R.style.Theme_Cast_Gruvbox),
        "Kanagawa" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Kanagawa_Light, darkThemeResId = R.style.Theme_Cast_Kanagawa),
        "Lavender" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Lavender_Light, darkThemeResId = R.style.Theme_Cast_Lavender),
        "Midnight" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Midnight_Light, darkThemeResId = R.style.Theme_Cast_Midnight),
        "Mocha" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Mocha_Light, darkThemeResId = R.style.Theme_Cast_Mocha),
        "Nord" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Nord_Light, darkThemeResId = R.style.Theme_Cast_Nord),
        "RosePine" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_RosePine_Light, darkThemeResId = R.style.Theme_Cast_RosePine),
        "Strawberry" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Strawberry_Light, darkThemeResId = R.style.Theme_Cast_Strawberry),
        "Tidal" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Tidal_Light, darkThemeResId = R.style.Theme_Cast_Tidal),
        "TakoGreen" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_TakoGreen_Light, darkThemeResId = R.style.Theme_Cast_TakoGreen),
        "TokyoNight" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_TokyoNight_Light, darkThemeResId = R.style.Theme_Cast_TokyoNight),
        "YinYang" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_YinYang_Light, darkThemeResId = R.style.Theme_Cast_YinYang),
        "Yotsuba" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Yotsuba_Light, darkThemeResId = R.style.Theme_Cast_Yotsuba),
        "Sapphire" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Sapphire_Light, darkThemeResId = R.style.Theme_Cast_Sapphire),
        "Sunset" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Sunset_Light, darkThemeResId = R.style.Theme_Cast_Sunset),
        "Ocean" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Ocean_Light, darkThemeResId = R.style.Theme_Cast_Ocean),
        "Forest" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Forest_Light, darkThemeResId = R.style.Theme_Cast_Forest),
        "RoseGold" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_RoseGold_Light, darkThemeResId = R.style.Theme_Cast_RoseGold),
        "Violet" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Violet_Light, darkThemeResId = R.style.Theme_Cast_Violet),
        "Amber" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Amber_Light, darkThemeResId = R.style.Theme_Cast_Amber),
        "Coral" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Coral_Light, darkThemeResId = R.style.Theme_Cast_Coral),
        "Slate" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Slate_Light, darkThemeResId = R.style.Theme_Cast_Slate),
        "Dracula" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Dracula_Light, darkThemeResId = R.style.Theme_Cast_Dracula),
        "Monochrome" to CastThemeRes(lightThemeResId = R.style.Theme_Cast_Monochrome_Light, darkThemeResId = R.style.Theme_Cast_Monochrome),
    )

    private fun resolveThemeResId(colorThemeName: String, isDark: Boolean): Int {
        val res = themeMap[colorThemeName] ?: themeMap.getValue("Default")
        return if (isDark) res.darkThemeResId else res.lightThemeResId
    }

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var pendingMedia: PendingCastMedia? = null

    private val _connectionState = MutableStateFlow(CastConnectionState.DISCONNECTED)
    val connectionState: StateFlow<CastConnectionState> = _connectionState

    private val _routeAvailable = MutableStateFlow(false)
    val routeAvailable: StateFlow<Boolean> = _routeAvailable

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _connectionState.value = CastConnectionState.CONNECTING
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            onConnected(session)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _connectionState.value = CastConnectionState.DISCONNECTED
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            onDisconnected()
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _connectionState.value = CastConnectionState.CONNECTING
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            onConnected(session)
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _connectionState.value = CastConnectionState.DISCONNECTED
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private val castStateListener = CastStateListener { state ->
        _routeAvailable.value = state != CastState.NO_DEVICES_AVAILABLE
    }

    fun init(context: Context) {
        if (castContext != null) return
        castContext = runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext?.addCastStateListener(castStateListener)
        castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { onConnected(it) }
    }

    fun isAvailable(): Boolean = castContext != null

    fun isConnected(): Boolean = _connectionState.value == CastConnectionState.CONNECTED

    fun routeSelector(): MediaRouteSelector? = castContext?.mergedSelector

    fun showCastPicker(context: Context, colorThemeName: String, isDark: Boolean) {
        val themedContext = ContextThemeWrapper(context, resolveThemeResId(colorThemeName, isDark))
        if (isConnected()) {
            MediaRouteControllerDialog(themedContext).show()
            return
        }
        val selector = routeSelector() ?: return
        MediaRouteChooserDialog(themedContext).apply { routeSelector = selector }.show()
    }

    fun requestLoad(url: String, title: String, mimeType: String?, isLive: Boolean, startPositionMs: Long) {
        pendingMedia = PendingCastMedia(url, title, mimeType, isLive, startPositionMs)
        if (isConnected()) loadPendingIfAny()
    }

    fun clearPending() {
        pendingMedia = null
    }

    private fun onConnected(session: CastSession) {
        currentSession = session
        _connectionState.value = CastConnectionState.CONNECTED
        loadPendingIfAny()
    }

    private fun onDisconnected() {
        currentSession = null
        _connectionState.value = CastConnectionState.DISCONNECTED
    }

    private fun loadPendingIfAny() {
        val media = pendingMedia ?: return
        val client = currentSession?.remoteMediaClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
        }
        val mediaInfo = MediaInfo.Builder(media.url)
            .setStreamType(if (media.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(media.mimeType ?: "application/x-mpegurl")
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(media.startPositionMs)
            .build()
        client.load(request)
    }

    fun play() {
        currentSession?.remoteMediaClient?.play()
    }

    fun pause() {
        currentSession?.remoteMediaClient?.pause()
    }

    fun seekTo(positionMs: Long) {
        currentSession?.remoteMediaClient?.seek(positionMs)
    }

    fun isRemotePlaying(): Boolean =
        currentSession?.remoteMediaClient?.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING

    fun remotePosition(): Long = currentSession?.remoteMediaClient?.approximateStreamPosition ?: 0L

    fun remoteDuration(): Long {
        val duration = currentSession?.remoteMediaClient?.mediaInfo?.streamDuration ?: 0L
        return if (duration > 0) duration else 0L
    }

    fun endSessionIfActive() {
        castContext?.sessionManager?.endCurrentSession(true)
    }
}
