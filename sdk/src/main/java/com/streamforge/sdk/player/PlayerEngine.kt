package com.streamforge.sdk.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.streamforge.sdk.StreamForgeConfig
import com.streamforge.sdk.event.EventBus
import com.streamforge.sdk.event.StreamForgeEvent

internal class PlayerEngine {

    private var exoPlayer: ExoPlayer? = null
    private var listener: PlayerEventListener? = null
    private var _playbackState: PlaybackState = PlaybackState.IDLE
    private var lastStreamUrl: String? = null
    private var lastProtocol: PlaybackProtocol? = null

    val player: ExoPlayer?
        get() = exoPlayer

    val currentPosition: Long
        get() = exoPlayer?.currentPosition ?: 0L

    val duration: Long
        get() = exoPlayer?.duration ?: 0L

    val isPlaying: Boolean
        get() = exoPlayer?.isPlaying ?: false

    val playbackState: PlaybackState
        get() = _playbackState

    fun initialize(context: Context, config: StreamForgeConfig? = null) {
        release()

        val builder = ExoPlayer.Builder(context)

        if (config != null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    config.bufferForPlaybackMs,
                    config.bufferForPlaybackAfterRebufferMs
                )
                .build()
            builder.setLoadControl(loadControl)
        }

        exoPlayer = builder.build().also { player ->
            player.addListener(exoPlayerListener)

            if (config != null) {
                val paramsBuilder = player.trackSelectionParameters.buildUpon()
                config.maxVideoWidth?.let { w ->
                    config.maxVideoHeight?.let { h ->
                        paramsBuilder.setMaxVideoSize(w, h)
                    }
                }
                config.maxBitrateKbps?.let { kbps ->
                    paramsBuilder.setMaxVideoBitrate(kbps * 1000)
                }
                config.minBitrateKbps?.let { kbps ->
                    paramsBuilder.setMinVideoBitrate(kbps * 1000)
                }
                player.trackSelectionParameters = paramsBuilder.build()
            }
        }
        updateState(PlaybackState.IDLE)
    }

    fun setEventListener(listener: PlayerEventListener?) {
        this.listener = listener
    }

    fun loadStream(url: String, protocol: PlaybackProtocol) {
        val player = exoPlayer ?: return
        lastStreamUrl = url
        lastProtocol = protocol
        updateState(PlaybackState.LOADING)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val mediaItem = MediaItem.fromUri(url)

        val mediaSource = when (protocol) {
            PlaybackProtocol.HLS -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            PlaybackProtocol.DASH -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        player.setMediaSource(mediaSource)
        player.prepare()
    }

    fun retry() {
        val url = lastStreamUrl ?: return
        val protocol = lastProtocol ?: return
        loadStream(url, protocol)
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun setMuted(muted: Boolean) {
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    fun release() {
        exoPlayer?.removeListener(exoPlayerListener)
        exoPlayer?.release()
        exoPlayer = null
        lastStreamUrl = null
        lastProtocol = null
        updateState(PlaybackState.IDLE)
    }

    private fun updateState(state: PlaybackState) {
        _playbackState = state
        listener?.onPlaybackStateChanged(state)
        EventBus.emit(StreamForgeEvent.PlaybackStateChanged(state))
    }

    private val exoPlayerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> updateState(PlaybackState.IDLE)
                Player.STATE_BUFFERING -> updateState(PlaybackState.BUFFERING)
                Player.STATE_READY -> {
                    updateState(PlaybackState.READY)
                    listener?.onPlayerReady()
                }
                Player.STATE_ENDED -> updateState(PlaybackState.ENDED)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                updateState(PlaybackState.PLAYING)
            } else if (exoPlayer?.playbackState == Player.STATE_READY) {
                updateState(PlaybackState.PAUSED)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            updateState(PlaybackState.ERROR)
            listener?.onPlayerError(error)
            EventBus.emit(StreamForgeEvent.PlayerError(error))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                listener?.onVideoSizeChanged(videoSize.width, videoSize.height)
                EventBus.emit(StreamForgeEvent.VideoSizeChanged(videoSize.width, videoSize.height))
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                    val format: Format? = group.getTrackFormat(0)
                    if (format != null && format.width > 0 && format.height > 0) {
                        val bitrate = format.bitrate.coerceAtLeast(0)
                        listener?.onQualityChanged(format.width, format.height, bitrate)
                        EventBus.emit(StreamForgeEvent.QualityChanged(format.width, format.height, bitrate))
                    }
                    break
                }
            }
        }
    }
}
