package com.livetvpro.app.ui.player.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
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


    private data class PageEntry(val label: String, val buildList: () -> List<TrackUiModel>)
    private val pages = mutableListOf<PageEntry>()


    private inner class TrackPagerAdapter : RecyclerView.Adapter<TrackPagerAdapter.PageVH>() {

        private val pageAdapters = mutableMapOf<Int, TrackAdapter<*>>()

        fun notifyPageChanged(position: Int) {
            pageAdapters.remove(position)
            notifyItemChanged(position)
        }

        fun notifyAllChanged() {
            pageAdapters.clear()
            notifyDataSetChanged()
        }

        @Suppress("UNCHECKED_CAST")
        private fun adapterForPage(position: Int): TrackAdapter<*> {
            return pageAdapters.getOrPut(position) {
                val page = pages[position]
                when (position) {
                    pages.indexOfFirst { it.label == "Video" } -> buildVideoAdapter()
                    pages.indexOfFirst { it.label == "Audio" } -> buildAudioAdapter()
                    pages.indexOfFirst { it.label == "Text"  } -> buildTextAdapter()
                    else                                        -> buildSpeedAdapter()
                }
            }
        }

        override fun getItemCount() = pages.size
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(parent.context)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            return PageVH(rv)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.recyclerView.adapter = adapterForPage(position)
        }

        inner class PageVH(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)
    }

    private var pagerAdapter: TrackPagerAdapter? = null


    private fun buildVideoAdapter(): TrackAdapter<TrackUiModel.Video> {
        var adapter_self_ref: TrackAdapter<TrackUiModel.Video>? = null
        adapter_self_ref = TrackAdapter { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedVideoQualities.clear(); isVideoAuto = true;  isVideoNone = false }
                -2   -> { selectedVideoQualities.clear(); isVideoAuto = false; isVideoNone = true  }
                else -> {
                    isVideoAuto = false; isVideoNone = false
                    val key = selectedVideoQualities.find {
                        it.groupIndex == selected.groupIndex && it.trackIndex == selected.trackIndex
                    }
                    if (key != null) selectedVideoQualities.remove(key)
                    else selectedVideoQualities.add(selected)
                    if (selectedVideoQualities.isEmpty()) isVideoAuto = true
                }
            }
            adapter_self_ref?.submit(buildVideoList())
        }
        adapter_self_ref.submit(buildVideoList())
        return adapter_self_ref
    }

    private fun buildAudioAdapter(): TrackAdapter<TrackUiModel.Audio> {
        var ref: TrackAdapter<TrackUiModel.Audio>? = null
        ref = TrackAdapter { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedAudio = null; isAudioNone = false; isAudioAuto = true  }
                -2   -> { selectedAudio = null; isAudioNone = true;  isAudioAuto = false }
                else -> { selectedAudio = selected; isAudioNone = false; isAudioAuto = false }
            }
            ref?.updateSelection(selected)
        }
        ref.submit(buildAudioList())
        return ref
    }

    private fun buildTextAdapter(): TrackAdapter<TrackUiModel.Text> {
        var ref: TrackAdapter<TrackUiModel.Text>? = null
        ref = TrackAdapter { selected ->
            when (selected.groupIndex) {
                -1   -> { selectedText = null; isTextNone = false; isTextAuto = true  }
                -2   -> { selectedText = null; isTextNone = true;  isTextAuto = false }
                else -> { selectedText = selected; isTextNone = false; isTextAuto = false }
            }
            ref?.updateSelection(selected)
        }
        ref.submit(buildTextList())
        return ref
    }

    private fun buildSpeedAdapter(): TrackAdapter<TrackUiModel.Speed> {
        var ref: TrackAdapter<TrackUiModel.Speed>? = null
        ref = TrackAdapter { selected ->
            selectedSpeed = selected.speed
            ref?.updateSelection(selected)
        }
        ref.submit(listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            .map { TrackUiModel.Speed(it, isSelected = it == selectedSpeed) })
        return ref
    }


    private fun buildVideoList(): List<TrackUiModel.Video> {
        val list = mutableListOf<TrackUiModel.Video>()
        list.add(TrackUiModel.Video(-1, -1, 0, 0, 0, isSelected = isVideoAuto,  isRadio = true))
        list.add(TrackUiModel.Video(-2, -2, 0, 0, 0, isSelected = isVideoNone,  isRadio = true))
        val useRadio = videoTracks.size == 1
        list.addAll(videoTracks.map { t ->
            val checked = selectedVideoQualities.any {
                it.groupIndex == t.groupIndex && it.trackIndex == t.trackIndex
            }
            t.copy(isSelected = !isVideoAuto && !isVideoNone && checked, isRadio = useRadio)
        })
        return list
    }

    private fun buildAudioList(): List<TrackUiModel.Audio> {
        val list = mutableListOf<TrackUiModel.Audio>()
        list.add(TrackUiModel.Audio(-1, -1, "Auto", 0, 0, isSelected = isAudioAuto))
        list.add(TrackUiModel.Audio(-2, -2, "None", 0, 0, isSelected = isAudioNone))
        list.addAll(audioTracks.map { t -> t.copy(isSelected = !isAudioAuto && !isAudioNone && t.isSelected) })
        return list
    }

    private fun buildTextList(): List<TrackUiModel.Text> {
        val list = mutableListOf<TrackUiModel.Text>()
        list.add(TrackUiModel.Text(-1, -1, "Auto", isSelected = isTextAuto))
        list.add(TrackUiModel.Text(-2, -2, "None", isSelected = isTextNone))
        list.addAll(textTracks.map { t -> t.copy(isSelected = !isTextAuto && !isTextNone && t.isSelected) })
        return list
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

            btnClose?.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    viewPager.requestFocus(); true
                } else false
            }
            tabLayout.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    viewPager.requestFocus(); true
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    btnClose?.requestFocus(); true
                } else if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                    val count = tabLayout.tabCount
                    if (count == 0) return@setOnKeyListener false
                    val next = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                        (viewPager.currentItem + 1).coerceAtMost(count - 1)
                    else
                        (viewPager.currentItem - 1).coerceAtLeast(0)
                    viewPager.currentItem = next
                    true
                } else false
            }
            btnCancel.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    viewPager.requestFocus(); true
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnApply.requestFocus(); true
                } else false
            }
            btnApply.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    viewPager.requestFocus(); true
                } else if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnCancel.requestFocus(); true
                } else false
            }
        }

        loadTracks()

        val allEmpty = videoTracks.isEmpty() && audioTracks.isEmpty() && textTracks.isEmpty()
        if (allEmpty) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.groups.isNotEmpty()) {
                        player.removeListener(this)
                        tracksListener = null
                        viewPager.post { loadTracks(); rebuildPages() }
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
        val density = dm.density
        val isLandscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE



        val swDp = context.resources.configuration.smallestScreenWidthDp


        val shortPx = minOf(dm.widthPixels, dm.heightPixels)
        val longPx  = maxOf(dm.widthPixels, dm.heightPixels)

        val dialogWidth = when {
            DeviceUtils.isTvDevice -> (longPx * 0.55f).toInt()
            swDp >= 720 ->           (shortPx * 0.70f).toInt()
            swDp >= 600 ->           (shortPx * 0.80f).toInt()
            isLandscape ->           (shortPx * 0.85f).toInt()
            else        ->           (shortPx * 0.88f).toInt()
        }
        val dialogHeight = when {
            DeviceUtils.isTvDevice -> (longPx  * 0.70f).toInt()
            swDp >= 720 ->           (longPx  * 0.60f).toInt()
            swDp >= 600 ->           (longPx  * 0.65f).toInt()
            isLandscape ->           (shortPx * 0.92f).toInt()
            else        ->           (longPx  * 0.75f).toInt()
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


    private fun rebuildPages() {
        pages.clear()
        if (videoTracks.isNotEmpty()) pages.add(PageEntry("Video") { buildVideoList() })
        if (audioTracks.isNotEmpty()) pages.add(PageEntry("Audio") { buildAudioList() })
        if (textTracks.isNotEmpty())  pages.add(PageEntry("Text")  { buildTextList()  })
        pages.add(PageEntry("Speed") { emptyList() })

        val adapter = TrackPagerAdapter()
        pagerAdapter = adapter
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = pages.size

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pages[position].label
        }.attach()

        if (DeviceUtils.isTvDevice) {
            tabLayout.post { tabLayout.requestFocus() }
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
