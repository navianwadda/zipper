package com.livetvpro.app.ui.player

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.LiveEventLink
import com.livetvpro.app.ui.player.compose.*
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
import com.livetvpro.app.ui.player.settings.PlayerSettingsDialog
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var listenerManager: com.livetvpro.app.utils.NativeListenerManager
    @Inject lateinit var favoritesRepository: com.livetvpro.app.data.repository.FavoritesRepository

    internal val viewModel: PlayerViewModel by viewModels()

    private val relatedContentState = mutableStateOf<RelatedContentState>(RelatedContentState.Hidden)
    private val nowPlayingState     = mutableStateOf<NowPlayingState>(NowPlayingState.Hidden)
    private val linksState          = mutableStateOf<List<LiveEventLink>>(emptyList())
    private val selectedLinkState   = mutableStateOf(0)
    private val linksRowState       = androidx.compose.foundation.lazy.LazyListState()
    private val messageBannerText   = mutableStateOf("")
    private val messageBannerUrl    = mutableStateOf("")
    private val showSettingsDialog  = mutableStateOf(false)
    private val showFloatingDialog  = mutableStateOf(false)
    internal val errorMessage       = mutableStateOf("")

    internal val isLandscapeState   = mutableStateOf(false)

    private var showChannelList     = mutableStateOf(false)

    private var player: ExoPlayer? by mutableStateOf(null)

    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    private val controlsState = PlayerControlsState()

    internal var gestureVolume: Int    = 100
    internal var gestureBrightness: Int = 0
    internal var isInPipMode by mutableStateOf(false)
    private var isEnteringPip = false
    private var isMuted by mutableStateOf(false)
    internal val skipMs = 10_000L

    private var resizeMode by mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    private var networkPortraitResizeMode  = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var resizeModesRestoredFromState = false
    private var aspectRatioAppliedForCurrentLoad = false

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private var pipReceiver: BroadcastReceiver? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var isScreenOff     = false
    private var wasLockedBeforePip = false
    private var isShowingSettingsDialog = false
    val isPipSupported by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) false
        else packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    internal var contentType: ContentType = ContentType.CHANNEL
    private var channelNumberInput  = ""
    private val channelNumberHandler  = Handler(Looper.getMainLooper())
    private val channelNumberRunnable = Runnable { navigateToChannelByNumber() }
    private val showChannelOverlayState = mutableStateOf<Pair<String, String>?>(null)
    private val overlayHideRunnable = Runnable { showChannelOverlayState.value = null }
    private var pendingChannelIndex     = -1
    private var pendingChannelDirection = 0
    private var pendingChannelNumber    = -1
    private var channelData: Channel?    = null
    private var eventData: LiveEvent?    = null

    private var relatedChannelsLockedForContentId: String? = null
    internal var allEventLinks    = listOf<LiveEventLink>()
    internal var currentLinkIndex = 0
    internal var contentId:   String = ""
    internal var contentName: String by mutableStateOf("")
    private var streamUrl: String = ""
    private var intentCategoryId:    String? = null
    private var intentSelectedGroup: String? = null
    private var intentIsSports: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    enum class ContentType { CHANNEL, EVENT, NETWORK_STREAM }
    companion object {
        private const val EXTRA_CHANNEL              = "extra_channel"
        private const val EXTRA_EVENT                = "extra_event"
        private const val EXTRA_SELECTED_LINK_INDEX  = "extra_selected_link_index"
        private const val EXTRA_RELATED_CHANNELS_KEY = "extra_related_channels_key"
        private const val EXTRA_CATEGORY_ID          = "extra_category_id"
        private const val EXTRA_IS_SPORTS            = "extra_is_sports"
        private const val EXTRA_CHANNEL_LIST_KEY     = "extra_channel_list_key"
        private const val EXTRA_SELECTED_GROUP       = "extra_selected_group"
        private const val ACTION_MEDIA_CONTROL       = "com.livetvpro.app.MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE         = "control_type"
        private const val CONTROL_TYPE_PLAY          = 1
        private const val CONTROL_TYPE_PAUSE         = 2
        private const val CONTROL_TYPE_REWIND        = 3
        private const val CONTROL_TYPE_FORWARD       = 4
        private const val CONTROL_TYPE_PREV_CHANNEL  = 5
        private const val CONTROL_TYPE_NEXT_CHANNEL  = 6
        var isInPip: Boolean = false
        fun startWithChannel(
            context: Context, channel: Channel, linkIndex: Int = -1,
            relatedChannels: ArrayList<Channel>? = null, categoryId: String? = null,
            selectedGroup: String? = null, isSports: Boolean = false,
            channelList: ArrayList<Channel>? = null, channelListCacheKey: String? = null,
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, channel as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                relatedChannels?.let {
                    val key = "related_${channel.id}"
                    ChannelListCache.put(key, it)
                    putExtra(EXTRA_RELATED_CHANNELS_KEY, key)
                }
                categoryId?.let    { putExtra(EXTRA_CATEGORY_ID, it) }
                selectedGroup?.let { putExtra(EXTRA_SELECTED_GROUP, it) }
                putExtra(EXTRA_IS_SPORTS, isSports)
                val key = channelListCacheKey ?: channelList?.let { list ->
                    val k = categoryId ?: channel.categoryId.takeIf { it.isNotEmpty() } ?: channel.id
                    ChannelListCache.put(k, list); k
                }
                key?.let { putExtra(EXTRA_CHANNEL_LIST_KEY, it) }
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (isInPip) addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) context.overridePendingTransition(0, 0)
        }
        fun startWithEvent(context: Context, event: LiveEvent, linkIndex: Int = -1) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_EVENT, event as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (isInPip) addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) context.overridePendingTransition(0, 0)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNewIntent(intent)
    }
    private fun handleNewIntent(intent: Intent) {
        cancelNumberInput()
        showChannelList.value = false
        intentCategoryId    = intent.getStringExtra(EXTRA_CATEGORY_ID)
        intentSelectedGroup = intent.getStringExtra(EXTRA_SELECTED_GROUP)
        intentIsSports      = intent.getBooleanExtra(EXTRA_IS_SPORTS, false)
        val newChannel = intent.parcelableExtra<Channel>(EXTRA_CHANNEL)
        val newEvent   = intent.parcelableExtra<LiveEvent>(EXTRA_EVENT)
        val linkIndex  = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)
        when {
            newChannel != null -> switchToChannel(newChannel, linkIndex)
            newEvent   != null -> switchToEventFromLiveEvent(newEvent, linkIndex)
        }
        setupWindowFlags(isLandscapeState.value)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.livetvpro.app.cast.CastManager.init(this)
        themeManager.registerActivityContext(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (DeviceUtils.isBigScreenLayout) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val isLandscape = DeviceUtils.isBigScreenLayout ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        parseIntent()
        if (isFinishing) return
        setupWindowFlags(isLandscape)
        setupSystemUI()
        if (contentType == ContentType.CHANNEL && contentId.isNotEmpty()) {
            val cacheKey   = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val cachedList = cacheKey?.let { ChannelListCache.get(it) }
            if (!cachedList.isNullOrEmpty()) {
                viewModel.setChannelList(cachedList)
                viewModel.refreshChannelData(contentId)
            } else {
                viewModel.loadAllChannelsForList(
                    categoryId       = intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "",
                    refreshChannelId = contentId,
                )
            }
        }

        applyOrientationSettings(isLandscape)
        val am     = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        gestureVolume = if (maxVol > 0) (curVol * 100f / maxVol).toInt() else 100
        setupRelatedChannels()
        setupLinksUI()
        setupMessageBanner()
        setupBackHandler()
        if (DeviceUtils.isTvDevice) {
            relatedContentState.value = RelatedContentState.Hidden
        } else if (!isLandscape) {
            relatedContentState.value = RelatedContentState.Loading
        }
        setupPlayer()
        loadRelatedContent()

        viewModel.refreshedChannel.observe(this) { freshChannel ->
            if (freshChannel?.links.isNullOrEmpty()) return@observe
            val newLinks = freshChannel!!.links!!.map { it.toLiveEventLink() }
            if (allEventLinks.isEmpty() || allEventLinks.size < newLinks.size) {
                allEventLinks = newLinks
                val matchIndex = allEventLinks.indexOfFirst { it.url == streamUrl }
                if (matchIndex != -1) currentLinkIndex = matchIndex
                val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                updateLinksForOrientation(landscape)
            }
        }
        viewModel.channelListItems.observe(this) { items ->
            if (items.isNullOrEmpty() || contentType != ContentType.CHANNEL) return@observe
            val pendingNum = pendingChannelNumber
            if (pendingNum != -1) {
                pendingChannelNumber    = -1
                pendingChannelDirection = 0
                val index = pendingNum - 1
                if (index in items.indices) {
                    val target = items[index]
                    if (target.id != contentId) switchToChannel(target)
                    showChannelOverlay((index + 1).toString(), target)
                    channelNumberHandler.removeCallbacks(overlayHideRunnable)
                    channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                } else {
                    showChannelOverlayState.value = null
                }
                return@observe
            }
            val direction = pendingChannelDirection
            if (direction != 0) {
                pendingChannelDirection = 0
                val currentIndex  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                val targetIndex   = (currentIndex + direction).coerceIn(0, items.size - 1)
                val targetChannel = items[targetIndex]
                if (targetChannel.id != contentId) switchToChannel(targetChannel)
                showChannelOverlay((targetIndex + 1).toString(), targetChannel)
                channelNumberHandler.removeCallbacks(overlayHideRunnable)
                channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
            }
        }
        viewModel.relatedItems.observe(this) { channels ->
            if (contentType != ContentType.CHANNEL) return@observe
            if (relatedChannelsLockedForContentId == contentId) return@observe
            relatedChannels = channels
            relatedContentState.value =
                if (channels.isEmpty()) RelatedContentState.Hidden
                else RelatedContentState.Channels(channels)
        }
        viewModel.relatedLiveEvents.observe(this) { liveEvents ->
            if (contentType != ContentType.EVENT) return@observe
            relatedContentState.value =
                if (liveEvents.isEmpty()) RelatedContentState.Hidden
                else RelatedContentState.Events(liveEvents)
        }

        setContent {
            CompositionLocalProvider(LocalActivity provides null) {
                LiveTVProTheme(
                    themeManager  = themeManager,
                    surfaceColor  = Color.Transparent,
                ) {
                    PlayerActivityRoot()
                }
            }
        }
    }
    @Composable
    private fun PlayerActivityRoot() {
        val isLandscape    = isLandscapeState.value
        val spanCount      = resources.getInteger(R.integer.grid_column_count)
        val eventSpanCount = resources.getInteger(R.integer.event_span_count)
        val isPlaying by produceState(initialValue = false, player) {
            while (true) { value = player?.isPlaying == true; delay(100) }
        }
        val isBuffering by produceState(initialValue = false, player) {
            while (true) { value = player?.playbackState == Player.STATE_BUFFERING; delay(100) }
        }

        val castState by com.livetvpro.app.cast.CastManager.connectionState.collectAsState()
        val isCasting = castState == com.livetvpro.app.cast.CastConnectionState.CONNECTED
        var castIsPlaying by remember { mutableStateOf(false) }

        var currentPosition  by remember { mutableLongStateOf(0L) }
        var duration         by remember { mutableLongStateOf(0L) }
        var bufferedPosition by remember { mutableLongStateOf(0L) }

        LaunchedEffect(player, isCasting) {
            while (true) {
                if (isCasting) {
                    currentPosition  = com.livetvpro.app.cast.CastManager.remotePosition()
                    duration         = com.livetvpro.app.cast.CastManager.remoteDuration()
                    bufferedPosition = currentPosition
                    castIsPlaying    = com.livetvpro.app.cast.CastManager.isRemotePlaying()
                } else {
                    currentPosition  = player?.currentPosition ?: 0L
                    bufferedPosition = player?.bufferedPosition ?: 0L
                    duration         = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
                }
                delay(500L)
            }
        }

        var wasCasting by remember { mutableStateOf(false) }
        LaunchedEffect(castState) {
            when (castState) {
                com.livetvpro.app.cast.CastConnectionState.CONNECTED -> {
                    wasCasting = true
                    val mimeType = PlayerStreamHelper.detectMimeTypeFromUrl(streamUrl)
                    val isLive   = contentType == ContentType.CHANNEL || contentType == ContentType.EVENT
                    com.livetvpro.app.cast.CastManager.requestLoad(
                        url             = streamUrl,
                        title           = contentName,
                        mimeType        = mimeType,
                        isLive          = isLive,
                        startPositionMs = player?.currentPosition ?: 0L,
                    )
                    player?.pause()
                }
                com.livetvpro.app.cast.CastConnectionState.DISCONNECTED -> {
                    if (wasCasting) {
                        wasCasting = false
                        val resumePosition = com.livetvpro.app.cast.CastManager.remotePosition()
                        player?.let {
                            if (resumePosition > 0L) it.seekTo(resumePosition)
                            it.play()
                        }
                    }
                }
                else -> {}
            }
        }

        val scope            = rememberCoroutineScope()
        val channelListItems by viewModel.channelListItems.observeAsState(emptyList())

        val isChannelListAvailable = contentType == ContentType.CHANNEL &&
            channelListItems.isNotEmpty() &&
            (isLandscape || DeviceUtils.isBigScreenLayout)
        val isPipEnabled = !DeviceUtils.isTvDevice &&
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            else false

        val onPlayPause: () -> Unit = {
            if (isCasting) {
                if (castIsPlaying) com.livetvpro.app.cast.CastManager.pause()
                else com.livetvpro.app.cast.CastManager.play()
            } else player?.let {
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = it.playbackState == Player.STATE_ENDED
                if (hasError || hasEnded) retryPlayback()
                else if (it.isPlaying) it.pause() else it.play()
            }
        }
        val onRewind: () -> Unit = {
            if (isCasting) {
                com.livetvpro.app.cast.CastManager.seekTo((currentPosition - skipMs).coerceAtLeast(0L))
            } else player?.let { it.seekTo((it.currentPosition - skipMs).coerceAtLeast(0L)) }
        }
        val onForward: () -> Unit = {
            if (isCasting) {
                val newPos = currentPosition + skipMs
                com.livetvpro.app.cast.CastManager.seekTo(if (duration > 0L) newPos.coerceAtMost(duration) else newPos)
            } else player?.let {
                val newPos = it.currentPosition + skipMs
                if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPos >= it.duration)
                    it.seekTo(it.duration)
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
                        val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
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
                        val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val next = (idx + 1).coerceAtMost(items.size - 1)
                        if (next != idx) switchToChannel(items[next])
                    }
                }
                else -> {}
            }
        }
        val onVolumeSwipe: (Int) -> Unit = { vol ->
            gestureVolume = vol
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val deviceVol = vol.coerceAtMost(100)
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (deviceVol / 100f * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0,
            )
            val boostPercent = (vol - 100).coerceIn(0, 100)
            player?.let { com.livetvpro.app.utils.VolumeBoostHelper.setBoostLevel(it, boostPercent) }
        }
        val onBrightnessSwipe: (Int) -> Unit = { bri ->
            gestureBrightness = bri
            val lp = window.attributes
            lp.screenBrightness = if (bri == 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else bri / 100f
            window.attributes = lp
        }
        @Composable
        fun Controls(isLandscape: Boolean) {
            PlayerControls(
                state                  = controlsState,
                isPlaying              = if (isCasting) castIsPlaying else isPlaying,
                isMuted                = isMuted,
                currentPosition        = currentPosition,
                duration               = duration,
                bufferedPosition       = bufferedPosition,
                channelName            = contentName,
                showPipButton          = isPipEnabled,

                showAspectRatioButton  = true,
                isLandscape            = isLandscape,
                isTvMode               = DeviceUtils.isBigScreenLayout,
                supportsPointerInput   = !DeviceUtils.isTvDevice,
                layoutMode     = if (contentType == ContentType.NETWORK_STREAM) 0 else preferencesManager.getLayoutMode(),
                isNetworkStream        = contentType == ContentType.NETWORK_STREAM,
                isChannelListAvailable = isChannelListAvailable,
                onBackClick            = { finish() },
                onPipClick             = { enterPipMode() },
                onSettingsClick        = { showSettingsDialog() },
                onMuteClick            = { toggleMute() },
                onLockClick            = { locked -> if (locked) controlsState.lock() else controlsState.unlock(scope) },
                onChannelListClick     = { showChannelList.value = true },
                onPlayPauseClick       = onPlayPause,
                onSeek                 = { position ->
                    if (isCasting) com.livetvpro.app.cast.CastManager.seekTo(position)
                    else player?.seekTo(position)
                },
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
                volumeBoostEnabled     = preferencesManager.isVolumeBoostingEnabled(),
                showCastButton         = com.livetvpro.app.cast.CastManager.isAvailable() &&
                    contentType != ContentType.NETWORK_STREAM,
                isCastConnected        = isCasting,
                onCastClick            = {
                    com.livetvpro.app.cast.CastManager.showCastPicker(
                        context        = this@PlayerActivity,
                        colorThemeName = themeManager.colorThemeFlow.value.name,
                        isDark         = themeManager.isDarkMode(this@PlayerActivity),
                    )
                },
            )
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .then(if (!isLandscape && !isInPipMode) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
        ) {
            if (!isLandscape) {
                val isNetworkStream = contentType == ContentType.NETWORK_STREAM
                Box(
                    modifier = Modifier
                        .then(
                            when {

                                isInPipMode     -> Modifier.fillMaxSize()
                                isNetworkStream -> Modifier.fillMaxWidth().weight(1f)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                else            -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            }
                        ),
                ) {
                    VideoSurface(
                        player     = player,
                        resizeMode = if (isNetworkStream) resizeMode else AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier   = Modifier.fillMaxSize(),
                    )
                    if (errorMessage.value.isNotBlank()) ErrorOverlay(errorMessage.value)
                    if (isBuffering && errorMessage.value.isBlank()) {
                        CircularProgressIndicator(
                            modifier    = Modifier.align(Alignment.Center).size(48.dp),
                            color       = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }
                    if (!isInPipMode) {
                        Controls(isLandscape = false)
                        if (isChannelListAvailable) {
                            ChannelListPanel(
                                visible          = showChannelList.value,
                                channels         = channelListItems,
                                currentChannelId = contentId,
                                onChannelClick   = { switchToChannel(it) },
                                onDismiss        = { showChannelList.value = false },
                                modifier         = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {

                Box(modifier = Modifier.fillMaxSize()) {
                    VideoSurface(
                        player     = player,
                        resizeMode = resizeMode,
                        modifier   = Modifier.fillMaxSize(),
                    )
                    if (errorMessage.value.isNotBlank()) ErrorOverlay(errorMessage.value)
                    if (isBuffering && errorMessage.value.isBlank()) {
                        CircularProgressIndicator(
                            modifier    = Modifier.align(Alignment.Center).size(48.dp),
                            color       = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }
                    if (!isInPipMode) {
                        Controls(isLandscape = true)
                        if (linksState.value.size > 1) {
                            LaunchedEffect(linksRowState.isScrollInProgress) {
                                if (linksRowState.isScrollInProgress) {
                                    controlsState.show(lifecycleScope)
                                }
                            }
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
                                    onChannelClick       = { channel, idx -> switchToChannel(channel, idx) },
                                    onEventClick         = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                                    onMessageBannerClick = {},
                                    spanCount            = spanCount,
                                    eventSpanCount       = eventSpanCount,
                                    linksRowState        = linksRowState,
                                )
                            }
                        }
                        if (isChannelListAvailable) {
                            ChannelListPanel(
                                visible          = showChannelList.value,
                                channels         = channelListItems,
                                currentChannelId = contentId,
                                onChannelClick   = { switchToChannel(it) },
                                onDismiss        = { showChannelList.value = false },
                                modifier         = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (!isLandscape && !isInPipMode && contentType != ContentType.NETWORK_STREAM) {
                PlayerScreen(
                    isLandscape          = false,
                    relatedContentState  = relatedContentState.value,
                    links                = linksState.value,
                    selectedLinkIndex    = selectedLinkState.value,
                    messageBanner        = messageBannerText.value,
                    messageBannerUrl     = messageBannerUrl.value,
                    onLinkClick          = { link, idx -> switchToLink(link, idx) },
                    onChannelClick       = { channel, idx -> switchToChannel(channel, idx) },
                    onEventClick         = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                    onMessageBannerClick = {
                        val url = messageBannerUrl.value
                        if (url.isNotBlank()) {
                            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            catch (_: Exception) {}
                        }
                    },
                    nowPlaying       = nowPlayingState.value,
                    onToggleFavorite = { channel -> toggleNowPlayingFavorite(channel) },
                    spanCount      = spanCount,
                    eventSpanCount = eventSpanCount,
                    linksRowState  = linksRowState,
                    modifier       = Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
        if (!isInPipMode && showSettingsDialog.value && player != null) {
            PlayerSettingsDialog(
                player    = player!!,
                onDismiss = {
                    showSettingsDialog.value = false
                    isShowingSettingsDialog  = false
                    applyRememberedAspectRatioIfEnabled()
                },
            )
        }
        if (!isInPipMode && showFloatingDialog.value) {
            FloatingPlayerDialog(
                preferencesManager = preferencesManager,
                onDismiss          = { showFloatingDialog.value = false },
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
        val isLandscape = DeviceUtils.isBigScreenLayout ||
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
        themeManager.refreshDynamicColors(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
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
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SAVE_CONTENT_TYPE",  contentType.name)
        outState.putParcelable("SAVE_CHANNEL_DATA", channelData)
        outState.putParcelable("SAVE_EVENT_DATA",   eventData)
        outState.putParcelableArrayList("SAVE_ALL_LINKS", ArrayList(allEventLinks))
        outState.putInt("SAVE_LINK_INDEX",      currentLinkIndex)
        outState.putString("SAVE_CONTENT_ID",   contentId)
        outState.putString("SAVE_CONTENT_NAME", contentName)
        outState.putString("SAVE_STREAM_URL",   streamUrl)
        outState.putString("SAVE_CATEGORY_ID",  intentCategoryId)
        outState.putString("SAVE_SELECTED_GROUP", intentSelectedGroup)
        outState.putBoolean("SAVE_IS_SPORTS",   intentIsSports)
        outState.putLong("SAVE_PLAYBACK_POSITION", player?.currentPosition ?: 0L)
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            outState.putInt("SAVE_RESIZE_LANDSCAPE", networkLandscapeResizeMode)
            outState.putInt("SAVE_RESIZE_PORTRAIT",  networkPortraitResizeMode)
        }
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val typeName = savedInstanceState.getString("SAVE_CONTENT_TYPE") ?: return
        contentType  = ContentType.valueOf(typeName)
        channelData  = savedInstanceState.parcelableCompat("SAVE_CHANNEL_DATA")
        eventData    = savedInstanceState.parcelableCompat("SAVE_EVENT_DATA")
        allEventLinks = savedInstanceState.parcelableArrayListCompat<LiveEventLink>("SAVE_ALL_LINKS") ?: emptyList()
        currentLinkIndex = savedInstanceState.getInt("SAVE_LINK_INDEX", 0)
        contentId    = savedInstanceState.getString("SAVE_CONTENT_ID",   "")
        contentName  = savedInstanceState.getString("SAVE_CONTENT_NAME", "")
        streamUrl    = savedInstanceState.getString("SAVE_STREAM_URL",   "")
        intentCategoryId    = savedInstanceState.getString("SAVE_CATEGORY_ID")
        intentSelectedGroup = savedInstanceState.getString("SAVE_SELECTED_GROUP")
        intentIsSports = savedInstanceState.getBoolean("SAVE_IS_SPORTS", false)
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            val savedL = savedInstanceState.getInt("SAVE_RESIZE_LANDSCAPE", -1)
            val savedP = savedInstanceState.getInt("SAVE_RESIZE_PORTRAIT",  -1)
            if (savedL != -1) networkLandscapeResizeMode = savedL
            if (savedP != -1) networkPortraitResizeMode  = savedP
            if (savedL != -1 || savedP != -1) resizeModesRestoredFromState = true
        }
    }
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            setPictureInPictureParams(buildPipParams())
        }

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> isScreenOff = true
                    Intent.ACTION_SCREEN_ON  -> isScreenOff = false
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }
    override fun onResume() {
        super.onResume()
        isScreenOff = false
        themeManager.refreshDynamicColors(this)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)
        setupWindowFlags(isLandscape)
        setupSystemUI()
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (player == null) setupPlayer()
        if (contentType == ContentType.CHANNEL && viewModel.channelListItems.value.isNullOrEmpty()) {
            val channelListKey    = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val isFavoritesSource = channelListKey?.startsWith("favorites_") == true
            if (isFavoritesSource) {
                ChannelListCache.get(channelListKey!!)?.let { viewModel.setChannelList(it) }
            } else {
                viewModel.loadAllChannelsForList(
                    intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: ""
                )
            }
        }
    }
    override fun onPause() {
        super.onPause()
        cancelNumberInput()
        val isPip = isEnteringPip ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
        if (!isPip) player?.pause()
    }
    override fun onStop() {
        super.onStop()
        screenOffReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenOffReceiver = null
        }
        if (isInPipMode) {
            if (!isScreenOff) { releasePlayer(); finish() }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        channelNumberHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        unregisterPipReceiver()
        releasePlayer()
    }
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let { unregisterReceiver(it); pipReceiver = null }
            isInPipMode   = false
            isEnteringPip = false
            isInPip       = false
            updateLinksState()
            super.onPictureInPictureModeChanged(false, newConfig)
            window.statusBarColor     = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controlsState.show(lifecycleScope)
            if (wasLockedBeforePip) { controlsState.lock(); wasLockedBeforePip = false }
            val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            setupWindowFlags(landscape)
            setupSystemUI()
            applyOrientationSettings(landscape)
            return
        }
        isInPipMode   = true
        isEnteringPip = false
        isInPip       = true
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        updateLinksState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPictureInPictureParams(buildPipParams(enter = true))
        controlsState.hide()
        setupPipReceiver()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPictureInPictureModeChanged(true, newConfig)
    }

    private fun refreshPipParamsIfNeeded() {
        if (!isInPipMode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { setPictureInPictureParams(buildPipParams()) } catch (_: Exception) {}
        }
    }
    @SuppressLint("NewApi")
    override fun onUserLeaveHint() {
        val isForegrounded = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (!DeviceUtils.isTvDevice && isPipSupported && player?.isPlaying == true && isForegrounded && !isFinishing) {
            isEnteringPip      = true
            wasLockedBeforePip = controlsState.isLocked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(buildPipParams(enter = true))
            } else {
                @Suppress("DEPRECATION") enterPictureInPictureMode()
            }
        }
        super.onUserLeaveHint()
    }
    @SuppressLint("NewApi")
    internal fun enterPipMode() {
        if (!isPipSupported) return
        isEnteringPip      = true
        wasLockedBeforePip = controlsState.isLocked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(buildPipParams(enter = true))
        } else {
            @Suppress("DEPRECATION") enterPictureInPictureMode()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(enter: Boolean = false): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) builder.setTitle(contentName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isPlaying = player?.isPlaying == true
            builder.setAutoEnterEnabled(isPlaying)
            builder.setSeamlessResizeEnabled(isPlaying)
        }
        builder.setActions(createPipActions(this, player?.isPlaying != true))
        player?.videoFormat?.let { format ->
            if (format.height > 0 && format.width > 0) {
                val r = Rational(format.width, format.height).toFloat()
                if (r in 0.42..2.38) builder.setAspectRatio(Rational(format.width, format.height))
            }
        }
        return builder.build()
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipActions(context: Context, isPaused: Boolean): List<RemoteAction> {
        fun makePendingIntent(requestCode: Int, controlType: Int) = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                .putExtra(EXTRA_CONTROL_TYPE, controlType),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playPauseAction = if (isPaused) RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_play),
            context.getString(R.string.play), context.getString(R.string.play),
            makePendingIntent(CONTROL_TYPE_PLAY, CONTROL_TYPE_PLAY),
        ) else RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_pause),
            context.getString(R.string.pause), context.getString(R.string.pause),
            makePendingIntent(CONTROL_TYPE_PAUSE, CONTROL_TYPE_PAUSE),
        )
        val layoutModeIsNav = preferencesManager.getLayoutMode() == PreferencesManager.LAYOUT_MODE_NAV_ONLY
        val showNav = when (contentType) {
            ContentType.CHANNEL -> layoutModeIsNav
            ContentType.EVENT   -> layoutModeIsNav && allEventLinks.size > 1
            else -> false
        }
        val prevDesc = if (contentType == ContentType.EVENT) "Previous link" else "Previous channel"
        val nextDesc = if (contentType == ContentType.EVENT) "Next link" else "Next channel"
        return if (showNav) listOf(
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_prev_channel),
                "Previous", prevDesc, makePendingIntent(CONTROL_TYPE_PREV_CHANNEL, CONTROL_TYPE_PREV_CHANNEL)),
            playPauseAction,
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_next_channel),
                "Next", nextDesc, makePendingIntent(CONTROL_TYPE_NEXT_CHANNEL, CONTROL_TYPE_NEXT_CHANNEL)),
        ) else listOf(
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_backward),
                "Rewind", "Rewind 10s", makePendingIntent(CONTROL_TYPE_REWIND, CONTROL_TYPE_REWIND)),
            playPauseAction,
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_forward),
                "Forward", "Forward 10s", makePendingIntent(CONTROL_TYPE_FORWARD, CONTROL_TYPE_FORWARD)),
        )
    }
    private fun setupPipReceiver() {
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_MEDIA_CONTROL) return
                val p        = player ?: return
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = p.playbackState == Player.STATE_ENDED
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY  -> {
                        if (hasError || hasEnded) retryPlayback() else p.play()
                        refreshPipParamsIfNeeded()
                    }
                    CONTROL_TYPE_PAUSE -> {
                        if (hasError || hasEnded) retryPlayback() else p.pause()
                        refreshPipParamsIfNeeded()
                    }
                    CONTROL_TYPE_REWIND  -> if (!hasError && !hasEnded)
                        p.seekTo((p.currentPosition - skipMs).coerceAtLeast(0L))
                    CONTROL_TYPE_FORWARD -> if (!hasError && !hasEnded) {
                        val newPos = p.currentPosition + skipMs
                        if (p.isCurrentWindowLive && p.duration != C.TIME_UNSET && newPos >= p.duration) p.seekTo(p.duration)
                        else p.seekTo(newPos)
                    }
                    CONTROL_TYPE_PREV_CHANNEL -> {
                        when (contentType) {
                            ContentType.CHANNEL -> {
                                val items = viewModel.channelListItems.value ?: return
                                val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                val prev = (idx - 1).coerceAtLeast(0)
                                if (prev != idx) switchToChannel(items[prev])
                            }
                            ContentType.EVENT -> {
                                if (allEventLinks.size > 1) {
                                    val prev = (currentLinkIndex - 1).coerceAtLeast(0)
                                    if (prev != currentLinkIndex) switchToLink(allEventLinks[prev], prev)
                                }
                            }
                            else -> {}
                        }
                    }
                    CONTROL_TYPE_NEXT_CHANNEL -> {
                        when (contentType) {
                            ContentType.CHANNEL -> {
                                val items = viewModel.channelListItems.value ?: return
                                val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                val next = (idx + 1).coerceAtMost(items.size - 1)
                                if (next != idx) switchToChannel(items[next])
                            }
                            ContentType.EVENT -> {
                                if (allEventLinks.size > 1) {
                                    val next = (currentLinkIndex + 1).coerceAtMost(allEventLinks.size - 1)
                                    if (next != currentLinkIndex) switchToLink(allEventLinks[next], next)
                                }
                            }
                            else -> {}
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
        try { pipReceiver?.let { unregisterReceiver(it); pipReceiver = null } } catch (_: Exception) {}
    }
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isInPipMode -> return
                    channelNumberInput.isNotEmpty() || pendingChannelIndex != -1 ||
                        pendingChannelDirection != 0 || pendingChannelNumber != -1 -> cancelNumberInput()
                    showChannelList.value -> showChannelList.value = false
                    else -> finish()
                }
            }
        })
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (code == android.view.KeyEvent.KEYCODE_MEDIA_NEXT ||
            code == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            code == android.view.KeyEvent.KEYCODE_CHANNEL_UP ||
            code == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN ||
            code == android.view.KeyEvent.KEYCODE_PAGE_UP ||
            code == android.view.KeyEvent.KEYCODE_PAGE_DOWN) {
            return when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> onKeyDown(code, event)
                android.view.KeyEvent.ACTION_UP   -> onKeyUp(code, event)
                else -> super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_CHANNEL_UP,
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
            android.view.KeyEvent.KEYCODE_PAGE_UP,
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                val direction = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                    android.view.KeyEvent.KEYCODE_PAGE_UP -> +1
                    else -> -1
                }
                val index = pendingChannelIndex
                pendingChannelIndex = -1
                val items = viewModel.channelListItems.value
                when {
                    contentType == ContentType.EVENT -> {
                        if (allEventLinks.size > 1) {
                            val targetIndex = (currentLinkIndex + direction).coerceIn(0, allEventLinks.size - 1)
                            if (targetIndex != currentLinkIndex) switchToLink(allEventLinks[targetIndex], targetIndex)
                        }
                    }
                    index != -1 && !items.isNullOrEmpty() && index in items.indices -> {
                        pendingChannelDirection = 0
                        val target = items[index]
                        if (target.id != contentId) switchToChannel(target)
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                    }
                    index == -1 && !items.isNullOrEmpty() && pendingChannelDirection != 0 -> {
                        val dir = pendingChannelDirection; pendingChannelDirection = 0
                        val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val targetIndex  = (currentIndex + dir).coerceIn(0, items.size - 1)
                        val target       = items[targetIndex]
                        if (target.id != contentId) { switchToChannel(target); showChannelOverlay((targetIndex + 1).toString(), target) }
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                    }
                    items.isNullOrEmpty() -> {
                        pendingChannelDirection = direction
                        viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
                    }
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_SPACE -> {
                cancelNumberInput()
                player?.let {
                    if (errorMessage.value.isNotBlank() || it.playbackState == Player.STATE_ENDED) retryPlayback()
                    else if (it.isPlaying) it.pause() else it.play()
                }
                controlsState.show(lifecycleScope); true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (showChannelList.value) return super.onKeyDown(keyCode, event)
                cancelNumberInput()
                player?.let {
                    val newPos = it.currentPosition + skipMs
                    if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPos >= it.duration) it.seekTo(it.duration)
                    else it.seekTo(newPos)
                }
                controlsState.show(lifecycleScope); true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (showChannelList.value) return super.onKeyDown(keyCode, event)
                cancelNumberInput()
                player?.let { it.seekTo((it.currentPosition - skipMs).coerceAtLeast(0L)) }
                controlsState.show(lifecycleScope); true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                val items     = viewModel.channelListItems.value
                val available = contentType == ContentType.CHANNEL && !items.isNullOrEmpty() &&
                    (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || DeviceUtils.isBigScreenLayout)
                if (available) {
                    if (!showChannelList.value) cancelNumberInput()
                    showChannelList.value = !showChannelList.value
                } else {
                    controlsState.show(lifecycleScope)
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (showChannelList.value) {
                    cancelNumberInput(); showChannelList.value = false; controlsState.show(lifecycleScope)
                } else {
                    cancelNumberInput()
                    player?.let {
                        if (errorMessage.value.isNotBlank() || it.playbackState == Player.STATE_ENDED) retryPlayback()
                        else if (it.isPlaying) it.pause() else it.play()
                    }
                    controlsState.show(lifecycleScope)
                }
                true
            }
            android.view.KeyEvent.KEYCODE_CHANNEL_UP,
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
            android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                if (showChannelList.value) { cancelNumberInput(); showChannelList.value = false; return true }
                clearNumberTyping()
                if (contentType == ContentType.EVENT) {
                    if (allEventLinks.size > 1) {
                        val next = (currentLinkIndex + 1).coerceAtMost(allEventLinks.size - 1)
                        if (next != currentLinkIndex) switchToLink(allEventLinks[next], next)
                    }
                    controlsState.show(lifecycleScope); return true
                }
                val items = viewModel.channelListItems.value
                if (contentType == ContentType.CHANNEL) {
                    if (!items.isNullOrEmpty()) {
                        val currentIndex = if (pendingChannelIndex != -1) pendingChannelIndex
                            else items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val nextIndex = (currentIndex + 1).coerceAtMost(items.size - 1)
                        pendingChannelIndex = nextIndex
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        showChannelOverlay((nextIndex + 1).toString(), items[nextIndex])
                    } else {
                        pendingChannelDirection = +1
                        showChannelOverlay("...", null)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 4000)
                    }
                    controlsState.show(lifecycleScope)
                }
                true
            }
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (showChannelList.value) { cancelNumberInput(); showChannelList.value = false; return true }
                clearNumberTyping()
                if (contentType == ContentType.EVENT) {
                    if (allEventLinks.size > 1) {
                        val prev = (currentLinkIndex - 1).coerceAtLeast(0)
                        if (prev != currentLinkIndex) switchToLink(allEventLinks[prev], prev)
                    }
                    controlsState.show(lifecycleScope); return true
                }
                val items = viewModel.channelListItems.value
                if (contentType == ContentType.CHANNEL) {
                    if (!items.isNullOrEmpty()) {
                        val currentIndex = if (pendingChannelIndex != -1) pendingChannelIndex
                            else items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                        pendingChannelIndex = prevIndex
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        showChannelOverlay((prevIndex + 1).toString(), items[prevIndex])
                    } else {
                        pendingChannelDirection = -1
                        showChannelOverlay("...", null)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 4000)
                    }
                    controlsState.show(lifecycleScope)
                }
                true
            }
            android.view.KeyEvent.KEYCODE_0,
            android.view.KeyEvent.KEYCODE_1, android.view.KeyEvent.KEYCODE_2,
            android.view.KeyEvent.KEYCODE_3, android.view.KeyEvent.KEYCODE_4,
            android.view.KeyEvent.KEYCODE_5, android.view.KeyEvent.KEYCODE_6,
            android.view.KeyEvent.KEYCODE_7, android.view.KeyEvent.KEYCODE_8,
            android.view.KeyEvent.KEYCODE_9 -> {
                if (!DeviceUtils.isBigScreenLayout) return super.onKeyDown(keyCode, event)
                if (event?.repeatCount != 0 || showChannelList.value) return true
                pendingChannelDirection = 0
                channelNumberInput += (keyCode - android.view.KeyEvent.KEYCODE_0).toString()
                channelNumberHandler.removeCallbacks(overlayHideRunnable)
                showChannelOverlay(channelNumberInput, null)
                channelNumberHandler.removeCallbacks(channelNumberRunnable)
                channelNumberHandler.postDelayed(channelNumberRunnable, 2000)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    private fun showChannelOverlay(number: String, channel: Channel?) {
        showChannelOverlayState.value = null
    }
    private fun navigateToChannelByNumber() {
        val number = channelNumberInput.toIntOrNull()
        channelNumberInput = ""
        if (number == null || number <= 0 || contentType != ContentType.CHANNEL) {
            showChannelOverlayState.value = null; return
        }
        val items = viewModel.channelListItems.value
        if (items.isNullOrEmpty()) {
            pendingChannelNumber = number
            showChannelOverlay("...", null)
            viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
            channelNumberHandler.postDelayed(overlayHideRunnable, 3000)
            return
        }
        val index = number - 1
        showChannelOverlayState.value = null
        if (index in items.indices) {
            switchToChannel(items[index])
            showChannelOverlay((index + 1).toString(), items[index])
            channelNumberHandler.removeCallbacks(overlayHideRunnable)
            channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
        }
    }
    private fun cancelNumberInput() {
        channelNumberInput      = ""
        pendingChannelIndex     = -1
        pendingChannelDirection = 0
        pendingChannelNumber    = -1
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)
        showChannelOverlayState.value = null
    }
    private fun clearNumberTyping() {
        channelNumberInput = ""
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)
    }
    internal fun cycleAspectRatio() {
        val isLandscape = DeviceUtils.isBigScreenLayout ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val next = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT         -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM        -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL        -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else                                           -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        resizeMode = next
        if (contentType != ContentType.NETWORK_STREAM && preferencesManager.isRememberAspectRatioEnabled()) {
            if (isLandscape) { networkLandscapeResizeMode = next; preferencesManager.setSavedAspectRatio(next) }
            else             { networkPortraitResizeMode  = next; preferencesManager.setSavedAspectRatioPortrait(next) }
        }
    }
    internal fun applyRememberedAspectRatioIfEnabled() {
        if (aspectRatioAppliedForCurrentLoad) return
        aspectRatioAppliedForCurrentLoad = true
        if (!resizeModesRestoredFromState) {
            if (contentType != ContentType.NETWORK_STREAM && preferencesManager.isRememberAspectRatioEnabled()) {
                val savedL = preferencesManager.getSavedAspectRatio()
                val savedP = preferencesManager.getSavedAspectRatioPortrait()
                if (savedL != -1) networkLandscapeResizeMode = savedL
                if (savedP != -1) networkPortraitResizeMode  = savedP
                resizeModesRestoredFromState = true
            }
        }
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        resizeMode = if (isLandscape) networkLandscapeResizeMode else networkPortraitResizeMode
    }
    internal fun toggleFullscreen() {
        if (DeviceUtils.isTvDevice) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    private fun parseIntent(): Boolean {
        return try {
            parseIntentInternal()
            true
        } catch (t: Throwable) {
            android.widget.Toast.makeText(this, "Unable to play this channel", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            false
        }
    }

    private fun parseIntentInternal() {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val rawUri     = intent.data!!.toString()
            val decodedUri = android.net.Uri.decode(rawUri)
            val parsed     = PlayerStreamHelper.parseStreamUrl(decodedUri)
            contentType  = ContentType.NETWORK_STREAM
            contentName  = intent.data!!.lastPathSegment ?: "Stream"
            contentId    = "external_${System.currentTimeMillis()}"
            streamUrl    = decodedUri
            allEventLinks = listOf(LiveEventLink(
                quality       = "Auto", url = parsed.url,
                cookie        = parsed.headers["Cookie"] ?: "",
                referer       = parsed.headers["Referer"] ?: "",
                origin        = parsed.headers["Origin"] ?: "",
                userAgent     = parsed.headers["User-Agent"] ?: "Default",
                xForwardedFor = parsed.headers["X-Forwarded-For"],
                drmScheme     = parsed.drmScheme ?: "",
                drmLicenseUrl = parsed.drmLicenseUrl ?: parsed.drmKeyId?.let { id ->
                    parsed.drmKey?.let { k -> "$id:$k" }
                } ?: "",
                customHeaders = parsed.customHeaders,
            ))
            currentLinkIndex = 0; return
        }
        if (intent.getBooleanExtra("IS_NETWORK_STREAM", false)) {
            contentType = ContentType.NETWORK_STREAM
            contentName = intent.getStringExtra("CHANNEL_NAME") ?: "Network Stream"
            contentId   = "network_stream_${System.currentTimeMillis()}"
            val rawUrl  = intent.getStringExtra("STREAM_URL") ?: ""
            if (rawUrl.contains("|")) {
                val parsed     = PlayerStreamHelper.parseStreamUrl(rawUrl)
                val mergedLink = LiveEventLink(
                    quality = "Network Stream", url = parsed.url,
                    cookie  = parsed.headers["Cookie"] ?: "",
                    referer = parsed.headers["Referer"] ?: "",
                    origin  = parsed.headers["Origin"] ?: "",
                    userAgent     = parsed.headers["User-Agent"] ?: intent.getStringExtra("USER_AGENT") ?: "Default",
                    xForwardedFor = parsed.headers["X-Forwarded-For"],
                    drmScheme     = parsed.drmScheme ?: intent.getStringExtra("DRM_SCHEME"),
                    drmLicenseUrl = parsed.drmLicenseUrl ?: parsed.drmKeyId?.let { id ->
                        parsed.drmKey?.let { k -> "$id:$k" }
                    },
                    customHeaders = parsed.customHeaders,
                )
                allEventLinks = listOf(mergedLink)
                streamUrl = PlayerStreamHelper.buildStreamUrl(mergedLink)
            } else {
                val link = LiveEventLink(
                    quality   = "Network Stream", url = rawUrl,
                    cookie    = intent.getStringExtra("COOKIE") ?: "",
                    referer   = intent.getStringExtra("REFERER") ?: "",
                    origin    = intent.getStringExtra("ORIGIN") ?: "",
                    userAgent = intent.getStringExtra("USER_AGENT") ?: "Default",
                    drmScheme = intent.getStringExtra("DRM_SCHEME") ?: "clearkey",
                    drmLicenseUrl = intent.getStringExtra("DRM_LICENSE") ?: "",
                )
                allEventLinks = listOf(link)
                streamUrl = PlayerStreamHelper.buildStreamUrl(link)
            }
            currentLinkIndex = 0; return
        }
        channelData = intent.parcelableExtra<Channel>(EXTRA_CHANNEL)
        eventData   = intent.parcelableExtra<LiveEvent>(EXTRA_EVENT)
        val passedLinkIndex  = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)
        intentCategoryId     = intent.getStringExtra(EXTRA_CATEGORY_ID)
        intentSelectedGroup  = intent.getStringExtra(EXTRA_SELECTED_GROUP)
        intentIsSports       = intent.getBooleanExtra(EXTRA_IS_SPORTS, false)
        when {
            channelData != null -> {
                val ch = channelData!!
                contentType = ContentType.CHANNEL
                contentId   = ch.id
                contentName = ch.name
                if (!ch.links.isNullOrEmpty()) {
                    allEventLinks = ch.links.map { it.toLiveEventLink() }
                    currentLinkIndex = when {
                        passedLinkIndex in allEventLinks.indices -> passedLinkIndex
                        else -> allEventLinks.indexOfFirst { it.url == ch.streamUrl }.takeIf { it != -1 } ?: 0
                    }
                    streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
                } else {
                    allEventLinks = emptyList()
                    streamUrl     = ch.streamUrl
                }
            }
            eventData != null -> {
                val ev = eventData!!
                contentType = ContentType.EVENT
                contentId   = ev.id
                contentName = ev.title.ifEmpty { "${ev.team1Name} vs ${ev.team2Name}" }
                allEventLinks = ev.links
                if (allEventLinks.isNotEmpty()) {
                    currentLinkIndex = if (passedLinkIndex in allEventLinks.indices) passedLinkIndex else 0
                    streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
                } else { currentLinkIndex = 0; streamUrl = "" }
            }
            else -> { finish(); return }
        }
        updateNowPlayingState()
    }
    private var relatedChannels = listOf<Channel>()

    private fun setupRelatedChannels() {
        if (contentType == ContentType.NETWORK_STREAM || DeviceUtils.isTvDevice) {
            relatedContentState.value = RelatedContentState.Hidden; return
        }
        relatedContentState.value = RelatedContentState.Loading
    }

    private fun setupLinksUI() { updateLinksState() }
    private fun updateLinksState() {
        linksState.value        = if (isInPipMode || allEventLinks.size <= 1) emptyList() else allEventLinks
        selectedLinkState.value = currentLinkIndex
    }
    private fun updateLinksForOrientation(isLandscape: Boolean) { updateLinksState() }
    private fun setupMessageBanner() {
        val message = listenerManager.getMessage()
        if (message.isNotBlank()) {
            messageBannerText.value = message
            messageBannerUrl.value  = listenerManager.getMessageUrl()
        }
    }
    private fun loadRelatedContent() {
        if (DeviceUtils.isTvDevice) return
        when (contentType) {
            ContentType.CHANNEL -> {
                channelData?.let { ch ->
                    val passedRelated  = intent.getStringExtra(EXTRA_RELATED_CHANNELS_KEY)?.let { ChannelListCache.get(it) }
                    val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
                    val isFavSrc       = channelListKey?.startsWith("favorites_") == true
                    when {
                        !passedRelated.isNullOrEmpty() -> {
                            val related = passedRelated.filter { it.id != ch.id }.take(9)
                            relatedChannelsLockedForContentId = contentId
                            relatedChannels = related
                            relatedContentState.value = if (related.isEmpty()) RelatedContentState.Hidden
                                                        else RelatedContentState.Channels(related)
                        }
                        isFavSrc -> {
                            val favList = ChannelListCache.get(channelListKey!!) ?: emptyList()
                            viewModel.setChannelList(favList)
                            val related = favList.filter { it.id != ch.id }.shuffled().take(9)
                            relatedChannelsLockedForContentId = contentId
                            relatedChannels = related
                            relatedContentState.value = if (related.isEmpty()) RelatedContentState.Hidden
                                                        else RelatedContentState.Channels(related)
                        }
                        intentIsSports -> {
                            relatedChannelsLockedForContentId = null
                            viewModel.loadRandomRelatedSports(ch.id)
                        }
                        else -> {
                            relatedChannelsLockedForContentId = null
                            viewModel.loadRandomRelatedChannels(
                                intentCategoryId?.takeIf { it.isNotEmpty() } ?: ch.categoryId, ch.id, intentSelectedGroup)
                        }
                    }
                }
            }
            ContentType.EVENT -> eventData?.let { viewModel.loadRelatedEvents(it.id) }
            else -> {}
        }
    }
    private var playerSetupInProgress = false
    private fun setupPlayer() {
        if (streamUrl.isBlank()) { errorMessage.value = "No stream URL"; return }
        if (player != null || playerSetupInProgress) return
        aspectRatioAppliedForCurrentLoad = false
        playerSetupInProgress = true
        lifecycleScope.launch {
            val parsed = allEventLinks.getOrNull(currentLinkIndex)
                ?.let { PlayerStreamHelper.buildStreamInfoFromLink(it) }
                ?: PlayerStreamHelper.parseStreamUrl(streamUrl)
            val headers = parsed.headers.toMutableMap()
            if (headers["User-Agent"].isNullOrBlank() || headers["User-Agent"] == "Default") {
                headers["User-Agent"] = "okhttp/4.12.0"
            }
            val mimeType = PlayerStreamHelper.detectMimeTypeFromUrl(parsed.url)
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
            val clearKeyMgr = when {
                parsed.drmScheme != "clearkey" -> null
                parsed.drmJwk != null ->
                    PlayerStreamHelper.buildClearKeyJwkManager(parsed.drmJwk)
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
                DefaultMediaSourceFactory(this@PlayerActivity)
                    .setDataSourceFactory(effectiveDataSourceFactory)
                    .setDrmSessionManagerProvider { clearKeyMgr }
            } else {
                DefaultMediaSourceFactory(this@PlayerActivity).setDataSourceFactory(dataSourceFactory)
            }

            val renderersFactory = DefaultRenderersFactory(this@PlayerActivity)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            val ts = DefaultTrackSelector(this@PlayerActivity).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            trackSelector = ts
            val exo = try {
                ExoPlayer.Builder(this@PlayerActivity)
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
                        true,
                    )
                    .build()
            } catch (t: Throwable) {
                try {
                    val safeRenderersFactory = DefaultRenderersFactory(this@PlayerActivity)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    ExoPlayer.Builder(this@PlayerActivity)
                        .setRenderersFactory(safeRenderersFactory)
                        .setTrackSelector(ts)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setWakeMode(C.WAKE_MODE_NETWORK)
                        .build()
                } catch (t2: Throwable) {
                    errorMessage.value = "Unable to start playback on this device"
                    playerSetupInProgress = false
                    return@launch
                }
            }
            player = exo
            com.livetvpro.app.utils.VolumeBoostHelper.attach(exo, preferencesManager)
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
                        .setMultiSession(true)
                        .build()
                )
            }
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> errorMessage.value = ""
                        Player.STATE_READY -> {
                            errorMessage.value = ""
                            applyRememberedAspectRatioIfEnabled()
                        }
                        else -> {}
                    }
                    refreshPipParamsIfNeeded()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    refreshPipParamsIfNeeded()
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (preferencesManager.isAutoSwitchStreamEnabled() && contentType == ContentType.EVENT && allEventLinks.size > 1) {
                        val nextIndex = currentLinkIndex + 1
                        if (nextIndex in allEventLinks.indices) {
                            switchToLink(allEventLinks[nextIndex], nextIndex)
                            return
                        }
                    }
                    errorMessage.value = error.localizedMessage ?: "Playback error"
                    refreshPipParamsIfNeeded()
                }
            }
            playerListener = listener
            exo.addListener(listener)
            exo.setMediaItem(mediaItemBuilder.build())
            exo.prepare()
            exo.playWhenReady = true
        }
    }
    private fun releasePlayer() {
        player?.let {
            try { playerListener?.let { l -> it.removeListener(l) }; com.livetvpro.app.utils.VolumeBoostHelper.release(it); it.stop(); it.release() }
            catch (_: Throwable) {}
        }
        player         = null
        playerListener = null
        playerSetupInProgress = false
    }
    internal fun retryPlayback() {
        errorMessage.value = ""
        player?.let { com.livetvpro.app.utils.VolumeBoostHelper.release(it) }
        player?.release()
        player = null
        playerSetupInProgress = false
        setupPlayer()
    }
    internal fun switchToChannel(newChannel: Channel, linkIndex: Int = -1) {
        try {
            releasePlayer()
            channelData = newChannel; eventData = null
            contentType = ContentType.CHANNEL
            contentId   = newChannel.id
            contentName = newChannel.name
            if (!newChannel.links.isNullOrEmpty()) {
                allEventLinks    = newChannel.links.map { it.toLiveEventLink() }
                currentLinkIndex = if (linkIndex in allEventLinks.indices) linkIndex else 0
                streamUrl        = allEventLinks.getOrNull(currentLinkIndex)
                    ?.let { PlayerStreamHelper.buildStreamUrl(it) } ?: newChannel.streamUrl
            } else {
                allEventLinks = emptyList(); streamUrl = newChannel.streamUrl
            }
            setupPlayer(); setupLinksUI()
            updateNowPlayingState()
            refreshPipParamsIfNeeded()
            val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val isFavSrc       = channelListKey?.startsWith("favorites_") == true
            if (isFavSrc) {
                val fav = ChannelListCache.get(channelListKey!!) ?: emptyList()
                viewModel.setChannelList(fav)
                val updated = fav.filter { it.id != newChannel.id }.shuffled().take(9)
                relatedChannels = updated
                relatedChannelsLockedForContentId = contentId
                relatedContentState.value = if (updated.isEmpty()) RelatedContentState.Hidden
                                            else RelatedContentState.Channels(updated)
            } else {
                relatedChannelsLockedForContentId = null
                val isSports   = newChannel.categoryId == "sports" || intentIsSports
                val categoryId = newChannel.categoryId.takeIf { it.isNotEmpty() } ?: intentCategoryId ?: ""
                viewModel.loadAllChannelsForList(categoryId, newChannel.id)
                if (isSports) viewModel.loadRandomRelatedSports(newChannel.id)
                else viewModel.loadRandomRelatedChannels(categoryId, newChannel.id, intentSelectedGroup)
            }
        } catch (t: Throwable) {
            errorMessage.value = "Unable to play this channel"
        }
    }
    private fun switchToEventFromLiveEvent(newEvent: LiveEvent, linkIndex: Int = 0) {
        try {
            releasePlayer()
            eventData = newEvent; channelData = null
            contentType = ContentType.EVENT
            contentId   = newEvent.id
            contentName = newEvent.title.ifEmpty { "${newEvent.team1Name} vs ${newEvent.team2Name}" }
            allEventLinks = newEvent.links
            if (allEventLinks.isNotEmpty()) {
                currentLinkIndex = if (linkIndex in allEventLinks.indices) linkIndex else 0
                streamUrl = PlayerStreamHelper.buildStreamUrl(allEventLinks[currentLinkIndex])
            } else { currentLinkIndex = 0; streamUrl = "" }
            setupPlayer(); setupLinksUI()
            updateNowPlayingState()
            refreshPipParamsIfNeeded()
            relatedContentState.value = RelatedContentState.Loading
            viewModel.loadRelatedEvents(newEvent.id)
        } catch (_: Exception) {}
    }
    private fun updateNowPlayingState() {
        when (contentType) {
            ContentType.CHANNEL -> {
                val ch = channelData
                if (ch == null) {
                    nowPlayingState.value = NowPlayingState.Hidden
                } else {
                    nowPlayingState.value = NowPlayingState.ChannelInfo(channel = ch, isFavorite = false)
                    lifecycleScope.launch {
                        val fav     = favoritesRepository.isFavorite(ch.id)
                        val current = nowPlayingState.value
                        if (current is NowPlayingState.ChannelInfo && current.channel.id == ch.id) {
                            nowPlayingState.value = current.copy(isFavorite = fav)
                        }
                    }
                }
            }
            ContentType.EVENT -> {
                val ev = eventData
                if (ev == null) {
                    nowPlayingState.value = NowPlayingState.Hidden
                } else {
                    val sourceName = allEventLinks.getOrNull(currentLinkIndex)?.quality.orEmpty()
                    nowPlayingState.value = NowPlayingState.EventInfo(
                        title      = ev.league,
                        team1Name  = ev.team1Name,
                        team1Logo  = ev.team1Logo,
                        team2Name  = ev.team2Name,
                        team2Logo  = ev.team2Logo,
                        sourceName = sourceName,
                        startTime  = ev.startTime,
                        endTime    = ev.endTime,
                        isLive     = ev.isLive,
                    )
                }
            }
            ContentType.NETWORK_STREAM -> nowPlayingState.value = NowPlayingState.Hidden
        }
    }
    internal fun toggleNowPlayingFavorite(channel: Channel) {
        lifecycleScope.launch {
            val isFav = favoritesRepository.isFavorite(channel.id)
            if (isFav) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                favoritesRepository.addFavorite(
                    com.livetvpro.app.data.models.FavoriteChannel(
                        id           = channel.id,
                        name         = channel.name,
                        logoUrl      = channel.logoUrl,
                        streamUrl    = channel.streamUrl,
                        categoryId   = channel.categoryId,
                        categoryName = channel.categoryName,
                        links        = channel.links,
                    )
                )
            }
            val current = nowPlayingState.value
            if (current is NowPlayingState.ChannelInfo && current.channel.id == channel.id) {
                nowPlayingState.value = current.copy(isFavorite = !isFav)
            }
        }
    }
    internal fun switchToLink(link: LiveEventLink, position: Int) {
        currentLinkIndex        = position
        selectedLinkState.value = position
        streamUrl               = PlayerStreamHelper.buildStreamUrl(link)
        releasePlayer()
        setupPlayer()
        updateNowPlayingState()
        refreshPipParamsIfNeeded()
    }
    internal fun showSettingsDialog() {
        if (player == null || isFinishing || isDestroyed || isShowingSettingsDialog) return
        isShowingSettingsDialog  = true
        showSettingsDialog.value = true
    }
    internal fun toggleMute() {
        isMuted = PlayerStreamHelper.toggleMute(player, isMuted)
    }
    override fun finish() {
        try {
            releasePlayer()
            unregisterPipReceiver()
            isInPipMode        = false
            wasLockedBeforePip = false
            isInPip            = false
            super.finish()
        } catch (_: Exception) { super.finish() }
    }
}
private inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)
private inline fun <reified T : Parcelable> Bundle.parcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelable(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelable(key)
private inline fun <reified T : Parcelable> Bundle.parcelableArrayListCompat(key: String): List<T>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayList(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableArrayList<T>(key)
private fun com.livetvpro.app.data.models.ChannelLink.toLiveEventLink() = com.livetvpro.app.data.models.LiveEventLink(
    quality       = quality,
    url           = url,
    cookie        = cookie,
    referer       = referer,
    origin        = origin,
    userAgent     = userAgent,
    xForwardedFor = xForwardedFor,
    drmScheme     = drmScheme,
    drmLicenseUrl = drmLicenseUrl,
    customHeaders = customHeaders,
    drmJwk        = drmJwk,
)

