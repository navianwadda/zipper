package com.livetvpro.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.player.dialogs.FloatingPlayerDialog
import com.livetvpro.app.ui.theme.LiveTVProTheme
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

    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var listenerManager: NativeListenerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            LiveTVProTheme(themeManager) {
                SettingsScreen(
                    navController     = findNavController(),
                    listenerManager   = listenerManager,
                    onSaveStates      = { (activity as? SettingsActions)?.onSettingsSaveStates() },
                    onShareApp        = { (activity as? SettingsActions)?.onSettingsShareApp() },
                    onCopyright       = { (activity as? SettingsActions)?.onSettingsCopyright() },
                    onNotice          = { (activity as? SettingsActions)?.onSettingsNotice() },
                    onFloatingPlayer  = {
                        (activity as? SettingsActions)?.onSettingsFloatingPlayer()
                            ?: FloatingPlayerDialog.newInstance()
                                .show(childFragmentManager, FloatingPlayerDialog.TAG)
                    },
                    onExit            = { activity?.finishAffinity() },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    navController: NavController,
    listenerManager: NativeListenerManager,
    onSaveStates: () -> Unit,
    onShareApp: () -> Unit,
    onCopyright: () -> Unit,
    onNotice: () -> Unit,
    onFloatingPlayer: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {

        item { SectionHeader("Quick Access") }

        item {
            SettingsCard(icon = R.drawable.ic_network_stream, label = "Network Stream") {
                navController.navigate(R.id.networkStreamFragment)
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_playlist, label = "Playlists") {
                navController.navigate(R.id.playlistsFragment)
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_cricket, label = "Cricket Score") {
                navController.navigate(R.id.cricketScoreFragment)
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_football, label = "Football Score") {
                navController.navigate(R.id.footballScoreFragment)
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_device_id, label = "Device ID", bottomMargin = 16.dp) {
                navController.navigate(R.id.deviceIdFragment)
            }
        }

        item { SectionHeader("Appearance") }

        item {
            SettingsCard(
                icon        = R.drawable.ic_theme_auto,
                label       = "Appearance",
                sublabel    = "Theme, dark mode, AMOLED",
                iconTint    = MaterialTheme.colorScheme.primary,
                showChevron = true,
                bottomMargin = 16.dp,
            ) {
                navController.navigate(R.id.action_settings_to_appearance)
            }
        }

        item { SectionHeader("Settings") }

        item {
            SettingsCard(icon = R.drawable.ic_pip, label = "Floating Player") {
                onFloatingPlayer()
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_save_states, label = "Save States") {
                onSaveStates()
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_share, label = "Share App") {
                onShareApp()
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_contact, label = "Contact") {
                val url = listenerManager.getContactUrl().takeIf { it.isNotBlank() }
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_website, label = "Website") {
                val url = listenerManager.getWebUrl().takeIf { it.isNotBlank() }
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_email, label = "Email Us") {
                val email = listenerManager.getEmailUs().takeIf { it.isNotBlank() }
                if (email != null) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "LiveTVPro Support")
                    }
                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                }
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_copyright, label = "Copyright") {
                onCopyright()
            }
        }
        item {
            SettingsCard(icon = R.drawable.ic_info, label = "Notice") {
                onNotice()
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onExit() },
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFFF4444)),
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_exit),
                        contentDescription = null,
                        tint              = Color.White,
                        modifier          = Modifier.size(24.dp),
                    )
                    Text(
                        text     = "Exit",
                        color    = Color.White,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        fontSize = 14.sp,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(
    icon: Int,
    label: String,
    sublabel: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    bottomMargin: androidx.compose.ui.unit.Dp = 8.dp,
    onClick: () -> Unit,
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomMargin)
            .clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter            = painterResource(icon),
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sublabel != null) {
                    Text(
                        text  = sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (showChevron) {
                Icon(
                    painter            = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier           = Modifier
                        .size(18.dp)
                        .then(
                            Modifier.padding(start = 4.dp)
                        ),
                )
            }
        }
    }
}
