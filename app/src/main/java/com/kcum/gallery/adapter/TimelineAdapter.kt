package com.kcum.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kcum.gallery.R
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.TimelineRow
import com.kcum.gallery.util.Formats
import com.kcum.gallery.util.visibleOr

/**
 * Adapter paparan Timeline - media dicampur dengan pengepala BULAN/TAHUN.
 * Fragment menghantar senarai baris penuh (header + media) yang telah dibina
 * oleh MediaRepository.buildTimeline().
 */
class TimelineAdapter(
    private var rows: List<TimelineRow>,
    private val onMediaClick: (item: MediaItem, originalIndex: Int) -> Unit,
    private val originalList: () -> List<MediaItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MEDIA = 1
    }

    fun submitRows(newRows: List<TimelineRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_timeline_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_grid, parent, false)
            MediaViewHolder(view)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is TimelineRow.Header) TYPE_HEADER else TYPE_MEDIA

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TimelineRow.Header -> (holder as HeaderViewHolder).bind(row.title)
            is TimelineRow.Media -> (holder as MediaViewHolder).bind(row.item)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txt: TextView = view.findViewById(R.id.txt_header)
        fun bind(title: String) {
            txt.text = title
        }
    }

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.img_thumb)
        private val badge: View = view.findViewById(R.id.badge_video)
        private val txtDuration: TextView = view.findViewById(R.id.txt_duration)

        fun bind(item: MediaItem) {
            Glide.with(img).load(item.uri).centerCrop()
                .placeholder(R.drawable.ic_photo).into(img)
            badge.visibleOr(item.isVideo)
            if (item.isVideo) txtDuration.text = Formats.duration(item.durationMs)
            itemView.setOnClickListener {
                // Cari indeks item dalam senarai penuh supaya penonton
                // membuka keseluruhan senarai dengan posisi yang betul
                val idx = originalList().indexOfFirst { it.uri == item.uri }
                if (idx >= 0) onMediaClick(item, idx)
            }
        }
    }
}
