package com.livetvpro.app.ui.adapters

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Playlist

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onEditClick: (Playlist) -> Unit,
    private val onDeleteClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.playlist_title)
        private val urlTextView: TextView = itemView.findViewById(R.id.playlist_url)
        private val editButton: ImageView = itemView.findViewById(R.id.edit_button)
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false

            itemView.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.03f else 1f)
                    .scaleY(if (hasFocus) 1.03f else 1f)
                    .setDuration(120)
                    .start()
                view.elevation = if (hasFocus) 8f else 0f
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        itemView.performClick()
                        true
                    }
                    else -> false
                }
            }
        }

        fun bind(playlist: Playlist) {
            titleTextView.text = playlist.title
            urlTextView.text = playlist.getSource()

            itemView.setOnClickListener { onPlaylistClick(playlist) }
            editButton.setOnClickListener { onEditClick(playlist) }
            deleteButton.setOnClickListener { onDeleteClick(playlist) }
        }
    }

    private class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem == newItem
    }
}
