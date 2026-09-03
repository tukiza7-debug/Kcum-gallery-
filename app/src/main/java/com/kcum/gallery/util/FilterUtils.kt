package com.kcum.gallery.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Penapis asas imej: kecerahan (brightness), kontras (contrast), ketepuan (saturation)
 * menggunakan ColorMatrix - tanpa perlu perpustakaan luaran.
 *
 * Julat:
 * - brightness : -100 .. +100 (ditambah pada setiap saluran)
 * - contrast   : 50 .. 150 (%) - 100 = asal
 * - saturation : 0 .. 200 (%)  - 100 = asal, 0 = hitam putih
 */
object FilterUtils {

    fun buildColorMatrix(brightness: Int, contrastPercent: Int, saturationPercent: Int): ColorMatrix {
        val contrast = contrastPercent / 100f
        val saturation = saturationPercent / 100f
        val brightnessF = brightness.toFloat()

        // Matriks kontras: skala di sekitar titik tengah 128
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, 128f * (1f - contrast),
                0f, contrast, 0f, 0f, 128f * (1f - contrast),
                0f, 0f, contrast, 0f, 128f * (1f - contrast),
                0f, 0f, 0f, 1f, 0f
            )
        )

        // Matriks kecerahan: tambah nilai tetap pada setiap saluran
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessF,
                0f, 1f, 0f, 0f, brightnessF,
                0f, 0f, 1f, 0f, brightnessF,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(saturation)

        // Gabungkan: saturasi x kontras x kecerahan
        saturationMatrix.postConcat(contrastMatrix)
        saturationMatrix.postConcat(brightnessMatrix)
        return saturationMatrix
    }

    /** Guna penapis warna pada bitmap dan pulangkan hasilnya (bitmap baru) */
    fun apply(src: Bitmap, brightness: Int, contrastPercent: Int, saturationPercent: Int): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                buildColorMatrix(brightness, contrastPercent, saturationPercent)
            )
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }
}
