package com.streamforge.sdk.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.ui.PlayerView

class StreamForgePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The player instance attached to this view. Set internally by SDK. */
    var player: StreamForgePlayer? = null
        internal set

    /** Whether the title overlay is shown. Defaults to true. */
    var showTitle: Boolean = true
        set(value) {
            field = value
            rebuildOverlay()
        }

    private var onBackClickListener: (() -> Unit)? = null
    private var onQualityClickListener: (() -> Unit)? = null
    private var onPipClickListener: (() -> Unit)? = null
    private var onFullscreenClickListener: (() -> Unit)? = null
    private var onVolumeClickListener: (() -> Unit)? = null

    private var overlayVisible = false
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }
    private val overlayTimeoutMs = 4000L

    // State
    private var titleText: String = ""
    private var subtitleText: String = ""
    private var viewerCountText: String = ""
    private var isLive: Boolean = false
    private var isMuted: Boolean = false
    private var currentQualityLabel: String = "Auto"
    private var isPortrait: Boolean = true

    // ── ExoPlayer surface ──
    private val exoPlayerView: PlayerView = PlayerView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.setShowNextButton(false)
        it.setShowPreviousButton(false)
        it.setShowFastForwardButton(false)
        it.setShowRewindButton(false)
        it.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        it.useController = false
        addView(it)
    }

    // ── Overlay container ──
    private val overlayContainer: FrameLayout = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        visibility = View.GONE
    }.also { addView(it) }

    init {
        isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        rebuildOverlay()
        setOnClickListener { toggleOverlay() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nowPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (nowPortrait != isPortrait) {
            isPortrait = nowPortrait
            rebuildOverlay()
        }
    }

    // ── Public API ──

    fun setTitle(title: String?) {
        titleText = title ?: ""
        rebuildOverlay()
    }

    fun setSubtitle(subtitle: String?) {
        subtitleText = subtitle ?: ""
        rebuildOverlay()
    }

    fun setViewerCount(count: String?) {
        viewerCountText = count ?: ""
        rebuildOverlay()
    }

    fun setLiveStatus(live: Boolean) {
        isLive = live
        rebuildOverlay()
    }

    fun setMuteState(muted: Boolean) {
        isMuted = muted
        rebuildOverlay()
    }

    fun setCurrentQualityLabel(label: String) {
        currentQualityLabel = label
        rebuildOverlay()
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        onBackClickListener = listener
    }

    fun setOnQualityClickListener(listener: () -> Unit) {
        onQualityClickListener = listener
    }

    fun setOnPipClickListener(listener: () -> Unit) {
        onPipClickListener = listener
    }

    fun setOnFullscreenClickListener(listener: () -> Unit) {
        onFullscreenClickListener = listener
    }

    fun setOnVolumeClickListener(listener: () -> Unit) {
        onVolumeClickListener = listener
    }

    fun setPipButtonVisible(visible: Boolean) {
        overlayContainer.findViewWithTag<View>("pip_button")?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    fun showQualitySelector(
        qualities: List<QualityOption>,
        currentQuality: QualityOption,
        onSelected: (QualityOption) -> Unit
    ) {
        val activity = context as? Activity ?: return
        val labels = qualities.map { option ->
            if (option.isAuto) "Auto" else "${option.label} (${option.width}x${option.height})"
        }.toTypedArray()

        val checkedIndex = qualities.indexOfFirst {
            it.isAuto == currentQuality.isAuto && it.height == currentQuality.height
        }

        AlertDialog.Builder(activity)
            .setTitle("Video Quality")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                onSelected(qualities[which])
                dialog.dismiss()
            }
            .show()
    }

    internal fun onPipModeChanged(isInPip: Boolean) {
        if (isInPip) hideOverlay()
    }

    internal fun attachEngine(engine: PlayerEngine) {
        exoPlayerView.player = engine.player
    }

    internal fun detachEngine() {
        exoPlayerView.player = null
    }

    fun setResizeMode(resizeMode: Int) {
        exoPlayerView.resizeMode = resizeMode
    }

    // ── Overlay visibility ──

    private fun toggleOverlay() {
        if (overlayVisible) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        overlayVisible = true
        overlayContainer.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, overlayTimeoutMs)
    }

    private fun hideOverlay() {
        overlayVisible = false
        overlayContainer.visibility = View.GONE
        hideHandler.removeCallbacks(hideRunnable)
    }

    // ── Build overlay based on orientation ──

    private fun rebuildOverlay() {
        overlayContainer.removeAllViews()
        if (isPortrait) {
            buildPortraitOverlay()
        } else {
            buildLandscapeOverlay()
        }
        // Restore visibility state
        overlayContainer.visibility = if (overlayVisible) View.VISIBLE else View.GONE
    }

    // ═══════════════════════════════════════════════════
    //  PORTRAIT LAYOUT
    // ═══════════════════════════════════════════════════

    private fun buildPortraitOverlay() {
        // Top gradient overlay
        val topGradient = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(120f).toInt()).apply {
                gravity = Gravity.TOP
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        overlayContainer.addView(topGradient)

        // Bottom gradient overlay
        val bottomGradient = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(180f).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        overlayContainer.addView(bottomGradient)

        // ── Top row: back button (left) + LIVE badge + viewer count (right) ──
        val topBar = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = dpToPx(16f).toInt()
                leftMargin = dpToPx(16f).toInt()
                rightMargin = dpToPx(16f).toInt()
            }
        }

        // Back button
        topBar.addView(createBackButton().apply {
            layoutParams = LayoutParams(dpToPx(40f).toInt(), dpToPx(40f).toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        })

        // Right section: LIVE badge + viewer count
        val topRight = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        if (isLive) {
            topRight.addView(createLiveBadge())
        }

        if (viewerCountText.isNotEmpty()) {
            topRight.addView(createViewerCount())
        }

        topBar.addView(topRight)
        overlayContainer.addView(topBar)

        // ── Bottom section ──
        val bottomSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dpToPx(16f).toInt()
                leftMargin = dpToPx(16f).toInt()
                rightMargin = dpToPx(16f).toInt()
            }
        }

        // Info card (title + subtitle)
        if (showTitle && (titleText.isNotEmpty() || subtitleText.isNotEmpty())) {
            bottomSection.addView(createInfoCard())
        }

        // Bottom controls row
        val bottomControls = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f).toInt()
            }
        }

        // Volume button (left)
        bottomControls.addView(createVolumeButton().apply {
            layoutParams = LayoutParams(dpToPx(40f).toInt(), dpToPx(40f).toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        })

        // Right controls: quality + fullscreen
        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        rightControls.addView(createQualityButton())
        rightControls.addView(createFullscreenButton())

        bottomControls.addView(rightControls)
        bottomSection.addView(bottomControls)
        overlayContainer.addView(bottomSection)
    }

    // ═══════════════════════════════════════════════════
    //  LANDSCAPE LAYOUT
    // ═══════════════════════════════════════════════════

    private fun buildLandscapeOverlay() {
        // Top gradient
        val topGradient = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(100f).toInt()).apply {
                gravity = Gravity.TOP
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        overlayContainer.addView(topGradient)

        // Bottom gradient
        val bottomGradient = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(80f).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        overlayContainer.addView(bottomGradient)

        // ── Top bar: [Back] [Icon] [Title/Subtitle] ... [LIVE] [Viewers] ──
        val topBar = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = dpToPx(16f).toInt()
                leftMargin = dpToPx(20f).toInt()
                rightMargin = dpToPx(20f).toInt()
            }
        }

        // Left section: back + icon + title/subtitle
        val topLeft = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        }

        topLeft.addView(createBackButton())

        if (showTitle && (titleText.isNotEmpty() || subtitleText.isNotEmpty())) {
            // SDK icon
            topLeft.addView(createSdkIcon().apply {
                val lp = layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(dpToPx(36f).toInt(), dpToPx(36f).toInt())
                lp.marginStart = dpToPx(12f).toInt()
                layoutParams = lp
            })

            // Title + Subtitle vertical
            val titleBlock = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(10f).toInt()
                }
            }

            if (titleText.isNotEmpty()) {
                titleBlock.addView(TextView(context).apply {
                    text = titleText
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    setSingleLine(true)
                })
            }

            if (subtitleText.isNotEmpty()) {
                titleBlock.addView(TextView(context).apply {
                    text = subtitleText
                    setTextColor(0xFFAAAAAA.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 1
                    setSingleLine(true)
                })
            }

            topLeft.addView(titleBlock)
        }

        topBar.addView(topLeft)

        // Right section: LIVE + viewer count
        val topRight = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        if (isLive) {
            topRight.addView(createLiveBadge())
        }

        if (viewerCountText.isNotEmpty()) {
            topRight.addView(createViewerCount())
        }

        topBar.addView(topRight)
        overlayContainer.addView(topBar)

        // ── Bottom bar: [Volume] ... [Quality] [PiP] [Fullscreen] ──
        val bottomBar = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dpToPx(16f).toInt()
                leftMargin = dpToPx(20f).toInt()
                rightMargin = dpToPx(20f).toInt()
            }
        }

        // Volume (left)
        bottomBar.addView(createVolumeButton().apply {
            layoutParams = LayoutParams(dpToPx(40f).toInt(), dpToPx(40f).toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        })

        // Right controls
        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        rightControls.addView(createQualityButton())

        // PiP button
        rightControls.addView(createPipButton())

        rightControls.addView(createFullscreenButton())

        bottomBar.addView(rightControls)
        overlayContainer.addView(bottomBar)
    }

    // ═══════════════════════════════════════════════════
    //  REUSABLE UI COMPONENTS
    // ═══════════════════════════════════════════════════

    private fun createBackButton(): FrameLayout {
        val size = dpToPx(40f).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x55000000.toInt())
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { onBackClickListener?.invoke() }

            addView(TextView(context).apply {
                text = "\u276E" // ❮ left chevron
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    private fun createLiveBadge(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val px6 = dpToPx(6f).toInt()
            val px12 = dpToPx(12f).toInt()
            setPadding(px12, px6, px12, px6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(8f).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14f)
                setColor(0xFFDC2626.toInt()) // Red
            }

            // Red dot
            addView(View(context).apply {
                val dotSize = dpToPx(6f).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginEnd = dpToPx(5f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
            })

            // "LIVE" text
            addView(TextView(context).apply {
                text = "LIVE"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createViewerCount(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(10f).toInt()
            }

            // Eye icon
            addView(TextView(context).apply {
                text = "\uD83D\uDC41" // 👁 eye emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(4f).toInt()
                }
            })

            // Count text
            addView(TextView(context).apply {
                text = viewerCountText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createSdkIcon(): FrameLayout {
        val size = dpToPx(36f).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8f)
                setColor(0xFF1A3A2A.toInt()) // Dark green
            }

            addView(TextView(context).apply {
                text = "SDK"
                setTextColor(0xFF4ADE80.toInt()) // Green text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    private fun createInfoCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val px14 = dpToPx(14f).toInt()
            val px10 = dpToPx(10f).toInt()
            setPadding(px14, px10, px14, px10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(0xE61A1A2E.toInt()) // Dark semi-transparent
            }

            // SDK icon
            addView(createSdkIcon().apply {
                val lp = layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(dpToPx(40f).toInt(), dpToPx(40f).toInt())
                lp.width = dpToPx(40f).toInt()
                lp.height = dpToPx(40f).toInt()
                layoutParams = lp
            })

            // Title + Subtitle
            val textBlock = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(12f).toInt()
                }
            }

            if (titleText.isNotEmpty()) {
                textBlock.addView(TextView(context).apply {
                    text = titleText
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                })
            }

            if (subtitleText.isNotEmpty()) {
                textBlock.addView(TextView(context).apply {
                    text = subtitleText
                    setTextColor(0xFF999999.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 1
                    setSingleLine(true)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(2f).toInt()
                    }
                })
            }

            addView(textBlock)
        }
    }

    private fun createVolumeButton(): FrameLayout {
        val size = dpToPx(40f).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            contentDescription = if (isMuted) "Unmute" else "Mute"
            setOnClickListener { onVolumeClickListener?.invoke() }

            addView(TextView(context).apply {
                text = if (isMuted) "\uD83D\uDD07" else "\uD83D\uDD0A" // 🔇 or 🔊
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    private fun createQualityButton(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val px10 = dpToPx(10f).toInt()
            val px6 = dpToPx(6f).toInt()
            setPadding(px10, px6, px10, px6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8f).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6f)
                setColor(0x55000000.toInt())
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Quality"
            setOnClickListener { onQualityClickListener?.invoke() }

            // Quality label (e.g., "1080p")
            addView(TextView(context).apply {
                text = currentQualityLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Dropdown arrow
            addView(TextView(context).apply {
                text = " \u25BE" // ▾
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createPipButton(): FrameLayout {
        val size = dpToPx(36f).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8f).toInt()
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Picture-in-Picture"
            tag = "pip_button"
            visibility = View.GONE
            setOnClickListener { onPipClickListener?.invoke() }

            addView(TextView(context).apply {
                text = "\u29C9" // ⧉ PiP icon
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    private fun createFullscreenButton(): FrameLayout {
        val size = dpToPx(36f).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            contentDescription = "Fullscreen"
            setOnClickListener { onFullscreenClickListener?.invoke() }

            addView(TextView(context).apply {
                text = "\u26F6" // ⛶ fullscreen icon
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    // ── Utility ──

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
