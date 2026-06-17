package com.streamforge.sdk

import android.content.Context
import com.streamforge.sdk.api.ApiClient
import com.streamforge.sdk.exception.SFNotInitializedException
import com.streamforge.sdk.model.TenantConfig
import com.streamforge.sdk.exception.StreamForgeException
import com.streamforge.sdk.player.PlayerConstants
import com.streamforge.sdk.player.StreamForgeErrorView
import com.streamforge.sdk.player.StreamForgePlayer
import com.streamforge.sdk.player.StreamForgePlayerView
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry point for the StreamForge SDK.
 *
 * Provides methods to initialize the SDK, create players, and manage playback.
 *
 * **Quick start (recommended):**
 * ```kotlin
 * StreamForge.createPlayer(
 *     context = this,
 *     apiKey = "sf_live_...",
 *     streamId = "your-stream-id",
 *     playerSetupListener = object : StreamForge.PlayerSetupListener {
 *         override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
 *             container.addView(player)
 *         }
 *         override fun onPlayerSetupFailed(error: StreamForgeException) {
 *             Log.e("StreamForge", "Setup failed", error)
 *         }
 *     }
 * )
 * ```
 *
 * @see StreamForgeConfig
 * @see StreamForgePlayerView
 */
object StreamForge {

    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var _isInitialized = false
    @Volatile private var _apiClient: ApiClient? = null
    @Volatile private var _tenantConfig: TenantConfig? = null
    @Volatile internal var _apiKey: String? = null
    @Volatile private var _streamUrl: String? = null
    @Volatile private var _rawStreamUrl: String? = null
    @Volatile private var _streamTitle: String? = null
    @Volatile private var _streamId: String? = null
    @Volatile internal var _config: StreamForgeConfig? = null

    /** Whether the SDK has been successfully initialized. */
    val isInitialized: Boolean get() = _isInitialized

    /** The tenant configuration returned from the server. Throws [SFNotInitializedException] if not initialized. */
    val tenantConfig: TenantConfig
        get() = _tenantConfig ?: throw SFNotInitializedException()
    val streamUrl: String?
        get() = _streamUrl
    val streamTitle: String?
        get() = _streamTitle
    val streamId: String?
        get() = _streamId

    /**
     * Live playback URL: fMP4 variant of the raw stream URL with the API key token.
     * Mirrors the web embed: `toFmp4Url(appendTokenToHlsUrl(stream.url, apiKey))`.
     */
    internal val liveUrl: String?
        get() {
            val raw = _rawStreamUrl ?: return _streamUrl
            return appendToken(toFmp4(raw), _apiKey)
        }

    /**
     * Full-window (24h) rewind/DVR playback URL for live-rewind.
     * Mirrors the web: `raw.replace("/index.m3u8", "/rewind-86400.fmp4.m3u8")` + token.
     */
    internal val rewindUrl: String?
        get() {
            val raw = _rawStreamUrl ?: return null
            if (!raw.contains("/index.m3u8")) return null
            return appendToken(raw.replace("/index.m3u8", "/rewind-86400.fmp4.m3u8"), _apiKey)
        }

    /**
     * Small-window rewind URL used only to DETECT DVR availability (near-empty playlist).
     * Mirrors the web: `raw.replace("/index.m3u8", "/rewind-60.m3u8")` + token.
     */
    internal val rewindProbeUrl: String?
        get() {
            val raw = _rawStreamUrl ?: return null
            if (!raw.contains("/index.m3u8")) return null
            return appendToken(
                raw.replace("/index.m3u8", "/rewind-${PlayerConstants.DVR_PROBE_WINDOW_S}.m3u8"),
                _apiKey
            )
        }

    /** Convert an HLS playlist URL to its fMP4 variant (index.m3u8 → index.fmp4.m3u8). */
    private fun toFmp4(url: String): String =
        url.replace(Regex("\\.m3u8(?=$|\\?)"), ".fmp4.m3u8")

