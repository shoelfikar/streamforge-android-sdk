package com.streamforge.sdk.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class RetryManager(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 2000,
    private val maxDelayMs: Long = 30000,
    private val backoffFactor: Double = 2.0
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob: Job? = null
    private var attempt = 0

    fun retry(action: () -> Unit, onGiveUp: (Int) -> Unit) {
        cancel()
        retryJob = scope.launch {
            attempt++
            if (attempt > maxRetries) {
                Log.w("StreamForge", "Max retries ($maxRetries) reached, giving up")
                onGiveUp(attempt - 1)
                return@launch
            }

            val delayMs = (initialDelayMs * Math.pow(backoffFactor, (attempt - 1).toDouble()))
                .toLong()
                .coerceAtMost(maxDelayMs)

            Log.d("StreamForge", "Retry attempt $attempt/$maxRetries in ${delayMs}ms")
            delay(delayMs)
            action()
        }
    }

    fun reset() {
        cancel()
        attempt = 0
    }

    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
