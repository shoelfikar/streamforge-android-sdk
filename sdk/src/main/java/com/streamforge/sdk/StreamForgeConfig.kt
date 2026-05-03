package com.streamforge.sdk

/**
 * Configuration for the StreamForge SDK.
 *
 * Use [StreamForgeConfig.builder] to construct:
 * ```kotlin
 * val config = StreamForgeConfig.builder()
 *     .enableLogging(true)
 *     .minBitrateKbps(500)
 *     .maxBitrateKbps(8000)
 *     .build()
 * ```
 *
 * @property baseUrl API base URL. Defaults to the StreamForge production server.
 * @property enableLogging Enable HTTP request/response logging.
 * @property trustAllCertificates Bypass SSL certificate validation (for development only).
 * @property minBitrateKbps Minimum adaptive bitrate in Kbps.
 * @property maxBitrateKbps Maximum adaptive bitrate in Kbps.
 * @property maxVideoWidth Maximum video width for adaptive bitrate selection.
 * @property maxVideoHeight Maximum video height for adaptive bitrate selection.
 * @property bufferForPlaybackMs Minimum buffer before playback starts (ms).
 * @property bufferForPlaybackAfterRebufferMs Minimum buffer after rebuffer before playback resumes (ms).
 * @property enableSse Enable SSE (Server-Sent Events) for real-time stream status. When false, uses polling only.
 */
data class StreamForgeConfig(
    val baseUrl: String = Companion.BASE_URL,
    val enableLogging: Boolean = false,
    val trustAllCertificates: Boolean = false,
    val enableSse: Boolean = false,
    // ABR (Adaptive Bitrate) configuration
    val minBitrateKbps: Int? = null,
    val maxBitrateKbps: Int? = null,
    val maxVideoWidth: Int? = null,
    val maxVideoHeight: Int? = null,
    val bufferForPlaybackMs: Int = 2500,
    val bufferForPlaybackAfterRebufferMs: Int = 5000
) {
    class Builder {
        private var baseUrl: String = BASE_URL
        private var enableLogging: Boolean = false
        private var trustAllCertificates: Boolean = false
        private var enableSse: Boolean = true
        private var minBitrateKbps: Int? = null
        private var maxBitrateKbps: Int? = null
        private var maxVideoWidth: Int? = null
        private var maxVideoHeight: Int? = null
        private var bufferForPlaybackMs: Int = 2500
        private var bufferForPlaybackAfterRebufferMs: Int = 5000

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun enableLogging(enable: Boolean) = apply { this.enableLogging = enable }
        fun trustAllCertificates(trust: Boolean) = apply { this.trustAllCertificates = trust }
        fun enableSse(enable: Boolean) = apply { this.enableSse = enable }
        fun minBitrateKbps(kbps: Int) = apply { this.minBitrateKbps = kbps }
        fun maxBitrateKbps(kbps: Int) = apply { this.maxBitrateKbps = kbps }
        fun maxVideoWidth(width: Int) = apply { this.maxVideoWidth = width }
        fun maxVideoHeight(height: Int) = apply { this.maxVideoHeight = height }
        fun bufferForPlaybackMs(ms: Int) = apply { this.bufferForPlaybackMs = ms }
        fun bufferForPlaybackAfterRebufferMs(ms: Int) = apply { this.bufferForPlaybackAfterRebufferMs = ms }

        fun build(): StreamForgeConfig {
            return StreamForgeConfig(
                baseUrl = baseUrl,
                enableLogging = enableLogging,
                trustAllCertificates = trustAllCertificates,
                enableSse = enableSse,
                minBitrateKbps = minBitrateKbps,
                maxBitrateKbps = maxBitrateKbps,
                maxVideoWidth = maxVideoWidth,
                maxVideoHeight = maxVideoHeight,
                bufferForPlaybackMs = bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs
            )
        }
    }

    companion object {
        internal const val BASE_URL = "https://docs.ctree.id"
        fun builder() = Builder()
    }
}
