package com.livetvpro.app.ui.score

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.livetvpro.app.utils.DeviceUtils

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreWebViewScreen(url: String) {
    var progress   by remember { mutableFloatStateOf(0f) }
    var showLoader by remember { mutableStateOf(true) }
    var showError  by remember { mutableStateOf(false) }
    var retryKey   by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (url.isNotBlank()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showLoader) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled    = true
                                domStorageEnabled    = true
                                loadWithOverviewMode = true
                                useWideViewPort      = true
                                builtInZoomControls  = true
                                displayZoomControls  = false
                                mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString      = if (DeviceUtils.isTvDevice) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                } else {
                                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, pageUrl: String) {
                                    if (pageUrl != "about:blank") showLoader = false
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) {
                                        loadUrl("about:blank")
                                        showLoader = false
                                        showError  = true
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress   = newProgress.toFloat()
                                    showLoader = newProgress < 100
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (retryKey > 0 && !showError) webView.loadUrl(url)
                    },
                )
            }
        }

        if (showError) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "No internet connection", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { showError = false; showLoader = true; retryKey++ }) {
                    Text("RETRY")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
