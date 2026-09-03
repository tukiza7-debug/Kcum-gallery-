package com.kcum.gallery.view

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.kcum.gallery.R
import com.kcum.gallery.adapter.MediaAdapter
import com.kcum.gallery.viewmodel.GalleryViewModel
import com.kcum.gallery.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Carian & Tapisan:
 * - Nama fail (teks)
 * - Tarikh (julat dari/hingga dengan DatePicker)
 * - Jenis (semua/gambar/video) melalui Chip
 * - Saiz fail (<1MB / 1-10MB / >10MB) melalui Chip
 */
class SearchActivity : AppCompatActivity() {

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: MediaAdapter
    private lateinit var btnDateFrom: Button
    private lateinit var btnDateTo: Button
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val toolbar = findViewById<Toolbar>(R.id.search_toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.search_title)

        val etName = findViewById<EditText>(R.id.et_search_name)
        val recycler = findViewById<RecyclerView>(R.id.recycler_search)
        val emptyView = findViewById<View>(R.id.search_empty)
        btnDateFrom = findViewById(R.id.btn_date_from)
        btnDateTo = findViewById(R.id.btn_date_to)

        recycler.layoutManager = GridLayoutManager(this, 3)
        adapter = MediaAdapter(
            MediaAdapter.MODE_GRID,
            onItemClick = { position -> openViewer(position) },
            onItemLongClick = { }
        )
        recycler.adapter = adapter

        // ---------- TAPIS NAMA ----------
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.nameQuery = s?.toString() ?: ""
                viewModel.applyFilters()
            }
        })

        // ---------- TAPIS JENIS ----------
        bindChip(R.id.chip_type_all) { viewModel.type = GalleryViewModel.TYPE_ALL }
        bindChip(R.id.chip_type_image) { viewModel.type = GalleryViewModel.TYPE_IMAGE }
        bindChip(R.id.chip_type_video) { viewModel.type = GalleryViewModel.TYPE_VIDEO }

        // ---------- TAPIS SAIZ ----------
        bindChip(R.id.chip_size_all) { viewModel.sizeFilter = SearchViewModel.SIZE_ALL }
        bindChip(R.id.chip_size_small) { viewModel.sizeFilter = SearchViewModel.SIZE_SMALL }
        bindChip(R.id.chip_size_medium) { viewModel.sizeFilter = SearchViewModel.SIZE_MEDIUM }
        bindChip(R.id.chip_size_large) { viewModel.sizeFilter = SearchViewModel.SIZE_LARGE }

        // ---------- TAPIS TARIKH ----------
        btnDateFrom.setOnClickListener { pickDate(true) }
        btnDateTo.setOnClickListener { pickDate(false) }
        findViewById<Button>(R.id.btn_date_clear).setOnClickListener {
            viewModel.dateFrom = null
            viewModel.dateTo = null
            btnDateFrom.text = getString(R.string.date_from)
            btnDateTo.text = getString(R.string.date_to)
            viewModel.applyFilters()
        }

        viewModel.results.observe(this) { results ->
            adapter.submitList(results)
            emptyView.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loadAll()
    }

    private fun bindChip(id: Int, onSelect: () -> Unit) {
        findViewById<Chip>(id).setOnClickListener {
            onSelect()
            viewModel.applyFilters()
        }
    }

    private fun pickDate(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isFrom) viewModel.dateFrom else viewModel.dateTo
        current?.let { cal.timeInMillis = it }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                val millis = cal.timeInMillis
                if (isFrom) {
                    viewModel.dateFrom = millis
                    btnDateFrom.text = dateFormat.format(millis)
                } else {
                    viewModel.dateTo = millis
                    btnDateTo.text = dateFormat.format(millis)
                }
                viewModel.applyFilters()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun openViewer(position: Int) {
        val results = viewModel.results.value ?: return
        if (results.isEmpty()) return
        ViewerActivity.start(this, results, position)
    }
}
