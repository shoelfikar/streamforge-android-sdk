package com.streamforge.sdk.player

import android.app.Activity
import android.content.Context
import com.streamforge.sdk.StreamForge
import com.streamforge.sdk.StreamForgeConfig
import com.streamforge.sdk.event.EventBus
import com.streamforge.sdk.event.StreamForgeEvent
import kotlinx.coroutines.flow.Flow

class StreamForgePlayer internal constructor(
    private val context: Context,
    private val config: StreamForgeConfig? = null
) {

    private val engine = PlayerEngine()
    private val statusMonitor = StreamStatusMonitor()
    private val retryManager = RetryManager()
    private val fullscreenManager = FullscreenManager()
    private val qualityManager = QualityManager()
    private val pipManager = PipManager()
    private var view: StreamForgePlayerView? = null
    private var userListener: PlayerEventListener? = null

    val currentPosition: Long get() = engine.currentPosition
    val duration: Long get() = engine.duration
    val isPlaying: Boolean get() = engine.isPlaying
    val playbackState: PlaybackState get() = engine.playbackState
    val isFullscreen: Boolean get() = fullscreenManager.isFullscreen

    init {
        engine.initialize(context, config)
    }

    fun attachView(playerView: StreamForgePlayerView) {
        view = playerView
        playerView.attachEngine(engine)
    }

    fun detachView() {
        view?.detachEngine()
        view = null
    }

    fun setEventListener(listener: PlayerEventListener?) {
        userListener = listener
        engine.setEventListener(createInternalListener(listener))
        statusMonitor.setEventListener(listener)
    }

    /**
     * Load the stream URL obtained during SDK init.
     * Starts stream status polling automatically.
     */
    fun load(listener: PlayerEventListener? = null) {
        listener?.let {
            userListener = it
            engine.setEventListener(createInternalListener(it))
            statusMonitor.setEventListener(it)
        }
        val url = StreamForge.streamUrl
            ?: throw IllegalStateException("Stream URL not available. Ensure StreamForge.init() was called with a valid streamId.")
        retryManager.reset()
        engine.loadStream(url, PlaybackProtocol.HLS)

        // Start status polling
        StreamForge.streamId?.let { statusMonitor.start(it) }
    }

    /**
     * Load a custom URL directly (e.g. for testing or alternative streams).
     */
    fun loadUrl(url: String, protocol: PlaybackProtocol = PlaybackProtocol.HLS) {
        retryManager.reset()
        engine.loadStream(url, protocol)
    }

    /**
     * Observe all SDK events as a Kotlin Flow.
     */
    fun observeEvents(): Flow<StreamForgeEvent> = EventBus.events

    var isMuted: Boolean = false
        private set

    fun play() = engine.play()
    fun pause() = engine.pause()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    fun setVolume(volume: Float) = engine.setVolume(volume)

    fun setMuted(muted: Boolean) {
        isMuted = muted
        engine.setMuted(muted)
        view?.setMuteState(muted)
    }

    fun toggleMute() {
        setMuted(!isMuted)
    }

    // ── Fullscreen ──

    fun enterFullscreen(activity: Activity) {
        fullscreenManager.enterFullscreen(activity, view)
        userListener?.onFullscreenChanged(true)
        EventBus.emit(StreamForgeEvent.FullscreenChanged(true))
    }

    fun exitFullscreen(activity: Activity) {
        fullscreenManager.exitFullscreen(activity, view)
        userListener?.onFullscreenChanged(false)
        EventBus.emit(StreamForgeEvent.FullscreenChanged(false))
    }

    fun toggleFullscreen(activity: Activity) {
        if (fullscreenManager.isFullscreen) exitFullscreen(activity) else enterFullscreen(activity)
    }

    // ── Quality Selector ──

    fun getAvailableQualities(): List<QualityOption> {
        return qualityManager.getAvailableQualities(engine.player)
    }

    fun setQuality(option: QualityOption) {
        qualityManager.setQuality(engine.player, option)
    }

    fun setAutoQuality() {
        qualityManager.setAutoQuality(engine.player)
    }

    // ── Picture-in-Picture ──

    fun isPipSupported(): Boolean = pipManager.isPipSupported(context)

    fun enterPip(activity: Activity) {
        pipManager.enterPip(activity, engine.player)
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean) {
        view?.onPipModeChanged(isInPip)
        userListener?.onPipChanged(isInPip)
        EventBus.emit(StreamForgeEvent.PipChanged(isInPip))
    }

    fun release() {
        retryManager.cancel()
        statusMonitor.stop()
        view?.detachEngine()
        view = null
        engine.release()
    }

    /**
     * Wraps the user's listener to add auto-retry on error.
     * On READY/PLAYING, resets the retry counter.
     */
    private fun createInternalListener(delegate: PlayerEventListener?): PlayerEventListener {
        return object : PlayerEventListener {
            override fun onPlaybackStateChanged(state: PlaybackState) {
                if (state == PlaybackState.READY || state == PlaybackState.PLAYING) {
                    retryManager.reset()
                }
                delegate?.onPlaybackStateChanged(state)
            }

            override fun onPlayerReady() {
                delegate?.onPlayerReady()
            }

            override fun onPlayerError(error: Exception) {
                retryManager.retry(
                    action = { engine.retry() },
                    onGiveUp = { delegate?.onPlayerError(error) }
                )
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                delegate?.onVideoSizeChanged(width, height)
            }

            override fun onStreamStatusChanged(isLive: Boolean) {
                view?.setLiveStatus(isLive)
                delegate?.onStreamStatusChanged(isLive)
            }

            override fun onQualityChanged(width: Int, height: Int, bitrate: Int) {
                delegate?.onQualityChanged(width, height, bitrate)
            }
        }
    }
}
