package com.livetvpro.app.ui.appearance

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.databinding.FragmentAppearanceBinding
import com.livetvpro.app.ui.theme.AppColorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceFragment : Fragment() {

    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var themeManager: ThemeManager

    private var adapter: ThemePreviewAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.appearanceScroll) { v, insets ->
            val navBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val bottomPad = navBars.bottom +
                    v.resources.getDimensionPixelSize(R.dimen.nav_bottom_margin) +
                    v.resources.getDimensionPixelSize(R.dimen.nav_height)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomPad)
            insets
        }

        binding.toolbarAppearance.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val currentMode = themeManager.getThemeMode()
        updateDarkModeButtons(currentMode)

        binding.btnDark.setOnClickListener   { setDarkMode(ThemeManager.THEME_DARK) }
        binding.btnLight.setOnClickListener  { setDarkMode(ThemeManager.THEME_LIGHT) }
        binding.btnSystem.setOnClickListener { setDarkMode(ThemeManager.THEME_AUTO) }

        binding.switchAmoled.isChecked = themeManager.isAmoledMode()
        binding.switchAmoled.setOnCheckedChangeListener { _, isChecked ->
            themeManager.setAmoledMode(isChecked)
            applyAndRecreate()
        }

        val isDark = when (themeManager.getThemeMode()) {
            ThemeManager.THEME_LIGHT -> false
            ThemeManager.THEME_DARK  -> true
            else -> (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        adapter = ThemePreviewAdapter(
            isDark        = isDark,
            selectedTheme = themeManager.getColorTheme(),
            onThemeSelected = { theme ->
                themeManager.setColorTheme(theme)
                adapter?.updateSelection(theme)
                applyAndRecreate()
            },
        )

        binding.rvThemes.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvThemes.adapter = adapter

        val selectedIndex = AppColorTheme.entries.indexOf(themeManager.getColorTheme())
        if (selectedIndex > 0) {
            binding.rvThemes.scrollToPosition(selectedIndex)
        }
    }

    private fun setDarkMode(mode: Int) {
        themeManager.setThemeMode(mode)
        updateDarkModeButtons(mode)
        applyAndRecreate()
    }

    private fun updateDarkModeButtons(mode: Int) {
        binding.btnDark.isSelected   = mode == ThemeManager.THEME_DARK
        binding.btnLight.isSelected  = mode == ThemeManager.THEME_LIGHT
        binding.btnSystem.isSelected = mode == ThemeManager.THEME_AUTO

        val primary = requireContext().getColor(R.color.primary)
        val white   = android.graphics.Color.WHITE
        val dimWhite = android.graphics.Color.parseColor("#66FFFFFF")

        binding.iconDark.setColorFilter  (if (mode == ThemeManager.THEME_DARK)  primary else white)
        binding.iconLight.setColorFilter (if (mode == ThemeManager.THEME_LIGHT) primary else white)
        binding.iconSystem.setColorFilter(if (mode == ThemeManager.THEME_AUTO)  primary else white)

        binding.textDark.setTextColor  (if (mode == ThemeManager.THEME_DARK)  primary else dimWhite)
        binding.textLight.setTextColor (if (mode == ThemeManager.THEME_LIGHT) primary else dimWhite)
        binding.textSystem.setTextColor(if (mode == ThemeManager.THEME_AUTO)  primary else dimWhite)
    }

    private fun applyAndRecreate() {
        themeManager.applyTheme()
        activity?.recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
