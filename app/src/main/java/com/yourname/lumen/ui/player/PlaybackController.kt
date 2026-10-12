package com.yourname.lumen.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.yourname.lumen.domain.model.LiveChannel

/**
 * The one player for the whole app. The Live TV preview and the fullscreen view both show this
 * same player, so going fullscreen and back never restarts the stream.
 */
@OptIn(UnstableApi::class)
class PlaybackController(context: Context) {

    val exo: ExoPlayer

    /** The channel that is loaded (playing, buffering or failed). Null when nothing is selected. */
    var channel by mutableStateOf<LiveChannel?>(null)
        private set
    var buffering by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Height of the decoded video in pixels, or 0 until the player knows it. */
    var videoHeight by mutableIntStateOf(0)
        private set

    private var urlFor: ((LiveChannel, String) -> String)? = null
    private var firstExtension = "m3u8"
    private var extension = "m3u8"
    private var canFallBack = false

    init {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Lumen/0.1")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) failed = false
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoHeight = videoSize.height
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (canFallBack && extension == "m3u8") {
                        // Some servers only serve plain TS. Try that once before giving up.
                        extension = "ts"
                        start()
                    } else {
                        failed = true
                        buffering = false
                    }
                }
            },
        )
    }

    /**
     * Plays [channel]. [supportsFormats] is true for Xtream-style sources, which can serve the same
     * channel as HLS (.m3u8) or plain TS, so a failure can be retried in the other format.
     */
    fun play(
        channel: LiveChannel,
        preferTs: Boolean,
        supportsFormats: Boolean,
        url: (LiveChannel, String) -> String,
    ) {
        this.channel = channel
        urlFor = url
        canFallBack = supportsFormats
        firstExtension = if (supportsFormats && preferTs) "ts" else "m3u8"
        extension = firstExtension
        failed = false
        videoHeight = 0
        start()
    }

    fun retry() {
        failed = false
        extension = firstExtension
        start()
    }

    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        channel = null
        buffering = false
        failed = false
        videoHeight = 0
    }

    fun setMuted(muted: Boolean) {
        exo.volume = if (muted) 0f else 1f
    }

    fun release() {
        exo.release()
    }

    private fun start() {
        val current = channel ?: return
        val address = urlFor?.invoke(current, extension) ?: return
        buffering = true
        val item = MediaItem.Builder().setUri(address).apply {
            if (address.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }
}
