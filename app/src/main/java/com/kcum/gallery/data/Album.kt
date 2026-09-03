package com.kcum.gallery.data

import android.net.Uri

/**
 * Model satu album/folder. Media disusun ikut folder secara automatik
 * dengan mengumpulkan MediaItem mengikut bucketId.
 */
data class Album(
    val bucketId: String,
    val name: String,
    /** Item terbaru sebagai muka depan album */
    val cover: MediaItem,
    val count: Int,
    val totalSize: Long
)
