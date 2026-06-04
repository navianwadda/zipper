package com.livetvpro.app.ui.adapters

import android.graphics.Color
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.databinding.ItemCategoryBinding
import com.livetvpro.app.utils.GlideExtensions

class CategoryAdapter(
    private val onCategoryClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    private var primaryColor: Int = 0
    private var surfaceContainerColor: Int = 0

    fun setColors(primary: Int, surfaceContainer: Int) {
        val changed = primaryColor != primary || surfaceContainerColor != surfaceContainer
        primaryColor = primary
        surfaceContainerColor = surfaceContainer
        if (changed) notifyItemRangeChanged(0, itemCount, PAYLOAD_COLOR)
    }

    companion object {
        private const val PAYLOAD_COLOR = "color"

        fun outerContainerColorForPosition(primaryColor: Int, position: Int): Int {
            if (primaryColor == 0) return Color.parseColor("#F0F0F0")
            val hsv = FloatArray(3)
            Color.colorToHSV(primaryColor, hsv)
            hsv[0] = (hsv[0] + position * 37f) % 360f
            hsv[1] = hsv[1].coerceIn(0.35f, 0.55f)
            hsv[2] = hsv[2].coerceIn(0.80f, 0.95f)
            val solid = Color.HSVToColor(hsv)
            return Color.argb(220, Color.red(solid), Color.green(solid), Color.blue(solid))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_COLOR)) {
            holder.applyColor(primaryColor, surfaceContainerColor, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onCategoryClick(getItem(pos))
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        onCategoryClick(getItem(pos))
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

        fun applyColor(
            primary: Int,
            surfaceContainer: Int,
            position: Int
        ) {
            binding.logoCard.setCardBackgroundColor(
                ContextCompat.getColor(binding.logoCard.context, R.color.card_logo_bg)
            )

            binding.logoOuterContainer.getChildAt(1)?.backgroundTintList = null

            if (surfaceContainer != 0) {
                binding.root.setCardBackgroundColor(surfaceContainer)
            }

            val outerBg = outerContainerColorForPosition(primary, position)
            binding.logoOuterContainer.setBackgroundColor(outerBg)
        }

        fun bind(category: Category, position: Int) {
            binding.categoryName.text = category.name
            binding.categoryIcon.setImageDrawable(null)

            if (!category.iconUrl.isNullOrEmpty()) {
                GlideExtensions.loadImage(
                    binding.categoryIcon,
                    category.iconUrl,
                    null,
                    null
                )
            } else {
                binding.categoryIcon.setImageResource(R.mipmap.ic_launcher_round)
            }

            applyColor(primaryColor, surfaceContainerColor, position)
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem == newItem
        }
    }
}
