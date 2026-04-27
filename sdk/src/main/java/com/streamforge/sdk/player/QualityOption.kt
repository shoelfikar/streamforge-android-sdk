package com.streamforge.sdk.player

/**
 * Represents a video quality track available for selection.
 *
 * Use [QualityOption.AUTO] for adaptive bitrate (ABR), or select a specific
 * quality from [StreamForgePlayer.getAvailableQualities].
 *
 * @property width Video width in pixels.
 * @property height Video height in pixels.
 * @property bitrate Video bitrate in bps.
 * @property label Human-readable label (e.g., "1080p", "720p", "Auto").
 * @property isAuto Whether this represents automatic quality selection.
 */
data class QualityOption(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val label: String,
    val isAuto: Boolean = false
) {
    companion object {
        val AUTO = QualityOption(
            width = 0,
            height = 0,
            bitrate = 0,
            label = "Auto",
            isAuto = true
        )

        fun fromResolution(width: Int, height: Int, bitrate: Int): QualityOption {
            val label = when {
                height >= 2160 -> "4K"
                height >= 1440 -> "1440p"
                height >= 1080 -> "1080p"
                height >= 720 -> "720p"
                height >= 480 -> "480p"
                height >= 360 -> "360p"
                height >= 240 -> "240p"
                else -> "${height}p"
            }
            return QualityOption(width = width, height = height, bitrate = bitrate, label = label)
        }
    }
}
