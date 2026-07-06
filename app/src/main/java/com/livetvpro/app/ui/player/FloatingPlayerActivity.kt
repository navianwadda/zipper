package com.livetvpro.app.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.LiveEventLink
import com.livetvpro.app.ui.player.compose.*
import com.livetvpro.app.ui.player.settings.PlayerSettingsDialog
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class FloatingPlayerActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var listenerManager: com.livetvpro.app.utils.NativeListenerManager

    private val viewModel: PlayerViewModel by viewModels()

    private val relatedContentState = mutableStateOf<RelatedContentState>(RelatedContentState.Hidden)
    private val linksState          = mutableStateOf<List<LiveEventLink>>(emptyList())
    private val selectedLinkState   = mutableStateOf(0)
    private val messageBannerText   = mutableStateOf("")
    private val messageBannerUrl    = mutableStateOf("")
    private val isLandscapeState    = mutableStateOf(false)
    private val errorMessage        = mutableStateOf("")
    private val showSettingsDialogState = mutableStateOf(false)

    private val controlsState = PlayerControlsState(initialVisible = false)
    private var gestureVolume: Int = 100
    private var gestureBrightness: Int = 0

    private var isInPipMode = false
    private var isMuted by mutableStateOf(false)
    private val skipMs = 10_000L
    private var userRequestedPip = false

    private var pipReceiver: BroadcastReceiver? = null
    private var wasLockedBeforePip = false
    private var isShowingSettingsDialog = false

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private var contentType: ContentType = ContentType.CHANNEL
    private var channelData: Channel? = null
    private var eventData: LiveEvent? = null
    private var allEventLinks = listOf<LiveEventLink>()
    private var currentLinkIndex = 0
    private var contentId: String = ""
    private var contentName: String by mutableStateOf("")
    private var streamUrl: String = ""
    private var intentCategoryId: String? = null
    private var intentSelectedGroup: String? = null
    private var intentIsSports: Boolean = false

    private var player: ExoPlayer? by mutableStateOf(null)
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    private var resizeMode by mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var networkPortraitResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    private var resizeModesRestoredFromState = false

    private var savedPlaybackPosition: Long = -1L
    private var relatedChannels = listOf<Channel>()

    private var relatedChannelsLockedForContentId: String? = null

    enum class ContentType {
        CHANNEL, EVENT, NETWORK_STREAM
    }

    companion object {
        private const val EXTRA_CHANNEL = "extra_channel"
        private const val EXTRA_EVENT = "extra_event"
        private const val EXTRA_SELECTED_LINK_INDEX = "extra_selected_link_index"
        private const val ACTION_MEDIA_CONTROL = "com.livetvpro.app.MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
        private const val CONTROL_TYPE_REWIND = 3
        private const val CONTROL_TYPE_FORWARD = 4
        private const val EXTRA_CATEGORY_ID = "extra_category_id"
        private const val EXTRA_SELECTED_GROUP = "extra_selected_group"
        private const val EXTRA_IS_SPORTS = "extra_is_sports"
        private const val EXTRA_CHANNEL_LIST_KEY = "extra_channel_list_key"

        fun startWithChannel(context: Context, channel: Channel, linkIndex: Int = -1, categoryId: String? = null, selectedGroup: String? = null, isSports: Boolean = false, channelList: ArrayList<Channel>? = null, channelListCacheKey: String? = null) {
            val intent = Intent(context, FloatingPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, channel as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) }
                selectedGroup?.let { putExtra(EXTRA_SELECTED_GROUP, it) }
                putExtra(EXTRA_IS_SPORTS, isSports)
                val key = channelListCacheKey ?: channelList?.let { list ->
                    val k = categoryId ?: channel.categoryId.takeIf { it.isNotEmpty() } ?: channel.id
                    ChannelListCache.put(k, list)
                    k
                }
                key?.let { putExtra(EXTRA_CHANNEL_LIST_KEY, it) }
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(0, 0)
            }
        }

        fun startWithEvent(context: Context, event: LiveEvent, linkIndex: Int = -1) {
            val intent = Intent(context, FloatingPlayerActivity::class.java).apply {
                putExtra(EXTRA_EVENT, event as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(0, 0)
            }
        }

        fun startWithNetworkStream(
            context: Context,
            streamUrl: String,
            cookie: String = "",
            referer: String = "",
            origin: String = "",
            drmLicense: String = "",
            userAgent: String = "",
            drmScheme: String = "clearkey",
            streamName: String = "Network Stream",
            xForwardedFor: String = "",
            playbackPosition: Long = -1L
        ) {
            val intent = Intent(context, FloatingPlayerActivity::class.java).apply {
                putExtra("IS_NETWORK_STREAM", true)

                putExtra("STREAM_URL", streamUrl)
                putExtra("COOKIE", cookie)
                putExtra("REFERER", referer)
                putExtra("ORIGIN", origin)
                putExtra("DRM_LICENSE", drmLicense)
                putExtra("USER_AGENT", userAgent)
                putExtra("DRM_SCHEME", drmScheme)
                putExtra("X_FORWARDED_FOR", xForwardedFor)

                putExtra("CHANNEL_NAME", streamName)

                if (playbackPosition > 0) {
                    putExtra("playback_position", playbackPosition)
                }

                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(0, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager.registerActivityContext(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (DeviceUtils.isTvDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        val isLandscape = DeviceUtils.isTvDevice ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        parseIntent()
        if (savedInstanceState != null && contentId.isEmpty()) {
            restoreFromBundle(savedInstanceState)
        }
        setupWindowFlags(isLandscape)
        setupSystemUI()

        if (contentType == ContentType.CHANNEL && contentId.isNotEmpty()) {
            viewModel.refreshChannelData(contentId)
            val cacheKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val cachedList = cacheKey?.let { ChannelListCache.get(it) }
            if (!cachedList.isNullOrEmpty()) {
                viewModel.setChannelList(cachedList)
            } else {
                viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
            }
        }

        applyOrientationSettings(isLandscape)

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        gestureVolume = if (maxVol > 0) (curVol * 100f / maxVol).toInt() else 100

        setupRelatedChannels()
        setupLinksUI()
        setupMessageBanner()

        setupPlayer()
        loadRelatedContent()

        viewModel.refreshedChannel.observe(this) { freshChannel ->
            if (freshChannel != null && freshChannel.links != null && freshChannel.links.isNotEmpty()) {
                if (allEventLinks.isEmpty() || allEventLinks.size < freshChannel.links.size) {
                    allEventLinks = freshChannel.links.map {
                        LiveEventLink(
                            quality = it.quality,
                            url = it.url,
                            cookie = it.cookie,
                            referer = it.referer,
                            origin = it.origin,
                            userAgent = it.userAgent,
                            xForwardedFor = it.xForwardedFor,
                            drmScheme = it.drmScheme,
                            drmLicenseUrl = it.drmLicenseUrl
                        )
                    }

                    val matchIndex = allEventLinks.indexOfFirst { it.url == streamUrl }
                    if (matchIndex != -1) {
                        currentLinkIndex = matchIndex
                    }

                    val isLandscapeNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    updateLinksForOrientation(isLandscapeNow)
                }
            }

            if (freshChannel != null) {
                channelData = freshChannel
            }
        }

        viewModel.relatedItems.observe(this) { channels ->
            if (contentType != ContentType.CHANNEL) return@observe
            if (relatedChannelsLockedForContentId == contentId) return@observe
            relatedChannels = channels
            relatedContentState.value = if (channels.isEmpty()) RelatedContentState.Hidden
            else RelatedContentState.Channels(channels)
        }

        viewModel.relatedLiveEvents.observe(this) { liveEvents ->
            if (contentType != ContentType.EVENT) return@observe
            relatedContentState.value = if (liveEvents.isEmpty()) RelatedContentState.Hidden
            else RelatedContentState.Events(liveEvents)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerPipReceiver()
        }

        setContent {
            CompositionLocalProvider(LocalActivity provides null) {
                LiveTVProTheme(
                    themeManager = themeManager,
                    surfaceColor = Color.Transparent,
                ) {
                    FloatingPlayerActivityRoot()
                }
            }
        }
    }

    @Composable
    private fun FloatingPlayerActivityRoot() {
        val isLandscape = DeviceUtils.isTvDevice || isLandscapeState.value

        val isPlaying by produceState(initialValue = false, player) {
            while (true) { value = player?.isPlaying == true; delay(100) }
        }
        val isBuffering by produceState(initialValue = false, player) {
            while (true) { value = player?.playbackState == Player.STATE_BUFFERING; delay(100) }
        }
        var currentPosition  by remember { mutableLongStateOf(0L) }
        var duration         by remember { mutableLongStateOf(0L) }
        var bufferedPosition by remember { mutableLongStateOf(0L) }
        LaunchedEffect(player) {
            while (true) {
                currentPosition  = player?.currentPosition ?: 0L
                bufferedPosition = player?.bufferedPosition ?: 0L
                duration         = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
                delay(500L)
            }
        }

        var showChannelList by remember { mutableStateOf(false) }
        val channelListItems by viewModel.channelListItems.observeAsState(emptyList())
        val isChannelListAvailable = contentType == ContentType.CHANNEL &&
            channelListItems.isNotEmpty() && (isLandscape || DeviceUtils.isTvDevice)

        val isPipEnabled = !DeviceUtils.isTvDevice &&
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isPipSupported() else false

        val onPlayPause: () -> Unit = {
            player?.let {
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = it.playbackState == Player.STATE_ENDED
                if (hasError || hasEnded) retryPlayback()
                else if (it.isPlaying) it.pause() else it.play()
            }
        }
        val onRewind: () -> Unit = {
            player?.let { it.seekTo((it.currentPosition - skipMs).coerceAtLeast(0L)) }
        }
        val onForward: () -> Unit = {
            player?.let {
                val newPos = it.currentPosition + skipMs
                if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPos >= it.duration) it.seekTo(it.duration)
                else it.seekTo(newPos)
            }
        }
        val onPrev: () -> Unit = {
            when (contentType) {
                ContentType.EVENT -> {
                    if (allEventLinks.size > 1) {
                        val prev = (currentLinkIndex - 1).coerceAtLeast(0)
                        if (prev != currentLinkIndex) switchToLink(allEventLinks[prev], prev)
                    }
                }
                ContentType.CHANNEL -> {
                    val items = viewModel.channelListItems.value
                    if (!items.isNullOrEmpty()) {
                        val idx = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val prev = (idx - 1).coerceAtLeast(0)
                        if (prev != idx) switchToChannel(items[prev])
                    }
                }
                else -> {}
            }
        }
        val onNext: () -> Unit = {
            when (contentType) {
                ContentType.EVENT -> {
                    if (allEventLinks.size > 1) {
                        val next = (currentLinkIndex + 1).coerceAtMost(allEventLinks.size - 1)
                        if (next != currentLinkIndex) switchToLink(allEventLinks[next], next)
                    }
                }
                ContentType.CHANNEL -> {
                    val items = viewModel.channelListItems.value
                    if (!items.isNullOrEmpty()) {
                        val idx = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val next = (idx + 1).coerceAtMost(items.size - 1)
                        if (next != idx) switchToChannel(items[next])
                    }
                }
                else -> {}
            }
        }
        val onVolumeSwipe: (Int) -> Unit = { vol ->
            gestureVolume = vol
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (vol / 100f * max).toInt(), 0)
        }
        val onBrightnessSwipe: (Int) -> Unit = { bri ->
            gestureBrightness = bri
            val lp = window.attributes
            lp.screenBrightness = if (bri == 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else bri / 100f
            window.attributes = lp
        }

        @Composable
        fun Controls(landscapeMode: Boolean) {
            PlayerControls(
                state                  = controlsState,
                isPlaying              = isPlaying,
                isMuted                = isMuted,
                currentPosition        = currentPosition,
                duration               = duration,
                bufferedPosition       = bufferedPosition,
                channelName            = contentName,
                showPipButton          = isPipEnabled,
                showAspectRatioButton  = true,
                isLandscape            = landscapeMode,
                isTvMode               = DeviceUtils.isTvDevice,
                centerControlsMode     = preferencesManager.getCenterControlsMode(),
                isNetworkStream        = contentType == ContentType.NETWORK_STREAM,
                isChannelListAvailable = isChannelListAvailable,
                onBackClick            = { finish() },
                onPipClick             = {
                    val currentChannel = channelData
                    val currentEvent = eventData
                    val currentPlayer = player
                    val currentStreamUrl = streamUrl
                    val currentName = contentName
                    val sourceInstanceId = intent.getStringExtra("source_instance_id")
                        ?: "fpa_${contentId.ifEmpty { currentStreamUrl.hashCode().toString() }}"

                    if (currentPlayer != null && contentType == ContentType.NETWORK_STREAM) {
                        PlayerHolder.transferPlayer(currentPlayer, currentStreamUrl, currentName)
                        val link = allEventLinks.getOrNull(currentLinkIndex)
                        val serviceIntent = Intent(this@FloatingPlayerActivity, FloatingPlayerService::class.java).apply {
                            putExtra("IS_NETWORK_STREAM", true)
                            putExtra("use_transferred_player", true)
                            putExtra("STREAM_URL", link?.url ?: currentStreamUrl)
                            putExtra("COOKIE", link?.cookie ?: "")
                            putExtra("REFERER", link?.referer ?: "")
                            putExtra("ORIGIN", link?.origin ?: "")
                            putExtra("DRM_LICENSE", link?.drmLicenseUrl ?: "")
                            putExtra("USER_AGENT", link?.userAgent ?: "")
                            putExtra("DRM_SCHEME", link?.drmScheme ?: "clearkey")
                            putExtra("CHANNEL_NAME", currentName)
                            putExtra(FloatingPlayerService.EXTRA_RESTORE_POSITION, true)
                            if (sourceInstanceId != null) putExtra(FloatingPlayerService.EXTRA_INSTANCE_ID, sourceInstanceId)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                        player = null
                        finish()
                    } else if (currentPlayer != null && (currentChannel != null || currentEvent != null)) {
                        PlayerHolder.transferPlayer(currentPlayer, currentStreamUrl, currentName)
                        val currentChannelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
                        val serviceIntent = Intent(this@FloatingPlayerActivity, FloatingPlayerService::class.java).apply {
                            if (currentChannel != null) putExtra(FloatingPlayerService.EXTRA_CHANNEL, currentChannel)
                            if (currentEvent != null) putExtra(FloatingPlayerService.EXTRA_EVENT, currentEvent)
                            putExtra(FloatingPlayerService.EXTRA_RESTORE_POSITION, true)
                            putExtra("use_transferred_player", true)
                            if (sourceInstanceId != null) putExtra(FloatingPlayerService.EXTRA_INSTANCE_ID, sourceInstanceId)
                            if (currentChannelListKey != null) putExtra(FloatingPlayerService.EXTRA_CHANNEL_LIST_KEY, currentChannelListKey)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                        player = null
                        finish()
                    }
                },
                onSettingsClick        = { showSettingsDialog() },
                onMuteClick            = { toggleMute() },
                onLockClick            = { _ -> },
                onChannelListClick     = { showChannelList = true },
                onPlayPauseClick       = onPlayPause,
                onSeek                 = { position -> player?.seekTo(position) },
                onRewindClick          = onRewind,
                onForwardClick         = onForward,
                onPrevClick            = onPrev,
                onNextClick            = onNext,
                onAspectRatioClick     = { cycleAspectRatio() },
                onFullscreenClick      = { toggleFullscreen() },
                onVolumeSwipe          = onVolumeSwipe,
                onBrightnessSwipe      = onBrightnessSwipe,
                initialVolume          = gestureVolume,
                initialBrightness      = gestureBrightness,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
        ) {
            if (!isLandscape) {
                val isNetworkStream = contentType == ContentType.NETWORK_STREAM
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isNetworkStream) Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars)
                            else Modifier.aspectRatio(16f / 9f)
                        ),
                ) {
                    VideoSurface(
                        player     = player,
                        resizeMode = if (isNetworkStream) resizeMode else AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier   = Modifier.fillMaxSize(),
                    )

                    if (errorMessage.value.isNotBlank()) {
                        ErrorOverlay(errorMessage.value)
                    }

                    if (isBuffering && errorMessage.value.isBlank()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                            color    = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }

                    Controls(landscapeMode = false)

                    if (isChannelListAvailable) {
                        ChannelListPanel(
                            visible          = showChannelList,
                            channels         = channelListItems,
                            currentChannelId = contentId,
                            onChannelClick   = { switchToChannel(it) },
                            onDismiss        = { showChannelList = false },
                            modifier         = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (!isNetworkStream) {
                    PlayerScreen(
                        isLandscape          = false,
                        relatedContentState  = relatedContentState.value,
                        links                = linksState.value,
                        selectedLinkIndex    = selectedLinkState.value,
                        messageBanner        = messageBannerText.value,
                        messageBannerUrl     = messageBannerUrl.value,
                        onLinkClick          = { link, idx -> switchToLink(link, idx) },
                        onChannelClick       = { switchToChannel(it) },
                        onEventClick         = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                        onMessageBannerClick = {
                            val url = messageBannerUrl.value
                            if (url.isNotBlank()) {
                                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                                catch (_: Exception) {}
                            }
                        },
                        spanCount      = resources.getInteger(R.integer.grid_column_count),
                        eventSpanCount = resources.getInteger(R.integer.event_span_count),
                        modifier       = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoSurface(
                        player     = player,
                        resizeMode = resizeMode,
                        modifier   = Modifier.fillMaxSize(),
                    )

                    if (errorMessage.value.isNotBlank()) {
                        ErrorOverlay(errorMessage.value)
                    }

                    if (isBuffering && errorMessage.value.isBlank()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                            color    = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }

                    Controls(landscapeMode = true)

                    if (linksState.value.size > 1) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible  = controlsState.isVisible && !controlsState.isLocked,
                            enter    = fadeIn(),
                            exit     = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 44.dp),
                        ) {
                            PlayerScreen(
                                isLandscape          = true,
                                relatedContentState  = RelatedContentState.Hidden,
                                links                = linksState.value,
                                selectedLinkIndex    = selectedLinkState.value,
                                messageBanner        = "",
                                messageBannerUrl     = "",
                                onLinkClick          = { link, idx -> switchToLink(link, idx) },
                                onChannelClick       = { switchToChannel(it) },
                                onEventClick         = { event, idx -> switchToEventFromLiveEvent(event, idx) },
                                onMessageBannerClick = {},
                                spanCount      = resources.getInteger(R.integer.grid_column_count),
                                eventSpanCount = resources.getInteger(R.integer.event_span_count),
                            )
                        }
                    }

                    if (isChannelListAvailable) {
                        ChannelListPanel(
                            visible          = showChannelList,
                            channels         = channelListItems,
                            currentChannelId = contentId,
                            onChannelClick   = { switchToChannel(it) },
                            onDismiss        = { showChannelList = false },
                            modifier         = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (showSettingsDialogState.value && player != null) {
            PlayerSettingsDialog(
                player    = player!!,
                onDismiss = {
                    showSettingsDialogState.value = false
                    isShowingSettingsDialog = false
                    applyRememberedAspectRatioIfEnabled()
                },
            )
        }
    }

    @Composable
    private fun VideoSurface(
        player: ExoPlayer?,
        resizeMode: Int,
        modifier: Modifier = Modifier,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player     = player
                view.resizeMode = resizeMode
            },
            modifier = modifier,
        )
    }

    @Composable
    private fun ErrorOverlay(message: String) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text       = message,
                color      = Color.White,
                fontFamily = FontFamily(Font(R.font.bergen_sans)),
                fontSize   = 15.sp,
                modifier   = Modifier
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }

    private fun setupWindowFlags(isLandscape: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val isNetworkStream = contentType == ContentType.NETWORK_STREAM
        if (!isLandscape && !isInPipMode) {
            window.decorView.setBackgroundColor(
                if (isNetworkStream) android.graphics.Color.BLACK else themeManager.getBackgroundColor(this)
            )
        } else if (isInPipMode) window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val isDark = isNetworkStream || themeManager.isDarkMode(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars     = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupSystemUI() {
        val isLandscape = DeviceUtils.isTvDevice ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        windowInsetsController.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isLandscape) {
                hide(WindowInsetsCompat.Type.systemBars())
            } else {

                show(WindowInsetsCompat.Type.navigationBars())
                show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            return
        }

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI()
        applyOrientationSettings(isLandscape)
        updateLinksForOrientation(isLandscape)
    }

    private fun applyOrientationSettings(isLandscape: Boolean) {
        isLandscapeState.value = isLandscape
        resizeMode = if (isLandscape) networkLandscapeResizeMode else networkPortraitResizeMode
        if (isLandscape) {
            relatedContentState.value = RelatedContentState.Hidden
        } else if (contentType != ContentType.NETWORK_STREAM) {
            if (relatedChannels.isNotEmpty() || contentType == ContentType.EVENT) {
                relatedContentState.value = when (contentType) {
                    ContentType.EVENT   -> viewModel.relatedLiveEvents.value
                        ?.let { if (it.isNotEmpty()) RelatedContentState.Events(it) else RelatedContentState.Loading }
                        ?: RelatedContentState.Loading
                    ContentType.CHANNEL -> RelatedContentState.Channels(relatedChannels)
                    else                -> RelatedContentState.Hidden
                }
            } else {
                relatedContentState.value = RelatedContentState.Loading
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        releasePlayer()
        parseIntent()
        setupWindowFlags(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        setupPlayer()
        setupLinksUI()
        setupRelatedChannels()
        refreshChannelListForCurrentIntent()
        loadRelatedContent()
    }

    private fun refreshChannelListForCurrentIntent() {
        if (contentType != ContentType.CHANNEL || contentId.isEmpty()) return
        val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
        if (channelListKey?.startsWith("favorites_") == true) return
        val cachedList = channelListKey?.let { ChannelListCache.get(it) }
        if (!cachedList.isNullOrEmpty()) {
            viewModel.setChannelList(cachedList)
        } else {
            viewModel.loadAllChannelsForList(
                intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: ""
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)

        if (player == null) {
            setupPlayer()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        isInPipMode = isInPictureInPictureMode

        if (isInPipMode) {
            enterPipUIMode()
        } else {
            exitPipUIMode(newConfig)
        }
    }

    private fun enterPipUIMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        windowInsetsController.apply {
            show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun exitPipUIMode(newConfig: Configuration) {
        userRequestedPip = false

        if (isFinishing) {
            return
        }

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setupWindowFlags(isLandscape)
        setupSystemUI()

        applyOrientationSettings(isLandscape)

        if (wasLockedBeforePip) {
            controlsState.lock()
            wasLockedBeforePip = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    override fun onBackPressed() {
        when {
            isInPipMode && isFinishing -> finish()
            isInPipMode -> return
            else -> finish()
        }
    }

    private fun parseIntent() {
        val isNetworkStream = intent.getBooleanExtra("IS_NETWORK_STREAM", false)

        if (isNetworkStream) {
            contentType = ContentType.NETWORK_STREAM
            contentName = intent.getStringExtra("CHANNEL_NAME") ?: "Network Stream"
            contentId = "network_stream_${System.currentTimeMillis()}"

            val streamUrlRaw = intent.getStringExtra("STREAM_URL") ?: ""

            if (streamUrlRaw.contains("|")) {
                val parsed = PlayerStreamHelper.parseStreamUrl(streamUrlRaw)
                val extraDrmScheme = intent.getStringExtra("DRM_SCHEME")?.takeIf { it.isNotBlank() }
                val extraDrmLicense = intent.getStringExtra("DRM_LICENSE")?.takeIf { it.isNotBlank() }
                val resolvedDrmScheme = parsed.drmScheme ?: extraDrmScheme

                val resolvedDrmLicenseUrl: String?
                val resolvedDrmKeyId: String?
                val resolvedDrmKey: String?
                if (parsed.drmKeyId != null) {
                    resolvedDrmLicenseUrl = null
                    resolvedDrmKeyId = parsed.drmKeyId
                    resolvedDrmKey = parsed.drmKey
                } else if (extraDrmLicense != null) {
                    val colonIdx = extraDrmLicense.indexOf(':')
                    if (extraDrmLicense.startsWith("http", ignoreCase = true)) {
                        resolvedDrmLicenseUrl = extraDrmLicense
                        resolvedDrmKeyId = null
                        resolvedDrmKey = null
                    } else if (colonIdx != -1) {
                        resolvedDrmLicenseUrl = null
                        resolvedDrmKeyId = extraDrmLicense.substring(0, colonIdx).trim()
                        resolvedDrmKey = extraDrmLicense.substring(colonIdx + 1).trim()
                    } else {
                        resolvedDrmLicenseUrl = extraDrmLicense
                        resolvedDrmKeyId = null
                        resolvedDrmKey = null
                    }
                } else {
                    resolvedDrmLicenseUrl = parsed.drmLicenseUrl
                    resolvedDrmKeyId = null
                    resolvedDrmKey = null
                }

                val mergedLink = LiveEventLink(
                    quality = "Network Stream",
                    url = parsed.url,
                    cookie = parsed.headers["Cookie"] ?: "",
                    referer = parsed.headers["Referer"] ?: "",
                    origin = parsed.headers["Origin"] ?: "",
                    userAgent = parsed.headers["User-Agent"] ?: intent.getStringExtra("USER_AGENT") ?: "",
                    xForwardedFor = parsed.headers["X-Forwarded-For"],
                    drmScheme = resolvedDrmScheme,
                    drmLicenseUrl = resolvedDrmLicenseUrl
                        ?: resolvedDrmKeyId?.let { id -> resolvedDrmKey?.let { k -> "$id:$k" } },
                    customHeaders = parsed.customHeaders
                )

                allEventLinks = listOf(mergedLink)
                streamUrl = PlayerStreamHelper.buildStreamUrl(mergedLink)
            } else {
                val cookie = intent.getStringExtra("COOKIE") ?: ""
                val referer = intent.getStringExtra("REFERER") ?: ""
                val origin = intent.getStringExtra("ORIGIN") ?: ""
                val drmLicense = intent.getStringExtra("DRM_LICENSE") ?: ""
                val userAgent = intent.getStringExtra("USER_AGENT") ?: ""
                val drmScheme = intent.getStringExtra("DRM_SCHEME") ?: "clearkey"
                val xForwardedFor = intent.getStringExtra("X_FORWARDED_FOR") ?: ""

                allEventLinks = listOf(
                    LiveEventLink(
                        quality = "Network Stream",
                        url = streamUrlRaw,
                        cookie = cookie,
                        referer = referer,
                        origin = origin,
                        userAgent = userAgent,
                        xForwardedFor = xForwardedFor.ifEmpty { null },
                        drmScheme = drmScheme,
                        drmLicenseUrl = drmLicense
                    )
                )

                streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[0])
            }

            currentLinkIndex = 0

            val savedPosition = intent.getLongExtra("playback_position", -1L)
            if (savedPosition > 0) {
                this.savedPlaybackPosition = savedPosition
            }
            return
        }

        channelData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CHANNEL, Channel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CHANNEL)
        }

        eventData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EVENT, LiveEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EVENT)
        }

        val passedLinkIndex = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)

        intentCategoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        intentSelectedGroup = intent.getStringExtra(EXTRA_SELECTED_GROUP)
        intentIsSports = intent.getBooleanExtra(EXTRA_IS_SPORTS, false)

        if (eventData != null) {
            contentType = ContentType.EVENT
            val event = eventData!!
            contentId = event.id
            contentName = event.title.ifEmpty { "${event.team1Name} vs ${event.team2Name}" }

            allEventLinks = event.links

            if (allEventLinks.isNotEmpty()) {
                currentLinkIndex = if (passedLinkIndex in allEventLinks.indices) passedLinkIndex else 0
                streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                currentLinkIndex = 0
                streamUrl = ""
            }

        } else if (channelData != null) {
            contentType = ContentType.CHANNEL
            val channel = channelData!!
            contentId = channel.id
            contentName = channel.name

            if (channel.links != null && channel.links.isNotEmpty()) {
                allEventLinks = channel.links.map {
                    LiveEventLink(
                        quality = it.quality,
                        url = it.url,
                        cookie = it.cookie,
                        referer = it.referer,
                        origin = it.origin,
                        userAgent = it.userAgent,
                        xForwardedFor = it.xForwardedFor,
                        drmScheme = it.drmScheme,
                        drmLicenseUrl = it.drmLicenseUrl
                    )
                }

                if (passedLinkIndex in allEventLinks.indices) {
                    currentLinkIndex = passedLinkIndex
                } else {
                    val matchIndex = allEventLinks.indexOfFirst { it.url == channel.streamUrl }
                    currentLinkIndex = if (matchIndex != -1) matchIndex else 0
                }

                streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                streamUrl = channel.streamUrl
                allEventLinks = emptyList()
            }

        } else {
            finish()
            return
        }

        val savedPosition = intent.getLongExtra("playback_position", -1L)
        if (savedPosition > 0) {
            this.savedPlaybackPosition = savedPosition
        }
    }

    private fun setupRelatedChannels() {
        if (contentType == ContentType.NETWORK_STREAM || DeviceUtils.isTvDevice) {
            relatedContentState.value = RelatedContentState.Hidden
            return
        }
        relatedContentState.value = RelatedContentState.Loading
    }

    private fun setupLinksUI() {
        updateLinksState()
    }

    private fun updateLinksState() {
        linksState.value = if (isInPipMode || allEventLinks.size <= 1) emptyList() else allEventLinks
        selectedLinkState.value = currentLinkIndex
    }

    private fun updateLinksForOrientation(isLandscape: Boolean) {
        updateLinksState()
    }

    private fun loadRelatedContent() {
        if (DeviceUtils.isTvDevice) return
        when (contentType) {
            ContentType.CHANNEL -> {
                channelData?.let { channel ->
                    val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
                    val isFavoritesSource = channelListKey?.startsWith("favorites_") == true

                    if (isFavoritesSource) {
                        val favList = ChannelListCache.get(channelListKey!!) ?: emptyList()
                        viewModel.setChannelList(favList)
                        val related = favList.filter { it.id != channel.id }.take(9)
                        relatedChannelsLockedForContentId = contentId
                        relatedChannels = related
                        relatedContentState.value = if (related.isEmpty()) RelatedContentState.Hidden
                        else RelatedContentState.Channels(related)
                    } else if (intentIsSports) {
                        relatedChannelsLockedForContentId = null
                        viewModel.loadRandomRelatedSports(channel.id)
                    } else {
                        relatedChannelsLockedForContentId = null
                        val categoryId = intentCategoryId?.takeIf { it.isNotEmpty() } ?: channel.categoryId
                        viewModel.loadRandomRelatedChannels(categoryId, channel.id, intentSelectedGroup)
                    }
                }
            }
            ContentType.EVENT -> {
                eventData?.let { event ->
                    viewModel.loadRelatedEvents(event.id)
                }
            }
            ContentType.NETWORK_STREAM -> {
            }
        }
    }

    private fun switchToChannel(newChannel: Channel) {
        releasePlayer()
        channelData = newChannel
        eventData = null
        contentType = ContentType.CHANNEL
        contentId = newChannel.id
        contentName = newChannel.name

        if (newChannel.links != null && newChannel.links.isNotEmpty()) {
            allEventLinks = newChannel.links.map {
                LiveEventLink(
                    quality = it.quality,
                    url = it.url,
                    cookie = it.cookie,
                    referer = it.referer,
                    origin = it.origin,
                    userAgent = it.userAgent,
                    xForwardedFor = it.xForwardedFor,
                    drmScheme = it.drmScheme,
                    drmLicenseUrl = it.drmLicenseUrl
                )
            }
            currentLinkIndex = 0
            streamUrl = allEventLinks.firstOrNull()?.let { PlayerStreamHelper.buildStreamUrl(it) } ?: newChannel.streamUrl
        } else {
            allEventLinks = emptyList()
            streamUrl = newChannel.streamUrl
        }

        setupPlayer()
        setupLinksUI()

        val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
        val isFavoritesSource = channelListKey?.startsWith("favorites_") == true

        if (isFavoritesSource) {
            val favList = ChannelListCache.get(channelListKey!!) ?: emptyList()
            viewModel.setChannelList(favList)
            val related = favList.filter { it.id != newChannel.id }.take(9)
            relatedChannelsLockedForContentId = contentId
            relatedChannels = related
            relatedContentState.value = if (related.isEmpty()) RelatedContentState.Hidden
            else RelatedContentState.Channels(related)
        } else {
            relatedChannelsLockedForContentId = null
            val isSports = newChannel.categoryId == "sports" || intentIsSports
            val categoryId = intentCategoryId?.takeIf { it.isNotEmpty() } ?: newChannel.categoryId
            relatedContentState.value = RelatedContentState.Loading
            if (isSports) viewModel.loadRandomRelatedSports(newChannel.id)
            else viewModel.loadRandomRelatedChannels(categoryId, newChannel.id, intentSelectedGroup)
        }
    }

    private fun switchToEvent(relatedChannel: Channel) {
        switchToChannel(relatedChannel)
    }

    private fun switchToEventFromLiveEvent(newEvent: LiveEvent, linkIndex: Int = 0) {
        try {
            releasePlayer()

            eventData = newEvent
            channelData = null
            contentType = ContentType.EVENT
            contentId = newEvent.id
            contentName = newEvent.title.ifEmpty { "${newEvent.team1Name} vs ${newEvent.team2Name}" }

            allEventLinks = newEvent.links

            if (allEventLinks.isNotEmpty()) {
                currentLinkIndex = if (linkIndex in allEventLinks.indices) linkIndex else 0
                streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                currentLinkIndex = 0
                streamUrl = ""
            }

            setupPlayer()
            setupLinksUI()
            relatedContentState.value = RelatedContentState.Loading
            viewModel.loadRelatedEvents(newEvent.id)

        } catch (e: Exception) {
        }
    }

    private fun switchToLink(link: LiveEventLink, position: Int) {
        currentLinkIndex = position
        streamUrl = PlayerStreamHelper.buildStreamUrl(link)
        updateLinksState()
        releasePlayer()
        setupPlayer()
    }

    override fun onPause() {
        super.onPause()
        val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else {
            false
        }

        if (!isPip && !DeviceUtils.isTvDevice) {
            player?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SAVE_CONTENT_TYPE", contentType.name)
        outState.putParcelable("SAVE_CHANNEL_DATA", channelData)
        outState.putParcelable("SAVE_EVENT_DATA", eventData)
        outState.putParcelableArrayList("SAVE_ALL_LINKS", ArrayList(allEventLinks))
        outState.putInt("SAVE_LINK_INDEX", currentLinkIndex)
        outState.putString("SAVE_CONTENT_ID", contentId)
        outState.putString("SAVE_CONTENT_NAME", contentName)
        outState.putString("SAVE_STREAM_URL", streamUrl)
        outState.putString("SAVE_CATEGORY_ID", intentCategoryId)
        outState.putString("SAVE_SELECTED_GROUP", intentSelectedGroup)
        outState.putLong("SAVE_PLAYBACK_POSITION", player?.currentPosition ?: 0L)
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            outState.putInt("SAVE_RESIZE_LANDSCAPE", networkLandscapeResizeMode)
            outState.putInt("SAVE_RESIZE_PORTRAIT", networkPortraitResizeMode)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreFromBundle(savedInstanceState)
    }

    private fun restoreFromBundle(bundle: Bundle) {
        val typeName = bundle.getString("SAVE_CONTENT_TYPE") ?: return
        contentType = ContentType.valueOf(typeName)
        channelData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable("SAVE_CHANNEL_DATA", Channel::class.java)
        } else {
            @Suppress("DEPRECATION") bundle.getParcelable("SAVE_CHANNEL_DATA")
        }
        eventData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable("SAVE_EVENT_DATA", LiveEvent::class.java)
        } else {
            @Suppress("DEPRECATION") bundle.getParcelable("SAVE_EVENT_DATA")
        }
        allEventLinks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelableArrayList("SAVE_ALL_LINKS", LiveEventLink::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION") bundle.getParcelableArrayList<LiveEventLink>("SAVE_ALL_LINKS") ?: emptyList()
        }
        currentLinkIndex = bundle.getInt("SAVE_LINK_INDEX", 0)
        contentId = bundle.getString("SAVE_CONTENT_ID", "")
        contentName = bundle.getString("SAVE_CONTENT_NAME", "")
        streamUrl = bundle.getString("SAVE_STREAM_URL", "")
        intentCategoryId = bundle.getString("SAVE_CATEGORY_ID")
        intentSelectedGroup = bundle.getString("SAVE_SELECTED_GROUP")
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            val savedResizeLandscape = bundle.getInt("SAVE_RESIZE_LANDSCAPE", -1)
            val savedResizePortrait = bundle.getInt("SAVE_RESIZE_PORTRAIT", -1)
            if (savedResizeLandscape != -1) networkLandscapeResizeMode = savedResizeLandscape
            if (savedResizePortrait != -1) networkPortraitResizeMode = savedResizePortrait
            if (savedResizeLandscape != -1 || savedResizePortrait != -1) resizeModesRestoredFromState = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingPlayerService.showAll(this)

        unregisterPipReceiver()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let {
            try {
                playerListener?.let { listener -> it.removeListener(listener) }
                it.stop()
                it.release()
            } catch (t: Throwable) {
            }
        }
        player = null
        playerListener = null
        playerSetupInProgress = false
    }

    private fun applyRememberedAspectRatioIfEnabled() {
        if (!resizeModesRestoredFromState) {
            if (contentType != ContentType.NETWORK_STREAM && preferencesManager.isRememberAspectRatioEnabled()) {
                val savedLandscape = preferencesManager.getSavedAspectRatio()
                val savedPortrait = preferencesManager.getSavedAspectRatioPortrait()
                if (savedLandscape != -1) networkLandscapeResizeMode = savedLandscape
                if (savedPortrait != -1) networkPortraitResizeMode = savedPortrait
                resizeModesRestoredFromState = true
            }
        }
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        resizeMode = if (isLandscape) networkLandscapeResizeMode else networkPortraitResizeMode
    }

    private var playerSetupInProgress = false
    private fun setupPlayer() {
        if (streamUrl.isBlank()) {
            errorMessage.value = "No stream URL"
            return
        }
        if (player != null || playerSetupInProgress) return

        val isTransferredFromFloating = intent.getBooleanExtra("use_transferred_player", false)
        if (isTransferredFromFloating && PlayerHolder.player != null) {
            val (transferredPlayer, _, _) = PlayerHolder.retrievePlayer()
            PlayerHolder.clearReferences()
            player = transferredPlayer
            errorMessage.value = ""
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            errorMessage.value = ""
                        }
                        Player.STATE_READY -> {
                            errorMessage.value = ""
                            applyRememberedAspectRatioIfEnabled()
                        }
                        Player.STATE_ENDED -> {}
                        else -> {}
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMessage.value = error.localizedMessage ?: "Playback error"
                }
            }
            playerListener = listener
            transferredPlayer?.addListener(listener)
            return
        }

        playerSetupInProgress = true
        lifecycleScope.launch {
            val parsed = allEventLinks.getOrNull(currentLinkIndex)
                ?.let { PlayerStreamHelper.buildStreamInfoFromLink(it) }
                ?: PlayerStreamHelper.parseStreamUrl(streamUrl)
            val headers = parsed.headers.toMutableMap()
            if (headers["User-Agent"].isNullOrBlank() || headers["User-Agent"] == "Default") {
                headers["User-Agent"] = "okhttp/4.12.0"
            }

            val mimeType: String? = PlayerStreamHelper.detectMimeTypeFromUrl(parsed.url)
                ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PlayerStreamHelper.resolveContentType(parsed.url, headers)
                }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)

            val clearKeyMgr: androidx.media3.exoplayer.drm.DefaultDrmSessionManager? = when {
                parsed.drmScheme != "clearkey" -> null
                parsed.drmKeyId != null && parsed.drmKey != null ->
                    PlayerStreamHelper.buildClearKeyInlineManager(parsed.drmKeyId, parsed.drmKey)
                parsed.drmLicenseUrl?.trimStart()?.startsWith("{") == true ->
                    PlayerStreamHelper.buildClearKeyJwkManager(parsed.drmLicenseUrl)
                parsed.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true ->
                    PlayerStreamHelper.buildClearKeyServerManager(parsed.drmLicenseUrl, headers)
                else -> null
            }

            val mediaSourceFactory = if (clearKeyMgr != null) {
                val effectiveDataSourceFactory = if (parsed.drmKeyId != null) {
                    val keyId = parsed.drmKeyId
                    androidx.media3.datasource.DataSource.Factory {
                        ClearKeyManifestRewritingDataSource(dataSourceFactory.createDataSource(), keyId)
                    }
                } else {
                    dataSourceFactory
                }
                DefaultMediaSourceFactory(this@FloatingPlayerActivity)
                    .setDataSourceFactory(effectiveDataSourceFactory)
                    .setDrmSessionManagerProvider { clearKeyMgr }
            } else {
                DefaultMediaSourceFactory(this@FloatingPlayerActivity).setDataSourceFactory(dataSourceFactory)
            }

            val renderersFactory = DefaultRenderersFactory(this@FloatingPlayerActivity)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            val ts = DefaultTrackSelector(this@FloatingPlayerActivity).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            trackSelector = ts

            val exo = ExoPlayer.Builder(this@FloatingPlayerActivity)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(ts)
                .setMediaSourceFactory(mediaSourceFactory)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                .build()

            player = exo
            playerSetupInProgress = false
            if (isMuted) exo.volume = 0f

            val mediaItemBuilder = MediaItem.Builder().setUri(parsed.url)
            mimeType?.let { mediaItemBuilder.setMimeType(it) }

            if ((parsed.drmScheme == "widevine" || parsed.drmScheme == "playready") && parsed.drmLicenseUrl != null) {
                val uuid = if (parsed.drmScheme == "widevine") C.WIDEVINE_UUID else C.PLAYREADY_UUID
                val licHeaders = headers.filter { (k, _) -> k.lowercase() !in setOf("referer", "origin") }
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(parsed.drmLicenseUrl)
                        .setLicenseRequestHeaders(licHeaders)
                        .setForceDefaultLicenseUri(true)
                        .setMultiSession(false)
                        .build()
                )
            }

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            errorMessage.value = ""
                        }
                        Player.STATE_READY -> {
                            errorMessage.value = ""
                            applyRememberedAspectRatioIfEnabled()
                        }
                        Player.STATE_ENDED -> {}
                        else -> {}
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMessage.value = error.localizedMessage ?: "Playback error"
                }
            }
            playerListener = listener
            exo.addListener(listener)

            exo.setMediaItem(mediaItemBuilder.build())
            if (savedPlaybackPosition > 0) {
                exo.seekTo(savedPlaybackPosition)
                savedPlaybackPosition = -1L
            }
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun toggleMute() {
        isMuted = PlayerStreamHelper.toggleMute(player, isMuted)
    }

    private fun cycleAspectRatio() {
        val isLandscape = DeviceUtils.isTvDevice || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val next = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT   -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM  -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else                                     -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        resizeMode = next
        if (contentType != ContentType.NETWORK_STREAM && preferencesManager.isRememberAspectRatioEnabled()) {
            if (isLandscape) { networkLandscapeResizeMode = next; preferencesManager.setSavedAspectRatio(next) }
            else             { networkPortraitResizeMode  = next; preferencesManager.setSavedAspectRatioPortrait(next) }
        }
    }

    private fun showSettingsDialog() {
        if (player == null) return
        if (isFinishing || isDestroyed) return
        if (isShowingSettingsDialog) return
        isShowingSettingsDialog = true
        showSettingsDialogState.value = true
    }

    private fun setupMessageBanner() {
        val message = listenerManager.getMessage()
        if (message.isNotBlank()) {
            messageBannerText.value = message
            messageBannerUrl.value = listenerManager.getMessageUrl()
        }
    }

    private fun toggleLock() {
        if (controlsState.isLocked) {
            controlsState.unlock(lifecycleScope)
        } else {
            controlsState.lock()
        }
    }

    private fun toggleFullscreen() {
        if (DeviceUtils.isTvDevice) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        player?.let {
            if (!it.isPlaying) {
                it.play()
            }
        }

        updatePipParams(enter = true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isPipSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else {
            false
        }
    }

    private fun updatePipParams(enter: Boolean = false): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            val format = player?.videoFormat
            val width = format?.width ?: 16
            val height = format?.height ?: 9

            var ratio = if (width > 0 && height > 0) {
                Rational(width, height)
            } else {
                Rational(16, 9)
            }

            val rationalLimitWide = Rational(239, 100)
            val rationalLimitTall = Rational(100, 239)

            if (ratio.toFloat() > rationalLimitWide.toFloat()) {
                ratio = rationalLimitWide
            } else if (ratio.toFloat() < rationalLimitTall.toFloat()) {
                ratio = rationalLimitTall
            }

            val builder = PictureInPictureParams.Builder()
            builder.setAspectRatio(ratio)
            builder.setActions(buildPipActions())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(false)
                builder.setSeamlessResizeEnabled(true)
            }

            val params = builder.build()
            if (enter) {
                enterPictureInPictureMode(params)
            } else {
                setPictureInPictureParams(params)
            }
            params
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        val rewindIntent = PendingIntent.getBroadcast(
            this,
            CONTROL_TYPE_REWIND,
            Intent(ACTION_MEDIA_CONTROL)
                .setPackage(packageName)
                .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_REWIND),
            PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_skip_backward),
            "Rewind",
            "Rewind 10s",
            rewindIntent
        ))

        val isPlaying = player?.isPlaying == true
        if (isPlaying) {
            val pauseIntent = PendingIntent.getBroadcast(
                this,
                CONTROL_TYPE_PAUSE,
                Intent(ACTION_MEDIA_CONTROL)
                    .setPackage(packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PAUSE),
                PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pause),
                getString(R.string.pause),
                getString(R.string.pause),
                pauseIntent
            ))
        } else {
            val playIntent = PendingIntent.getBroadcast(
                this,
                CONTROL_TYPE_PLAY,
                Intent(ACTION_MEDIA_CONTROL)
                    .setPackage(packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PLAY),
                PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_play),
                getString(R.string.play),
                getString(R.string.play),
                playIntent
            ))
        }

        val forwardIntent = PendingIntent.getBroadcast(
            this,
            CONTROL_TYPE_FORWARD,
            Intent(ACTION_MEDIA_CONTROL)
                .setPackage(packageName)
                .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD),
            PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_skip_forward),
            "Forward",
            "Forward 10s",
            forwardIntent
        ))

        return actions
    }

    private fun registerPipReceiver() {
        if (pipReceiver != null) return
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_MEDIA_CONTROL) return
                val currentPlayer = player ?: return
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = currentPlayer.playbackState == Player.STATE_ENDED
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> {
                        if (hasError || hasEnded) retryPlayback() else currentPlayer.play()
                        updatePipParams()
                    }
                    CONTROL_TYPE_PAUSE -> {
                        if (hasError || hasEnded) retryPlayback() else currentPlayer.pause()
                        updatePipParams()
                    }
                    CONTROL_TYPE_REWIND -> {
                        if (!hasError && !hasEnded) {
                            val newPos = currentPlayer.currentPosition - skipMs
                            currentPlayer.seekTo(if (newPos < 0) 0 else newPos)
                        }
                    }
                    CONTROL_TYPE_FORWARD -> {
                        if (!hasError && !hasEnded) {
                            val newPos = currentPlayer.currentPosition + skipMs
                            if (currentPlayer.isCurrentWindowLive && currentPlayer.duration != C.TIME_UNSET && newPos >= currentPlayer.duration) {
                                currentPlayer.seekTo(currentPlayer.duration)
                            } else {
                                currentPlayer.seekTo(newPos)
                            }
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, IntentFilter(ACTION_MEDIA_CONTROL), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
        }
    }

    private fun unregisterPipReceiver() {
        try {
            pipReceiver?.let {
                unregisterReceiver(it)
                pipReceiver = null
            }
        } catch (e: Exception) {
        }
    }

    private fun retryPlayback() {
        errorMessage.value = ""
        player?.release()
        player = null
        playerSetupInProgress = false
        setupPlayer()
    }

    override fun finish() {
        try {
            FloatingPlayerService.showAll(this)
            if (player != null) releasePlayer()
            unregisterPipReceiver()
            isInPipMode = false
            userRequestedPip = false
            wasLockedBeforePip = false
            super.finish()
        } catch (e: Exception) {
            super.finish()
        }
    }
}

