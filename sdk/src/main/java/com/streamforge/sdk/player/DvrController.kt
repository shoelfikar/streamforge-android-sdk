package com.streamforge.sdk.player

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.max
import kotlin.math.min

/** Which playlist the player is currently sourced from. */
internal enum class DvrMode { LIVE, REWIND }

/**
 * Snapshot of DVR state, pushed to the UI on every poll. Mirrors the values
 * returned by the web `useDvrTracking` hook (all times in **seconds** on a
 * virtual timeline ending at the live edge).
 */
internal data class DvrState(
    val available: Boolean,
    val currentTime: Double,
    val seekableStart: Double,
    val seekableEnd: Double,
    val dvrDuration: Double,
    val isAtLiveEdge: Boolean,
    val mode: DvrMode
)

/**
 * DVR (live-rewind) controller — a 1:1 port of the web embed player's
 * `use-dvr-tracking.ts` plus the background DVR probe in `embed/[token]/page.tsx`.
 *
 * Playback always *starts* on the live playlist (ad-bearing). The seekbar
 * exposes a virtual window of [DEFAULT_DVR_WINDOW_S] ending at the live edge.
 * Only when the viewer seeks meaningfully behind live do we swap to the rewind
 * playlist; seeking back to the edge swaps back to live so ads resume.
 *
 * @param engine        the player engine (read window / seek)
 * @param loadLive      callback to (re)load the live playlist
 * @param loadRewind    callback to load the rewind playlist
 * @param trustAll      bypass SSL validation for the probe (dev only)
 * @param onState       UI callback invoked on each poll tick
 */
internal class DvrController(
    private val engine: PlayerEngine,
    private val loadLive: () -> Unit,
    private val loadRewind: () -> Unit,
    private val trustAll: Boolean,
    private val onState: (DvrState) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val windowDepth = PlayerConstants.DEFAULT_DVR_WINDOW_S

    @Volatile private var dvrAvailable = false
    private var mode = DvrMode.LIVE
    private var hasSeekedToLive = false
    /** Offset (s behind live) to apply once a freshly-loaded source exposes a usable window. */
    private var pendingBehindLive: Double? = null
    private var probed = false
    private var running = false

    private val probeClient: OkHttpClient by lazy { buildProbeClient() }

    val isDvrAvailable: Boolean get() = dvrAvailable

    // ── Lifecycle ────────────────────────────────────────────────────

    fun start() {
        if (running) return
        running = true
        handler.post(pollRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(pollRunnable)
    }

    /** Reset internal state — call when the underlying source changes. */
    fun resetSeek() {
        hasSeekedToLive = false
        pendingBehindLive = null
        mode = DvrMode.LIVE
    }

    // ── Background DVR probe (non-blocking) ──────────────────────────

    /**
     * Detect DVR availability by fetching a tiny rewind playlist. Runs once,
     * off the main thread; on success the seekbar becomes available.
     */
    fun probe(probeUrl: String?) {
        if (probed || probeUrl == null) return
        probed = true
        val request = Request.Builder().url(probeUrl).get().build()
        probeClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // DVR is non-critical — fail silently (seekbar stays hidden).
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful && body.contains("#EXTM3U")) {
                        dvrAvailable = true
                    }
                }
            }
        })
    }

    // ── Polling (mirror of the 500ms seekable poll) ──────────────────

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            handler.postDelayed(this, PlayerConstants.DVR_POLL_MS)
        }
    }

    private fun tick() {
        val w = engine.getLiveWindow()
        if (w == null || w.durationMs <= 0L) {
            handler.removeCallbacks(pollRunnable)
            if (running) handler.postDelayed(pollRunnable, PlayerConstants.DVR_POLL_MS)
            return
        }

        val aStart = 0.0
        val aEnd = w.durationMs / 1000.0
        val posSec = w.positionMs / 1000.0
        val realDepth = aEnd - aStart

        // Apply a pending post-swap seek once the new (rewind) window is ready.
        val pending = pendingBehindLive
        if (pending != null && realDepth > PlayerConstants.DVR_MIN_WINDOW_S) {
            val target = max(aStart + 1, aEnd - pending)
            engine.seekTo((target * 1000).toLong())
            pendingBehindLive = null
        }

        // Snap to the live edge on first load (live mode only). ExoPlayer's
        // LiveConfiguration already targets the latency offset; this guards the
        // case where it doesn't snap.
        if (mode == DvrMode.LIVE && !hasSeekedToLive && realDepth > PlayerConstants.DVR_MIN_WINDOW_S) {
            hasSeekedToLive = true
        }

        val displayEnd = aEnd
        val displayStart = if (mode == DvrMode.REWIND) aStart else aEnd - windowDepth
        val current = min(max(posSec, displayStart), displayEnd)
        val isAtEdge = (aEnd - posSec) < PlayerConstants.LIVE_EDGE_THRESHOLD_S

        onState(
            DvrState(
                available = dvrAvailable,
                currentTime = current,
                seekableStart = displayStart,
                seekableEnd = displayEnd,
                dvrDuration = displayEnd - displayStart,
                isAtLiveEdge = isAtEdge,
                mode = mode
            )
        )
    }

    // ── Seeking ──────────────────────────────────────────────────────

    /**
     * Seek to [newTimeSec] on the virtual timeline (where seekableEnd == live edge).
     */
    fun handleSeek(newTimeSec: Double) {
        val w = engine.getLiveWindow() ?: return
        val liveEdge = w.durationMs / 1000.0
        seekToBehindLive(liveEdge - newTimeSec)
    }

    /** Jump the playhead back to the live edge (and resume the ad-bearing live playlist). */
    fun jumpToLive() {
        seekToBehindLive(0.0)
    }

    /**
     * Move the playhead to [behindLive] seconds behind the live edge, swapping the
     * underlying playlist when the request crosses the live ↔ rewind boundary.
     */
    private fun seekToBehindLive(behindLive: Double) {
        val w = engine.getLiveWindow() ?: return
        val aEnd = w.durationMs / 1000.0

        // At (or near) the live edge → ensure the ad-bearing live playlist is loaded.
        if (behindLive <= PlayerConstants.LIVE_EDGE_THRESHOLD_S) {
            if (mode == DvrMode.REWIND) {
                mode = DvrMode.LIVE
                hasSeekedToLive = false
                pendingBehindLive = null
                loadLive()
            } else {
                engine.seekTo(((aEnd - PlayerConstants.TARGET_LATENCY_S) * 1000).toLong())
            }
            return
        }

        // Behind live.
        if (mode == DvrMode.REWIND) {
            // Already on the full-window rewind playlist → seek in place.
            val target = max(1.0, aEnd - behindLive)
            engine.seekTo((target * 1000).toLong())
        } else {
            // On the (short) live playlist → swap to rewind, apply offset when ready.
            mode = DvrMode.REWIND
            pendingBehindLive = behindLive
            loadRewind()
        }
    }

    // ── Probe HTTP client ────────────────────────────────────────────

    private fun buildProbeClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .callTimeout(PlayerConstants.DVR_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(PlayerConstants.DVR_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PlayerConstants.DVR_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (trustAll) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }
}
