package com.streamforge.sample

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.streamforge.sdk.StreamForge
import com.streamforge.sdk.StreamForgeConfig
import com.streamforge.sdk.cast.StreamForgeCast
import com.streamforge.sdk.exception.StreamForgeException
import com.streamforge.sdk.player.QualityOption
import com.streamforge.sdk.player.StreamForgeErrorView
import com.streamforge.sdk.player.StreamForgePlayerView

class MainActivity : AppCompatActivity() {

    private var playerView: StreamForgePlayerView? = null
    private var currentQuality: QualityOption = QualityOption.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow both portrait and landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        // Container for the player view
        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
        }
        setContentView(container)

        // Fullscreen immersive (must be after setContentView)
        enableFullscreen()

        // Initialize Chromecast (optional — skip gracefully if Cast SDK unavailable)
        try {
            StreamForgeCast.initialize(this)
        } catch (e: Exception) {
            Log.w("StreamForge", "Chromecast init failed (non-fatal): ${e.message}")
        }

        // ────────────────────────────────────────────────
        // GANTI DENGAN VALUE KAMU:
        val apiKey   = "sf_live_YVcX22MkKrf2E0l-LG66w35JhEaGFXImVhtfV4iffOo"
        val streamId = "eed851bc-94c4-4fd9-9479-37864de25a9c"
        // ────────────────────────────────────────────────

        StreamForge.createPlayer(
            context = this,
            apiKey = apiKey,
            streamId = streamId,
            playerParameters = StreamForgeConfig.builder()
                .enableLogging(true)
                .trustAllCertificates(true)
                .minBitrateKbps(500)
                .maxBitrateKbps(8000)
                .build(),
            playerSetupListener = object : StreamForge.PlayerSetupListener {
                override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
                    playerView = player
                    container.addView(player)

                    val sfPlayer = player.player ?: return

                    // ── Set stream metadata ──
                    player.setSubtitle("Metro TV")
                    player.setLiveStatus(true)
                    player.setViewerCount("45.2K")

                    // ── Wire up control bar buttons ──

                    // Back button — finish activity
                    player.setOnBackClickListener {
                        this@MainActivity.finish()
                    }

                    // Volume toggle
                    player.setOnVolumeClickListener {
                        sfPlayer.toggleMute()
                    }

                    // Quality selector
                    player.setOnQualityClickListener {
                        val qualities = sfPlayer.getAvailableQualities()
                        if (qualities.isNotEmpty()) {
                            player.showQualitySelector(qualities, currentQuality) { selected ->
                                currentQuality = selected
                                if (selected.isAuto) {
                                    sfPlayer.setAutoQuality()
                                    player.setCurrentQualityLabel("Auto")
                                } else {
                                    sfPlayer.setQuality(selected)
                                    player.setCurrentQualityLabel(selected.label)
                                }
                                Log.d("StreamForge", "Quality selected: ${selected.label}")
                            }
                        }
                    }

                    // PiP button
                    if (sfPlayer.isPipSupported()) {
                        player.setPipButtonVisible(true)
                        player.setOnPipClickListener {
                            sfPlayer.enterPip(this@MainActivity)
                        }
                    }

                    Log.d("StreamForge", "Player setup success — stream is playing")
                }

                override fun onPlayerSetupFailed(error: StreamForgeException, errorView: StreamForgeErrorView) {
                    Log.e("StreamForge", "Player setup failed", error)
                    errorView.setOnBackClickListener { this@MainActivity.finish() }
                    errorView.setOnRetryClickListener {
                        container.removeAllViews()
                        recreate()
                    }
                    container.addView(errorView)
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView?.player?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP when user presses home
        val p = playerView?.player ?: return
        if (p.isPipSupported() && p.isPlaying) {
            p.enterPip(this)
        }
    }

    private fun enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView?.player?.release()
        playerView = null
        StreamForgeCast.release()
    }
}
