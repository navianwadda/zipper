package com.livetvpro.app.ui.player.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.databinding.DialogFloatingPlayerSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FloatingPlayerDialog : DialogFragment() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var _binding: DialogFloatingPlayerSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogFloatingPlayerSettingsBinding.inflate(LayoutInflater.from(requireContext()))

        setupViews()
        loadSettings()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private fun setupViews() {
        val maxWindowsOptions = listOf("Disable (1 window)", "2 windows", "3 windows", "4 windows", "5 windows")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            maxWindowsOptions
        )
        binding.maxFloatingWindowsDropdown.setAdapter(adapter)

        binding.floatingPlayerSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setFloatingPlayerEnabled(isChecked)
            binding.maxFloatingWindowsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.maxFloatingWindowsDropdown.setOnItemClickListener { _, _, position, _ ->
            val maxWindows = when (position) {
                0 -> 1
                1 -> 2
                2 -> 3
                3 -> 4
                4 -> 5
                else -> 1
            }
            preferencesManager.setMaxFloatingWindows(maxWindows)
        }
    }

    private fun loadSettings() {
        val isEnabled = preferencesManager.isFloatingPlayerEnabled()
        val maxWindows = preferencesManager.getMaxFloatingWindows()

        binding.floatingPlayerSwitch.isChecked = isEnabled
        binding.maxFloatingWindowsContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE

        val selectedText = when (maxWindows) {
            0, 1 -> "Disable (1 window)"
            2 -> "2 windows"
            3 -> "3 windows"
            4 -> "4 windows"
            5 -> "5 windows"
            else -> "Disable (1 window)"
        }
        binding.maxFloatingWindowsDropdown.setText(selectedText, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FloatingPlayerDialog"

        fun newInstance() = FloatingPlayerDialog()
    }
}
