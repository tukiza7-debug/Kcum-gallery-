package com.kcum.gallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.data.StorageStats
import kotlinx.coroutines.launch

/**
 * ViewModel Tetapan - menyediakan statistik penggunaan storan.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository.get(app)

    private val _stats = MutableLiveData<StorageStats?>()
    val stats: LiveData<StorageStats?> = _stats

    /** Kira statistik (jumlah gambar/video, saiz, ruang kosong) */
    fun loadStats() {
        viewModelScope.launch {
            _stats.value = repo.storageStats()
        }
    }
}
