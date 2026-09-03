package com.kcum.gallery.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.util.ExifUtils
import com.kcum.gallery.util.FilterUtils
import com.kcum.gallery.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Editor asas imej:
 * - Putar 90° kiri/kanan
 * - Potong (crop) dengan nisbah bebas/1:1/4:3/16:9 (CropOverlayView)
 * - Penapis: kecerahan, kontras, ketepuan (slider, pratonton langsung)
 * - Lukis/annotasi (warna + ketebalan) dan teks overlay boleh diseret
 *
 * Aliran data:
 * originalBitmap (dari EXIF decode)
 *   -> baseBitmap   (selepas putar/potong)
 *   -> previewBitmap (versi kecil untuk pratonton pantas penapis)
 * Simpan: baseBitmap penuh + penapis + overlay (DrawView) -> MediaStore.
 */
class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        private const val MAX_DISPLAY_DIM = 1600

        // Mod editor
        const val MODE_NONE = 0
        const val MODE_ROTATE = 1
        const val MODE_CROP = 2
        const val MODE_FILTER = 3
        const val MODE_DRAW = 4
        const val MODE_TEXT = 5
    }

    private lateinit var ivEdit: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var drawView: DrawView

    private lateinit var panelRotate: View
    private lateinit var panelCrop: View
    private lateinit var panelFilter: View
    private lateinit var panelDraw: View
    private lateinit var panelText: View

    private var sourceUri: Uri? = null
    private var baseBitmap: Bitmap? = null
    private var previewBase: Bitmap? = null // versi kecil untuk pratonton penapis

    // Nilai penapis semasa
    private var brightness = 0     // -100..100
    private var contrast = 100     // 50..150
    private var saturation = 100   // 0..200

    private val filterColors = intArrayOf(
        0xFFF44336.toInt(), 0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(),
        0xFF2196F3.toInt(), 0xFF9C27B0.toInt(), 0xFF212121.toInt()
    )
    private var drawColor = filterColors.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        ivEdit = findViewById(R.id.iv_edit)
        cropOverlay = findViewById(R.id.crop_overlay)
        drawView = findViewById(R.id.draw_view)

        panelRotate = findViewById(R.id.panel_rotate)
        panelCrop = findViewById(R.id.panel_crop)
        panelFilter = findViewById(R.id.panel_filter)
        panelDraw = findViewById(R.id.panel_draw)
        panelText = findViewById(R.id.panel_text)

        sourceUri = Uri.parse(intent.getStringExtra(EXTRA_URI))
        loadBitmap()

        setupToolbar()
        setupModeButtons()
        setupRotatePanel()
        setupCropPanel()
        setupFilterPanel()
        setupDrawPanel()
        setupTextPanel()

        // Mod mula: luar mod (semua panel sorok)
        setMode(MODE_NONE)
    }

    // Mod editor semasa
    private var mode = MODE_NONE

    private fun loadBitmap() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = sourceUri?.let { ExifUtils.decodeSampled(applicationContext, it, MAX_DISPLAY_DIM) }
            withContext(Dispatchers.Main) {
                if (bmp == null) {
                    Toast.makeText(this@EditorActivity, R.string.load_failed, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    baseBitmap = bmp
                    previewBase = scaleDown(bmp, 1024)
                    refreshPreview()
                }
            }
        }
    }

    private fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= maxDim) return src
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt(), (src.height * scale).toInt(), true
        )
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.editor_toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.editor_title)
        toolbar.inflateMenu(R.menu.menu_editor)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_undo -> { drawView.undoLast(); true }
                R.id.action_save -> { saveToGallery(); true }
                else -> false
            }
        }
    }

    private fun setupModeButtons() {
        findViewById<ImageButton>(R.id.btn_mode_rotate).setOnClickListener { setMode(MODE_ROTATE) }
        findViewById<ImageButton>(R.id.btn_mode_crop).setOnClickListener { setMode(MODE_CROP) }
        findViewById<ImageButton>(R.id.btn_mode_filter).setOnClickListener { setMode(MODE_FILTER) }
        findViewById<ImageButton>(R.id.btn_mode_draw).setOnClickListener { setMode(MODE_DRAW) }
        findViewById<ImageButton>(R.id.btn_mode_text).setOnClickListener { setMode(MODE_TEXT) }
    }

    /** Tukar mod editor: tunjuk panel berkaitan sahaja */
    private fun setMode(newMode: Int) {
        mode = newMode
        panelRotate.visibility = if (mode == MODE_ROTATE) View.VISIBLE else View.GONE
        panelCrop.visibility = if (mode == MODE_CROP) View.VISIBLE else View.GONE
        panelFilter.visibility = if (mode == MODE_FILTER) View.VISIBLE else View.GONE
        panelDraw.visibility = if (mode == MODE_DRAW) View.VISIBLE else View.GONE
        panelText.visibility = if (mode == MODE_TEXT) View.VISIBLE else View.GONE

        cropOverlay.visibility = if (mode == MODE_CROP) View.VISIBLE else View.GONE
        drawView.visibility =
            if (mode == MODE_DRAW || mode == MODE_TEXT) View.VISIBLE else View.GONE
        if (mode == MODE_DRAW) drawView.mode = DrawView.MODE_DRAW
        if (mode == MODE_TEXT) drawView.mode = DrawView.MODE_TEXT
        if (mode == MODE_CROP) cropOverlay.resetToFull()
    }

    // =====================================================================
    // PUTAR
    // =====================================================================

    private fun setupRotatePanel() {
        findViewById<ImageButton>(R.id.btn_rot_left).setOnClickListener { rotate(-90f) }
        findViewById<ImageButton>(R.id.btn_rot_right).setOnClickListener { rotate(90f) }
    }

    private fun rotate(degrees: Float) {
        val current = baseBitmap ?: return
        val matrix = Matrix().apply { postRotate(degrees) }
        baseBitmap = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        previewBase = scaleDown(baseBitmap!!, 1024)
        drawView.clearAll() // koordinat overlay tidak lagi sah selepas geometri berubah
        refreshPreview()
    }

    // =====================================================================
    // POTONG (CROP)
    // =====================================================================

    private fun setupCropPanel() {
        findViewById<ImageButton>(R.id.btn_crop_free).setOnClickListener { cropOverlay.setAspectRatio(0f) }
        findViewById<ImageButton>(R.id.btn_crop_1_1).setOnClickListener { cropOverlay.setAspectRatio(1f) }
        findViewById<ImageButton>(R.id.btn_crop_4_3).setOnClickListener { cropOverlay.setAspectRatio(4f / 3f) }
        findViewById<ImageButton>(R.id.btn_crop_16_9).setOnClickListener { cropOverlay.setAspectRatio(16f / 9f) }
        findViewById<ImageButton>(R.id.btn_crop_apply).setOnClickListener { applyCrop() }
        findViewById<ImageButton>(R.id.btn_crop_cancel).setOnClickListener { setMode(MODE_NONE) }
    }

    /** Tukar rect overlay (koordinat view) -> rect bitmap dan potong */
    private fun applyCrop() {
        val current = baseBitmap ?: return
        val imgRect = imageDisplayRect() ?: return
        val cropRect = cropOverlay.getCropRect()

        val scale = min(
            imgRect.width() / current.width,
            imgRect.height() / current.height
        )
        val left = ((cropRect.left - imgRect.left) / scale).toInt().coerceIn(0, current.width)
        val top = ((cropRect.top - imgRect.top) / scale).toInt().coerceIn(0, current.height)
        val right = ((cropRect.right - imgRect.left) / scale).toInt().coerceIn(0, current.width)
        val bottom = ((cropRect.bottom - imgRect.top) / scale).toInt().coerceIn(0, current.height)
        val w = right - left
        val h = bottom - top
        if (w < 16 || h < 16) {
            Toast.makeText(this, R.string.crop_too_small, Toast.LENGTH_SHORT).show()
            return
        }
        baseBitmap = Bitmap.createBitmap(current, left, top, w, h)
        previewBase = scaleDown(baseBitmap!!, 1024)
        drawView.clearAll()
        setMode(MODE_NONE)
        refreshPreview()
    }

    /**
     * Kira rect paparan sebenar imej dalam ImageView (FIT_CENTER)
     * supaya koordinat overlay boleh dipetakan ke koordinat bitmap.
     */
    private fun imageDisplayRect(): RectF? {
        val bmp = baseBitmap ?: return null
        val vw = ivEdit.width.toFloat()
        val vh = ivEdit.height.toFloat()
        if (vw <= 0 || vh <= 0) return null
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = min(vw / bw, vh / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f
        return RectF(left, top, left + dw, top + dh)
    }

    // =====================================================================
    // PENAPIS (BRIGHTNESS / CONTRAST / SATURATION)
    // =====================================================================

    private fun setupFilterPanel() {
        val sliderBrightness = findViewById<SeekBar>(R.id.slider_brightness)
        val sliderContrast = findViewById<SeekBar>(R.id.slider_contrast)
        val sliderSaturation = findViewById<SeekBar>(R.id.slider_saturation)

        sliderBrightness.progress = brightness + 100      // 0..200 => -100..100
        sliderContrast.progress = contrast - 50           // 0..100 => 50..150
        sliderSaturation.progress = saturation            // 0..200

        sliderBrightness.setOnSeekBarChangeListener(simpleSeek {
            brightness = it - 100
            refreshPreview()
        })
        sliderContrast.setOnSeekBarChangeListener(simpleSeek {
            contrast = it + 50
            refreshPreview()
        })
        sliderSaturation.setOnSeekBarChangeListener(simpleSeek {
            saturation = it
            refreshPreview()
        })

        findViewById<ImageButton>(R.id.btn_filter_reset).setOnClickListener {
            brightness = 0; contrast = 100; saturation = 100
            sliderBrightness.progress = 100
            sliderContrast.progress = 50
            sliderSaturation.progress = 100
            refreshPreview()
        }
    }

    private fun simpleSeek(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        }
    }

    // =====================================================================
    // LUKIS (DRAW) & TEKS
    // =====================================================================

    private fun setupDrawPanel() {
        val colorIds = intArrayOf(
            R.id.btn_c1, R.id.btn_c2, R.id.btn_c3, R.id.btn_c4, R.id.btn_c5, R.id.btn_c6
        )
        colorIds.forEachIndexed { index, id ->
            findViewById<ImageButton>(id).setOnClickListener {
                drawColor = filterColors[index]
                drawView.currentColor = drawColor
            }
        }
        val widthSlider = findViewById<SeekBar>(R.id.slider_draw_width)
        widthSlider.progress = (drawView.currentWidthFraction * 1000).toInt()
        widthSlider.setOnSeekBarChangeListener(simpleSeek {
            drawView.currentWidthFraction = it / 1000f
        })
    }

    private fun setupTextPanel() {
        val colorIds = intArrayOf(
            R.id.btn_tc1, R.id.btn_tc2, R.id.btn_tc3, R.id.btn_tc4, R.id.btn_tc5, R.id.btn_tc6
        )
        var textColor = filterColors.last()
        colorIds.forEachIndexed { index, id ->
            findViewById<ImageButton>(id).setOnClickListener { textColor = filterColors[index] }
        }
        findViewById<ImageButton>(R.id.btn_add_text).setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_input_text, null)
            val etText = view.findViewById<EditText>(R.id.et_input_text)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_text_title)
                .setView(view)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val text = etText.text.toString().trim()
                    if (text.isNotEmpty()) drawView.addText(text, textColor)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // =====================================================================
    // PRATONTON & SIMPAN
    // =====================================================================

    /** Kemas kini ImageView dengan baseBitmap + penapis semasa */
    private fun refreshPreview() {
        val base = previewBase ?: return
        val filtered = if (brightness == 0 && contrast == 100 && saturation == 100) {
            base
        } else {
            FilterUtils.apply(base, brightness, contrast, saturation)
        }
        ivEdit.setImageBitmap(filtered)
    }

    /** Simpan hasil akhir ke galeri melalui MediaStore */
    private fun saveToGallery() {
        val base = baseBitmap ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Guna penapis pada resolusi penuh
                val filtered = if (brightness == 0 && contrast == 100 && saturation == 100) {
                    base.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    FilterUtils.apply(base, brightness, contrast, saturation)
                }

                // 2. Bakukan overlay lukisan/teks (koordinat normalized)
                if (drawView.hasContent()) {
                    val canvas = Canvas(filtered)
                    drawView.renderOn(canvas, filtered.width, filtered.height)
                }

                // 3. Simpan ke MediaStore (Pictures/Kcum Gallery)
                val uri = MediaStoreUtils.saveBitmapToGallery(applicationContext, filtered)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Toast.makeText(
                            this@EditorActivity, R.string.saved_as_copy, Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
