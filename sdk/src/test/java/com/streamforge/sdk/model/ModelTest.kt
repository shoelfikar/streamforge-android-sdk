package com.streamforge.sdk.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ModelTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `TenantConfig maps from SdkTenantResponse`() {
        val response = SdkTenantResponse(
            tenant = SdkTenantInfo(
                id = "t-123",
                slug = "demo",
                name = "Demo Corp",
                status = "active",
                brandColor = "#00FF00",
                logoUrl = "https://example.com/logo.png",
                faviconUrl = null,
                plan = SdkTenantPlan(name = "pro", displayName = "Professional")
            )
        )

        val config = TenantConfig.fromSdkTenantResponse(response)
        assertEquals("t-123", config.tenantId)
        assertEquals("demo", config.tenantSlug)
        assertEquals("Demo Corp", config.tenantName)
        assertEquals("active", config.status)
        assertEquals("#00FF00", config.brandColor)
        assertEquals("https://example.com/logo.png", config.logoUrl)
        assertNull(config.faviconUrl)
        assertEquals("pro", config.planName)
        assertEquals("Professional", config.planDisplayName)
    }

    @Test
    fun `StreamUrlResponse parses from JSON`() {
        val json = """{"url":"https://edge.test/live/s1/index.m3u8","title":"Test Stream","is_live":true}"""
        val adapter = moshi.adapter(StreamUrlResponse::class.java)
        val response = adapter.fromJson(json)!!

        assertEquals("https://edge.test/live/s1/index.m3u8", response.url)
        assertEquals("Test Stream", response.title)
        assertTrue(response.isLive)
    }

    @Test
    fun `Stream isLive returns true when status is live`() {
        val stream = Stream(
            id = "s1", tenantId = "t1", name = "live1",
            streamKey = "key", flussonicStreamName = "live1",
            ingestProtocol = "rtmp", status = "live",
            createdAt = "2026-01-01", updatedAt = "2026-01-01"
        )
        assertTrue(stream.isLive)
    }

    @Test
    fun `Stream isLive returns true when liveStatus alive`() {
        val stream = Stream(
            id = "s1", tenantId = "t1", name = "live1",
            streamKey = "key", flussonicStreamName = "live1",
            ingestProtocol = "rtmp", status = "created",
            createdAt = "2026-01-01", updatedAt = "2026-01-01",
            liveStatus = StreamLiveStatus(alive = true, status = "running")
        )
        assertTrue(stream.isLive)
    }

    @Test
    fun `Stream isLive returns false when offline`() {
        val stream = Stream(
            id = "s1", tenantId = "t1", name = "live1",
            streamKey = "key", flussonicStreamName = "live1",
            ingestProtocol = "rtmp", status = "offline",
            createdAt = "2026-01-01", updatedAt = "2026-01-01"
        )
        assertFalse(stream.isLive)
    }

    @Test
    fun `StreamStatus constants are correct`() {
        assertEquals("created", StreamStatus.CREATED)
        assertEquals("live", StreamStatus.LIVE)
        assertEquals("offline", StreamStatus.OFFLINE)
        assertEquals("error", StreamStatus.ERROR)
    }

    @Test
    fun `SdkTenantResponse parses from JSON`() {
        val json = """{"tenant":{"id":"t1","slug":"test","name":"Test","status":"active"}}"""
        val adapter = moshi.adapter(SdkTenantResponse::class.java)
        val response = adapter.fromJson(json)!!

        assertEquals("t1", response.tenant.id)
        assertEquals("test", response.tenant.slug)
        assertEquals("Test", response.tenant.name)
        assertEquals("active", response.tenant.status)
        assertNull(response.tenant.plan)
    }
}
