package com.kcum.gallery.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Lapisan lukisan/antotasi untuk Editor.
 * Menyokong:
 * - Mod LUKIS : sentuh untuk melukis laluan (path) dengan warna & ketebalan dipilih
 * - Mod TEKS  : seret teks overlay ke posisi yang diingini
 *
 * Semua koordinat disimpan NORMALIZED (0..1) supaya lukisan kekal tepat apabila
 * dimuktamadkan pada bitmap resolusi penuh yang berbeza saiz daripada skrin.
 */
class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Stroke(val points: MutableList<PointF>, val color: Int, val widthFraction: Float)
    data class OverlayText(
        val text: String,
        var x: Float,   // normalized 0..1 (pusat)
        var y: Float,   // normalized 0..1 (pusat)
        val color: Int,
        val sizeFraction: Float // berkaitan dengan tinggi view
    )

    var mode = MODE_DRAW
    var currentColor = 0xFFF44336.toInt()
    var currentWidthFraction = 0.006f

    private val strokes = ArrayList<Stroke>()
    private val texts = ArrayList<OverlayText>()
    private var currentStroke: Stroke? = null
    private var draggedText: OverlayText? = null

    companion object {
        const val MODE_DRAW = 0
        const val MODE_TEXT = 1
    }

    /** Tambah teks baru di tengah kanvas */
    fun addText(text: String, color: Int) {
        texts.add(OverlayText(text, 0.5f, 0.5f, color, 0.055f))
        invalidate()
    }

    /** Buang elemen terakhir (lukisan atau teks) */
    fun undoLast() {
        if (currentStroke == null && texts.isNotEmpty()) {
            texts.removeAt(texts.size - 1)
        } else if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
        }
        invalidate()
    }

    fun hasContent(): Boolean = strokes.isNotEmpty() || texts.isNotEmpty()

    /** Setel semula semua (tanpa simpan) */
    fun clearAll() {
        strokes.clear()
        texts.clear()
        currentStroke = null
        invalidate()
    }

    /**
     * Render kandungan overlay ke kanvas bitmap resolusi penuh (untuk simpan).
     * Koordinat normalized didarab dengan saiz bitmap sasaran.
     */
    fun renderOn(canvas: Canvas, bitmapWidth: Int, bitmapHeight: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.widthFraction * bitmapHeight
            val path = Path()
            stroke.points.forEachIndexed { index, p ->
                val px = p.x * bitmapWidth
                val py = p.y * bitmapHeight
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        for (t in texts) {
            textPaint.color = t.color
            textPaint.textSize = t.sizeFraction * bitmapHeight
            canvas.drawText(t.text, t.x * bitmapWidth, t.y * bitmapHeight, textPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.widthFraction * h
            val path = Path()
            stroke.points.forEachIndexed { index, p ->
                val px = p.x * w
                val py = p.y * h
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint)
        }
        if (currentStroke != null) {
            paint.color = currentStroke!!.color
            paint.strokeWidth = currentStroke!!.widthFraction * h
            val path = Path()
            currentStroke!!.points.forEachIndexed { index, p ->
                val px = p.x * w
                val py = p.y * h
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        for (t in texts) {
            textPaint.color = t.color
            textPaint.textSize = t.sizeFraction * h
            canvas.drawText(t.text, t.x * w, t.y * h, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val xNorm = event.x / width
        val yNorm = event.y / height

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (mode == MODE_DRAW) {
                    currentStroke = Stroke(
                        mutableListOf(PointF(xNorm, yNorm)),
                        currentColor,
                        currentWidthFraction
                    )
                } else {
                    draggedText = nearestText(xNorm, yNorm)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAW) {
                    currentStroke?.points?.add(PointF(xNorm, yNorm))
                } else {
                    draggedText?.let {
                        it.x = xNorm
                        it.y = yNorm
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (mode == MODE_DRAW) {
                    currentStroke?.let { if (it.points.size > 1) strokes.add(it) }
                    currentStroke = null
                }
                draggedText = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Cari teks paling hampir dengan sentuhan (jarak < 0.1) untuk diseret */
    private fun nearestText(x: Float, y: Float): OverlayText? {
        var best: OverlayText? = null
        var bestDist = 0.1f
        for (t in texts) {
            val dx = t.x - x
            val dy = t.y - y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < bestDist) {
                bestDist = dist
                best = t
            }
        }
        return best
    }
}
