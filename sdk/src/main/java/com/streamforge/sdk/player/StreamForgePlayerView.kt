package com.streamforge.sdk.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.media3.ui.PlayerView
import com.streamforge.sdk.R
import java.net.URL

/**
 * Live embed player view — a 1:1 port of the StreamForge web embed player
 * (`streamforge-frontend/src/app/embed/[token]/page.tsx`).
 *
 * Single responsive layout: bottom control bar (play/pause, volume, LIVE
 * indicator, quality dropdown, fullscreen), a DVR live-rewind seekbar, and
 * loading / buffering / error / big-play / center-action overlays — all brand
 * themed. Controls auto-hide after 3s; tap toggles controls, double-tap toggles
 * play/pause.
 */
class StreamForgePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The player instance attached to this view. Set internally by the SDK. */
    var player: StreamForgePlayer? = null
        internal set

    // ── Theming ──
    private var brandColor: Int = Color.parseColor(PlayerConstants.DEFAULT_BRAND_COLOR)

    // ── State ──
    private var controlsEnabled = true
    private var isPlaying = false
    private var isLoading = true
    private var isBuffering = false
    private var hasError = false
    private var hasPlayed = false
    private var controlsVisible = true
    private var isMuted = false
    private var isFullscreen = false

    private var dvrAvailable = false
    private var dvrDuration = 0.0
    private var seekableStart = 0.0
    private var seekableEnd = 0.0
    private var dvrCurrent = 0.0
    private var isAtLiveEdge = true
    private var userSeeking = false

    private var levels: List<QualityOption> = emptyList()
    private var currentQualityHeight = -1   // -1 = Auto
    private var activeHeight = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    // ── ExoPlayer surface ──
    private val exoPlayerView = PlayerView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.useController = false
        it.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        it.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        it.setBackgroundColor(Color.BLACK)
        addView(it)
    }

    // ── Logo overlay (top-left) ──
    private val logoView = ImageView(context).apply {
        adjustViewBounds = true
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(22)).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(10); leftMargin = dp(12)
        }
        visibility = View.GONE
    }.also { addView(it) }

    // ── Big-play overlay (initial paused) ──
    private val bigPlayView = FrameLayout(context).apply {
        layoutParams = LayoutParams(dp(64), dp(64), Gravity.CENTER)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x1FFFFFFF)
            setStroke(dp(2), 0x33FFFFFF)
        }
        addView(ImageView(context).apply {
            setImageDrawable(icon(R.drawable.sf_ic_play))
            layoutParams = LayoutParams(dp(28), dp(28), Gravity.CENTER)
        })
        visibility = View.GONE
    }.also { addView(it) }

    // ── Center action overlay (YouTube-style play/pause ping) ──
    private val centerActionIcon = ImageView(context).apply {
        layoutParams = LayoutParams(dp(32), dp(32), Gravity.CENTER)
    }
    private val centerActionView = FrameLayout(context).apply {
        layoutParams = LayoutParams(dp(64), dp(64), Gravity.CENTER)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
        addView(centerActionIcon)
        visibility = View.GONE
    }.also { addView(it) }

    // ── Loading overlay ──
    private val loadingPing = PingRingView(context, brandColor).apply {
        layoutParams = LayoutParams(dp(40), dp(40), Gravity.CENTER)
    }
    private val loadingSpinner = SpinnerView(context, brandColor).apply {
        layoutParams = LayoutParams(dp(40), dp(40), Gravity.CENTER)
    }
    private val loadingText = TextView(context).apply {
        text = "Connecting to stream…"
        setTextColor(0xFF9CA3AF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12); gravity = Gravity.CENTER }
        gravity = Gravity.CENTER
    }
    private val loadingOverlay = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(0xCC000000.toInt())
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER }
                addView(loadingPing); addView(loadingSpinner)
            })
            addView(loadingText)
        }
        addView(col)
    }.also { addView(it) }

    // ── Buffering overlay (spinner only, transparent) ──
    private val bufferingSpinner = SpinnerView(context, brandColor).apply {
        layoutParams = LayoutParams(dp(40), dp(40), Gravity.CENTER)
    }
    private val bufferingOverlay = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(bufferingSpinner)
        visibility = View.GONE
    }.also { addView(it) }

    // ── Error overlay ──
    private val errorText = TextView(context).apply {
        setTextColor(0xFFD1D5DB.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }
    private val errorOverlay = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(0xCC000000.toInt())
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(ImageView(context).apply {
                setImageDrawable(icon(R.drawable.sf_ic_warning))
                setColorFilter(0xFFF59E0B.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            addView(errorText)
            addView(buildRetryButton())
        }
        addView(col)
        visibility = View.GONE
    }.also { addView(it) }

    // ── DVR seekbar (above the controls bar) ──
    private val seekBubble = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(8), dp(3), dp(8), dp(3))
        background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(0xD9000000.toInt()) }
        visibility = View.GONE
    }
    private val dvrSeekBar = SeekBar(context).apply {
        max = 1000
        progressTintList = android.content.res.ColorStateList.valueOf(brandColor)
        thumbTintList = android.content.res.ColorStateList.valueOf(brandColor)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x4DFFFFFF)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    }
    private val seekbarContainer = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = dp(52); leftMargin = dp(12); rightMargin = dp(12)
        }
        addView(dvrSeekBar)
        addView(seekBubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        visibility = View.GONE
    }.also { addView(it) }

    // ── Controls bar ──
    private lateinit var playButton: ImageView
    private lateinit var muteButton: ImageView
    private lateinit var volumeSlider: SeekBar
    private lateinit var liveDot: View
    private lateinit var liveText: TextView
    private lateinit var liveButton: LinearLayout
    private lateinit var qualityGroup: LinearLayout
    private lateinit var qualityLabel: TextView
    private lateinit var fullscreenButton: ImageView
    private val controlsBar: FrameLayout = buildControlsBar()

    // ── Gestures ──
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        // Must return true so the detector keeps receiving MOVE/UP after DOWN —
        // otherwise onSingleTapConfirmed / onDoubleTap never fire.
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (controlsEnabled) toggleControls()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            togglePlay()
            return true
        }
    })

    init {
        addView(controlsBar)
        setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
        applyTheme()
        startStallPoll()
    }

    // ═══════════════════════════════════════════════════
    //  Public / internal API
    // ═══════════════════════════════════════════════════

    fun setBrandColor(hex: String) {
        var c = hex.trim()
        if (!c.startsWith("#")) c = "#$c"
        brandColor = try { Color.parseColor(c) } catch (_: Exception) {
            Color.parseColor(PlayerConstants.DEFAULT_BRAND_COLOR)
        }
        applyTheme()
    }

    fun setLogo(url: String?, opacity: Float) {
        logoView.alpha = opacity.coerceIn(0f, 1f)
        if (url.isNullOrBlank()) { logoView.visibility = View.GONE; return }
        loadLogo(url)
    }

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        controlsBar.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) seekbarContainer.visibility = View.GONE
    }

    internal fun attachEngine(engine: PlayerEngine) { exoPlayerView.player = engine.player }
    internal fun detachEngine() { exoPlayerView.player = null }
    fun setResizeMode(resizeMode: Int) { exoPlayerView.resizeMode = resizeMode }

    internal fun onPlaybackStateChanged(state: PlaybackState, playing: Boolean) {
        isPlaying = playing
        when (state) {
            PlaybackState.READY, PlaybackState.PLAYING -> { isLoading = false; hasError = false }
            PlaybackState.LOADING -> { /* keep loading overlay */ }
            else -> {}
        }
        if (playing) hasPlayed = true
        updatePlayButton()
        updateOverlays()
        if (playing) scheduleHide() else showControls()
    }

    internal fun showError(message: String?) {
        hasError = true
        isLoading = false
        errorText.text = message ?: "Network error — stream may be offline"
        updateOverlays()
        showControls()
    }

    internal fun clearError() { hasError = false; updateOverlays() }

    internal fun onDvrState(state: DvrState) {
        dvrAvailable = state.available
        dvrDuration = state.dvrDuration
        seekableStart = state.seekableStart
        seekableEnd = state.seekableEnd
        dvrCurrent = state.currentTime
        isAtLiveEdge = state.isAtLiveEdge
        updateLiveIndicator()
        updateSeekbar()
    }

    internal fun onQualityInfo(available: List<QualityOption>, activeH: Int, currentHeight: Int) {
        levels = available.filter { !it.isAuto }
        activeHeight = activeH
        currentQualityHeight = currentHeight
        updateQualityButton()
    }

    internal fun onMuteChanged(muted: Boolean) {
        isMuted = muted
        muteButton.setImageDrawable(icon(if (muted) R.drawable.sf_ic_vol_off else R.drawable.sf_ic_vol_on))
        if (!muted && volumeSlider.progress == 0) volumeSlider.progress = 100
    }

    internal fun onFullscreenChanged(full: Boolean) {
        isFullscreen = full
        fullscreenButton.setImageDrawable(
            icon(if (full) R.drawable.sf_ic_fs_exit else R.drawable.sf_ic_fs_enter)
        )
    }

    // ═══════════════════════════════════════════════════
    //  Controls bar construction
    // ═══════════════════════════════════════════════════

    private fun buildControlsBar(): FrameLayout {
        val bar = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x66000000, 0x00000000)
            )
            setPadding(dp(12), dp(64), dp(12), dp(12))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Play / pause
        playButton = ctrlIcon(R.drawable.sf_ic_play).apply { setOnClickListener { togglePlay() } }
        row.addView(playButton, lp(dp(24), dp(24)).apply { marginEnd = dp(16) })

        // Volume group (mute button + slider)
        muteButton = ctrlIcon(R.drawable.sf_ic_vol_on).apply { setOnClickListener { player?.toggleMute() } }
        row.addView(muteButton, lp(dp(24), dp(24)).apply { marginEnd = dp(6) })
        volumeSlider = SeekBar(context).apply {
            max = 100; progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x40FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) player?.setVolume(p / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        row.addView(volumeSlider, lp(dp(72), dp(24)).apply { marginEnd = dp(16) })

        // LIVE indicator
        liveDot = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFEF4444.toInt()) }
        }
        liveText = TextView(context).apply {
            text = "LIVE"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        liveButton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(liveDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) })
            addView(liveText)
            setOnClickListener { if (dvrAvailable && !isAtLiveEdge) player?.dvrJumpToLive() }
        }
        row.addView(liveButton, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Spacer
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        // Quality dropdown
        qualityLabel = TextView(context).apply {
            text = "Auto"
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        qualityGroup = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageDrawable(icon(R.drawable.sf_ic_settings))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(6) }
            })
            addView(qualityLabel)
            setOnClickListener { showQualityMenu() }
            visibility = View.GONE
        }
        row.addView(qualityGroup, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(16) })

        // Fullscreen
        fullscreenButton = ctrlIcon(R.drawable.sf_ic_fs_enter).apply {
            setOnClickListener { findActivity()?.let { player?.toggleFullscreen(it) } }
        }
        row.addView(fullscreenButton, lp(dp(24), dp(24)))

        bar.addView(row)
        return bar
    }

    private fun buildRetryButton(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(0x1AFFFFFF)
                setStroke(dp(1), 0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL }
            addView(ImageView(context).apply {
                setImageDrawable(icon(R.drawable.sf_ic_refresh))
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(6) }
            })
            addView(TextView(context).apply {
                text = "Retry"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            setOnClickListener { player?.retryPlayback() }
        }
    }

    // ═══════════════════════════════════════════════════
    //  Quality popup (web-style dropdown)
    // ═══════════════════════════════════════════════════

    private fun showQualityMenu() {
        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xF2171717.toInt())
                setStroke(dp(1), 0x1AFFFFFF)
            }
            val minW = dp(150)
            layoutParams = ViewGroup.LayoutParams(minW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        // Header
        menu.addView(TextView(context).apply {
            text = "QUALITY"
            setTextColor(0xFF737373.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })

        val popup = PopupWindow(menu, dp(160), ViewGroup.LayoutParams.WRAP_CONTENT, true)

        // Auto
        menu.addView(qualityOption("Auto", "ABR", currentQualityHeight == -1) {
            player?.setAutoQuality(); currentQualityHeight = -1; updateQualityButton(); popup.dismiss()
        })
        // Per-level (descending)
        for (lvl in levels.sortedByDescending { it.height }) {
            val badge = "${(lvl.bitrate / 1000)} kbps"
            menu.addView(qualityOption("${lvl.height}p", badge, currentQualityHeight == lvl.height) {
                player?.setQuality(lvl); currentQualityHeight = lvl.height; updateQualityButton(); popup.dismiss()
            })
        }

        popup.elevation = dp(8).toFloat()
        popup.showAsDropDown(qualityGroup, 0, -(qualityGroup.height + dp(220)))
        resetHideTimerWhileVisible()
    }

    private fun qualityOption(label: String, badge: String, selected: Boolean, onClick: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            if (selected) setBackgroundColor(withAlpha(brandColor, 0.08f))
            addView(TextView(context).apply {
                text = label
                setTextColor(if (selected) brandColor else 0xFFD1D5DB.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = badge
                setTextColor(0xFF6B7280.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            })
            setOnClickListener { onClick() }
        }
    }

    // ═══════════════════════════════════════════════════
    //  State → UI
    // ═══════════════════════════════════════════════════

    private fun togglePlay() {
        val p = player ?: return
        if (isPlaying) { p.pause(); showCenterAction(R.drawable.sf_ic_pause) }
        else { p.play(); hasPlayed = true; showCenterAction(R.drawable.sf_ic_play) }
    }

    private fun updatePlayButton() {
        playButton.setImageDrawable(icon(if (isPlaying) R.drawable.sf_ic_pause else R.drawable.sf_ic_play))
    }

    private fun updateLiveIndicator() {
        val atEdge = isAtLiveEdge
        liveText.setTextColor(if (atEdge) Color.WHITE else 0x80FFFFFF.toInt())
        (liveDot.background as? GradientDrawable)?.setColor(
            if (atEdge) 0xFFEF4444.toInt() else 0xFFA3A3A3.toInt()
        )
    }

    private fun updateQualityButton() {
        qualityGroup.visibility = if (levels.size > 1) View.VISIBLE else View.GONE
        qualityLabel.text = when {
            currentQualityHeight == -1 && activeHeight > 0 -> "Auto (${activeHeight}p)"
            currentQualityHeight == -1 -> "Auto"
            else -> "${currentQualityHeight}p"
        }
    }

    private fun updateSeekbar() {
        val visible = controlsEnabled && dvrAvailable && dvrDuration > PlayerConstants.DVR_SEEKBAR_MIN_DURATION_S
        seekbarContainer.visibility = if (visible && (controlsVisible || !isPlaying)) View.VISIBLE else View.GONE
        if (!visible || userSeeking) return
        val pct = if (dvrDuration > 0) ((dvrCurrent - seekableStart) / dvrDuration) else 0.0
        dvrSeekBar.progress = (pct.coerceIn(0.0, 1.0) * 1000).toInt()
    }

    private fun updateOverlays() {
        loadingOverlay.visibility = if (isLoading && !hasError) View.VISIBLE else View.GONE
        bufferingOverlay.visibility = if (isBuffering && !isLoading && !hasError) View.VISIBLE else View.GONE
        errorOverlay.visibility = if (hasError) View.VISIBLE else View.GONE
        bigPlayView.visibility =
            if (!hasPlayed && !isPlaying && !isLoading && !isBuffering && !hasError && controlsEnabled)
                View.VISIBLE else View.GONE
    }

    private fun showCenterAction(iconRes: Int) {
        centerActionIcon.setImageDrawable(icon(iconRes))
        centerActionView.visibility = View.VISIBLE
        centerActionView.scaleX = 0.8f; centerActionView.scaleY = 0.8f; centerActionView.alpha = 1f
        centerActionView.animate()
            .scaleX(1.5f).scaleY(1.5f).alpha(0f)
            .setDuration(PlayerConstants.CENTER_ACTION_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { centerActionView.visibility = View.GONE }
            .start()
    }

    // ── Controls visibility ──

    private fun toggleControls() { if (controlsVisible) hideControls() else showControls() }

    private fun showControls() {
        controlsVisible = true
        if (controlsEnabled) controlsBar.visibility = View.VISIBLE
        updateSeekbar()
        scheduleHide()
    }

    private fun hideControls() {
        if (!isPlaying) return // keep controls while paused (matches web)
        controlsVisible = false
        controlsBar.visibility = View.GONE
        seekbarContainer.visibility = View.GONE
        mainHandler.removeCallbacks(hideRunnable)
    }

    private fun scheduleHide() {
        mainHandler.removeCallbacks(hideRunnable)
        if (isPlaying) mainHandler.postDelayed(hideRunnable, PlayerConstants.CONTROLS_HIDE_MS)
    }

    private fun resetHideTimerWhileVisible() {
        controlsVisible = true
        scheduleHide()
    }

    // ── DVR seekbar listener ──

    private fun initSeekbarListener() {
        dvrSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newTime = seekableStart + (p / 1000.0) * dvrDuration
                val behind = (seekableEnd - newTime).coerceAtLeast(0.0)
                seekBubble.text = "-" + formatOffset(behind)
                seekBubble.visibility = View.VISIBLE
                seekBubble.translationX = ((p / 1000f) * dvrSeekBar.width) - seekBubble.width / 2f
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true; mainHandler.removeCallbacks(hideRunnable) }
            override fun onStopTrackingTouch(sb: SeekBar) {
                userSeeking = false
                seekBubble.visibility = View.GONE
                val newTime = seekableStart + (sb.progress / 1000.0) * dvrDuration
                player?.dvrHandleSeek(newTime)
                scheduleHide()
            }
        })
    }

    // ── Stall detector (buffering overlay, progress-based) ──

    private var lastPos = -1L
    private var lastAdvanceAt = 0L
    private val stallRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && !isLoading && !hasError) {
                val pos = p.currentPosition
                val now = System.currentTimeMillis()
                if (p.isPlaying) {
                    if (pos == lastPos) {
                        if (now - lastAdvanceAt > PlayerConstants.STALL_THRESHOLD_MS && !isBuffering) {
                            isBuffering = true; updateOverlays()
                        }
                    } else {
                        lastPos = pos; lastAdvanceAt = now
                        if (isBuffering) { isBuffering = false; updateOverlays() }
                    }
                } else if (isBuffering) { isBuffering = false; updateOverlays() }
            }
            mainHandler.postDelayed(this, PlayerConstants.STALL_POLL_MS)
        }
    }

    private fun startStallPoll() {
        initSeekbarListener()
        lastAdvanceAt = System.currentTimeMillis()
        mainHandler.postDelayed(stallRunnable, PlayerConstants.STALL_POLL_MS)
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    // ═══════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════

    private fun applyTheme() {
        dvrSeekBar.progressTintList = android.content.res.ColorStateList.valueOf(brandColor)
        dvrSeekBar.thumbTintList = android.content.res.ColorStateList.valueOf(brandColor)
        loadingSpinner.setBrandColor(brandColor)
        bufferingSpinner.setBrandColor(brandColor)
        loadingPing.setBrandColor(brandColor)
    }

    private fun ctrlIcon(res: Int): ImageView = ImageView(context).apply {
        setImageDrawable(icon(res))
        isClickable = true; isFocusable = true
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun icon(res: Int) = AppCompatResources.getDrawable(context, res)

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun formatOffset(seconds: Double): String {
        val s = seconds.toInt().coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    private fun findActivity(): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    private fun loadLogo(url: String) {
        Thread {
            try {
                val bmp: Bitmap? = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null) mainHandler.post {
                    logoView.setImageBitmap(bmp); logoView.visibility = View.VISIBLE
                }
            } catch (_: Exception) { /* ignore logo failures */ }
        }.start()
    }
}
