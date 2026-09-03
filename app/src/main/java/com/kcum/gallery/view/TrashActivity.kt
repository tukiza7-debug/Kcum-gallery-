package com.kcum.gallery.view

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.adapter.TrashAdapter
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.data.TrashItem
import com.kcum.gallery.viewmodel.TrashViewModel
import kotlinx.coroutines.launch

/**
 * Tong Sampah (Recycle Bin):
 * - Item dipadam dialih ke sini selama 30 hari
 * - Pulihkan ke lokasi asal melalui MediaStore
 * - Padam kekal / kosongkan tong sampah
 */
class TrashActivity : AppCompatActivity() {

    private val viewModel: TrashViewModel by viewModels()
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        val toolbar = findViewById<Toolbar>(R.id.trash_toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.trash_title)
        toolbar.inflateMenu(R.menu.menu_trash)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_empty_trash) {
                confirmEmptyTrash()
                true
            } else false
        }

        val recycler = findViewById<RecyclerView>(R.id.recycler_trash)
        val emptyView = findViewById<View>(R.id.trash_empty)
        recycler.layoutManager = LinearLayoutManager(this)

        val repo = MediaRepository.get(applicationContext)
        adapter = TrashAdapter(
            onRestore = { item -> restoreItem(item) },
            onDeleteForever = { item -> confirmDeleteForever(item) }
        )
        recycler.adapter = adapter

        viewModel.trashItems.observe(this) { items ->
            adapter.submitList(items)
            emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Pulihkan item ke lokasi asal melalui MediaStore insert */
    private fun restoreItem(item: TrashItem) {
        lifecycleScope.launch {
            val ok = MediaRepository.get(applicationContext).restoreFromTrash(item)
            Toast.makeText(
                this@TrashActivity,
                if (ok) R.string.restore_done else R.string.restore_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDeleteForever(item: TrashItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_forever_title)
            .setMessage(getString(R.string.delete_forever_confirm, item.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    MediaRepository.get(applicationContext).deleteTrashPermanently(item)
                    Toast.makeText(this@TrashActivity, R.string.deleted_permanently, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmEmptyTrash() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.empty_trash)
            .setMessage(R.string.empty_trash_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    MediaRepository.get(applicationContext).emptyTrash()
                    Toast.makeText(this@TrashActivity, R.string.trash_emptied, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
