package com.livetvpro.app.ui.networkstream

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper

@Composable
fun NetworkStreamRoute(preferencesManager: PreferencesManager) {
    val context = LocalContext.current
    NetworkStreamScreen(
        onPlay = { streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme ->
            launchPlayer(context, preferencesManager, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
        }
    )
}

private fun launchPlayer(
    context: Context,
    preferencesManager: PreferencesManager,
    streamUrl: String,
    cookie: String,
    referer: String,
    origin: String,
    drmLicense: String,
    userAgent: String,
    drmScheme: String
) {
    if (streamUrl.isBlank()) {
        Toast.makeText(context, R.string.stream_url_required, Toast.LENGTH_SHORT).show()
        return
    }
    val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
    val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

    if (DeviceUtils.isTvDevice || !floatingEnabled) {
        launchFullscreenPlayer(context, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
        return
    }
    if (!hasPermission) {
        Toast.makeText(context, "Overlay permission required for floating player. Opening normally instead.", Toast.LENGTH_LONG).show()
        launchFullscreenPlayer(context, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
        return
    }
    try {
        FloatingPlayerHelper.launchFloatingPlayerWithNetworkStream(
            context = context,
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
        Toast.makeText(context, "Failed to launch floating player: ${e.message}", Toast.LENGTH_SHORT).show()
        launchFullscreenPlayer(context, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme)
    }
}

private fun launchFullscreenPlayer(
    context: Context,
    streamUrl: String,
    cookie: String,
    referer: String,
    origin: String,
    drmLicense: String,
    userAgent: String,
    drmScheme: String
) {
    val intent = Intent(context, PlayerActivity::class.java).apply {
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
    context.startActivity(intent)
}
