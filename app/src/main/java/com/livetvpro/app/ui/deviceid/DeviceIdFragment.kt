package com.livetvpro.app.ui.deviceid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.livetvpro.app.databinding.FragmentDeviceIdBinding
import com.livetvpro.app.utils.DeviceUtils
import java.security.MessageDigest

class DeviceIdFragment : Fragment() {

    private var _binding: FragmentDeviceIdBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceIdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deviceId = getDeviceFingerprint()
        binding.tvDeviceId.text = deviceId

        binding.btnCopy.apply {
            if (DeviceUtils.isTvDevice) {
                isFocusable = true
                isFocusableInTouchMode = false
                requestFocus()
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                        (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                         keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                         keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                        copyToClipboard(deviceId)
                        true
                    } else false
                }
            }
            setOnClickListener { copyToClipboard(deviceId) }
        }
    }

    private fun copyToClipboard(deviceId: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
        Toast.makeText(requireContext(), "Device ID copied!", Toast.LENGTH_SHORT).show()
    }

    private fun getDeviceFingerprint(): String {
        val raw = "${Build.BOARD}${Build.BRAND}${Build.DEVICE}" +
                  "${Build.HARDWARE}${Build.MANUFACTURER}" +
                  "${Build.MODEL}${Build.PRODUCT}"
        val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
