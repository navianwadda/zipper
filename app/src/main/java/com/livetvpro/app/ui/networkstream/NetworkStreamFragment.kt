package com.livetvpro.app.ui.networkstream
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.livetvpro.app.R
import com.livetvpro.app.databinding.FragmentNetworkStreamBinding
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
@AndroidEntryPoint
class NetworkStreamFragment : Fragment() {
    private var _binding: FragmentNetworkStreamBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NetworkStreamViewModel by viewModels()
    @Inject
    lateinit var preferencesManager: com.livetvpro.app.data.local.PreferencesManager
    private val userAgentOptions = listOf(
        "Default",
        "Chrome(Android)",
        "Chrome(PC)",
        "IE(PC)",
        "Firefox(PC)",
        "iPhone",
        "Nokia",
        "Custom"
    )
    private val drmSchemeOptions = listOf(
        "clearkey",
        "widevine",
        "playready"
    )
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkStreamBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        restoreFieldState()
        setupCustomUserAgentVisibility()
        setupPasteIcons()
        setupFieldStateSaving()
        setupPlayButton()
        if (DeviceUtils.isTvDevice) {
            binding.etStreamUrl.requestFocus()
        }
    }
    private fun restoreFieldState() {
        binding.etStreamUrl.setText(viewModel.streamUrl)
        binding.etCookie.setText(viewModel.cookie)
        binding.etReferer.setText(viewModel.referer)
        binding.etOrigin.setText(viewModel.origin)
        binding.etDrmLicense.setText(viewModel.drmLicense)
        binding.etCustomUserAgent.setText(viewModel.customUserAgent)
        binding.actvUserAgent.setText(viewModel.selectedUserAgent, false)
        binding.actvDrmScheme.setText(viewModel.selectedDrmScheme, false)
        if (viewModel.selectedUserAgent == "Custom") {
            binding.tilCustomUserAgent.visibility = View.VISIBLE
        }
    }

    private fun setupFieldStateSaving() {
        binding.etStreamUrl.doOnTextChanged { text, _, _, _ -> viewModel.streamUrl = text?.toString() ?: "" }
        binding.etCookie.doOnTextChanged { text, _, _, _ -> viewModel.cookie = text?.toString() ?: "" }
        binding.etReferer.doOnTextChanged { text, _, _, _ -> viewModel.referer = text?.toString() ?: "" }
        binding.etOrigin.doOnTextChanged { text, _, _, _ -> viewModel.origin = text?.toString() ?: "" }
        binding.etDrmLicense.doOnTextChanged { text, _, _, _ -> viewModel.drmLicense = text?.toString() ?: "" }
        binding.etCustomUserAgent.doOnTextChanged { text, _, _, _ -> viewModel.customUserAgent = text?.toString() ?: "" }
        binding.actvUserAgent.doOnTextChanged { text, _, _, _ -> viewModel.selectedUserAgent = text?.toString() ?: "Default" }
        binding.actvDrmScheme.doOnTextChanged { text, _, _, _ -> viewModel.selectedDrmScheme = text?.toString() ?: "clearkey" }
    }

    private fun setupPasteIcons() {
        listOf(
            binding.tilStreamUrl       to binding.etStreamUrl,
            binding.tilCookie          to binding.etCookie,
            binding.tilReferer         to binding.etReferer,
            binding.tilOrigin          to binding.etOrigin,
            binding.tilDrmLicense      to binding.etDrmLicense,
            binding.tilCustomUserAgent to binding.etCustomUserAgent
        ).forEach { (til, et) ->
            et.highlightColor = 0x55EF4444.toInt()
            updateEndIcon(til, et.text?.isNotEmpty() == true)
            et.doOnTextChanged { text, _, _, _ ->
                updateEndIcon(til, !text.isNullOrEmpty())
            }
            til.setEndIconOnClickListener {
                if (et.text?.isNotEmpty() == true) {
                    et.setText("")
                } else {
                    pasteFromClipboard(et)
                }
            }
        }
    }
    private fun updateEndIcon(til: com.google.android.material.textfield.TextInputLayout, hasText: Boolean) {
        til.endIconDrawable = if (hasText) {
            androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
        } else {
            androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_paste)
        }
    }
    private fun pasteFromClipboard(et: com.google.android.material.textfield.TextInputEditText) {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(requireContext())
                ?.toString()
                ?.takeIf { it.isNotEmpty() }
            if (text != null) {
                et.setText(text)
                et.setSelection(text.length)
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not read clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    private fun setupDropdowns() {
        if (DeviceUtils.isTvDevice) {
            binding.actvUserAgent.setText(userAgentOptions[0], false)
            binding.actvUserAgent.setOnClickListener { showTvSelectionDialog("User Agent", userAgentOptions, binding.actvUserAgent) }
            binding.actvUserAgent.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) binding.actvUserAgent.setOnKeyListener { _, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                        (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                        showTvSelectionDialog("User Agent", userAgentOptions, binding.actvUserAgent)
                        true
                    } else false
                }
            }
            binding.actvDrmScheme.setText(drmSchemeOptions[0], false)
            binding.actvDrmScheme.setOnClickListener { showTvSelectionDialog("DRM Scheme", drmSchemeOptions, binding.actvDrmScheme) }
            binding.actvDrmScheme.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) binding.actvDrmScheme.setOnKeyListener { _, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                        (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                        showTvSelectionDialog("DRM Scheme", drmSchemeOptions, binding.actvDrmScheme)
                        true
                    } else false
                }
            }
        } else {
            val userAgentAdapter = ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown_menu,
                userAgentOptions
            )
            binding.actvUserAgent.setAdapter(userAgentAdapter)
            binding.actvUserAgent.setText(userAgentOptions[0], false)
            val drmSchemeAdapter = ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown_menu,
                drmSchemeOptions
            )
            binding.actvDrmScheme.setAdapter(drmSchemeAdapter)
            binding.actvDrmScheme.setText(drmSchemeOptions[0], false)
        }
    }
    private fun showTvSelectionDialog(title: String, options: List<String>, target: AutoCompleteTextView) {
        val currentSelection = target.text?.toString() ?: options[0]
        val checkedIndex = options.indexOf(currentSelection).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), checkedIndex) { dialog, which ->
                target.setText(options[which], false)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun setupCustomUserAgentVisibility() {
        binding.actvUserAgent.doOnTextChanged { text, _, _, _ ->
            if (text.toString() == "Custom") {
                binding.tilCustomUserAgent.visibility = View.VISIBLE
            } else {
                binding.tilCustomUserAgent.visibility = View.GONE
            }
        }
    }
    private fun setupPlayButton() {
        binding.fabPlay.setOnClickListener {
            val streamUrl = binding.etStreamUrl.text?.toString()?.trim()
            if (streamUrl.isNullOrEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.stream_url_required,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val cookie = binding.etCookie.text?.toString()?.trim() ?: ""
            val referer = binding.etReferer.text?.toString()?.trim() ?: ""
            val origin = binding.etOrigin.text?.toString()?.trim() ?: ""
            val drmLicense = binding.etDrmLicense.text?.toString()?.trim() ?: ""
            val userAgentSelection = binding.actvUserAgent.text?.toString() ?: "Default"
            val drmScheme = binding.actvDrmScheme.text?.toString() ?: "clearkey"
            val userAgent = if (userAgentSelection == "Custom") {
                binding.etCustomUserAgent.text?.toString()?.trim() ?: "Default"
            } else {
                userAgentSelection
            }
            launchPlayer(
                streamUrl = streamUrl,
                cookie = cookie,
                referer = referer,
                origin = origin,
                drmLicense = drmLicense,
                userAgent = userAgent,
                drmScheme = drmScheme
            )
        }
    }
    private fun launchPlayer(
        streamUrl: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String
    ) {
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (DeviceUtils.isTvDevice || !floatingEnabled) {
            launchFullscreenPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
            return
        }
        if (floatingEnabled) {
            if (!hasPermission) {
                Toast.makeText(
                    requireContext(),
                    "Overlay permission required for floating player. Opening normally instead.",
                    Toast.LENGTH_LONG
                ).show()
                launchFullscreenPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayerWithNetworkStream(
                    context = requireContext(),
                    streamUrl = streamUrl,
                    cookie = cookie,
                    referer = referer,
                    origin = origin,
                    drmLicense = drmLicense,
                    userAgent = userAgent,
                    drmScheme = drmScheme,
                    streamName = "Network Stream"
                )
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to launch floating player: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                launchFullscreenPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
            }
        } else {
            launchFullscreenPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
        }
    }
    private fun launchFullscreenPlayer(
        streamUrl: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String
    ) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("IS_NETWORK_STREAM", true)
            putExtra("STREAM_URL", streamUrl)
            putExtra("COOKIE", cookie)
            putExtra("REFERER", referer)
            putExtra("ORIGIN", origin)
            putExtra("DRM_LICENSE", drmLicense)
            putExtra("USER_AGENT", userAgent)
            putExtra("DRM_SCHEME", drmScheme)
            putExtra("CHANNEL_NAME", "Network Stream")
        }
        startActivity(intent)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
