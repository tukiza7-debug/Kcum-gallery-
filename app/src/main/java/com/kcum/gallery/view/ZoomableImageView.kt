package com.kcum.gallery.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * ImageView dengan zoom cubit (pinch), pan (seret) dan dwi-ketuk (double tap).
 * Digunakan dalam penonton skrin penuh. Zoom >= 1.5x akan meminta parent
 * (ViewPager2) supaya tidak memintas sentuhan, jadi pan berfungsi lancar.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private val baseMatrix = Matrix()
    private var currentScale = 1f

    private val minScale = 1f
    private val maxScale = 5f
    private val midScale = 2.5f

    private var scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                currentScale = (currentScale * factor).coerceIn(minScale, maxScale)
                postTranslateMatrix.apply {
                    set(baseMatrix)
                    postScale(currentScale, currentScale, detector.focusX, detector.focusY)
                }
                imageMatrix = postTranslateMatrix
                fixTranslation()
                return true
            }
        }
    )

    private var lastTouch = PointF()
    private var dragging = false
    private val postTranslateMatrix = Matrix()

    private var tapListener: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > minScale + 0.01f) {
                    resetZoom()
                } else {
                    currentScale = midScale
                    postTranslateMatrix.apply {
                        set(baseMatrix)
                        postScale(currentScale, currentScale, e.x, e.y)
                    }
                    imageMatrix = postTranslateMatrix
                    fixTranslation()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                tapListener?.invoke()
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    /** Set pendengar ketukan tunggal (untuk tunjuk/sorok kawalan penonton) */
    fun setOnTapListener(listener: (() -> Unit)?) {
        tapListener = listener
    }

    /** Setel semula zum ke 1x - dipanggil apabila halaman pager bertukar */
    fun resetZoom() {
        currentScale = 1f
        applyBaseMatrix()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        post { applyBaseMatrix() }
    }

    private fun applyBaseMatrix() {
        val drawable = drawable ?: return
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) return

        baseMatrix.reset()
        val scale = min(vw / dw, vh / dh)
        baseMatrix.postTranslate((vw - dw) / 2f, (vh - dh) / 2f)
        baseMatrix.postScale(scale, scale, vw / 2f, vh / 2f)
        postTranslateMatrix.set(baseMatrix)
        imageMatrix = postTranslateMatrix
        currentScale = 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                dragging = false
                // Beritahu ViewPager2 supaya jangan rampas sentuhan semasa zum
                if (currentScale > 1.01f) {
                    (parent as? androidx.viewpager2.widget.ViewPager2)
                        ?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (currentScale > 1.01f) {
                    (parent as? androidx.viewpager2.widget.ViewPager2)
                        ?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    if (currentScale > 1.01f) {
                        dragging = dragging || (dx * dx + dy * dy > 25)
                        (parent as? androidx.viewpager2.widget.ViewPager2)
                            ?.requestDisallowInterceptTouchEvent(true)
                        postTranslateMatrix.postTranslate(dx, dy)
                        imageMatrix = postTranslateMatrix
                        fixTranslation()
                        lastTouch.set(event.x, event.y)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                (parent as? androidx.viewpager2.widget.ViewPager2)
                    ?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /** Elak imej tersekat di luar skrin semasa pan */
    private fun fixTranslation() {
        val drawable = drawable ?: return
        postTranslateMatrix.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val contentW = drawable.intrinsicWidth * scale
        val contentH = drawable.intrinsicHeight * scale

        var tx = matrixValues[Matrix.MTRANS_X]
        var ty = matrixValues[Matrix.MTRANS_Y]

        if (contentW <= viewW) {
            tx = (viewW - contentW) / 2f
        } else {
            tx = max(min(tx, 0f), viewW - contentW)
        }
        if (contentH <= viewH) {
            ty = (viewH - contentH) / 2f
        } else {
            ty = max(min(ty, 0f), viewH - contentH)
        }
        matrixValues[Matrix.MTRANS_X] = tx
        matrixValues[Matrix.MTRANS_Y] = ty
        postTranslateMatrix.setValues(matrixValues)
        imageMatrix = postTranslateMatrix
    }
}
