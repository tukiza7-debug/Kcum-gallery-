package com.kcum.gallery.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.View
import java.util.Locale

/**
 * Fungsi sambungan (extensions) kecil yang dikongsi seluruh aplikasi.
 */
object Formats {

    /** Saiz fail mesra manusia: "1.2 MB" */
    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    /** Tarikh relatif mesra: "Hari ini 14:32", "Semalam", atau tarikh penuh */
    fun date(context: Context, timeMs: Long): CharSequence {
        return DateUtils.getRelativeTimeSpanString(
            timeMs, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
        )
    }

    /** Tempoh video: "1:23" atau "12:34" */
    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Kongsi satu URI melalui Intent.ACTION_SEND */
fun Context.shareUri(uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null))
}

/** Kongsi pelbagai URI melalui Intent.ACTION_SEND_MULTIPLE */
fun Context.shareUris(uris: List<Uri>, mimeType: String) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
        val itemMimeType = mimeType.ifBlank { guessMime(uris.first()) }
        shareUri(uris.first(), itemMimeType)
        return
    }
    val isMixed = uris.groupBy { guessMime(it).startsWith("video") }.size > 1
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (isMixed) "*/*" else mimeType.ifBlank { guessMime(uris.first()) }
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null))
}

fun guessMime(uri: Uri): String {
    val path = uri.toString().lowercase(Locale.ROOT)
    return if (path.contains("video")) "video/*" else "image/*"
}

/** Tunjuk/sembunyi view dengan kemasan null-selamat */
fun View.visibleOr(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
