package com.kcum.gallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kcum.gallery.data.AppDatabase
import com.kcum.gallery.data.HiddenItem

/**
 * ViewModel Album Peribadi - observe item tersembunyi dari Room.
 */
class HiddenViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).hiddenDao()

    val hiddenItems: LiveData<List<HiddenItem>> = dao.getAll().asLiveData()
}
