package com.kcum.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rekod satu item dalam Recycle Bin (Tong Sampah).
 *
 * Strategi: apabila pengguna "padam" item, bait fail disalin ke storan dalaman
 * app (filesDir/trash) sebelum entri MediaStore asal dipadam. Item kekal di sini
 * selama 30 hari, selepas itu dibersihkan secara automatik (purge).
 */
@Entity(tableName = "trash_items")
data class TrashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** URI MediaStore asal (content://media/...) untuk restore */
    val originalUri: String,
    val displayName: String,
    val mimeType: String,
    /** Laluan relatif asal, contoh "Pictures/Camera/" - dipulihkan ke sini */
    val relativePath: String,
    val isVideo: Boolean,
    val size: Long,
    /** Laluan penuh fail salinan dalam storan dalaman */
    val storedPath: String,
    /** Masa dimasukkan ke tong sampah (milisaat) */
    val trashedAt: Long
)
