package com.livetvpro.app.ui.dialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
        var dialog: AlertDialog? = null

        val blurLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (16 * dp)
                setColor(Color.parseColor("#CC1A1A1A"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(
                    RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                )
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        content.addView(object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), 0f,
                        intArrayOf(
                            Color.argb(0, 239, 68, 68),
                            Color.argb(60, 239, 68, 68),
                            Color.argb(40, 239, 68, 68),
                            Color.argb(0, 239, 68, 68)
                        ),
                        floatArrayOf(0f, 0.3f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }.apply {
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (2 * dp).toInt()
            )
        })

        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (18 * dp).toInt(), (22 * dp).toInt(), (14 * dp).toInt())

            addView(TextView(context).apply {
                text = "We Need Your Support"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })

            addView(View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#EF4444"), Color.argb(0, 239, 68, 68))
                ).apply { cornerRadius = (2 * dp) }
                layoutParams = LinearLayout.LayoutParams(
                    (48 * dp).toInt(), (2 * dp).toInt()
                ).also { it.topMargin = (8 * dp).toInt() }
            })
        })

        content.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            )
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
                setTextColor(Color.parseColor("#CCCCCC"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (i < 3) it.bottomMargin = (8 * dp).toInt() }
            })
        }
        content.addView(body)

        content.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            )
        })

        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())

            addView(MaterialButton(
                context, null, android.R.attr.borderlessButtonStyle
            ).apply {
                text = "Cancel"
                textSize = 13f
                setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()
                ).also { it.marginEnd = (4 * dp).toInt() }
                setOnClickListener {
                    dialog?.dismiss()
                    onCancel()
                }
            })

            addView(MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                text = "Click Here"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#EF4444")
                )
                cornerRadius = (50 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()
                )
                setOnClickListener {
                    dialog?.dismiss()
                    onClickHere()
                }
            })
        })

        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (16 * dp)
                setStroke((1 * dp).toInt(), Color.parseColor("#EF4444"))
            }
            clipToOutline = true
            addView(blurLayer)
            addView(content)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setView(container)
            .setCancelable(true)
            .setOnCancelListener { onCancel() }
            .create()

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setDimAmount(0.6f)
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }
}
