package com.livetvpro.app.ui.score

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.livetvpro.app.R
import com.livetvpro.app.utils.DeviceUtils

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreWebScreen(url: String) {
    var showError by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showProgress by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadTrigger by remember { mutableStateOf(0) }
    var contentVisible by remember { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!showError) {
            AndroidView(
                factory = { context ->
                    WebView(context).also { wv ->
                        webViewRef = wv
                        wv.setBackgroundColor(backgroundColor.toArgb())
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = if (DeviceUtils.isBigScreenLayout) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            } else {
                                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                        }
                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageCommitVisible(view: WebView, loadedUrl: String) {
                                if (loadedUrl != "about:blank") contentVisible = true
                            }
                            override fun onPageFinished(view: WebView, loadedUrl: String) {
                                if (loadedUrl != "about:blank") {
                                    showProgress = false
                                    contentVisible = true
                                }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    view.loadUrl("about:blank")
                                    showProgress = false
                                    showError = true
                                }
                            }
                        }
                        wv.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                                showProgress = newProgress < 100
                            }
                        }
                        if (url.isNotBlank()) wv.loadUrl(url)
                    }
                },
                update = { wv ->
                    if (loadTrigger > 0 && url.isNotBlank()) {
                        showError = false
                        showProgress = true
                        contentVisible = false
                        wv.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize().alpha(if (contentVisible) 1f else 0f)
            )
        }

        if (showProgress && !showError) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        if (showError) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = "No internet connection",
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        showError = false
                        showProgress = true
                        contentVisible = false
                        loadTrigger++
                        webViewRef?.loadUrl(url)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "RETRY",
                        fontFamily = BergenSans,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
