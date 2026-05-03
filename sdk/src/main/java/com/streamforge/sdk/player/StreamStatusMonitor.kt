package com.streamforge.sdk.player

import android.util.Log
import com.streamforge.sdk.StreamForge
import com.streamforge.sdk.event.EventBus
import com.streamforge.sdk.event.StreamForgeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Monitors stream live status using SSE (Server-Sent Events) with polling fallback.
 *
 * Primary: connects to `GET /sdk/stream/:id/realtime?key=<apiKey>` via SSE
 * for real-time push notifications of stream status changes.
 *
 * Fallback: if SSE fails to connect, falls back to polling
 * `GET /sdk/stream/:id` every 15 seconds.
 */
internal class StreamStatusMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var sseClient: SseClient? = null
    private var listener: PlayerEventListener? = null
    @Volatile
    private var lastIsLive: Boolean? = null
    @Volatile
    private var usingSse = false

    fun setEventListener(listener: PlayerEventListener?) {
        this.listener = listener
    }

    fun start(streamId: String) {
        stop()
        lastIsLive = null

        val config = StreamForge._config
        val apiKey = StreamForge._apiKey
        val baseUrl = config?.baseUrl

        val sseEnabled = config?.enableSse != false
        if (sseEnabled && apiKey != null && baseUrl != null) {
            startSse(streamId, baseUrl, apiKey, config?.trustAllCertificates == true)
        } else {
            startPolling(streamId)
        }
    }

    fun stop() {
        sseClient?.disconnect()
        sseClient = null
        pollingJob?.cancel()
        pollingJob = null
        lastIsLive = null
        usingSse = false
    }

    /**
     * Connect to the SSE endpoint for real-time stream status.
     * Falls back to polling if SSE fails.
     */
    private fun startSse(streamId: String, baseUrl: String, apiKey: String, trustAll: Boolean) {
        val normalizedBase = baseUrl.trimEnd('/')
        val sseUrl = if (normalizedBase.endsWith("/api/v1")) {
            "$normalizedBase/sdk/stream/$streamId/realtime?key=$apiKey"
        } else {
            "$normalizedBase/api/v1/sdk/stream/$streamId/realtime?key=$apiKey"
        }

        Log.d(TAG, "Connecting SSE: $sseUrl")

        val client = SseClient(trustAllCertificates = trustAll)
        sseClient = client
        usingSse = true

        client.connect(sseUrl, object : SseClient.Listener {
            override fun onEvent(type: String, data: String) {
                if (type == "stream_status") {
                    try {
                        val json = JSONObject(data)
                        val isLive = json.optBoolean("is_live", false)
                        val viewers = json.optInt("viewers", 0)

                        if (lastIsLive != isLive) {
                            lastIsLive = isLive
                            EventBus.emit(StreamForgeEvent.StreamStatusChanged(isLive))
                            listener?.onStreamStatusChanged(isLive)
                            Log.d(TAG, "SSE stream status: ${if (isLive) "LIVE" else "OFFLINE"} (viewers: $viewers)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SSE parse error: ${e.message}")
                    }
                }
            }

            override fun onError(error: Exception) {
                if (usingSse) {
                    Log.w(TAG, "SSE error, falling back to polling: ${error.message}")
                    usingSse = false
                    startPolling(streamId)
                }
            }

            override fun onClosed() {
                Log.d(TAG, "SSE connection closed")
            }
        })
    }

    /**
     * Fallback: poll the stream status endpoint every 15 seconds.
     */
    private fun startPolling(streamId: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            Log.d(TAG, "Starting polling fallback for stream $streamId")
            while (isActive) {
                try {
                    val response = StreamForge.apiClient.execute { getStreamUrl(streamId) }
                    val isLive = response.isLive
                    if (lastIsLive != isLive) {
                        lastIsLive = isLive
                        EventBus.emit(StreamForgeEvent.StreamStatusChanged(isLive))
                        listener?.onStreamStatusChanged(isLive)
                        Log.d(TAG, "Poll stream status: ${if (isLive) "LIVE" else "OFFLINE"}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Status poll failed: ${e.message}")
                }
                delay(15_000)
            }
        }
    }

    companion object {
        private const val TAG = "StreamForge"
    }
}
