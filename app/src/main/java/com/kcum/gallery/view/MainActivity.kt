package com.kcum.gallery.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.util.PermissionUtils

/**
 * Aktiviti utama: hos 3 tab (Gambar / Album / Tetapan) melalui BottomNavigationView
 * + sistem Kunci App (PIN / biometrik dengan timeout).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fragmentContainer: View

    // ---- Sistem kunci app ----
    companion object {
        /** Masa app terakhir ke background (untuk kira timeout kunci) */
        var lastPausedAt: Long = 0L
        /** Sudah dibuka untuk sesi semasa (elak minta PIN berulang) */
        var unlockedThisSession: Boolean = false
    }

    private val pinLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                unlockedThisSession = true
            } else {
                // Gagal buka - tutup app
                finishAffinity()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (PermissionUtils.hasStoragePermission(this)) {
                refreshCurrentFragment()
            } else {
                val denied = grants.filterValues { !it }.keys.toList()
                if (!PermissionUtils.canAskAgain(this, denied)) {
                    // Ditolak kekal -> arah ke Settings (seperti keperluan spesifikasi)
                    showSettingsRedirectDialog()
                } else {
                    Toast.makeText(
                        this, R.string.permission_denied_toast, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsRepository.get(this)
        setContentView(R.layout.activity_main)

        fragmentContainer = findViewById(R.id.fragment_container)
        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_photos -> { showFragment(GalleryFragment.newInstance(null, null)); true }
                R.id.nav_albums -> { showFragment(AlbumsFragment()); true }
                R.id.nav_settings -> { showFragment(SettingsFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_photos
        }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        enforceAppLock()
    }

    override fun onPause() {
        super.onPause()
        lastPausedAt = System.currentTimeMillis()
    }

    /** Kunci app jika PIN aktif dan timeout dicapai */
    private fun enforceAppLock() {
        if (!prefs.hasPin()) return
        if (prefs.lockTimeout == PrefsRepository.LOCK_NEVER && unlockedThisSession) return

        val elapsed = System.currentTimeMillis() - lastPausedAt
        val needLock = when {
            lastPausedAt == 0L -> true // proses baru dibuka
            prefs.lockTimeout == PrefsRepository.LOCK_NEVER -> false
            else -> elapsed >= prefs.lockTimeout
        }
        if (needLock && !unlockedThisSession) {
            val intent = Intent(this, PinActivity::class.java)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY)
            pinLauncher.launch(intent)
        }
    }

    private fun showSettingsRedirectDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.permission_rationale_msg)
            .setPositiveButton(R.string.permission_open_settings) { _, _ ->
                PermissionUtils.openAppSettings(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun refreshCurrentFragment() {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        (current as? GalleryFragment)?.onPermissionGranted()
        (current as? AlbumsFragment)?.onPermissionGranted()
    }

    /** Minta kebenaran runtime mengikut versi OS (dipanggil dari fragment juga) */
    fun ensurePermissions() {
        if (PermissionUtils.hasStoragePermission(this)) {
            refreshCurrentFragment()
        } else {
            permissionLauncher.launch(PermissionUtils.requiredPermissions())
        }
    }
}
