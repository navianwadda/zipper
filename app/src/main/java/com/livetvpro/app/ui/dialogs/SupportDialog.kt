package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
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
        val radius = 24 * dp

        val glassCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setWillNotDraw(false)
            clipToOutline = true
            background = object : android.graphics.drawable.Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCC0D0D0D.toInt()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                }
                private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = 0x33FFFFFF
                    strokeWidth = 2f
                }
                private val rectF = RectF()
                override fun draw(canvas: Canvas) {
                    rectF.set(bounds)
                    canvas.drawRoundRect(rectF, radius, radius, paint)
                    canvas.drawRoundRect(rectF, radius, radius, borderPaint)
                }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }



        glassCard.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt(), (16 * dp).toInt())

            addView(TextView(context).apply {
                text = "We Need Your Support"
                textSize = 17f
                typeface = bergenSans
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.argb(0, 239, 68, 68), Color.parseColor("#EF4444"), Color.argb(0, 239, 68, 68))
                ).apply { cornerRadius = (2 * dp) }
                layoutParams = LinearLayout.LayoutParams(
                    (80 * dp).toInt(), (2f * dp).toInt()
                ).also {
                    it.topMargin = (8 * dp).toInt()
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
            })
        })

        glassCard.addView(View(context).apply {
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also {
                it.marginStart = (16 * dp).toInt()
                it.marginEnd = (16 * dp).toInt()
            }
        })

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (14 * dp).toInt(), (22 * dp).toInt(), (14 * dp).toInt())
        }
        listOf(
            "1. Click the button below",
            "2. Wait for the page to load",
            "3. Check out the ads page for $durationSeconds seconds",
            "4. After $durationSeconds seconds ads will be closed automatically"
        ).forEachIndexed { i, step ->
            body.addView(TextView(context).apply {
                text = step
                textSize = 13.5f
                typeface = bergenSans
                setTextColor(Color.WHITE)
                setShadowLayer(4f, 0f, 1f, Color.argb(100, 0, 0, 0))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (i < 3) it.bottomMargin = (8 * dp).toInt() }
            })
        }
        glassCard.addView(body)

        glassCard.addView(View(context).apply {
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also {
                it.marginStart = (16 * dp).toInt()
                it.marginEnd = (16 * dp).toInt()
            }
        })

        glassCard.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())

            val cancelBtn = MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                text = "Cancel"
                textSize = 15f
                typeface = bergenSans
                setTextColor(Color.argb(220, 255, 255, 255))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.argb(50, 255, 255, 255)
                )
                strokeColor = android.content.res.ColorStateList.valueOf(Color.argb(80, 255, 255, 255))
                strokeWidth = (1 * dp).toInt()
                cornerRadius = (50 * dp).toInt()
                insetTop = 0
                insetBottom = 0
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(
                    0, (52 * dp).toInt(), 1f
                ).also { it.marginEnd = (6 * dp).toInt() }
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
                textSize = 15f
                typeface = bergenSans
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.argb(210, 239, 68, 68)
                )
                cornerRadius = (50 * dp).toInt()
                insetTop = 0
                insetBottom = 0
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                isFocusable = true
                isFocusableInTouchMode = false
                elevation = (6 * dp)
                layoutParams = LinearLayout.LayoutParams(
                    0, (52 * dp).toInt(), 1f
                )
                setOnClickListener {
                    dialog?.setOnCancelListener(null)
                    dialog?.dismiss()
                    onClickHere()
                }
            }

            cancelBtn.id = android.view.View.generateViewId()
            clickHereBtn.id = android.view.View.generateViewId()

            addView(cancelBtn)
            addView(clickHereBtn)

            cancelBtn.nextFocusRightId = clickHereBtn.id
            clickHereBtn.nextFocusLeftId = cancelBtn.id

            cancelBtn.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    clickHereBtn.requestFocus()
                    true
                } else false
            }

            clickHereBtn.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    cancelBtn.requestFocus()
                    true
                } else false
            }

            post { cancelBtn.requestFocus() }
        })

        dialog = MaterialAlertDialogBuilder(context)
            .setView(glassCard)
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

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setDimAmount(0.5f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.also { it.blurBehindRadius = 60 }
            }
        }

        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val screenWidthPx = context.resources.displayMetrics.widthPixels
            val dialogWidth = (screenWidthPx * 0.88f).toInt()
                .coerceIn((280 * dp).toInt(), (480 * dp).toInt())
            setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            (decorView as? android.view.ViewGroup)?.let { dv ->
                for (i in 0 until dv.childCount) {
                    val child = dv.getChildAt(i)
                    child.background = null
                    child.elevation = 0f
                    if (child is android.view.ViewGroup) {
                        for (j in 0 until child.childCount) {
                            child.getChildAt(j).background = null
                            child.getChildAt(j).elevation = 0f
                        }
                    }
                }
            }
        }
    }
}
