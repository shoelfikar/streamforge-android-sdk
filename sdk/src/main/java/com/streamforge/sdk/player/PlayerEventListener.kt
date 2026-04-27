package com.streamforge.sdk.player

/**
 * Listener for player and stream events.
 *
 * All methods have default empty implementations so you only need to override
 * the events you care about.
 */
interface PlayerEventListener {
    /** Called when the playback state changes. */
    fun onPlaybackStateChanged(state: PlaybackState) {}
    /** Called when the player is initialized and ready. */
    fun onPlayerReady() {}
    /** Called when a playback error occurs. The SDK may auto-retry before calling this. */
    fun onPlayerError(error: Exception) {}
    /** Called when the video resolution changes. */
    fun onVideoSizeChanged(width: Int, height: Int) {}
    /** Called when the stream goes live or offline. */
    fun onStreamStatusChanged(isLive: Boolean) {}
    /** Called when the adaptive bitrate quality changes. */
    fun onQualityChanged(width: Int, height: Int, bitrate: Int) {}
    /** Called when fullscreen mode changes. */
    fun onFullscreenChanged(isFullscreen: Boolean) {}
    /** Called when Picture-in-Picture mode changes. */
    fun onPipChanged(isInPip: Boolean) {}
}
