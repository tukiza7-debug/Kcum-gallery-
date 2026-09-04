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
import com.kcum.gallery.data.Album

/**
 * Adapter grid album. Klik -> buka album; tekan lama -> rename/padam folder.
 */
class AlbumAdapter(
    private val onClick: (Album) -> Unit,
    private val onLongClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    private var albums: List<Album> = emptyList()

    fun submitList(newItems: List<Album>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = albums.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                albums[oldPos].bucketId == newItems[newPos].bucketId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                albums[oldPos] == newItems[newPos]
        })
        albums = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun getItemCount(): Int = albums.size

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position])
    }

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.img_cover)
        private val txtName: TextView = view.findViewById(R.id.txt_album_name)
        private val txtCount: TextView = view.findViewById(R.id.txt_album_count)

        fun bind(album: Album) {
            Glide.with(img)
                .load(album.cover.uri)
                .override(400, 400)
                .centerCrop()
                .placeholder(R.drawable.ic_folder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(img)
            txtName.text = album.name
            txtCount.text = img.context.getString(R.string.album_count, album.count)
            itemView.setOnClickListener { onClick(album) }
            itemView.setOnLongClickListener {
                onLongClick(album)
                true
            }
        }
    }
}
