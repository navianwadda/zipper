package com.livetvpro.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
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

    @Inject
    lateinit var listenerManager: NativeListenerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    onNetworkStream      = { findNavController().navigate(R.id.networkStreamFragment) },
                    onPlaylists          = { findNavController().navigate(R.id.playlistsFragment) },
                    onCricketScore       = { findNavController().navigate(R.id.cricketScoreFragment) },
                    onFootballScore      = { findNavController().navigate(R.id.footballScoreFragment) },
                    onDeviceId           = { findNavController().navigate(R.id.deviceIdFragment) },
                    onAppearance         = { findNavController().navigate(R.id.action_settings_to_appearance) },
                    onFloatingPlayer     = {
                        (activity as? SettingsActions)?.onSettingsFloatingPlayer()
                            ?: FloatingPlayerDialog.newInstance().show(childFragmentManager, FloatingPlayerDialog.TAG)
                    },
                    onSaveStates         = { (activity as? SettingsActions)?.onSettingsSaveStates() },
                    onShareApp           = { (activity as? SettingsActions)?.onSettingsShareApp() },
                    onContact            = {
                        val url = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
                        url?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    },
                    onWebsite            = {
                        val url = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
                        url?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    },
                    onEmail              = {
                        val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
                        email?.let {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                            }
                            startActivity(Intent.createChooser(intent, "Send Email"))
                        }
                    },
                    onCopyright          = { (activity as? SettingsActions)?.onSettingsCopyright() },
                    onNotice             = { (activity as? SettingsActions)?.onSettingsNotice() },
                    onExit               = { activity?.finishAffinity() },
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onNetworkStream: () -> Unit,
    onPlaylists: () -> Unit,
    onCricketScore: () -> Unit,
    onFootballScore: () -> Unit,
    onDeviceId: () -> Unit,
    onAppearance: () -> Unit,
    onFloatingPlayer: () -> Unit,
    onSaveStates: () -> Unit,
    onShareApp: () -> Unit,
    onContact: () -> Unit,
    onWebsite: () -> Unit,
    onEmail: () -> Unit,
    onCopyright: () -> Unit,
    onNotice: () -> Unit,
    onExit: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 90.dp),
    ) {
        item {
            SettingsSectionLabel("Quick Access")
            SettingsRow(icon = R.drawable.ic_network_stream, label = "Network Stream", onClick = onNetworkStream)
            SettingsRow(icon = R.drawable.ic_playlist,       label = "Playlists",       onClick = onPlaylists)
            SettingsRow(icon = R.drawable.ic_cricket,        label = "Cricket Score",   onClick = onCricketScore)
            SettingsRow(icon = R.drawable.ic_football,       label = "Football Score",  onClick = onFootballScore)
            SettingsRow(icon = R.drawable.ic_device_id,      label = "Device ID",       onClick = onDeviceId)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            SettingsSectionLabel("Preferences")
            SettingsRow(icon = R.drawable.ic_appearance,      label = "Appearance",      onClick = onAppearance)
            SettingsRow(icon = R.drawable.ic_floating_player, label = "Floating Player", onClick = onFloatingPlayer)
            SettingsRow(icon = R.drawable.ic_save_states,     label = "Save States",     onClick = onSaveStates)
            SettingsRow(icon = R.drawable.ic_share,           label = "Share App",       onClick = onShareApp)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            SettingsSectionLabel("Support")
            SettingsRow(icon = R.drawable.ic_contact,   label = "Contact",   onClick = onContact)
            SettingsRow(icon = R.drawable.ic_website,   label = "Website",   onClick = onWebsite)
            SettingsRow(icon = R.drawable.ic_email,     label = "Email Us",  onClick = onEmail)
            SettingsRow(icon = R.drawable.ic_copyright, label = "Copyright", onClick = onCopyright)
            SettingsRow(icon = R.drawable.ic_info,      label = "Notice",    onClick = onNotice)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExit() },
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4444)),
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_exit),
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Exit", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun SettingsRow(icon: Int, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter            = painterResource(icon),
                contentDescription = null,
                modifier           = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
