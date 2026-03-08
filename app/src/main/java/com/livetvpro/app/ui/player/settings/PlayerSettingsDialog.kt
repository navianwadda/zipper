package com.livetvpro.app.ui.player.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatDialog
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.livetvpro.app.R
import com.livetvpro.app.utils.DeviceUtils
import timber.log.Timber

class PlayerSettingsDialog(
    context: Context,
    private val player: ExoPlayer
) : AppCompatDialog(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog) {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnApply: MaterialButton

    private var selectedAudio: TrackUiModel.Audio? = null
    private var selectedText: TrackUiModel.Text? = null
    private var selectedSpeed: Float = 1.0f
    private var selectedVideoQualities = mutableSetOf<TrackUiModel.Video>()

    private var isVideoNone = false
    private var isAudioNone = false
    private var isTextNone  = false
    private var isVideoAuto = true
    private var isAudioAuto = true
    private var isTextAuto  = true

    private var videoTracks = listOf<TrackUiModel.Video>()
    private var audioTracks = listOf<TrackUiModel.Audio>()
    private var textTracks  = listOf<TrackUiModel.Text>()

    private var tracksListener: Player.Listener? = null

    private var videoAdapter: TrackAdapter<TrackUiModel.Video>? = null
    private var audioAdapter: TrackAdapter<TrackUiModel.Audio>? = null
    private var textAdapter:  TrackAdapter<TrackUiModel.Text>?  = null
    private var speedAdapter: TrackAdapter<TrackUiModel.Speed>? = null

    private data class PageEntry(val label: String)
    private val pages = mutableListOf<PageEntry>()
    private val pageRecyclerViews = mutableMapOf<Int, RecyclerView>()

    private inner class TrackPagerAdapter : RecyclerView.Adapter<TrackPagerAdapter.PageVH>() {

        override fun getItemCount() = pages.size
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val rv = object : RecyclerView(parent.context) {
                override fun dispatchKeyEvent(event: android.view.KeyEvent?): Boolean {
                    if (event != null && DeviceUtils.isTvDevice && event.action == android.view.KeyEvent.ACTION_UP) {
                        val lm = layoutManager as? LinearLayoutManager
                        when (event.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (lm?.findFirstCompletelyVisibleItemPosition() == 0) {
                                    tabLayout.requestFocus()
                                    return true
                                }
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (lm?.findLastCompletelyVisibleItemPosition() == (adapter?.itemCount ?: 0) - 1) {
                                    btnCancel.requestFocus()
                                    return true
                                }
                            }
                        }
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(parent.context)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                if (DeviceUtils.isTvDevice) {
                    isFocusable = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
            }
            return PageVH(rv)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.recyclerView.adapter = when (pages[position].label) {
                "Video" -> videoAdapter
                "Audio" -> audioAdapter
                "Text"  -> textAdapter
                else    -> speedAdapter
            }
            pageRecyclerViews[position] = holder.recyclerView
        }

        inner class PageVH(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)
    }

    private var pagerAdapter: TrackPagerAdapter? = null

    private fun buildVideoAdapter(): TrackAdapter<TrackUiModel.Video> {
        val adapter = TrackAdapter<TrackUiModel.Video> { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedVideoQualities.clear(); isVideoAuto = true;  isVideoNone = false }
                -2   -> { selectedVideoQualities.clear(); isVideoAuto = false; isVideoNone = true  }
                else -> {
                    isVideoAuto = false; isVideoNone = false
                    val existing = selectedVideoQualities.find {
                        it.groupIndex == selected.groupIndex && it.trackIndex == selected.trackIndex
                    }
                    if (existing != null) selectedVideoQualities.remove(existing)
                    else selectedVideoQualities.add(selected)
                    if (selectedVideoQualities.isEmpty()) isVideoAuto = true
                }
            }
            videoAdapter?.submit(buildVideoList())
        }
        adapter.submit(buildVideoList())
        return adapter
    }

    private fun buildAudioAdapter(): TrackAdapter<TrackUiModel.Audio> {
        val adapter = TrackAdapter<TrackUiModel.Audio> { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedAudio = null; isAudioNone = false; isAudioAuto = true  }
                -2   -> { selectedAudio = null; isAudioNone = true;  isAudioAuto = false }
                else -> { selectedAudio = selected; isAudioNone = false; isAudioAuto = false }
            }
            audioAdapter?.submit(buildAudioList())
        }
        adapter.submit(buildAudioList())
        return adapter
    }

    private fun buildTextAdapter(): TrackAdapter<TrackUiModel.Text> {
        val adapter = TrackAdapter<TrackUiModel.Text> { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedText = null; isTextNone = false; isTextAuto = true  }
                -2   -> { selectedText = null; isTextNone = true;  isTextAuto = false }
                else -> { selectedText = selected; isTextNone = false; isTextAuto = false }
            }
            textAdapter?.submit(buildTextList())
        }
        adapter.submit(buildTextList())
        return adapter
    }

    private fun buildSpeedAdapter(): TrackAdapter<TrackUiModel.Speed> {
        val adapter = TrackAdapter<TrackUiModel.Speed> { selected ->
            selectedSpeed = selected.speed
            speedAdapter?.submit(buildSpeedList())
        }
        adapter.submit(buildSpeedList())
        return adapter
    }

    private fun buildVideoList(): List<TrackUiModel.Video> {
        val useRadio = videoTracks.size == 1
        return buildList {
            add(TrackUiModel.Video(-1, -1, 0, 0, 0, isSelected = isVideoAuto, isRadio = true))
            add(TrackUiModel.Video(-2, -2, 0, 0, 0, isSelected = isVideoNone, isRadio = true))
            addAll(videoTracks.map { t ->
                val checked = selectedVideoQualities.any {
                    it.groupIndex == t.groupIndex && it.trackIndex == t.trackIndex
                }
                t.copy(isSelected = !isVideoAuto && !isVideoNone && checked, isRadio = useRadio)
            })
        }
    }

    private fun buildAudioList(): List<TrackUiModel.Audio> {
        return buildList {
            add(TrackUiModel.Audio(-1, -1, "Auto", 0, 0, isSelected = isAudioAuto))
            add(TrackUiModel.Audio(-2, -2, "None", 0, 0, isSelected = isAudioNone))
            addAll(audioTracks.map { t ->
                t.copy(isSelected = !isAudioAuto && !isAudioNone &&
                    selectedAudio?.groupIndex == t.groupIndex &&
                    selectedAudio?.trackIndex == t.trackIndex)
            })
        }
    }

    private fun buildTextList(): List<TrackUiModel.Text> {
        return buildList {
            add(TrackUiModel.Text(-1, -1, "Auto", isSelected = isTextAuto))
            add(TrackUiModel.Text(-2, -2, "None", isSelected = isTextNone))
            addAll(textTracks.map { t ->
                t.copy(isSelected = !isTextAuto && !isTextNone &&
                    selectedText?.groupIndex == t.groupIndex &&
                    selectedText?.trackIndex == t.trackIndex)
            })
        }
    }

    private fun buildSpeedList(): List<TrackUiModel.Speed> {
        return listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            .map { TrackUiModel.Speed(it, isSelected = it == selectedSpeed) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_player_settings)

        viewPager = findViewById<ViewPager2>(R.id.viewPager)!!
        tabLayout = findViewById<TabLayout>(R.id.tabLayout)!!
        btnCancel = findViewById<MaterialButton>(R.id.btnCancel)!!
        btnApply  = findViewById<MaterialButton>(R.id.btnApply)!!

        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }
        btnApply.setOnClickListener  { applySelections(); dismiss() }

        if (DeviceUtils.isTvDevice) {
            btnClose?.isFocusable = true
            btnCancel.isFocusable = true
            btnApply.isFocusable  = true

            val focusCurrentPage: () -> Boolean = {
                viewPager.post {
                    val rv = pageRecyclerViews[viewPager.currentItem]
                    if (rv != null) {
                        val lm = rv.layoutManager as? LinearLayoutManager
                        val firstVisible = lm?.findFirstVisibleItemPosition() ?: 0
                        rv.findViewHolderForAdapterPosition(firstVisible)?.itemView?.requestFocus()
                            ?: rv.requestFocus()
                    } else {
                        viewPager.requestFocus()
                    }
                }
                true
            }

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val pos = tab?.position ?: return
                    if (viewPager.currentItem != pos) viewPager.currentItem = pos
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            btnClose?.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    tabLayout.getTabAt(viewPager.currentItem)?.view?.requestFocus()
                        ?: tabLayout.requestFocus()
                    true
                } else false
            }

            tabLayout.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusCurrentPage()
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    btnClose?.requestFocus(); true
                } else false
            }
            btnCancel.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusCurrentPage()
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnApply.requestFocus(); true
                } else false
            }
            btnApply.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusCurrentPage()
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnCancel.requestFocus(); true
                } else false
            }
        }

        loadTracks()
        buildAdapters()

        val allEmpty = videoTracks.isEmpty() && audioTracks.isEmpty() && textTracks.isEmpty()
        if (allEmpty) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.groups.isNotEmpty()) {
                        player.removeListener(this)
                        tracksListener = null
                        viewPager.post {
                            loadTracks()
                            buildAdapters()
                            rebuildPages()
                        }
                    }
                }
            }
            tracksListener = listener
            player.addListener(listener)
        }

        rebuildPages()
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        val isLandscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val swDp = context.resources.configuration.smallestScreenWidthDp
        val maxH = (dm.heightPixels * 0.93f).toInt()

        val dialogWidth = when {
            DeviceUtils.isTvDevice -> (dm.widthPixels * 0.55f).toInt()
            swDp >= 720 ->           (dm.widthPixels * 0.55f).toInt()
            swDp >= 600 ->           (dm.widthPixels * 0.65f).toInt()
            isLandscape ->           (dm.widthPixels * 0.60f).toInt()
            else        ->           (dm.widthPixels * 0.82f).toInt()
        }
        val dialogHeight = when {
            DeviceUtils.isTvDevice -> minOf((dm.heightPixels * 0.80f).toInt(), maxH)
            swDp >= 720 ->           minOf((dm.heightPixels * 0.75f).toInt(), maxH)
            swDp >= 600 ->           minOf((dm.heightPixels * 0.80f).toInt(), maxH)
            isLandscape ->           minOf((dm.heightPixels * 0.90f).toInt(), maxH)
            else        ->           minOf((dm.heightPixels * 0.65f).toInt(), maxH)
        }
        window?.setLayout(dialogWidth, dialogHeight)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
    }

    override fun onStop() {
        super.onStop()
        tracksListener?.let { player.removeListener(it) }
        tracksListener = null
    }

    private fun buildAdapters() {
        videoAdapter = if (videoTracks.isNotEmpty()) buildVideoAdapter() else null
        audioAdapter = if (audioTracks.isNotEmpty()) buildAudioAdapter() else null
        textAdapter  = if (textTracks.isNotEmpty())  buildTextAdapter()  else null
        speedAdapter = buildSpeedAdapter()
    }

    private fun rebuildPages() {
        pages.clear()
        if (videoTracks.isNotEmpty()) pages.add(PageEntry("Video"))
        if (audioTracks.isNotEmpty()) pages.add(PageEntry("Audio"))
        if (textTracks.isNotEmpty())  pages.add(PageEntry("Text"))
        pages.add(PageEntry("Speed"))

        pageRecyclerViews.clear()
        val adapter = TrackPagerAdapter()
        pagerAdapter = adapter
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = pages.size

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pages[position].label
        }.attach()

        if (DeviceUtils.isTvDevice) {
            tabLayout.post { tabLayout.getTabAt(0)?.view?.requestFocus() ?: tabLayout.requestFocus() }
        }
    }

    private fun loadTracks() {
        try {
            val params = player.trackSelectionParameters

            val disabledVideo = params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
            val hasVideoOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_VIDEO && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isVideoNone = disabledVideo
            isVideoAuto = !disabledVideo && !hasVideoOver

            val disabledAudio = params.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
            val hasAudioOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_AUDIO && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isAudioNone = disabledAudio
            isAudioAuto = !disabledAudio && !hasAudioOver

            val disabledText = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val hasTextOver  = player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_TEXT && params.overrides.containsKey(it.mediaTrackGroup)
            }
            isTextNone = disabledText
            isTextAuto = !disabledText && !hasTextOver

            videoTracks = PlayerTrackMapper.videoTracks(player)
            audioTracks = PlayerTrackMapper.audioTracks(player)
            textTracks  = PlayerTrackMapper.textTracks(player)

            selectedVideoQualities.clear()
            if (!isVideoAuto && !isVideoNone)
                selectedVideoQualities.addAll(videoTracks.filter { it.isSelected })

            selectedAudio = if (!isAudioAuto && !isAudioNone) audioTracks.firstOrNull { it.isSelected } else null
            selectedText  = if (!isTextAuto  && !isTextNone)  textTracks.firstOrNull  { it.isSelected } else null
            selectedSpeed = player.playbackParameters.speed

            Timber.d("Tracks — video:${videoTracks.size} audio:${audioTracks.size} text:${textTracks.size}")
        } catch (e: Exception) {
            Timber.e(e, "Error loading tracks")
        }
    }

    private fun applySelections() {
        try {
            TrackSelectionApplier.applyMultipleVideo(
                player       = player,
                videoTracks  = if (isVideoAuto || isVideoNone) emptyList() else selectedVideoQualities.toList(),
                audio        = selectedAudio,
                text         = selectedText,
                disableVideo = isVideoNone,
                disableAudio = isAudioNone,
                disableText  = isTextNone
            )
            player.setPlaybackSpeed(selectedSpeed)
        } catch (e: Exception) {
            Timber.e(e, "Error applying selections")
        }
    }
}
