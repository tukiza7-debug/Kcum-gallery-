package com.kcum.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kcum.gallery.R
import com.kcum.gallery.data.HiddenItem
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.visibleOr
import java.io.File

/**
 * Adapter grid Album Peribadi (item tersembunyi dari storan dalaman).
 */
class HiddenAdapter(
    val onClick: (HiddenItem) -> Unit
) : RecyclerView.Adapter<HiddenAdapter.HiddenViewHolder>() {

    private var items: List<HiddenItem> = emptyList()

    fun submitList(newItems: List<HiddenItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiddenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hidden, parent, false)
        return HiddenViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: HiddenViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class HiddenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.img_thumb)
        private val txtName: TextView = view.findViewById(R.id.txt_name)
        private val badge: View = view.findViewById(R.id.badge_video)

        fun bind(item: HiddenItem) {
            Glide.with(img)
                .load(File(item.storedPath))
                .override(300, 300)
                .centerCrop()
                .placeholder(R.drawable.ic_lock)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(img)
            txtName.text = item.displayName
            badge.visibleOr(item.isVideo)
            itemView.setOnClickListener { onClickSafe(item) }
        }

        private fun onClickSafe(item: HiddenItem) {
            val listener = (bindingAdapter as? HiddenAdapter)?.onClick ?: return
            listener(item)
        }
    }
}
