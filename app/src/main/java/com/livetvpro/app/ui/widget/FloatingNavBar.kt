package com.livetvpro.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.livetvpro.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Tab(
        val id: Int,
        val iconResSelected: Int,
        val iconResUnselected: Int,
        val label: String,
        var badgeCount: Int = 0,
        var badgeText: String? = null
    )

    interface OnTabSelectedListener {
        fun onTabSelected(tab: Tab)
    }

    private val tabs = mutableListOf<Tab>()
    private var selectedIndex = 0
    private var selectionAnimatedIndex = 0f
    private var selectionAnimator: ValueAnimator? = null
    private var onTabSelectedListener: OnTabSelectedListener? = null

    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float) = (value * density)
    private fun dp(value: Int) = (value * density)

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1C1C1E")
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.67f)
        color = Color.parseColor("#33FFFFFF")
    }
    private val selectedIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF")
    }
    private val selectedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val unselectedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.parseColor("#80FFFFFF"), PorterDuff.Mode.SRC_IN)
        alpha = 153
    }
    private val bergenSans: Typeface? = ResourcesCompat.getFont(context, R.font.bergen_sans)

    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        bergenSans?.let { typeface = it }
    }
    private val unselectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textAlign = Paint.Align.CENTER
        bergenSans?.let { typeface = it }
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B30")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pillRect = RectF()
    private val indicatorRect = RectF()
    private val iconRect = Rect()
    private val badgeRect = RectF()

    private val cachedIcons = mutableMapOf<Int, Drawable>()

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchIndex = -1

    private val tabScales = mutableListOf<Float>()
    private val tabScaleAnimators = mutableListOf<ValueAnimator?>()

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setTabs(vararg tab: Tab) {
        tabs.clear()
        tabs.addAll(tab.toList())
        tabScales.clear()
        tabScaleAnimators.clear()
        repeat(tabs.size) {
            tabScales.add(1f)
            tabScaleAnimators.add(null)
        }
        selectionAnimatedIndex = selectedIndex.toFloat()
        invalidate()
    }

    fun setSelectedIndex(index: Int, animated: Boolean = true) {
        if (index == selectedIndex) return
        val from = selectionAnimatedIndex
        val to = index.toFloat()
        selectedIndex = index

        if (!animated) {
            selectionAnimatedIndex = to
            invalidate()
            return
        }

        selectionAnimator?.cancel()
        selectionAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                selectionAnimatedIndex = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        animateTabScale(index)
    }

    fun getSelectedIndex() = selectedIndex

    fun setOnTabSelectedListener(listener: OnTabSelectedListener) {
        onTabSelectedListener = listener
    }

    fun setBadge(tabIndex: Int, count: Int) {
        if (tabIndex in tabs.indices) {
            tabs[tabIndex] = tabs[tabIndex].copy(badgeCount = count, badgeText = if (count > 0) count.toString() else null)
            invalidate()
        }
    }

    fun setBadgeText(tabIndex: Int, text: String?) {
        if (tabIndex in tabs.indices) {
            tabs[tabIndex] = tabs[tabIndex].copy(badgeText = text, badgeCount = if (text != null) 1 else 0)
            invalidate()
        }
    }

    private fun animateTabScale(index: Int) {
        tabScaleAnimators[index]?.cancel()
        tabScaleAnimators[index] = ValueAnimator.ofFloat(1f, 0.88f, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                tabScales[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (tabs.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = h / 2f

        pillRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillStrokePaint)

        val tabW = w / tabs.size
        val indicatorPad = dp(6f)
        val indicatorX = selectionAnimatedIndex * tabW + indicatorPad
        val indicatorRight = indicatorX + tabW - indicatorPad * 2f
        indicatorRect.set(indicatorX, indicatorPad, indicatorRight, h - indicatorPad)
        val indicatorRadius = (h - indicatorPad * 2f) / 2f
        canvas.drawRoundRect(indicatorRect, indicatorRadius, indicatorRadius, selectedIndicatorPaint)

        val iconSize = dp(22f).roundToInt()
        val labelSize = dp(9.5f)
        val iconTopOffset = dp(8f)

        selectedLabelPaint.textSize = labelSize
        unselectedLabelPaint.textSize = labelSize

        tabs.forEachIndexed { index, tab ->
            val tabCenterX = tabW * index + tabW / 2f
            val tabCenterY = h / 2f
            val isSelected = index == selectedIndex
            val selFrac = (1f - abs(selectionAnimatedIndex - index).coerceAtMost(1f))
            val scale = tabScales[index]

            canvas.save()
            canvas.scale(scale, scale, tabCenterX, tabCenterY)

            val iconRes = if (isSelected) tab.iconResSelected else tab.iconResUnselected
            val drawable = cachedIcons.getOrPut(iconRes) {
                ContextCompat.getDrawable(context, iconRes)!!.mutate()
            }

            val iconLeft = (tabCenterX - iconSize / 2f).roundToInt()
            val iconTop = iconTopOffset.roundToInt()
            val iconRight = iconLeft + iconSize
            val iconBottom = iconTop + iconSize

            iconRect.set(iconLeft, iconTop, iconRight, iconBottom)
            drawable.bounds = iconRect

            val iconPaint = if (isSelected) selectedIconPaint else unselectedIconPaint
            drawable.colorFilter = iconPaint.colorFilter
            drawable.draw(canvas)

            val labelY = iconBottom + dp(3f) + labelSize
            val labelPaint = if (selFrac > 0.5f) selectedLabelPaint else unselectedLabelPaint
            labelPaint.alpha = if (selFrac > 0.5f)
                ((selFrac - 0.5f) * 2f * 255).roundToInt().coerceIn(0, 255)
            else
                ((1f - selFrac * 2f) * 255).roundToInt().coerceIn(100, 255)

            canvas.drawText(tab.label, tabCenterX, labelY, labelPaint)

            if (tab.badgeText != null) {
                drawBadge(canvas, tab.badgeText!!, iconRight.toFloat(), iconTop.toFloat())
            }

            canvas.restore()
        }
    }

    private fun drawBadge(canvas: Canvas, text: String, anchorX: Float, anchorY: Float) {
        badgeTextPaint.textSize = dp(8.5f)
        val isNum = text.length <= 2
        val badgeW = if (isNum) dp(16f) else dp(20f)
        val badgeH = dp(16f)
        val badgeX = anchorX - dp(4f)
        val badgeY = anchorY - dp(4f)

        badgeRect.set(badgeX - badgeW / 2f, badgeY - badgeH / 2f, badgeX + badgeW / 2f, badgeY + badgeH / 2f)
        canvas.drawRoundRect(badgeRect, badgeH / 2f, badgeH / 2f, badgePaint)

        val textY = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(text, badgeRect.centerX(), textY, badgeTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchIndex = (event.x / (width.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.lastIndex)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = abs(event.x - touchDownX)
                val dy = abs(event.y - touchDownY)
                if (dx < dp(10f) && dy < dp(10f)) {
                    val tappedIndex = (event.x / (width.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.lastIndex)
                    if (tappedIndex == selectedIndex) {
                        performClick()
                        return true
                    }
                    setSelectedIndex(tappedIndex, animated = true)
                    onTabSelectedListener?.onTabSelected(tabs[tappedIndex])
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
