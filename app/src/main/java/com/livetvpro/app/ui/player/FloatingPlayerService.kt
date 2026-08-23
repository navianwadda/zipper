package com.livetvpro.app.ui.player

import android.widget.FrameLayout
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.util.UUID
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.ui.player.ChannelListCache
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@dagger.hilt.android.AndroidEntryPoint
class FloatingPlayerService : Service() {

    data class FloatingPlayerInstance(
        val instanceId: String,
        val floatingView: View,
        val player: ExoPlayer,
        val playerView: PlayerView,
        val params: WindowManager.LayoutParams,
        var currentChannel: Channel?,
        var currentEvent: com.livetvpro.app.data.models.LiveEvent? = null,
        var controlsLocked: Boolean = false,
        var isMuted: Boolean = false,
        val lockOverlay: View?,
        val unlockButton: ImageButton?,
        var isNetworkStream: Boolean = false,
        var networkStreamUrl: String? = null,
        var networkStreamName: String? = null,
        var networkCookie: String? = null,
        var networkReferer: String? = null,
        var networkOrigin: String? = null,
        var networkDrmLicense: String? = null,
        var networkUserAgent: String? = null,
        var networkDrmScheme: String? = null,
        var networkXForwardedFor: String? = null,
        var networkCustomHeaders: String? = null,
        var currentLinkIndex: Int = 0,
        var channelList: List<Channel>? = null,
var channelListCacheKey: String? = null,
var isSports: Boolean = false,
var isBoosted: Boolean = false
    )

    private var windowManager: WindowManager? = null
    private val activeInstances = mutableMapOf<String, FloatingPlayerInstance>()

    private val hideControlsHandlers = mutableMapOf<String, android.os.Handler>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @javax.inject.Inject
    lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager

    @javax.inject.Inject
    lateinit var channelRepository: com.livetvpro.app.data.repository.ChannelRepository

    @javax.inject.Inject
    lateinit var categoryRepository: com.livetvpro.app.data.repository.CategoryRepository

    private fun getMinWidth() = dpToPx(260)
    private fun getMaxWidth() = dpToPx(400)
    private fun getMinHeight() = getMinWidth() * 9 / 16
    private fun getMaxHeight() = getMaxWidth() * 9 / 16

