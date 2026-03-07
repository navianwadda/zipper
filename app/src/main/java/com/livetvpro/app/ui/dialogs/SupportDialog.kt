package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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

        val glassCard = object : LinearLayout(context) {
            private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (1f * dp)
            }
            private val rect = RectF()

            override fun onDraw(canvas: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())

                basePaint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.argb(80, 255, 255, 255),
                        Color.argb(40, 220, 220, 235),
                        Color.argb(55, 200, 210, 255),
                        Color.argb(45, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, radius, radius, basePaint)

                innerGlowPaint.shader = RadialGradient(
                    width * 0.25f, height * 0.1f,
                    width * 0.75f,
                    intArrayOf(
                        Color.argb(60, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, radius, radius, innerGlowPaint)

                val specRect = RectF(0f, 0f, width.toFloat(), height * 0.38f)
                specularPaint.shader = LinearGradient(
                    0f, 0f, 0f, height * 0.38f,
                    intArrayOf(
                        Color.argb(120, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(specRect, radius, radius, specularPaint)

                strokePaint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.argb(160, 255, 255, 255),
                        Color.argb(80, 255, 255, 255),
                        Color.argb(60, 239, 68, 68),
                        Color.argb(100, 239, 68, 68)
                    ),
                    floatArrayOf(0f, 0.4f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, radius, radius, strokePaint)

                super.onDraw(canvas)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setWillNotDraw(false)
            clipToOutline = true
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }



        glassCard.addView(object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), 0f,
                        intArrayOf(
                            Color.argb(0, 255, 255, 255),
                            Color.argb(180, 255, 255, 255),
                            Color.argb(255, 255, 255, 255),
                            Color.argb(180, 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                        ),
                        floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }.apply {
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1.5f * dp).toInt()
            )
        })

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
                isFocusableInTouchMode = true
                layoutParams = LinearLayout.LayoutParams(
                    0, (52 * dp).toInt(), 1f
                ).also { it.marginEnd = (6 * dp).toInt() }
                setOnClickListener {
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
                isFocusableInTouchMode = true
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

            clickHereBtn.post { clickHereBtn.requestFocus() }
        })

        dialog = MaterialAlertDialogBuilder(context)
            .setView(glassCard)
            .setCancelable(true)
            .setOnCancelListener { onCancel() }
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
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
            val displayWidth = context.resources.displayMetrics.widthPixels
            val horizontalMargin = (24 * dp).toInt()
            setLayout(displayWidth - horizontalMargin * 2, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
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
