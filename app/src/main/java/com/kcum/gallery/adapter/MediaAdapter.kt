package com.kcum.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kcum.gallery.R
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.visibleOr

/**
 * Adapter utama galeri - menyokong DUA mod paparan (GRID / SENARAI) + pemilihan berbilang.
 * Dipakai oleh GalleryFragment (semua gambar + album detail) dan SearchActivity.
 */
class MediaAdapter(
    private val mode: Int, // MODE_GRID atau MODE_LIST
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    companion object {
        const val MODE_GRID = 0
        const val MODE_LIST = 1
    }

    private var items: List<MediaItem> = emptyList()
    val selectedUris = LinkedHashSet<String>()

    var selectionMode = false
        private set

    fun submitList(newItems: List<MediaItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].uri == newItems[newPos].uri
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        items = newItems
        // Buang pilihan untuk item yang sudah tiada
        val validUris = items.map { it.uri.toString() }.toHashSet()
        selectedUris.retainAll { it in validUris }
        diff.dispatchUpdatesTo(this)
    }

    fun getItem(position: Int): MediaItem = items[position]

    fun toggleSelection(position: Int) {
        val key = items[position].uri.toString()
        if (!selectedUris.add(key)) selectedUris.remove(key)
        if (selectedUris.isEmpty()) selectionMode = false
        notifyItemChanged(position)
    }

    fun enterSelectionMode(position: Int) {
        selectionMode = true
        selectedUris.add(items[position].uri.toString())
        notifyItemChanged(position)
    }

    fun exitSelectionMode() {
        selectionMode = false
        val previous = ArrayList(selectedUris)
        selectedUris.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<MediaItem> =
        items.filter { it.uri.toString() in selectedUris }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val layout = if (mode == MODE_GRID) R.layout.item_media_grid else R.layout.item_media_list
        val binding = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MediaViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val img: ImageView = itemView.findViewById(R.id.img_thumb)
        private val badge: View? = itemView.findViewById(R.id.badge_video)
        private val txtDuration: TextView? = itemView.findViewById(R.id.txt_duration)
        private val txtName: TextView? = itemView.findViewById(R.id.txt_name)
        private val txtMeta: TextView? = itemView.findViewById(R.id.txt_meta)
        private val overlay: View? = itemView.findViewById(R.id.view_overlay)
        private val check: ImageView? = itemView.findViewById(R.id.img_check)

        fun bind(item: MediaItem) {
            Glide.with(img)
                .load(item.uri)
                .centerCrop()
                .placeholder(R.drawable.ic_photo)
                .into(img)

            badge.visibleOr(item.isVideo)
            if (item.isVideo) txtDuration?.text = Formats.duration(item.durationMs)

            if (mode == MODE_LIST) {
                txtName?.text = item.name
                txtMeta?.text = buildString {
                    append(Formats.date(img.context, item.dateMs))
                    append(" • ")
                    append(Formats.fileSize(item.size))
                }
            }

            val selected = item.uri.toString() in selectedUris
            overlay?.visibleOr(selectionMode && !selected)
            check?.visibleOr(selected)

            itemView.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(bindingAdapterPosition)
                    onItemClick(bindingAdapterPosition) // notifikasi untuk kemas kini toolbar
                } else {
                    onItemClick(bindingAdapterPosition)
                }
            }
            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    enterSelectionMode(bindingAdapterPosition)
                    onItemLongClick(bindingAdapterPosition)
                } else {
                    toggleSelection(bindingAdapterPosition)
                    onItemLongClick(bindingAdapterPosition)
                }
                true
            }
        }
    }
}
