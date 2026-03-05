package com.livetvpro.app.ui.adapters

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.GlideExtensions
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.databinding.ItemFavoriteBinding
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.FloatingPlayerHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class FavoriteAdapter(
    private val preferencesManager: PreferencesManager,
    private val onChannelClick: ((FavoriteChannel, () -> Unit) -> Boolean)? = null,
    private val onFavoriteToggle: (FavoriteChannel) -> Unit,
    private val getLiveChannel: ((String) -> Channel?)? = null
) : ListAdapter<FavoriteChannel, FavoriteAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(
        private val binding: ItemFavoriteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(favorite: FavoriteChannel) {
            binding.apply {
                tvName.text = favorite.name

                root.isFocusable = true
                root.isFocusableInTouchMode = false
                root.setOnFocusChangeListener { view, hasFocus ->
                    view.animate()
                        .scaleX(if (hasFocus) 1.05f else 1f)
                        .scaleY(if (hasFocus) 1.05f else 1f)
                        .setDuration(120)
                        .start()
                    view.elevation = if (hasFocus) 8f else 0f
                }
                root.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            root.performClick()
                            true
                        }
                        else -> false
                    }
                }

                GlideExtensions.loadImage(imgLogo, favorite.logoUrl, R.mipmap.ic_launcher_round, R.mipmap.ic_launcher_round)

                root.setOnClickListener {
                    val liveChannel = getLiveChannel?.invoke(favorite.id)

                    val channelToUse = if (liveChannel != null) {
                        liveChannel
                    } else {
                        val resolvedStreamUrl = favorite.streamUrl.ifEmpty {
                            favorite.links?.firstOrNull()?.url ?: ""
                        }
                        Channel(
                            id = favorite.id,
                            name = favorite.name,
                            logoUrl = favorite.logoUrl,
                            streamUrl = resolvedStreamUrl,
                            categoryId = favorite.categoryId,
                            categoryName = favorite.categoryName,
                            links = favorite.links
                        )
                    }

                    val links = channelToUse.links
                    val playerAction: () -> Unit = if (links.isNullOrEmpty()) {
                        if (channelToUse.streamUrl.isNotEmpty()) {
                            { launchPlayerWithUrl(channelToUse, channelToUse.streamUrl) }
                        } else {
                            {
                                android.widget.Toast.makeText(
                                    binding.root.context,
                                    "No stream available for ${favorite.name}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else if (links.size > 1) {
                        { showLinkSelectionDialog(channelToUse, links) }
                    } else {
                        { launchPlayer(channelToUse, 0) }
                    }

                    val redirected = onChannelClick?.invoke(favorite, playerAction) ?: false
                    if (!redirected) {
                        playerAction()
                    }
                }

                removeButton.setOnClickListener {
                    onFavoriteToggle(favorite)
                }
            }
        }

        private fun showLinkSelectionDialog(
            channel: Channel,
            links: List<com.livetvpro.app.data.models.ChannelLink>
        ) {
            val context = binding.root.context
            val linkLabels = links.map { it.quality }.toTypedArray()

            MaterialAlertDialogBuilder(context)
                .setTitle("Multiple Links Available")
                .setItems(linkLabels) { dialog, which ->
                    launchPlayer(channel, which)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        private fun launchPlayer(channel: Channel, linkIndex: Int) {
            val context = binding.root.context

            val selectedLink = channel.links?.getOrNull(linkIndex)
            val streamUrl = if (selectedLink != null) {
                buildStreamUrlFromLink(selectedLink)
            } else {
                channel.streamUrl
            }

            val channelWithUrl = Channel(
                id = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = streamUrl,
                categoryId = channel.categoryId,
                categoryName = channel.categoryName,
                links = channel.links
            )

            if (DeviceUtils.isTvDevice) {
                PlayerActivity.startWithChannel(context, channelWithUrl, linkIndex)
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
                    PlayerActivity.startWithChannel(context, channelWithUrl, linkIndex)
                    return
                }
                try {
                    FloatingPlayerHelper.launchFloatingPlayer(context, channelWithUrl, linkIndex)
                } catch (e: Exception) {
                    PlayerActivity.startWithChannel(context, channelWithUrl, linkIndex)
                }
            } else {
                PlayerActivity.startWithChannel(context, channelWithUrl, linkIndex)
            }
        }

        private fun buildStreamUrlFromLink(link: com.livetvpro.app.data.models.ChannelLink): String {
            val parts = mutableListOf<String>()
            parts.add(link.url)

            link.referer?.let { if (it.isNotEmpty()) parts.add("referer=$it") }
            link.cookie?.let { if (it.isNotEmpty()) parts.add("cookie=$it") }
            link.origin?.let { if (it.isNotEmpty()) parts.add("origin=$it") }
            link.userAgent?.let { if (it.isNotEmpty()) parts.add("User-Agent=$it") }
            link.drmScheme?.let { if (it.isNotEmpty()) parts.add("drmScheme=$it") }
            link.drmLicenseUrl?.let { if (it.isNotEmpty()) parts.add("drmLicense=$it") }

            return if (parts.size > 1) {
                parts.joinToString("|")
            } else {
                parts[0]
            }
        }

        private fun launchPlayerWithUrl(channel: Channel, url: String) {
            val context = binding.root.context

            if (DeviceUtils.isTvDevice) {
                PlayerActivity.startWithChannel(context, channel, -1)
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
                    PlayerActivity.startWithChannel(context, channel, -1)
                    return
                }
                try {
                    FloatingPlayerHelper.launchFloatingPlayer(context, channel, -1)
                } catch (e: Exception) {
                    PlayerActivity.startWithChannel(context, channel, -1)
                }
            } else {
                PlayerActivity.startWithChannel(context, channel, -1)
            }
        }
    }

    private class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteChannel>() {
        override fun areItemsTheSame(oldItem: FavoriteChannel, newItem: FavoriteChannel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FavoriteChannel, newItem: FavoriteChannel): Boolean {
            return oldItem == newItem
        }
    }
}
