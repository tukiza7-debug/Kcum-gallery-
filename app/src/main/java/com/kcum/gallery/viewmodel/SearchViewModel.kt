package com.kcum.gallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.MediaRepository
import kotlinx.coroutines.launch

/**
 * ViewModel carian & tapisan:
 * - Nama fail (sebahagian, tak peka huruf besar/kecil)
 * - Julat tarikh (dari / hingga)
 * - Jenis (semua/gambar/video)
 * - Saiz fail (<1MB, 1-10MB, >10MB)
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository.get(app)

    private val allMedia = MutableLiveData<List<MediaItem>>(emptyList())
    val results = MutableLiveData<List<MediaItem>>(emptyList())

    // Keadaan tapisan semasa
    var nameQuery: String = ""
    var dateFrom: Long? = null   // milisaat
    var dateTo: Long? = null     // milisaat
    var type: String = GalleryViewModel.TYPE_ALL
    var sizeFilter: String = SIZE_ALL

    companion object {
        const val SIZE_ALL = "all"
        const val SIZE_SMALL = "small"   // < 1 MB
        const val SIZE_MEDIUM = "medium" // 1 - 10 MB
        const val SIZE_LARGE = "large"   // > 10 MB
    }

    /** Muat semua media sekali sahaja semasa skrin dibuka */
    fun loadAll() {
        viewModelScope.launch {
            allMedia.value = repo.loadMedia(null)
            applyFilters()
        }
    }

    /** Guna semua tapisan dan keluarkan hasil */
    fun applyFilters() {
        val source = allMedia.value ?: emptyList()
        val filtered = source.filter { item ->
            // 1. Nama fail
            val matchName = nameQuery.isBlank() ||
                item.name.contains(nameQuery.trim(), ignoreCase = true)

            // 2. Julat tarikh
            val matchDateFrom = dateFrom == null || item.dateMs >= (dateFrom ?: 0)
            val matchDateTo = dateTo == null || item.dateMs <= ((dateTo ?: Long.MAX_VALUE) + 86_399_999)

            // 3. Jenis
            val matchType = when (type) {
                GalleryViewModel.TYPE_IMAGE -> !item.isVideo
                GalleryViewModel.TYPE_VIDEO -> item.isVideo
                else -> true
            }

            // 4. Saiz fail
            val matchSize = when (sizeFilter) {
                SIZE_SMALL -> item.size < 1_000_000
                SIZE_MEDIUM -> item.size in 1_000_000..10_000_000
                SIZE_LARGE -> item.size > 10_000_000
                else -> true
            }

            matchName && matchDateFrom && matchDateTo && matchType && matchSize
        }
        results.value = repo.sortItems(filtered, "date", false)
    }
}
