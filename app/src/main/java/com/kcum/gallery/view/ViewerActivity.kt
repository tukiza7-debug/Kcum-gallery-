package com.kcum.gallery.view

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.kcum.gallery.R
import com.kcum.gallery.adapter.ViewerPagerAdapter
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.shareUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Penonton skrin penuh:
 * - Leret (swipe) antara media + zum cubit / dwi-ketuk
 * - Video: main/jeda, cari (seek), butang senyap
 * - Slideshow automatik (tempoh dari Tetapan)
 * - Kongsi, Edit, Padam (ke tong sampah), Tetap sebagai wallpaper,
 *   Tetap sebagai foto kenalan, Eksport salinan (SAF), Info fail
 *
 * NOTA: senarai item dihantar melalui companion `items` (cache statik) untuk
 * elak TransactionTooLargeException apabila galeri besar.
 */
class ViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_POSITION = "extra_position"

        /** Cache senarai media semasa - ditetapkan sebelum startActivity() */
        var items: List<MediaItem> = emptyList()

        fun start(context: android.content.Context, list: List<MediaItem>, position: Int) {
            items = list
            context.startActivity(
                Intent(context, ViewerActivity::class.java).putExtra(EXTRA_POSITION, position)
            )
        }
    }

    private lateinit var prefs: PrefsRepository
    private lateinit var pager: ViewPager2
    private lateinit var adapter: ViewerPagerAdapter
    private lateinit var topBar: View
    private lateinit var txtName: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtSlideshow: TextView
    private lateinit var videoControls: View
    private lateinit var btnPlay: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtTime: TextView

    private var currentList: List<MediaItem> = emptyList()
    private var activePlayer: ExoPlayer? = null
    private var slideshowRunning = false
    private val slideshowHandler = Handler(Looper.getMainLooper())
    private val progressHandler = Handler(Looper.getMainLooper())

    /** Kemas kini seek bar video setiap 500ms */
    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = activePlayer
            if (player != null) {
                seekBar.max = (player.duration.coerceAtLeast(0)).toInt()
                seekBar.progress = player.currentPosition.coerceAtLeast(0).toInt()
                txtTime.text = Formats.duration(player.currentPosition)
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { exportTo(it) }
        }

    private val trashLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingDeleteItem?.let { performDelete(it) }
            }
            pendingDeleteItem = null
        }

    private var pendingDeleteItem: MediaItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Skrin penuh gelap
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        setContentView(R.layout.activity_viewer)

        prefs = PrefsRepository.get(this)
        currentList = items

        pager = findViewById(R.id.view_pager)
        topBar = findViewById(R.id.viewer_top_bar)
        txtName = findViewById(R.id.txt_media_name)
        txtDate = findViewById(R.id.txt_media_date)
        txtSlideshow = findViewById(R.id.txt_slideshow)
        videoControls = findViewById(R.id.video_controls)
        btnPlay = findViewById(R.id.btn_play)
        btnMute = findViewById(R.id.btn_mute)
        seekBar = findViewById(R.id.seek_video)
        txtTime = findViewById(R.id.txt_time)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu)

        adapter = ViewerPagerAdapter(
            currentList,
            onImageTap = { toggleControls() },
            onPlayerReady = { player -> bindPlayer(player) }
        )
        pager.adapter = adapter

        val startPos = intent.getIntExtra(EXTRA_POSITION, 0).coerceIn(0, (currentList.size - 1).coerceAtLeast(0))
        pager.setCurrentItem(startPos, false)
        pager.registerOnPageChangeCallback(pageCallback)

        btnBack.setOnClickListener { finish() }
        btnMenu.setOnClickListener { v -> showMenu(v) }

        btnPlay.setOnClickListener { togglePlayPause() }
        btnMute.setOnClickListener { toggleMute() }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) activePlayer?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        updateHeader(startPos)
        updateForItem(startPos)
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            updateHeader(position)
            updateForItem(position)
            // Hentikan video halaman lain secara tidak langsung (holder dikitar semula)
        }
    }

    private fun updateHeader(position: Int) {
        val item = currentList.getOrNull(position) ?: return
        txtName.text = item.name
        txtDate.text = Formats.date(this, item.dateMs)
    }

    /** Sesuaikan kawalan video & jeda slideshow bergantung pada jenis item */
    private fun updateForItem(position: Int) {
        val item = currentList.getOrNull(position) ?: return
        videoControls.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        if (!item.isVideo) {
            bindPlayer(null)
        }
    }

    /** Sambungkan kawalan UI ke ExoPlayer halaman semasa (atau null) */
    private fun bindPlayer(player: ExoPlayer?) {
        activePlayer = player
        if (player != null) {
            btnPlay.setImageResource(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )
            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.post(progressRunnable)
        } else {
            progressHandler.removeCallbacks(progressRunnable)
        }
    }

    private fun togglePlayPause() {
        val player = activePlayer ?: return
        if (player.isPlaying) {
            player.pause()
            btnPlay.setImageResource(R.drawable.ic_play_arrow)
        } else {
            player.play()
            btnPlay.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun toggleMute() {
        val player = activePlayer ?: return
        val newVolume = if (player.volume > 0f) 0f else 1f
        player.volume = newVolume
        btnMute.setImageResource(
            if (newVolume == 0f) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
    }

    /** Tunjuk/sorok bar atas & kawalan video */
    private fun toggleControls() {
        topBar.visibility = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (currentList.getOrNull(pager.currentItem)?.isVideo == true) {
            videoControls.visibility =
                if (videoControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    // =====================================================================
    // MENU POPUP: SEMUA TINDAKAN
    // =====================================================================

    private fun showMenu(anchor: View) {
        val item = currentList.getOrNull(pager.currentItem) ?: return
        val menu = PopupMenu(this, anchor)
        menu.menuInflater.inflate(R.menu.menu_viewer, menu.menu)
        menu.menu.findItem(R.id.action_slideshow).isChecked = slideshowRunning
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_slideshow -> { toggleSlideshow(); true }
                R.id.action_edit -> { openEditor(item); true }
                R.id.action_share -> { shareUri(item.uri, item.mimeType); true }
                R.id.action_wallpaper -> { setAsWallpaper(item); true }
                R.id.action_contact -> { setAsContactPhoto(item); true }
                R.id.action_export -> { exportCopy(item); true }
                R.id.action_info -> { showInfo(item); true }
                R.id.action_delete -> { confirmDelete(item); true }
                else -> false
            }
        }
        menu.show()
    }

    // =====================================================================
    // SLIDESHOW
    // =====================================================================

    private fun toggleSlideshow() {
        if (slideshowRunning) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        slideshowRunning = true
        txtSlideshow.visibility = View.VISIBLE
        topBar.visibility = View.GONE
        val intervalMs = prefs.slideshowSeconds * 1000L
        slideshowHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!slideshowRunning) return
                val next = pager.currentItem + 1
                if (next >= currentList.size) {
                    // Gelung semula ke permulaan
                    pager.setCurrentItem(0, true)
                } else {
                    pager.setCurrentItem(next, true)
                }
                slideshowHandler.postDelayed(this, intervalMs)
            }
        }, intervalMs)
        Toast.makeText(this, R.string.slideshow_on, Toast.LENGTH_SHORT).show()
    }

    private fun stopSlideshow() {
        slideshowRunning = false
        slideshowHandler.removeCallbacksAndMessages(null)
        txtSlideshow.visibility = View.GONE
        topBar.visibility = View.VISIBLE
        Toast.makeText(this, R.string.slideshow_off, Toast.LENGTH_SHORT).show()
    }

    // =====================================================================
    // TINDAKAN
    // =====================================================================

    private fun openEditor(item: MediaItem) {
        startActivity(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_URI, item.uri.toString())
        )
    }

    /** Tetap sebagai wallpaper (diperlukan bitmap) */
    private fun setAsWallpaper(item: MediaItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(item.uri)
                    .submit(1080, 1920)
                    .get()
                val manager = WallpaperManager.getInstance(applicationContext)
                manager.setBitmap(bitmap)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewerActivity, R.string.wallpaper_set, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewerActivity, R.string.wallpaper_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Tetap sebagai foto kenalan: guna ACTION_ATTACH_DATA supaya sistem
     * memaparkan dialog pilih aplikasi kenalan kepada pengguna.
     */
    private fun setAsContactPhoto(item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.contact_set_fail, Toast.LENGTH_SHORT).show()
        }
    }

    /** Eksport salinan ke lokasi lain melalui SAF (ACTION_CREATE_DOCUMENT) */
    private fun exportCopy(item: MediaItem) {
        pendingExportItem = item
        exportLauncher.launch(item.name)
    }

    private var pendingExportItem: MediaItem? = null

    private fun exportTo(destination: Uri) {
        val item = pendingExportItem ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                contentResolver.openInputStream(item.uri)?.use { input ->
                    contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    }
                } != null
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ViewerActivity,
                    if (ok) R.string.export_success else R.string.export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showInfo(item: MediaItem) {
        val message = buildString {
            append(getString(R.string.info_name, item.name)).append('\n')
            append(getString(R.string.info_type, item.mimeType)).append('\n')
            append(getString(R.string.info_size, Formats.fileSize(item.size))).append('\n')
            append(
                getString(
                    R.string.info_dimensions,
                    "${item.width} × ${item.height}"
                )
            ).append('\n')
            append(getString(R.string.info_date, Formats.date(this@ViewerActivity, item.dateMs))).append('\n')
            append(getString(R.string.info_path, item.relativePath + item.name))
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.viewer_info_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun confirmDelete(item: MediaItem) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.trash_confirm_title)
            .setMessage(getString(R.string.trash_confirm_msg, 1))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val repo = MediaRepository.get(applicationContext)
                    val result = repo.moveToTrash(listOf(item))
                    if (result.failed > 0 && result.intentSender != null) {
                        pendingDeleteItem = item
                        trashLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build()
                        )
                        return@launch
                    }
                    performDelete(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Buang item dari senarai penonton selepas berjaya dipadam */
    private fun performDelete(item: MediaItem) {
        currentList = currentList.filter { it.uri != item.uri }
        items = currentList
        if (currentList.isEmpty()) {
            finish()
            return
        }
        adapter.submitList(currentList)
        Toast.makeText(this, getString(R.string.moved_to_trash, 1), Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        activePlayer?.pause()
        if (slideshowRunning) stopSlideshow()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacksAndMessages(null)
        slideshowHandler.removeCallbacksAndMessages(null)
        pager.unregisterOnPageChangeCallback(pageCallback)
        activePlayer?.release()
        activePlayer = null
    }
}
