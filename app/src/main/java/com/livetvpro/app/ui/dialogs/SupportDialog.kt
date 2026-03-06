package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class SupportDialog : DialogFragment() {

    var url: String = ""
    var durationSeconds: Long = 10L
    var onClickHere: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var clickHereInvoked = false
    private var cancelInvoked = false

    companion object {
        const val TAG = "SupportDialog"
        fun newInstance() = SupportDialog()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = requireContext().resources.displayMetrics.density
        val isTv = isTv()
        val isLarge = isLargeScreen()

        val basePadding = if (isTv || isLarge) (24 * dp).toInt() else (16 * dp).toInt()
        val titleSize = if (isTv) 22f else if (isLarge) 20f else 18f
        val bodySize = if (isTv) 16f else if (isLarge) 15f else 14f
        val bottomMarginItem = if (isTv || isLarge) (10 * dp).toInt() else (6 * dp).toInt()
        val titleBottomMargin = if (isTv || isLarge) (16 * dp).toInt() else (12 * dp).toInt()
        val buttonTopMargin = if (isTv || isLarge) (24 * dp).toInt() else (16 * dp).toInt()
        val dividerBottom = if (isTv || isLarge) (20 * dp).toInt() else (16 * dp).toInt()

        val root = FrameLayout(requireContext())

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(basePadding, basePadding, basePadding, basePadding)
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
                textSize = titleSize
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = titleBottomMargin }
            })

            addView(View(requireContext()).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#00BCD4"))
                layoutParams = LinearLayout.LayoutParams(
                    (60 * dp).toInt(), (2 * dp).toInt()
                ).also {
                    it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = dividerBottom
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
                    textSize = bodySize
                    setTextColor(android.graphics.Color.LTGRAY)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = bottomMarginItem }
                })
            }

            val buttonRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = buttonTopMargin }
            }

            buttonRow.addView(Button(requireContext()).apply {
                text = "Cancel"
                textSize = bodySize
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(android.graphics.Color.LTGRAY)
                isFocusable = true
                isFocusableInTouchMode = !isTv
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    cancelInvoked = true
                    onCancel?.invoke()
                    dismiss()
                }
            })

            buttonRow.addView(Button(requireContext()).apply {
                text = "Click Here"
                textSize = bodySize
                isFocusable = true
                isFocusableInTouchMode = !isTv
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    clickHereInvoked = true
                    onClickHere?.invoke()
                    dismiss()
                }
            })

            addView(buttonRow)
        }

        root.addView(card)
        return root
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (!clickHereInvoked && !cancelInvoked) {
            cancelInvoked = true
            onCancel?.invoke()
        }
    }

    override fun onStart() {
        super.onStart()
        val isTv = isTv()
        val isLarge = isLargeScreen()
        val widthFraction = when {
            isTv -> 0.55
            isLarge -> 0.65
            else -> 0.92
        }
        dialog?.window?.apply {
            setLayout(
                (requireContext().resources.displayMetrics.widthPixels * widthFraction).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun isTv(): Boolean {
        val uiModeManager = requireContext().getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun isLargeScreen(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= 600
    }
}
