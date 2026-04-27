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

internal class StreamStatusMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var listener: PlayerEventListener? = null
    private var lastIsLive: Boolean? = null

    fun setEventListener(listener: PlayerEventListener?) {
        this.listener = listener
    }

    fun start(streamId: String) {
        stop()
        lastIsLive = null
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val response = StreamForge.apiClient.execute { getStreamUrl(streamId) }
                    val isLive = response.isLive
                    if (lastIsLive != isLive) {
                        lastIsLive = isLive
                        EventBus.emit(StreamForgeEvent.StreamStatusChanged(isLive))
                        listener?.onStreamStatusChanged(isLive)
                        Log.d("StreamForge", "Stream status: ${if (isLive) "LIVE" else "OFFLINE"}")
                    }
                } catch (e: Exception) {
                    Log.w("StreamForge", "Status poll failed: ${e.message}")
                }
                delay(15_000)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        lastIsLive = null
    }
}
