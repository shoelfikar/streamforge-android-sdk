package com.streamforge.sdk.event

import com.streamforge.sdk.player.PlaybackState

/**
 * Sealed class representing all events emitted by the SDK.
 *
 * Observe via [com.streamforge.sdk.player.StreamForgePlayer.observeEvents] as a Kotlin Flow.
 */
sealed class StreamForgeEvent {
    data class PlaybackStateChanged(val state: PlaybackState) : StreamForgeEvent()
    data class PlayerError(val error: Exception) : StreamForgeEvent()
    data class VideoSizeChanged(val width: Int, val height: Int) : StreamForgeEvent()
    data class StreamStatusChanged(val isLive: Boolean) : StreamForgeEvent()
    data class QualityChanged(val width: Int, val height: Int, val bitrate: Int) : StreamForgeEvent()
    data class FullscreenChanged(val isFullscreen: Boolean) : StreamForgeEvent()
    data class PipChanged(val isInPip: Boolean) : StreamForgeEvent()
}
