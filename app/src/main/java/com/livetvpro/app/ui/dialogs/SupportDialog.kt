package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            elevation = (24 * dp)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val wrapper = android.widget.FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (20 * dp)
                setColor(Color.argb(60, 20, 20, 20))
                setStroke((1 * dp).toInt(), Color.argb(80, 239, 68, 68))
            }
            clipToOutline = true
            addView(card)
        }

        card.addView(object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), 0f,
                        intArrayOf(
                            Color.argb(0, 255, 255, 255),
                            Color.argb(60, 255, 255, 255),
                            Color.argb(100, 255, 255, 255),
                            Color.argb(60, 255, 255, 255),
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

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt(), (16 * dp).toInt())

            addView(TextView(context).apply {
                text = "We Need Your Support"
                textSize = 17f
                typeface = bergenSans
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#EF4444"), Color.argb(0, 239, 68, 68))
                ).apply { cornerRadius = (2 * dp) }
                layoutParams = LinearLayout.LayoutParams(
                    (56 * dp).toInt(), (2.5f * dp).toInt()
                ).also {
                    it.topMargin = (8 * dp).toInt()
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
            })
        })

        card.addView(View(context).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
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
                setShadowLayer(4f, 0f, 1f, Color.argb(80, 0, 0, 0))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (i < 3) it.bottomMargin = (8 * dp).toInt() }
            })
        }
        card.addView(body)

        card.addView(View(context).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also {
                it.marginStart = (16 * dp).toInt()
                it.marginEnd = (16 * dp).toInt()
            }
        })

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())

            addView(MaterialButton(
                context, null, android.R.attr.borderlessButtonStyle
            ).apply {
                text = "Cancel"
                textSize = 15f
                typeface = bergenSans
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0, (52 * dp).toInt(), 1f
                ).also { it.marginEnd = (6 * dp).toInt() }
                setOnClickListener {
                    dialog?.dismiss()
                    onCancel()
                }
            })

            addView(MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                text = "Click Here"
                textSize = 15f
                typeface = bergenSans
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.argb(200, 239, 68, 68)
                )
                cornerRadius = (50 * dp).toInt()
                elevation = (4 * dp)
                layoutParams = LinearLayout.LayoutParams(
                    0, (52 * dp).toInt(), 1f
                )
                setOnClickListener {
                    dialog?.setOnCancelListener(null)
                    dialog?.dismiss()
                    onClickHere()
                }
            })
        })

        dialog = MaterialAlertDialogBuilder(context)
            .setView(wrapper)
            .setCancelable(false)
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
            setDimAmount(0.6f)
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
        }
    }
}
