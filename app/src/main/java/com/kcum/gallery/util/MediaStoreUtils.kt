package com.kcum.gallery.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utiliti MediaStore untuk semua operasi storan.
 *
 * PENTING (scoped storage Android 10+):
 * - Folder baru dicipta melalui MediaStore API (RELATIVE_PATH + IS_PENDING),
 *   BUKAN File API terus, kerana akses laluan terus disekat pada Android 10+.
 * - Rename/move/copy pada Android 10+ dilakukan dengan mengemas kini
 *   RELATIVE_PATH / DISPLAY_NAME melalui ContentResolver.
 * - Pada API < 29 (Android 9 ke bawah) kita guna File API + MediaScanner
 *   kerana WRITE_EXTERNAL_STORAGE masih sah di sana.
 */
object MediaStoreUtils {

    /** Dibuang apabila sistem menolak operasi (RecoverableSecurityException) */
    class AccessDeniedException(val intentSender: IntentSender?) :
        Exception("Akses ditolak oleh sistem storan")

    // Nama subfolder untuk hasil edit / restore lalai
    const val APP_FOLDER = "Kcum Gallery"

    // =====================================================================
    // CREATE FOLDER (melalui MediaStore - lihat nota di atas)
    // =====================================================================

    /**
     * Cipta folder baru dalam galeri storan.
     *
     * Teknik MediaStore (Android 10+): MediaStore tidak menyediakan API "mkdir"
     * terus, jadi kita masukkan SATU entri media sementara (IS_PENDING=1) dengan
     * RELATIVE_PATH="Pictures/<nama>", tulis bait placeholder, tandakan siap,
     * kemudian padam entri placeholder tersebut. Direktori yang dicipta oleh
     * MediaProvider KEKAL wujud pada kebanyakan peranti walaupun fail di dalamnya
     * dipadam; dan walaupun dibersihkan, direktori akan dicipta semula secara
     * automatik apabila fail pertama dipindahkan ke dalamnya kelak.
     *
     * Pada Android 9 ke bawah, kita boleh guna File API (WRITE_EXTERNAL_STORAGE).
     *
     * @return true jika berjaya
     */
    fun createFolderInGallery(context: Context, folderName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createFolderViaMediaStore(context, folderName)
        } else {
            // Android 9 ke bawah: direktori terus + imbasan MediaScanner
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                folderName
            )
            val ok = dir.exists() || dir.mkdirs()
            if (ok) scanPath(context, dir.absolutePath)
            ok
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createFolderViaMediaStore(context: Context, folderName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ".kcum_folder_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$folderName")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return try {
            val uri = resolver.insert(collection, values) ?: return false
            // Tulis bait kosong untuk finalisasi entri pending
            resolver.openOutputStream(uri)?.use { it.write(ByteArray(0)) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            // Buang placeholder - direktori kekal (lihat doc di atas)
            resolver.delete(uri, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Imbas semula laluan supaya MediaStore mengenalinya (untuk API < 29) */
    fun scanPath(context: Context, path: String) {
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(path), null, null
        )
    }

    // =====================================================================
    // RENAME
    // =====================================================================

    /** Tukar nama satu fail media melalui MediaStore */
    fun renameFile(context: Context, uri: Uri, newName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                context.contentResolver.update(uri, values, null, null) > 0
            } else {
                // API < 29: rename terus pada fail, kemudian imbas semula
                val path = queryDataPath(context, uri) ?: return false
                val file = File(path)
                val renamed = File(file.parent, newName)
                val ok = file.renameTo(renamed)
                if (ok) {
                    scanPath(context, path)
                    scanPath(context, renamed.absolutePath)
                }
                ok
            }
        } catch (e: AccessDeniedException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tukar nama folder: kemas kini RELATIVE_PATH semua item di dalamnya
     * (Android 10+). Pada Android 9 ke bawah, guna File.renameTo.
     */
    fun renameFolder(
        context: Context,
        items: List<com.kcum.gallery.data.MediaItem>,
        oldRelativePath: String,
        newFolderName: String
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val newRelativePath = oldRelativePath
                .trim('/').substringBeforeLast('/') + "/" + newFolderName + "/"
            val resolver = context.contentResolver
            var allOk = true
            for (item in items) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, newRelativePath)
                    }
                    if (resolver.update(item.uri, values, null, null) <= 0) allOk = false
                } catch (e: android.app.RecoverableSecurityException) {
                    throw AccessDeniedException(e.userAction.actionIntent.intentSender)
                } catch (e: Exception) {
                    allOk = false
                }
            }
            allOk
        } else {
            // API < 29: rename direktori fizikal
            val first = items.firstOrNull() ?: return false
            val parentPath = File(first.relativePath)
            val dir = File(Environment.getExternalStorageDirectory(), oldRelativePath.trim('/'))
            val target = File(dir.parentFile, newFolderName)
            val ok = dir.renameTo(target)
            if (ok) {
                scanPath(context, dir.absolutePath)
                scanPath(context, target.absolutePath)
            }
            ok
        }
    }

    // =====================================================================
    // MOVE & COPY
    // =====================================================================

    /** Alih item ke folder (relativePath) lain melalui kemas kini MediaStore */
    fun moveItems(
        context: Context,
        items: List<com.kcum.gallery.data.MediaItem>,
        targetRelativePath: String
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            var allOk = true
            for (item in items) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                    }
                    if (resolver.update(item.uri, values, null, null) <= 0) allOk = false
                } catch (e: android.app.RecoverableSecurityException) {
                    throw AccessDeniedException(e.userAction.actionIntent.intentSender)
                } catch (e: Exception) {
                    allOk = false
                }
            }
            allOk
        } else {
            var allOk = true
            for (item in items) {
                try {
                    val src = File(queryDataPath(context, item.uri) ?: continue)
                    val destDir = File(
                        Environment.getExternalStorageDirectory(), targetRelativePath.trim('/')
                    )
                    destDir.mkdirs()
                    val dest = uniqueFile(destDir, src.name)
                    if (!src.renameTo(dest)) allOk = false
                    else {
                        scanPath(context, src.absolutePath)
                        scanPath(context, dest.absolutePath)
                    }
                } catch (e: Exception) {
                    allOk = false
                }
            }
            allOk
        }
    }

    /** Salin (duplicate) item ke folder lain - item baru diwujudkan di MediaStore */
    fun copyItems(
        context: Context,
        items: List<com.kcum.gallery.data.MediaItem>,
        targetRelativePath: String
    ): Int {
        var ok = 0
        for (item in items) {
            val displayName = duplicateName(item.name)
            val success = try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    insertMediaFromStream(
                        context, targetRelativePath, displayName,
                        item.mimeType, item.isVideo, input
                    )
                } ?: false
            } catch (e: AccessDeniedException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (success) ok++
        }
        return ok
    }

    /** Nama untuk salinan: "IMG_1.jpg" -> "IMG_1 (salinan).jpg" / " (salinan 2)" dst. */
    private fun duplicateName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return "$base (salinan)$ext"
    }

    /** Cari nama fail yang belum wujud dalam direktori (untuk API < 29) */
    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            f = File(dir, "$base-$i$ext")
            i++
        }
        return f
    }

    // =====================================================================
    // INSERT / SIMPAN MEDIA
    // =====================================================================

    /**
     * Masukkan media baru ke MediaStore daripada fail sumber / stream.
     * Berfungsi untuk: copy item, restore dari tong sampah, unhide item.
     */
    fun insertMediaFromStream(
        context: Context,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        source: File
    ): Boolean = source.inputStream().use { insertMediaFromStream(
        context, relativePath, displayName, mimeType, isVideo, it) }

    fun insertMediaFromStream(
        context: Context,
        relativePath: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        source: InputStream
    ): Boolean {
        val resolver = context.contentResolver
        val mime = mimeType.ifBlank {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                displayName.substringAfterLast('.', "jpg")
            ) ?: if (isVideo) "video/mp4" else "image/jpeg"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection =
                (if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath.ensureTrailingSlash())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            try {
                val uri = resolver.insert(collection, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> source.copyTo(out) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            // API < 29: tulis terus + imbas
            return try {
                val base = Environment.getExternalStoragePublicDirectory(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                )
                val dir = File(
                    base,
                    relativePath.trim('/').removePrefix("Pictures/").removePrefix("Movies/")
                        .ifBlank { APP_FOLDER }
                )
                dir.mkdirs()
                val dest = uniqueFile(dir, displayName)
                dest.outputStream().use { out -> source.copyTo(out) }
                scanPath(context, dest.absolutePath)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Simpan bitmap hasil editor ke galeri (Pictures/Kcum Gallery).
     * @return URI item baru atau null jika gagal
     */
    fun saveBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap): Uri? {
        val name = "Kcum_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$APP_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                APP_FOLDER
            )
            dir.mkdirs()
            val file = uniqueFile(dir, name)
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            scanPath(context, file.absolutePath)
            Uri.fromFile(file)
        }
    }

    // =====================================================================
    // PERTANYAAN BANTU
    // =====================================================================

    /** Dapatkan laluan DATA penuh untuk satu URI (API < 29 perlukan ini) */
    fun queryDataPath(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Dapatkan URI MediaStore daripada ID + jenis */
    fun mediaUri(id: Long, isVideo: Boolean): Uri {
        val collection =
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(collection, id)
    }

    /** Ruang kosong storan utama dalam bait */
    fun freeBytes(): Long {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
