package com.livetvpro.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

class FloatingNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33EF4444")
    }

    private val pillRect = RectF()
    private val pillRadius = dpf(15f)
    private var pillLeft = 0f
    private var pillTop = 0f
    private var pillRight = 0f
    private var pillBottom = 0f

    private var pillAnimator: ValueAnimator? = null
    private var tabCount = 0
    private var initialized = false

    fun setTabCount(count: Int) {
        tabCount = count
    }

    fun selectTab(index: Int, animate: Boolean) {
        if (tabCount == 0 || width == 0) {
            post { selectTab(index, animate) }
            return
        }

        val tabW = width.toFloat() / tabCount
        val pillH = dpf(34f)
        val pillW = tabW * 0.75f
        val targetLeft = tabW * index + (tabW - pillW) / 2f
        val targetTop = (height - pillH) / 2f
        val targetRight = targetLeft + pillW
        val targetBottom = targetTop + pillH

        if (!initialized) {
            pillLeft = targetLeft
            pillTop = targetTop
            pillRight = targetRight
            pillBottom = targetBottom
            initialized = true
            invalidate()
            return
        }

        if (!animate) {
            pillLeft = targetLeft
            pillTop = targetTop
            pillRight = targetRight
            pillBottom = targetBottom
            invalidate()
            return
        }

        pillAnimator?.cancel()

        val fromLeft = pillLeft
        val fromRight = pillRight
        val fromTop = pillTop
        val fromBottom = pillBottom

        pillAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val tFast = minOf(1f, t * 1.4f)
                val tSlow = maxOf(0f, t * 1.4f - 0.4f)
                if (targetLeft < fromLeft) {
                    pillLeft = fromLeft + (targetLeft - fromLeft) * tFast
                    pillRight = fromRight + (targetRight - fromRight) * tSlow
                } else {
                    pillLeft = fromLeft + (targetLeft - fromLeft) * tSlow
                    pillRight = fromRight + (targetRight - fromRight) * tFast
                }
                pillTop = fromTop + (targetTop - fromTop) * t
                pillBottom = fromBottom + (targetBottom - fromBottom) * t
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (initialized) {
            pillRect.set(pillLeft, pillTop, pillRight, pillBottom)
            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)
        }
        super.dispatchDraw(canvas)
    }

    private fun dpf(value: Float) = value * resources.displayMetrics.density
}
