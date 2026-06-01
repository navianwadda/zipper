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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.livetvpro.app.R
import java.security.MessageDigest

class DeviceIdFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                DeviceIdScreen()
            }
        }
    }
}

@Composable
fun DeviceIdScreen() {
    val context  = LocalContext.current
    val deviceId = remember { getDeviceFingerprint() }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = "YOUR DEVICE ID",
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            letterSpacing = 0.15.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text       = deviceId,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign  = TextAlign.Center,
                fontSize   = 18.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 28.sp,
                color      = MaterialTheme.colorScheme.onBackground,
            )
        }

        Button(
            onClick  = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape  = RoundedCornerShape(14.dp),
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_paste),
                contentDescription = null,
                modifier           = Modifier.padding(end = 8.dp),
            )
            Text(text = "COPY TO CLIPBOARD", letterSpacing = 0.15.sp)
        }
    }
}

private fun getDeviceFingerprint(): String {
    val raw = "${Build.BOARD}${Build.BRAND}${Build.DEVICE}" +
              "${Build.HARDWARE}${Build.MANUFACTURER}" +
              "${Build.MODEL}${Build.PRODUCT}"
    val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
