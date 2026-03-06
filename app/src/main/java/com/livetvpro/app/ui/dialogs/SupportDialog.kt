package com.livetvpro.app.ui.dialogs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.livetvpro.app.ui.webview.WebViewActivity

class SupportDialog : DialogFragment() {

    var url: String = ""
    var durationSeconds: Long = 10L
    var onTimerCompleted: (() -> Unit)? = null
    var onDismissedEarly: (() -> Unit)? = null

    companion object {
        const val TAG = "SupportDialog"
        fun newInstance() = SupportDialog()
    }

    private val webViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == WebViewActivity.RESULT_VALIDATED) {
            onTimerCompleted?.invoke()
        }
        // RESULT_CANCELED (X pressed, VPN detected, no internet) — not validated, do nothing
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = requireContext().resources.displayMetrics.density
        val px16 = (16 * dp).toInt()
        val px12 = (12 * dp).toInt()
        val px8 = (8 * dp).toInt()
        val px6 = (6 * dp).toInt()
        val px2 = (2 * dp).toInt()

        val root = FrameLayout(requireContext())

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px16, px16, px16, px16)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (12 * dp)
                setColor(android.graphics.Color.parseColor("#FF2C2C2C"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.CENTER_VERTICAL }

            addView(TextView(requireContext()).apply {
                text = "We Need Your Support"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
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
                    setTextColor(android.graphics.Color.LTGRAY)
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
                setTextColor(android.graphics.Color.LTGRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = px8 }
                setOnClickListener {
                    onDismissedEarly?.invoke()
                    dismiss()
                }
            })

            buttonRow.addView(Button(requireContext()).apply {
                text = "Click Here"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    val intent = Intent(requireContext(), WebViewActivity::class.java).apply {
                        putExtra("extra_url", url)
                        putExtra("extra_duration", durationSeconds)
                    }
                    webViewLauncher.launch(intent)
                    dismiss()
                }
            })

            addView(buttonRow)
        }

        root.addView(card)
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (requireContext().resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}