    /** Append `?token=<apiKey>` to an HLS URL (mirror appendTokenToHlsUrl). */
    private fun appendToken(url: String, token: String?): String {
        if (token.isNullOrBlank()) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}token=$token"
    }

    internal val apiClient: ApiClient
        get() = _apiClient ?: throw SFNotInitializedException()

    /**
     * Initialize the SDK (Kotlin coroutine).
     *
     * Validates the API key against BackendV2 (GET /sdk/tenant),
     * fetches the stream URL (GET /sdk/stream/:id),
     * and caches both tenant config and stream URL.
     */
    suspend fun init(
        context: Context,
        apiKey: String,
        streamId: String,
        config: StreamForgeConfig
    ): TenantConfig = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "apiKey must not be empty" }
        require(apiKey.startsWith("sf_")) {
            "Invalid API key format. Expected: sf_live_... or sf_test_..."
        }
        require(streamId.isNotBlank()) { "streamId must not be empty" }

        val client = ApiClient(
            baseUrl = config.baseUrl,
            apiKey = apiKey,
            enableLogging = config.enableLogging,
            trustAllCertificates = config.trustAllCertificates
        )

        val sdkTenantResponse = client.execute { getTenant() }
        val tenantConfig = TenantConfig.fromSdkTenantResponse(sdkTenantResponse)

        val streamUrlResponse = client.execute { getStreamUrl(streamId) }

        _apiClient = client
        _tenantConfig = tenantConfig
        _apiKey = apiKey
        _streamId = streamId
        val baseStreamUrl = streamUrlResponse.url
        _rawStreamUrl = baseStreamUrl
        val separator = if (baseStreamUrl.contains("?")) "&" else "?"
        _streamUrl = "${baseStreamUrl}${separator}token=${apiKey}"
        _streamTitle = streamUrlResponse.title
        _config = config
        _isInitialized = true

        tenantConfig
    }

    /**
     * Initialize the SDK with a callback (Java-friendly).
     *
     * @param context Application or Activity context.
     * @param apiKey Your StreamForge API key (format: `sf_live_...` or `sf_test_...`).
     * @param streamId The UUID of the stream to play.
     * @param config SDK configuration built via [StreamForgeConfig.builder].
     * @param callback Receives [InitCallback.onReady] on success or [InitCallback.onError] on failure.
     */
    fun init(
        context: Context,
        apiKey: String,
        streamId: String,
        config: StreamForgeConfig,
        callback: InitCallback
    ) {
        sdkScope.launch {
            try {
                val tenantConfig = init(context, apiKey, streamId, config)
                callback.onReady(tenantConfig)
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }

    /**
     * Create a new player instance (advanced usage).
     * SDK must be initialized via [init] before calling this method.
     */
    fun createPlayer(context: Context): StreamForgePlayer {
        if (!_isInitialized) throw SFNotInitializedException()
        return StreamForgePlayer(context, _config)
    }

    /**
     * Seamless one-call API to initialize the SDK, create a player, and start playback.
     *
     * Handles everything internally: SDK init → player creation → view setup → stream load → auto-play.
     * The [StreamForgePlayerView] returned via [playerSetupListener] is ready to be added to your view hierarchy.
     *
     * Usage:
     * ```kotlin
     * StreamForge.createPlayer(
     *     context = this,
     *     apiKey = "sf_live_...",
     *     streamId = "your-stream-id",
     *     playerParameters = StreamForgeConfig.builder()
     *         .baseUrl("https://api.example.com")
     *         .build(),
     *     playerSetupListener = object : StreamForge.PlayerSetupListener {
     *         override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
     *             container.addView(player)
     *         }
     *         override fun onPlayerSetupFailed(error: StreamForgeException) {
     *             Log.e("StreamForge", "Setup failed", error)
     *         }
     *     }
     * )
     * ```
     */
    fun createPlayer(
        context: Context,
        apiKey: String,
        streamId: String,
        playerParameters: StreamForgeConfig = StreamForgeConfig.builder().build(),
        playerSetupListener: PlayerSetupListener
    ) {
        sdkScope.launch {
            try {
                // 1. Initialize SDK
                init(context.applicationContext, apiKey, streamId, playerParameters)

                // 2. Create player & view
                val player = StreamForgePlayer(context, _config)
                val playerView = StreamForgePlayerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF000000.toInt())
                }

                // 3. Attach view, apply branding (matches the web embed look)
                player.attachView(playerView)
                playerView.player = player
                playerView.setBrandColor(_tenantConfig?.brandColor ?: PlayerConstants.DEFAULT_BRAND_COLOR)
                playerView.setLogo(_tenantConfig?.logoUrl, _config?.logoOpacity ?: 1f)
                playerView.setControlsEnabled(_config?.showControls ?: true)

                // 4. Load stream — autoplay / initial-mute honoured from config
                player.load()

                // 5. Notify success
                playerSetupListener.onPlayerSetupSuccess(playerView)
            } catch (e: StreamForgeException) {
                val errorView = StreamForgeErrorView(context, e).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                playerSetupListener.onPlayerSetupFailed(e, errorView)
            } catch (e: Exception) {
                val sfException = StreamForgeException("Player setup failed: ${e.message}", e)
                val errorView = StreamForgeErrorView(context, sfException).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                playerSetupListener.onPlayerSetupFailed(sfException, errorView)
            }
        }
    }

    /**
     * Reset SDK state and release internal resources.
     * After calling this, [init] or [createPlayer] must be called again before use.
     */
    fun reset() {
        _isInitialized = false
        _apiClient = null
        _tenantConfig = null
        _apiKey = null
        _streamUrl = null
        _rawStreamUrl = null
        _streamTitle = null
        _streamId = null
        _config = null
    }

    /** Callback for [init] (Java interop). */
    interface InitCallback {
        /** Called when the SDK is initialized and ready. */
        fun onReady(tenantConfig: TenantConfig)
        /** Called when initialization fails. */
        fun onError(error: Exception)
    }

    /** Callback for the seamless [createPlayer] API. */
    interface PlayerSetupListener {
        /** Called when the player is ready. Add the [player] view to your layout. */
        fun onPlayerSetupSuccess(player: StreamForgePlayerView)
        /** Called when player setup fails. [errorView] is a ready-to-use error UI that can be added to your layout. */
        fun onPlayerSetupFailed(error: StreamForgeException, errorView: StreamForgeErrorView)
    }
}
