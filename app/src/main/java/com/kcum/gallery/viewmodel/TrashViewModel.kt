package com.kcum.gallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kcum.gallery.data.AppDatabase
import com.kcum.gallery.data.TrashItem

/**
 * ViewModel Tong Sampah - observe item dari Room secara reaktif (Flow -> LiveData).
 */
class TrashViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).trashDao()

    val trashItems: LiveData<List<TrashItem>> = dao.getAll().asLiveData()
}
