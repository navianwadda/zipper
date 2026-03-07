package com.livetvpro.app.ui.webview

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Proxy
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.livetvpro.app.MainActivity

class WebActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var countDownTimer: CountDownTimer? = null
    private var pageLoaded = false
    private var timerStarted = false
    private var usingCustomTabs = false
    private var validated = false
    private var customTabLaunched = false
    private var customTabPaused = false

    private lateinit var timerLabel: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var customTabDurationSeconds = 0L
    private var customTabSecondsElapsed = 0L
    private val customTabTickRunnable = object : Runnable {
        override fun run() {
            customTabSecondsElapsed++
            val remaining = customTabDurationSeconds - customTabSecondsElapsed
            if (remaining <= 0) {
                onCustomTabTimerFinished()
            } else {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    companion object {
        private const val EXTRA_URL      = "extra_url"
        private const val EXTRA_DURATION = "extra_duration"
        const val RESULT_VALIDATED       = 100

        private const val ACTION_TIMER_DONE = "com.livetvpro.app.CUSTOM_TAB_TIMER_DONE"

        private val CUSTOM_TABS_BROWSERS = listOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "com.chrome.canary", "org.mozilla.firefox", "org.mozilla.firefox_beta",
            "com.microsoft.emmx", "com.brave.browser", "com.opera.browser",
            "com.opera.mini.native", "com.sec.android.app.sbrowser", "com.UCMobile.intl"
        )

        fun isCustomTabsSupported(context: Context): Boolean {
            val pm = context.packageManager
            val serviceIntent = Intent("android.support.customtabs.action.CustomTabsService")
            if (pm.queryIntentServices(serviceIntent, 0).isNotEmpty()) return true
            return CUSTOM_TABS_BROWSERS.any {
                try { pm.getPackageInfo(it, 0); true }
                catch (e: PackageManager.NameNotFoundException) { false }
            }
        }

        fun getCustomTabsPackage(context: Context): String? {
            val pm = context.packageManager
            val serviceIntent = Intent("android.support.customtabs.action.CustomTabsService")
            val resolved = pm.queryIntentServices(serviceIntent, 0)
            if (resolved.isNotEmpty()) return resolved.first().serviceInfo.packageName
            return CUSTOM_TABS_BROWSERS.firstOrNull {
                try { pm.getPackageInfo(it, 0); true }
                catch (e: PackageManager.NameNotFoundException) { false }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url             = intent.getStringExtra(EXTRA_URL) ?: ""
        val durationSeconds = intent.getLongExtra(EXTRA_DURATION, 10L)

        if (isVpnOrProxyActive()) {
            Toast.makeText(this, "Please disable VPN/Proxy to continue", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED); finish(); return
        }
        if (!hasInternet()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED); finish(); return
        }

        if (isCustomTabsSupported(this)) {
            usingCustomTabs = true
            startCustomTabFlow(url, durationSeconds)
        } else {
            usingCustomTabs = false
            launchWebView(url, durationSeconds)
        }
    }

    override fun onResume() {
        super.onResume()
        if (usingCustomTabs && customTabLaunched && customTabPaused && !validated) {
            handler.removeCallbacks(customTabTickRunnable)
            setResult(RESULT_CANCELED)
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (usingCustomTabs && customTabLaunched) {
            customTabPaused = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(customTabTickRunnable)
        countDownTimer?.cancel()
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    override fun onBackPressed() {
        if (!usingCustomTabs && webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            handler.removeCallbacks(customTabTickRunnable)
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    private fun startCustomTabFlow(url: String, durationSeconds: Long) {
        customTabDurationSeconds = durationSeconds
        customTabSecondsElapsed  = 0L

        setContentView(View(this))

        launchCustomTab(url)
        customTabLaunched = true

        Toast.makeText(this, "Ad started. Please wait ${durationSeconds}s…", Toast.LENGTH_LONG).show()
        handler.postDelayed(customTabTickRunnable, 1000L)
    }

    private fun launchCustomTab(url: String) {
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.parseColor("#CC000000"))
            .build()
        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorSchemeParams)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
        val pkg = getCustomTabsPackage(this)
        if (pkg != null) customTabsIntent.intent.setPackage(pkg)
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun onCustomTabTimerFinished() {
        if (validated) return
        validated = true
        Toast.makeText(this, "Thank you for your support!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_VALIDATED)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun launchWebView(url: String, durationSeconds: Long) {
        val dp   = resources.displayMetrics.density
        val root = FrameLayout(this)

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (4 * dp).toInt()
            ).also { it.topMargin = (40 * dp).toInt() }
        }

        timerLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#AA000000"))
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            text = "Waiting for page..."
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.TOP or Gravity.END
                it.topMargin = (44 * dp).toInt()
                it.marginEnd = (8 * dp).toInt()
            }
        }

        val topBar = View(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (40 * dp).toInt()
            )
        }

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(
                (40 * dp).toInt(), (40 * dp).toInt()
            ).also { it.gravity = Gravity.TOP or Gravity.END }
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
            post { requestFocus() }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cookieManager.setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                databaseEnabled                  = true
                loadWithOverviewMode             = true
                useWideViewPort                  = true
                setSupportMultipleWindows(true)
                setSupportZoom(true)
                builtInZoomControls              = true
                displayZoomControls              = false
                allowFileAccess                  = true
                allowContentAccess               = true
                loadsImagesAutomatically         = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setGeolocationEnabled(true)
                userAgentString = buildUserAgent()
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString()); return true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (!pageLoaded && url != "about:blank") {
                        pageLoaded = true
                        if (!timerStarted) {
                            timerStarted = true
                            Toast.makeText(this@WebActivity,
                                "Ad started. Please wait ${durationSeconds}s…",
                                Toast.LENGTH_LONG).show()
                            startWebViewTimer(durationSeconds)
                        }
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !pageLoaded)
                        Toast.makeText(this@WebActivity, "Failed to load page", Toast.LENGTH_SHORT).show()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    callback.invoke(origin, true, false)
                }
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
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
        root.addView(topBar)
        root.addView(progressBar)
        root.addView(timerLabel)
        root.addView(closeBtn)
        setContentView(root)
    }

    private fun startWebViewTimer(durationSeconds: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                timerLabel.text = "Closing in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                timerLabel.text = "Done!"
                Toast.makeText(this@WebActivity, "Thank you for your support!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_VALIDATED)
                finish()
            }
        }.start()
    }

    private fun buildUserAgent(): String {
        val androidVersion = android.os.Build.VERSION.RELEASE
        val model          = android.os.Build.MODEL
        val uiModeManager  = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return when (uiModeManager.currentModeType) {
            Configuration.UI_MODE_TYPE_TELEVISION ->
                "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/4.0 Chrome/124.0.0.0 TV Safari/537.36"
            Configuration.UI_MODE_TYPE_DESK ->
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            else -> {
                if (resources.configuration.smallestScreenWidthDp >= 600)
                    "Mozilla/5.0 (Linux; Android $androidVersion; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                else
                    "Mozilla/5.0 (Linux; Android $androidVersion; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
        }
    }

    private fun isVpnOrProxyActive(): Boolean {
        val cm   = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTv = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                || packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        if (isTv) return false
        if (!Proxy.getDefaultHost().isNullOrEmpty()) return true
        if (!System.getProperty("http.proxyHost").isNullOrEmpty()) return true
        if (!System.getProperty("https.proxyHost").isNullOrEmpty()) return true
        return false
    }

    private fun hasInternet(): Boolean {
        val cm   = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
