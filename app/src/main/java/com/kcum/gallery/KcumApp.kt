package com.kcum.gallery

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.data.PrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Kelas Application - guna tema tersimpan semasa app dimulakan
 * dan bersihkan tong sampah lapuk (lebih 30 hari) secara latar belakang.
 */
class KcumApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Guna mod tema gelap/terahang yang dipilih pengguna
        val prefs = PrefsRepository.get(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        // Auto-bersih item tong sampah lebih 30 hari
        CoroutineScope(Dispatchers.IO).launch {
            MediaRepository.get(this@KcumApp).purgeOldTrash()
        }
    }
}
