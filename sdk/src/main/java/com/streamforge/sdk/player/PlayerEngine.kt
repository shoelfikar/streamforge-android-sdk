package com.streamforge.sdk.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.datasource.DefaultHttpDataSource
import com.streamforge.sdk.StreamForgeConfig
import com.streamforge.sdk.event.EventBus
import com.streamforge.sdk.event.StreamForgeEvent

/** Snapshot of the current live/DVR seekable window, mirroring `video.seekable` on the web. */
internal data class LiveWindow(
    /** Whether the current media is a live window. */
    val isLive: Boolean,
    /** Seekable depth in ms (window duration). */
    val durationMs: Long,
    /** Playhead position relative to window start, in ms. */
    val positionMs: Long,
    /** Distance behind the live edge in ms (0 when at edge / unknown). */
    val liveOffsetMs: Long
)

internal class PlayerEngine {

    private var exoPlayer: ExoPlayer? = null
    private var listener: PlayerEventListener? = null
    private var _playbackState: PlaybackState = PlaybackState.IDLE
    private var lastStreamUrl: String? = null
    private var lastProtocol: PlaybackProtocol? = null
    private val window = Timeline.Window()

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

        // ── Buffer goals (mirror shaka-live.ts LIVE/REBUFFERING goals) ──
        // minBuffer ≈ live buffering goal (16s), rebuffer resume ≈ one full segment (6s).
        val liveGoalMs = PlayerConstants.LIVE_BUFFERING_GOAL_S * 1000
        val dvrGoalMs = PlayerConstants.DVR_BUFFERING_GOAL_S * 1000
        val rebufferMs = PlayerConstants.REBUFFERING_GOAL_S * 1000
        val playbackStartMs = config?.bufferForPlaybackMs ?: 2500
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                liveGoalMs,
                dvrGoalMs.coerceAtLeast(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS),
                playbackStartMs,
                rebufferMs
            )
            .build()
        builder.setLoadControl(loadControl)

        // ── ABR: start at the lowest rendition (mirror DEFAULT_BANDWIDTH_ESTIMATE 300k) ──
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(PlayerConstants.DEFAULT_BANDWIDTH_ESTIMATE)
            .build()
        builder.setBandwidthMeter(bandwidthMeter)

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
        loadSource(url, protocol, isLive = true, seekToMs = null, resumePlay = true)
    }

    /**
     * Load a media source. Used both for the initial live load and for live↔rewind
     * swaps (mirrors the web `loadSource(useRewind)`). The current volume/mute is
     * preserved across swaps; optional [seekToMs] positions the playhead once ready.
     */
    fun loadSource(
        url: String,
        protocol: PlaybackProtocol,
        isLive: Boolean,
        seekToMs: Long?,
        resumePlay: Boolean
    ) {
        val player = exoPlayer ?: return
        lastStreamUrl = url
        lastProtocol = protocol
        updateState(PlaybackState.LOADING)

        val dataSourceFactory = DefaultHttpDataSource.Factory()

        // ── liveSync: target 12s behind edge, never speed up, gentle slow-down ──
        // (mirror shaka-live.ts liveSync targetLatency / playbackRate bounds)
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (isLive) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(PlayerConstants.TARGET_LATENCY_S * 1000L)
                    .setMinPlaybackSpeed(PlayerConstants.LIVE_MIN_PLAYBACK_RATE)
                    .setMaxPlaybackSpeed(PlayerConstants.LIVE_MAX_PLAYBACK_RATE)
                    .build()
            )
        }
        val mediaItem = mediaItemBuilder.build()

        val mediaSource = when (protocol) {
            PlaybackProtocol.HLS -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            PlaybackProtocol.DASH -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        player.setMediaSource(mediaSource)
        if (seekToMs != null) {
            player.setMediaSource(mediaSource, seekToMs)
        }
        player.playWhenReady = resumePlay
        player.prepare()
    }

    fun retry() {
        val url = lastStreamUrl ?: return
        val protocol = lastProtocol ?: return
        loadSource(url, protocol, isLive = true, seekToMs = null, resumePlay = true)
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

    /** Seek to the live edge (default position of the current window). */
    fun seekToLiveEdge() {
        exoPlayer?.seekToDefaultPosition()
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    val volume: Float
        get() = exoPlayer?.volume ?: 1f

    fun setMuted(muted: Boolean) {
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    /** Read the current live/DVR seekable window (mirror of `video.seekable`). */
    fun getLiveWindow(): LiveWindow? {
        val player = exoPlayer ?: return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        timeline.getWindow(player.currentMediaItemIndex, window)
        val durationMs = if (window.durationUs == C.TIME_UNSET) 0L else window.durationUs / 1000
        val liveOffset = player.currentLiveOffset
        return LiveWindow(
            isLive = window.isLive(),
            durationMs = durationMs,
            positionMs = player.currentPosition,
            liveOffsetMs = if (liveOffset == C.TIME_UNSET) 0L else liveOffset
        )
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
