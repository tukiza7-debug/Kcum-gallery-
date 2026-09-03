package com.kcum.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rekod satu item dalam album peribadi (tersembunyi).
 *
 * Strategi: bait fail dipindahkan ke storan dalaman app (filesDir/hidden)
 * dan entri MediaStore asal dipadam, supaya item hilang dari galeri lain.
 * Untuk keselamatan tambahan, storan dalaman app adalah privasi app dan
 * dikecualikan dari Android Backup (lihat backup_rules.xml).
 */
@Entity(tableName = "hidden_items")
data class HiddenItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUri: String,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val isVideo: Boolean,
    val size: Long,
    val storedPath: String,
    /** Masa disembunyikan (milisaat) */
    val hiddenAt: Long
)
