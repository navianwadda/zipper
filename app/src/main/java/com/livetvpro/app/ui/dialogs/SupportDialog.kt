package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SupportDialog {

    const val TAG = "SupportDialog"

    fun show(
        context: Context,
        durationSeconds: Long,
        onClickHere: () -> Unit,
        onCancel: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val bergenSans = ResourcesCompat.getFont(context, com.livetvpro.app.R.font.bergen_sans)
        var dialog: AlertDialog? = null

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())

            addView(TextView(context).apply {
                text = "We Need Your Support"
                textSize = 18f
                typeface = Typeface.create(bergenSans, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        })

        root.addView(View(context).apply {
            setBackgroundColor(0x1AFFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also {
                it.marginStart = (16 * dp).toInt()
                it.marginEnd = (16 * dp).toInt()
            }
        })

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())

            listOf(
                "1. Click the button below",
                "2. Wait for the page to load",
                "3. Check out the ads page for $durationSeconds seconds",
                "4. After $durationSeconds seconds ads will be closed automatically"
            ).forEachIndexed { i, step ->
                addView(TextView(context).apply {
                    text = step
                    textSize = 14f
                    typeface = bergenSans
                    setTextColor(0xCCFFFFFF.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { if (i < 3) it.bottomMargin = (8 * dp).toInt() }
                })
            }
        })

        root.addView(View(context).apply {
            setBackgroundColor(0x1AFFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also {
                it.marginStart = (16 * dp).toInt()
                it.marginEnd = (16 * dp).toInt()
            }
        })

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())

            val cancelBtn = MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Cancel"
                textSize = 14f
                typeface = bergenSans
                setTextColor(0xFFFFFFFF.toInt())
                strokeColor = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                strokeWidth = (1 * dp).toInt()
                cornerRadius = 0
                insetTop = 0
                insetBottom = 0
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    dialog?.setOnCancelListener(null)
                    dialog?.dismiss()
                    onCancel()
                }
            }

            val clickHereBtn = MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                text = "Click Here"
                textSize = 14f
                typeface = Typeface.create(bergenSans, Typeface.BOLD)
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFEF4444.toInt())
                cornerRadius = (50 * dp).toInt()
                insetTop = 0
                insetBottom = 0
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    dialog?.setOnCancelListener(null)
                    dialog?.dismiss()
                    onClickHere()
                }
            }

            cancelBtn.id = View.generateViewId()
            clickHereBtn.id = View.generateViewId()

            addView(cancelBtn)
            addView(clickHereBtn)

            cancelBtn.nextFocusRightId = clickHereBtn.id
            clickHereBtn.nextFocusLeftId = cancelBtn.id

            cancelBtn.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    clickHereBtn.requestFocus(); true
                } else false
            }
            clickHereBtn.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    cancelBtn.requestFocus(); true
                } else false
            }

            post { cancelBtn.requestFocus() }
        })

        dialog = MaterialAlertDialogBuilder(context, com.livetvpro.app.R.style.ThemeOverlay_LiveTVPro_Dialog_Transparent)
            .setView(root)
            .setCancelable(true)
            .setOnCancelListener { onCancel() }
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                dialog?.setOnCancelListener(null)
                dialog?.dismiss()
                onCancel()
                true
            } else false
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC0D0D0D.toInt()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
            }
            private val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = 0x33FFFFFF
                strokeWidth = 2f
            }
            private val rectF = android.graphics.RectF()
            private val radius = 28f * context.resources.displayMetrics.density
            override fun draw(canvas: android.graphics.Canvas) {
                rectF.set(bounds)
                canvas.drawRoundRect(rectF, radius, radius, paint)
                canvas.drawRoundRect(rectF, radius, radius, borderPaint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        })
    }
}
