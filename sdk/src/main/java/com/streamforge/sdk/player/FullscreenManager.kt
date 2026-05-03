package com.streamforge.sdk.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout

internal class FullscreenManager {

    var isFullscreen: Boolean = false
        private set

    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun enterFullscreen(activity: Activity, playerView: StreamForgePlayerView?) {
        if (isFullscreen) return

        originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerView?.let { view ->
            originalLayoutParams = view.layoutParams?.let { lp ->
                // Clone the layout params
                when (lp) {
                    is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(lp)
                    is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(lp)
                    else -> ViewGroup.LayoutParams(lp)
                }
            }
            view.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        hideSystemBars(activity)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isFullscreen = true
    }

    fun exitFullscreen(activity: Activity, playerView: StreamForgePlayerView?) {
        if (!isFullscreen) return

        activity.requestedOrientation = originalOrientation

        playerView?.let { view ->
            originalLayoutParams?.let { lp ->
                view.layoutParams = lp
            }
        }

        showSystemBars(activity)

        isFullscreen = false
    }

    private fun hideSystemBars(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    private fun showSystemBars(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
