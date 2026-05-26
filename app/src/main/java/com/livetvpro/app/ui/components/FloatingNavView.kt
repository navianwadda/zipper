package com.livetvpro.app.ui.components
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.livetvpro.app.R
class FloatingNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Tab(
        val filledIcon: Int,
        val outlineIcon: Int,
        val label: String
    )
    private val tabs = mutableListOf<Tab>()
    private val iconDrawables = mutableListOf<Drawable?>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A2AABEE")
    }
    private val navBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0111111")
    }
    private val pillRect = RectF()
    private val pillRadius = dp(15f)
    private val iconSize = dp(24f).toInt()
    private var pillLeft = 0f
    private var pillRight = 0f
    private var pillTop = 0f
    private var pillBottom = 0f
    private var initialized = false
    private var selectedIndex = 0
    private var pillAnimator: ValueAnimator? = null
    private val activeColor = Color.parseColor("#2AABEE")
    private val inactiveColor = Color.parseColor("#6B7280")
    private val pillContentColor = Color.parseColor("#EF4444")
    var onTabSelected: ((Int) -> Unit)? = null
    fun setTabs(vararg tabList: Tab) {
        tabs.clear()
        tabs.addAll(tabList)
        iconDrawables.clear()
        tabs.forEach { iconDrawables.add(null) }
        invalidate()
    }
    fun selectTab(index: Int, animate: Boolean) {
        if (tabs.isEmpty() || width == 0) {
            post { selectTab(index, animate) }
            return
        }
        selectedIndex = index
        reloadIcons()
        val tabW = width.toFloat() / tabs.size
        val pillH = dp(34f)
        val pillW = tabW * 0.78f
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
            duration = 300
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val tFast = minOf(1f, t * 1.5f)
                val tSlow = maxOf(0f, t * 1.5f - 0.5f)
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
    private fun reloadIcons() {
        tabs.forEachIndexed { i, tab ->
            val res = if (i == selectedIndex) tab.filledIcon else tab.outlineIcon
            val d = ContextCompat.getDrawable(context, res)?.mutate()
            d?.setBounds(0, 0, iconSize, iconSize)
            d?.setTint(if (i == selectedIndex) activeColor else inactiveColor)
            iconDrawables[i] = d
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reloadIcons()
        if (!initialized && tabs.isNotEmpty()) {
            selectTab(selectedIndex, false)
        }
    }
    override fun onDraw(canvas: Canvas) {
        if (tabs.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val tabW = w / tabs.size
        tabs.forEachIndexed { i, _ ->
            val cx = tabW * i + tabW / 2f
            val icon = iconDrawables.getOrNull(i)
            val iconTop = (h / 2f - dp(14f)).toInt()
            val labelY = h / 2f + dp(14f) + dp(2f)
            textPaint.color = if (i == selectedIndex) activeColor else inactiveColor
            icon?.let {
                canvas.save()
                canvas.translate(cx - iconSize / 2f, iconTop.toFloat())
                it.draw(canvas)
                canvas.restore()
            }
            canvas.drawText(tabs[i].label, cx, labelY, textPaint)
        }
        if (!initialized) return
        pillRect.set(pillLeft, pillTop, pillRight, pillBottom)
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint)
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        tabs.forEachIndexed { i, tab ->
            val cx = tabW * i + tabW / 2f
            val icon = iconDrawables.getOrNull(i)
            val iconTop = (h / 2f - dp(14f)).toInt()
            val labelY = h / 2f + dp(14f) + dp(2f)
            textPaint.color = Color.WHITE
            icon?.let {
                val copy = it.constantState?.newDrawable()?.mutate()
                copy?.setBounds(0, 0, iconSize, iconSize)
                copy?.setTint(Color.WHITE)
                canvas.save()
                canvas.translate(cx - iconSize / 2f, iconTop.toFloat())
                copy?.draw(canvas)
                canvas.restore()
            }
            canvas.drawText(tab.label, cx, labelY, textPaint)
        }
        val invertPath = android.graphics.Path().apply {
            addRect(0f, 0f, w, h, android.graphics.Path.Direction.CW)
            addRoundRect(pillRect, pillRadius, pillRadius, android.graphics.Path.Direction.CCW)
        }
        canvas.drawPath(invertPath, clearPaint)
        canvas.restoreToCount(layer)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && tabs.isNotEmpty()) {
            val index = (event.x / (width.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.size - 1)
            if (index != selectedIndex) {
                onTabSelected?.invoke(index)
            }
        }
        return true
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
