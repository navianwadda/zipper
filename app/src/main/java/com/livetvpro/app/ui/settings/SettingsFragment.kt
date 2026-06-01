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

interface SettingsActions {
    fun onSettingsSaveStates()
    fun onSettingsCopyright()
    fun onSettingsNotice()
    fun onSettingsShareApp()
    fun onSettingsFloatingPlayer()
}

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
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.navSettingsScroll) { v, insets ->
            val navBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val bottom = navBars.bottom + v.resources.getDimensionPixelSize(com.livetvpro.app.R.dimen.nav_bottom_margin) + v.resources.getDimensionPixelSize(com.livetvpro.app.R.dimen.nav_height)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }

        binding.cardAppearance.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_appearance)
        }

        binding.cardNetworkStream.setOnClickListener {
            findNavController().navigate(R.id.networkStreamFragment)
        }

        binding.cardPlaylists.setOnClickListener {
            findNavController().navigate(R.id.playlistsFragment)
        }

        binding.cardCricketScore.setOnClickListener {
            findNavController().navigate(R.id.cricketScoreFragment)
        }

        binding.cardFootballScore.setOnClickListener {
            findNavController().navigate(R.id.footballScoreFragment)
        }

        binding.cardDeviceId.setOnClickListener {
            findNavController().navigate(R.id.deviceIdFragment)
        }

        binding.cardFloatingPlayer.setOnClickListener {
            (activity as? SettingsActions)?.onSettingsFloatingPlayer()
                ?: FloatingPlayerDialog.newInstance().show(childFragmentManager, FloatingPlayerDialog.TAG)
        }

        binding.cardSaveStates.setOnClickListener {
            (activity as? SettingsActions)?.onSettingsSaveStates()
        }

        binding.cardShareApp.setOnClickListener {
            (activity as? SettingsActions)?.onSettingsShareApp()
        }

        binding.cardContact.setOnClickListener {
            val contactUrl = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
            if (contactUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contactUrl)))
            }
        }

        binding.cardWebsite.setOnClickListener {
            val webUrl = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
            if (webUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            }
        }

        binding.cardEmail.setOnClickListener {
            val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
            if (email != null) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
        }

        binding.cardCopyright.setOnClickListener {
            (activity as? SettingsActions)?.onSettingsCopyright()
        }

        binding.cardNotice.setOnClickListener {
            (activity as? SettingsActions)?.onSettingsNotice()
        }

        binding.cardExit.setOnClickListener {
            activity?.finishAffinity()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
