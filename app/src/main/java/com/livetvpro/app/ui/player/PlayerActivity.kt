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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.databinding.ActivityPlayerBinding
import com.livetvpro.app.ui.adapters.RelatedChannelAdapter
import com.livetvpro.app.ui.adapters.LinkChipAdapter
import com.livetvpro.app.ui.adapters.LiveEventAdapter
import com.livetvpro.app.data.models.LiveEventLink
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.livetvpro.app.ui.player.compose.PlayerControls
import com.livetvpro.app.ui.player.compose.PlayerControlsState
import com.livetvpro.app.ui.player.compose.GestureState
import android.media.AudioManager
import com.livetvpro.app.ui.theme.AppTheme
import com.livetvpro.app.utils.DeviceUtils
import kotlinx.coroutines.delay

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private val mainHandler = Handler(Looper.getMainLooper())
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null

    @javax.inject.Inject
    lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager

    @javax.inject.Inject
    lateinit var listenerManager: com.livetvpro.app.utils.NativeListenerManager

    private lateinit var relatedChannelsAdapter: RelatedChannelAdapter
    private var relatedChannels = listOf<Channel>()
    private lateinit var relatedEventsAdapter: LiveEventAdapter
    private lateinit var linkChipAdapter: LinkChipAdapter

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private val controlsState = PlayerControlsState()
    private var showChannelList = mutableStateOf(false)
    private var gestureVolume: Int = 100
    private var gestureBrightness: Int = 0

    private var isInPipMode = false
    private var isEnteringPip = false
    private var isMuted = false
    private val skipMs = 10_000L

    private var networkPortraitResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var networkLandscapeResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

    private var pipReceiver: BroadcastReceiver? = null
    private var wasLockedBeforePip = false
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
    private val overlayHideRunnable = Runnable { binding.channelNumberOverlay?.visibility = View.GONE }
    private var pendingChannelIndex: Int = -1
    private var pendingChannelDirection: Int = 0 // +1 = up, -1 = down, 0 = none
    private var pendingChannelNumber: Int = -1   // number pad target when list wasn't ready
    private var channelData: Channel? = null
    private var eventData: LiveEvent? = null
    private var allEventLinks = listOf<LiveEventLink>()
    private var currentLinkIndex = 0
    private var contentId: String = ""
    private var contentName: String = ""
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
        private const val EXTRA_RELATED_CHANNELS = "extra_related_channels"
        private const val EXTRA_CATEGORY_ID = "extra_category_id"
        private const val EXTRA_IS_SPORTS = "extra_is_sports"
        private const val EXTRA_SELECTED_GROUP = "extra_selected_group"

        private const val ACTION_MEDIA_CONTROL = "com.livetvpro.app.MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
        private const val CONTROL_TYPE_REWIND = 3
        private const val CONTROL_TYPE_FORWARD = 4

        private const val PIP_INTENTS_FILTER = "com.livetvpro.app.PIP_CONTROL"
        private const val PIP_INTENT_ACTION = "pip_action"
        private const val PIP_PLAY = 1
        private const val PIP_PAUSE = 2
        private const val PIP_FR = 3
        private const val PIP_FF = 4

        var isInPip: Boolean = false

        fun startWithChannel(context: Context, channel: Channel, linkIndex: Int = -1, relatedChannels: ArrayList<Channel>? = null, categoryId: String? = null, selectedGroup: String? = null, isSports: Boolean = false) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, channel as Parcelable)
                putExtra(EXTRA_SELECTED_LINK_INDEX, linkIndex)
                relatedChannels?.let {
                    putParcelableArrayListExtra(EXTRA_RELATED_CHANNELS, it)
                }
                categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) }
                selectedGroup?.let { putExtra(EXTRA_SELECTED_GROUP, it) }
                putExtra(EXTRA_IS_SPORTS, isSports)
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
            newEvent != null   -> switchToEventFromLiveEvent(newEvent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition()
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            viewModel.refreshChannelData(contentId)
            viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
        }

        applyResizeModeForOrientation(isLandscape)
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

        binding.playerView.useController = false

        if (DeviceUtils.isTvDevice) {
            binding.relatedLoadingProgress.visibility = View.GONE
            binding.relatedChannelsSection.visibility = View.GONE
        } else if (!isLandscape) {
            binding.relatedLoadingProgress.visibility = View.VISIBLE
            binding.relatedChannelsRecycler.visibility = View.GONE
        }

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

            // Execute pending number pad navigation first (takes priority over direction)
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
                    // Number out of range — just hide overlay
                    binding.channelNumberOverlay?.visibility = View.GONE
                }
                return@observe
            }

            // Execute pending direction navigation
            val direction = pendingChannelDirection
            if (direction != 0) {
                pendingChannelDirection = 0
                val currentIndex = items.indexOfFirst { it.id == contentId }.takeIf { it != -1 } ?: 0
                val targetIndex = (currentIndex + direction).coerceIn(0, items.size - 1)
                val targetChannel = items[targetIndex]
                if (targetChannel.id != contentId) {
                    switchToChannel(targetChannel)
                }
                // Always update overlay — clears "..." and shows real channel info
                showChannelOverlay((targetIndex + 1).toString(), targetChannel)
                channelNumberHandler.removeCallbacks(overlayHideRunnable)
                channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
            }
        }

        viewModel.relatedItems.observe(this) { channels ->
            relatedChannels = channels
            relatedChannelsAdapter.submitList(channels)
            binding.relatedChannelsSection.visibility = if (channels.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.relatedLoadingProgress.visibility = View.GONE
            binding.relatedChannelsRecycler.visibility = View.VISIBLE
        }

        viewModel.relatedLiveEvents.observe(this) { liveEvents ->
            if (contentType == ContentType.EVENT && ::relatedEventsAdapter.isInitialized) {
                relatedEventsAdapter.updateData(liveEvents)
                binding.relatedChannelsSection.visibility = if (liveEvents.isEmpty()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                binding.relatedLoadingProgress.visibility = View.GONE
                binding.relatedChannelsRecycler.visibility = View.VISIBLE
            }
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
            binding.root.setOnApplyWindowInsetsListener { view, insets ->
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                val params = binding.playerContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
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
                binding.playerContainer.layoutParams = params
                binding.playerContainer.setPadding(0, 0, 0, 0)
                insets
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            binding.playerView.hideController()
            return
        }

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)
        applyResizeModeForOrientation(isLandscape)
        applyOrientationSettings(isLandscape)
        setSubtitleTextSize()
        updateMessageBannerForOrientation(isLandscape)
        updateLinksForOrientation(isLandscape)

        if (player?.playbackState == Player.STATE_BUFFERING) {
            binding.playerView.hideController()
        }

        binding.root.post {
            binding.root.requestLayout()
            binding.playerContainer.requestLayout()
            binding.playerView.requestLayout()
        }
    }

    private fun applyResizeModeForOrientation(isLandscape: Boolean) {
        if (contentType == ContentType.NETWORK_STREAM) {
            binding.playerView.resizeMode =
                if (isLandscape) networkLandscapeResizeMode else networkPortraitResizeMode
        }
    }

    private fun applyOrientationSettings(isLandscape: Boolean) {
        adjustLayoutForOrientation(isLandscape)
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

            binding.playerView.controllerAutoShow = false
            binding.playerView.controllerShowTimeoutMs = 3000

        } else {

            if (contentType == ContentType.NETWORK_STREAM) {
                val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
                params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.topMargin = 0
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

            binding.playerView.controllerAutoShow = false
            binding.playerView.controllerShowTimeoutMs = 5000

            val relatedParams = binding.relatedChannelsSection.layoutParams as ConstraintLayout.LayoutParams
            relatedParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            relatedParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            relatedParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            relatedParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            relatedParams.topToBottom = binding.messageBannerContainer.id
            relatedParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            binding.relatedChannelsSection.layoutParams = relatedParams
        }

        binding.root.requestLayout()
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
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            setPictureInPictureParams(updatePipParams())
        }
    }

    override fun onResume() {
        super.onResume()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationSettings(isLandscape)

        if (player == null) {
            setupPlayer()
        }
        if (contentType == ContentType.CHANNEL && viewModel.channelListItems.value.isNullOrEmpty()) {
            viewModel.loadAllChannelsForList(intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: "")
        }
        binding.playerView.onResume()
        binding.playerView.player = player
        binding.playerView.requestFocus()
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
        binding.playerControlsCompose.visibility = View.GONE

        val pipParams = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
        pipParams.dimensionRatio = null
        pipParams.topMargin = 0
        pipParams.bottomMargin = 0
        pipParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        pipParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        binding.playerContainer.layoutParams = pipParams

        setupPipReceiver()

        super.onPictureInPictureModeChanged(true, newConfig)
    }

    private fun exitPipUIMode(newConfig: Configuration) {
        setSubtitleTextSize()

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupWindowFlags(isLandscape)
        setupSystemUI(isLandscape)

        applyOrientationSettings(isLandscape)

        binding.playerControlsCompose.visibility = View.VISIBLE

        if (!isLandscape) {
            val params = binding.playerContainer.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = "H,16:9"
            params.topMargin = 0
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            binding.playerContainer.layoutParams = params

            val hasRelated = relatedChannels.isNotEmpty() ||
                (contentType == ContentType.EVENT && ::relatedEventsAdapter.isInitialized)
            if (hasRelated) {
                binding.relatedChannelsSection.visibility = View.VISIBLE
                binding.relatedChannelsRecycler.visibility = View.VISIBLE
                binding.relatedLoadingProgress.visibility = View.GONE
            }
        }

        setupComposeControls()

        if (wasLockedBeforePip) {
            controlsState.isLocked = true
            wasLockedBeforePip = false
        } else {
            controlsState.isLocked = false
        }

        binding.playerView.useController = false
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
        binding.channelNumberOverlay?.visibility = View.GONE
    }

    // Clears typed number input only. Preserves pendingChannelIndex and pendingChannelDirection for hold-to-scroll.
    private fun clearNumberTyping() {
        channelNumberInput = ""
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.removeCallbacks(overlayHideRunnable)
        // Don't hide overlay — caller will immediately show updated overlay
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
                    contentType != ContentType.CHANNEL -> { /* non-channel, ignore */ }

                    // Normal path: list was ready during keyDown, index computed
                    index != -1 && !items.isNullOrEmpty() && index in items.indices -> {
                        pendingChannelDirection = 0
                        val targetChannel = items[index]
                        if (targetChannel.id != contentId) switchToChannel(targetChannel)
                        channelNumberHandler.removeCallbacks(overlayHideRunnable)
                        channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
                    }

                    // List loaded between keyDown and keyUp — compute now
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

                    // List still empty — keep direction queued, trigger load
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
                    val hasError = binding.errorView.visibility == View.VISIBLE
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
                        val hasError = binding.errorView.visibility == View.VISIBLE
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
                pendingChannelDirection = 0  // typing overrides any queued direction
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
        val overlay = binding.channelNumberOverlay ?: return
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
            binding.channelNumberOverlay?.visibility = View.GONE
            return
        }
        if (contentType != ContentType.CHANNEL) {
            binding.channelNumberOverlay?.visibility = View.GONE
            return
        }
        val items = viewModel.channelListItems.value
        if (items.isNullOrEmpty()) {
            // Store the number so the observer can execute it once the list loads
            pendingChannelNumber = number
            showChannelOverlay("...", null)
            viewModel.loadAllChannelsForList(
                intentCategoryId?.takeIf { it.isNotEmpty() } ?: channelData?.categoryId ?: ""
            )
            channelNumberHandler.postDelayed(overlayHideRunnable, 3000)
            return
        }
        val index = number - 1
        binding.channelNumberOverlay?.visibility = View.GONE
        if (index in items.indices) {
            switchToChannel(items[index])
            showChannelOverlay((index + 1).toString(), items[index])
            channelNumberHandler.removeCallbacks(overlayHideRunnable)
            channelNumberHandler.postDelayed(overlayHideRunnable, 2000)
        }
    }

    private fun setupComposeControls() {
        binding.playerControlsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
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
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    var showChannelList by this@PlayerActivity.showChannelList
                    val channelListItems by viewModel.channelListItems.observeAsState(emptyList())
                    val isChannelListAvailable = contentType == ContentType.CHANNEL && channelListItems.isNotEmpty() && (isLandscape || DeviceUtils.isTvDevice)

                    LaunchedEffect(controlsState.isVisible, controlsState.isLocked, isLandscape, showChannelList) {
                        if (isLandscape) {
                            val landscapeLinksRecycler = binding.playerContainer.findViewById<RecyclerView>(R.id.exo_links_recycler)
                            val chipsVisible = controlsState.isVisible && !controlsState.isLocked && !showChannelList
                            landscapeLinksRecycler?.visibility = if (chipsVisible) View.VISIBLE else View.GONE
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = ViewTreeObserver.OnGlobalLayoutListener {
                            val rect = Rect()
                            val surface = binding.playerView.videoSurfaceView
                            val target = surface ?: binding.playerView
                            target.getGlobalVisibleRect(rect)
                            if (!rect.isEmpty) {
                                pipRect = rect
                            }
                        }
                        binding.playerView.viewTreeObserver.addOnGlobalLayoutListener(listener)

                        onDispose {
                            binding.playerView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
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
                                    val hasError = binding.errorView.visibility == View.VISIBLE
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
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val current = binding.playerView.resizeMode
        val next = when (current) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT   -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM  -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else                                     -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        if (contentType == ContentType.NETWORK_STREAM) {
            if (isLandscape) networkLandscapeResizeMode = next
            else networkPortraitResizeMode = next
        }

        binding.playerView.resizeMode = next
    }

    private fun showSettingsDialog() {
        val exoPlayer = player ?: return
        if (isFinishing || isDestroyed) return
        try {
            val dialog = com.livetvpro.app.ui.player.settings.PlayerSettingsDialog(this, exoPlayer)
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "Error showing settings dialog", e)
        }
    }

    private fun parseIntent() {
        val isExternalView = intent.action == android.content.Intent.ACTION_VIEW && intent.data != null
        if (isExternalView) {
            val uri = intent.data!!
            contentType = ContentType.NETWORK_STREAM
            contentName = uri.lastPathSegment ?: "Stream"
            contentId = "external_${System.currentTimeMillis()}"
            streamUrl = uri.toString()
            allEventLinks = listOf(
                com.livetvpro.app.data.models.LiveEventLink(
                    quality = "Auto",
                    url = uri.toString(),
                    cookie = "",
                    referer = "",
                    origin = "",
                    userAgent = "Default",
                    drmScheme = "",
                    drmLicenseUrl = ""
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
                allEventLinks = listOf(
                    LiveEventLink(
                        quality = "Network Stream",
                        url = parsed.url,
                        cookie = parsed.headers["Cookie"] ?: "",
                        referer = parsed.headers["Referer"] ?: "",
                        origin = parsed.headers["Origin"] ?: "",
                        userAgent = parsed.headers["User-Agent"] ?: "Default",
                        drmScheme = parsed.drmScheme,
                        drmLicenseUrl = parsed.drmLicenseUrl
                    )
                )
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

        val passedRelatedChannels: List<Channel>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_RELATED_CHANNELS, Channel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_RELATED_CHANNELS)
        }

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
            binding.relatedChannelsSection.visibility = View.GONE
            return
        }

        if (contentType == ContentType.EVENT) {
            relatedEventsAdapter = LiveEventAdapter(
                context = this,
                events = emptyList(),
                preferencesManager = preferencesManager,
                onEventClick = { event, linkIndex ->
                    switchToEventFromLiveEvent(event)
                }
            )

            binding.relatedChannelsRecycler.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.event_span_count))
            binding.relatedChannelsRecycler.adapter = relatedEventsAdapter
        } else {
            relatedChannelsAdapter = RelatedChannelAdapter { relatedItem ->
                switchToChannel(relatedItem)
            }

            binding.relatedChannelsRecycler.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.grid_column_count))
            binding.relatedChannelsRecycler.adapter = relatedChannelsAdapter
        }
    }

    private fun setupLinksUI() {
        linkChipAdapter = LinkChipAdapter { link, position -> switchToLink(link, position) }

        val portraitLinksRecycler = binding.linksRecyclerView
        portraitLinksRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        portraitLinksRecycler.adapter = linkChipAdapter

        val landscapeLinksRecycler = binding.playerContainer.findViewById<RecyclerView>(R.id.exo_links_recycler)
        landscapeLinksRecycler?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val landscapeLinkAdapter = LinkChipAdapter { link, position -> switchToLink(link, position) }
        landscapeLinksRecycler?.adapter = landscapeLinkAdapter
        landscapeLinksRecycler?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0) controlsState.show(lifecycleScope)
            }
        })

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (allEventLinks.size > 1) {
            if (isLandscape) {
                binding.linksSection.visibility = View.GONE
                landscapeLinksRecycler?.visibility = View.VISIBLE
                landscapeLinkAdapter.submitList(allEventLinks)
                landscapeLinkAdapter.setSelectedPosition(currentLinkIndex)
            } else {
                binding.linksSection.visibility = View.VISIBLE
                landscapeLinksRecycler?.visibility = View.GONE
                linkChipAdapter.submitList(allEventLinks)
                linkChipAdapter.setSelectedPosition(currentLinkIndex)
            }
        } else {
            binding.linksSection.visibility = View.GONE
            landscapeLinksRecycler?.visibility = View.GONE
        }
    }

    private fun updateLinksForOrientation(isLandscape: Boolean) {
        if (!::linkChipAdapter.isInitialized) return
        val landscapeLinksRecycler = binding.playerContainer.findViewById<RecyclerView>(R.id.exo_links_recycler)

        if (allEventLinks.size > 1) {
            if (isLandscape) {
                binding.linksSection.visibility = View.GONE
                val chipsVisible = controlsState.isVisible && !controlsState.isLocked
                landscapeLinksRecycler?.visibility = if (chipsVisible) View.VISIBLE else View.GONE
                val landscapeAdapter = landscapeLinksRecycler?.adapter as? LinkChipAdapter
                landscapeAdapter?.submitList(allEventLinks)
                landscapeAdapter?.setSelectedPosition(currentLinkIndex)
            } else {
                binding.linksSection.visibility = View.VISIBLE
                landscapeLinksRecycler?.visibility = View.GONE
                linkChipAdapter.submitList(allEventLinks)
                linkChipAdapter.setSelectedPosition(currentLinkIndex)
            }
        } else {
            binding.linksSection.visibility = View.GONE
            landscapeLinksRecycler?.visibility = View.GONE
        }
    }

    private fun loadRelatedContent() {
        if (DeviceUtils.isTvDevice) return
        when (contentType) {
            ContentType.CHANNEL -> {
                channelData?.let { channel ->
                    val passedRelatedChannels: List<Channel>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(EXTRA_RELATED_CHANNELS, Channel::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(EXTRA_RELATED_CHANNELS)
                    }

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

        binding.relatedLoadingProgress.visibility = View.VISIBLE
        binding.relatedChannelsRecycler.visibility = View.GONE
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

    private fun switchToEventFromLiveEvent(newEvent: LiveEvent) {
        try {
            releasePlayer()

            eventData = newEvent
            channelData = null
            contentType = ContentType.EVENT
            contentId = newEvent.id
            contentName = newEvent.title.ifEmpty { "${newEvent.team1Name} vs ${newEvent.team2Name}" }

            allEventLinks = newEvent.links

            if (allEventLinks.isNotEmpty()) {
                currentLinkIndex = 0
                streamUrl = allEventLinks.firstOrNull()?.let { buildStreamUrl(it) } ?: ""
            } else {
                currentLinkIndex = 0
                streamUrl = ""
            }

            setupPlayer()
            setupLinksUI()

            binding.relatedLoadingProgress.visibility = View.VISIBLE
            binding.relatedChannelsRecycler.visibility = View.GONE

            viewModel.loadRelatedEvents(newEvent.id)

        } catch (e: Exception) {
        }
    }

    private fun switchToLink(link: LiveEventLink, position: Int) {
        currentLinkIndex = position
        streamUrl = buildStreamUrl(link)
        if (::linkChipAdapter.isInitialized) {
            linkChipAdapter.setSelectedPosition(position)
        }
        val landscapeLinksRecycler = binding.playerContainer.findViewById<RecyclerView>(R.id.exo_links_recycler)
        val landscapeAdapter = landscapeLinksRecycler?.adapter as? LinkChipAdapter
        landscapeAdapter?.setSelectedPosition(position)
        releasePlayer()
        setupPlayer()
    }

    override fun onPause() {
        super.onPause()
        cancelNumberInput()

        val isPip = isEnteringPip ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
        if (!isPip) {
            binding.playerView.onPause()
            player?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isInPipMode) {
            if (isFinishing) {
                finish()
            } else {
                // User closed the PiP window via the X button — stop audio and clean up
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
        val pipeIndex = streamUrl.indexOf('|')
        if (pipeIndex == -1) {
            return StreamInfo(streamUrl, mapOf(), null, null, null, null)
        }

        val url = streamUrl.substring(0, pipeIndex).trim()
        val rawParams = streamUrl.substring(pipeIndex + 1).trim()

        val parts = buildList {
            for (segment in rawParams.split("|")) {
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
        link.drmScheme?.let { if (it.isNotEmpty()) params.add("drmScheme=$it") }
        link.drmLicenseUrl?.let { if (it.isNotEmpty()) params.add("drmLicense=$it") }

        if (params.isNotEmpty()) {
            url += "|" + params.joinToString("|")
        }

        return url
    }

    private fun setupPlayer() {
        if (player != null) return
        binding.errorView.visibility = View.GONE
        binding.errorText.text = ""
        binding.progressBar.visibility = View.VISIBLE

        binding.playerView.hideController()

        if (DeviceUtils.isTvDevice) {
            binding.root.findViewById<com.google.android.material.button.MaterialButton?>(R.id.btn_error_retry)
                ?.setOnClickListener { retryPlayback() }
        }

        trackSelector = DefaultTrackSelector(this)

        try {
            val streamInfo = parseStreamUrl(streamUrl)

            if (streamInfo.url.isBlank()) {
                showError("Invalid stream URL")
                return
            }

            val headers = streamInfo.headers.toMutableMap()
            if (!headers.containsKey("User-Agent")) {
                headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)

            val clearKeyMgr = when {
                streamInfo.drmScheme != "clearkey" -> null
                streamInfo.drmKeyId != null && streamInfo.drmKey != null ->
                    createClearKeyDrmManager(streamInfo.drmKeyId, streamInfo.drmKey)
                streamInfo.drmLicenseUrl?.trimStart()?.startsWith("{") == true ->
                    createClearKeyDrmManagerFromJwk(streamInfo.drmLicenseUrl)
                streamInfo.drmLicenseUrl?.startsWith("http", ignoreCase = true) == true ->
                    createClearKeyServerDrmManager(streamInfo.drmLicenseUrl, headers)
                else -> null
            }
            val mediaSourceFactory = if (clearKeyMgr != null) {
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
                    .setDrmSessionManagerProvider { clearKeyMgr }
            } else {
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
            }

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector!!)
                .setMediaSourceFactory(mediaSourceFactory)
                .setSeekBackIncrementMs(skipMs)
                .setSeekForwardIncrementMs(skipMs)
                .build().also { exo ->
                    binding.playerView.player = exo
                    applyResizeModeForOrientation(
                        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    )

                    binding.playerView.hideController()

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
                    }

                    if ((streamInfo.drmScheme == "widevine" || streamInfo.drmScheme == "playready")
                        && streamInfo.drmLicenseUrl != null) {
                        val drmUuid = if (streamInfo.drmScheme == "widevine") C.WIDEVINE_UUID else C.PLAYREADY_UUID
                        val licenseHeaders = headers.filter { (k, _) -> k != "Referer" && k != "Origin" }
                        mediaItemBuilder.setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(drmUuid)
                                .setLicenseUri(streamInfo.drmLicenseUrl)
                                .setLicenseRequestHeaders(licenseHeaders)
                                .setForceDefaultLicenseUri(true)
                                .build()
                        )
                    }

                    val mediaItem = mediaItemBuilder.build()
                    exo.setMediaItem(mediaItem)
                    exo.prepare()
                    exo.playWhenReady = true

                    playerListener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    binding.progressBar.visibility = View.GONE
                                    binding.errorView.visibility = View.GONE
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
                                }
                                Player.STATE_BUFFERING -> {
                                    binding.progressBar.visibility = View.VISIBLE
                                    binding.errorView.visibility = View.GONE
                                }
                                Player.STATE_ENDED -> {
                                    binding.progressBar.visibility = View.GONE
                                    binding.playerView.showController()
                                }
                                Player.STATE_IDLE -> {}
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
                            binding.progressBar.visibility = View.GONE

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
        binding.progressBar.visibility = View.GONE

        binding.errorText.apply {
            text = message
            typeface = try {
                resources.getFont(R.font.bergen_sans)
            } catch (e: Exception) {
                android.graphics.Typeface.DEFAULT
            }
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setPadding(48, 20, 48, 20)
            setBackgroundResource(R.drawable.error_message_background)
            elevation = 0f
        }

        val layoutParams = binding.errorView.layoutParams
        if (layoutParams is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            layoutParams.verticalBias = 0.35f
            binding.errorView.layoutParams = layoutParams
        }

        binding.errorView.visibility = View.VISIBLE
    }

    private fun createClearKeyDrmManager(keyIdHex: String, keyHex: String): DefaultDrmSessionManager? {
        return try {
            val clearKeyUuid = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")
            val keyIdBytes = hexToBytes(keyIdHex)
            val keyBytes = hexToBytes(keyHex)

            val keyIdBase64 = android.util.Base64.encodeToString(
                keyIdBytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            val keyBase64 = android.util.Base64.encodeToString(
                keyBytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )

            val jwkResponse = """
            {
                "keys": [
                    {
                        "kty": "oct",
                        "k": "$keyBase64",
                        "kid": "$keyIdBase64"
                    }
                ]
            }
            """.trimIndent()

            val drmCallback = LocalMediaDrmCallback(jwkResponse.toByteArray())

            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(clearKeyUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .build(drmCallback)
        } catch (e: Exception) {
            null
        }
    }

    private fun createClearKeyDrmManagerFromJwk(jwkJson: String): DefaultDrmSessionManager? {
        return try {
            val clearKeyUuid = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")
            val drmCallback = LocalMediaDrmCallback(jwkJson.toByteArray())
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(clearKeyUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .build(drmCallback)
        } catch (e: Exception) {
            null
        }
    }

    private fun createClearKeyServerDrmManager(licenseUrl: String, headers: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val clearKeyUuid = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(30000).setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
            val cb = HttpMediaDrmCallback(licenseUrl, factory)
            headers.forEach { (k, v) -> cb.setKeyRequestProperty(k, v) }
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(clearKeyUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false).build(cb)
        } catch (e: Exception) { null }
    }

    private fun createWidevineDrmManager(licenseUrl: String, requestHeaders: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val widevineUuid = C.WIDEVINE_UUID

            val licenseDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(requestHeaders["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(requestHeaders)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)

            val drmCallback = HttpMediaDrmCallback(
                licenseUrl,
                licenseDataSourceFactory
            )

            requestHeaders.forEach { (key, value) ->
                drmCallback.setKeyRequestProperty(key, value)
            }

            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(widevineUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .build(drmCallback)
        } catch (e: Exception) {
            null
        }
    }

    private fun createPlayReadyDrmManager(licenseUrl: String, requestHeaders: Map<String, String>): DefaultDrmSessionManager? {
        return try {
            val playReadyUuid = C.PLAYREADY_UUID

            val licenseDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(requestHeaders["User-Agent"] ?: "LiveTVPro/1.0")
                .setDefaultRequestProperties(requestHeaders)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)

            val drmCallback = HttpMediaDrmCallback(
                licenseUrl,
                licenseDataSourceFactory
            )

            requestHeaders.forEach { (key, value) ->
                drmCallback.setKeyRequestProperty(key, value)
            }

            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(playReadyUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .build(drmCallback)
        } catch (e: Exception) {
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return try {
            val cleanHex = hex.replace(" ", "").replace("-", "").lowercase()
            if (cleanHex.length % 2 != 0) return ByteArray(0)
            cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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
            binding.tvMessageBanner.text = message
            binding.tvMessageBanner.isSelected = true
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            binding.messageBannerContainer.visibility = if (isLandscape) View.GONE else View.VISIBLE
            val url = listenerManager.getMessageUrl()
            if (url.isNotBlank()) {
                binding.tvMessageBanner.setOnClickListener {
                    try {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (e: Exception) { }
                }
            } else {
                binding.tvMessageBanner.setOnClickListener(null)
            }
        }
    }

    private fun updateMessageBannerForOrientation(isLandscape: Boolean) {
        if (binding.tvMessageBanner.text.isNotBlank()) {
            binding.messageBannerContainer.visibility = if (isLandscape) View.GONE else View.VISIBLE
        }
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

        if (allEventLinks.size > 1) {
            binding.linksSection.visibility = View.VISIBLE
        }
        val hasRelated = relatedChannels.isNotEmpty() ||
            (contentType == ContentType.EVENT && ::relatedEventsAdapter.isInitialized)
        if (hasRelated) {
            binding.relatedChannelsSection.visibility = View.VISIBLE
        }
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
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        binding.playerContainer.layoutParams = params

        binding.relatedChannelsSection.visibility = View.GONE
        binding.linksSection.visibility = View.GONE
    }

    private fun setSubtitleTextSize() {
        val subtitleView = binding.playerView.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
    }

    private fun setSubtitleTextSizePiP() {
        val subtitleView = binding.playerView.subtitleView ?: return
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2)
    }

    private fun prepareUIForPip() {
        controlsState.hide()
    }

    @SuppressLint("NewApi")
    private fun enterPipMode() {
        binding.playerView.useController = false
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
                val hasError = binding.errorView.visibility == View.VISIBLE
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

        actions.add(RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_skip_backward),
            "Rewind", "Rewind 10s",
            PendingIntent.getBroadcast(context, CONTROL_TYPE_REWIND,
                Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_REWIND),
                PendingIntent.FLAG_IMMUTABLE)
        ))

        if (isPaused) {
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_play),
                context.getString(R.string.play), context.getString(R.string.play),
                PendingIntent.getBroadcast(context, CONTROL_TYPE_PLAY,
                    Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                        .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PLAY),
                    PendingIntent.FLAG_IMMUTABLE)
            ))
        } else {
            actions.add(RemoteAction(
                Icon.createWithResource(context, R.drawable.ic_pause),
                context.getString(R.string.pause), context.getString(R.string.pause),
                PendingIntent.getBroadcast(context, CONTROL_TYPE_PAUSE,
                    Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                        .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE)
            ))
        }

        actions.add(RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_skip_forward),
            "Forward", "Forward 10s",
            PendingIntent.getBroadcast(context, CONTROL_TYPE_FORWARD,
                Intent(ACTION_MEDIA_CONTROL).setPackage(context.packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD),
                PendingIntent.FLAG_IMMUTABLE)
        ))

        return actions
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
