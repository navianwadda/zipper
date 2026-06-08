package com.livetvpro.app.ui.networkstream

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NetworkStreamFragment : Fragment() {

    private val viewModel: NetworkStreamViewModel by viewModels()

    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            LiveTVProTheme(themeManager) {
                NetworkStreamScreen(
                    viewModel = viewModel,
                    onPlay = { streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme ->
                        launchPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
                    }
                )
            }
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
        if (streamUrl.isBlank()) {
            Toast.makeText(requireContext(), com.livetvpro.app.R.string.stream_url_required, Toast.LENGTH_SHORT).show()
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(requireContext())

        if (DeviceUtils.isTvDevice || !floatingEnabled) {
            launchFullscreenPlayer(streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
            return
        }
        if (!hasPermission) {
            Toast.makeText(requireContext(), "Overlay permission required for floating player. Opening normally instead.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(requireContext(), "Failed to launch floating player: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
