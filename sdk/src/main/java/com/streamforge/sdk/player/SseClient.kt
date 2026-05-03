package com.streamforge.sdk.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Lightweight SSE (Server-Sent Events) client using OkHttp.
 *
 * Connects to an SSE endpoint and parses the event stream.
 * Supports auto-reconnect with exponential backoff.
 */
internal class SseClient(
    private val trustAllCertificates: Boolean = false
) {
    interface Listener {
        fun onEvent(type: String, data: String)
        fun onError(error: Exception)
        fun onClosed()
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply {
                if (trustAllCertificates) {
                    val trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for SSE
            .build()
    }

    fun connect(url: String, listener: Listener) {
        running = true
        thread = Thread({
            var retryDelay = 1000L // Start with 1 second
            val maxRetryDelay = 30_000L

            while (running) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .build()

                    val response: Response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        listener.onError(Exception("SSE connection failed: HTTP ${response.code}"))
                        response.close()
                        break // Don't retry on auth errors
                    }

                    // Reset retry delay on successful connection
                    retryDelay = 1000L
                    Log.d(TAG, "SSE connected to $url")

                    val body = response.body ?: run {
                        listener.onError(Exception("SSE: empty response body"))
                        return@Thread
                    }

                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var eventType = "message"
                    val dataBuilder = StringBuilder()

                    var line: String? = null
                    while (running && reader.readLine().also { line = it } != null) {
                        val l = line!!
                        when {
                            l.startsWith("event:") -> {
                                eventType = l.substring(6).trim()
                            }
                            l.startsWith("data:") -> {
                                if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                                dataBuilder.append(l.substring(5).trim())
                            }
                            l.isEmpty() -> {
                                // Empty line = end of event
                                if (dataBuilder.isNotEmpty()) {
                                    listener.onEvent(eventType, dataBuilder.toString())
                                    dataBuilder.clear()
                                    eventType = "message"
                                }
                            }
                        }
                    }

                    reader.close()
                    response.close()
                    Log.d(TAG, "SSE stream ended")

                } catch (e: Exception) {
                    if (!running) break
                    Log.w(TAG, "SSE error, retrying in ${retryDelay}ms: ${e.message}")
                    listener.onError(e)
                }

                if (!running) break

                // Wait before reconnecting
                try {
                    Thread.sleep(retryDelay)
                } catch (_: InterruptedException) {
                    break
                }
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
            }

            listener.onClosed()
        }, "SSE-Client").also { it.isDaemon = true; it.start() }
    }

    fun disconnect() {
        running = false
        thread?.interrupt()
        thread = null
    }

    companion object {
        private const val TAG = "StreamForge.SSE"
    }
}
