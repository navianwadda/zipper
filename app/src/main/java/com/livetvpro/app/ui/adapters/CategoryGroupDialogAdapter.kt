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

class CategoryGroupDialogAdapter(
    private val onGroupClick: (String) -> Unit
) : ListAdapter<String, CategoryGroupDialogAdapter.ViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_group_dialog, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onGroupClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val groupName: TextView = itemView.findViewById(R.id.group_name)
        private val groupIcon: ImageView = itemView.findViewById(R.id.group_icon)

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false

            itemView.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.03f else 1f)
                    .scaleY(if (hasFocus) 1.03f else 1f)
                    .setDuration(100)
                    .start()
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnKeyListener false
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

        fun bind(group: String, onGroupClick: (String) -> Unit) {
            groupName.text = group
            groupName.typeface = itemView.context.resources.getFont(R.font.bergen_sans)
            groupIcon.visibility = View.VISIBLE
            groupIcon.setImageResource(R.drawable.ic_playlist)
            itemView.setOnClickListener { onGroupClick(group) }
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
