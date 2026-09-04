package com.kcum.gallery.view

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.PermissionUtils
import com.kcum.gallery.util.SecurityUtils
import com.kcum.gallery.viewmodel.SettingsViewModel

/**
 * Skrin Tetapan:
 * - Tema (gelap/terahang/sistem)
 * - Bahasa (BM/English/ikut sistem)
 * - Saiz grid (2/3/4 lajur)
 * - Tempoh slideshow
 * - Keselamatan: PIN, biometrik, timeout kunci app
 * - Album peribadi (dengan kunci)
 * - Statistik storan
 * - Akses penuh storan (MANAGE_EXTERNAL_STORAGE - dengan amaran risiko)
 */
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var prefs: PrefsRepository

    /** Launcher PIN untuk buka Album Peribadi (didafarkan awal - peraturan API) */
    private val pinLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                startActivity(Intent(requireContext(), HiddenActivity::class.java))
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsRepository.get(requireContext())

        bindRows(view)
        bindSecurityRows(view)

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats?.let { showStorageDialog(it) } ?: return@observe
            // Reset supaya boleh dibuka semula
            viewModel.clearStats()
        }

        view.findViewById<View>(R.id.row_storage).setOnClickListener {
            viewModel.loadStats()
        }
    }

    private fun bindRows(view: View) {
        // ---------- TEMA ----------
        view.findViewById<View>(R.id.row_theme).setOnClickListener {
            val options = arrayOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            val checked = when (prefs.themeMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    val mode = when (which) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    prefs.themeMode = mode
                    AppCompatDelegate.setDefaultNightMode(mode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ---------- BAHASA ----------
        view.findViewById<View>(R.id.row_language).setOnClickListener {
            val options = arrayOf(
                getString(R.string.lang_system),
                getString(R.string.lang_ms),
                getString(R.string.lang_en)
            )
            val checked = when (prefs.langTag) {
                "ms" -> 1
                "en" -> 2
                else -> 0
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    val tag = when (which) {
                        1 -> "ms"
                        2 -> "en"
                        else -> ""
                    }
                    prefs.langTag = tag
                    // Bahasa setiap aplikasi (appcompat 1.6+, berfungsi sejak API 24)
                    val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                    AppCompatDelegate.setApplicationLocales(locales)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ---------- SAIZ GRID ----------
        view.findViewById<View>(R.id.row_grid).setOnClickListener {
            val options = arrayOf("2", "3", "4")
            val checked = when (prefs.gridSpan) {
                2 -> 0
                4 -> 2
                else -> 1
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_grid_size)
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    prefs.gridSpan = which + 2
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ---------- SLIDESHOW ----------
        view.findViewById<View>(R.id.row_slideshow).setOnClickListener {
            val options = arrayOf("3", "5", "10")
            val checked = when (prefs.slideshowSeconds) {
                3 -> 0
                10 -> 2
                else -> 1
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_slideshow)
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    prefs.slideshowSeconds = intArrayOf(3, 5, 10)[which]
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ---------- TONG SAMPAH ----------
        view.findViewById<View>(R.id.row_trash).setOnClickListener {
            startActivity(Intent(requireContext(), TrashActivity::class.java))
        }

        // ---------- ALBUM PERIBADI ----------
        view.findViewById<View>(R.id.row_private_album).setOnClickListener {
            openPrivateAlbum()
        }

        // ---------- AKSES PENUH STORAN ----------
        view.findViewById<View>(R.id.row_manage_storage).setOnClickListener {
            showManageStorageDialog()
        }
    }

    private fun bindSecurityRows(view: View) {
        // ---------- PIN ----------
        val pinSubtitle = view.findViewById<TextView>(R.id.txt_pin_subtitle)
        pinSubtitle.text = if (prefs.hasPin()) getString(R.string.pin_change)
        else getString(R.string.pin_set)

        view.findViewById<View>(R.id.row_pin).setOnClickListener {
            val mode = if (prefs.hasPin()) PinActivity.MODE_CHANGE else PinActivity.MODE_SETUP
            val intent = Intent(requireContext(), PinActivity::class.java)
                .putExtra(PinActivity.EXTRA_MODE, mode)
            startActivity(intent)
        }

        // ---------- VIDEO ----------
        val switchVideoAutoplay = view.findViewById<Switch>(R.id.switch_video_autoplay)
        switchVideoAutoplay.isChecked = prefs.videoAutoplay
        switchVideoAutoplay.setOnCheckedChangeListener { _, checked ->
            prefs.videoAutoplay = checked
        }

        val switchVideoLoop = view.findViewById<Switch>(R.id.switch_video_loop)
        switchVideoLoop.isChecked = prefs.videoLoop
        switchVideoLoop.setOnCheckedChangeListener { _, checked ->
            prefs.videoLoop = checked
        }

        val switchVideoMuted = view.findViewById<Switch>(R.id.switch_video_muted)
        switchVideoMuted.isChecked = prefs.videoMuted
        switchVideoMuted.setOnCheckedChangeListener { _, checked ->
            prefs.videoMuted = checked
        }

        // ---------- BIOMETRIK ----------
        val switchBiometric = view.findViewById<Switch>(R.id.switch_biometric)
        switchBiometric.isChecked = prefs.biometricEnabled
        switchBiometric.isEnabled = SecurityUtils.canUseBiometric(requireContext())
        switchBiometric.setOnCheckedChangeListener { _, checked ->
            prefs.biometricEnabled = checked
        }

        // ---------- TIMEOUT KUNCI APP ----------
        view.findViewById<View>(R.id.row_lock_timeout).setOnClickListener {
            val options = arrayOf(
                getString(R.string.timeout_immediate),
                getString(R.string.timeout_1min),
                getString(R.string.timeout_5min),
                getString(R.string.timeout_never)
            )
            val checked = when (prefs.lockTimeout) {
                PrefsRepository.LOCK_1_MIN -> 1
                PrefsRepository.LOCK_5_MIN -> 2
                PrefsRepository.LOCK_NEVER -> 3
                else -> 0
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_lock_timeout)
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    prefs.lockTimeout = when (which) {
                        1 -> PrefsRepository.LOCK_1_MIN
                        2 -> PrefsRepository.LOCK_5_MIN
                        3 -> PrefsRepository.LOCK_NEVER
                        else -> PrefsRepository.LOCK_IMMEDIATE
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun openPrivateAlbum() {
        val intent = Intent(requireContext(), PinActivity::class.java)
            .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY)
        pinLauncher.launch(intent)
    }

    /**
     * Dialog AKSES PENUH STORAN (MANAGE_EXTERNAL_STORAGE).
     *
     * RISIKO (ringkasan, lihat juga komen penuh dalam AndroidManifest):
     * 1. Privasi - app akan dapat baca & tulis SEMUA fail kongsi pengguna.
     * 2. Google Play - app galeri biasa biasanya DITOLAK; hanya app seperti
     *    pengurus fail/antivirus dibenarkan dengan justifikasi.
     * 3. Kepercayaan pengguna menurun.
     * Gunakan HANYA jika anda perlu urus folder di luar Pictures/Movies pada
     * Android 11+. Kebanyakan fungsi app ini tidak memerlukannya.
     */
    private fun showManageStorageDialog() {
        val granted = PermissionUtils.hasFullStorageAccess()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_full_access)
            .setMessage(
                getString(
                    R.string.full_access_risk_msg,
                    getString(if (granted) R.string.full_access_status_on else R.string.full_access_status_off)
                )
            )
            .setPositiveButton(R.string.full_access_open) { _, _ ->
                PermissionUtils.requestFullStorageAccess(requireContext())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Dialog statistik storan: jumlah gambar/video, saiz, ruang kosong */
    private fun showStorageDialog(stats: com.kcum.gallery.data.StorageStats) {
        val message = buildString {
            append(getString(R.string.storage_images, stats.imageCount)).append('\n')
            append(getString(R.string.storage_videos, stats.videoCount)).append('\n')
            append(getString(R.string.storage_total, Formats.fileSize(stats.totalBytes))).append('\n')
            append(getString(R.string.storage_free, Formats.fileSize(stats.freeBytes))).append('\n')
            append(getString(R.string.storage_trash, stats.trashCount)).append('\n')
            append(getString(R.string.storage_hidden, stats.hiddenCount))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_storage_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
