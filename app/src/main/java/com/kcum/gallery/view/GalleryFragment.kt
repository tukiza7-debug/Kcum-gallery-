package com.kcum.gallery.view

import android.Manifest
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kcum.gallery.R
import com.kcum.gallery.adapter.MediaAdapter
import com.kcum.gallery.adapter.TimelineAdapter
import com.kcum.gallery.data.Album
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.data.TimelineRow
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.MediaStoreUtils
import com.kcum.gallery.util.shareUris
import com.kcum.gallery.util.visibleOr
import com.kcum.gallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

/**
 * Skrin galeri utama.
 * Menyokong:
 * - Mod paparan: GRID / SENARAI / TIMELINE (dengan toggle)
 * - Susunan ikut tarikh/saiz/nama/jenis (keutamaan disimpan)
 * - Tapisan jenis (gambar/video) + carian
 * - Album mode (args bucketId != null)
 * - Pemilihan berbilang + operasi: kongsi, pindah, salin, padam(ke tong sampah),
 *   sembunyi, rename
 * - Cipta folder baru (dialog + MediaStore/SAF)
 */
class GalleryFragment : Fragment() {

    companion object {
        private const val ARG_BUCKET_ID = "arg_bucket_id"
        private const val ARG_BUCKET_NAME = "arg_bucket_name"

        fun newInstance(bucketId: String?, bucketName: String?): GalleryFragment {
            return GalleryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BUCKET_ID, bucketId)
                    putString(ARG_BUCKET_NAME, bucketName)
                }
            }
        }
    }

    private val viewModel: GalleryViewModel by activityViewModels()
    private lateinit var prefs: PrefsRepository

    private lateinit var toolbar: Toolbar
    private lateinit var selectionToolbar: Toolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var selectionBar: ViewGroup
    private lateinit var txtSelectionCount: TextView

    private lateinit var mediaAdapter: MediaAdapter
    private var timelineAdapter: TimelineAdapter? = null

    private var bucketId: String? = null
    private var bucketName: String? = null

    /** Pilihan paparan semasa (grid/list/timeline) */
    private var viewMode: String = PrefsRepository.VIEW_GRID

    /** Operasi tertunda menunggu kebenaran sistem (RecoverableSecurityException) */
    private var pendingRetry: (() -> Unit)? = null

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
                pendingRetry?.invoke()
            }
            pendingRetry = null
        }

    private val pinLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                startActivity(android.content.Intent(requireContext(), HiddenActivity::class.java))
            }
        }

    private val folderTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            treeUri?.let { uri ->
                // Kekalkan kebenaran kekal untuk pohon dokumen ini
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                pendingFolderName?.let { name -> createFolderInSafTree(uri, name) }
                pendingFolderName = null
            }
        }

    private var pendingFolderName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bucketId = arguments?.getString(ARG_BUCKET_ID)
        bucketName = arguments?.getString(ARG_BUCKET_NAME)
        prefs = PrefsRepository.get(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.gallery_toolbar)
        selectionToolbar = view.findViewById(R.id.selection_toolbar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        recycler = view.findViewById(R.id.recycler_gallery)
        emptyView = view.findViewById(R.id.empty_view)
        selectionBar = view.findViewById(R.id.selection_bar)
        txtSelectionCount = view.findViewById(R.id.txt_selection_count)

        viewMode = if (bucketId == null) prefs.viewMode else PrefsRepository.VIEW_GRID

        setupToolbars()
        setupSelectionBar()
        setupAdapter()

        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        view.findViewById<View>(R.id.btn_grant).setOnClickListener {
            (activity as? MainActivity)?.ensurePermissions()
        }

        observeViewModel()
        onPermissionGranted()
    }

    private fun setupToolbars() {
        if (bucketId != null) {
            // Mod album: tajuk nama folder + butang kembali
            toolbar.title = bucketName
            toolbar.navigationIcon =
                androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        }
        // NOTA: GUNA inflateMenu terus (JANGAN setSupportActionBar) supaya
        // pendengar setOnMenuItemClickListener di toolbar berfungsi.
        toolbar.inflateMenu(R.menu.menu_gallery)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> { openSearch(); true }
                R.id.action_sort -> { showSortDialog(); true }
                R.id.action_view -> { showViewModeDialog(); true }
                R.id.action_select -> { enterSelectViaMenu(); true }
                R.id.action_new_folder -> { showCreateFolderDialog(); true }
                R.id.action_private -> { openPrivateAlbum(); true }
                else -> false
            }
        }
        selectionToolbar.inflateMenu(R.menu.menu_selection)
        selectionToolbar.setNavigationIcon(R.drawable.ic_close)
        selectionToolbar.setNavigationOnClickListener { exitSelection() }
        selectionToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_done) { exitSelection(); true } else false
        }
    }

    private fun setupSelectionBar() {
        bindAction(R.id.btn_sel_share) { shareSelected() }
        bindAction(R.id.btn_sel_move) { moveSelected() }
        bindAction(R.id.btn_sel_copy) { copySelected() }
        bindAction(R.id.btn_sel_trash) { trashSelected() }
        bindAction(R.id.btn_sel_hide) { hideSelected() }
        bindAction(R.id.btn_sel_rename) { renameSelected() }
    }

    private fun bindAction(id: Int, action: () -> Unit) {
        selectionBar.findViewById<ImageButton>(id)?.setOnClickListener { action() }
    }

    private fun setupAdapter() {
        mediaAdapter = MediaAdapter(
            MediaAdapter.MODE_GRID,
            onItemClick = { position -> handleItemClick(position) },
            onItemLongClick = { _ -> updateSelectionUi() }
        )
        applyAdapterForMode()
    }

    private fun applyAdapterForMode() {
        when (viewMode) {
            PrefsRepository.VIEW_GRID -> {
                mediaAdapter = MediaAdapter(
                    MediaAdapter.MODE_GRID,
                    { position -> handleItemClick(position) },
                    { _ -> updateSelectionUi() }
                )
                recycler.layoutManager = GridLayoutManager(requireContext(), prefs.gridSpan)
                recycler.adapter = mediaAdapter
            }
            PrefsRepository.VIEW_LIST -> {
                mediaAdapter = MediaAdapter(
                    MediaAdapter.MODE_LIST,
                    { position -> handleItemClick(position) },
                    { _ -> updateSelectionUi() }
                )
                recycler.layoutManager = LinearLayoutManager(requireContext())
                recycler.adapter = mediaAdapter
            }
            PrefsRepository.VIEW_TIMELINE -> {
                timelineAdapter = TimelineAdapter(
                    emptyList(),
                    onMediaClick = { _, index -> openViewer(index) },
                    originalList = { viewModel.media.value ?: emptyList() }
                )
                recycler.layoutManager = LinearLayoutManager(requireContext())
                recycler.adapter = timelineAdapter
            }
        }
        publishMedia()
    }

    private fun observeViewModel() {
        viewModel.media.observe(viewLifecycleOwner) { publishMedia() }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            swipeRefresh.isRefreshing = loading == true
        }
    }

    private fun publishMedia() {
        if (viewMode == PrefsRepository.VIEW_TIMELINE) {
            timelineAdapter?.submitRows(viewModel.timeline.value ?: emptyList())
        } else {
            mediaAdapter.submitList(viewModel.media.value ?: emptyList())
        }
        val isEmpty = (viewModel.media.value ?: emptyList()).isEmpty()
        emptyView.isVisible = isEmpty
        recycler.isVisible = !isEmpty
    }

    /** Dipanggil apabila kebenaran storan sudah ada (atau selepas diberikan) */
    fun onPermissionGranted() {
        // Segar semula jika bucket berubah (mod album vs semua gambar)
        if (viewModel.activeBucketId != bucketId || viewModel.media.value.isNullOrEmpty()) {
            viewModel.activeBucketId = bucketId
            viewModel.refresh()
        }
    }

    private fun handleItemClick(position: Int) {
        if (mediaAdapter.selectionMode) {
            updateSelectionUi()
        } else {
            openViewer(position)
        }
    }

    private fun openViewer(position: Int) {
        val items = viewModel.media.value ?: return
        if (position !in items.indices) return
        ViewerActivity.start(requireContext(), items, position)
    }

    // =====================================================================
    // MENU: CARIAN, SUSUNAN, PAPARAN
    // =====================================================================

    private fun openSearch() {
        startActivity(android.content.Intent(requireContext(), SearchActivity::class.java))
    }

    private fun showSortDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_sort, null)
        val rgSort = view.findViewById<android.widget.RadioGroup>(R.id.rg_sort)
        val rgOrder = view.findViewById<android.widget.RadioGroup>(R.id.rg_order)
        when (prefs.sortBy) {
            PrefsRepository.SORT_NAME -> rgSort.check(R.id.rb_sort_name)
            PrefsRepository.SORT_SIZE -> rgSort.check(R.id.rb_sort_size)
            PrefsRepository.SORT_TYPE -> rgSort.check(R.id.rb_sort_type)
            else -> rgSort.check(R.id.rb_sort_date)
        }
        rgOrder.check(if (prefs.sortAsc) R.id.rb_sort_asc else R.id.rb_sort_desc)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val sortBy = when (rgSort.checkedRadioButtonId) {
                    R.id.rb_sort_name -> PrefsRepository.SORT_NAME
                    R.id.rb_sort_size -> PrefsRepository.SORT_SIZE
                    R.id.rb_sort_type -> PrefsRepository.SORT_TYPE
                    else -> PrefsRepository.SORT_DATE
                }
                val asc = rgOrder.checkedRadioButtonId == R.id.rb_sort_asc
                viewModel.setSort(sortBy, asc)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showViewModeDialog() {
        val options = arrayOf(
            getString(R.string.view_grid),
            getString(R.string.view_list),
            getString(R.string.view_timeline)
        )
        val checked = when (viewMode) {
            PrefsRepository.VIEW_LIST -> 1
            PrefsRepository.VIEW_TIMELINE -> 2
            else -> 0
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.view_mode_title)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                viewMode = when (which) {
                    1 -> PrefsRepository.VIEW_LIST
                    2 -> PrefsRepository.VIEW_TIMELINE
                    else -> PrefsRepository.VIEW_GRID
                }
                if (bucketId == null) prefs.viewMode = viewMode
                applyAdapterForMode()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // =====================================================================
    // CIPTA FOLDER (MediaStore + SAF custom location)
    // =====================================================================

    private fun showCreateFolderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        val etName = view.findViewById<EditText>(R.id.et_folder_name)
        val rbDefault = view.findViewById<android.widget.RadioButton>(R.id.rb_location_default)
        val rbCustom = view.findViewById<android.widget.RadioButton>(R.id.rb_location_custom)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_folder_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                val error = validateFolderName(name)
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    showCreateFolderDialog() // papar dialog semula
                    return@setPositiveButton
                }
                if (rbCustom.isChecked) {
                    // Lokasi custom melalui SAF (pengguna pilih pohon direktori)
                    pendingFolderName = name
                    folderTreeLauncher.launch(null)
                } else {
                    // Lokasi lalai: Pictures/<nama> melalui MediaStore
                    createFolderInGallery(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Validasi nama folder: tidak kosong, tiada simbol tidak sah.
     * Simbol tidak sah untuk nama fail Android: \ / : * ? " < > |
     */
    private fun validateFolderName(name: String): String? {
        if (name.isEmpty()) return getString(R.string.folder_name_error_empty)
        if (name == "." || name == "..") return getString(R.string.folder_name_error_invalid)
        val invalidChars = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        if (name.any { it in invalidChars }) {
            return getString(R.string.folder_name_error_invalid)
        }
        if (name.startsWith(".")) return getString(R.string.folder_name_error_invalid)
        return null
    }

    private fun createFolderInGallery(name: String) {
        lifecycleScope.launch {
            val ok = MediaStoreUtils.createFolderInGallery(requireContext(), name)
            if (ok) {
                Toast.makeText(requireContext(), R.string.folder_created, Toast.LENGTH_SHORT).show()
                viewModel.refresh() // auto-refresh galeri selepas folder dicipta
            } else {
                Toast.makeText(requireContext(), R.string.folder_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createFolderInSafTree(treeUri: Uri, folderName: String) {
        lifecycleScope.launch {
            try {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    requireContext(), treeUri
                )
                val created = root?.createDirectory(folderName)
                if (created != null && created.exists()) {
                    Toast.makeText(requireContext(), R.string.folder_created, Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                } else {
                    Toast.makeText(requireContext(), R.string.folder_create_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.folder_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =====================================================================
    // ALBUM PERIBADI
    // =====================================================================

    private fun openPrivateAlbum() {
        val intent = android.content.Intent(requireContext(), PinActivity::class.java)
            .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY)
        pinLauncher.launch(intent)
    }

    // =====================================================================
    // MOD PEMILIHAN + OPERASI BERKUMPULAN
    // =====================================================================

    private fun enterSelectViaMenu() {
        if (mediaAdapter.itemCount > 0) {
            mediaAdapter.enterSelectionMode(0)
            updateSelectionUi()
        }
    }

    private fun updateSelectionUi() {
        val inSelection = mediaAdapter.selectionMode
        toolbar.visibleOr(!inSelection)
        selectionToolbar.visibleOr(inSelection)
        selectionBar.visibleOr(inSelection)
        val count = mediaAdapter.selectedUris.size
        txtSelectionCount.text = getString(R.string.selected_count, count)
        // Rename hanya untuk 1 item
        selectionBar.findViewById<ImageButton>(R.id.btn_sel_rename)?.isEnabled = count == 1
    }

    private fun exitSelection() {
        mediaAdapter.exitSelectionMode()
        updateSelectionUi()
    }

    private fun getSelected(): List<MediaItem> = mediaAdapter.getSelectedItems()

    private fun shareSelected() {
        val items = getSelected()
        if (items.isEmpty()) return
        requireContext().shareUris(items.map { it.uri }, "image/*")
    }

    private fun trashSelected() {
        val items = getSelected()
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.trash_confirm_title)
            .setMessage(getString(R.string.trash_confirm_msg, items.size))
            .setPositiveButton(R.string.delete) { _, _ -> performTrash(items) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performTrash(items: List<MediaItem>) {
        lifecycleScope.launch {
            val result = com.kcum.gallery.data.MediaRepository.get(requireContext())
                .moveToTrash(items)
            if (result.failed > 0 && result.intentSender != null) {
                // Sistem minta pengguna benarkan dulu - jadualkan cuba semula
                pendingRetry = { performTrash(items) }
                intentSenderLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build()
                )
                return@launch
            }
            viewModel.removeItems(items)
            exitSelection()
            val msg = if (result.failed > 0) R.string.operation_partial else R.string.moved_to_trash
            Toast.makeText(
                requireContext(),
                getString(msg, result.success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hideSelected() {
        val items = getSelected()
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.hide_confirm_title)
            .setMessage(getString(R.string.hide_confirm_msg, items.size))
            .setPositiveButton(R.string.hide) { _, _ ->
                lifecycleScope.launch {
                    val result = com.kcum.gallery.data.MediaRepository.get(requireContext())
                        .hideItems(items)
                    if (result.failed > 0 && result.intentSender != null) {
                        pendingRetry = { hideSelectedRetry(items) }
                        intentSenderLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build()
                        )
                        return@launch
                    }
                    viewModel.removeItems(items)
                    exitSelection()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.hidden_count, result.success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun hideSelectedRetry(items: List<MediaItem>) {
        lifecycleScope.launch {
            val result = com.kcum.gallery.data.MediaRepository.get(requireContext()).hideItems(items)
            viewModel.removeItems(items)
            exitSelection()
            Toast.makeText(
                requireContext(),
                getString(R.string.hidden_count, result.success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =====================================================================
    // PINDAH / SALIN / RENAME
    // =====================================================================

    private fun moveSelected() {
        val items = getSelected()
        if (items.isEmpty()) return
        chooseTargetFolder { targetPath ->
            lifecycleScope.launch {
                try {
                    val ok = MediaStoreUtils.moveItems(requireContext(), items, targetPath)
                    if (ok) {
                        Toast.makeText(requireContext(), R.string.move_success, Toast.LENGTH_SHORT).show()
                        exitSelection()
                        viewModel.refresh()
                    } else {
                        Toast.makeText(requireContext(), R.string.move_failed, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: MediaStoreUtils.AccessDeniedException) {
                    e.intentSender?.let { sender ->
                        pendingRetry = { moveSelectedRetry(items, targetPath) }
                        intentSenderLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }
            }
        }
    }

    private fun moveSelectedRetry(items: List<MediaItem>, targetPath: String) {
        lifecycleScope.launch {
            try {
                MediaStoreUtils.moveItems(requireContext(), items, targetPath)
                viewModel.refresh()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.move_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copySelected() {
        val items = getSelected()
        if (items.isEmpty()) return
        chooseTargetFolder { targetPath ->
            lifecycleScope.launch {
                try {
                    val copied = MediaStoreUtils.copyItems(requireContext(), items, targetPath)
                    if (copied > 0) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.copied_count, copied),
                            Toast.LENGTH_SHORT
                        ).show()
                        exitSelection()
                        viewModel.refresh()
                    } else {
                        Toast.makeText(requireContext(), R.string.copy_failed, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: MediaStoreUtils.AccessDeniedException) {
                    e.intentSender?.let { sender ->
                        pendingRetry = { copySelectedRetry(items, targetPath) }
                        intentSenderLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }
            }
        }
    }

    private fun copySelectedRetry(items: List<MediaItem>, targetPath: String) {
        lifecycleScope.launch {
            try {
                MediaStoreUtils.copyItems(requireContext(), items, targetPath)
                viewModel.refresh()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.copy_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dialog pilih folder sasaran untuk pindah/salin.
     * Senarai album dari MediaStore + pilihan "Folder Baharu...".
     */
    private fun chooseTargetFolder(onChosen: (String) -> Unit) {
        val albums: List<Album> = viewModel.albums.value ?: emptyList()
        val names = albums.map { it.name }.toTypedArray()
        val choices = arrayOf(getString(R.string.new_folder_option)) + names
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_folder_title)
            .setItems(choices) { _, which ->
                if (which == 0) {
                    // Cipta folder baru dahulu, kemudian teruskan operasi ke situ
                    showCreateFolderDialogWithCallback { relativePath -> onChosen(relativePath) }
                } else {
                    val album = albums[which - 1]
                    onChosen(album.cover.relativePath.ifBlank { "Pictures/${album.name}/" })
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateFolderDialogWithCallback(onCreated: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        val etName = view.findViewById<EditText>(R.id.et_folder_name)
        val rbCustom = view.findViewById<android.widget.RadioButton>(R.id.rb_location_custom)
        rbCustom.isVisible = false // dalam aliran pindah/salin, guna lokasi lalai sahaja
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_folder_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                if (validateFolderName(name) != null) {
                    Toast.makeText(
                        requireContext(),
                        R.string.folder_name_error_invalid,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    lifecycleScope.launch {
                        val ok = MediaStoreUtils.createFolderInGallery(requireContext(), name)
                        if (ok) {
                            onCreated("Pictures/$name/")
                        } else {
                            Toast.makeText(
                                requireContext(),
                                R.string.folder_create_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameSelected() {
        val items = getSelected()
        if (items.size != 1) return
        val item = items.first()
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val etName = view.findViewById<EditText>(R.id.et_rename_name)
        etName.setText(item.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty() || newName == item.name) return@setPositiveButton
                lifecycleScope.launch {
                    val ok = MediaStoreUtils.renameFile(requireContext(), item.uri, newName)
                    if (ok) {
                        Toast.makeText(requireContext(), R.string.rename_success, Toast.LENGTH_SHORT).show()
                        exitSelection()
                        viewModel.refresh()
                    } else {
                        Toast.makeText(requireContext(), R.string.rename_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Papar/sorok view guna extension `visibleOr` dari util/Extensions.kt

    override fun onResume() {
        super.onResume()
        // Auto-refresh ringan semasa kembali ke skrin ini
        if (!viewModel.media.value.isNullOrEmpty()) {
            publishMedia()
        }
    }
}
