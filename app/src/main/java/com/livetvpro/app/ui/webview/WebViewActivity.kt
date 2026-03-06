package com.livetvpro.app.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var countDownTimer: CountDownTimer? = null

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_DURATION = "extra_duration"

        fun start(context: Context, url: String, durationSeconds: Long) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java).apply {
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_DURATION, durationSeconds)
                }
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val durationSeconds = intent.getLongExtra(EXTRA_DURATION, 10L)

        val dp = resources.displayMetrics.density

        val root = FrameLayout(this)

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (4 * dp).toInt())
        }

        val timerLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#AA000000"))
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            text = "Closing in ${durationSeconds}s"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                it.topMargin = (4 * dp).toInt() + (4 * dp).toInt()
                it.marginEnd = (8 * dp).toInt()
            }
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                    val newWebView = WebView(view.context)
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                            view.loadUrl(url)
                        }
                    }
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }
            }
            loadUrl(url.ifEmpty { "about:blank" })
        }

        root.addView(webView)
        root.addView(progressBar)
        root.addView(timerLabel)
        setContentView(root)

        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                timerLabel.text = "Closing in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                setResult(RESULT_OK)
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
