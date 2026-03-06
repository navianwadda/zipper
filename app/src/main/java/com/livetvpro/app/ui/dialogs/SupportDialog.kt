package com.livetvpro.app.ui.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class SupportDialog : DialogFragment() {

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var countDownTimer: CountDownTimer? = null
    private var timerFinished = false
    private var webViewStarted = false

    var url: String = ""
    var durationSeconds: Long = 10L
    var onTimerCompleted: (() -> Unit)? = null
    var onDismissedEarly: (() -> Unit)? = null

    companion object {
        const val TAG = "SupportDialog"
        fun newInstance() = SupportDialog()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val frame = FrameLayout(requireContext())
        frame.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frame.addView(buildInstructionsView(frame))
        return frame
    }

    private fun buildInstructionsView(parent: FrameLayout): LinearLayout {
        val dp = requireContext().resources.displayMetrics.density
        val px16 = (16 * dp).toInt()
        val px12 = (12 * dp).toInt()
        val px8 = (8 * dp).toInt()
        val px6 = (6 * dp).toInt()
        val px2 = (2 * dp).toInt()

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px16, px16, px16, px16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.CENTER_VERTICAL }

            addView(TextView(requireContext()).apply {
                text = "We Need Your Support"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = px12 }
            })

            addView(View(requireContext()).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#00BCD4"))
                layoutParams = LinearLayout.LayoutParams(
                    (60 * dp).toInt(), px2
                ).also {
                    it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = px16
                }
            })

            listOf(
                "1. Click the button below",
                "2. Wait for the page to load",
                "3. Check out the ads page for $durationSeconds seconds",
                "4. After $durationSeconds seconds ads will be closed automatically"
            ).forEach { line ->
                addView(TextView(requireContext()).apply {
                    text = line
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = px6 }
                })
            }

            val buttonRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = px16 }
            }

            buttonRow.addView(Button(requireContext()).apply {
                text = "Cancel"
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = px8 }
                setOnClickListener { dismiss() }
            })

            buttonRow.addView(Button(requireContext()).apply {
                text = "Click Here"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    parent.removeAllViews()
                    parent.addView(buildWebView())
                    webViewStarted = true
                    startTimer()
                }
            })

            addView(buttonRow)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): FrameLayout {
        val frame = FrameLayout(requireContext())
        frame.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 8)
        }

        webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.topMargin = 8 }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar?.progress = newProgress
                    progressBar?.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }
            loadUrl(url?.ifEmpty { "about:blank" } ?: "about:blank")
        }

        frame.addView(progressBar)
        frame.addView(webView)
        return frame
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        timerFinished = false
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                timerFinished = true
                onTimerCompleted?.invoke()
                dismissAllowingStateLoss()
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        if (webViewStarted && !timerFinished) {
            onDismissedEarly?.invoke()
        }
    }
}
