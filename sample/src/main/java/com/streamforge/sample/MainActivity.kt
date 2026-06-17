package com.streamforge.sample

import android.content.pm.ActivityInfo
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
import com.streamforge.sdk.exception.StreamForgeException
import com.streamforge.sdk.player.StreamForgeErrorView
import com.streamforge.sdk.player.StreamForgePlayerView

class MainActivity : AppCompatActivity() {

    private var playerView: StreamForgePlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow both portrait and landscape (player handles its own fullscreen rotation)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        val container = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        setContentView(container)
        enableImmersive()

        // ────────────────────────────────────────────────
        // GANTI DENGAN VALUE KAMU:
        val apiKey   = "sf_live_YVcX22MkKrf2E0l-LG66w35JhEaGFXImVhtfV4iffOo"
        val streamId = "eed851bc-94c4-4fd9-9479-37864de25a9c"
        // ────────────────────────────────────────────────

        // One-call setup. The player view renders the full web-style control bar,
        // DVR seekbar, quality menu, and overlays internally — no extra wiring needed.
        StreamForge.createPlayer(
            context = this,
            apiKey = apiKey,
            streamId = streamId,
            playerParameters = StreamForgeConfig.builder()
                .enableLogging(true)
                .trustAllCertificates(true)
                .autoplay(true)
                .minBitrateKbps(500)
                .maxBitrateKbps(8000)
                .build(),
            playerSetupListener = object : StreamForge.PlayerSetupListener {
                override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
                    playerView = player
                    container.addView(player)
                    Log.d("StreamForge", "Player setup success — stream is playing")
                }

                override fun onPlayerSetupFailed(error: StreamForgeException, errorView: StreamForgeErrorView) {
                    Log.e("StreamForge", "Player setup failed", error)
                    errorView.setOnBackClickListener { finish() }
                    errorView.setOnRetryClickListener {
                        container.removeAllViews()
                        recreate()
                    }
                    container.addView(errorView)
                }
            }
        )
    }

    private fun enableImmersive() {
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
    }
}
