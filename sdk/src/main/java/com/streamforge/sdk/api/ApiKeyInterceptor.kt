package com.streamforge.sdk.api

import okhttp3.Interceptor
import okhttp3.Response

internal class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-API-Key", apiKey)
            .addHeader(
                "User-Agent",
                "StreamForge-Android-SDK/${com.streamforge.sdk.BuildConfig.SDK_VERSION}"
            )
            .build()
        return chain.proceed(request)
    }
}
