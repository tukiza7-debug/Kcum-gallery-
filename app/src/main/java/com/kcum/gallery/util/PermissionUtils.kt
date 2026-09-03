package com.kcum.gallery.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Pengendalian kebenaran storan mengikut versi Android:
 * - API 33+: READ_MEDIA_IMAGES + READ_MEDIA_VIDEO (+ VISUAL_USER_SELECTED untuk akses separa)
 * - API < 33: READ_EXTERNAL_STORAGE
 * Termasuk bantuan redirect ke Settings apabila kebenaran ditolak kekal.
 */
object PermissionUtils {

    /** Senarai kebenaran runtime yang diperlukan mengikut versi OS */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Adakah kita sudah boleh membaca media?
     * Android 14: jika pengguna pilih "Pilih gambar" (akses separa),
     * READ_MEDIA_VISUAL_USER_SELECTED akan diluluskan sebagai ganti.
     */
    fun hasStoragePermission(context: Context): Boolean {
        val required = requiredPermissions()
        val allGranted = required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val partial = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
            if (partial) return true
        }
        return false
    }

    /** Benarkan tanya semula? false = ditolak kekal (perlu redirect ke Settings) */
    fun canAskAgain(context: Context, deniedPermissions: List<String>): Boolean {
        return deniedPermissions.any { shouldShowRationaleStatic(context, it) }
    }

    private fun shouldShowRationaleStatic(context: Context, permission: String): Boolean {
        return try {
            val activity = context as? android.app.Activity
            activity != null && androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(activity, permission)
        } catch (e: Exception) {
            false
        }
    }

    /** Buka halaman tetapan aplikasi (untuk kebenaran yang ditolak kekal) */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Tiada skrin tetapan - abaikan
        }
    }

    // =====================================================================
    // MANAGE_EXTERNAL_STORAGE (akses penuh) - lihat nota risiko di Manifest
    // =====================================================================

    /** Adakah akses penuh storan telah diberikan (Android 11+)? */
    fun hasFullStorageAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
    }

    /**
     * Buka skrin sistem untuk memberi akses penuh storan.
     * AMARAN: hanya dipanggil apabila pengguna memintanya secara eksplisit
     * dari Tetapan dalam aplikasi (lihat SettingsFragment untuk ulasan risiko).
     */
    fun requestFullStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: ActivityNotFoundException) {
                // Tiada skrin - abaikan
            }
        }
    }
}