    companion object {
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_EVENT = "extra_event"
        const val EXTRA_CHANNEL_LIST_KEY = "extra_channel_list_key"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PLAYBACK_POSITION = "extra_playback_position"
        const val EXTRA_LINK_INDEX = "extra_link_index"
        const val EXTRA_INSTANCE_ID = "extra_instance_id"
        const val EXTRA_RESTORE_POSITION = "extra_restore_position"
        const val ACTION_STOP = "action_stop"
        const val ACTION_STOP_INSTANCE = "action_stop_instance"
        const val ACTION_UPDATE_STREAM = "action_update_stream"
        const val ACTION_HIDE_OTHERS = "action_hide_others"
        const val ACTION_SHOW_ALL = "action_show_all"
        const val ACTION_UPDATE_NETWORK_STREAM = "action_update_network_stream"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_player_channel"

        fun start(context: Context, channel: Channel, linkIndex: Int = 0, playbackPosition: Long = 0L) {
            try {
                if (channel.streamUrl.isBlank() && channel.links.isNullOrEmpty()) return

                val intent = Intent(context, FloatingPlayerService::class.java).apply {
                    putExtra(EXTRA_CHANNEL, channel)
                    putExtra(EXTRA_TITLE, channel.name)
                    putExtra(EXTRA_PLAYBACK_POSITION, playbackPosition)
                    putExtra(EXTRA_LINK_INDEX, linkIndex)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPlayerService::class.java))
        }

        fun startFloatingPlayer(
            context: Context,
            instanceId: String,
            channel: Channel? = null,
            event: com.livetvpro.app.data.models.LiveEvent? = null,
            linkIndex: Int = 0,
            channelListCacheKey: String? = null,
            isSports: Boolean = false
        ): Boolean {
            try {
                if (channel == null && event == null) return false

                val title = channel?.name ?: event?.title ?: "Unknown"

                val intent = Intent(context, FloatingPlayerService::class.java).apply {
                    putExtra(EXTRA_INSTANCE_ID, instanceId)
                    if (channel != null) putExtra(EXTRA_CHANNEL, channel)
                    if (event != null) putExtra(EXTRA_EVENT, event)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PLAYBACK_POSITION, 0L)
                    putExtra(EXTRA_LINK_INDEX, linkIndex)
                    channelListCacheKey?.let { putExtra(EXTRA_CHANNEL_LIST_KEY, it) }
                    putExtra("EXTRA_IS_SPORTS", isSports)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                return true

            } catch (e: Exception) {
                return false
            }
        }

        fun startFloatingPlayerWithNetworkStream(
            context: Context,
            instanceId: String,
            streamUrl: String,
            cookie: String = "",
            referer: String = "",
            origin: String = "",
            drmLicense: String = "",
            userAgent: String = "",
            drmScheme: String = "clearkey",
            streamName: String = "Network Stream",
            xForwardedFor: String = "",
            customHeaders: String = ""
        ): Boolean {
            try {
                val intent = Intent(context, FloatingPlayerService::class.java).apply {
                    putExtra(EXTRA_INSTANCE_ID, instanceId)
                    putExtra("IS_NETWORK_STREAM", true)
                    putExtra("STREAM_URL", streamUrl)
                    putExtra("COOKIE", cookie)
                    putExtra("REFERER", referer)
                    putExtra("ORIGIN", origin)
                    putExtra("DRM_LICENSE", drmLicense)
                    putExtra("USER_AGENT", userAgent)
                    putExtra("DRM_SCHEME", drmScheme)
                    putExtra("CHANNEL_NAME", streamName)
                    putExtra("X_FORWARDED_FOR", xForwardedFor)
                    putExtra("CUSTOM_HEADERS", customHeaders)
                    putExtra(EXTRA_LINK_INDEX, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                return true

            } catch (e: Exception) {
                return false
            }
        }

        fun updateFloatingPlayer(context: Context, instanceId: String, channel: Channel, linkIndex: Int, channelListCacheKey: String? = null) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_UPDATE_STREAM
                putExtra(EXTRA_INSTANCE_ID, instanceId)
                putExtra(EXTRA_CHANNEL, channel)
                putExtra(EXTRA_LINK_INDEX, linkIndex)
                channelListCacheKey?.let { putExtra(EXTRA_CHANNEL_LIST_KEY, it) }
            }
            context.startService(intent)
        }

        fun updateFloatingPlayerWithEvent(context: Context, instanceId: String, channel: Channel, event: com.livetvpro.app.data.models.LiveEvent, linkIndex: Int) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_UPDATE_STREAM
                putExtra(EXTRA_INSTANCE_ID, instanceId)
                putExtra(EXTRA_CHANNEL, channel)
                putExtra(EXTRA_EVENT, event)
                putExtra(EXTRA_LINK_INDEX, linkIndex)
            }
            context.startService(intent)
        }

        fun updateFloatingPlayerWithNetworkStream(
            context: Context,
            instanceId: String,
            streamUrl: String,
            cookie: String = "",
            referer: String = "",
            origin: String = "",
            drmLicense: String = "",
            userAgent: String = "",
            drmScheme: String = "clearkey",
            streamName: String = "Network Stream",
            xForwardedFor: String = "",
            customHeaders: String = ""
        ) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_UPDATE_NETWORK_STREAM
                putExtra(EXTRA_INSTANCE_ID, instanceId)
                putExtra("STREAM_URL", streamUrl)
                putExtra("COOKIE", cookie)
                putExtra("REFERER", referer)
                putExtra("ORIGIN", origin)
                putExtra("DRM_LICENSE", drmLicense)
                putExtra("USER_AGENT", userAgent)
                putExtra("DRM_SCHEME", drmScheme)
                putExtra("CHANNEL_NAME", streamName)
                putExtra("X_FORWARDED_FOR", xForwardedFor)
                putExtra("CUSTOM_HEADERS", customHeaders)
            }
            context.startService(intent)
        }

        fun stopFloatingPlayer(context: Context, instanceId: String) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_STOP_INSTANCE
                putExtra(EXTRA_INSTANCE_ID, instanceId)
            }
            context.startService(intent)
        }

        fun hideOthers(context: Context, exceptInstanceId: String) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_HIDE_OTHERS
                putExtra(EXTRA_INSTANCE_ID, exceptInstanceId)
            }
            context.startService(intent)
        }

        fun showAll(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_SHOW_ALL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Floating Player")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (intent == null) return START_STICKY
        return try {
            handleStartCommand(intent)
        } catch (t: Throwable) {
            START_STICKY
        }
    }

    private fun handleStartCommand(intent: Intent): Int {
        when (intent.action) {
            ACTION_STOP -> {
                stopAllInstances()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_INSTANCE -> {
                val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID)
                if (instanceId != null) stopInstance(instanceId)
                if (activeInstances.isEmpty()) stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STREAM -> {
                val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID)
                val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CHANNEL, Channel::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CHANNEL)
                }
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_EVENT, com.livetvpro.app.data.models.LiveEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<com.livetvpro.app.data.models.LiveEvent>(EXTRA_EVENT)
                }
                val linkIndex = intent.getIntExtra(EXTRA_LINK_INDEX, 0)
                val updatedCacheKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
                if (instanceId != null && (channel != null || event != null)) {
                    if (updatedCacheKey != null) {
                        ChannelListCache.get(updatedCacheKey)?.let { activeInstances[instanceId]?.channelList = it }
                    }
                    updateInstanceStream(instanceId, channel, event, linkIndex)
                }
                return START_STICKY
            }
            ACTION_HIDE_OTHERS -> {
                val exceptId = intent.getStringExtra(EXTRA_INSTANCE_ID)
                activeInstances.forEach { (id, instance) ->
                    if (id != exceptId) instance.floatingView.visibility = View.INVISIBLE
                }
                return START_STICKY
            }
            ACTION_SHOW_ALL -> {
                activeInstances.values.forEach {
                    it.floatingView.visibility = View.VISIBLE
                    it.player.play()
                }
                return START_STICKY
            }
            ACTION_UPDATE_NETWORK_STREAM -> {
                val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID)
                val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
                val cookie = intent.getStringExtra("COOKIE") ?: ""
                val referer = intent.getStringExtra("REFERER") ?: ""
                val origin = intent.getStringExtra("ORIGIN") ?: ""
                val drmLicense = intent.getStringExtra("DRM_LICENSE") ?: ""
                val userAgent = intent.getStringExtra("USER_AGENT") ?: ""
                val drmScheme = intent.getStringExtra("DRM_SCHEME") ?: "clearkey"
                val streamName = intent.getStringExtra("CHANNEL_NAME") ?: "Network Stream"
                val xForwardedFor = intent.getStringExtra("X_FORWARDED_FOR") ?: ""
                val customHeaders = intent.getStringExtra("CUSTOM_HEADERS") ?: ""
                if (instanceId != null && streamUrl.isNotBlank()) {
                    updateInstanceStreamWithNetworkStream(instanceId, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme, streamName, xForwardedFor, customHeaders)
                }
                return START_STICKY
            }
        }

        val instanceId = intent?.getStringExtra(EXTRA_INSTANCE_ID) ?: java.util.UUID.randomUUID().toString()
        val isRestoredFromFullscreen = intent?.getBooleanExtra("use_transferred_player", false) == true
        val isNetworkStream = intent?.getBooleanExtra("IS_NETWORK_STREAM", false) == true

        if (activeInstances.containsKey(instanceId) && !isRestoredFromFullscreen) {
            return START_STICKY
        }

        if (isNetworkStream) {
            val streamUrl = intent?.getStringExtra("STREAM_URL") ?: ""
            val cookie = intent?.getStringExtra("COOKIE") ?: ""
            val referer = intent?.getStringExtra("REFERER") ?: ""
            val origin = intent?.getStringExtra("ORIGIN") ?: ""
            val drmLicense = intent?.getStringExtra("DRM_LICENSE") ?: ""
            val userAgent = intent?.getStringExtra("USER_AGENT") ?: ""
            val drmScheme = intent?.getStringExtra("DRM_SCHEME") ?: "clearkey"
            val streamName = intent?.getStringExtra("CHANNEL_NAME") ?: "Network Stream"
            val xForwardedFor = intent?.getStringExtra("X_FORWARDED_FOR") ?: ""
            val customHeaders = intent?.getStringExtra("CUSTOM_HEADERS") ?: ""

            val restorePosition = intent?.getBooleanExtra(EXTRA_RESTORE_POSITION, false) ?: false
            if (isRestoredFromFullscreen && PlayerHolder.player != null) {
                createFloatingPlayerInstanceFromNetworkStreamTransfer(
                    instanceId, streamName, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme, xForwardedFor, customHeaders = customHeaders, restorePosition = restorePosition
                )
                updateNotification()
            } else if (streamUrl.isNotBlank()) {
                createFloatingPlayerInstanceForNetworkStream(instanceId, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme, streamName, xForwardedFor, customHeaders = customHeaders, restorePosition = restorePosition)
                updateNotification()
            }
            return START_STICKY
        }

        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CHANNEL, Channel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_CHANNEL)
        }

        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_EVENT, com.livetvpro.app.data.models.LiveEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_EVENT)
        }

        val parsedChannelListKey = intent?.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
        val parsedChannelList: List<Channel>? = parsedChannelListKey?.let { ChannelListCache.get(it) }

        val linkIndex = intent?.getIntExtra(EXTRA_LINK_INDEX, 0) ?: 0
        val playbackPosition = intent?.getLongExtra(EXTRA_PLAYBACK_POSITION, 0L) ?: 0L
        val restorePosition = intent?.getBooleanExtra(EXTRA_RESTORE_POSITION, false) ?: false
        val useTransferredPlayer = intent?.getBooleanExtra("use_transferred_player", false) ?: false
        val isSports = intent?.getBooleanExtra("EXTRA_IS_SPORTS", false) ?: false

        if (channel != null || event != null) {
            if (useTransferredPlayer) {
    createFloatingPlayerInstanceFromTransfer(instanceId, channel, event, restorePosition, parsedChannelList, isSports)
} else {
    createFloatingPlayerInstance(instanceId, channel, event, linkIndex, playbackPosition, restorePosition, parsedChannelList, isSports)
}
activeInstances[instanceId]?.channelListCacheKey = parsedChannelListKey
            updateNotification()
        }

        return START_STICKY
    }

    private fun createFloatingPlayerInstance(
        instanceId: String,
        channel: Channel? = null,
        event: com.livetvpro.app.data.models.LiveEvent? = null,
        linkIndex: Int,
        playbackPosition: Long,
        restorePosition: Boolean = false,
        channelList: List<Channel>? = null,
        isSports: Boolean = false
    ) {
        try {
            val streamUrl = when {
                channel != null -> {
                    val links = channel.links
                    if (!links.isNullOrEmpty()) {
                        val selectedLink = if (linkIndex in links.indices) links[linkIndex] else links.firstOrNull()
                        selectedLink?.url ?: channel.streamUrl
                    } else {
                        channel.streamUrl
                    }.also { if (it.isBlank()) return }
                }
                event != null -> {
                    val links = event.links
                    if (links.isEmpty()) return
                    val selectedLink = if (linkIndex in links.indices) links[linkIndex] else links.firstOrNull()
                    selectedLink?.url ?: return
                }
                else -> return
            }

            val title = channel?.name ?: event?.title ?: "Unknown"
            val floatingView = LayoutInflater.from(this).inflate(R.layout.floating_player_window, null)

            val screenWidth = getScreenWidth()
            val screenHeight = getScreenHeight()
            val statusBarHeight: Int = run {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            }

            val savedWidth = preferencesManager.getFloatingPlayerWidth()
            val savedHeight = preferencesManager.getFloatingPlayerHeight()
            val initialWidth = if (restorePosition && savedWidth > 0) savedWidth.coerceIn(getMinWidth(), getMaxWidth()) else getMinWidth()
            val initialHeight = if (restorePosition && savedHeight > 0) savedHeight.coerceIn(getMinHeight(), getMaxHeight()) else getMinHeight()

            val savedX = preferencesManager.getFloatingPlayerX()
            val savedY = preferencesManager.getFloatingPlayerY()
            val initialX = if (restorePosition && savedX != Int.MIN_VALUE) savedX else (screenWidth - initialWidth) / 2
            val initialY = if (restorePosition && savedY != Int.MIN_VALUE) savedY else statusBarHeight + (screenHeight - statusBarHeight - initialHeight) / 2

            val params = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }

            val parsedStream: StreamInfo = when {
                channel != null -> {
                    val links = channel.links
                    val sel = if (links != null && linkIndex in links.indices) links[linkIndex] else links?.firstOrNull()
                    if (sel != null) buildStreamInfoFromLink(sel.url, sel.cookie, sel.referer, sel.origin, sel.userAgent, sel.drmScheme, sel.drmLicenseUrl, sel.xForwardedFor, sel.customHeaders, sel.drmJwk)
                    else parseStreamUrl(streamUrl)
                }
                event != null -> {
                    val links = event.links
                    val sel = if (linkIndex in links.indices) links[linkIndex] else links.firstOrNull()
                    if (sel != null) buildStreamInfoFromLink(sel.url, sel.cookie, sel.referer, sel.origin, sel.userAgent, sel.drmScheme, sel.drmLicenseUrl, sel.xForwardedFor, sel.customHeaders, sel.drmJwk)
                    else parseStreamUrl(streamUrl)
                }
                else -> parseStreamUrl(streamUrl)
            }
            val actualUrl = parsedStream.url
            val headers = parsedStream.headers.toMutableMap()
            val ua = headers["User-Agent"]
            if (ua.isNullOrBlank() || ua == "Default") {
                headers["User-Agent"] = "okhttp/4.12.0"
            }
            val effectiveStreamInfo = parsedStream
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val mediaSourceFactory = buildDrmMediaSourceFactory(effectiveStreamInfo, dataSourceFactory, headers)
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            val player = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(false)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false
                )
                .build()
            com.livetvpro.app.utils.VolumeBoostHelper.attach(player, preferencesManager)
            val playerView = floatingView.findViewById<PlayerView>(R.id.player_view)
            playerView.player = player

            val mediaItem = buildDrmMediaItem(effectiveStreamInfo, headers)
            player.setMediaItem(mediaItem)

            if (playbackPosition > 0) player.seekTo(playbackPosition)

            val titleText = floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = title

            val lockOverlay = floatingView.findViewById<View>(R.id.lock_overlay)
            val unlockButton = floatingView.findViewById<ImageButton>(R.id.unlock_button)

            setupFloatingControls(floatingView, playerView, params, instanceId, player, lockOverlay, unlockButton, channel, event)

            player.prepare()
            player.playWhenReady = true

            windowManager?.addView(floatingView, params)
            hideControlsHandlers[instanceId] = android.os.Handler(android.os.Looper.getMainLooper())

            val instance = FloatingPlayerInstance(
                instanceId = instanceId,
                floatingView = floatingView,
                player = player,
                playerView = playerView,
                params = params,
                currentChannel = channel,
                currentEvent = event,
                lockOverlay = lockOverlay,
                unlockButton = unlockButton,
                currentLinkIndex = linkIndex,
                channelList = channelList,
                isSports = isSports
            )

            activeInstances[instanceId] = instance

            if (activeInstances.size == 1) {
                val notification = createNotification(title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

        } catch (e: Throwable) {
        }
    }

    private fun createFloatingPlayerInstanceFromTransfer(
        instanceId: String,
        channel: Channel? = null,
        event: com.livetvpro.app.data.models.LiveEvent? = null,
        restorePosition: Boolean,
        channelList: List<Channel>? = null,
        isSports: Boolean = false
    ) {
        try {
            val transferredPlayer = PlayerHolder.player
            if (transferredPlayer == null) {
                createFloatingPlayerInstance(instanceId, channel, event, 0, 0L, restorePosition, channelList, isSports)
                return
            }

            PlayerHolder.clearReferences()

            val floatingView = LayoutInflater.from(this).inflate(R.layout.floating_player_window, null)

            val screenWidth = getScreenWidth()
            val screenHeight = getScreenHeight()
            val statusBarHeight: Int = run {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            }

            val savedWidth = preferencesManager.getFloatingPlayerWidth()
            val savedHeight = preferencesManager.getFloatingPlayerHeight()
            val initialWidth = if (restorePosition && savedWidth > 0) savedWidth.coerceIn(getMinWidth(), getMaxWidth()) else getMinWidth()
            val initialHeight = if (restorePosition && savedHeight > 0) savedHeight.coerceIn(getMinHeight(), getMaxHeight()) else getMinHeight()

            val savedX = preferencesManager.getFloatingPlayerX()
            val savedY = preferencesManager.getFloatingPlayerY()
            val initialX = if (restorePosition && savedX != Int.MIN_VALUE) savedX else (screenWidth - initialWidth) / 2
            val initialY = if (restorePosition && savedY != Int.MIN_VALUE) savedY
                           else statusBarHeight + (screenHeight - statusBarHeight - initialHeight) / 2

            val params = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = initialX
                y = initialY
            }

            val playerView = floatingView.findViewById<PlayerView>(R.id.player_view)
            playerView.player = transferredPlayer

            val titleText = floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = channel?.name ?: event?.title ?: "Unknown"

            val lockOverlay = floatingView.findViewById<View>(R.id.lock_overlay)
            val unlockButton = floatingView.findViewById<ImageButton>(R.id.unlock_button)

            setupFloatingControls(floatingView, playerView, params, instanceId, transferredPlayer, lockOverlay, unlockButton, channel, event)

            windowManager?.addView(floatingView, params)
            hideControlsHandlers[instanceId] = android.os.Handler(android.os.Looper.getMainLooper())

            val instance = FloatingPlayerInstance(
                instanceId = instanceId,
                floatingView = floatingView,
                player = transferredPlayer,
                playerView = playerView,
                params = params,
                currentChannel = channel,
                currentEvent = event,
                lockOverlay = lockOverlay,
                unlockButton = unlockButton,
                channelList = channelList,
                isSports = isSports
            )
            activeInstances[instanceId] = instance

            val contentName = channel?.name ?: event?.title ?: "Unknown"
            val contentType = if (channel != null) "channel" else "event"
            com.livetvpro.app.utils.FloatingPlayerManager.addPlayer(instanceId, contentName, contentType)

            if (activeInstances.size == 1) {
                val notification = createNotification(contentName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

        } catch (e: Exception) {
        }
    }

    private fun createFloatingPlayerInstanceFromNetworkStreamTransfer(
        instanceId: String,
        streamName: String,
        streamUrl: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String,
        xForwardedFor: String = "",
        customHeaders: String = "",
        restorePosition: Boolean = false
    ) {
        try {
            val transferredPlayer = PlayerHolder.player
            if (transferredPlayer == null) {
                createFloatingPlayerInstanceForNetworkStream(
                    instanceId, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme, streamName, xForwardedFor, customHeaders = customHeaders, restorePosition = restorePosition
                )
                return
            }

            PlayerHolder.clearReferences()

            val floatingView = LayoutInflater.from(this).inflate(R.layout.floating_player_window, null)

            val screenWidth = getScreenWidth()
            val screenHeight = getScreenHeight()
            val statusBarHeight: Int = run {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            }

            val initialWidth = getMinWidth()
            val initialHeight = getMinHeight()

            val savedX = preferencesManager.getFloatingPlayerX()
            val savedY = preferencesManager.getFloatingPlayerY()
            val initialX = if (savedX != Int.MIN_VALUE) savedX else (screenWidth - initialWidth) / 2
            val initialY = if (savedY != Int.MIN_VALUE) savedY
                           else statusBarHeight + (screenHeight - statusBarHeight - initialHeight) / 2

            val params = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = initialX
                y = initialY
            }

            val playerView = floatingView.findViewById<PlayerView>(R.id.player_view)
            playerView.player = transferredPlayer

            val titleText = floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = streamName

            val lockOverlay = floatingView.findViewById<View>(R.id.lock_overlay)
            val unlockButton = floatingView.findViewById<ImageButton>(R.id.unlock_button)

            setupFloatingControls(floatingView, playerView, params, instanceId, transferredPlayer, lockOverlay, unlockButton, null, null)

            windowManager?.addView(floatingView, params)
            hideControlsHandlers[instanceId] = android.os.Handler(android.os.Looper.getMainLooper())

            val instance = FloatingPlayerInstance(
                instanceId = instanceId,
                floatingView = floatingView,
                player = transferredPlayer,
                playerView = playerView,
                params = params,
                currentChannel = null,
                currentEvent = null,
                lockOverlay = lockOverlay,
                unlockButton = unlockButton,
                isNetworkStream = true,
                networkStreamUrl = streamUrl,
                networkStreamName = streamName,
                networkCookie = cookie,
                networkReferer = referer,
                networkOrigin = origin,
                networkDrmLicense = drmLicense,
                networkUserAgent = userAgent,
                networkDrmScheme = drmScheme,
                networkXForwardedFor = xForwardedFor,
                networkCustomHeaders = customHeaders,
            )
            activeInstances[instanceId] = instance

            com.livetvpro.app.utils.FloatingPlayerManager.addPlayer(instanceId, streamName, "network_stream")

            if (activeInstances.size == 1) {
                val notification = createNotification(streamName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

        } catch (e: Exception) {
        }
    }

    private fun createFloatingPlayerInstanceForNetworkStream(
        instanceId: String,
        streamUrl: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String,
        streamName: String,
        xForwardedFor: String = "",
        customHeaders: String = "",
        restorePosition: Boolean = false
    ) {
        try {
            val floatingView = LayoutInflater.from(this).inflate(R.layout.floating_player_window, null)

            val screenWidth = getScreenWidth()
            val screenHeight = getScreenHeight()
            val statusBarHeight: Int = run {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            }

            val savedWidth = preferencesManager.getFloatingPlayerWidth()
            val savedHeight = preferencesManager.getFloatingPlayerHeight()
            val initialWidth = if (restorePosition && savedWidth > 0) savedWidth.coerceIn(getMinWidth(), getMaxWidth()) else getMinWidth()
            val initialHeight = if (restorePosition && savedHeight > 0) savedHeight.coerceIn(getMinHeight(), getMaxHeight()) else getMinHeight()

            val savedX = preferencesManager.getFloatingPlayerX()
            val savedY = preferencesManager.getFloatingPlayerY()
            val initialX = if (restorePosition && savedX != Int.MIN_VALUE) savedX else (screenWidth - initialWidth) / 2
            val initialY = if (restorePosition && savedY != Int.MIN_VALUE) savedY
                           else statusBarHeight + (screenHeight - statusBarHeight - initialHeight) / 2

            val layoutParams = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = initialX
                y = initialY
            }

            val parsedPipe = parseStreamUrl(streamUrl)
            val headers = mutableMapOf<String, String>()
            headers.putAll(parsedPipe.headers)
            if (cookie.isNotEmpty()) headers["Cookie"] = cookie
            if (referer.isNotEmpty()) headers["Referer"] = referer
            if (origin.isNotEmpty()) headers["Origin"] = origin
            if (xForwardedFor.isNotEmpty()) headers["X-Forwarded-For"] = xForwardedFor
            if (customHeaders.isNotBlank()) {
                try {
                    val json = org.json.JSONObject(customHeaders)
                    json.keys().asSequence().forEach { headers[it] = json.getString(it) }
                } catch (_: Exception) {}
            }
            val effectiveUserAgent = if (userAgent.isNotEmpty() && userAgent != "Default")
                userAgent
            else
                headers["User-Agent"] ?: "okhttp/4.12.0"
            headers["User-Agent"] = effectiveUserAgent

            val resolvedDrmScheme = parsedPipe.drmScheme ?: normalizeDrmScheme(drmScheme).takeIf { it.isNotEmpty() }
            val resolvedDrmLicense = when {
                parsedPipe.drmJwk != null -> parsedPipe.drmJwk
                parsedPipe.drmKeyId != null && parsedPipe.drmKey != null ->
                    "${parsedPipe.drmKeyId}:${parsedPipe.drmKey}"
                parsedPipe.drmLicenseUrl != null -> parsedPipe.drmLicenseUrl
                else -> drmLicense
            }

            val nsStreamInfo = buildStreamInfoFromDrmFields(
                parsedPipe.url, headers,
                resolvedDrmScheme ?: drmScheme,
                resolvedDrmLicense
            )

            val nsDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(effectiveUserAgent)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val nsMediaSourceFactory = buildDrmMediaSourceFactory(nsStreamInfo, nsDataSourceFactory, headers)

            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            val player = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(nsMediaSourceFactory)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(false)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false
                )
                .build()
            com.livetvpro.app.utils.VolumeBoostHelper.attach(player, preferencesManager)

            val nsMediaItem = buildDrmMediaItem(nsStreamInfo, headers)
            player.setMediaItem(nsMediaItem)

            val playerView = floatingView.findViewById<PlayerView>(R.id.player_view)
            playerView.player = player

            val titleText = floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = streamName

            val lockOverlay = floatingView.findViewById<View>(R.id.lock_overlay)
            val unlockButton = floatingView.findViewById<ImageButton>(R.id.unlock_button)

            setupFloatingControls(floatingView, playerView, layoutParams, instanceId, player, lockOverlay, unlockButton, null, null)

            player.prepare()
            player.playWhenReady = true

            windowManager?.addView(floatingView, layoutParams)
            hideControlsHandlers[instanceId] = android.os.Handler(android.os.Looper.getMainLooper())

            val instance = FloatingPlayerInstance(
                instanceId = instanceId,
                floatingView = floatingView,
                player = player,
                playerView = playerView,
                params = layoutParams,
                currentChannel = null,
                currentEvent = null,
                lockOverlay = lockOverlay,
                unlockButton = unlockButton,
                isNetworkStream = true,
                networkStreamUrl = streamUrl,
                networkStreamName = streamName,
                networkCookie = cookie,
                networkReferer = referer,
                networkOrigin = origin,
                networkDrmLicense = drmLicense,
                networkUserAgent = userAgent,
                networkDrmScheme = drmScheme,
                networkXForwardedFor = xForwardedFor,
                networkCustomHeaders = customHeaders,
            )
            activeInstances[instanceId] = instance

            if (activeInstances.size == 1) {
                val notification = createNotification(streamName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

        } catch (e: Throwable) {
        }
    }

    private fun updateInstanceStream(
        instanceId: String,
        channel: Channel? = null,
        event: com.livetvpro.app.data.models.LiveEvent? = null,
        linkIndex: Int
    ) {
        val instance = activeInstances[instanceId] ?: return

        try {
            val parsedStream: StreamInfo = when {
                channel != null -> {
                    val sel = if (!channel.links.isNullOrEmpty()) {
                        if (linkIndex in channel.links!!.indices) channel.links!![linkIndex]
                        else channel.links!!.firstOrNull()
                    } else null
                    if (sel != null) buildStreamInfoFromLink(sel.url, sel.cookie, sel.referer, sel.origin, sel.userAgent, sel.drmScheme, sel.drmLicenseUrl, sel.xForwardedFor, sel.customHeaders, sel.drmJwk)
                    else {
                        val fallbackUrl = channel.streamUrl.takeIf { it.isNotBlank() } ?: return
                        parseStreamUrl(fallbackUrl)
                    }
                }
                event != null -> {
                    val links = event.links
                    if (links.isEmpty()) return
                    val sel = if (linkIndex in links.indices) links[linkIndex] else links.firstOrNull() ?: return
                    buildStreamInfoFromLink(sel.url, sel.cookie, sel.referer, sel.origin, sel.userAgent, sel.drmScheme, sel.drmLicenseUrl, sel.xForwardedFor, sel.customHeaders, sel.drmJwk)
                }
                else -> return
            }

            if (parsedStream.url.isBlank()) return
            val headers = parsedStream.headers.toMutableMap()
            val ua = headers["User-Agent"]
            if (ua.isNullOrBlank() || ua == "Default") {
                headers["User-Agent"] = "okhttp/4.12.0"
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val mediaSourceFactory = buildDrmMediaSourceFactory(parsedStream, dataSourceFactory, headers)

            instance.currentChannel = channel
            instance.currentEvent = event
            instance.isNetworkStream = false
            instance.networkStreamUrl = null
            instance.networkStreamName = null
            instance.networkCookie = null
            instance.networkReferer = null
            instance.networkOrigin = null
            instance.networkDrmLicense = null
            instance.networkUserAgent = null
            instance.networkDrmScheme = null
            instance.networkXForwardedFor = null
            instance.currentLinkIndex = linkIndex

            val titleText = instance.floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = channel?.name ?: event?.title ?: "Unknown"

            val wasMuted = instance.isMuted
            instance.player.stop()
            com.livetvpro.app.utils.VolumeBoostHelper.release(instance.player)
            instance.player.release()

            val renderersFactory2 = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            val trackSelector2 = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            val newPlayer = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory2)
                .setTrackSelector(trackSelector2)
                .setMediaSourceFactory(mediaSourceFactory)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(false)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false
                )
                .build()
            com.livetvpro.app.utils.VolumeBoostHelper.attach(newPlayer, preferencesManager)
            com.livetvpro.app.utils.VolumeBoostHelper.setBoostLevel(newPlayer, if (instance.isBoosted) 100 else 0)
            newPlayer.volume = if (wasMuted) 0f else 1f
            instance.playerView.player = newPlayer

            val mediaItem = buildDrmMediaItem(parsedStream, headers)
            newPlayer.setMediaItem(mediaItem)

            activeInstances[instanceId] = instance.copy(player = newPlayer)
            val btnPlayPause1 = instance.playerView.findViewById<ImageButton>(R.id.btn_play_pause)
            attachPlayerListener(newPlayer, btnPlayPause1, instanceId)

            newPlayer.prepare()
            newPlayer.playWhenReady = true

            updateNotification()

        } catch (e: Throwable) {
        }
    }

    private fun updateInstanceStreamWithNetworkStream(
        instanceId: String,
        streamUrl: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String,
        streamName: String,
        xForwardedFor: String,
        customHeaders: String = ""
    ) {
        val instance = activeInstances[instanceId] ?: return

        try {
            val headers = mutableMapOf<String, String>()
            if (cookie.isNotEmpty()) headers["Cookie"] = cookie
            if (referer.isNotEmpty()) headers["Referer"] = referer
            if (origin.isNotEmpty()) headers["Origin"] = origin
            if (xForwardedFor.isNotEmpty()) headers["X-Forwarded-For"] = xForwardedFor
            if (customHeaders.isNotBlank()) {
                try {
                    val json = org.json.JSONObject(customHeaders)
                    json.keys().asSequence().forEach { headers[it] = json.getString(it) }
                } catch (_: Exception) {}
            }
            val effectiveUserAgent = if (userAgent.isNotEmpty() && userAgent != "Default")
                userAgent else "okhttp/4.12.0"
            headers["User-Agent"] = effectiveUserAgent

            val nsStreamInfo = resolveNetworkStreamInfo(streamUrl, headers, drmScheme, drmLicense)
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(effectiveUserAgent)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val mediaSourceFactory = buildDrmMediaSourceFactory(nsStreamInfo, dataSourceFactory, headers)

            val titleText = instance.floatingView.findViewById<TextView>(R.id.tv_title)
            titleText.text = streamName

            val wasMuted = instance.isMuted
            instance.player.stop()
            com.livetvpro.app.utils.VolumeBoostHelper.release(instance.player)
            instance.player.release()

            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            val newPlayer = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(false)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false
                )
                .build()
            com.livetvpro.app.utils.VolumeBoostHelper.attach(newPlayer, preferencesManager)
            com.livetvpro.app.utils.VolumeBoostHelper.setBoostLevel(newPlayer, if (instance.isBoosted) 100 else 0)
            newPlayer.volume = if (wasMuted) 0f else 1f
            instance.playerView.player = newPlayer

            newPlayer.setMediaItem(buildDrmMediaItem(nsStreamInfo, headers))

            activeInstances[instanceId] = instance.copy(
                player = newPlayer,
                currentChannel = null,
                currentEvent = null,
                isNetworkStream = true,
                networkStreamUrl = streamUrl,
                networkStreamName = streamName,
                networkCookie = cookie,
                networkReferer = referer,
                networkOrigin = origin,
                networkDrmLicense = drmLicense,
                networkUserAgent = userAgent,
                networkDrmScheme = drmScheme,
                networkXForwardedFor = xForwardedFor,
                networkCustomHeaders = customHeaders,
            )
            val btnPlayPause = instance.playerView.findViewById<ImageButton>(R.id.btn_play_pause)
            attachPlayerListener(newPlayer, btnPlayPause, instanceId)

            newPlayer.prepare()
            newPlayer.playWhenReady = true

            updateNotification()

        } catch (e: Throwable) {
        }
    }

    private fun setupFloatingControls(
        floatingView: View,
        playerView: PlayerView,
        params: WindowManager.LayoutParams,
        instanceId: String,
        player: ExoPlayer,
        lockOverlay: View?,
        unlockButton: ImageButton?,
        channel: Channel?,
        event: com.livetvpro.app.data.models.LiveEvent? = null
    ) {
        val btnClose = playerView.findViewById<ImageButton>(R.id.btn_close)
        val btnFullscreen = playerView.findViewById<ImageButton>(R.id.btn_fullscreen)
        val btnMute = playerView.findViewById<ImageButton>(R.id.btn_mute)
        val btnLock = playerView.findViewById<ImageButton>(R.id.btn_lock)
        val btnPlayPause = playerView.findViewById<ImageButton>(R.id.btn_play_pause)
        val btnSeekBack = playerView.findViewById<ImageButton>(R.id.btn_seek_back)

        playerView.findViewById<android.widget.ProgressBar?>(androidx.media3.ui.R.id.exo_buffering)
            ?.indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        val btnSeekForward = playerView.findViewById<ImageButton>(R.id.btn_seek_forward)
        val btnPrevChannel = playerView.findViewById<ImageButton>(R.id.btn_prev_channel)
        val btnNextChannel = playerView.findViewById<ImageButton>(R.id.btn_next_channel)
        val btnResize = floatingView.findViewById<ImageButton>(R.id.btn_resize)

        val layoutMode = preferencesManager.getLayoutMode()
        val isNetworkStream = (channel == null && event == null)
        val showSeeks = layoutMode == com.livetvpro.app.data.local.PreferencesManager.LAYOUT_MODE_SEEKS_ONLY ||
                        layoutMode == com.livetvpro.app.data.local.PreferencesManager.LAYOUT_MODE_SEEKS_AND_NAV
        val showNav   = !isNetworkStream && (
                        layoutMode == com.livetvpro.app.data.local.PreferencesManager.LAYOUT_MODE_SEEKS_AND_NAV ||
                        layoutMode == com.livetvpro.app.data.local.PreferencesManager.LAYOUT_MODE_NAV_ONLY)
        btnSeekBack?.visibility    = if (showSeeks) View.VISIBLE else View.GONE
        btnSeekForward?.visibility = if (showSeeks) View.VISIBLE else View.GONE
        btnPrevChannel?.visibility = if (showNav) View.VISIBLE else View.GONE
        btnNextChannel?.visibility = if (showNav) View.VISIBLE else View.GONE

        btnClose?.setOnClickListener {
            stopInstance(instanceId)
        }

        btnFullscreen?.setOnClickListener {
            val instance = activeInstances[instanceId]
            val currentPlayer = instance?.player
            val currentChannel = instance?.currentChannel
            val currentEvent = instance?.currentEvent

            if (currentPlayer != null) {
                preferencesManager.setFloatingPlayerWidth(params.width)
                preferencesManager.setFloatingPlayerHeight(params.height)
                preferencesManager.setFloatingPlayerX(params.x)
                preferencesManager.setFloatingPlayerY(params.y)

                val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                val streamUrl = when {
                    currentUri != null -> currentUri
                    currentChannel != null -> currentChannel.links?.firstOrNull()?.url ?: ""
                    currentEvent != null -> currentEvent.links.firstOrNull()?.url ?: ""
                    else -> ""
                }
                val contentName = currentChannel?.name ?: currentEvent?.title ?: "Unknown"
                PlayerHolder.transferPlayer(currentPlayer, streamUrl, contentName)

                activeInstances.forEach { (id, inst) ->
                    if (id != instanceId) {
                        inst.floatingView.visibility = View.INVISIBLE
                        inst.player.pause()
                    }
                }

                val intent = Intent(this, FloatingPlayerActivity::class.java).apply {
                    val inst = activeInstances[instanceId]
                    if (inst?.isNetworkStream == true) {
                        putExtra("IS_NETWORK_STREAM", true)
                        putExtra("STREAM_URL", inst.networkStreamUrl ?: "")
                        putExtra("CHANNEL_NAME", inst.networkStreamName ?: "Network Stream")
                        putExtra("COOKIE", inst.networkCookie ?: "")
                        putExtra("REFERER", inst.networkReferer ?: "")
                        putExtra("ORIGIN", inst.networkOrigin ?: "")
                        putExtra("DRM_LICENSE", inst.networkDrmLicense ?: "")
                        putExtra("USER_AGENT", inst.networkUserAgent ?: "Default")
                        putExtra("DRM_SCHEME", inst.networkDrmScheme ?: "clearkey")
                        putExtra("X_FORWARDED_FOR", inst.networkXForwardedFor ?: "")
                        putExtra("CUSTOM_HEADERS", inst.networkCustomHeaders ?: "")
                    } else {
                        if (currentChannel != null) putExtra("extra_channel", currentChannel)
if (currentEvent != null) putExtra("extra_event", currentEvent)
putExtra("extra_selected_link_index", inst?.currentLinkIndex ?: 0)
putExtra("extra_is_sports", inst?.isSports ?: false)
currentChannel?.categoryId?.takeIf { it.isNotEmpty() }?.let {
    putExtra("extra_category_id", it)
}
inst?.channelListCacheKey?.let { putExtra("extra_channel_list_key", it) }
                    }
                    putExtra("use_transferred_player", true)
                    putExtra("source_instance_id", instanceId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)

                hideControlsHandlers[instanceId]?.removeCallbacksAndMessages(null)
                hideControlsHandlers.remove(instanceId)
                try {
                    windowManager?.removeView(activeInstances[instanceId]?.floatingView)
                } catch (e: Exception) { }
                activeInstances.remove(instanceId)
                com.livetvpro.app.utils.FloatingPlayerManager.removePlayer(instanceId)

                if (activeInstances.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
        }

        btnMute?.setOnClickListener {
            val instance = activeInstances[instanceId] ?: return@setOnClickListener
            instance.isMuted = !instance.isMuted
            instance.player.volume = if (instance.isMuted) 0f else 1f
            btnMute.setImageResource(if (instance.isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        }

        btnMute?.setOnLongClickListener {
            val instance = activeInstances[instanceId] ?: return@setOnLongClickListener true
            if (!preferencesManager.isVolumeBoostingEnabled()) {
                android.widget.Toast.makeText(this, "Turn on \"Allow volume boosting\" in Player Settings first", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
            instance.isBoosted = !instance.isBoosted
            com.livetvpro.app.utils.VolumeBoostHelper.setBoostLevel(instance.player, if (instance.isBoosted) 100 else 0)
            android.widget.Toast.makeText(this, if (instance.isBoosted) "Volume boost on" else "Volume boost off", android.widget.Toast.LENGTH_SHORT).show()
            true
        }

        btnLock?.setOnClickListener {
            toggleLock(instanceId)
        }

        unlockButton?.setOnClickListener {
            toggleLock(instanceId)
        }

        btnPlayPause?.setOnClickListener {
            val instance = activeInstances[instanceId] ?: return@setOnClickListener
            val p = instance.player
            val hasError = p.playerError != null
            val hasEnded = p.playbackState == Player.STATE_ENDED

            if (hasError || hasEnded) {
                retryInstance(instanceId)
            } else {
                if (p.isPlaying) {
                    p.pause()
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                } else {
                    p.play()
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        btnSeekBack?.setOnClickListener {
            activeInstances[instanceId]?.player?.seekBack()
        }

        btnSeekForward?.setOnClickListener {
            activeInstances[instanceId]?.player?.seekForward()
        }

        btnPrevChannel?.setOnClickListener {
            val instance = activeInstances[instanceId] ?: return@setOnClickListener
            if (instance.currentEvent != null) {
                val currentEvent = instance.currentEvent!!
                if (currentEvent.links.size > 1) {
                    val prevIndex = (instance.currentLinkIndex - 1).coerceAtLeast(0)
                    if (prevIndex != instance.currentLinkIndex) {
                        updateInstanceStream(instanceId, null, currentEvent, prevIndex)
                    }
                }
                return@setOnClickListener
            }
            val currentChannel = instance.currentChannel ?: return@setOnClickListener
            serviceScope.launch {
                val allChannels = instance.channelList?.takeIf { it.isNotEmpty() }
                    ?: withContext(Dispatchers.IO) {
                        if (instance.isSports) {
                            categoryRepository.getSports()
                        } else {
                            channelRepository.getChannelsByCategory(currentChannel.categoryId)
                        }
                    }.also { if (it.isNotEmpty()) instance.channelList = it }
                if (allChannels.isEmpty()) return@launch
                val currentIndex = allChannels.indexOfFirst { it.id == currentChannel.id }.takeIf { it != -1 } ?: 0
                val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                if (prevIndex != currentIndex) {
                    updateInstanceStream(instanceId, allChannels[prevIndex], null, 0)
                }
            }
        }

        btnNextChannel?.setOnClickListener {
            val instance = activeInstances[instanceId] ?: return@setOnClickListener
            if (instance.currentEvent != null) {
                val currentEvent = instance.currentEvent!!
                if (currentEvent.links.size > 1) {
                    val nextIndex = (instance.currentLinkIndex + 1).coerceAtMost(currentEvent.links.size - 1)
                    if (nextIndex != instance.currentLinkIndex) {
                        updateInstanceStream(instanceId, null, currentEvent, nextIndex)
                    }
                }
                return@setOnClickListener
            }
            val currentChannel = instance.currentChannel ?: return@setOnClickListener
            serviceScope.launch {
                val allChannels = instance.channelList?.takeIf { it.isNotEmpty() }
                    ?: withContext(Dispatchers.IO) {
                        if (instance.isSports) {
                            categoryRepository.getSports()
                        } else {
                            channelRepository.getChannelsByCategory(currentChannel.categoryId)
                        }
                    }.also { if (it.isNotEmpty()) instance.channelList = it }
                if (allChannels.isEmpty()) return@launch
                val currentIndex = allChannels.indexOfFirst { it.id == currentChannel.id }.takeIf { it != -1 } ?: 0
                val nextIndex = (currentIndex + 1).coerceAtMost(allChannels.size - 1)
                if (nextIndex != currentIndex) {
                    updateInstanceStream(instanceId, allChannels[nextIndex], null, 0)
                }
            }
        }

        attachPlayerListener(player, btnPlayPause, instanceId)

        setupResizeFunctionality(floatingView, btnResize, params, instanceId)
        setupDragFunctionality(floatingView, params, playerView, lockOverlay, unlockButton, instanceId)
    }

    private fun attachPlayerListener(player: ExoPlayer, btnPlayPause: ImageButton?, instanceId: String) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val instance = activeInstances[instanceId] ?: return
                if (instance.player !== player) return
                if (player.playerError != null) return
                btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val instance = activeInstances[instanceId] ?: return
                if (instance.player !== player) return
                if (player.playerError != null) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> btnPlayPause?.setImageResource(R.drawable.ic_pause)
                    Player.STATE_READY -> btnPlayPause?.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                    Player.STATE_ENDED -> btnPlayPause?.setImageResource(R.drawable.ic_play)
                    else -> {}
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                val instance = activeInstances[instanceId] ?: return
                if (instance.player !== player) return

                val event = instance.currentEvent
                if (preferencesManager.isAutoSwitchStreamEnabled() && event != null && event.links.size > 1) {
                    val nextIndex = instance.currentLinkIndex + 1
                    if (nextIndex in event.links.indices) {
                        serviceScope.launch {
                            updateInstanceStream(instanceId, null, event, nextIndex)
                        }
                        return
                    }
                }

                btnPlayPause?.setImageResource(R.drawable.ic_error_outline)
            }
        })
    }

    private fun setupResizeFunctionality(
        floatingView: View,
        btnResize: ImageButton?,
        params: WindowManager.LayoutParams,
        instanceId: String
    ) {
        var resizeInitialWidth = 0
        var resizeInitialHeight = 0
        var resizeInitialTouchX = 0f
        var resizeInitialTouchY = 0f

        btnResize?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeInitialWidth = params.width
                    resizeInitialHeight = params.height
                    resizeInitialTouchX = event.rawX
                    resizeInitialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - resizeInitialTouchX
                    val dy = event.rawY - resizeInitialTouchY
                    val delta = ((dx + dy) / 2).toInt()
                    var newWidth = resizeInitialWidth + delta
                    newWidth = newWidth.coerceIn(getMinWidth(), getMaxWidth())
                    val newHeight = newWidth * 9 / 16

                    if (newWidth != params.width || newHeight != params.height) {
                        params.width = newWidth
                        params.height = newHeight
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    preferencesManager.setFloatingPlayerWidth(params.width)
                    preferencesManager.setFloatingPlayerHeight(params.height)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDragFunctionality(
        floatingView: View,
        params: WindowManager.LayoutParams,
        playerView: PlayerView,
        lockOverlay: View?,
        unlockButton: ImageButton?,
        instanceId: String
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var hasMoved = false

        playerView.setOnTouchListener { _, event ->
            val instance = activeInstances[instanceId] ?: return@setOnTouchListener false

            if (instance.controlsLocked) {
                params?.let { p ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = p.x
                            initialY = p.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            hasMoved = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            if (abs(dx) > 10 || abs(dy) > 10) {
                                isDragging = true
                                hasMoved = true
                                val minVisible = p.width / 4
                                val screenW = getScreenWidth()
                                val screenH = getScreenHeight() - getNavBarHeight()
                                p.x = (initialX + dx).coerceIn(-(p.width - minVisible), screenW - minVisible)
                                p.y = (initialY + dy).coerceIn(-(p.height - minVisible), screenH - minVisible)
                                windowManager?.updateViewLayout(floatingView, p)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (!hasMoved) {
                                if (unlockButton?.visibility == View.VISIBLE) {
                                    hideUnlockButton(instanceId)
                                } else {
                                    showUnlockButton(instanceId)
                                }
                            } else {
                                preferencesManager.setFloatingPlayerX(p.x)
                                preferencesManager.setFloatingPlayerY(p.y)
                                preferencesManager.setFloatingPlayerXRatio(p.x.toFloat() / getScreenWidth())
                                preferencesManager.setFloatingPlayerYRatio(p.y.toFloat() / (getScreenHeight() - getNavBarHeight()))
                            }
                            isDragging = false
                            hasMoved = false
                            true
                        }
                        else -> false
                    }
                } ?: false
            } else {
                params?.let { p ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = p.x
                            initialY = p.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            hasMoved = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            if (abs(dx) > 10 || abs(dy) > 10) {
                                isDragging = true
                                hasMoved = true
                                val minVisible = p.width / 4
                                val screenW = getScreenWidth()
                                val screenH = getScreenHeight() - getNavBarHeight()
                                p.x = (initialX + dx).coerceIn(-(p.width - minVisible), screenW - minVisible)
                                p.y = (initialY + dy).coerceIn(-(p.height - minVisible), screenH - minVisible)
                                windowManager?.updateViewLayout(floatingView, p)
                            }
                            isDragging
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (!hasMoved) {
                                val currentlyVisible = playerView.isControllerFullyVisible == true
                                if (currentlyVisible) {
                                    playerView.hideController()
                                } else {
                                    playerView.showController()
                                }
                            } else {
                                preferencesManager.setFloatingPlayerX(p.x)
                                preferencesManager.setFloatingPlayerY(p.y)
                                preferencesManager.setFloatingPlayerXRatio(p.x.toFloat() / getScreenWidth())
                                preferencesManager.setFloatingPlayerYRatio(p.y.toFloat() / (getScreenHeight() - getNavBarHeight()))
                            }
                            val wasMoving = hasMoved
                            isDragging = false
                            hasMoved = false
                            wasMoving
                        }
                        else -> false
                    }
                } ?: false
            }
        }

        lockOverlay?.apply {
            isClickable = false
            isFocusable = false
        }
    }

    private fun toggleLock(instanceId: String) {
        val instance = activeInstances[instanceId] ?: return
        val lockBtn = instance.playerView.findViewById<ImageButton>(R.id.btn_lock)

        if (instance.controlsLocked) {
            instance.controlsLocked = false
            instance.lockOverlay?.visibility = View.GONE
            hideUnlockButton(instanceId)
            lockBtn?.setImageResource(R.drawable.ic_lock_open)
            instance.playerView.showController()
        } else {
            instance.controlsLocked = true
            lockBtn?.setImageResource(R.drawable.ic_lock_closed)
            instance.playerView.hideController()
            instance.lockOverlay?.apply {
                visibility = View.VISIBLE
                isClickable = false
                isFocusable = false
            }
        }
    }

    private fun showUnlockButton(instanceId: String) {
        val instance = activeInstances[instanceId] ?: return
        val handler = hideControlsHandlers[instanceId] ?: return
        instance.unlockButton?.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ instance.unlockButton?.visibility = View.GONE }, 3000)
    }

    private fun hideUnlockButton(instanceId: String) {
        val instance = activeInstances[instanceId] ?: return
        val handler = hideControlsHandlers[instanceId] ?: return
        handler.removeCallbacksAndMessages(null)
        instance.unlockButton?.visibility = View.GONE
    }

    private fun retryInstance(instanceId: String) {
        val instance = activeInstances[instanceId] ?: return
        try {
            if (instance.isNetworkStream) {
                val streamUrl = instance.networkStreamUrl ?: return
                val cookie = instance.networkCookie ?: ""
                val referer = instance.networkReferer ?: ""
                val origin = instance.networkOrigin ?: ""
                val drmLicense = instance.networkDrmLicense ?: ""
                val userAgent = instance.networkUserAgent ?: "Default"
                val drmScheme = instance.networkDrmScheme ?: "clearkey"
                val xForwardedFor = instance.networkXForwardedFor ?: ""
                val customHeaders = instance.networkCustomHeaders ?: ""

                val headers = mutableMapOf<String, String>()
                if (cookie.isNotEmpty()) headers["Cookie"] = cookie
                if (referer.isNotEmpty()) headers["Referer"] = referer
                if (origin.isNotEmpty()) headers["Origin"] = origin
                if (xForwardedFor.isNotEmpty()) headers["X-Forwarded-For"] = xForwardedFor
                if (customHeaders.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(customHeaders)
                        json.keys().asSequence().forEach { headers[it] = json.getString(it) }
                    } catch (_: Exception) {}
                }
                val effectiveUserAgent = if (userAgent.isNotEmpty() && userAgent != "Default")
                    userAgent else "okhttp/4.12.0"
                headers["User-Agent"] = effectiveUserAgent

                val nsStreamInfo = resolveNetworkStreamInfo(streamUrl, headers, drmScheme, drmLicense)
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(effectiveUserAgent)
                    .setDefaultRequestProperties(headers)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(true)
                val mediaSourceFactory = buildDrmMediaSourceFactory(nsStreamInfo, dataSourceFactory, headers)

                instance.player.stop()
                com.livetvpro.app.utils.VolumeBoostHelper.release(instance.player)
                instance.player.release()

                val renderersFactory3 = DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true)
                val trackSelector3 = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
                    parameters = buildUponParameters()
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowAudioMixedMimeTypeAdaptiveness(true)
                        .setAllowAudioMixedChannelCountAdaptiveness(true)
                        .build()
                }
                val newPlayer = ExoPlayer.Builder(this)
                    .setRenderersFactory(renderersFactory3)
                    .setTrackSelector(trackSelector3)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .setHandleAudioBecomingNoisy(false)
                    .setAudioAttributes(
                        androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false
                    )
                    .build()
                com.livetvpro.app.utils.VolumeBoostHelper.attach(newPlayer, preferencesManager)
                com.livetvpro.app.utils.VolumeBoostHelper.setBoostLevel(newPlayer, if (instance.isBoosted) 100 else 0)

                instance.playerView.player = newPlayer
                newPlayer.setMediaItem(buildDrmMediaItem(nsStreamInfo, headers))

                activeInstances[instanceId] = instance.copy(player = newPlayer)
                val btnPlayPause2 = instance.playerView.findViewById<ImageButton>(R.id.btn_play_pause)
                attachPlayerListener(newPlayer, btnPlayPause2, instanceId)

                newPlayer.prepare()
                newPlayer.playWhenReady = true
            } else {
                updateInstanceStream(instanceId, instance.currentChannel, instance.currentEvent, 0)
            }
        } catch (e: Throwable) {
        }
    }

    private fun stopInstance(instanceId: String) {
        val instance = activeInstances[instanceId] ?: return

        hideControlsHandlers[instanceId]?.removeCallbacksAndMessages(null)
        hideControlsHandlers.remove(instanceId)

        com.livetvpro.app.utils.VolumeBoostHelper.release(instance.player)
        instance.player.release()

        try {
            windowManager?.removeView(instance.floatingView)
        } catch (e: Exception) { }

        activeInstances.remove(instanceId)
        com.livetvpro.app.utils.FloatingPlayerManager.removePlayer(instanceId)

        if (activeInstances.isEmpty()) {
            preferencesManager.setFloatingPlayerX(Int.MIN_VALUE)
            preferencesManager.setFloatingPlayerY(Int.MIN_VALUE)
        }

        updateNotification()

        if (activeInstances.isEmpty()) stopSelf()
    }

    private fun stopAllInstances() {
        val instanceIds = activeInstances.keys.toList()
        instanceIds.forEach { stopInstance(it) }
    }

    private fun updateNotification() {
        if (activeInstances.isEmpty()) return

        val count = activeInstances.size
        val title = if (count == 1) {
            val instance = activeInstances.values.first()
            instance.currentChannel?.name ?: instance.currentEvent?.title ?: "Unknown"
        } else {
            "$count Floating Players Active"
        }

        val notification = createNotification(title)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating player service notification"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String): Notification {
        val stopIntent = Intent(this, FloatingPlayerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Player")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_play)
            .addAction(R.drawable.ic_close, "Stop All", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.width() ?: 1080
        } else {
            val display = windowManager?.defaultDisplay
            val size = Point()
            display?.getSize(size)
            size.x
        }
    }

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.height() ?: 1920
        } else {
            val display = windowManager?.defaultDisplay
            val size = Point()
            display?.getSize(size)
            size.y
        }
    }

    private fun getNavBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val newScreenW = getScreenWidth()
        val newScreenH = getScreenHeight() - getNavBarHeight()
        val ratioX = preferencesManager.getFloatingPlayerXRatio()
        val ratioY = preferencesManager.getFloatingPlayerYRatio()
        if (ratioX < 0f || ratioY < 0f) return
        activeInstances.values.forEach { instance ->
            val p = instance.params
            val minVisible = p.width / 4
            p.x = (ratioX * newScreenW).toInt().coerceIn(-(p.width - minVisible), newScreenW - minVisible)
            p.y = (ratioY * newScreenH).toInt().coerceIn(-(p.height - minVisible), newScreenH - minVisible)
            try { windowManager?.updateViewLayout(instance.floatingView, p) } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopAllInstances()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class StreamInfo(
        val url: String,
        val headers: Map<String, String>,
        val drmScheme: String?,
        val drmKeyId: String?,
        val drmKey: String?,
        val drmLicenseUrl: String?,
        val customHeaders: Map<String, String> = emptyMap(),
        val drmJwk: String? = null,
    )

    private fun buildStreamInfoFromLink(
        url: String,
        cookie: String?,
        referer: String?,
        origin: String?,
        userAgent: String?,
        drmScheme: String?,
        drmLicenseUrl: String?,
        xForwardedFor: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
        drmJwk: String? = null,
    ): StreamInfo {
        val base = parseStreamUrl(url)

        val headers = base.headers.toMutableMap()
        val custom = base.customHeaders.toMutableMap()

        referer?.takeIf { it.isNotEmpty() }?.let { headers["Referer"] = it }
        cookie?.takeIf { it.isNotEmpty() }?.let { headers["Cookie"] = it }
        origin?.takeIf { it.isNotEmpty() }?.let { headers["Origin"] = it }
        userAgent?.takeIf { it.isNotEmpty() }?.let { headers["User-Agent"] = it }
        xForwardedFor?.takeIf { it.isNotEmpty() }?.let { headers["X-Forwarded-For"] = it }
        customHeaders.forEach { (k, v) ->
            if (v.isNotEmpty()) {
                headers[k] = v
                custom[k] = v
            }
        }

        val resolvedDrmScheme = drmScheme?.takeIf { it.isNotEmpty() }
            ?.let { normalizeDrmScheme(it) } ?: base.drmScheme

        var resolvedDrmLicenseUrl = base.drmLicenseUrl
        var resolvedDrmKeyId = base.drmKeyId
        var resolvedDrmKey = base.drmKey

        drmLicenseUrl?.takeIf { it.isNotEmpty() }?.let { value ->
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
            customHeaders = custom,
            drmJwk = drmJwk ?: base.drmJwk,
        )
    }

    private fun parseStreamUrl(streamUrl: String): StreamInfo {
        val normalizedUrl = streamUrl.replace("%7c", "|", ignoreCase = true)
        val pipeIndex = normalizedUrl.indexOf('|')
        if (pipeIndex == -1) return StreamInfo(normalizedUrl, mapOf(), null, null, null, null)

        val url = normalizedUrl.substring(0, pipeIndex).trim().trimEnd('?')
        val parts = buildList {
            for (segment in normalizedUrl.substring(pipeIndex + 1).split("|")) {
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
        var drmJwk: String? = null

        for (part in parts) {
            val eqIndex = part.indexOf('=')
            if (eqIndex == -1) continue
            val key = part.substring(0, eqIndex).trim()
            val value = part.substring(eqIndex + 1).trim()
            when (key.lowercase()) {
                "drmscheme" -> drmScheme = normalizeDrmScheme(value)
                "drmlicense" -> when {
                    value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true) -> drmLicenseUrl = value
                    value.trimStart().startsWith("{") -> drmLicenseUrl = value
                    else -> {
                        val colonIndex = value.indexOf(':')
                        if (colonIndex != -1) {
                            drmKeyId = value.substring(0, colonIndex).trim()
                            drmKey = value.substring(colonIndex + 1).trim()
                        }
                    }
                }
                "drmjwk" -> {
                    try {
                        drmJwk = String(
                            android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
                            Charsets.UTF_8
                        )
                    } catch (_: Exception) { drmJwk = value }
                }
                "drmkeyid" -> drmKeyId = value
                "drmkey" -> drmKey = value
                "user-agent", "useragent" -> headers["User-Agent"] = value
                "referer", "referrer" -> headers["Referer"] = value
                "cookie" -> headers["Cookie"] = value
                "origin" -> headers["Origin"] = value
                "x-forwarded-for" -> headers["X-Forwarded-For"] = value
                else -> customHeaders[key] = value
            }
        }
        return StreamInfo(url, headers + customHeaders, drmScheme, drmKeyId, drmKey, drmLicenseUrl, customHeaders, drmJwk)
    }

    private fun buildStreamInfoFromDrmFields(
        url: String,
        headers: Map<String, String>,
        drmScheme: String,
        drmLicense: String
    ): StreamInfo {
        val scheme = normalizeDrmScheme(drmScheme).takeIf { it.isNotEmpty() }
        return when {
            drmLicense.trimStart().startsWith("{") ->
                StreamInfo(url, headers, scheme, null, null, drmLicense)
            drmLicense.startsWith("http://", ignoreCase = true) ||
            drmLicense.startsWith("https://", ignoreCase = true) ->
                StreamInfo(url, headers, scheme, null, null, drmLicense)
            drmLicense.contains(':') -> {
                val i = drmLicense.indexOf(':')
                StreamInfo(url, headers, scheme,
                    drmLicense.substring(0, i).trim(),
                    drmLicense.substring(i + 1).trim(), null)
            }
            else -> StreamInfo(url, headers, scheme, null, null, null)
        }
    }

    private fun resolveNetworkStreamInfo(
        streamUrl: String,
        headers: Map<String, String>,
        drmScheme: String,
        drmLicense: String
    ): StreamInfo {
        val parsed = parseStreamUrl(streamUrl)
        val mergedHeaders = headers + parsed.headers
        val resolvedScheme = parsed.drmScheme?.takeIf { it.isNotEmpty() }
            ?: normalizeDrmScheme(drmScheme).takeIf { it.isNotEmpty() }
        val explicitInfo = buildStreamInfoFromDrmFields(parsed.url, mergedHeaders, drmScheme, drmLicense)
        return StreamInfo(
            url = parsed.url,
            headers = mergedHeaders,
            drmScheme = resolvedScheme,
            drmKeyId = parsed.drmKeyId ?: explicitInfo.drmKeyId,
            drmKey = parsed.drmKey ?: explicitInfo.drmKey,
            drmLicenseUrl = parsed.drmLicenseUrl ?: explicitInfo.drmLicenseUrl,
            customHeaders = parsed.customHeaders,
            drmJwk = parsed.drmJwk,
        )
    }

    private fun normalizeDrmScheme(scheme: String): String {
        val lower = scheme.lowercase()
        return when {
            lower.contains("clearkey") || lower == "org.w3.clearkey" -> "clearkey"
            lower.contains("widevine") || lower == "com.widevine.alpha" -> "widevine"
            lower.contains("playready") || lower == "com.microsoft.playready" -> "playready"
            else -> lower
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return try {
            val clean = hex.replace(" ", "").replace("-", "").lowercase()
            if (clean.length % 2 != 0) return ByteArray(0)
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { ByteArray(0) }
    }

    private fun buildClearKeyInlineManager(keyIdHex: String, keyHex: String): DefaultDrmSessionManager? {
        return try {
            val keyIdBytes = hexToBytes(keyIdHex)
            val keyBytes   = hexToBytes(keyHex)
            if (keyIdBytes.isEmpty() || keyBytes.isEmpty()) return null
            val keyBase64 = android.util.Base64.encodeToString(keyBytes,   android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val kidBase64 = android.util.Base64.encodeToString(keyIdBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val jwk = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(LocalMediaDrmCallback(jwk.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) { null }
    }

    private fun buildClearKeyJwkManager(jwkJson: String): DefaultDrmSessionManager? {
        return try {
            val isMultiKey = try {
                val arr = org.json.JSONObject(jwkJson).optJSONArray("keys")
                arr != null && arr.length() > 1
            } catch (_: Exception) { false }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(isMultiKey)
                .setPlayClearSamplesWithoutKeys(true)
                .build(LocalMediaDrmCallback(jwkJson.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) { null }
    }

    private fun buildClearKeyServerManager(licenseUrl: String, headers: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true).setKeepPostFor302Redirects(true)
            val cb = HttpMediaDrmCallback(licenseUrl, factory)
            headers.forEach { (k, v) -> cb.setKeyRequestProperty(k, v) }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(cb)
        } catch (e: Exception) { null }
    }

    private fun buildWidevineOrPlayReadyManager(
        scheme: String,
        licenseUrl: String,
        headers: Map<String, String>
    ): DefaultDrmSessionManager? {
        return try {
            val uuid = if (scheme == "widevine") C.WIDEVINE_UUID else C.PLAYREADY_UUID
            val licenseHeaders = headers.filter { (k, _) ->
                k.lowercase() !in setOf("referer", "origin")
            }
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(licenseHeaders["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(licenseHeaders)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val cb = HttpMediaDrmCallback(licenseUrl, factory)
            licenseHeaders.forEach { (k, v) -> cb.setKeyRequestProperty(k, v) }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .build(cb)
        } catch (e: Exception) { null }
    }

    private fun buildDrmMediaSourceFactory(
        streamInfo: StreamInfo,
        dataSourceFactory: DefaultHttpDataSource.Factory,
        headers: Map<String, String>
    ): DefaultMediaSourceFactory {
        val drmMgr = when {
            streamInfo.drmScheme == "clearkey" -> when {
                streamInfo.drmJwk != null ->
                    buildClearKeyJwkManager(streamInfo.drmJwk)
                streamInfo.drmKeyId != null && streamInfo.drmKey != null ->
                    buildClearKeyInlineManager(streamInfo.drmKeyId, streamInfo.drmKey)
                streamInfo.drmLicenseUrl?.trimStart()?.startsWith("{") == true ->
                    buildClearKeyJwkManager(streamInfo.drmLicenseUrl)
                streamInfo.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true ->
                    buildClearKeyServerManager(streamInfo.drmLicenseUrl, headers)
                else -> null
            }
            (streamInfo.drmScheme == "widevine" || streamInfo.drmScheme == "playready") &&
                    streamInfo.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true ->
                buildWidevineOrPlayReadyManager(streamInfo.drmScheme, streamInfo.drmLicenseUrl, headers)
            else -> null
        }
        return if (drmMgr != null) {
            val effectiveDataSourceFactory = if (streamInfo.drmScheme == "clearkey" && streamInfo.drmKeyId != null) {
                val keyId = streamInfo.drmKeyId
                androidx.media3.datasource.DataSource.Factory {
                    ClearKeyManifestRewritingDataSource(dataSourceFactory.createDataSource(), keyId)
                }
            } else {
                dataSourceFactory
            }
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(effectiveDataSourceFactory)
                .setDrmSessionManagerProvider { drmMgr }
        } else {
            DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
        }
    }

    private fun buildDrmMediaItem(streamInfo: StreamInfo, headers: Map<String, String>): MediaItem {
        val builder = MediaItem.Builder().setUri(streamInfo.url)
        val mimeType: String? = PlayerStreamHelper.detectMimeTypeFromUrl(streamInfo.url)
            ?: try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    PlayerStreamHelper.resolveContentType(streamInfo.url, headers)
                }
            } catch (e: Exception) { null }
        mimeType?.let { builder.setMimeType(it) }
        if (streamInfo.drmScheme == "widevine" || streamInfo.drmScheme == "playready") {
            streamInfo.drmLicenseUrl?.let { licUrl ->
                val uuid = if (streamInfo.drmScheme == "widevine") C.WIDEVINE_UUID else C.PLAYREADY_UUID
                val licenseHeaders = headers.filter { (k, _) ->
                    k.lowercase() !in setOf("referer", "origin")
                }
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(licUrl)
                        .setLicenseRequestHeaders(licenseHeaders)
                        .setForceDefaultLicenseUri(true)
                        .setMultiSession(false)
                        .build()
                )
            }
        }
        return builder.build()
    }
}
