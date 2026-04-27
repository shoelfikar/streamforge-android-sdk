package com.streamforge.sdk.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer

internal class QualityManager {

    private var selectedQuality: QualityOption = QualityOption.AUTO

    val currentQuality: QualityOption
        get() = selectedQuality

    fun getAvailableQualities(player: ExoPlayer?): List<QualityOption> {
        val qualities = mutableListOf(QualityOption.AUTO)
        val exoPlayer = player ?: return qualities

        val tracks = exoPlayer.currentTracks
        val seen = mutableSetOf<String>()

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.width > 0 && format.height > 0) {
                    val key = "${format.width}x${format.height}"
                    if (seen.add(key)) {
                        qualities.add(
                            QualityOption.fromResolution(
                                format.width,
                                format.height,
                                format.bitrate.coerceAtLeast(0)
                            )
                        )
                    }
                }
            }
        }

        // Sort by height descending (highest quality first), Auto stays at index 0
        return listOf(qualities.first()) + qualities.drop(1).sortedByDescending { it.height }
    }

    fun setQuality(player: ExoPlayer?, option: QualityOption) {
        val exoPlayer = player ?: return

        if (option.isAuto) {
            setAutoQuality(player)
            return
        }

        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        paramsBuilder.setMaxVideoSize(option.width, option.height)
        paramsBuilder.setMinVideoSize(option.width, option.height)

        exoPlayer.trackSelectionParameters = paramsBuilder.build()
        selectedQuality = option
    }

    fun setAutoQuality(player: ExoPlayer?) {
        val exoPlayer = player ?: return

        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        paramsBuilder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        paramsBuilder.setMinVideoSize(0, 0)
        paramsBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)

        exoPlayer.trackSelectionParameters = paramsBuilder.build()
        selectedQuality = QualityOption.AUTO
    }
}
