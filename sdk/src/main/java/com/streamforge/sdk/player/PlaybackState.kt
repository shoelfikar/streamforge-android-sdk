package com.streamforge.sdk.player

/**
 * Represents the current state of the player.
 */
enum class PlaybackState {
    /** Player is idle and has no media loaded. */
    IDLE,
    /** Player is loading media. */
    LOADING,
    /** Player is ready to play. */
    READY,
    /** Player is actively playing media. */
    PLAYING,
    /** Player is paused. */
    PAUSED,
    /** Player is buffering (stalled). */
    BUFFERING,
    /** Playback has ended. */
    ENDED,
    /** An error occurred during playback. */
    ERROR
}
