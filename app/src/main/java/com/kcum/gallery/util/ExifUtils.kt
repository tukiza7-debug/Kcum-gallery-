package com.kcum.gallery.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Utiliti EXIF: decode bitmap dengan orientasi betul + pensampelan (inSampleSize)
 * supaya tidak kehabisan memori untuk imej bersaiz besar.
 */
object ExifUtils {

    /** Muat bitmap dengan saiz maksimum `maxDim` px (sisi terpanjang) + orientasi EXIF */
    fun decodeSampled(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        // 1. Baca dims sahaja dahulu
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        // 2. Kira inSampleSize
        var sample = 1
        val largest = maxOf(options.outWidth, options.outHeight)
        while (largest / (sample * 2) >= maxDim) sample *= 2

        // 3. Decode sebenar
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // 4. Putar mengikut EXIF
        return applyExifRotation(context, uri, raw)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
