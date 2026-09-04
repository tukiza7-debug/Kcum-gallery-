package com.kcum.gallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kcum.gallery.data.Album
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.data.TimelineRow
import kotlinx.coroutines.launch

/**
 * ViewModel utama galeri (skop Activity - dikongsi antara tab).
 * Menyimpan senarai media, album, timeline + keadaan susunan/tapisan.
 */
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository.get(app)
    val prefs = PrefsRepository.get(app)

    val media = MutableLiveData<List<MediaItem>>(emptyList())
    val albums = MutableLiveData<List<Album>>(emptyList())
    val timeline = MutableLiveData<List<TimelineRow>>(emptyList())
    val loading = MutableLiveData(false)

    /** Bucket aktif (null = semua gambar; tidak null = mod album detail) */
    var activeBucketId: String? = null

    /** Tapisan jenis: TYPE_ALL, TYPE_IMAGE, TYPE_VIDEO */
    var typeFilter: String = TYPE_ALL
        private set

    companion object {
        const val TYPE_ALL = "all"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    /** Muat semula data daripada MediaStore dan guna susunan + tapisan terkini */
    fun refresh() {
        refreshJob?.cancel()
        loading.value = true
        refreshJob = viewModelScope.launch {
            val all = repo.loadMedia(activeBucketId)
            publish(all)
            loading.value = false
        }
    }

    private suspend fun publish(all: List<MediaItem>) {
        val filtered = when (typeFilter) {
            TYPE_IMAGE -> all.filter { !it.isVideo }
            TYPE_VIDEO -> all.filter { it.isVideo }
            else -> all
        }
        val sorted = repo.sortItems(filtered, prefs.sortBy, prefs.sortAsc)
        // Post timeline & albums DAHULU, media kemudian - supaya apabila pemerhati
        // media berjalan, timeline sudah dikemas kini (elak race condition).
        timeline.postValue(repo.buildTimeline(sorted))
        if (activeBucketId == null) {
            albums.postValue(repo.groupIntoAlbums(sorted))
        }
        media.postValue(sorted)
    }

    /** Tukar tapisan jenis dan muat semula */
    fun setTypeFilter(type: String) {
        typeFilter = type
        refresh()
    }

    /** Kemas kini keutamaan susunan pengguna (disimpan ke prefs) */
    fun setSort(sortBy: String, asc: Boolean) {
        prefs.sortBy = sortBy
        prefs.sortAsc = asc
        media.value?.let { current ->
            media.postValue(repo.sortItems(current, sortBy, asc))
        }
    }

    /** Buang item dari senarai dalam memori selepas operasi padam/sembunyi */
    fun removeItems(removed: List<MediaItem>) {
        val removedUris = removed.map { it.uri }.toHashSet()
        media.value = media.value?.filter { it.uri !in removedUris }
        timeline.value = timeline.value?.filterIsInstance<TimelineRow>()
            ?.filter { row ->
                row !is TimelineRow.Media || row.item.uri !in removedUris
            }
    }
}
