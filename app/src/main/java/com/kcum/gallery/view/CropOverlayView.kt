package com.kcum.gallery.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lapisan atas (overlay) untuk mod CROP dalam Editor.
 * Pengguna boleh seret penjuru/pinggir/whole rect untuk pilih kawasan potong.
 * Rect dipegang dalam koordinat view; EditorActivity menukarkannya ke
 * koordinat bitmap apabila "Guna" ditekan.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rect = RectF()
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var aspectRatio = 0f // 0 = bebas

    // Zon sentuh
    private var dragMode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_MOVE = 1
        private const val MODE_TOP_LEFT = 2
        private const val MODE_TOP_RIGHT = 3
        private const val MODE_BOTTOM_LEFT = 4
        private const val MODE_BOTTOM_RIGHT = 5
        private const val MODE_LEFT = 6
        private const val MODE_TOP = 7
        private const val MODE_RIGHT = 8
        private const val MODE_BOTTOM = 9
        private const val TOUCH_TOLERANCE = 48f
        private const val CORNER_RADIUS = 12f
    }

    /** Set nisbah aspek (width/height). 0 = bebas */
    fun setAspectRatio(ratio: Float) {
        aspectRatio = ratio
        if (ratio > 0) {
            // Laraskan rect supaya kekal di dalam bounds dengan nisbah baru
            val cx = rect.centerX()
            val cy = rect.centerY()
            var w = rect.width()
            var h = w / ratio
            if (h > height) {
                h = height.toFloat()
                w = h * ratio
            }
            rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            clampRect()
        }
        invalidate()
    }

    /** Set rect penuh (semasa mod crop diaktifkan) */
    fun resetToFull() {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        if (aspectRatio > 0) setAspectRatio(aspectRatio)
        invalidate()
    }

    /** Kembalikan rect semasa (koordinat view) */
    fun getCropRect(): RectF = RectF(rect)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (rect.isEmpty) resetToFull()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rect.isEmpty) return

        // Gelapkan kawasan luar rect (even-odd fill)
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(rect, Path.Direction.CW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(path, dimPaint)

        // Bingkai + penjuru
        canvas.drawRect(rect, borderPaint)
        canvas.drawCircle(rect.left, rect.top, CORNER_RADIUS, cornerPaint)
        canvas.drawCircle(rect.right, rect.top, CORNER_RADIUS, cornerPaint)
        canvas.drawCircle(rect.left, rect.bottom, CORNER_RADIUS, cornerPaint)
        canvas.drawCircle(rect.right, rect.bottom, CORNER_RADIUS, cornerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = detectZone(event.x, event.y)
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return dragMode != MODE_NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                applyDrag(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragMode = MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectZone(x: Float, y: Float): Int {
        val tol = TOUCH_TOLERANCE
        val nearLeft = abs(x - rect.left) <= tol
        val nearRight = abs(x - rect.right) <= tol
        val nearTop = abs(y - rect.top) <= tol
        val nearBottom = abs(y - rect.bottom) <= tol
        return when {
            nearLeft && nearTop -> MODE_TOP_LEFT
            nearRight && nearTop -> MODE_TOP_RIGHT
            nearLeft && nearBottom -> MODE_BOTTOM_LEFT
            nearRight && nearBottom -> MODE_BOTTOM_RIGHT
            nearLeft && y in rect.top..rect.bottom -> MODE_LEFT
            nearRight && y in rect.top..rect.bottom -> MODE_RIGHT
            nearTop && x in rect.left..rect.right -> MODE_TOP
            nearBottom && x in rect.left..rect.right -> MODE_BOTTOM
            rect.contains(x, y) -> MODE_MOVE
            else -> MODE_NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val boundsLeft = 0f
        val boundsTop = 0f
        val boundsRight = width.toFloat()
        val boundsBottom = height.toFloat()
        when (dragMode) {
            MODE_MOVE -> {
                val w = rect.width()
                val h = rect.height()
                var newLeft = (rect.left + dx).coerceIn(boundsLeft, boundsRight - w)
                var newTop = (rect.top + dy).coerceIn(boundsTop, boundsBottom - h)
                rect.set(newLeft, newTop, newLeft + w, newTop + h)
            }
            MODE_TOP_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(boundsLeft, rect.right - minSize())
                rect.top = (rect.top + dy).coerceIn(boundsTop, rect.bottom - minSize())
                enforceAspect(TOP_LEFT)
            }
            MODE_TOP_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize(), boundsRight)
                rect.top = (rect.top + dy).coerceIn(boundsTop, rect.bottom - minSize())
                enforceAspect(TOP_LEFT)
            }
            MODE_BOTTOM_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(boundsLeft, rect.right - minSize())
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize(), boundsBottom)
                enforceAspect(TOP_LEFT)
            }
            MODE_BOTTOM_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize(), boundsRight)
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize(), boundsBottom)
                enforceAspect(TOP_LEFT)
            }
            MODE_LEFT -> rect.left = (rect.left + dx).coerceIn(boundsLeft, rect.right - minSize())
            MODE_TOP -> rect.top = (rect.top + dy).coerceIn(boundsTop, rect.bottom - minSize())
            MODE_RIGHT -> rect.right = (rect.right + dx).coerceIn(rect.left + minSize(), boundsRight)
            MODE_BOTTOM -> rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize(), boundsBottom)
        }
        clampRect()
    }

    private fun minSize(): Float = 80f

    private fun enforceAspect(anchor: Int) {
        if (aspectRatio <= 0) return
        val w = rect.width()
        val h = rect.height()
        // Laraskan tinggi mengikut lebar supaya nisbah dikekalkan
        val newH = w / aspectRatio
        if (anchor == TOP_LEFT) {
            val bottomLimit = height.toFloat()
            rect.bottom = min(rect.top + newH, bottomLimit)
            if (rect.bottom - rect.top < minSize()) rect.bottom = rect.top + minSize()
        }
    }

    private fun clampRect() {
        val l = max(0f, rect.left)
        val t = max(0f, rect.top)
        val r = min(width.toFloat(), rect.right)
        val b = min(height.toFloat(), rect.bottom)
        if (r - l > minSize() && b - t > minSize()) rect.set(l, t, r, b)
    }
}
