package com.livetvpro.app.ui.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.databinding.ItemLiveEventBinding
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.livetvpro.app.utils.GlideExtensions
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LiveEventAdapter(
    private val context: Context,
    private var events: List<LiveEvent>,
    private val preferencesManager: PreferencesManager,
    private val onEventClick: ((LiveEvent, Int) -> Unit)? = null,
    private val onEventInteraction: ((LiveEvent, () -> Unit) -> Boolean)? = null
) : RecyclerView.Adapter<LiveEventAdapter.EventViewHolder>() {

    // Must use Locale.US — Locale.getDefault() can break date parsing on non-English locales
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US).apply {
        val symbols = dateFormatSymbols
        symbols.amPmStrings = arrayOf("AM", "PM")
        dateFormatSymbols = symbols
    }
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)

    companion object {
        const val PAYLOAD_TIMER = "timer"
    }

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            for (i in events.indices) notifyItemChanged(i, PAYLOAD_TIMER)
            tickHandler.postDelayed(this, 1000)
        }
    }

    inner class EventViewHolder(val binding: ItemLiveEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        tickHandler.post(tickRunnable)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        tickHandler.removeCallbacks(tickRunnable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemLiveEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_TIMER)) {

            bindTimer(holder.binding, events[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val binding = holder.binding

        binding.leagueName.text = event.league ?: "Unknown League"

        binding.categoryTag.text = event.category.ifEmpty {
            event.eventCategoryName.ifEmpty { "Sports" }
        }

        if (event.wrapper.isNotEmpty()) {
            binding.wrapperBadge.text = event.wrapper
            binding.wrapperBadge.visibility = View.VISIBLE
        } else {
            binding.wrapperBadge.visibility = View.GONE
        }

        GlideExtensions.loadImage(
            binding.leagueLogo,
            event.leagueLogo,
            R.mipmap.ic_launcher_round,
            R.mipmap.ic_launcher_round,
            isCircular = false
        )

        binding.team1Name.text = event.team1Name
        binding.team2Name.text = event.team2Name

        GlideExtensions.loadImage(
            binding.team1Logo,
            event.team1Logo,
            R.mipmap.ic_launcher_round,
            R.mipmap.ic_launcher_round,
            isCircular = true
        )

        GlideExtensions.loadImage(
            binding.team2Logo,
            event.team2Logo,
            R.mipmap.ic_launcher_round,
            R.mipmap.ic_launcher_round,
            isCircular = true
        )

        bindTimer(binding, event)

        holder.itemView.setOnClickListener {
            launchPlayer(event)
        }

        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) 1.03f else 1f)
                .scaleY(if (hasFocus) 1.03f else 1f)
                .setDuration(120)
                .start()
            view.elevation = if (hasFocus) 8f else 0f
        }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    launchPlayer(events[holder.bindingAdapterPosition])
                    true
                }
                else -> false
            }
        }
    }

    private fun bindTimer(binding: ItemLiveEventBinding, event: LiveEvent) {
        try {
            val startDate = apiDateFormat.parse(event.startTime)
            val startTimeMillis = startDate?.time ?: 0L
            val currentTime = System.currentTimeMillis()

            val endTimeMillis = if (!event.endTime.isNullOrEmpty()) {
                try {
                    apiDateFormat.parse(event.endTime)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            } else {
                Long.MAX_VALUE
            }

            when {
                (currentTime >= startTimeMillis && currentTime <= endTimeMillis) || event.isLive -> {
                    binding.liveAnimation.visibility = View.VISIBLE
                    if (!binding.liveAnimation.isAnimating) {
                        binding.liveAnimation.playAnimation()
                    }

                    binding.matchTime.visibility = View.GONE
                    binding.matchDate.visibility = View.GONE

                    val elapsedMillis = currentTime - startTimeMillis
                    val hours = (elapsedMillis / 1000 / 3600).toInt()
                    val minutes = ((elapsedMillis / 1000 / 60) % 60).toInt()
                    val seconds = ((elapsedMillis / 1000) % 60).toInt()

                    binding.statusText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    binding.statusText.setTextColor(Color.parseColor("#EF4444"))
                    binding.statusText.visibility = View.VISIBLE
                }

                currentTime < startTimeMillis -> {
                    binding.liveAnimation.visibility = View.GONE
                    binding.liveAnimation.pauseAnimation()

                    if (startDate != null) {
                        binding.matchTime.text = timeFormat.format(startDate)
                        binding.matchTime.setTextColor(Color.parseColor("#10B981"))
                        binding.matchTime.visibility = View.VISIBLE

                        binding.matchDate.text = dateFormat.format(startDate)
                        binding.matchDate.visibility = View.VISIBLE

                        val diff = startTimeMillis - currentTime
                        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                        val hours = ((diff / (1000 * 60 * 60)) % 24).toInt()
                        val minutes = ((diff / (1000 * 60)) % 60).toInt()
                        val seconds = ((diff / 1000) % 60).toInt()

                        binding.statusText.text = when {
                            days > 0 -> String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
                            hours > 0 -> String.format("%02dh %02dm %02ds", hours, minutes, seconds)
                            minutes > 0 -> String.format("%02dm %02ds", minutes, seconds)
                            else -> String.format("%02ds", seconds)
                        }
                        binding.statusText.setTextColor(Color.parseColor("#10B981"))
                        binding.statusText.visibility = View.VISIBLE
                    }
                }

                else -> {
                    binding.liveAnimation.visibility = View.GONE
                    binding.liveAnimation.pauseAnimation()

                    val endDate = if (endTimeMillis != Long.MAX_VALUE) {
                        apiDateFormat.parse(event.endTime)
                    } else {
                        startDate
                    }

                    if (endDate != null) {
                        binding.matchTime.text = timeFormat.format(endDate)
                        binding.matchTime.setTextColor(Color.GRAY)
                        binding.matchTime.visibility = View.VISIBLE

                        binding.matchDate.text = dateFormat.format(endDate)
                        binding.matchDate.visibility = View.VISIBLE
                    }

                    binding.statusText.text = "Ended"
                    binding.statusText.setTextColor(Color.GRAY)
                    binding.statusText.visibility = View.VISIBLE
                }
            }

        } catch (e: Exception) {
            binding.liveAnimation.visibility = View.GONE
            binding.liveAnimation.pauseAnimation()
            binding.matchTime.visibility = View.GONE
            binding.matchDate.visibility = View.GONE
            binding.statusText.text = "Unknown"
            binding.statusText.setTextColor(Color.LTGRAY)
            binding.statusText.visibility = View.VISIBLE
        }

    }

    private fun launchPlayer(event: LiveEvent) {
        if (event.links.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "No streams available for this event",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val playerAction: () -> Unit = if (onEventClick != null) {
            if (event.links.size == 1) {
                { onEventClick.invoke(event, 0) }
            } else {
                { showLinkSelectionDialogForSwitching(event) }
            }
        } else if (event.links.size > 1) {
            { showLinkSelectionDialog(event) }
        } else {
            { proceedWithPlayer(event, 0) }
        }

        val redirected = onEventInteraction?.invoke(event, playerAction) ?: false
        if (!redirected) {
            playerAction()
        }
    }

    private fun showLinkSelectionDialogForSwitching(event: LiveEvent) {
        val linkLabels = event.links.map { it.quality }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { dialog, which ->
                onEventClick?.invoke(event, which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLinkSelectionDialog(event: LiveEvent) {
        val linkLabels = event.links.map { it.quality }.toTypedArray()

        val hasExistingPlayer = FloatingPlayerHelper.hasFloatingPlayerForEvent(event.id)

        MaterialAlertDialogBuilder(context)
            .setTitle("Multiple Links Available")
            .setItems(linkLabels) { dialog, which ->
                proceedWithPlayer(event, which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun proceedWithPlayer(event: LiveEvent, linkIndex: Int) {
        if (DeviceUtils.isTvDevice) {
            PlayerActivity.startWithEvent(context, event, linkIndex)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

        if (floatingEnabled) {
            if (!hasPermission) {
                android.widget.Toast.makeText(
                    context,
                    "Overlay permission required for floating player. Opening normally instead.",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                PlayerActivity.startWithEvent(context, event, linkIndex)
                return
            }

            try {

                val channel = Channel(
                    id = event.id,
                    name = "${event.team1Name} vs ${event.team2Name}",
                    logoUrl = event.leagueLogo.ifEmpty { event.team1Logo },
                    categoryName = event.category,
                    links = event.links.map { liveEventLink ->
                        com.livetvpro.app.data.models.ChannelLink(
                            quality = liveEventLink.quality,
                            url = liveEventLink.url,
                            cookie = liveEventLink.cookie,
                            referer = liveEventLink.referer,
                            origin = liveEventLink.origin,
                            userAgent = liveEventLink.userAgent,
                            drmScheme = liveEventLink.drmScheme,
                            drmLicenseUrl = liveEventLink.drmLicenseUrl
                        )
                    }
                )

                FloatingPlayerHelper.launchFloatingPlayerWithEvent(context, channel, event, linkIndex)

            } catch (e: Exception) {
                PlayerActivity.startWithEvent(context, event, linkIndex)
            }
        } else {
            PlayerActivity.startWithEvent(context, event, linkIndex)
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateData(newEvents: List<LiveEvent>) {

        if (newEvents == events) return

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = events.size
            override fun getNewListSize() = newEvents.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                events[oldPos].id == newEvents[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = events[oldPos]
                val new = newEvents[newPos]

                return old.id == new.id &&
                    old.leagueLogo == new.leagueLogo &&
                    old.team1Logo == new.team1Logo &&
                    old.team2Logo == new.team2Logo &&
                    old.team1Name == new.team1Name &&
                    old.team2Name == new.team2Name &&
                    old.league == new.league &&
                    old.isLive == new.isLive
            }

            override fun getChangePayload(oldPos: Int, newPos: Int): Any? = PAYLOAD_TIMER
        })
        events = newEvents
        diff.dispatchUpdatesTo(this)
    }

}
