package com.kcum.gallery.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.adapter.HiddenAdapter
import com.kcum.gallery.data.HiddenItem
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.viewmodel.HiddenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Album Peribadi (Hidden Album):
 * - Fail disimpan dalam storan dalaman app (filesDir/hidden) + metadata Room
 * - Akses dilindungi PIN/biometrik (dipanggil selepas PinActivity sah)
 * - Tindakan: pra-tonton, pulihkan (unhide), kongsi, padam kekal
 */
class HiddenActivity : AppCompatActivity() {

    private val viewModel: HiddenViewModel by viewModels()
    private lateinit var adapter: HiddenAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden)

        val toolbar = findViewById<Toolbar>(R.id.hidden_toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.hidden_title)

        val recycler = findViewById<RecyclerView>(R.id.recycler_hidden)
        val emptyView = findViewById<View>(R.id.hidden_empty)
        recycler.layoutManager = GridLayoutManager(this, 3)

        val repo = MediaRepository.get(applicationContext)
        adapter = HiddenAdapter(onClick = { item -> showItemDialog(item) })
        recycler.adapter = adapter

        viewModel.hiddenItems.observe(this) { items ->
            adapter.submitList(items)
            emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Dialog tindakan item tersembunyi: pra-tonton + unhide/kongsi/padam */
    private fun showItemDialog(item: HiddenItem) {
        val view = layoutInflater.inflate(R.layout.dialog_hidden_preview, null)
        val img = view.findViewById<ImageView>(R.id.img_preview)
        Glide.with(this).load(File(item.storedPath)).fitCenter().into(img)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(item.displayName)
            .setView(view)
            .setPositiveButton(R.string.unhide) { _, _ -> unhideItem(item) }
            .setNeutralButton(R.string.share) { _, _ -> shareItem(item) }
            .setNegativeButton(R.string.delete) { _, _ -> confirmDelete(item) }
            .show()

        // Padam juga bila dialog ditutup - buang rujukan bitmap
        dialog.setOnDismissListener { Glide.with(img).clear(img) }
    }

    /** Pulihkan ke galeri (lokasi asal) melalui MediaStore */
    private fun unhideItem(item: HiddenItem) {
        lifecycleScope.launch {
            val ok = MediaRepository.get(applicationContext).unhideItem(item)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@HiddenActivity,
                    if (ok) R.string.unhide_done else R.string.restore_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Kongsi melalui FileProvider (fail dalam storan dalaman) */
    private fun shareItem(item: HiddenItem) {
        try {
            val file = File(item.storedPath)
            val uri = FileProvider.getUriForFile(
                this, "com.kcum.gallery.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: HiddenItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_forever_title)
            .setMessage(getString(R.string.delete_forever_confirm, item.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    MediaRepository.get(applicationContext).deleteHiddenPermanently(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
