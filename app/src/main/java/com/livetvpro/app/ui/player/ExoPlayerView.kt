package com.livetvpro.app.ui.player

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

/**
 * ExoPlayerView — a pure-Kotlin wrapper around [androidx.media3.ui.PlayerView].
 *
 * No XML layout is needed. Construct this view programmatically and embed it
 * wherever you need video playback — either by adding it to a [ViewGroup] directly
 * in a traditional Activity, or by hosting it inside a Compose [AndroidView].
 *
 * ### Usage in Compose (PlayerScreen / PlayerActivityRoot)
 * ```kotlin
 * val context = LocalContext.current
 * val exoPlayerView = remember {
 *     ExoPlayerView(context).apply {
 *         resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
 *     }
 * }
 * // Attach the current ExoPlayer instance whenever it changes
 * LaunchedEffect(player) {
 *     exoPlayerView.setPlayer(player)
 * }
 * AndroidView(
 *     factory = { exoPlayerView },
 *     modifier = Modifier.fillMaxSize(),
 *     update = { view -> view.setPlayer(player) }
 * )
 * ```
 *
 * ### Usage in a traditional Activity / ViewGroup
 * ```kotlin
 * val exoPlayerView = ExoPlayerView(this)
 * playerContainer.addView(
 *     exoPlayerView,
 *     ConstraintLayout.LayoutParams(
 *         ConstraintLayout.LayoutParams.MATCH_PARENT,
 *         ConstraintLayout.LayoutParams.MATCH_PARENT,
 *     )
 * )
 * exoPlayerView.setPlayer(exoPlayer)
 * ```
 *
 * Both [PlayerActivity] and [FloatingPlayerActivity] can hold a reference to this
 * view, call [setPlayer] / [onResume] / [onPause], and forward resize-mode changes
 * through [resizeMode].
 */
@UnstableApi
class ExoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    // -------------------------------------------------------------------------
    // Inner PlayerView — the real Media3 surface host
    // -------------------------------------------------------------------------

    private val innerPlayerView: PlayerView = PlayerView(context).apply {
        // Disable the built-in controller; our Compose overlay handles all UI.
        useController          = false
        controllerAutoShow     = false
        controllerShowTimeoutMs = 0

        // Use a SurfaceView (hardware-accelerated; better for DRM streams).
        // Set to "texture_view" if you need transparency / View animations.
        setShutterBackgroundColor(Color.BLACK)

        // Default resize mode — callers can change via the property below.
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        // Prevent the PlayerView itself from consuming touch/focus so that
        // the Compose gesture layer above it stays in control.
        isFocusable          = false
        isFocusableInTouchMode = false
        isClickable          = false

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
    }

    // -------------------------------------------------------------------------
    // Init — add the inner view and make the container black
    // -------------------------------------------------------------------------

    init {
        setBackgroundColor(Color.BLACK)
        addView(innerPlayerView)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Attach or detach an [ExoPlayer] from this view.
     * Pass `null` to detach (e.g. before releasing the player).
     */
    fun setPlayer(player: ExoPlayer?) {
        innerPlayerView.player = player
    }

    /**
     * Returns the currently attached [ExoPlayer], or `null` if none.
     */
    val player: ExoPlayer?
        get() = innerPlayerView.player as? ExoPlayer

    /**
     * Controls how the video is scaled inside the view.
     * Use [AspectRatioFrameLayout] constants:
     *  - [AspectRatioFrameLayout.RESIZE_MODE_FIT]   — letterbox/pillarbox (default)
     *  - [AspectRatioFrameLayout.RESIZE_MODE_FILL]  — crop to fill
     *  - [AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH]
     *  - [AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT]
     *  - [AspectRatioFrameLayout.RESIZE_MODE_ZOOM]
     */
    var resizeMode: Int
        get() = innerPlayerView.resizeMode
        set(value) { innerPlayerView.resizeMode = value }

    /**
     * Cycles through all resize modes in order and returns the next one applied.
     * Handy for wiring to the aspect-ratio toggle button in your controls.
     */
    fun cycleResizeMode(): Int {
        val modes = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        )
        val current = innerPlayerView.resizeMode
        val nextIdx = (modes.indexOf(current) + 1).coerceIn(0, modes.lastIndex)
        innerPlayerView.resizeMode = modes[nextIdx]
        return modes[nextIdx]
    }

    /**
     * Access the subtitle/caption view if you need to customise caption styling.
     */
    val subtitleView: SubtitleView?
        get() = innerPlayerView.subtitleView

    /**
     * Exposes the raw [PlayerView] for callers that need low-level access
     * (e.g. PiP rect computation, `onResume`/`onPause` lifecycle forwarding).
     */
    val rawPlayerView: PlayerView
        get() = innerPlayerView

    // -------------------------------------------------------------------------
    // Lifecycle forwarding — call these from your Activity
    // -------------------------------------------------------------------------

    /**
     * Must be called from [android.app.Activity.onResume].
     * Re-attaches the surface and resumes the playback session.
     */
    fun onResume() {
        innerPlayerView.onResume()
    }

    /**
     * Must be called from [android.app.Activity.onPause].
     * Detaches the surface so the OS can reclaim it while in the background.
     */
    fun onPause() {
        innerPlayerView.onPause()
    }

    /**
     * Hides the built-in controller (no-op when [useController] is false,
     * but kept for symmetry / future use).
     */
    fun hideController() {
        innerPlayerView.hideController()
    }

    /**
     * Returns `true` when the built-in controller is fully visible.
     */
    val isControllerFullyVisible: Boolean
        get() = innerPlayerView.isControllerFullyVisible
}
