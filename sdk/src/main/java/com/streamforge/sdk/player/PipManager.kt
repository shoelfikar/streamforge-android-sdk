package com.streamforge.sdk.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.media3.exoplayer.ExoPlayer

internal class PipManager {

    fun isPipSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPip(activity: Activity, player: ExoPlayer?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isPipSupported(activity)) return

        val videoSize = player?.videoSize
        val aspectRatio = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational(16, 9)
        }

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()

        activity.enterPictureInPictureMode(params)
    }
}
