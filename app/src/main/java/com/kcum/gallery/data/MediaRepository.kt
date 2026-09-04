package com.kcum.gallery.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.kcum.gallery.util.MediaStoreUtils
import com.kcum.gallery.util.MediaStoreUtils.AccessDeniedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Satu baris dalam paparan timeline: sama ada pengepala (bulan/tahun) atau media */
sealed class TimelineRow {
    data class Header(val title: String) : TimelineRow()
    data class Media(val item: MediaItem) : TimelineRow()
}

/** Statistik penggunaan storan */
data class StorageStats(
    val imageCount: Int,
    val videoCount: Int,
    val totalBytes: Long,
    val freeBytes: Long,
    val trashCount: Int,
    val hiddenCount: Int
)

/** Hasil operasi pukal (delete/move/copy/hide) */
data class BatchResult(
    val success: Int,
    val failed: Int,
    /** IntentSender daripada RecoverableSecurityException untuk minta akses pengguna */
    val intentSender: android.content.IntentSender? = null
)

/**
 * Repository utama (lapisan data MVVM).
 * Semua pertanyaan MediaStore dan operasi fail di sini; ViewModel hanya memanggil.
 */
class MediaRepository private constructor(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val trashDao = db.trashDao()
    private val hiddenDao = db.hiddenDao()

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 hari

        @Volatile
        private var instance: MediaRepository? = null

        fun get(context: Context): MediaRepository =
            instance ?: synchronized(this) {
                instance ?: MediaRepository(context.applicationContext).also { instance = it }
            }
    }

    // =====================================================================
    // PERTANYAAN MEDIASTORE
    // =====================================================================

    /**
     * Muat semua media (gambar + video),opsyenal ditapis ikut baldi (album).
     * Guna MediaStore API sepenuhnya (serasi scoped storage Android 10+).
     */
    suspend fun loadMedia(bucketId: String? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val useSqlFilter = bucketId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val selection = if (useSqlFilter) MediaStore.MediaColumns.BUCKET_ID + " = ?" else null
        val selectionArgs = if (useSqlFilter) arrayOf(bucketId) else null
        val result = ArrayList<MediaItem>()
        result += queryCollection(
            collection = imagesCollection(),
            isVideo = false,
            projection = imageProjection(),
            selection = selection,
            selectionArgs = selectionArgs
        )
        result += queryCollection(
            collection = videosCollection(),
            isVideo = true,
            projection = videoProjection(),
            selection = selection,
            selectionArgs = selectionArgs
        )
    }

    private fun imagesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun videosCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun imageProjection(): Array<String> {
        val base = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA, // laluan penuh - untuk keserasian API < 29
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        // RELATIVE_PATH & BUCKET_* hanya wujud pada API 29+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) base + arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        ) else base
    }

    private fun videoProjection(): Array<String> =
        imageProjection() + arrayOf(MediaStore.Video.VideoColumns.DURATION)

    private fun queryCollection(
        collection: Uri,
        isVideo: Boolean,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val relIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val bucketIdIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID) else -1
            val bucketNameIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) else -1
            val durationIdx = if (isVideo)
                cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val data = cursor.getString(dataIdx) ?: continue
                val relativePath: String
                val bucketId: String
                val bucketName: String
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relIdx >= 0) {
                    relativePath = cursor.getString(relIdx) ?: ""
                    bucketId = if (bucketIdIdx >= 0) cursor.getString(bucketIdIdx) ?: "" else ""
                    bucketName = if (bucketNameIdx >= 0)
                        (cursor.getString(bucketNameIdx) ?: "") else ""
                } else {
                    // API < 29 : terbitkan maklumat folder daripada laluan DATA
                    val parent = File(data).parent ?: ""
                    relativePath = if (parent.contains("/")) {
                        val idx = parent.lastIndexOf('/')
                        parent.substring(0, idx + 1)
                    } else ""
                    bucketId = parent.hashCode().toString()
                    bucketName = parent.substringAfterLast('/', "Storage")
                }
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameIdx) ?: "",
                    relativePath = relativePath,
                    bucketId = bucketId,
                    bucketName = bucketName,
                    dateAdded = cursor.getLong(addedIdx),
                    dateModified = cursor.getLong(modifiedIdx),
                    size = cursor.getLong(sizeIdx),
                    mimeType = cursor.getString(mimeIdx) ?: "image/*",
                    isVideo = isVideo,
                    durationMs = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L,
                    width = cursor.getInt(widthIdx),
                    height = cursor.getInt(heightIdx)
                )
            }
        }
        return items
    }

    /** Kumpulkan media ikut album/folder secara automatik */
    fun groupIntoAlbums(items: List<MediaItem>): List<Album> {
        return items.groupBy { it.bucketId }
            .map { (bucketId, list) ->
                val sorted = list.sortedByDescending { it.dateAdded }
                Album(
                    bucketId = bucketId,
                    name = sorted.first().bucketName.ifBlank {
                        sorted.first().relativePath.trim('/').substringAfterLast('/', "Storage")
                    },
                    cover = sorted.first(),
                    count = list.size,
                    totalSize = list.sumOf { it.size }
                )
            }
            .sortedByDescending { it.cover.dateAdded }
    }

    /** Bina baris timeline (dikumpulkan ikut bulan & tahun) */
    fun buildTimeline(items: List<MediaItem>): List<TimelineRow> {
        val rows = ArrayList<TimelineRow>()
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val sorted = items.sortedByDescending { it.dateAdded }
        var lastTitle: String? = null
        for (item in sorted) {
            val title = format.format(Date(item.dateMs))
            if (title != lastTitle) {
                rows += TimelineRow.Header(title)
                lastTitle = title
            }
            rows += TimelineRow.Media(item)
        }
        return rows
    }

    // =====================================================================
    // SUSUNAN & TAPISAN
    // =====================================================================

    fun sortItems(items: List<MediaItem>, sortBy: String, asc: Boolean): List<MediaItem> {
        val comparator = when (sortBy) {
            PrefsRepository.SORT_NAME -> compareBy<MediaItem> { it.name.lowercase(Locale.ROOT) }
            PrefsRepository.SORT_SIZE -> compareBy { it.size }
            PrefsRepository.SORT_TYPE -> compareBy<MediaItem> { it.isVideo }.thenBy { it.name }
            else -> compareBy { it.dateAdded } // SORT_DATE
        }
        return if (asc) items.sortedWith(comparator) else items.sortedWith(comparator.reversed())
    }

    // =====================================================================
    // RECYCLE BIN (TONG SAMPAH)
    // =====================================================================

    /**
     * Alih item ke tong sampah: salin bait ke storan dalaman, rekod dalam Room,
     * kemudian padam entri MediaStore. Item boleh dipulihkan dalam 30 hari.
     */
    suspend fun moveToTrash(items: List<MediaItem>): BatchResult = withContext(Dispatchers.IO) {
        var ok = 0; var fail = 0; var sender: android.content.IntentSender? = null
        val trashDir = File(context.filesDir, "trash").apply { mkdirs() }
        for (item in items) {
            val stored = File(trashDir, "${UUID.randomUUID()}.${extensionOf(item)}")
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    stored.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Tidak dapat membuka URI")
                val rowId = trashDao.insert(
                    TrashItem(
                        originalUri = item.uri.toString(),
                        displayName = item.name,
                        mimeType = item.mimeType,
                        relativePath = item.relativePath.ifBlank { "Pictures/Kcum Gallery/" },
                        isVideo = item.isVideo,
                        size = item.size,
                        storedPath = stored.absolutePath,
                        trashedAt = System.currentTimeMillis()
                    )
                )
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    ok++
                } catch (e: android.app.RecoverableSecurityException) {
                    // Sistem minta pengguna benarkan akses ke item ini
                    trashDao.deleteById(rowId)
                    stored.delete()
                    fail++
                    sender = sender ?: e.userAction.actionIntent.intentSender
                } catch (e: com.kcum.gallery.util.MediaStoreUtils.AccessDeniedException) {
                    trashDao.deleteById(rowId)
                    stored.delete()
                    fail++
                    sender = sender ?: e.intentSender
                }
            } catch (e: Exception) {
                stored.delete()
                fail++
            }
        }
        BatchResult(ok, fail, sender)
    }

    /** Pulihkan item dari tong sampah ke lokasi asal melalui MediaStore */
    suspend fun restoreFromTrash(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        val ok = MediaStoreUtils.insertMediaFromStream(
            context = context,
            relativePath = item.relativePath.ifBlank { "Pictures/Kcum Gallery/" },
            displayName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.isVideo,
            source = File(item.storedPath)
        )
        if (ok) {
            File(item.storedPath).delete()
            trashDao.delete(item)
        }
        ok
    }

    /** Padam kekal satu item dari tong sampah */
    suspend fun deleteTrashPermanently(item: TrashItem) = withContext(Dispatchers.IO) {
        File(item.storedPath).delete()
        trashDao.delete(item)
    }

    /** Kosongkan seluruh tong sampah */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        trashDao.getAllOnce().forEach { File(it.storedPath).delete() }
        trashDao.deleteAll()
    }

    /** Auto-bersih item tong sampah lebih 30 hari - dipanggil semasa app dimulakan */
    suspend fun purgeOldTrash() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        trashDao.getOlderThan(cutoff).forEach {
            File(it.storedPath).delete()
            trashDao.delete(it)
        }
    }

    fun observeTrash() = trashDao.getAll()

    // =====================================================================
    // ALBUM PERIBADI (HIDE / UNHIDE)
    // =====================================================================

    /** Sembunyikan item: pindahkan bait ke storan dalaman + buang dari MediaStore */
    suspend fun hideItems(items: List<MediaItem>): BatchResult = withContext(Dispatchers.IO) {
        var ok = 0; var fail = 0; var sender: android.content.IntentSender? = null
        val hiddenDir = File(context.filesDir, "hidden").apply { mkdirs() }
        for (item in items) {
            val stored = File(hiddenDir, "${UUID.randomUUID()}.${extensionOf(item)}")
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    stored.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Tidak dapat membuka URI")
                val rowId = hiddenDao.insert(
                    HiddenItem(
                        originalUri = item.uri.toString(),
                        displayName = item.name,
                        mimeType = item.mimeType,
                        relativePath = item.relativePath.ifBlank { "Pictures/Kcum Gallery/" },
                        isVideo = item.isVideo,
                        size = item.size,
                        storedPath = stored.absolutePath,
                        hiddenAt = System.currentTimeMillis()
                    )
                )
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    ok++
                } catch (e: android.app.RecoverableSecurityException) {
                    hiddenDao.deleteById(rowId)
                    stored.delete()
                    fail++
                    sender = sender ?: e.userAction.actionIntent.intentSender
                } catch (e: com.kcum.gallery.util.MediaStoreUtils.AccessDeniedException) {
                    hiddenDao.deleteById(rowId)
                    stored.delete()
                    fail++
                    sender = sender ?: e.intentSender
                }
            } catch (e: Exception) {
                stored.delete()
                fail++
            }
        }
        BatchResult(ok, fail, sender)
    }

    /** Pulihkan item tersembunyi ke lokasi asal dalam galeri */
    suspend fun unhideItem(item: HiddenItem): Boolean = withContext(Dispatchers.IO) {
        val ok = MediaStoreUtils.insertMediaFromStream(
            context = context,
            relativePath = item.relativePath.ifBlank { "Pictures/Kcum Gallery/" },
            displayName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.isVideo,
            source = File(item.storedPath)
        )
        if (ok) {
            File(item.storedPath).delete()
            hiddenDao.delete(item)
        }
        ok
    }

    /** Padam kekal item tersembunyi (tanpa pulihkan) */
    suspend fun deleteHiddenPermanently(item: HiddenItem) = withContext(Dispatchers.IO) {
        File(item.storedPath).delete()
        hiddenDao.delete(item)
    }

    fun observeHidden() = hiddenDao.getAll()

    // =====================================================================
    // STATISTIK STORAN
    // =====================================================================

    suspend fun storageStats(): StorageStats = withContext(Dispatchers.IO) {
        val all = loadMedia()
        StorageStats(
            imageCount = all.count { !it.isVideo },
            videoCount = all.count { it.isVideo },
            totalBytes = all.sumOf { it.size },
            freeBytes = MediaStoreUtils.freeBytes(),
            trashCount = trashDao.count(),
            hiddenCount = hiddenDao.count()
        )
    }

    // =====================================================================
    // UTILITI KECIL
    // =====================================================================

    private fun extensionOf(item: MediaItem): String {
        val fromName = item.name.substringAfterLast('.', "")
        if (fromName.isNotBlank() && fromName.length <= 5) return fromName
        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(item.mimeType) ?: if (item.isVideo) "mp4" else "jpg"
    }

    /** Kemas kini pilihan susunan pengguna */
    fun updateSortValues(prefs: PrefsRepository, sortBy: String, asc: Boolean) {
        prefs.sortBy = sortBy
        prefs.sortAsc = asc
    }

    /** Padam satu URI MediaStore terus (dengan pengendalian akses) */
    suspend fun deleteFromMediaStore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: AccessDeniedException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Tulis ContentValues ke MediaStore (contoh: kemas kini metadata) */
    suspend fun updateMedia(uri: Uri, values: ContentValues): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.update(uri, values, null, null) > 0
            } catch (e: Exception) {
                false
            }
        }
}
