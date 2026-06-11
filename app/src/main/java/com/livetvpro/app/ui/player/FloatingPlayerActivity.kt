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
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.databinding.ActivityPlayerBinding
import com.livetvpro.app.data.models.LiveEventLink
import com.livetvpro.app.ui.player.PlayerStreamHelper
import com.livetvpro.app.ui.player.StreamInfo
import com.livetvpro.app.ui.player.compose.PlayerScreen
import com.livetvpro.app.ui.player.compose.RelatedContentState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.livetvpro.app.ui.player.compose.PlayerControls
import com.livetvpro.app.ui.player.compose.PlayerControlsState
import com.livetvpro.app.ui.player.compose.GestureState
import android.media.AudioManager
import com.livetvpro.app.ui.theme.AppThemeContent
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.ui.player.ChannelListCache
import kotlinx.coroutines.delay

@UnstableApi
@AndroidEntryPoint
class FloatingPlayerActivity : BasePlayerActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var floatingComposeView: androidx.compose.ui.platform.ComposeView
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    @javax.inject.Inject
    override lateinit var themeManager: ThemeManager

    @javax.inject.Inject
    lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager

    @javax.inject.Inject
    lateinit var listenerManager: com.livetvpro.app.utils.NativeListenerManager

    override val playerContainer: ConstraintLayout
        get() = binding.playerContainer

    private val relatedContentState = androidx.compose.runtime.mutableStateOf<RelatedContentState>(RelatedContentState.Hidden)
    private val linksState          = androidx.compose.runtime.mutableStateOf<List<LiveEventLink>>(emptyList())
    private val selectedLinkState   = androidx.compose.runtime.mutableStateOf(0)
    private val messageBannerText   = androidx.compose.runtime.mutableStateOf("")
    private val messageBannerUrl    = androidx.compose.runtime.mutableStateOf("")

    private val controlsState = PlayerControlsState(initialVisible = false)
    private var gestureVolume: Int = 100
    private var gestureBrightness: Int = 0

    private var isInPipMode = false
    private var isMuted by mutableStateOf(false)
    private val skipMs = 10_000L
    private var userRequestedPip = false

    private var pipReceiver: BroadcastReceiver? = null
    private var wasLockedBeforePip = false

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

    private var networkPortraitResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    private var resizeModesRestoredFromState = false

    private var savedPlaybackPosition: Long = -1L
    private var isShowingSettingsDialog = false
    private val showSettingsDialogState = androidx.compose.runtime.mutableStateOf(false)

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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition()
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)

        floatingComposeView = androidx.compose.ui.platform.ComposeView(this)
        val rootLayout = android.widget.FrameLayout(this).apply {
            addView(
                binding.root,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )
            addView(
                floatingComposeView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )
        }
        setContentView(rootLayout)

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (DeviceUtils.isTvDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        val currentOrientation = resources.configuration.orientation
        val isLandscape = DeviceUtils.isTvDevice || currentOrientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)
        setupWindowInsets(binding.root)

        parseIntent()

        if (savedInstanceState != null && contentId.isEmpty()) {
            restoreFromBundle(savedInstanceState)
        }


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

        setupComposeControls()
        setupRelatedChannels()
        setupLinksUI()
        setupMessageBanner()
        configurePlayerInteractions()
        setupLockOverlay()

        binding.playerView.useController = false

        binding.progressBar.visibility = View.VISIBLE

        binding.root.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.root.viewTreeObserver.removeOnPreDrawListener(this)
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

            if (freshChannel != null && contentType == ContentType.CHANNEL) {
                channelData = freshChannel
                if (intentIsSports) {
                    viewModel.loadRandomRelatedSports(freshChannel.id)
                } else {
                    val categoryId = intentCategoryId?.takeIf { it.isNotEmpty() } ?: freshChannel.categoryId
                    if (categoryId.isNotEmpty()) {
                        viewModel.loadRandomRelatedChannels(categoryId, freshChannel.id, intentSelectedGroup)
                    }
                }
            }
        }

        viewModel.relatedItems.observe(this) { channels ->
            if (contentType != ContentType.CHANNEL) return@observe
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
        binding.playerView.hideController()
        return
    }

    val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

    val wasControllerVisible = binding.playerView.isControllerFullyVisible
    val isBuffering = player?.playbackState == Player.STATE_BUFFERING

    setupWindowFlags(isLandscape)
    setupSystemUI(isLandscape)
    applyOrientationSettings(isLandscape)
    setSubtitleTextSize()
    updateMessageBannerForOrientation(isLandscape)

    if (wasControllerVisible && !isBuffering) {
        binding.playerView.postDelayed({
            if (player?.playbackState != Player.STATE_BUFFERING) {
                binding.playerView.showController()
            }
        }, 100)
    } else if (isBuffering) {
        binding.playerView.hideController()
    }

    binding.root.post {
        binding.root.requestLayout()
        binding.playerContainer.requestLayout()
        binding.playerView.requestLayout()
    }
    applyAdapterColors()
}

    private fun applyAdapterColors() {  }

    private fun applyOrientationSettings(isLandscape: Boolean) {
        adjustLayoutForOrientation(isLandscape)
        updateLinksForOrientation(isLandscape)
        applyResizeModeForOrientation(isLandscape)
    }

    private fun applyResizeModeForOrientation(isLandscape: Boolean) {
        if (isLandscape) {
            binding.playerView.resizeMode = networkLandscapeResizeMode
        } else {
            binding.playerView.resizeMode = networkPortraitResizeMode
        }
    }

    private fun adjustLayoutForOrientation(isLandscape: Boolean) {
        if (isLandscape) {
            enterFullscreen()

            val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.topMargin = 0
            params.bottomMargin = 0
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

            binding.playerContainer.setPadding(0, 0, 0, 0)
            binding.playerContainer.layoutParams = params

            binding.playerView.controllerAutoShow = true
            binding.playerView.controllerShowTimeoutMs = 3000

        } else {

            if (contentType == ContentType.NETWORK_STREAM) {
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())?.top ?: 0
                } else {
                    @Suppress("DEPRECATION") window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
                }
                val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
                params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.topMargin = topInset
                params.bottomMargin = 0
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.dimensionRatio = null

                binding.playerContainer.setPadding(0, 0, 0, 0)
                binding.playerContainer.layoutParams = params
            } else {
                exitFullscreen()
            }

            binding.playerView.controllerAutoShow = true
            binding.playerView.controllerShowTimeoutMs = 5000

        }

        binding.root.requestLayout()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        releasePlayer()
        parseIntent()
        binding.progressBar.visibility = View.VISIBLE
        setupPlayer()
    }

    override fun onResume() {
        super.onResume()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)

        if (player == null) {
            setupPlayer()
        } else {
            binding.playerView.player = player
        }
        binding.playerView.onResume()
        binding.playerView.requestFocus()
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
        binding.playerView.useController = false

        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.playerView.hideController()

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

        setSubtitleTextSize()

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)

        applyOrientationSettings(isLandscape)

        if (wasLockedBeforePip) {
            controlsState.lock()
            wasLockedBeforePip = false
        }

        binding.playerView.useController = false
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
                        ?: resolvedDrmKeyId?.let { id -> resolvedDrmKey?.let { k -> "$id:$k" } }
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
        binding.linksSection.visibility = View.GONE
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
                    if (intentIsSports) {
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
        setupRelatedChannels()

        if (intentIsSports) {
            viewModel.loadRandomRelatedSports(newChannel.id)
        } else {
            val categoryId = intentCategoryId?.takeIf { it.isNotEmpty() } ?: newChannel.categoryId
            viewModel.loadRandomRelatedChannels(categoryId, newChannel.id, intentSelectedGroup)
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
            setupRelatedChannels()

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
            binding.playerView.onPause()
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
    }

    private fun setupPlayer() {
        if (streamUrl.isBlank()) {
            binding.errorView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }

        val isTransferredFromFloating = intent.getBooleanExtra("use_transferred_player", false)
        if (isTransferredFromFloating && PlayerHolder.player != null) {
            val (transferredPlayer, _, _) = PlayerHolder.retrievePlayer()
            PlayerHolder.clearReferences()
            player = transferredPlayer
            binding.playerView.player = transferredPlayer
            binding.progressBar.visibility = View.GONE
            binding.errorView.visibility = View.GONE
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.errorView.visibility = View.GONE
                        }
                        Player.STATE_READY -> {
                            binding.progressBar.visibility = View.GONE
                            binding.errorView.visibility = View.GONE
                            if (!resizeModesRestoredFromState) {
                                val savedLandscape = preferencesManager.getSavedAspectRatio()
                                val savedPortrait = preferencesManager.getSavedAspectRatioPortrait()
                                if (savedLandscape != -1) networkLandscapeResizeMode = savedLandscape
                                if (savedPortrait != -1) networkPortraitResizeMode = savedPortrait
                                resizeModesRestoredFromState = true
                                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                applyResizeModeForOrientation(isLandscape)
                            }
                            setSubtitleTextSize()
                        }
                        Player.STATE_ENDED -> {
                            binding.progressBar.visibility = View.GONE
                        }
                        else -> {}
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorView.visibility = View.VISIBLE
                }
            }
            playerListener = listener
            transferredPlayer?.addListener(listener)
            return
        }

        val parsed = PlayerStreamHelper.parseStreamUrl(streamUrl)
        val headers = parsed.headers.toMutableMap()
        if (headers["User-Agent"].isNullOrBlank() || headers["User-Agent"] == "Default") {
            headers["User-Agent"] = "okhttp/4.12.0"
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
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)
                .setDrmSessionManagerProvider { clearKeyMgr }
        } else {
            DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val ts = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedChannelCountAdaptiveness(true)
                .build()
        }
        trackSelector = ts

        val exo = ExoPlayer.Builder(this)
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
        binding.playerView.player = exo

        val urlLower = parsed.url.lowercase()
        val mediaItemBuilder = MediaItem.Builder().setUri(parsed.url)
        when {
            urlLower.contains("m3u8") || urlLower.contains("extension=m3u8") ->
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            urlLower.contains(".mpd") || urlLower.contains("/dash/") || urlLower.contains("type=mpd") ->
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            urlLower.contains(".ism") || urlLower.contains(".isml") ->
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SS)
            urlLower.contains(".mp4") || urlLower.contains(".m4v") ->
                mediaItemBuilder.setMimeType("video/mp4")
            urlLower.contains(".ts") || urlLower.contains("/ts") ->
                mediaItemBuilder.setMimeType("video/mp2t")
            urlLower.contains(".mkv") ->
                mediaItemBuilder.setMimeType("video/x-matroska")
        }
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
                        binding.progressBar.visibility = View.VISIBLE
                        binding.errorView.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        binding.progressBar.visibility = View.GONE
                        binding.errorView.visibility = View.GONE
                        if (!resizeModesRestoredFromState) {
                            val savedLandscape = preferencesManager.getSavedAspectRatio()
                            val savedPortrait = preferencesManager.getSavedAspectRatioPortrait()
                            if (savedLandscape != -1) networkLandscapeResizeMode = savedLandscape
                            if (savedPortrait != -1) networkPortraitResizeMode = savedPortrait
                            resizeModesRestoredFromState = true
                            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            applyResizeModeForOrientation(isLandscape)
                        }
                        setSubtitleTextSize()
                    }
                    Player.STATE_ENDED -> {
                        binding.progressBar.visibility = View.GONE
                    }
                    else -> {}
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.errorView.visibility = View.VISIBLE
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

    private fun toggleMute() {
        isMuted = PlayerStreamHelper.toggleMute(player, isMuted)
    }

    private fun setupComposeControls() {
        floatingComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppThemeContent(themeManager) {
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

                    var showChannelList by remember { mutableStateOf(false) }
                    val channelListItems by viewModel.channelListItems.observeAsState(emptyList())
                    val isChannelListAvailable = contentType == ContentType.CHANNEL && channelListItems.isNotEmpty() && (isLandscape || DeviceUtils.isTvDevice)

                    val pipSupported = !DeviceUtils.isTvDevice && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        isPipSupported()
                    } else { false }

                    val playerControlsComposable: @androidx.compose.runtime.Composable () -> Unit = {
                        PlayerControls(
                            state = controlsState,
                            isPlaying = isPlaying,
                            isMuted = isMuted,
                            currentPosition = currentPosition,
                            duration = duration,
                            bufferedPosition = bufferedPosition,
                            channelName = contentName,
                            showPipButton = pipSupported,
                            showAspectRatioButton = true,
                            isLandscape = isLandscape,
                            isTvMode = DeviceUtils.isTvDevice,
                            centerControlsMode = preferencesManager.getCenterControlsMode(),
                            isNetworkStream = contentType == ContentType.NETWORK_STREAM,
                            isChannelListAvailable = isChannelListAvailable,
                            onBackClick = { finish() },
                            onPipClick = {
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
                                    val serviceIntent = Intent(this@FloatingPlayerActivity, FloatingPlayerService::class.java).apply {
                                        if (currentChannel != null) putExtra(FloatingPlayerService.EXTRA_CHANNEL, currentChannel)
                                        if (currentEvent != null) putExtra(FloatingPlayerService.EXTRA_EVENT, currentEvent)
                                        putExtra(FloatingPlayerService.EXTRA_RESTORE_POSITION, true)
                                        putExtra("use_transferred_player", true)
                                        if (sourceInstanceId != null) putExtra(FloatingPlayerService.EXTRA_INSTANCE_ID, sourceInstanceId)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                                    else startService(serviceIntent)
                                    player = null
                                    finish()
                                }
                            },
                            onSettingsClick = { showSettingsDialog() },
                            onMuteClick = { toggleMute() },
                            onLockClick = { _ -> },
                            onChannelListClick = { showChannelList = true },
                            onPlayPauseClick = {
                                player?.let {
                                    val hasError = binding.errorView.visibility == View.VISIBLE
                                    val hasEnded = it.playbackState == Player.STATE_ENDED
                                    if (hasError || hasEnded) retryPlayback()
                                    else if (it.isPlaying) it.pause() else it.play()
                                }
                            },
                            onSeek = { position -> player?.seekTo(position) },
                            onRewindClick = {
                                player?.let { it.seekTo((it.currentPosition - skipMs).coerceAtLeast(0L)) }
                            },
                            onForwardClick = {
                                player?.let {
                                    val newPosition = it.currentPosition + skipMs
                                    if (it.isCurrentWindowLive && it.duration != C.TIME_UNSET && newPosition >= it.duration) it.seekTo(it.duration)
                                    else it.seekTo(newPosition)
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
                            onAspectRatioClick = { cycleAspectRatio() },
                            onFullscreenClick = { toggleFullscreen() },
                            onVolumeSwipe = { vol ->
                                gestureVolume = vol
                                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (vol / 100f * max).toInt(), 0)
                            },
                            onBrightnessSwipe = { bri ->
                                gestureBrightness = bri
                                val lp = window.attributes
                                lp.screenBrightness = if (bri == 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else bri / 100f
                                window.attributes = lp
                            },
                            initialVolume = gestureVolume,
                            initialBrightness = gestureBrightness,
                        )
                    }

                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!isLandscape) {
                            val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                                .asPaddingValues().calculateTopPadding()
                            val navBarHeight = androidx.compose.foundation.layout.WindowInsets.navigationBars
                                .asPaddingValues().calculateBottomPadding()
                            val isNetworkStream = contentType == ContentType.NETWORK_STREAM
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = statusBarHeight)
                                    .then(
                                        if (isNetworkStream) Modifier.weight(1f)
                                        else Modifier.aspectRatio(16f / 9f)
                                    )
                            ) {
                                playerControlsComposable()

                                if (isChannelListAvailable) {
                                    com.livetvpro.app.ui.player.compose.ChannelListPanel(
                                        visible = showChannelList,
                                        channels = channelListItems,
                                        currentChannelId = contentId,
                                        onChannelClick = { channel -> switchToChannel(channel) },
                                        onDismiss = { showChannelList = false },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (showSettingsDialogState.value) {
                                    player?.let { exo ->
                                        com.livetvpro.app.ui.player.settings.PlayerSettingsDialog(
                                            player = exo,
                                            onDismiss = {
                                                showSettingsDialogState.value = false
                                                isShowingSettingsDialog = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (!isNetworkStream) {
                            PlayerScreen(
                                isLandscape = false,
                                relatedContentState = relatedContentState.value,
                                links = linksState.value,
                                selectedLinkIndex = selectedLinkState.value,
                                messageBanner = messageBannerText.value,
                                messageBannerUrl = messageBannerUrl.value,
                                onLinkClick = { link, pos -> switchToLink(link, pos) },
                                onChannelClick = { channel -> switchToChannel(channel) },
                                onEventClick = { event, idx -> switchToEventFromLiveEvent(event, idx) },
                                onMessageBannerClick = {
                                    val url = messageBannerUrl.value
                                    if (url.isNotBlank()) {
                                        try {
                                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                        } catch (_: Exception) {}
                                    }
                                },
                                spanCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count),
                                eventSpanCount = resources.getInteger(com.livetvpro.app.R.integer.event_span_count),
                            )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                playerControlsComposable()

                                if (linksState.value.size > 1) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = controlsState.isVisible && !controlsState.isLocked,
                                        enter = androidx.compose.animation.fadeIn(),
                                        exit = androidx.compose.animation.fadeOut(),
                                        modifier = Modifier
                                            .align(androidx.compose.ui.Alignment.TopCenter)
                                            .padding(top = 44.dp),
                                    ) {
                                        PlayerScreen(
                                            isLandscape = true,
                                            relatedContentState = RelatedContentState.Hidden,
                                            links = linksState.value,
                                            selectedLinkIndex = selectedLinkState.value,
                                            messageBanner = "",
                                            messageBannerUrl = "",
                                            onLinkClick = { link, pos -> switchToLink(link, pos) },
                                            onChannelClick = { channel -> switchToChannel(channel) },
                                            onEventClick = { event, idx -> switchToEventFromLiveEvent(event, idx) },
                                            onMessageBannerClick = {},
                                            spanCount = resources.getInteger(com.livetvpro.app.R.integer.grid_column_count),
                                            eventSpanCount = resources.getInteger(com.livetvpro.app.R.integer.event_span_count),
                                        )
                                    }
                                }

                                if (isChannelListAvailable) {
                                    com.livetvpro.app.ui.player.compose.ChannelListPanel(
                                        visible = showChannelList,
                                        channels = channelListItems,
                                        currentChannelId = contentId,
                                        onChannelClick = { channel -> switchToChannel(channel) },
                                        onDismiss = { showChannelList = false },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (showSettingsDialogState.value) {
                                    player?.let { exo ->
                                        com.livetvpro.app.ui.player.settings.PlayerSettingsDialog(
                                            player = exo,
                                            onDismiss = {
                                                showSettingsDialogState.value = false
                                                isShowingSettingsDialog = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cycleAspectRatio() {
        val isLandscape = DeviceUtils.isTvDevice || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val current = binding.playerView.resizeMode
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

        binding.playerView.resizeMode = next
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

    private fun updateMessageBannerForOrientation(isLandscape: Boolean) {
    }

    private fun configurePlayerInteractions() {
        binding.playerView.apply {
            setControllerHideDuringAds(false)
            controllerShowTimeoutMs = 5000
            controllerHideOnTouch = true
        }
    }

    private fun setupLockOverlay() {

    }

    private fun showUnlockButton() {

    }

    private fun hideUnlockButton() {

    }

    private fun toggleLock() {
        if (controlsState.isLocked) {

            controlsState.unlock(lifecycleScope)
        } else {
            controlsState.lock()
        }
        binding.playerView.useController = false
    }

    private fun toggleFullscreen() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun exitFullscreen() {
        windowInsetsController.apply {
            show(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }

        val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.dimensionRatio = "H,16:9"
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        binding.playerContainer.layoutParams = params
        binding.playerContainer.visibility = View.VISIBLE

    }

    private fun enterFullscreen() {
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding.root.setPadding(0, 0, 0, 0)
        binding.playerContainer.setPadding(0, 0, 0, 0)

        val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.topMargin = 0
        params.dimensionRatio = null

        binding.playerContainer.layoutParams = params
    }

    private fun setSubtitleTextSize() {
        val subtitleView = binding.playerView.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
    }

    private fun setSubtitleTextSizePiP() {
        val subtitleView = binding.playerView.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        player?.let {
            if (!it.isPlaying) {
                it.play()
            }
        }

        binding.playerView.useController = false

        setSubtitleTextSizePiP()

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

            val actions = buildPipActions()
            builder.setActions(actions)

            val pipSourceRect = android.graphics.Rect()
            binding.playerView.getGlobalVisibleRect(pipSourceRect)
            if (!pipSourceRect.isEmpty) {
                builder.setSourceRectHint(pipSourceRect)
            }

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
                val hasError = binding.errorView.visibility == View.VISIBLE
                val hasEnded = currentPlayer.playbackState == Player.STATE_ENDED

                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> {
                        if (hasError || hasEnded) {
                            retryPlayback()
                        } else {
                            currentPlayer.play()
                        }
                        updatePipParams()
                    }
                    CONTROL_TYPE_PAUSE -> {
                        if (hasError || hasEnded) {
                            retryPlayback()
                        } else {
                            currentPlayer.pause()
                        }
                        updatePipParams()
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
        binding.errorView.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        binding.playerView.hideController()

        player?.release()
        player = null
        setupPlayer()
    }

    override fun finish() {
        try {
            FloatingPlayerService.showAll(this)

            if (player != null) {
                releasePlayer()
            }
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
