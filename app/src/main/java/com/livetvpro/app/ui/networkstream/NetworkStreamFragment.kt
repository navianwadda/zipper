package com.livetvpro.app.ui.networkstream

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NetworkStreamFragment : Fragment() {

    private val viewModel: NetworkStreamViewModel by viewModels()

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                NetworkStreamScreen(
                    viewModel          = viewModel,
                    preferencesManager = preferencesManager,
                    onPlay             = { url, cookie, referer, origin, drmLicense, userAgent, drmScheme ->
                        launchPlayer(url, cookie, referer, origin, drmLicense, userAgent, drmScheme)
                    },
                )
            }
        }
    }

    private fun launchPlayer(
        streamUrl: String, cookie: String, referer: String, origin: String,
        drmLicense: String, userAgent: String, drmScheme: String
    ) {
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission   = FloatingPlayerHelper.hasOverlayPermission(requireContext())
        if (DeviceUtils.isTvDevice || !floatingEnabled) {
            launchFullscreen(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
            return
        }
        if (!hasPermission) {
            Toast.makeText(requireContext(), "Overlay permission required. Opening normally.", Toast.LENGTH_LONG).show()
            launchFullscreen(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
            return
        }
        try {
            FloatingPlayerHelper.launchFloatingPlayerWithNetworkStream(
                context    = requireContext(), streamUrl = streamUrl, cookie = cookie,
                referer    = referer, origin = origin, drmLicense = drmLicense,
                userAgent  = userAgent, drmScheme = drmScheme, streamName = "Network Stream"
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to launch floating player: ${e.message}", Toast.LENGTH_SHORT).show()
            launchFullscreen(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
        }
    }

    private fun launchFullscreen(
        streamUrl: String, cookie: String, referer: String, origin: String,
        drmLicense: String, userAgent: String, drmScheme: String
    ) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("IS_NETWORK_STREAM", true)
            putExtra("STREAM_URL", streamUrl)
            putExtra("COOKIE", cookie)
            putExtra("REFERER", referer)
            putExtra("ORIGIN", origin)
            putExtra("DRM_LICENSE", drmLicense)
            putExtra("USER_AGENT", userAgent)
            putExtra("DRM_SCHEME", drmScheme)
            putExtra("CHANNEL_NAME", "Network Stream")
        })
    }
}

private val userAgentOptions = listOf("Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)", "iPhone", "Nokia", "Custom")
private val drmSchemeOptions  = listOf("clearkey", "widevine", "playready")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStreamScreen(
    viewModel: NetworkStreamViewModel,
    preferencesManager: PreferencesManager,
    onPlay: (String, String, String, String, String, String, String) -> Unit,
) {
    val context = LocalContext.current

    var streamUrl       by remember { mutableStateOf(viewModel.streamUrl) }
    var cookie          by remember { mutableStateOf(viewModel.cookie) }
    var referer         by remember { mutableStateOf(viewModel.referer) }
    var origin          by remember { mutableStateOf(viewModel.origin) }
    var drmLicense      by remember { mutableStateOf(viewModel.drmLicense) }
    var customUserAgent by remember { mutableStateOf(viewModel.customUserAgent) }
    var selectedAgent   by remember { mutableStateOf(viewModel.selectedUserAgent) }
    var selectedDrm     by remember { mutableStateOf(viewModel.selectedDrmScheme) }
    var agentExpanded   by remember { mutableStateOf(false) }
    var drmExpanded     by remember { mutableStateOf(false) }

    fun paste(setter: (String) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        setter(clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: "")
    }

    @Composable
    fun StreamField(label: String, value: String, onValueChange: (String) -> Unit) {
        OutlinedTextField(
            value         = value,
            onValueChange = {
                onValueChange(it)
                viewModel.let { vm ->
                    when (label) {
                        "Stream URL"         -> vm.streamUrl = it
                        "Cookie"             -> vm.cookie = it
                        "Referer"            -> vm.referer = it
                        "Origin"             -> vm.origin = it
                        "DRM License URL"    -> vm.drmLicense = it
                        "Custom User Agent"  -> vm.customUserAgent = it
                    }
                }
            },
            label         = { Text(label) },
            modifier      = Modifier.fillMaxWidth(),
            trailingIcon  = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                } else {
                    IconButton(onClick = { paste(onValueChange) }) {
                        Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste")
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StreamField("Stream URL",      streamUrl,       { streamUrl = it })
            StreamField("Cookie",          cookie,          { cookie = it })
            StreamField("Referer",         referer,         { referer = it })
            StreamField("Origin",          origin,          { origin = it })
            StreamField("DRM License URL", drmLicense,      { drmLicense = it })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded         = agentExpanded,
                    onExpandedChange = { agentExpanded = it },
                    modifier         = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value         = selectedAgent,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("User Agent") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(agentExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = agentExpanded, onDismissRequest = { agentExpanded = false }) {
                        userAgentOptions.forEach { option ->
                            DropdownMenuItem(
                                text    = { Text(option) },
                                onClick = {
                                    selectedAgent = option
                                    viewModel.selectedUserAgent = option
                                    agentExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded         = drmExpanded,
                    onExpandedChange = { drmExpanded = it },
                    modifier         = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value         = selectedDrm,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("DRM Scheme") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(drmExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = drmExpanded, onDismissRequest = { drmExpanded = false }) {
                        drmSchemeOptions.forEach { option ->
                            DropdownMenuItem(
                                text    = { Text(option) },
                                onClick = {
                                    selectedDrm = option
                                    viewModel.selectedDrmScheme = option
                                    drmExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedAgent == "Custom") {
                StreamField("Custom User Agent", customUserAgent, { customUserAgent = it })
            }
        }

        FloatingActionButton(
            onClick  = {
                if (streamUrl.isBlank()) {
                    Toast.makeText(context, "Stream URL is required", Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                val resolvedAgent = if (selectedAgent == "Custom") customUserAgent else selectedAgent
                onPlay(streamUrl, cookie, referer, origin, drmLicense, resolvedAgent, selectedDrm)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
        }
    }
}
