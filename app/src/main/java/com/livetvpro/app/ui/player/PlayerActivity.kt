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
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
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

    internal val viewModel: PlayerViewModel by viewModels()

    private val relatedContentState = mutableStateOf<RelatedContentState>(RelatedContentState.Hidden)
    private val linksState          = mutableStateOf<List<LiveEventLink>>(emptyList())
    private val selectedLinkState   = mutableStateOf(0)
    private val messageBannerText   = mutableStateOf("")
    private val messageBannerUrl    = mutableStateOf("")
    private val showSettingsDialog  = mutableStateOf(false)
    private val showFloatingDialog  = mutableStateOf(false)
    internal val errorMessage       = mutableStateOf("")
    internal val isLandscapeState   = mutableStateOf(false)
    private var relatedChannels     = listOf<Channel>()

    private var showChannelList     = mutableStateOf(false)

    
    private var player: ExoPlayer? by mutableStateOf(null)

    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    private val controlsState = PlayerControlsState()

    internal var gestureVolume: Int = 100
    internal var gestureBrightness: Int = 0
    internal var isInPipMode by mutableStateOf(false)
    private var isEnteringPip = false
    private var isMuted by mutableStateOf(false)
    internal val skipMs = 10_000L

    private var resizeMode by mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var networkPortraitResizeMode  = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    private var resizeModesRestoredFromState = false

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private var pipReceiver: BroadcastReceiver? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var isScreenOff = false
    private var wasLockedBeforePip = false
    private var isShowingSettingsDialog = false
    val isPipSupported by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) false
        else packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    internal var contentType: ContentType = ContentType.CHANNEL
    private var channelNumberInput  = ""
    private val channelNumberHandler = Handler(Looper.getMainLooper())
    private val channelNumberRunnable = Runnable { navigateToChannelByNumber() }
    private val showChannelOverlayState = mutableStateOf<Pair<String, String>?>(null)
    private val overlayHideRunnable = Runnable { showChannelOverlayState.value = null }
    private var pendingChannelIndex    = -1
    private var pendingChannelDirection = 0
    private var pendingChannelNumber   = -1
    private var channelData: Channel?   = null
    private var eventData: LiveEvent?   = null
    internal var allEventLinks = listOf<LiveEventLink>()
    internal var currentLinkIndex = 0
    internal var contentId:   String = ""
    internal var contentName: String by mutableStateOf("")
    private var streamUrl: String = ""
    private var intentCategoryId:   String? = null
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

        private const val ACTION_MEDIA_CONTROL  = "com.livetvpro.app.MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE    = "control_type"
        private const val CONTROL_TYPE_PLAY     = 1
        private const val CONTROL_TYPE_PAUSE    = 2
        private const val CONTROL_TYPE_REWIND   = 3
        private const val CONTROL_TYPE_FORWARD  = 4
        private const val CONTROL_TYPE_PREV_CHANNEL = 5
        private const val CONTROL_TYPE_NEXT_CHANNEL = 6

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
        val newChannel = intent.parcelableExtra<Channel>(EXTRA_CHANNEL)
        val newEvent   = intent.parcelableExtra<LiveEvent>(EXTRA_EVENT)
        val linkIndex  = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)
        when {
            newChannel != null -> switchToChannel(newChannel, linkIndex)
            newEvent   != null -> switchToEventFromLiveEvent(newEvent, linkIndex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager.registerActivityContext(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (DeviceUtils.isTvDevice) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val isLandscape = DeviceUtils.isTvDevice ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        setupWindowFlags(isLandscape)
        setupSystemUI()

        parseIntent()

        if (contentType == ContentType.CHANNEL && contentId.isNotEmpty()) {
            val cacheKey   = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val cachedList = cacheKey?.let { ChannelListCache.get(it) }
            if (!cachedList.isNullOrEmpty()) {
                viewModel.setChannelList(cachedList)
                viewModel.refreshChannelData(contentId)
            } else {
                viewModel.loadAllChannelsForList(
                    categoryId      = intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "",
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
        val isLandscape     = isLandscapeState.value
        val spanCount       = resources.getInteger(R.integer.grid_column_count)
        val eventSpanCount  = resources.getInteger(R.integer.event_span_count)

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

        val scope             = rememberCoroutineScope()
        val channelListItems  by viewModel.channelListItems.observeAsState(emptyList())
        val isChannelListAvailable = contentType == ContentType.CHANNEL &&
            channelListItems.isNotEmpty() &&
            (isLandscape || DeviceUtils.isTvDevice)

        val isPipEnabled = !DeviceUtils.isTvDevice &&
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            else false

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
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (vol / 100f * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0,
            )
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
                isPlaying              = isPlaying,
                isMuted                = isMuted,
                currentPosition        = currentPosition,
                duration               = duration,
                bufferedPosition       = bufferedPosition,
                channelName            = contentName,
                showPipButton          = isPipEnabled,
                showAspectRatioButton  = true,
                isLandscape            = isLandscape,
                isTvMode               = DeviceUtils.isTvDevice,
                centerControlsMode     = preferencesManager.getCenterControlsMode(),
                isNetworkStream        = contentType == ContentType.NETWORK_STREAM,
                isChannelListAvailable = isChannelListAvailable,
                onBackClick            = { finish() },
                onPipClick             = { enterPipMode() },
                onSettingsClick        = { showSettingsDialog() },
                onMuteClick            = { toggleMute() },
                onLockClick            = { locked -> if (locked) controlsState.lock() else controlsState.unlock(scope) },
                onChannelListClick     = { showChannelList.value = true },
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

        Column(modifier = Modifier
            .fillMaxSize()
            .then(if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
        ) {
            if (!isLandscape) {
                val isNetworkStream = contentType == ContentType.NETWORK_STREAM
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isNetworkStream) Modifier.weight(1f)
                            else Modifier.aspectRatio(16f / 9f)
                        ),
                ) {
                    PlayerSurface(
                        player      = player,
                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                        modifier    = Modifier.fillMaxSize(),
                    )

                    if (!isInPipMode) {
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
                    PlayerSurface(
                        player      = player,
                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                        modifier    = Modifier.fillMaxSize(),
                    )

                    if (!isInPipMode) {
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

                        Controls(isLandscape = true)

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
                                    onEventClick         = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                                    onMessageBannerClick = {},
                                    spanCount            = spanCount,
                                    eventSpanCount       = eventSpanCount,
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
                    onChannelClick       = { switchToChannel(it) },
                    onEventClick         = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                    onMessageBannerClick = {
                        val url = messageBannerUrl.value
                        if (url.isNotBlank()) {
                            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            catch (_: Exception) {}
                        }
                    },
                    spanCount       = spanCount,
                    eventSpanCount  = eventSpanCount,
                )
            }
        }

        if (!isInPipMode && showSettingsDialog.value && player != null) {
            PlayerSettingsDialog(
                player    = player!!,
                onDismiss = { showSettingsDialog.value = false; isShowingSettingsDialog = false },
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
        if (!isLandscape) window.decorView.setBackgroundColor(themeManager.getBackgroundColor(this))
        val isDark = themeManager.isDarkMode(this)
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
                hide(WindowInsetsCompat.Type.navigationBars())
                show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

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
        outState.putInt("SAVE_LINK_INDEX",     currentLinkIndex)
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
        intentCategoryId   = savedInstanceState.getString("SAVE_CATEGORY_ID")
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
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)
        setupWindowFlags(isLandscape)
        setupSystemUI()
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (player == null) setupPlayer()
        if (contentType == ContentType.CHANNEL && viewModel.channelListItems.value.isNullOrEmpty()) {
            val channelListKey    = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val isFavoritesSource = channelListKey == "favorites_session"
            if (isFavoritesSource) {
                ChannelListCache.get("favorites_session")?.let { viewModel.setChannelList(it) }
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let { unregisterReceiver(it); pipReceiver = null }
            isInPipMode     = false
            isEnteringPip   = false
            isInPip         = false
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
        updateLinksState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPictureInPictureParams(buildPipParams(enter = true))
        controlsState.hide()
        setupPipReceiver()
        super.onPictureInPictureModeChanged(true, newConfig)
    }

    @SuppressLint("NewApi")
    override fun onUserLeaveHint() {
        val isForegrounded = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (!DeviceUtils.isTvDevice && isPipSupported && player?.isPlaying == true && isForegrounded && !isFinishing) {
            isEnteringPip    = true
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
        val showNav = preferencesManager.getCenterControlsMode() ==
            PreferencesManager.CENTER_MODE_NAV_ONLY && contentType == ContentType.CHANNEL
        return if (showNav) listOf(
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_prev_channel),
                "Previous", "Previous channel", makePendingIntent(CONTROL_TYPE_PREV_CHANNEL, CONTROL_TYPE_PREV_CHANNEL)),
            playPauseAction,
            RemoteAction(Icon.createWithResource(context, R.drawable.ic_skip_next_channel),
                "Next", "Next channel", makePendingIntent(CONTROL_TYPE_NEXT_CHANNEL, CONTROL_TYPE_NEXT_CHANNEL)),
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
                val p = player ?: return
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = p.playbackState == Player.STATE_ENDED
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY   -> { if (hasError || hasEnded) retryPlayback() else p.play()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPictureInPictureParams(buildPipParams()) }
                    CONTROL_TYPE_PAUSE  -> { if (hasError || hasEnded) retryPlayback() else p.pause()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPictureInPictureParams(buildPipParams()) }
                    CONTROL_TYPE_REWIND  -> if (!hasError && !hasEnded) p.seekTo((p.currentPosition - skipMs).coerceAtLeast(0L))
                    CONTROL_TYPE_FORWARD -> if (!hasError && !hasEnded) {
                        val newPos = p.currentPosition + skipMs
                        if (p.isCurrentWindowLive && p.duration != C.TIME_UNSET && newPos >= p.duration) p.seekTo(p.duration)
                        else p.seekTo(newPos)
                    }
                    CONTROL_TYPE_PREV_CHANNEL -> {
                        if (contentType == ContentType.CHANNEL) {
                            val items = viewModel.channelListItems.value ?: return
                            val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                            val prev = (idx - 1).coerceAtLeast(0)
                            if (prev != idx) switchToChannel(items[prev])
                        }
                    }
                    CONTROL_TYPE_NEXT_CHANNEL -> {
                        if (contentType == ContentType.CHANNEL) {
                            val items = viewModel.channelListItems.value ?: return
                            val idx  = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                            val next = (idx + 1).coerceAtMost(items.size - 1)
                            if (next != idx) switchToChannel(items[next])
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
            code == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) {
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
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                val direction = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> +1
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
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
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
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                cancelNumberInput(); controlsState.show(lifecycleScope); true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                val items = viewModel.channelListItems.value
                val available = contentType == ContentType.CHANNEL && !items.isNullOrEmpty() &&
                    (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || DeviceUtils.isTvDevice)
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
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
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
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
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
                if (!DeviceUtils.isTvDevice) return super.onKeyDown(keyCode, event)
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
        channelNumberInput = ""
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
        val isLandscape = DeviceUtils.isTvDevice ||
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val next = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT         -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM        -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL        -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else                                           -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        resizeMode = next
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            if (isLandscape) { networkLandscapeResizeMode = next; preferencesManager.setSavedAspectRatio(next) }
            else             { networkPortraitResizeMode  = next; preferencesManager.setSavedAspectRatioPortrait(next) }
        }
    }

    internal fun showSettingsDialog() {
        if (player == null || isFinishing || isDestroyed || isShowingSettingsDialog) return
        isShowingSettingsDialog  = true
        showSettingsDialog.value = true
    }

    internal fun toggleMute() {
        isMuted = PlayerStreamHelper.toggleMute(player, isMuted)
    }

    internal fun toggleFullscreen() {
        if (DeviceUtils.isTvDevice) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun parseIntent() {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val rawUri     = intent.data!!.toString()
            val decodedUri = android.net.Uri.decode(rawUri)
            val parsed     = PlayerStreamHelper.parseStreamUrl(decodedUri)
            contentType  = ContentType.NETWORK_STREAM
            contentName  = intent.data!!.lastPathSegment ?: "Stream"
            contentId    = "external_${System.currentTimeMillis()}"
            streamUrl    = decodedUri
            allEventLinks = listOf(LiveEventLink(
                quality = "Auto", url = parsed.url,
                cookie = parsed.headers["Cookie"] ?: "", referer = parsed.headers["Referer"] ?: "",
                origin = parsed.headers["Origin"] ?: "", userAgent = parsed.headers["User-Agent"] ?: "Default",
                xForwardedFor = parsed.headers["X-Forwarded-For"],
                drmScheme = parsed.drmScheme ?: "",
                drmLicenseUrl = parsed.drmLicenseUrl ?: parsed.drmKeyId?.let { id -> parsed.drmKey?.let { k -> "$id:$k" } } ?: "",
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
                val parsed      = PlayerStreamHelper.parseStreamUrl(rawUrl)
                val mergedLink  = LiveEventLink(
                    quality = "Network Stream", url = parsed.url,
                    cookie  = parsed.headers["Cookie"] ?: "", referer = parsed.headers["Referer"] ?: "",
                    origin  = parsed.headers["Origin"] ?: "", userAgent = parsed.headers["User-Agent"] ?: intent.getStringExtra("USER_AGENT") ?: "Default",
                    xForwardedFor = parsed.headers["X-Forwarded-For"],
                    drmScheme = parsed.drmScheme ?: intent.getStringExtra("DRM_SCHEME"),
                    drmLicenseUrl = parsed.drmLicenseUrl,
                    customHeaders = parsed.customHeaders,
                )
                allEventLinks = listOf(mergedLink)
                streamUrl = PlayerStreamHelper.buildStreamUrl(mergedLink)
            } else {
                val link = LiveEventLink(
                    quality = "Network Stream", url = rawUrl,
                    cookie  = intent.getStringExtra("COOKIE") ?: "",
                    referer = intent.getStringExtra("REFERER") ?: "",
                    origin  = intent.getStringExtra("ORIGIN") ?: "",
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
        val passedLinkIndex = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)
        intentCategoryId    = intent.getStringExtra(EXTRA_CATEGORY_ID)
        intentSelectedGroup = intent.getStringExtra(EXTRA_SELECTED_GROUP)
        intentIsSports      = intent.getBooleanExtra(EXTRA_IS_SPORTS, false)

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
    }

    private fun setupRelatedChannels() {
        if (contentType == ContentType.NETWORK_STREAM || DeviceUtils.isTvDevice) {
            relatedContentState.value = RelatedContentState.Hidden; return
        }
        relatedContentState.value = RelatedContentState.Loading
    }

    private fun setupLinksUI() { updateLinksState() }

    private fun updateLinksState() {
        linksState.value      = if (isInPipMode || allEventLinks.size <= 1) emptyList() else allEventLinks
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
                    val passedRelated = intent.getStringExtra(EXTRA_RELATED_CHANNELS_KEY)?.let { ChannelListCache.get(it) }
                    val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
                    val isFavSrc = channelListKey == "favorites_session"
                    when {
                        !passedRelated.isNullOrEmpty() -> viewModel.setRelatedChannels(passedRelated.filter { it.id != ch.id })
                        isFavSrc -> viewModel.setRelatedChannels(ChannelListCache.get("favorites_session")?.filter { it.id != ch.id } ?: emptyList())
                        intentIsSports -> viewModel.loadRandomRelatedSports(ch.id)
                        else -> viewModel.loadRandomRelatedChannels(
                            intentCategoryId?.takeIf { it.isNotEmpty() } ?: ch.categoryId, ch.id, intentSelectedGroup)
                    }
                }
            }
            ContentType.EVENT -> eventData?.let { viewModel.loadRelatedEvents(it.id) }
            else -> {}
        }
    }

    private fun setupPlayer() {
        if (streamUrl.isBlank()) { errorMessage.value = "No stream URL"; return }
        lifecycleScope.launch {
            val parsed  = PlayerStreamHelper.parseStreamUrl(streamUrl)
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
                parsed.drmKeyId != null && parsed.drmKey != null ->
                    PlayerStreamHelper.buildClearKeyInlineManager(parsed.drmKeyId, parsed.drmKey)
                parsed.drmLicenseUrl?.trimStart()?.startsWith("{") == true ->
                    PlayerStreamHelper.buildClearKeyJwkManager(parsed.drmLicenseUrl)
                parsed.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true ->
                    PlayerStreamHelper.buildClearKeyServerManager(parsed.drmLicenseUrl, headers)
                else -> null
            }

            val mediaSourceFactory = if (clearKeyMgr != null) {
                DefaultMediaSourceFactory(this@PlayerActivity)
                    .setDataSourceFactory(dataSourceFactory)
                    .setDrmSessionManagerProvider { clearKeyMgr }
            } else {
                DefaultMediaSourceFactory(this@PlayerActivity).setDataSourceFactory(dataSourceFactory)
            }

            val renderersFactory = DefaultRenderersFactory(this@PlayerActivity)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            val ts = DefaultTrackSelector(this@PlayerActivity).apply {
                parameters = buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .build()
            }
            trackSelector = ts

            val exo = ExoPlayer.Builder(this@PlayerActivity)
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

            player = exo

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
                        Player.STATE_BUFFERING -> errorMessage.value = ""
                        Player.STATE_READY -> {
                            errorMessage.value = ""
                            if (!resizeModesRestoredFromState) {
                                val savedL = preferencesManager.getSavedAspectRatio()
                                val savedP = preferencesManager.getSavedAspectRatioPortrait()
                                if (savedL != -1) networkLandscapeResizeMode = savedL
                                if (savedP != -1) networkPortraitResizeMode  = savedP
                                resizeModesRestoredFromState = true
                                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                resizeMode = if (isLandscape) networkLandscapeResizeMode else networkPortraitResizeMode
                            }
                        }
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
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun releasePlayer() {
        player?.let {
            try { playerListener?.let { l -> it.removeListener(l) }; it.stop(); it.release() }
            catch (_: Throwable) {}
        }
        player         = null
        playerListener = null
    }

    internal fun retryPlayback() {
        errorMessage.value = ""
        player?.release()
        player = null
        setupPlayer()
    }

    internal fun switchToChannel(newChannel: Channel, linkIndex: Int = -1) {
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
        relatedContentState.value = RelatedContentState.Loading
        val channelListKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
        val isFavSrc       = channelListKey == "favorites_session"
        if (isFavSrc) {
            val fav = ChannelListCache.get("favorites_session") ?: emptyList()
            viewModel.setChannelList(fav)
            viewModel.setRelatedChannels(fav.filter { it.id != newChannel.id })
        } else {
            val isSports   = newChannel.categoryId == "sports" || intentIsSports
            val categoryId = newChannel.categoryId.takeIf { it.isNotEmpty() } ?: intentCategoryId ?: ""
            viewModel.loadAllChannelsForList(categoryId, newChannel.id)
            if (isSports) viewModel.loadRandomRelatedSports(newChannel.id)
            else viewModel.loadRandomRelatedChannels(categoryId, newChannel.id, intentSelectedGroup)
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
            relatedContentState.value = RelatedContentState.Loading
            viewModel.loadRelatedEvents(newEvent.id)
        } catch (_: Exception) {}
    }

    internal fun switchToLink(link: LiveEventLink, position: Int) {
        currentLinkIndex        = position
        selectedLinkState.value = position
        streamUrl               = PlayerStreamHelper.buildStreamUrl(link)
        releasePlayer()
        setupPlayer()
    }

    override fun finish() {
        try {
            releasePlayer()
            unregisterPipReceiver()
            isInPipMode = false
            wasLockedBeforePip = false
            isInPip = false
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
)
