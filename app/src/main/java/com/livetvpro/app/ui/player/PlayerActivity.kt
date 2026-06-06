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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Rational
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.LiveEventLink
import com.livetvpro.app.ui.player.compose.PlayerControls
import com.livetvpro.app.ui.player.compose.PlayerControlsState
import com.livetvpro.app.ui.player.compose.GestureState
import com.livetvpro.app.ui.player.compose.PlayerScreen
import com.livetvpro.app.ui.player.compose.RelatedContentState
import com.livetvpro.app.ui.player.compose.LandscapeLinksRow
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
import com.livetvpro.app.ui.player.settings.PlayerSettingsDialog
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.ui.player.ChannelListCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.media.AudioManager

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {
    private val relatedContentState = mutableStateOf<RelatedContentState>(RelatedContentState.Hidden)
    private val linksState          = mutableStateOf<List<LiveEventLink>>(emptyList())
    private val selectedLinkState   = mutableStateOf(0)
    private val messageBannerText   = mutableStateOf("")
    private val messageBannerUrl    = mutableStateOf("")
    private val showSettingsDialog  = mutableStateOf(false)
    private val showFloatingDialog  = mutableStateOf(false)
    private val errorMessage        = mutableStateOf("")
    private var relatedChannels = listOf<Channel>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    @javax.inject.Inject
    lateinit var themeManager: ThemeManager

    @javax.inject.Inject
    lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager

    @javax.inject.Inject
    lateinit var listenerManager: com.livetvpro.app.utils.NativeListenerManager
    private lateinit var playerViewRef: androidx.media3.ui.PlayerView
    private lateinit var playerContainer: androidx.constraintlayout.widget.ConstraintLayout

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private val controlsState = PlayerControlsState()
    private var showChannelList = mutableStateOf(false)
    private var gestureVolume: Int = 100
    private var gestureBrightness: Int = 0

    private var isInPipMode = false
    private var isEnteringPip = false
    private var isMuted by mutableStateOf(false)
    private val skipMs = 10_000L

    private var networkPortraitResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    private var resizeModesRestoredFromState = false

    private var pipReceiver: BroadcastReceiver? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var isScreenOff = false
    private var wasLockedBeforePip = false
    private var isShowingSettingsDialog = false
    private var pipRect: Rect? = null
    val isPipSupported by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            false
        } else {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }
    }

    private var contentType: ContentType = ContentType.CHANNEL
    private var channelNumberInput: String = ""
    private val channelNumberHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val channelNumberRunnable = Runnable { navigateToChannelByNumber() }
    private val showChannelOverlayState = mutableStateOf<Pair<String, String>?>(null)
    private val overlayHideRunnable = Runnable { showChannelOverlayState.value = null }
    private var pendingChannelIndex: Int = -1
    private var pendingChannelDirection: Int = 0
    private var pendingChannelNumber: Int = -1
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

    enum class ContentType {
        CHANNEL, EVENT, NETWORK_STREAM
    }

    companion object {
        private const val EXTRA_CHANNEL = "extra_channel"
        private const val EXTRA_EVENT = "extra_event"
        private const val EXTRA_SELECTED_LINK_INDEX = "extra_selected_link_index"
        private const val EXTRA_RELATED_CHANNELS_KEY = "extra_related_channels_key"
        private const val EXTRA_CATEGORY_ID = "extra_category_id"
        private const val EXTRA_IS_SPORTS = "extra_is_sports"
        private const val EXTRA_CHANNEL_LIST_KEY = "extra_channel_list_key"
        private const val EXTRA_SELECTED_GROUP = "extra_selected_group"

        private const val ACTION_MEDIA_CONTROL = "com.livetvpro.app.MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
        private const val CONTROL_TYPE_REWIND = 3
        private const val CONTROL_TYPE_FORWARD = 4
        private const val CONTROL_TYPE_PREV_CHANNEL = 5
        private const val CONTROL_TYPE_NEXT_CHANNEL = 6

        private const val PIP_INTENTS_FILTER = "com.livetvpro.app.PIP_CONTROL"
        private const val PIP_INTENT_ACTION = "pip_action"
        private const val PIP_PLAY = 1
        private const val PIP_PAUSE = 2
        private const val PIP_FR = 3
        private const val PIP_FF = 4

        var isInPip: Boolean = false

        fun startWithChannel(context: Context, channel: Channel, linkIndex: Int = -1, relatedChannels: ArrayList<Channel>? = null, categoryId: String? = null, selectedGroup: String? = null, isSports: Boolean = false, channelList: ArrayList<Channel>? = null, channelListCacheKey: String? = null) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, channel as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                relatedChannels?.let {
                    val key = "related_${channel.id}"
                    ChannelListCache.put(key, it)
                    putExtra(EXTRA_RELATED_CHANNELS_KEY, key)
                }
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
                if (isInPip) addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(0, 0)
            }
        }

        fun startWithEvent(context: Context, event: LiveEvent, linkIndex: Int = -1) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_EVENT, event as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (isInPip) addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(0, 0)
            }
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

        val newChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CHANNEL, Channel::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_CHANNEL)
        }
        val newEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EVENT, LiveEvent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_EVENT)
        }
        val linkIndex = intent.getIntExtra(EXTRA_SELECTED_LINK_INDEX, -1)

        when {
            newChannel != null -> switchToChannel(newChannel, linkIndex)
            newEvent != null   -> switchToEventFromLiveEvent(newEvent, linkIndex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition()
        }

        val binding = androidx.compose.ui.platform.ComposeView(this).also { composeRoot ->
            composeRoot.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            composeRoot.setContent {
                LiveTVProTheme(themeManager) {
                    PlayerActivityRoot(
                        activity = this,
                        controlsState = controlsState,
                        showChannelList = showChannelList,
                        isMuted = isMuted,
                        relatedContentState = relatedContentState.value,
                        links = linksState.value,
                        selectedLinkIndex = selectedLinkState.value,
                        messageBanner = messageBannerText.value,
                        messageBannerUrl = messageBannerUrl.value,
                        errorMessage = errorMessage.value,
                        showSettingsDialog = showSettingsDialog.value,
                        showFloatingDialog = showFloatingDialog.value,
                        player = player,
                        preferencesManager = preferencesManager,
                        onSettingsDismiss = { showSettingsDialog.value = false; isShowingSettingsDialog = false },
                        onFloatingDismiss = { showFloatingDialog.value = false },
                        onLinkClick = { link, idx -> switchToLink(link, idx) },
                        onChannelClick = { switchToChannel(it) },
                        onEventClick = { event, linkIdx -> switchToEventFromLiveEvent(event, linkIdx) },
                        onMessageBannerClick = {
                            val url = messageBannerUrl.value
                            if (url.isNotBlank()) {
                                try {
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                } catch (_: Exception) {}
                            }
                        },
                    )
                }
            }
        }
        val rootLayout = android.widget.FrameLayout(this).apply {
            addView(
                android.view.LayoutInflater.from(this@PlayerActivity)
                    .inflate(R.layout.activity_player, null),
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )
            addView(
                (binding as androidx.compose.ui.platform.ComposeView),
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )
        }
        setContentView(rootLayout)
        playerViewRef = rootLayout.findViewById(R.id.player_view)
        playerContainer = rootLayout.findViewById(R.id.player_container)

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (DeviceUtils.isTvDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        val currentOrientation = resources.configuration.orientation
        val isLandscape = DeviceUtils.isTvDevice || currentOrientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)
        setupWindowInsets()

        parseIntent()

        if (contentType == ContentType.CHANNEL && contentId.isNotEmpty()) {
            val cacheKey = intent.getStringExtra(EXTRA_CHANNEL_LIST_KEY)
            val cachedList = cacheKey?.let { ChannelListCache.get(it) }
            if (!cachedList.isNullOrEmpty()) {

                viewModel.setChannelList(cachedList)
                viewModel.refreshChannelData(contentId)
            } else {

                viewModel.loadAllChannelsForList(
                    categoryId = intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "",
                    refreshChannelId = contentId
                )
            }
        }

        applyOrientationSettings(isLandscape)

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        gestureVolume = if (maxVol > 0) (curVol * 100f / maxVol).toInt() else 100

        setupComposeControls()
        setupRelatedChannels()
        setupLinksUI()
        setupMessageBanner()
        configurePlayerInteractions()
        setupBackHandler()

        playerViewRef.useController = false

        if (DeviceUtils.isTvDevice) {
            relatedContentState.value = RelatedContentState.Hidden
            relatedContentState.value = RelatedContentState.Hidden
        } else if (!isLandscape) {
            relatedContentState.value = RelatedContentState.Loading
        }

        window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startPostponedEnterTransition()
                    }
                    return true
                }
            }
        )

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

                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    updateLinksForOrientation(isLandscape)
                }
            }
        }

        viewModel.channelListItems.observe(this) { items ->
            if (items.isNullOrEmpty() || contentType != ContentType.CHANNEL) return@observe
            val pendingNum = pendingChannelNumber
            if (pendingNum != -1) {
                pendingChannelNumber = -1
                pendingChannelDirection = 0
                val index = pendingNum - 1
                if (index in items.indices) {
                    val targetChannel = items[index]
                    if (targetChannel.id != contentId) switchToChannel(targetChannel)
                    showChannelOverlay((index + 1).toString(), targetChannel)
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
                val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                val targetIndex = (currentIndex + direction).coerceIn(0, items.size - 1)
                val targetChannel = items[targetIndex]
                if (targetChannel.id != contentId) {
                    switchToChannel(targetChannel)
                }

                showChannelOverlay((targetIndex + 1).toString(), targetChannel)
                channelNumberHandler.removeCallbacks(overlayHideRunnable)
                channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
            }
        }

        viewModel.relatedItems.observe(this) { channels ->
            if (contentType != ContentType.CHANNEL) return@observe
            relatedChannels = channels
            relatedContentState.value = if (channels.isEmpty()) RelatedContentState.Hidden else RelatedContentState.Channels(channels)
        }

        viewModel.relatedLiveEvents.observe(this) { liveEvents ->
            if (contentType != ContentType.EVENT) return@observe
            relatedContentState.value = if (liveEvents.isEmpty()) RelatedContentState.Hidden else RelatedContentState.Events(liveEvents)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        }
    }

    private fun setupWindowFlags(isLandscape: Boolean) {
        if (isLandscape) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }

    private fun setupSystemUI(isLandscape: Boolean) {
        if (isLandscape) {
            windowInsetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            windowInsetsController.apply {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    private fun setupWindowInsets() {
        if (DeviceUtils.isTvDevice) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
                if (!isLandscape) {
                    val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.getInsets(WindowInsets.Type.systemBars()).top
                    } else {
                        @Suppress("DEPRECATION") insets.systemWindowInsetTop
                    }
                    params.topMargin = topInset
                } else {
                    params.topMargin = 0
                }
                playerContainer.layoutParams = params
                playerContainer.setPadding(0, 0, 0, 0)
                insets
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            playerViewRef.hideController()
            return
        }

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)
        applyOrientationSettings(isLandscape)
        setSubtitleTextSize()
        updateMessageBannerForOrientation(isLandscape)
        updateLinksForOrientation(isLandscape)
        applyAdapterColors()

        if (player?.playbackState == Player.STATE_BUFFERING) {
            playerViewRef.hideController()
        }

        window.decorView.post {
            playerContainer.requestLayout()
            playerViewRef.requestLayout()
        }
    }

    private fun applyAdapterColors() {  }
    private fun applyResizeModeForOrientation(isLandscape: Boolean) {
        if (isLandscape) {
            playerViewRef.resizeMode = networkLandscapeResizeMode
        } else {
            playerViewRef.resizeMode = networkPortraitResizeMode
        }
    }

    private fun applyOrientationSettings(isLandscape: Boolean) {
        adjustLayoutForOrientation(isLandscape)
        applyResizeModeForOrientation(isLandscape)
    }

    private fun adjustLayoutForOrientation(isLandscape: Boolean) {
        if (isLandscape) {
            enterFullscreen()

            val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.topMargin = 0
            params.bottomMargin = 0
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

            playerContainer.setPadding(0, 0, 0, 0)
            playerContainer.layoutParams = params

            playerViewRef.controllerAutoShow = false
            playerViewRef.controllerShowTimeoutMs = 3000

        } else {

            if (contentType == ContentType.NETWORK_STREAM) {
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())?.top ?: 0
                } else {
                    @Suppress("DEPRECATION") window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
                }
                val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
                params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.topMargin = topInset
                params.bottomMargin = 0
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.dimensionRatio = null

                playerContainer.setPadding(0, 0, 0, 0)
                playerContainer.layoutParams = params
            } else {
                exitFullscreen()
            }

            playerViewRef.controllerAutoShow = false
            playerViewRef.controllerShowTimeoutMs = 5000
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
        outState.putBoolean("SAVE_IS_SPORTS", intentIsSports)
        outState.putLong("SAVE_PLAYBACK_POSITION", player?.currentPosition ?: 0L)
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            outState.putInt("SAVE_RESIZE_LANDSCAPE", networkLandscapeResizeMode)
            outState.putInt("SAVE_RESIZE_PORTRAIT", networkPortraitResizeMode)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val typeName = savedInstanceState.getString("SAVE_CONTENT_TYPE") ?: return
        contentType = ContentType.valueOf(typeName)
        channelData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable("SAVE_CHANNEL_DATA", Channel::class.java)
        } else {
            @Suppress("DEPRECATION") savedInstanceState.getParcelable("SAVE_CHANNEL_DATA")
        }
        eventData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable("SAVE_EVENT_DATA", LiveEvent::class.java)
        } else {
            @Suppress("DEPRECATION") savedInstanceState.getParcelable("SAVE_EVENT_DATA")
        }
        allEventLinks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelableArrayList("SAVE_ALL_LINKS", LiveEventLink::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION") savedInstanceState.getParcelableArrayList<LiveEventLink>("SAVE_ALL_LINKS") ?: emptyList()
        }
        currentLinkIndex = savedInstanceState.getInt("SAVE_LINK_INDEX", 0)
        contentId = savedInstanceState.getString("SAVE_CONTENT_ID", "")
        contentName = savedInstanceState.getString("SAVE_CONTENT_NAME", "")
        streamUrl = savedInstanceState.getString("SAVE_STREAM_URL", "")
        intentCategoryId = savedInstanceState.getString("SAVE_CATEGORY_ID")
        intentSelectedGroup = savedInstanceState.getString("SAVE_SELECTED_GROUP")
        intentIsSports = savedInstanceState.getBoolean("SAVE_IS_SPORTS", false)
        if (preferencesManager.isRememberAspectRatioEnabled()) {
            val savedResizeLandscape = savedInstanceState.getInt("SAVE_RESIZE_LANDSCAPE", -1)
            val savedResizePortrait = savedInstanceState.getInt("SAVE_RESIZE_PORTRAIT", -1)
            if (savedResizeLandscape != -1) networkLandscapeResizeMode = savedResizeLandscape
            if (savedResizePortrait != -1) networkPortraitResizeMode = savedResizePortrait
            if (savedResizeLandscape != -1 || savedResizePortrait != -1) resizeModesRestoredFromState = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            setPictureInPictureParams(updatePipParams())
        }
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> isScreenOff = true
                    Intent.ACTION_SCREEN_ON  -> isScreenOff = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        isScreenOff = false
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)

        if (player == null) {
            setupPlayer()
        }
        if (contentType == ContentType.CHANNEL && viewModel.channelListItems.value.isNullOrEmpty()) {
            viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
        }
        playerViewRef.onResume()
        playerViewRef.player = player
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (!isInPictureInPictureMode) {

            pipReceiver?.let {
                unregisterReceiver(it)
                pipReceiver = null
            }
            isInPipMode = false
            isEnteringPip = false
            isInPip = false

            super.onPictureInPictureModeChanged(false, newConfig)

            controlsState.show(lifecycleScope)

            if (wasLockedBeforePip) {
                controlsState.lock()
                wasLockedBeforePip = false
            }

            exitPipUIMode(newConfig)
            return
        }

        isInPipMode = true
        isEnteringPip = false
        isInPip = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(updatePipParams(enter = true))
        }

        controlsState.hide()

        val pipParams = playerContainer.layoutParams as ConstraintLayout.LayoutParams
        pipParams.dimensionRatio = null
        pipParams.topMargin = 0
        pipParams.bottomMargin = 0
        pipParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        playerContainer.layoutParams = pipParams

        setupPipReceiver()

        super.onPictureInPictureModeChanged(true, newConfig)
    }

    private fun exitPipUIMode(newConfig: Configuration) {
        setSubtitleTextSize()

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)

        applyOrientationSettings(isLandscape)

        if (!isLandscape) {
            val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = "H,16:9"
            params.topMargin = 0
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            playerContainer.layoutParams = params

            val hasRelated = relatedChannels.isNotEmpty() ||
                (contentType == ContentType.EVENT)
            if (hasRelated) {
                relatedContentState.value = RelatedContentState.Hidden
            }
        }

        if (wasLockedBeforePip) {
            controlsState.isLocked = true
            wasLockedBeforePip = false
        } else {
            controlsState.isLocked = false
        }

        playerViewRef.useController = false
        updateLinksForOrientation(isLandscape)
    }

    @SuppressLint("NewApi")
    override fun onUserLeaveHint() {
        val isForegrounded = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (!DeviceUtils.isTvDevice && isPipSupported && player?.isPlaying == true && isForegrounded && !isFinishing) {
            isEnteringPip = true
            wasLockedBeforePip = controlsState.isLocked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(updatePipParams(enter = true))
            } else {
                enterPictureInPictureMode()
            }
        }
        super.onUserLeaveHint()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isInPipMode -> return
                    channelNumberInput.isNotEmpty() || pendingChannelIndex != -1 || pendingChannelDirection != 0 || pendingChannelNumber != -1 -> cancelNumberInput()
                    showChannelList.value -> showChannelList.value = false
                    else -> finish()
                }
            }
        })
    }

    private fun cancelNumberInput() {
        channelNumberInput = ""
        pendingChannelIndex = -1
        pendingChannelDirection = 0
        pendingChannelNumber = -1
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)
        showChannelOverlayState.value = null
    }
    private fun clearNumberTyping() {
        channelNumberInput = ""
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)

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
                            if (targetIndex != currentLinkIndex) {
                                switchToLink(allEventLinks[targetIndex], targetIndex)
                            }
                        }
                    }
                    index != -1 && !items.isNullOrEmpty() && index in items.indices -> {
                        pendingChannelDirection = 0
                        val targetChannel = items[index]
                        if (targetChannel.id != contentId) switchToChannel(targetChannel)
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                    }
                    index == -1 && !items.isNullOrEmpty() && pendingChannelDirection != 0 -> {
                        val dir = pendingChannelDirection
                        pendingChannelDirection = 0
                        val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                        val targetIndex = (currentIndex + dir).coerceIn(0, items.size - 1)
                        val targetChannel = items[targetIndex]
                        if (targetChannel.id != contentId) {
                            switchToChannel(targetChannel)
                            showChannelOverlay((targetIndex + 1).toString(), targetChannel)
                        }
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                    }
                    items.isNullOrEmpty() -> {
                        pendingChannelDirection = direction
                        viewModel.loadAllChannelsForList(
                            intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: ""
                        )
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
                    val hasError = errorMessage.value.isNotBlank()
                    val hasEnded = it.playbackState == Player.STATE_ENDED
                    if (hasError || hasEnded) retryPlayback()
                    else if (it.isPlaying) it.pause() else it.play()
                }
                controlsState.show(lifecycleScope)
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (showChannelList.value) return super.onKeyDown(keyCode, event)
                cancelNumberInput()
                player?.let {
                    val newPosition = it.currentPosition + skipMs
                    if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPosition >= it.duration) {
                        it.seekTo(it.duration)
                    } else if (it.duration == C.TIME_UNSET) {
                        it.seekTo(newPosition)
                    } else {
                        it.seekTo(newPosition.coerceAtMost(it.duration))
                    }
                }
                controlsState.show(lifecycleScope)
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (showChannelList.value) return super.onKeyDown(keyCode, event)
                cancelNumberInput()
                player?.let { it.seekTo((it.currentPosition - skipMs).coerceAtLeast(0L)) }
                controlsState.show(lifecycleScope)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                cancelNumberInput()
                controlsState.show(lifecycleScope)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                val channelListItems = viewModel.channelListItems.value
                val isChannelListAvailable = contentType == ContentType.CHANNEL &&
                    !channelListItems.isNullOrEmpty() &&
                    (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || DeviceUtils.isTvDevice)
                if (isChannelListAvailable) {
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
                    cancelNumberInput()
                    showChannelList.value = false
                    controlsState.show(lifecycleScope)
                } else {
                    cancelNumberInput()
                    player?.let {
                        val hasError = errorMessage.value.isNotBlank()
                        val hasEnded = it.playbackState == Player.STATE_ENDED
                        if (hasError || hasEnded) retryPlayback()
                        else if (it.isPlaying) it.pause() else it.play()
                    }
                    controlsState.show(lifecycleScope)
                }
                true
            }
            android.view.KeyEvent.KEYCODE_CHANNEL_UP,
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (showChannelList.value) {
                    cancelNumberInput()
                    showChannelList.value = false
                    return true
                }
                clearNumberTyping()
                if (contentType == ContentType.EVENT) {
                    if (allEventLinks.size > 1) {
                        val nextIndex = (currentLinkIndex + 1).coerceAtMost(allEventLinks.size - 1)
                        if (nextIndex != currentLinkIndex) switchToLink(allEventLinks[nextIndex], nextIndex)
                    }
                    controlsState.show(lifecycleScope)
                    return true
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
                if (showChannelList.value) {
                    cancelNumberInput()
                    showChannelList.value = false
                    return true
                }
                clearNumberTyping()
                if (contentType == ContentType.EVENT) {
                    if (allEventLinks.size > 1) {
                        val prevIndex = (currentLinkIndex - 1).coerceAtLeast(0)
                        if (prevIndex != currentLinkIndex) switchToLink(allEventLinks[prevIndex], prevIndex)
                    }
                    controlsState.show(lifecycleScope)
                    return true
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
            android.view.KeyEvent.KEYCODE_1,
            android.view.KeyEvent.KEYCODE_2,
            android.view.KeyEvent.KEYCODE_3,
            android.view.KeyEvent.KEYCODE_4,
            android.view.KeyEvent.KEYCODE_5,
            android.view.KeyEvent.KEYCODE_6,
            android.view.KeyEvent.KEYCODE_7,
            android.view.KeyEvent.KEYCODE_8,
            android.view.KeyEvent.KEYCODE_9 -> {
                if (!DeviceUtils.isTvDevice) return super.onKeyDown(keyCode, event)
                if (event?.repeatCount != 0) return true
                if (showChannelList.value) return true
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

    private fun showChannelOverlay(number: String, channel: com.livetvpro.app.data.models.Channel?) {
        showChannelOverlayState.value = null
        return
        val logoView = overlay.findViewById<android.widget.ImageView>(R.id.channel_overlay_logo)
        val numberView = overlay.findViewById<android.widget.TextView>(R.id.channel_overlay_number)
        val nameView = overlay.findViewById<android.widget.TextView>(R.id.channel_overlay_name)
        numberView?.text = number
        nameView?.text = channel?.name ?: ""
        nameView?.visibility = if (channel != null) View.VISIBLE else View.GONE
        if (channel != null && channel.logoUrl.isNotEmpty()) {
            logoView?.visibility = View.VISIBLE
            logoView?.let { com.livetvpro.app.utils.GlideExtensions.loadImage(it, channel.logoUrl) }
        } else {
            logoView?.visibility = View.GONE
        }
        overlay.visibility = View.VISIBLE
    }

    private fun navigateToChannelByNumber() {
        val number = channelNumberInput.toIntOrNull()
        channelNumberInput = ""
        if (number == null || number <= 0) {
            showChannelOverlayState.value = null
            return
        }
        if (contentType != ContentType.CHANNEL) {
            showChannelOverlayState.value = null
            return
        }
        val items = viewModel.channelListItems.value
        if (items.isNullOrEmpty()) {

            pendingChannelNumber = number
            showChannelOverlay("...", null)
            viewModel.loadAllChannelsForList(
                intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: ""
            )
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

    private fun setupComposeControls() {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LiveTVProTheme(themeManager = themeManager, surfaceColor = androidx.compose.ui.graphics.Color.Transparent) {
                    val isPlaying by produceState(initialValue = false, player) {
                        while (true) {
                            value = player?.isPlaying == true
                            delay(100)
                        }
                    }

                    var currentPosition by remember { mutableStateOf(0L) }
                    var duration by remember { mutableStateOf(0L) }
                    var bufferedPosition by remember { mutableStateOf(0L) }

                    LaunchedEffect(player) {
                        while (true) {
                            currentPosition = player?.currentPosition ?: 0L
                            bufferedPosition = player?.bufferedPosition ?: 0L
                            duration = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
                            delay(500L)
                        }
                    }
                    val isLandscape = DeviceUtils.isTvDevice || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    var showChannelList by this@PlayerActivity.showChannelList
                    val channelListItems by viewModel.channelListItems.observeAsState(emptyList())
                    val isChannelListAvailable = contentType == ContentType.CHANNEL && channelListItems.isNotEmpty() && (isLandscape || DeviceUtils.isTvDevice)

                    LaunchedEffect(controlsState.isVisible, controlsState.isLocked, isLandscape, showChannelList) {
                        if (isLandscape) {
                            val landscapeLinksRecycler = playerContainer.findViewById<RecyclerView>(R.id.exo_links_recycler)
                            val chipsVisible = controlsState.isVisible && !controlsState.isLocked && !showChannelList
                            landscapeLinksRecycler?.visibility = if (chipsVisible) View.VISIBLE else View.GONE
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = ViewTreeObserver.OnGlobalLayoutListener {
                            val rect = Rect()
                            val surface = playerViewRef.videoSurfaceView
                            val target = surface ?: playerViewRef
                            target.getGlobalVisibleRect(rect)
                            if (!rect.isEmpty) {
                                pipRect = rect
                            }
                        }
                        playerViewRef.viewTreeObserver.addOnGlobalLayoutListener(listener)

                        onDispose {
                            playerViewRef.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerControls(
                            state = controlsState,
                            isPlaying = isPlaying,
                            isMuted = isMuted,
                            currentPosition = currentPosition,
                            duration = duration,
                            bufferedPosition = bufferedPosition,
                            channelName = contentName,
                            showPipButton = isPipSupported && !DeviceUtils.isTvDevice,
                            showAspectRatioButton = true,
                            isLandscape = isLandscape,
                            isTvMode = DeviceUtils.isTvDevice,
                            centerControlsMode = preferencesManager.getCenterControlsMode(),
                            isNetworkStream = contentType == ContentType.NETWORK_STREAM,
                            isChannelListAvailable = isChannelListAvailable,
                            onBackClick = { finish() },
                            onPipClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    wasLockedBeforePip = controlsState.isLocked
                                    enterPictureInPictureMode(updatePipParams(enter = true))
                                }
                            },
                            onSettingsClick = { showSettingsDialog() },
                            onMuteClick = { toggleMute() },
                            onLockClick = { locked -> },
                            onChannelListClick = { showChannelList = true },
                            onPlayPauseClick = {
                                player?.let {
                                    val hasError = errorMessage.value.isNotBlank()
                                    val hasEnded = it.playbackState == Player.STATE_ENDED

                                    if (hasError || hasEnded) {
                                        retryPlayback()
                                    } else {
                                        if (it.isPlaying) it.pause() else it.play()
                                    }
                                }
                            },
                            onSeek = { position ->
                                player?.seekTo(position)
                            },
                            onRewindClick = {
                                player?.let {
                                    val newPosition = it.currentPosition - skipMs
                                    it.seekTo(if (newPosition < 0) 0 else newPosition)
                                }
                            },
                            onForwardClick = {
                                player?.let {
                                    val newPosition = it.currentPosition + skipMs
                                    if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPosition >= it.duration) {
                                        it.seekTo(it.duration)
                                    } else {
                                        it.seekTo(newPosition)
                                    }
                                }
                            },
                            onPrevClick = {
                                when (contentType) {
                                    ContentType.EVENT -> {
                                        if (allEventLinks.size > 1) {
                                            val prevIndex = (currentLinkIndex - 1).coerceAtLeast(0)
                                            if (prevIndex != currentLinkIndex) switchToLink(allEventLinks[prevIndex], prevIndex)
                                        }
                                    }
                                    ContentType.CHANNEL -> {
                                        val items = viewModel.channelListItems.value
                                        if (!items.isNullOrEmpty()) {
                                            val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                            val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                                            if (prevIndex != currentIndex) switchToChannel(items[prevIndex])
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onNextClick = {
                                when (contentType) {
                                    ContentType.EVENT -> {
                                        if (allEventLinks.size > 1) {
                                            val nextIndex = (currentLinkIndex + 1).coerceAtMost(allEventLinks.size - 1)
                                            if (nextIndex != currentLinkIndex) switchToLink(allEventLinks[nextIndex], nextIndex)
                                        }
                                    }
                                    ContentType.CHANNEL -> {
                                        val items = viewModel.channelListItems.value
                                        if (!items.isNullOrEmpty()) {
                                            val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                            val nextIndex = (currentIndex + 1).coerceAtMost(items.size - 1)
                                            if (nextIndex != currentIndex) switchToChannel(items[nextIndex])
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onAspectRatioClick = {
                                if (isLandscape || contentType == ContentType.NETWORK_STREAM) {
                                    cycleAspectRatio()
                                }
                            },
                            onFullscreenClick = { toggleFullscreen() },
                            onVolumeSwipe = { vol ->
                                gestureVolume = vol
                                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val target = (vol / 100f * max).toInt()
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    target,
                                    0
                                )
                            },
                            onBrightnessSwipe = { bri ->
                                gestureBrightness = bri
                                val lp = window.attributes
                                lp.screenBrightness = if (bri == 0) {
                                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                } else {
                                    bri / 100f
                                }
                                window.attributes = lp
                            },
                            initialVolume = gestureVolume,
                            initialBrightness = gestureBrightness,
                        )

                        if (isLandscape && isChannelListAvailable) {
                            com.livetvpro.app.ui.player.compose.ChannelListPanel(
                                visible = showChannelList,
                                channels = channelListItems,
                                currentChannelId = contentId,
                                onChannelClick = { channel -> switchToChannel(channel) },
                                onDismiss = { showChannelList = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun cycleAspectRatio() {
        val isLandscape = DeviceUtils.isTvDevice || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val current = playerViewRef.resizeMode
        val next = when (current) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT   -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM  -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else                                     -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        if (preferencesManager.isRememberAspectRatioEnabled()) {
            if (isLandscape) networkLandscapeResizeMode = next
            else networkPortraitResizeMode = next
            if (isLandscape) preferencesManager.setSavedAspectRatio(next)
            else preferencesManager.setSavedAspectRatioPortrait(next)
        }

        playerViewRef.resizeMode = next
    }

    private fun showSettingsDialog() {
        if (player == null) return
        if (isFinishing || isDestroyed) return
        if (isShowingSettingsDialog) return
        isShowingSettingsDialog = true
        showSettingsDialog.value = true
    }

    private fun parseIntent() {
        val isExternalView = intent.action == android.content.Intent.ACTION_VIEW && intent.data != null
        if (isExternalView) {
            val uri = intent.data!!
            contentType = ContentType.NETWORK_STREAM
            contentName = uri.lastPathSegment ?: "Stream"
            contentId = "external_${System.currentTimeMillis()}"
            val rawUriString = uri.toString()
            val decodedUriString = android.net.Uri.decode(rawUriString)
            streamUrl = decodedUriString

            val parsed = parseStreamUrl(decodedUriString)

            allEventLinks = listOf(
                com.livetvpro.app.data.models.LiveEventLink(
                    quality = "Auto",
                    url = parsed.url,
                    cookie = parsed.headers["Cookie"] ?: "",
                    referer = parsed.headers["Referer"] ?: "",
                    origin = parsed.headers["Origin"] ?: "",
                    userAgent = parsed.headers["User-Agent"] ?: "Default",
                    xForwardedFor = parsed.headers["X-Forwarded-For"],
                    drmScheme = parsed.drmScheme ?: "",
                    drmLicenseUrl = parsed.drmLicenseUrl
                        ?: parsed.drmKeyId?.let { id -> parsed.drmKey?.let { k -> "$id:$k" } }
                        ?: ""
                )
            )
            currentLinkIndex = 0
            return
        }

        val isNetworkStream = intent.getBooleanExtra("IS_NETWORK_STREAM", false)

        if (isNetworkStream) {
            contentType = ContentType.NETWORK_STREAM
            contentName = intent.getStringExtra("CHANNEL_NAME") ?: "Network Stream"
            contentId = "network_stream_${System.currentTimeMillis()}"

            val streamUrlRaw = intent.getStringExtra("STREAM_URL") ?: ""

            if (streamUrlRaw.contains("|")) {
                streamUrl = streamUrlRaw

                val parsed = parseStreamUrl(streamUrlRaw)
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
                    userAgent = parsed.headers["User-Agent"] ?: intent.getStringExtra("USER_AGENT") ?: "Default",
                    xForwardedFor = parsed.headers["X-Forwarded-For"],
                    drmScheme = resolvedDrmScheme,
                    drmLicenseUrl = resolvedDrmLicenseUrl
                        ?: resolvedDrmKeyId?.let { id -> resolvedDrmKey?.let { k -> "$id:$k" } }
                )
                streamUrl = buildStreamUrl(mergedLink)

                allEventLinks = listOf(mergedLink)
            } else {
                val cookie = intent.getStringExtra("COOKIE") ?: ""
                val referer = intent.getStringExtra("REFERER") ?: ""
                val origin = intent.getStringExtra("ORIGIN") ?: ""
                val drmLicense = intent.getStringExtra("DRM_LICENSE") ?: ""
                val userAgent = intent.getStringExtra("USER_AGENT") ?: "Default"
                val drmScheme = intent.getStringExtra("DRM_SCHEME") ?: "clearkey"

                allEventLinks = listOf(
                    LiveEventLink(
                        quality = "Network Stream",
                        url = streamUrlRaw,
                        cookie = cookie,
                        referer = referer,
                        origin = origin,
                        userAgent = userAgent,
                        drmScheme = drmScheme,
                        drmLicenseUrl = drmLicense
                    )
                )

                streamUrl = buildStreamUrl(allEventLinks[0])
            }

            currentLinkIndex = 0
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

        val passedRelatedChannels: List<Channel>? =
            intent.getStringExtra(EXTRA_RELATED_CHANNELS_KEY)?.let { ChannelListCache.get(it) }

        if (channelData != null) {
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

                streamUrl = buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                streamUrl = channel.streamUrl
                allEventLinks = emptyList()
            }

            if (allEventLinks.isEmpty()) {
            }

        } else if (eventData != null) {
            contentType = ContentType.EVENT
            val event = eventData!!
            contentId = event.id
            contentName = event.title.ifEmpty { "${event.team1Name} vs ${event.team2Name}" }

            allEventLinks = event.links

            if (allEventLinks.isNotEmpty()) {
                currentLinkIndex = if (passedLinkIndex in allEventLinks.indices) passedLinkIndex else 0
                streamUrl = buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                currentLinkIndex = 0
                streamUrl = ""
            }

        } else {
            finish()
            return
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
                    val passedRelatedChannels: List<Channel>? =
                        intent.getStringExtra(EXTRA_RELATED_CHANNELS_KEY)?.let { ChannelListCache.get(it) }

                    if (passedRelatedChannels != null && passedRelatedChannels.isNotEmpty()) {
                        val filteredChannels = passedRelatedChannels.filter { it.id != channel.id }
                        viewModel.setRelatedChannels(filteredChannels)
                    } else if (intentIsSports) {
                        viewModel.loadRandomRelatedSports(channel.id)
                    } else {
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

    private fun switchToChannel(newChannel: Channel, linkIndex: Int = -1) {
        releasePlayer()
        val previousContentType = contentType
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
            currentLinkIndex = if (linkIndex in allEventLinks.indices) linkIndex else 0
            streamUrl = allEventLinks.getOrNull(currentLinkIndex)?.let { buildStreamUrl(it) } ?: newChannel.streamUrl
        } else {
            allEventLinks = emptyList()
            streamUrl = newChannel.streamUrl
        }

        setupPlayer()
        setupLinksUI()

        relatedChannelsAdapter = RelatedChannelAdapter { relatedItem ->
            switchToChannel(relatedItem)
        }

        relatedContentState.value = RelatedContentState.Loading

        val isSports = newChannel.categoryId == "sports" || intentIsSports && previousContentType != ContentType.EVENT
        val categoryId = newChannel.categoryId.takeIf { it.isNotEmpty() } ?: intentCategoryId ?: ""
        val group = if (previousContentType == ContentType.EVENT) null else intentSelectedGroup

        if (isSports) {
            viewModel.loadRandomRelatedSports(newChannel.id)
        } else {
            viewModel.loadRandomRelatedChannels(categoryId, newChannel.id, group)
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
                streamUrl = buildStreamUrl(allEventLinks[currentLinkIndex])
            } else {
                currentLinkIndex = 0
                streamUrl = ""
            }

            setupPlayer()
            setupLinksUI()
                )
            }

            relatedContentState.value = RelatedContentState.Loading

            viewModel.loadRelatedEvents(newEvent.id)

        } catch (e: Exception) {
        }
    }

    private fun switchToLink(link: LiveEventLink, position: Int) {
        currentLinkIndex = position
        streamUrl = buildStreamUrl(link)
        releasePlayer()
        setupPlayer()
    }

    override fun onPause() {
        super.onPause()
        cancelNumberInput()

        val isPip = isEnteringPip ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
        if (!isPip) {
            playerViewRef.onPause()
            player?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        screenOffReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenOffReceiver = null
        }
        if (isInPipMode) {
            if (isScreenOff) return
            if (isFinishing) {
                finish()
            } else {
                releasePlayer()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)
        mainHandler.removeCallbacksAndMessages(null)
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
    }

    private data class StreamInfo(
        val url: String,
        val headers: Map<String, String>,
        val drmScheme: String?,
        val drmKeyId: String?,
        val drmKey: String?,
        val drmLicenseUrl: String? = null
    )

    private fun parseStreamUrl(streamUrl: String): StreamInfo {
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
                when {
                    value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true) -> add(segment)
                    value.contains('=') -> add(segment)
                    else -> addAll(segment.split("&"))
                }
            }
        }

        val headers = mutableMapOf<String, String>()
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
                "drmkey"   -> drmKey   = value
                "referer", "referrer" -> headers["Referer"] = value
                "user-agent", "useragent" -> headers["User-Agent"] = value
                "origin" -> headers["Origin"] = value
                "cookie" -> headers["Cookie"] = value
                "x-forwarded-for" -> headers["X-Forwarded-For"] = value
                else -> headers[key] = value
            }
        }

        return StreamInfo(url, headers, drmScheme, drmKeyId, drmKey, drmLicenseUrl)
    }

    private fun normalizeDrmScheme(scheme: String): String {
        val lower = scheme.lowercase()
        return when {
            lower.contains("clearkey") || lower == "org.w3.clearkey" -> "clearkey"
            lower.contains("widevine") || lower == "com.widevine.alpha" -> "widevine"
            lower.contains("playready") || lower == "com.microsoft.playready" -> "playready"
            lower.contains("fairplay") -> "fairplay"
            else -> lower
        }
    }

    private fun buildStreamUrl(link: LiveEventLink): String {
        var url = link.url
        val params = mutableListOf<String>()

        link.referer?.let { if (it.isNotEmpty()) params.add("referer=$it") }
        link.cookie?.let { if (it.isNotEmpty()) params.add("cookie=$it") }
        link.origin?.let { if (it.isNotEmpty()) params.add("origin=$it") }
        link.userAgent?.let { if (it.isNotEmpty()) params.add("user-agent=$it") }
        link.xForwardedFor?.let { if (it.isNotEmpty()) params.add("x-forwarded-for=$it") }
        link.drmScheme?.let { if (it.isNotEmpty()) params.add("drmScheme=$it") }
        link.drmLicenseUrl?.let { if (it.isNotEmpty()) params.add("drmLicense=$it") }

        if (params.isNotEmpty()) {
            url += "|" + params.joinToString("|")
        }

        return url
    }
    private fun setupPlayer() {
        if (player != null) return
        errorMessage.value = ""
        playerViewRef.hideController()

        if (DeviceUtils.isTvDevice) {
                ?.setOnClickListener { retryPlayback() }
        }
        trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedChannelCountAdaptiveness(true)
                .clearVideoSizeConstraints()

                .setTunnelingEnabled(DeviceUtils.isTvDevice)
                .build()
        }

        try {
            val streamInfo = parseStreamUrl(streamUrl)

            if (streamInfo.url.isBlank()) {
                showError("Invalid stream URL")
                return
            }
            val headers = streamInfo.headers.toMutableMap()
            val ua = headers["User-Agent"]
            if (ua.isNullOrBlank() || ua == "Default") {
                headers["User-Agent"] = "okhttp/4.12.0"
            }

            val baseDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val clearKeyBranch = when {
                streamInfo.drmScheme == "clearkey" && streamInfo.drmKeyId != null && streamInfo.drmKey != null -> "inline-hex"
                streamInfo.drmScheme == "clearkey" && streamInfo.drmLicenseUrl?.trimStart()?.startsWith("{") == true -> "jwk-inline"
                streamInfo.drmScheme == "clearkey" && streamInfo.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true -> "license-server"
                streamInfo.drmScheme == "clearkey" -> "none(no-key-material)"
                else -> "none(scheme=${streamInfo.drmScheme})"
            }
            val clearKeyMgr: DefaultDrmSessionManager? = when {
                streamInfo.drmScheme == "clearkey" && streamInfo.drmKeyId != null && streamInfo.drmKey != null -> {
                    val mgr = buildClearKeyInlineManager(streamInfo.drmKeyId, streamInfo.drmKey)
                    mgr
                }
                streamInfo.drmScheme == "clearkey" && streamInfo.drmLicenseUrl?.trimStart()?.startsWith("{") == true -> {
                    val mgr = buildClearKeyJwkManager(streamInfo.drmLicenseUrl)
                    mgr
                }
                streamInfo.drmScheme == "clearkey" && streamInfo.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true -> {
                    val mgr = buildClearKeyServerManager(streamInfo.drmLicenseUrl, headers)
                    mgr
                }
                else -> null
            }
            val mediaSourceFactory = if (clearKeyMgr != null) {
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(baseDataSourceFactory)
                    .setDrmSessionManagerProvider { clearKeyMgr }
            } else {
                if (streamInfo.drmScheme == "clearkey") {
                    android.util.Log.e("PlayerActivity",
                        "ClearKey DRM required but key material is missing or invalid. " +
                        "keyId=${streamInfo.drmKeyId} key=${streamInfo.drmKey} branch=$clearKeyBranch")
                    showError("DRM Error: ClearKey material invalid")
                    return
                }
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(baseDataSourceFactory)
            }
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector!!)
                .setMediaSourceFactory(mediaSourceFactory)
                .setSeekBackIncrementMs(skipMs)
                .setSeekForwardIncrementMs(skipMs)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                .build().also { exo ->
                    playerViewRef.player = exo

                    if (!resizeModesRestoredFromState && preferencesManager.isRememberAspectRatioEnabled()) {
                        val savedLandscape = preferencesManager.getSavedAspectRatio()
                        if (savedLandscape != -1) networkLandscapeResizeMode = savedLandscape
                        val savedPortrait = preferencesManager.getSavedAspectRatioPortrait()
                        if (savedPortrait != -1) networkPortraitResizeMode = savedPortrait
                    }

                    applyResizeModeForOrientation(
                        DeviceUtils.isTvDevice || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    )
                    playerViewRef.hideController()
                    val uri = android.net.Uri.parse(streamInfo.url)
                    val mediaItemBuilder = MediaItem.Builder().setUri(uri)
                    val urlLower = streamInfo.url.lowercase()
                    when {
                        urlLower.contains("m3u8") || urlLower.contains("extension=m3u8") ->
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                        urlLower.contains(".mpd") || urlLower.contains("/dash/") || urlLower.contains("type=mpd") ->
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                        urlLower.contains(".ism") || urlLower.contains(".isml") ->
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SS)
                        urlLower.contains(".flv") ->
                            mediaItemBuilder.setMimeType("video/x-flv")
                        urlLower.contains(".ts") || urlLower.contains(".mts") || urlLower.contains(".m2ts") ->
                            mediaItemBuilder.setMimeType("video/mp2t")
                        urlLower.contains(".mp4") || urlLower.contains(".m4v") || urlLower.contains(".m4a") ->
                            mediaItemBuilder.setMimeType("video/mp4")
                        urlLower.contains(".mkv") ->
                            mediaItemBuilder.setMimeType("video/x-matroska")
                        urlLower.contains(".webm") ->
                            mediaItemBuilder.setMimeType("video/webm")
                        urlLower.contains(".avi") ->
                            mediaItemBuilder.setMimeType("video/avi")
                        urlLower.contains(".mov") ->
                            mediaItemBuilder.setMimeType("video/quicktime")
                        urlLower.contains(".mp3") ->
                            mediaItemBuilder.setMimeType("audio/mpeg")
                        urlLower.contains(".aac") ->
                            mediaItemBuilder.setMimeType("audio/aac")
                        urlLower.startsWith("rtmp://") || urlLower.startsWith("rtmps://") ->
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_RTSP)
                        urlLower.startsWith("rtsp://") ->
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_RTSP)
                    }

                    when {
                        streamInfo.drmScheme == "clearkey" && clearKeyMgr != null -> {
                        }
                        streamInfo.drmScheme == "widevine" && streamInfo.drmLicenseUrl != null -> {

                            val licenseHeaders = headers.filter { (k, _) ->
                                k.lowercase() !in setOf("referer", "origin")
                            }
                            mediaItemBuilder.setDrmConfiguration(
                                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                    .setLicenseUri(streamInfo.drmLicenseUrl)
                                    .setLicenseRequestHeaders(licenseHeaders)

                                    .setForceDefaultLicenseUri(true)

                                    .setMultiSession(false)
                                    .build()
                            )
                        }
                        streamInfo.drmScheme == "playready" && streamInfo.drmLicenseUrl != null -> {
                            val licenseHeaders = headers.filter { (k, _) ->
                                k.lowercase() !in setOf("referer", "origin")
                            }
                            mediaItemBuilder.setDrmConfiguration(
                                MediaItem.DrmConfiguration.Builder(C.PLAYREADY_UUID)
                                    .setLicenseUri(streamInfo.drmLicenseUrl)
                                    .setLicenseRequestHeaders(licenseHeaders)
                                    .setForceDefaultLicenseUri(true)
                                    .setMultiSession(false)
                                    .build()
                            )
                        }

                        else -> {
                        }
                    }

                    val mediaItem = mediaItemBuilder.build()
                    exo.setMediaItem(mediaItem)
                    exo.prepare()
                    exo.playWhenReady = true
                    playerListener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    errorMessage.value = ""
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
                                }
                                Player.STATE_BUFFERING -> {
                                    errorMessage.value = ""
                                }
                                Player.STATE_ENDED -> {
                                    playerViewRef.showController()
                                }
                                Player.STATE_IDLE -> {}
                            }
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            if (!preferencesManager.isForceLowestQualityEnabled()) return
                            val ts = trackSelector ?: return
                            var lowestHeight = Int.MAX_VALUE
                            var lowestGroupIndex = -1
                            var lowestTrackIndex = -1
                            tracks.groups.forEachIndexed { gi, group ->
                                if (group.type != androidx.media3.common.C.TRACK_TYPE_VIDEO) return@forEachIndexed
                                for (ti in 0 until group.length) {
                                    val fmt = group.getTrackFormat(ti)
                                    if (fmt.height > 0 && fmt.height < lowestHeight) {
                                        lowestHeight = fmt.height
                                        lowestGroupIndex = gi
                                        lowestTrackIndex = ti
                                    }
                                }
                            }
                            if (lowestGroupIndex != -1) {
                                val group = tracks.groups[lowestGroupIndex]
                                ts.parameters = ts.parameters.buildUpon()
                                    .setOverrideForType(
                                        androidx.media3.common.TrackSelectionOverride(
                                            group.mediaTrackGroup,
                                            listOf(lowestTrackIndex)
                                        )
                                    )
                                    .build()
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPipMode) {
                                setPictureInPictureParams(updatePipParams(enter = false))
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            super.onVideoSizeChanged(videoSize)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            super.onPlayerError(error)

                            if (contentType == ContentType.EVENT && allEventLinks.size > 1) {
                                val nextIndex = currentLinkIndex + 1
                                if (nextIndex in allEventLinks.indices) {
                                    switchToLink(allEventLinks[nextIndex], nextIndex)
                                    return
                                }
                            }

                            val errorMessage = when {
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT ->
                                    "Connection Failed"
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                    when {
                                        error.message?.contains("403") == true -> "Access Denied"
                                        error.message?.contains("404") == true -> "Stream Not Found"
                                        else -> "Playback Error"
                                    }
                                }
                                error.message?.contains("drm", ignoreCase = true) == true ||
                                error.message?.contains("widevine", ignoreCase = true) == true ||
                                error.message?.contains("clearkey", ignoreCase = true) == true ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                                    "Stream Error"
                                error.message?.contains("geo", ignoreCase = true) == true ||
                                error.message?.contains("region", ignoreCase = true) == true ->
                                    "Not Available"
                                else -> "Playback Error"
                            }
                            showError(errorMessage)
                        }
                    }
                    exo.addListener(playerListener!!)
                }
        } catch (e: Exception) {
            showError("Failed to initialize player")
        }
    }

    private fun showError(message: String) {
                android.graphics.Typeface.DEFAULT
            }
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setPadding(48, 20, 48, 20)
            setBackgroundResource(R.drawable.error_message_background)
            elevation = 0f
        }
    }
    private fun buildClearKeyInlineManager(keyIdHex: String, keyHex: String): DefaultDrmSessionManager? {
        return try {
            val keyIdBytes = hexToBytes(keyIdHex)
            val keyBytes   = hexToBytes(keyHex)
            if (keyIdBytes.isEmpty() || keyBytes.isEmpty()) {
                android.util.Log.e("PlayerActivity", "ClearKey: failed to decode hex — keyId=${keyIdHex.take(8)}... key=${keyHex.take(8)}...")
                return null
            }
            val keyBase64 = android.util.Base64.encodeToString(
                keyBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

            val keyIdBase64 = android.util.Base64.encodeToString(
                keyIdBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

            val adaptiveCallback = buildAdaptiveClearKeyCallback(keyBase64, keyIdBase64, "ClearKey-InlineHex")

            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(adaptiveCallback)
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "buildClearKeyInlineManager failed: ${e.message}", e)
            null
        }
    }
    private fun buildClearKeyJwkManager(jwkJson: String): DefaultDrmSessionManager? {
        return try {
            val drmCallback = LocalMediaDrmCallback(jwkJson.toByteArray(Charsets.UTF_8))
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(drmCallback)
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "buildClearKeyJwkManager failed: ${e.message}", e)
            null
        }
    }
    private fun buildClearKeyServerManager(licenseUrl: String, headers: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val licenseFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val callback = HttpMediaDrmCallback(licenseUrl, licenseFactory)
            headers.forEach { (k, v) -> callback.setKeyRequestProperty(k, v) }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setPlayClearSamplesWithoutKeys(true)
                .build(callback)
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "buildClearKeyServerManager failed: ${e.message}", e)
            null
        }
    }
    private fun buildAdaptiveClearKeyCallback(
        keyBase64: String,
        fallbackKidBase64: String,
        tag: String
    ): androidx.media3.exoplayer.drm.MediaDrmCallback {
        return object : androidx.media3.exoplayer.drm.MediaDrmCallback {

            override fun executeProvisionRequest(
                uuid: UUID,
                request: androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest
            ): androidx.media3.exoplayer.drm.MediaDrmCallback.Response =
                androidx.media3.exoplayer.drm.MediaDrmCallback.Response(ByteArray(0))

            override fun executeKeyRequest(
                uuid: UUID,
                request: androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest
            ): androidx.media3.exoplayer.drm.MediaDrmCallback.Response {
                return try {
                    val requestBody = String(request.data, Charsets.UTF_8)
                    val requestedKids = mutableListOf<String>()
                    Regex(""""kids"\s*:\s*\[([^\]]+)]""").find(requestBody)?.let { match ->
                        Regex(""""([A-Za-z0-9+/=_-]+)"""").findAll(match.groupValues[1])
                            .forEach { requestedKids.add(it.groupValues[1]) }
                    }
                    val keyEntries = if (requestedKids.isNotEmpty()) {
                        requestedKids.joinToString(",") { kid ->
                            """{"kty":"oct","k":"$keyBase64","kid":"$kid"}"""
                        }
                    } else {

                        """{"kty":"oct","k":"$keyBase64","kid":"$fallbackKidBase64"}"""
                    }

                    val jwkResponse = """{"keys":[$keyEntries],"type":"temporary"}"""

                    androidx.media3.exoplayer.drm.MediaDrmCallback.Response(
                        jwkResponse.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {

                    val fallback = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$fallbackKidBase64"}],"type":"temporary"}"""
                    androidx.media3.exoplayer.drm.MediaDrmCallback.Response(
                        fallback.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
    private fun hexToBytes(hex: String): ByteArray {
        return try {
            val clean = hex.replace(" ", "").replace("-", "").lowercase()
            if (clean.length % 2 != 0) return ByteArray(0)
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    private fun toggleMute() {
        player?.let {
            isMuted = !isMuted
            it.volume = if (isMuted) 0f else 1f
        }
    }

    private fun setupMessageBanner() {
        val message = listenerManager.getMessage()
        if (message.isNotBlank()) {
            messageBannerText.value = message
            messageBannerUrl.value = listenerManager.getMessageUrl()
        }
    }

    private fun updateMessageBannerForOrientation(isLandscape: Boolean) {
    }

    private fun configurePlayerInteractions() {

    }

    private fun setupLockOverlay() {

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

    private fun exitFullscreen() {
        windowInsetsController.apply {
            show(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }

        val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.dimensionRatio = "H,16:9"
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        playerContainer.layoutParams = params
        playerContainer.visibility = View.VISIBLE

        if (allEventLinks.size > 1) {
        }
        val hasRelated = relatedChannels.isNotEmpty() ||
            (contentType == ContentType.EVENT)
        if (hasRelated) {
        }
    }

    private fun enterFullscreen() {
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        playerContainer.setPadding(0, 0, 0, 0)

        val params = playerContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.topMargin = 0
        params.dimensionRatio = null
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        playerContainer.layoutParams = params

        relatedContentState.value = RelatedContentState.Hidden
    }

    private fun setSubtitleTextSize() {
        val subtitleView = playerViewRef.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
    }

    private fun setSubtitleTextSizePiP() {
        val subtitleView = playerViewRef.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2)
    }

    private fun prepareUIForPip() {
        controlsState.hide()
    }

    @SuppressLint("NewApi")
    private fun enterPipMode() {
        playerViewRef.useController = false
        setSubtitleTextSizePiP()
        updatePipParams(enter = true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipRatio(): Rational {
        return try {
            val videoFormat = player?.videoFormat
            if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
                Rational(videoFormat.width, videoFormat.height)
            } else {
                Rational(16, 9)
            }
        } catch (e: Exception) {
            Rational(16, 9)
        }
    }

    private fun setupPipReceiver() {
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_MEDIA_CONTROL) return
                val currentPlayer = player ?: return
                val hasError = errorMessage.value.isNotBlank()
                val hasEnded = currentPlayer.playbackState == Player.STATE_ENDED
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> {
                        if (hasError || hasEnded) {
                            retryPlayback()
                        } else {
                            currentPlayer.play()
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            updatePipParams()
                        }
                    }
                    CONTROL_TYPE_PAUSE -> {
                        if (hasError || hasEnded) {
                            retryPlayback()
                        } else {
                            currentPlayer.pause()
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            updatePipParams()
                        }
                    }
                    CONTROL_TYPE_REWIND -> {
                        if (!hasError && !hasEnded) {
                            val newPosition = currentPlayer.currentPosition - skipMs
                            currentPlayer.seekTo(if (newPosition < 0) 0 else newPosition)
                        }
                    }
                    CONTROL_TYPE_FORWARD -> {
                        if (!hasError && !hasEnded) {
                            val newPosition = currentPlayer.currentPosition + skipMs
                            if (currentPlayer.isCurrentWindowLive && currentPlayer.duration != C.TIME_UNSET && newPosition >= currentPlayer.duration) {
                                currentPlayer.seekTo(currentPlayer.duration)
                            } else {
                                currentPlayer.seekTo(newPosition)
                            }
                        }
                    }
                    CONTROL_TYPE_PREV_CHANNEL -> {
                        if (contentType == ContentType.CHANNEL) {
                            val items = viewModel.channelListItems.value
                            if (!items.isNullOrEmpty()) {
                                val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                                if (prevIndex != currentIndex) switchToChannel(items[prevIndex])
                            }
                        }
                    }
                    CONTROL_TYPE_NEXT_CHANNEL -> {
                        if (contentType == ContentType.CHANNEL) {
                            val items = viewModel.channelListItems.value
                            if (!items.isNullOrEmpty()) {
                                val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                                val nextIndex = (currentIndex + 1).coerceAtMost(items.size - 1)
                                if (nextIndex != currentIndex) switchToChannel(items[nextIndex])
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePipParams(enter: Boolean = false): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle(contentName)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isPlaying = player?.isPlaying == true
            builder.setAutoEnterEnabled(isPlaying)
            builder.setSeamlessResizeEnabled(isPlaying)
        }

        val isPaused = player?.isPlaying != true
        builder.setActions(createPipActions(this, isPaused))
        builder.setSourceRectHint(pipRect)

        player?.videoFormat?.let { format ->
            val height = format.height
            val width = format.width
            if (height > 0 && width > 0) {
                val rational = Rational(width, height).toFloat()
                if (rational in 0.42..2.38) {
                    builder.setAspectRatio(Rational(width, height))
                }
            }
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipActions(context: Context, isPaused: Boolean): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()
        val centerMode = preferencesManager.getCenterControlsMode()

        fun makePendingIntent(requestCode: Int, controlType: Int) =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, controlType),
                PendingIntent.FLAG_IMMUTABLE
            )

        val playPauseAction = if (isPaused) {
            RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_play),
                context.getString(R.string.play), context.getString(R.string.play),
                makePendingIntent(CONTROL_TYPE_PLAY, CONTROL_TYPE_PLAY)
            )
        } else {
            RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_pause),
                context.getString(R.string.pause), context.getString(R.string.pause),
                makePendingIntent(CONTROL_TYPE_PAUSE, CONTROL_TYPE_PAUSE)
            )
        }

        val showNav = centerMode == com.livetvpro.app.data.local.PreferencesManager.CENTER_MODE_NAV_ONLY
                && contentType == ContentType.CHANNEL

        if (showNav) {
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_skip_prev_channel),
                "Previous", "Previous channel",
                makePendingIntent(CONTROL_TYPE_PREV_CHANNEL, CONTROL_TYPE_PREV_CHANNEL)
            ))
            actions.add(playPauseAction)
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_skip_next_channel),
                "Next", "Next channel",
                makePendingIntent(CONTROL_TYPE_NEXT_CHANNEL, CONTROL_TYPE_NEXT_CHANNEL)
            ))
        } else {
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_skip_backward),
                "Rewind", "Rewind 10s",
                makePendingIntent(CONTROL_TYPE_REWIND, CONTROL_TYPE_REWIND)
            ))
            actions.add(playPauseAction)
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_skip_forward),
                "Forward", "Forward 10s",
                makePendingIntent(CONTROL_TYPE_FORWARD, CONTROL_TYPE_FORWARD)
            ))
        }

        return actions
    }

    private fun retryPlayback() {
        errorMessage.value = ""

        playerViewRef.hideController()

        player?.release()
        player = null
        setupPlayer()
    }

    override fun finish() {
        try {
            releasePlayer()
            pipReceiver?.let {
                unregisterReceiver(it)
                pipReceiver = null
            }
            isInPipMode = false
            wasLockedBeforePip = false
            isInPip = false
            super.finish()
        } catch (e: Exception) {
            super.finish()
        }
    }

    @SuppressLint("NewApi")
    private fun showUnlockButton() {

    }

    private fun hideUnlockButton() {

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
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@androidx.compose.runtime.Composable
private fun PlayerActivity.PlayerActivityRoot(
    activity: PlayerActivity,
    controlsState: com.livetvpro.app.ui.player.compose.PlayerControlsState,
    showChannelList: androidx.compose.runtime.MutableState<Boolean>,
    isMuted: Boolean,
    relatedContentState: com.livetvpro.app.ui.player.compose.RelatedContentState,
    links: List<com.livetvpro.app.data.models.LiveEventLink>,
    selectedLinkIndex: Int,
    messageBanner: String,
    messageBannerUrl: String,
    errorMessage: String,
    showSettingsDialog: Boolean,
    showFloatingDialog: Boolean,
    player: androidx.media3.exoplayer.ExoPlayer?,
    preferencesManager: com.livetvpro.app.data.local.PreferencesManager,
    onSettingsDismiss: () -> Unit,
    onFloatingDismiss: () -> Unit,
    onLinkClick: (com.livetvpro.app.data.models.LiveEventLink, Int) -> Unit,
    onChannelClick: (com.livetvpro.app.data.models.Channel) -> Unit,
    onEventClick: (com.livetvpro.app.data.models.LiveEvent, Int) -> Unit,
    onMessageBannerClick: () -> Unit,
) {
    val isLandscape = activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        com.livetvpro.app.utils.DeviceUtils.isTvDevice
    val spanCount = activity.resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
    val eventSpanCount = activity.resources.getInteger(com.livetvpro.app.R.integer.event_span_count)

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize()
    ) {
        if (!isLandscape) {
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        } else {
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier.fillMaxSize()
            )
        }
        if (!isLandscape) {
            com.livetvpro.app.ui.player.compose.PlayerScreen(
                isLandscape = false,
                relatedContentState = relatedContentState,
                links = links,
                selectedLinkIndex = selectedLinkIndex,
                messageBanner = messageBanner,
                messageBannerUrl = messageBannerUrl,
                onLinkClick = onLinkClick,
                onChannelClick = onChannelClick,
                onEventClick = onEventClick,
                onMessageBannerClick = onMessageBannerClick,
                spanCount = spanCount,
                eventSpanCount = eventSpanCount,
            )
        }
    }
    if (errorMessage.isNotBlank()) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(top = (activity.resources.displayMetrics.heightPixels * 0.25f).dp)
        ) {
            androidx.compose.material3.Surface(
                color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 48.dp, vertical = 20.dp)
            ) {
                androidx.compose.material3.Text(
                    text = errorMessage,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily(
                        androidx.compose.ui.text.font.Font(com.livetvpro.app.R.font.bergen_sans)
                    ),
                    fontSize = 15.sp,
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 48.dp, vertical = 20.dp)
                )
            }
        }
    }
    if (showSettingsDialog && player != null) {
        com.livetvpro.app.ui.player.settings.PlayerSettingsDialog(
            player = player,
            onDismiss = onSettingsDismiss,
        )
    }
    if (showFloatingDialog) {
        com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog(
            preferencesManager = preferencesManager,
            onDismiss = onFloatingDismiss,
        )
    }
}

private val Int.dp: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
private val Float.dp: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(this)
private val Float.sp: androidx.compose.ui.unit.TextUnit get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
