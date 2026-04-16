package com.livetvpro.app.ui.widget

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

class FloatingNavController(
    private val navBar: FloatingNavBar
) {

    private var isVisible = true
    private var animator: ValueAnimator? = null

    private val density = navBar.context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    fun setVisible(visible: Boolean, animated: Boolean = true) {
        if (isVisible == visible) return
        isVisible = visible

        animator?.cancel()

        val hiddenTransY = dp(40f)

        if (!animated) {
            val factor = if (visible) 1f else 0f
            applyFactor(factor, hiddenTransY)
            navBar.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }

        if (visible) {
            navBar.visibility = View.VISIBLE
        }

        val startFactor = navBar.alpha
        val endFactor = if (visible) 1f else 0f

        animator = ValueAnimator.ofFloat(startFactor, endFactor).apply {
            duration = 380
            interpolator = DecelerateInterpolator(2.5f)
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                applyFactor(f, hiddenTransY)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!visible) navBar.visibility = View.GONE
                }
            })
            start()
        }
    }

    private fun applyFactor(factor: Float, hiddenTransY: Float) {
        val scale = 0.85f + 0.15f * factor
        navBar.alpha = factor
        navBar.scaleX = scale
        navBar.scaleY = scale
        navBar.translationY = hiddenTransY * (1f - factor)
    }

    fun isVisible() = isVisible
}
