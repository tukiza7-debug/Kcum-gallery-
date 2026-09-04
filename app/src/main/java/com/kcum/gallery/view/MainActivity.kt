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

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fragmentContainer: View

    private var galleryFragment: GalleryFragment? = null
    private var albumsFragment: AlbumsFragment? = null
    private var settingsFragment: SettingsFragment? = null

    companion object {
        var lastPausedAt: Long = 0L
        var unlockedThisSession: Boolean = false

        private const val TAB_PHOTOS = "photos"
        private const val TAB_ALBUMS = "albums"
        private const val TAB_SETTINGS = "settings"
    }

    private val pinLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                unlockedThisSession = true
            } else {
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

        if (savedInstanceState != null) {
            galleryFragment = supportFragmentManager.findFragmentByTag("photos") as? GalleryFragment
            albumsFragment = supportFragmentManager.findFragmentByTag("albums") as? AlbumsFragment
            settingsFragment = supportFragmentManager.findFragmentByTag("settings") as? SettingsFragment
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_photos -> { switchTab(TAB_PHOTOS); true }
                R.id.nav_albums -> { switchTab(TAB_ALBUMS); true }
                R.id.nav_settings -> { switchTab(TAB_SETTINGS); true }
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

    private fun enforceAppLock() {
        if (!prefs.hasPin()) return
        if (prefs.lockTimeout == PrefsRepository.LOCK_NEVER && unlockedThisSession) return

        val elapsed = System.currentTimeMillis() - lastPausedAt
        val needLock = when {
            lastPausedAt == 0L -> true
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

    private fun switchTab(tab: String) {
        val transaction = supportFragmentManager.beginTransaction()

        galleryFragment?.let { transaction.hide(it) }
        albumsFragment?.let { transaction.hide(it) }
        settingsFragment?.let { transaction.hide(it) }

        when (tab) {
            TAB_PHOTOS -> {
                val f = galleryFragment
                if (f == null) {
                    val newF = GalleryFragment.newInstance(null, null)
                    galleryFragment = newF
                    transaction.add(R.id.fragment_container, newF, TAB_PHOTOS)
                } else {
                    transaction.show(f)
                }
            }
            TAB_ALBUMS -> {
                val f = albumsFragment
                if (f == null) {
                    val newF = AlbumsFragment()
                    albumsFragment = newF
                    transaction.add(R.id.fragment_container, newF, TAB_ALBUMS)
                } else {
                    transaction.show(f)
                }
            }
            TAB_SETTINGS -> {
                val f = settingsFragment
                if (f == null) {
                    val newF = SettingsFragment()
                    settingsFragment = newF
                    transaction.add(R.id.fragment_container, newF, TAB_SETTINGS)
                } else {
                    transaction.show(f)
                }
            }
        }
        transaction.commit()
    }

    private fun refreshCurrentFragment() {
        galleryFragment?.onPermissionGranted()
        albumsFragment?.onPermissionGranted()
    }

    fun ensurePermissions() {
        if (PermissionUtils.hasStoragePermission(this)) {
            refreshCurrentFragment()
        } else {
            permissionLauncher.launch(PermissionUtils.requiredPermissions())
        }
    }
}
