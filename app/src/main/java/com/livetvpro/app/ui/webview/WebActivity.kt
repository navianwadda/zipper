package com.livetvpro.app.ui.webview

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.livetvpro.app.MainActivity

class WebActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var countDownTimer: CountDownTimer? = null
    private var pageLoaded = false
    private var timerStarted = false
    private var usingCustomTabs = false
    private var validated = false
    private var customTabLaunched = false
    private var customTabLaunchTimeMs = 0L

    private lateinit var timerLabel: TextView
    private var customTabDurationSeconds = 0L
    private var customTabStartTimeMs = 0L
    private val timerDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TimerService.ACTION_TIMER_DONE) onCustomTabTimerFinished()
        }
    }

    companion object {
        private const val EXTRA_URL             = "extra_url"
        private const val EXTRA_DURATION        = "extra_duration"
        private const val EXTRA_HIDE_AD_CONTENT = "extra_hide_ad_content"
        const val RESULT_VALIDATED              = 100

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
        val hideAdContent   = intent.getBooleanExtra(EXTRA_HIDE_AD_CONTENT, true)

        if (isVpnOrProxyActive()) {
            Toast.makeText(this, "Please disable VPN/Proxy to continue", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED); finish(); return
        }
        if (!hasInternet()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED); finish(); return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerDoneReceiver, IntentFilter(TimerService.ACTION_TIMER_DONE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerDoneReceiver, IntentFilter(TimerService.ACTION_TIMER_DONE))
        }

        if (!hideAdContent && isCustomTabsSupported(this)) {
            usingCustomTabs = true
            startCustomTabFlow(url, durationSeconds)
        } else {
            launchWebView(url, durationSeconds)
        }
    }

    override fun onResume() {
        super.onResume()
        if (usingCustomTabs && customTabLaunched && !validated) {
            if (System.currentTimeMillis() - customTabLaunchTimeMs < 2000L) return
            val elapsedSeconds = (System.currentTimeMillis() - customTabStartTimeMs) / 1000L
            if (elapsedSeconds >= customTabDurationSeconds) {
                TimerService.stop(this)
                onCustomTabTimerFinished()
            } else {
                TimerService.stop(this)
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TimerService.stop(this)
        try { unregisterReceiver(timerDoneReceiver) } catch (_: Exception) {}
        countDownTimer?.cancel()
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    override fun onBackPressed() {
        when {
            usingCustomTabs && !validated -> {
                TimerService.stop(this)
                setResult(RESULT_CANCELED)
                finish()
            }
            !usingCustomTabs && webView?.canGoBack() == true -> {
                webView?.goBack()
            }
            else -> {
                TimerService.stop(this)
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
    private fun startCustomTabFlow(url: String, durationSeconds: Long) {
        customTabDurationSeconds = durationSeconds
        customTabStartTimeMs     = System.currentTimeMillis()
        setContentView(View(this))

        customTabLaunched = true
        customTabLaunchTimeMs = System.currentTimeMillis()
        launchCustomTab(url)

        Handler(Looper.getMainLooper()).postDelayed({
            TimerService.start(this, durationSeconds)
            Toast.makeText(this, "Ad started. Please wait ${durationSeconds}s…", Toast.LENGTH_LONG).show()
        }, 3000L)
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
        TimerService.stop(this)
        Toast.makeText(applicationContext, "Timer completed! You can go back now.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
        setResult(RESULT_VALIDATED)
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
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * dp).toInt(), Color.WHITE)
            }
            text = "…"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (32 * dp).toInt(), (32 * dp).toInt()
            ).also { it.marginEnd = (4 * dp).toInt() }
        }

        val topBar = View(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (40 * dp).toInt()
            )
        }

        val ripple = android.util.TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }
        fun headerButton(iconRes: Int, description: String, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(iconRes)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setBackgroundResource(ripple.resourceId)
            val pad = (10 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            setColorFilter(Color.WHITE)
            contentDescription = description
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = android.widget.LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
            setOnClickListener { onClick() }
        }

        val backBtn = headerButton(com.livetvpro.app.R.drawable.ic_arrow_back, "Back") {
            if (webView?.canGoBack() == true) webView?.goBack()
        }
        val reloadBtn = headerButton(com.livetvpro.app.R.drawable.ic_refresh, "Reload") {
            webView?.reload()
        }
        val copyBtn = headerButton(com.livetvpro.app.R.drawable.ic_link, "Copy link") {
            val link = webView?.url ?: url
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", link))
            Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
        }
        val shareBtn = headerButton(com.livetvpro.app.R.drawable.ic_share, "Share") {
            val link = webView?.url ?: url
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }, "Share link"
            ))
        }
        val closeBtn = headerButton(com.livetvpro.app.R.drawable.ic_close, "Close") {
            setResult(RESULT_CANCELED); finish()
        }.apply { post { requestFocus() } }

        val headerRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (40 * dp).toInt()
            ).also { it.gravity = Gravity.TOP }
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            addView(backBtn)
            addView(reloadBtn)
            addView(copyBtn)
            addView(shareBtn)
            addView(View(this@WebActivity).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            })
            addView(timerLabel)
            addView(closeBtn)
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            if (isTvDevice()) {
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webView?.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    40f, 40f, android.graphics.Shader.TileMode.CLAMP
                )
            )
        }

        val blurScrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#D9000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = !isTvDevice()
            isFocusable = !isTvDevice()
        }

        val blurMessage = TextView(this).apply {
            text = "Ad content hidden\nPlease wait for the timer to finish"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
        }

        root.addView(webView)
        root.addView(blurScrim)
        root.addView(blurMessage)
        root.addView(topBar)
        root.addView(progressBar)
        root.addView(headerRow)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            (topBar.layoutParams as FrameLayout.LayoutParams).height = (40 * dp).toInt() + statusBarTop
            topBar.requestLayout()
            (progressBar.layoutParams as FrameLayout.LayoutParams).topMargin = (40 * dp).toInt() + statusBarTop
            progressBar.requestLayout()
            (headerRow.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarTop
            headerRow.requestLayout()
            insets
        }
    }

    private fun startWebViewTimer(durationSeconds: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toString()
                timerLabel.textSize = if (secs.length > 2) 10f else 12f
                timerLabel.text = secs
            }
            override fun onFinish() {
                timerLabel.text = "✓"
                Toast.makeText(this@WebActivity, "Thank you for your support!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_VALIDATED)
                finish()
            }
        }.start()
    }

    private fun buildUserAgent(): String =
        WebSettings.getDefaultUserAgent(this).replace(" wv", "")

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            || packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }

    private fun isVpnOrProxyActive(): Boolean {
        val cm   = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        if (isTvDevice()) return false
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
