package com.kcum.gallery.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Simpan semua pilihan pengguna (SharedPreferences).
 * - Susunan (tarikh/saiz/nama/jenis + menaik/menurun)
 * - Mod paparan (grid/senarai/timeline) dan bilangan lajur grid (2/3/4)
 * - Tema gelap/terahang/ikut sistem
 * - Tetapan kunci app (PIN hash, timeout, biometrik)
 * - Tempoh slideshow
 */
class PrefsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kcum_prefs", Context.MODE_PRIVATE)

    companion object {
        const val SORT_DATE = "date"
        const val SORT_NAME = "name"
        const val SORT_SIZE = "size"
        const val SORT_TYPE = "type"

        const val VIEW_GRID = "grid"
        const val VIEW_LIST = "list"
        const val VIEW_TIMELINE = "timeline"

        // Timeout kunci app (milisaat). -1 = tidak pernah
        const val LOCK_IMMEDIATE = 0L
        const val LOCK_1_MIN = 60_000L
        const val LOCK_5_MIN = 300_000L
        const val LOCK_NEVER = -1L

        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SORT_ASC = "sort_asc"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_GRID_SPAN = "grid_span"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANG = "lang_tag"
        private const val KEY_SLIDESHOW_SEC = "slideshow_sec"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_VIDEO_AUTOPLAY = "video_autoplay"
        private const val KEY_VIDEO_LOOP = "video_loop"
        private const val KEY_VIDEO_MUTED = "video_muted"

        @Volatile
        private var instance: PrefsRepository? = null

        fun get(context: Context): PrefsRepository =
            instance ?: synchronized(this) {
                instance ?: PrefsRepository(context.applicationContext).also { instance = it }
            }
    }

    // ---------- Susunan ----------
    var sortBy: String
        get() = prefs.getString(KEY_SORT_BY, SORT_DATE) ?: SORT_DATE
        set(value) = prefs.edit().putString(KEY_SORT_BY, value).apply()

    var sortAsc: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASC, false) // lalai: terbaru dahulu
        set(value) = prefs.edit().putBoolean(KEY_SORT_ASC, value).apply()

    // ---------- Paparan ----------
    var viewMode: String
        get() = prefs.getString(KEY_VIEW_MODE, VIEW_GRID) ?: VIEW_GRID
        set(value) = prefs.edit().putString(KEY_VIEW_MODE, value).apply()

    /** Bilangan lajur grid: 2, 3 atau 4 */
    var gridSpan: Int
        get() = prefs.getInt(KEY_GRID_SPAN, 3)
        set(value) = prefs.edit().putInt(KEY_GRID_SPAN, value).apply()

    // ---------- Tema ----------
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    // ---------- Bahasa ----------
    /** "" = ikut sistem, "ms", "en" */
    var langTag: String
        get() = prefs.getString(KEY_LANG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    // ---------- Slideshow ----------
    var slideshowSeconds: Int
        get() = prefs.getInt(KEY_SLIDESHOW_SEC, 5)
        set(value) = prefs.edit().putInt(KEY_SLIDESHOW_SEC, value).apply()

    // ---------- Keselamatan ----------
    var lockTimeout: Long
        get() = prefs.getLong(KEY_LOCK_TIMEOUT, LOCK_IMMEDIATE)
        set(value) = prefs.edit().putLong(KEY_LOCK_TIMEOUT, value).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    fun hasPin(): Boolean = !pinHash.isNullOrBlank()

    // ---------- Video ----------
    var videoAutoplay: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_AUTOPLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_AUTOPLAY, value).apply()

    var videoLoop: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_LOOP, true)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_LOOP, value).apply()

    var videoMuted: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_MUTED, value).apply()
}
