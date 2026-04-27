package com.streamforge.sdk.api

import com.streamforge.sdk.model.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

internal interface StreamForgeApi {

    // ── SDK Endpoints ──────────────────────────────────────

    @GET("sdk/tenant")
    suspend fun getTenant(): Response<SdkTenantResponse>

    @GET("sdk/streams")
    suspend fun getStreams(): Response<StreamsResponse>

    // ── Stream Detail Endpoints ────────────────────────────

    @GET("sdk/stream/{id}")
    suspend fun getStreamUrl(@Path("id") streamId: String): Response<StreamUrlResponse>
}
