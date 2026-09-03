package com.kcum.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kcum.gallery.R
import com.kcum.gallery.data.TrashItem
import com.kcum.gallery.util.Formats
import java.io.File

/**
 * Adapter senarai Tong Sampah: nama + tarikh + saiz + butang RESTORE & PADAM KEKAL.
 */
class TrashAdapter(
    val onRestore: (TrashItem) -> Unit,
    val onDeleteForever: (TrashItem) -> Unit
) : RecyclerView.Adapter<TrashAdapter.TrashViewHolder>() {

    private var items: List<TrashItem> = emptyList()

    fun submitList(newItems: List<TrashItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class TrashViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.img_thumb)
        private val txtName: TextView = view.findViewById(R.id.txt_name)
        private val txtMeta: TextView = view.findViewById(R.id.txt_meta)
        private val btnRestore: ImageButton = view.findViewById(R.id.btn_restore)
        private val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_forever)

        fun bind(item: TrashItem) {
            txtName.text = item.displayName
            txtMeta.text = buildString {
                append(Formats.date(itemView.context, item.trashedAt))
                append(" • ")
                append(Formats.fileSize(item.size))
                append(" • ")
                append(item.relativePath)
            }
            Glide.with(img)
                .load(File(item.storedPath))
                .centerCrop()
                .placeholder(R.drawable.ic_photo)
                .into(img)
            btnRestore.setOnClickListener { onRestoreSafe(item) }
            btnDelete.setOnClickListener { onDeleteForeverSafe(item) }
        }

        private fun onRestoreSafe(item: TrashItem) {
            val listener = (bindingAdapter as? TrashAdapter)?.onRestore ?: return
            listener(item)
        }

        private fun onDeleteForeverSafe(item: TrashItem) {
            val listener = (bindingAdapter as? TrashAdapter)?.onDeleteForever ?: return
            listener(item)
        }
    }
}
