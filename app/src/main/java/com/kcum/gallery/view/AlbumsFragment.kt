package com.kcum.gallery.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.adapter.AlbumAdapter
import com.kcum.gallery.data.MediaRepository
import com.kcum.gallery.util.MediaStoreUtils
import com.kcum.gallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

/**
 * Skrin Album: semua folder ditunjukkan secara automatik (dikumpulkan dari MediaStore).
 * Tekan lama album -> Rename / Padam folder.
 */
class AlbumsFragment : Fragment() {

    private val viewModel: GalleryViewModel by activityViewModels()
    private lateinit var adapter: AlbumAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.albums_toolbar)
        // Guna inflateMenu terus (bukan setSupportActionBar) supaya listener berfungsi
        toolbar.title = getString(R.string.nav_albums)
        toolbar.inflateMenu(R.menu.menu_albums)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_new_folder) {
                showCreateFolderDialog()
                true
            } else false
        }

        recycler = view.findViewById(R.id.recycler_albums)
        emptyView = view.findViewById(R.id.albums_empty)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter = AlbumAdapter(
            onClick = { album ->
                // Buka kandungan album (GalleryFragment dalam mod bucket)
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        GalleryFragment.newInstance(album.bucketId, album.name)
                    )
                    .addToBackStack(null)
                    .commit()
            },
            onLongClick = { album -> showAlbumActionsDialog(album) }
        )
        recycler.adapter = adapter

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            adapter.submitList(albums)
            emptyView.isVisible = albums.isEmpty()
        }

        onPermissionGranted()
    }

    fun onPermissionGranted() {
        if (viewModel.activeBucketId != null || viewModel.albums.value.isNullOrEmpty()) {
            viewModel.activeBucketId = null
            viewModel.refresh()
        }
    }

    private fun showCreateFolderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        val etName = view.findViewById<EditText>(R.id.et_folder_name)
        val rbCustom = view.findViewById<android.widget.RadioButton>(R.id.rb_location_custom)
        // Dalam skrin album, guna lokasi lalai sahaja (custom via Galeri tab)
        rbCustom.isEnabled = false

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_folder_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.folder_name_error_empty, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val ok = MediaStoreUtils.createFolderInGallery(requireContext(), name)
                    if (ok) {
                        Toast.makeText(requireContext(), R.string.folder_created, Toast.LENGTH_SHORT).show()
                        viewModel.refresh()
                    } else {
                        Toast.makeText(requireContext(), R.string.folder_create_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Dialog tindakan album: Rename / Padam / Batal */
    private fun showAlbumActionsDialog(album: com.kcum.gallery.data.Album) {
        val options = arrayOf(
            getString(R.string.rename),
            getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(album.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> renameFolder(album)
                    1 -> deleteFolder(album)
                }
            }
            .show()
    }

    private fun renameFolder(album: com.kcum.gallery.data.Album) {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val etName = view.findViewById<EditText>(R.id.et_rename_name)
        etName.setText(album.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty() || newName == album.name) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        // Muat semua item dalam album, kemudian tukar RELATIVE_PATH masing-masing
                        val items = MediaRepository.get(requireContext())
                            .loadMedia(album.bucketId)
                        val oldPath = album.cover.relativePath
                        val ok = MediaStoreUtils.renameFolder(requireContext(), items, oldPath, newName)
                        if (ok) {
                            Toast.makeText(requireContext(), R.string.folder_renamed, Toast.LENGTH_SHORT).show()
                            viewModel.refresh()
                        } else {
                            Toast.makeText(requireContext(), R.string.folder_rename_failed, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: MediaStoreUtils.AccessDeniedException) {
                        Toast.makeText(requireContext(), R.string.access_denied, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFolder(album: com.kcum.gallery.data.Album) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.album_delete_title)
            .setMessage(getString(R.string.album_delete_confirm, album.count))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val repo = MediaRepository.get(requireContext())
                    val items = repo.loadMedia(album.bucketId)
                    val result = repo.moveToTrash(items)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.moved_to_trash, result.success),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
