package com.livetvpro.app.ui.appearance

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.livetvpro.app.R
import com.livetvpro.app.ui.theme.AppColorTheme

class ThemePreviewAdapter(
    private val isDark: Boolean,
    private var selectedTheme: AppColorTheme,
    private val onThemeSelected: (AppColorTheme) -> Unit,
) : RecyclerView.Adapter<ThemePreviewAdapter.VH>() {

    private val themes = AppColorTheme.entries

    fun updateSelection(theme: AppColorTheme) {
        val old = themes.indexOf(selectedTheme)
        selectedTheme = theme
        val new = themes.indexOf(theme)
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    override fun getItemCount() = themes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_theme_preview, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(themes[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val card:          MaterialCardView = view.findViewById(R.id.card_preview)
        private val root:          View             = view.findViewById(R.id.preview_root)
        private val barTop:        View             = view.findViewById(R.id.preview_bar_top)
        private val previewSwitch: View             = view.findViewById(R.id.preview_switch)
        private val dot:           View             = view.findViewById(R.id.preview_dot)
        private val dotTertiary:   View             = view.findViewById(R.id.preview_dot_tertiary)
        private val barBottom:     View             = view.findViewById(R.id.preview_bar_bottom)
        private val nameText:      TextView         = view.findViewById(R.id.text_theme_name)

        fun bind(theme: AppColorTheme) {
            val primary   = if (isDark) theme.primaryDark   else theme.primaryLight
            val secondary = if (isDark) theme.secondaryDark else theme.secondaryLight
            val tertiary  = if (isDark) theme.tertiaryDark  else theme.tertiaryLight
            val bg        = if (isDark) theme.backgroundDark else theme.backgroundLight
            val surface   = if (isDark) theme.backgroundDark else theme.backgroundLight

            val primaryInt   = colorToInt(primary)
            val secondaryInt = colorToInt(secondary)
            val tertiaryInt  = colorToInt(tertiary)
            val bgInt        = colorToInt(bg)
            val surfaceInt   = colorToInt(surface)

            root.setBackgroundColor(bgInt)
            card.setCardBackgroundColor(surfaceInt)

            barTop.backgroundTintList        = ColorStateList.valueOf(primaryInt)
            previewSwitch.backgroundTintList = ColorStateList.valueOf(primaryInt)
            dot.backgroundTintList           = ColorStateList.valueOf(secondaryInt)
            dotTertiary.backgroundTintList   = ColorStateList.valueOf(tertiaryInt)
            barBottom.backgroundTintList     = ColorStateList.valueOf(primaryInt)

            nameText.text = theme.displayName

            val isSelected = theme == selectedTheme
            nameText.setTextColor(
                if (isSelected) primaryInt
                else Color.parseColor("#99FFFFFF")
            )

            card.strokeWidth = if (isSelected) 8 else 0
            card.strokeColor = primaryInt

            itemView.setOnClickListener { onThemeSelected(theme) }
        }

        private fun colorToInt(color: androidx.compose.ui.graphics.Color): Int =
            Color.argb(
                (color.alpha * 255).toInt(),
                (color.red   * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue  * 255).toInt(),
            )
    }
}
