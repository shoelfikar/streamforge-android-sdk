package com.streamforge.sdk

import android.content.Context
import com.streamforge.sdk.api.ApiClient
import com.streamforge.sdk.exception.SFNotInitializedException
import com.streamforge.sdk.model.TenantConfig
import com.streamforge.sdk.exception.StreamForgeException
import com.streamforge.sdk.player.PlaybackState
import com.streamforge.sdk.player.PlayerEventListener
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
    @Volatile private var _apiKey: String? = null
    @Volatile private var _streamUrl: String? = null
    @Volatile private var _streamTitle: String? = null
    @Volatile private var _streamId: String? = null
    @Volatile private var _config: StreamForgeConfig? = null

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

                // 3. Attach view & set title
                player.attachView(playerView)
                playerView.player = player
                playerView.setTitle(_streamTitle)

                // 4. Load stream with auto-play
                player.load(object : PlayerEventListener {
                    override fun onPlaybackStateChanged(state: PlaybackState) {
                        if (state == PlaybackState.READY) {
                            player.play()
                        }
                    }
                })

                // 5. Notify success
                playerSetupListener.onPlayerSetupSuccess(playerView)
            } catch (e: StreamForgeException) {
                playerSetupListener.onPlayerSetupFailed(e)
            } catch (e: Exception) {
                playerSetupListener.onPlayerSetupFailed(
                    StreamForgeException("Player setup failed: ${e.message}", e)
                )
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
        /** Called when player setup fails. */
        fun onPlayerSetupFailed(error: StreamForgeException)
    }
}
