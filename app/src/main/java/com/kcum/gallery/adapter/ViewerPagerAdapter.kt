package com.kcum.gallery.adapter

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import com.bumptech.glide.Glide
import com.kcum.gallery.data.MediaItem
import com.kcum.gallery.data.PrefsRepository
import com.kcum.gallery.view.ZoomableImageView

/**
 * Pager adapter untuk ViewerActivity (ViewPager2).
 * - Halaman GAMBAR : ZoomableImageView dengan Glide
 * - Halaman VIDEO  : PlayerView dengan ExoPlayer (play/pause/seek)
 *
 * Setiap pemegang video membina ExoPlayer sendiri dan melepaskannya semasa
 * dikitar semula (onViewRecycled) supaya memori terkawal.
 */
class ViewerPagerAdapter(
    private var items: List<MediaItem>,
    private val onImageTap: () -> Unit,
    private val onPlayerReady: (ExoPlayer?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
    }

    fun submitList(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItem(position: Int): MediaItem = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_IMAGE) {
            val zoom = ZoomableImageView(parent.context)
            zoom.layoutParams = ViewGroup.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            ImageViewHolder(zoom)
        } else {
            val playerView = PlayerView(parent.context)
            playerView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            playerView.useController = false
            VideoViewHolder(playerView)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isVideo) TYPE_VIDEO else TYPE_IMAGE

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> holder.bind(items[position])
            is VideoViewHolder -> holder.bind(items[position])
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) holder.release()
    }

    inner class ImageViewHolder(val zoomView: ZoomableImageView) :
        RecyclerView.ViewHolder(zoomView) {

        fun bind(item: MediaItem) {
            zoomView.resetZoom()
            Glide.with(zoomView)
                .load(item.uri)
                .fitCenter()
                .into(zoomView)
            zoomView.setOnTapListener { onImageTap() }
        }
    }

    inner class VideoViewHolder(val playerView: PlayerView) :
        RecyclerView.ViewHolder(playerView) {

        private var player: ExoPlayer? = null

        fun bind(item: MediaItem) {
            release()
            val prefs = PrefsRepository.get(playerView.context)
            player = ExoPlayer.Builder(playerView.context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(item.uri))
                repeatMode = if (prefs.videoLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (prefs.videoMuted) 0f else 1f
                prepare()
                playWhenReady = prefs.videoAutoplay
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) onPlayerReady(player) 
                    }
                })
            }
            playerView.player = player
            // Ketuk video = tunjuk/sorok kawalan penonton
            playerView.setOnClickListener { onImageTap() }
            onPlayerReady(player)
        }

        fun release() {
            player?.release()
            player = null
            playerView.player = null
            onPlayerReady(null)
        }
    }
}
