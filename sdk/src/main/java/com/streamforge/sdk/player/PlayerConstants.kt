package com.streamforge.sdk.player

/**
 * Shared HLS / DVR / live-tuning constants.
 *
 * Ported 1:1 from the web embed player so the Android player matches its
 * behaviour: streamforge-frontend/src/constants/player.ts.
 *
 * Shaka-specific tuning is mapped to the closest Media3/ExoPlayer equivalent
 * where applicable (see PlayerEngine). Values that drive UI/DVR logic
 * (thresholds, windows, latency) are kept identical.
 */
internal object PlayerConstants {

    // ── DVR / live-edge ──────────────────────────────────────────────

    /** How close (s) to the live edge the playhead must be to count as "at live". */
    const val LIVE_EDGE_THRESHOLD_S = 15.0

    /** Minimum seekable window (s) before we treat a stream as having a usable DVR buffer. */
    const val DVR_MIN_WINDOW_S = 10.0

    /** Default DVR depth (s) exposed on the seekbar when a rewind playlist exists (24h). */
    const val DEFAULT_DVR_WINDOW_S = 86_400.0

    /** Small rewind window (s) used purely to DETECT DVR availability. */
    const val DVR_PROBE_WINDOW_S = 60

    /** Timeout (ms) for the client-side DVR rewind-playlist probe (non-critical). */
    const val DVR_PROBE_TIMEOUT_MS = 2_000L

    /** Seekbar is only shown once the virtual DVR depth exceeds this (s). */
    const val DVR_SEEKBAR_MIN_DURATION_S = 30.0

    // ── Live latency / buffering ─────────────────────────────────────

    /** Forward buffer goal (s) for live playback — ~2.5 segments @6s. */
    const val LIVE_BUFFERING_GOAL_S = 16

    /** Forward buffer goal (s) for DVR/rewind playback. */
    const val DVR_BUFFERING_GOAL_S = 30

    /** Minimum data (s) to buffer before resuming after a stall — one full ~6s segment. */
    const val REBUFFERING_GOAL_S = 6

    /** Target latency (s) behind the live edge (Shaka liveSync → ExoPlayer targetOffsetMs). */
    const val TARGET_LATENCY_S = 12

    /** liveSync playback-rate bounds (never speed up; gentle slow-down near edge). */
    const val LIVE_MAX_PLAYBACK_RATE = 1.0f
    const val LIVE_MIN_PLAYBACK_RATE = 0.95f

    /** Default ABR bandwidth estimate (bps) — starts ABR at the lowest rendition (360p). */
    const val DEFAULT_BANDWIDTH_ESTIMATE = 300_000L

    // ── Stuck-playback recovery (watchdog) ───────────────────────────

    /** How long (ms) the playhead must stay frozen while playing before recovery. */
    const val RECOVERY_STALL_MS = 12_000L

    /** Minimum gap (ms) between recovery attempts. */
    const val RECOVERY_COOLDOWN_MS = 8_000L

    /** Consecutive recovery attempts before backing off hard. */
    const val RECOVERY_MAX_ATTEMPTS = 3

    /** Long cool-off (ms) after RECOVERY_MAX_ATTEMPTS fail back-to-back. */
    const val RECOVERY_BACKOFF_MS = 30_000L

    // ── Cosmetic stall (buffering overlay) ───────────────────────────

    /** Poll interval (ms) for the progress-based stall detector. */
    const val STALL_POLL_MS = 250L

    /** Playhead frozen longer than this (ms) while playing → show buffering overlay. */
    const val STALL_THRESHOLD_MS = 700L

    // ── UI timings ───────────────────────────────────────────────────

    /** Controls auto-hide delay (ms) while playing. */
    const val CONTROLS_HIDE_MS = 3_000L

    /** Max gap (ms) between two taps to count as a double tap on touch devices. */
    const val DOUBLE_TAP_DELAY_MS = 280L

    /** DVR tracking poll interval (ms). */
    const val DVR_POLL_MS = 500L

    /** Center play/pause action animation duration (ms). */
    const val CENTER_ACTION_MS = 500L

    // ── Theming ──────────────────────────────────────────────────────

    /** Default brand color (emerald) when the tenant has none. */
    const val DEFAULT_BRAND_COLOR = "#10b981"
}
