package com.streamforge.sdk.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
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
            if (!value) topBar.visibility = View.GONE
        }

    private var onBackClickListener: (() -> Unit)? = null
    private var onQualityClickListener: (() -> Unit)? = null
    private var onPipClickListener: (() -> Unit)? = null

    private var overlayVisible = false
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }
    private val overlayTimeoutMs = 4000L

    private val playerView: PlayerView = PlayerView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.setShowNextButton(false)
        it.setShowPreviousButton(false)
        it.setShowFastForwardButton(false)
        it.setShowRewindButton(false)
        it.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        it.useController = false // Hide all default ExoPlayer controls (play/pause, bottom bar)
        addView(it)
    }

    // ── Top bar (back button + title) ──

    private val topBar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val px8 = dpToPx(8f).toInt()
        setPadding(px8, px8, px8, px8)
        visibility = View.GONE
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }.also { addView(it) }

    private val backButton: FrameLayout = createIconButton("←", "Back", sizeDp = 44f) {
        onBackClickListener?.invoke()
    }.also { topBar.addView(it) }

    private val titleView: TextView = TextView(context).apply {
        val px8 = dpToPx(8f).toInt()
        setPadding(px8, 0, px8, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.WHITE)
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }.also { topBar.addView(it) }

    // ── Control bar (bottom-right overlay) ──

    private val controlBar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val px8 = dpToPx(8f).toInt()
        setPadding(px8, px8, px8, px8)
        visibility = View.GONE
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = dpToPx(12f).toInt()
            rightMargin = dpToPx(8f).toInt()
        }
    }.also { addView(it) }

    init {
        rebuildControlBar()
        // Tap on player toggles overlay (title + control bar)
        setOnClickListener { toggleOverlay() }
    }

    fun setTitle(title: String?) {
        titleView.text = title ?: ""
    }

    private fun toggleOverlay() {
        if (overlayVisible) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        overlayVisible = true
        if (showTitle) {
            topBar.visibility = View.VISIBLE
        }
        controlBar.visibility = View.VISIBLE
        // Auto-hide after timeout
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, overlayTimeoutMs)
    }

    private fun hideOverlay() {
        overlayVisible = false
        topBar.visibility = View.GONE
        controlBar.visibility = View.GONE
        hideHandler.removeCallbacks(hideRunnable)
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

    fun setPipButtonVisible(visible: Boolean) {
        controlBar.findViewWithTag<View>("pip_button")?.visibility =
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
        if (isInPip) {
            hideOverlay()
        }
        // useController stays false — we manage overlay manually
    }

    internal fun attachEngine(engine: PlayerEngine) {
        playerView.player = engine.player
    }

    internal fun detachEngine() {
        playerView.player = null
    }

    fun setResizeMode(resizeMode: Int) {
        playerView.resizeMode = resizeMode
    }

    private fun rebuildControlBar() {
        controlBar.removeAllViews()

        controlBar.addView(createIconButton("⚙", "Quality") {
            onQualityClickListener?.invoke()
        })
        controlBar.addView(createIconButton("⧉", "PiP") {
            onPipClickListener?.invoke()
        }.apply {
            visibility = View.GONE
            tag = "pip_button"
        })
    }

    private fun createIconButton(
        iconText: String,
        description: String,
        sizeDp: Float = 36f,
        onClick: () -> Unit
    ): FrameLayout {
        val size = dpToPx(sizeDp).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = dpToPx(4f).toInt()
                marginEnd = dpToPx(4f).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x80000000.toInt())
            }
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = iconText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(size, size)
            })
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
