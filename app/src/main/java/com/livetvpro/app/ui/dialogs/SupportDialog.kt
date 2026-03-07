package com.livetvpro.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class SupportDialog(
    context: Context,
    private val durationSeconds: Long,
    private val onClickHere: () -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    companion object {
        const val TAG = "SupportDialog"
    }

    private var actionInvoked = false

    init {
        val dp = context.resources.displayMetrics.density
        val isTv = isTv()
        val isLarge = isLargeScreen()

        val basePadding   = if (isTv || isLarge) (24 * dp).toInt() else (16 * dp).toInt()
        val titleSize     = if (isTv) 22f else if (isLarge) 20f else 18f
        val bodySize      = if (isTv) 16f else if (isLarge) 15f else 14f
        val itemMargin    = if (isTv || isLarge) (10 * dp).toInt() else (6 * dp).toInt()
        val titleMargin   = if (isTv || isLarge) (16 * dp).toInt() else (12 * dp).toInt()
        val buttonMargin  = if (isTv || isLarge) (24 * dp).toInt() else (16 * dp).toInt()
        val dividerMargin = if (isTv || isLarge) (20 * dp).toInt() else (16 * dp).toInt()

        window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#99000000")))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        val widthFraction = when {
            isTv -> 0.55
            isLarge -> 0.65
            else -> 0.92
        }
        val cardWidth = (context.resources.displayMetrics.widthPixels * widthFraction).toInt()

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                if (!actionInvoked) {
                    actionInvoked = true
                    onCancel()
                    dismiss()
                }
            }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(basePadding, basePadding, basePadding, basePadding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * dp
                setColor(Color.parseColor("#FF2C2C2C"))
            }
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER
            }
            setOnClickListener {}

            addView(TextView(context).apply {
                text = "We Need Your Support"
                textSize = titleSize
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = titleMargin }
            })

            addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#00BCD4"))
                layoutParams = LinearLayout.LayoutParams(
                    (60 * dp).toInt(), (2 * dp).toInt()
                ).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = dividerMargin
                }
            })

            listOf(
                "1. Click the button below",
                "2. Wait for the page to load",
                "3. Check out the ads page for $durationSeconds seconds",
                "4. After $durationSeconds seconds ads will be closed automatically"
            ).forEach { line ->
                addView(TextView(context).apply {
                    text = line
                    textSize = bodySize
                    setTextColor(Color.LTGRAY)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = itemMargin }
                })
            }

            val buttonRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = buttonMargin }
            }

            buttonRow.addView(Button(context).apply {
                text = "Cancel"
                textSize = bodySize
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.LTGRAY)
                isFocusable = true
                isFocusableInTouchMode = !isTv
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    if (actionInvoked) return@setOnClickListener
                    actionInvoked = true
                    onCancel()
                    dismiss()
                }
            })

            buttonRow.addView(Button(context).apply {
                text = "Click Here"
                textSize = bodySize
                isFocusable = true
                isFocusableInTouchMode = !isTv
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    if (actionInvoked) return@setOnClickListener
                    actionInvoked = true
                    onClickHere()
                    dismiss()
                }
            })

            addView(buttonRow)
        }

        root.addView(card)
        setContentView(root)

        setOnCancelListener {
            if (!actionInvoked) {
                actionInvoked = true
                onCancel()
            }
        }
    }

    private fun isTv(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun isLargeScreen(): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }
}
