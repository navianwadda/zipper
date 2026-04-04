package com.livetvpro.app.ui.adapters

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.utils.GlideExtensions
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onFavoriteToggle: (Channel) -> Unit,
    private val isFavorite: (String) -> Boolean
) : PagingDataAdapter<Channel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) getItem(pos)?.let { onChannelClick(it) }
            }

            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) getItem(pos)?.let { showFavoriteDialog(it) }
                true
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        getItem(pos)?.let { onChannelClick(it) }
                        true
                    }
                    KeyEvent.KEYCODE_MENU -> {
                        getItem(pos)?.let { showFavoriteDialog(it) }
                        true
                    }
                    else -> false
                }
            }

            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false

            binding.root.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.05f else 1f)
                    .scaleY(if (hasFocus) 1.05f else 1f)
                    .setDuration(120)
                    .start()
                view.elevation = if (hasFocus) 8f else 0f
            }
        }

        private fun showFavoriteDialog(channel: Channel) {
            val context     = binding.root.context
            val isFav       = isFavorite(channel.id)
            val title       = if (isFav) "Remove from Favorites?" else "Add to Favorites?"
            val message     = if (isFav) "Remove \"${channel.name}\" from favorites?" else "Add \"${channel.name}\" to favorites?"
            val posBtnLabel = if (isFav) "Remove" else "Add"

            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(posBtnLabel) { dialog, _ ->
                    onFavoriteToggle(channel)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun bind(channel: Channel) {
            binding.channelName.text = channel.name
            binding.channelName.isSelected = true

            binding.favoriteIndicator.visibility =
                if (isFavorite(channel.id)) android.view.View.VISIBLE else android.view.View.GONE

            GlideExtensions.loadImage(
                binding.channelLogo,
                channel.logoUrl,
                R.mipmap.ic_launcher_round,
                R.mipmap.ic_launcher_round
            )
        }
    }

    fun refreshItem(channelId: String) {
        val position = snapshot().indexOfFirst { it?.id == channelId }
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
    }

    private class ChannelDiffCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(oldItem: Channel, newItem: Channel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Channel, newItem: Channel) = oldItem == newItem
    }
}
