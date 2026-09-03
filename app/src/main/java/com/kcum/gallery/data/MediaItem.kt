package com.kcum.gallery.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model tunggal untuk satu item media (gambar ATAU video)
 * yang dibaca daripada MediaStore.
 */
@Parcelize
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    /** Contoh: "Pictures/Camera/" (selalu berakhir dengan '/') */
    val relativePath: String,
    /** ID baldi (folder) daripada MediaStore */
    val bucketId: String,
    /** Nama folder, contoh: "Camera" */
    val bucketName: String,
    /** Tarikh ditambah dalam sistem (saat sejak epoch) */
    val dateAdded: Long,
    /** Tarikh diubah dalam sistem (saat sejak epoch) */
    val dateModified: Long,
    /** Saiz fail dalam bait */
    val size: Long,
    val mimeType: String,
    val isVideo: Boolean,
    /** Tempoh video dalam milisaat (0 untuk gambar) */
    val durationMs: Long,
    val width: Int,
    val height: Int
) : Parcelable {

    /** Tarikh dalam milisaat untuk paparan */
    val dateMs: Long get() = dateAdded * 1000L

    /** Folder penuh tanpa nama fail, contoh: "Pictures/Camera/" */
    val folder: String get() = relativePath
}
