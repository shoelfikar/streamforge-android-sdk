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
    private var dvrController: DvrController? = null
    private var view: StreamForgePlayerView? = null
    private var userListener: PlayerEventListener? = null

    /** Currently-selected quality height: -1 = Auto, else a pinned rendition height. */
    private var selectedHeight = -1
    private var activeHeight = 0

    val currentPosition: Long get() = engine.currentPosition
    val duration: Long get() = engine.duration
    val isPlaying: Boolean get() = engine.isPlaying
    val playbackState: PlaybackState get() = engine.playbackState
    val isFullscreen: Boolean get() = fullscreenManager.isFullscreen

    init {
        engine.initialize(context, config)
        engine.setEventListener(buildInternalListener())
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
        statusMonitor.setEventListener(listener)
    }

    /**
     * Load the live stream resolved during SDK init, applying autoplay / initial
     * mute from config, then start status monitoring and the DVR controller.
     */
    fun load(listener: PlayerEventListener? = null) {
        listener?.let { userListener = it; statusMonitor.setEventListener(it) }

        val liveUrl = StreamForge.liveUrl ?: StreamForge.streamUrl
            ?: throw IllegalStateException("Stream URL not available. Ensure StreamForge.init() was called with a valid streamId.")

        retryManager.reset()

        // Initial mute (mirror the embed `muted` param).
        setMuted(config?.muted ?: false)

        engine.loadSource(
            url = liveUrl,
            protocol = PlaybackProtocol.HLS,
            isLive = true,
            seekToMs = null,
            resumePlay = config?.autoplay ?: true
        )

        StreamForge.streamId?.let { statusMonitor.start(it) }

        setupDvr()
    }

    /** Load a custom URL directly (e.g. for testing or alternative streams). */
    fun loadUrl(url: String, protocol: PlaybackProtocol = PlaybackProtocol.HLS) {
        retryManager.reset()
        engine.loadSource(url, protocol, isLive = true, seekToMs = null, resumePlay = true)
    }

    /** Re-attempt playback after an error (wired to the error overlay's Retry button). */
    fun retryPlayback() {
        view?.clearError()
        retryManager.reset()
        engine.retry()
    }

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
        view?.onMuteChanged(muted)
    }

    fun toggleMute() = setMuted(!isMuted)

    // ── DVR (live-rewind) ──

    private fun setupDvr() {
        dvrController?.stop()
        val controller = DvrController(
            engine = engine,
            loadLive = {
                StreamForge.liveUrl?.let {
                    engine.loadSource(it, PlaybackProtocol.HLS, isLive = true, seekToMs = null, resumePlay = true)
                }
            },
            loadRewind = {
                StreamForge.rewindUrl?.let {
                    engine.loadSource(it, PlaybackProtocol.HLS, isLive = false, seekToMs = null, resumePlay = true)
                }
            },
            trustAll = config?.trustAllCertificates ?: false,
            onState = { state -> view?.onDvrState(state) }
        )
        dvrController = controller
        controller.probe(StreamForge.rewindProbeUrl)
        controller.start()
    }

    internal fun dvrHandleSeek(timeSec: Double) = dvrController?.handleSeek(timeSec)
    internal fun dvrJumpToLive() = dvrController?.jumpToLive()

    // ── Fullscreen ──

    fun enterFullscreen(activity: Activity) {
        fullscreenManager.enterFullscreen(activity, view)
        view?.onFullscreenChanged(true)
        userListener?.onFullscreenChanged(true)
        EventBus.emit(StreamForgeEvent.FullscreenChanged(true))
    }

    fun exitFullscreen(activity: Activity) {
        fullscreenManager.exitFullscreen(activity, view)
        view?.onFullscreenChanged(false)
        userListener?.onFullscreenChanged(false)
        EventBus.emit(StreamForgeEvent.FullscreenChanged(false))
    }

    fun toggleFullscreen(activity: Activity) {
        if (fullscreenManager.isFullscreen) exitFullscreen(activity) else enterFullscreen(activity)
    }

    // ── Quality ──

    fun getAvailableQualities(): List<QualityOption> = qualityManager.getAvailableQualities(engine.player)

    fun setQuality(option: QualityOption) {
        qualityManager.setQuality(engine.player, option)
        selectedHeight = if (option.isAuto) -1 else option.height
    }

    fun setAutoQuality() {
        qualityManager.setAutoQuality(engine.player)
        selectedHeight = -1
    }

    fun release() {
        retryManager.cancel()
        statusMonitor.stop()
        dvrController?.stop()
        view?.detachEngine()
        view = null
        engine.release()
    }

    /** Internal engine listener — drives the view and delegates to the user listener. */
    private fun buildInternalListener(): PlayerEventListener = object : PlayerEventListener {
        override fun onPlaybackStateChanged(state: PlaybackState) {
            if (state == PlaybackState.READY || state == PlaybackState.PLAYING) {
                retryManager.reset()
            }
            view?.onPlaybackStateChanged(state, engine.isPlaying)
            userListener?.onPlaybackStateChanged(state)
        }

        override fun onPlayerReady() {
            userListener?.onPlayerReady()
        }

        override fun onPlayerError(error: Exception) {
            retryManager.retry(
                action = { engine.retry() },
                onGiveUp = {
                    view?.showError(null)
                    userListener?.onPlayerError(error)
                }
            )
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            userListener?.onVideoSizeChanged(width, height)
        }

        override fun onStreamStatusChanged(isLive: Boolean) {
            userListener?.onStreamStatusChanged(isLive)
        }

        override fun onQualityChanged(width: Int, height: Int, bitrate: Int) {
            activeHeight = height
            view?.onQualityInfo(getAvailableQualities(), activeHeight, selectedHeight)
            userListener?.onQualityChanged(width, height, bitrate)
        }
    }
}
