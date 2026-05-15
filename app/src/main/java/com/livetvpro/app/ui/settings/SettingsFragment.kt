package com.livetvpro.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
import com.livetvpro.app.databinding.FragmentSettingsBinding
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
import com.livetvpro.app.utils.NativeListenerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var listenerManager: NativeListenerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Network Stream
        binding.cardNetworkStream.setOnClickListener {
            findNavController().navigate(R.id.networkStreamFragment)
        }
        
        // Playlists
        binding.cardPlaylists.setOnClickListener {
            findNavController().navigate(R.id.playlistsFragment)
        }
        
        // Cricket Score
        binding.cardCricketScore.setOnClickListener {
            findNavController().navigate(R.id.cricketScoreFragment)
        }
        
        // Football Score
        binding.cardFootballScore.setOnClickListener {
            findNavController().navigate(R.id.footballScoreFragment)
        }
        
        // Device ID
        binding.cardDeviceId.setOnClickListener {
            findNavController().navigate(R.id.deviceIdFragment)
        }
        
        // Floating Player
        binding.cardFloatingPlayer.setOnClickListener {
            FloatingPlayerDialog.newInstance().show(childFragmentManager, FloatingPlayerDialog.TAG)
        }
        
        // Save States
        binding.cardSaveStates.setOnClickListener {
            showSaveStatesDialog()
        }
        
        // Share App
        binding.cardShareApp.setOnClickListener {
            shareApp()
        }
        
        // Contact
        binding.cardContact.setOnClickListener {
            val contactUrl = listenerManager.getContactUrl()
            if (contactUrl.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contactUrl)))
            }
        }
        
        // Website
        binding.cardWebsite.setOnClickListener {
            val webUrl = listenerManager.getWebUrl()
            if (webUrl.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            }
        }
        
        // Email
        binding.cardEmail.setOnClickListener {
            val email = listenerManager.getEmailUs()
            if (email.isNotBlank()) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
        }
        
        // Copyright
        binding.cardCopyright.setOnClickListener {
            showCopyrightDialog()
        }
        
        // Notice
        binding.cardNotice.setOnClickListener {
            showNoticeDialog()
        }
        
        // Exit
        binding.cardExit.setOnClickListener {
            activity?.finishAffinity()
        }
    }

    private fun showSaveStatesDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save States")
            .setMessage("Save current playback states?")
            .setPositiveButton("Save") { _, _ ->
                // Save states logic
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Live TV Pro")
            putExtra(Intent.EXTRA_TEXT, "Check out Live TV Pro app!")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun showCopyrightDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Copyright")
            .setMessage("© 2024 Live TV Pro. All rights reserved.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showNoticeDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Notice")
            .setMessage("This app is for demonstration purposes only.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}