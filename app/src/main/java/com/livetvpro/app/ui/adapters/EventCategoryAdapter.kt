package com.livetvpro.app.ui.adapters

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.livetvpro.app.R
import com.livetvpro.app.utils.GlideExtensions
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.databinding.ItemEventCategoryBinding
class EventCategoryAdapter(
    private val onCategoryClick: (EventCategory) -> Unit
) : ListAdapter<EventCategory, EventCategoryAdapter.ViewHolder>(CategoryDiffCallback()) {
    private var selectedPosition = 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }
    inner class ViewHolder(
        private val binding: ItemEventCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.08f else 1f)
                    .scaleY(if (hasFocus) 1.08f else 1f)
                    .setDuration(120)
                    .start()
                view.elevation = if (hasFocus) 8f else 0f
            }
            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        binding.root.performClick()
                        true
                    }
                    else -> false
                }
            }
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val oldPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(position)
                    onCategoryClick(getItem(position))
                }
            }
        }
        fun bind(category: EventCategory, isSelected: Boolean) {
            binding.categoryName.text = category.name
            binding.categoryName.isSelected = true
            binding.root.isSelected = isSelected
            binding.categoryCard.strokeWidth = if (isSelected) 5 else 4
            binding.categoryCard.strokeColor = if (isSelected) {
                android.graphics.Color.parseColor("#EF4444")
            } else {
                android.graphics.Color.parseColor("#5A5A5A")
            }
            GlideExtensions.loadImage(binding.categoryLogo, category.logoUrl, R.mipmap.ic_launcher_round, R.mipmap.ic_launcher_round, isCircular = true)
        }
    }
    private class CategoryDiffCallback : DiffUtil.ItemCallback<EventCategory>() {
        override fun areItemsTheSame(oldItem: EventCategory, newItem: EventCategory): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: EventCategory, newItem: EventCategory): Boolean {
            return oldItem == newItem
        }
    }
}
