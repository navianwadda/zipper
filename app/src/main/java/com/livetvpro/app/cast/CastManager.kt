package com.livetvpro.app.cast

import android.content.Context
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

object CastManager {

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

    fun showCastPicker(context: Context) {
        if (isConnected()) {
            MediaRouteControllerDialog(context).show()
            return
        }
        val selector = routeSelector() ?: return
        MediaRouteChooserDialog(context).apply { routeSelector = selector }.show()
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
