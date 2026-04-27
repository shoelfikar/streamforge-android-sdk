package com.streamforge.sdk.api

import com.streamforge.sdk.exception.SFApiException
import com.streamforge.sdk.exception.SFAuthException
import com.streamforge.sdk.model.SdkTenantResponse
import com.streamforge.sdk.model.StreamUrlResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = ApiClient(
            baseUrl = server.url("/").toString(),
            apiKey = "sf_test_abc123",
            enableLogging = false,
            trustAllCertificates = false
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends API key in X-API-Key header`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"tenant":{"id":"t1","slug":"test","name":"Test","status":"active"}}"""))

        client.execute<SdkTenantResponse> { getTenant() }

        val request = server.takeRequest()
        assertEquals("sf_test_abc123", request.getHeader("X-API-Key"))
    }

    @Test
    fun `appends api v1 to base url`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"tenant":{"id":"t1","slug":"test","name":"Test","status":"active"}}"""))

        client.execute<SdkTenantResponse> { getTenant() }

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/v1/"))
    }

    @Test
    fun `parses tenant response correctly`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"tenant":{"id":"t1","slug":"demo","name":"Demo Tenant","status":"active","brand_color":"#FF0000"}}"""))

        val response = client.execute<SdkTenantResponse> { getTenant() }
        assertEquals("t1", response.tenant.id)
        assertEquals("demo", response.tenant.slug)
        assertEquals("Demo Tenant", response.tenant.name)
        assertEquals("active", response.tenant.status)
        assertEquals("#FF0000", response.tenant.brandColor)
    }

    @Test
    fun `parses stream url response correctly`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"url":"https://edge.example.com/live/stream1/index.m3u8","title":"My Stream","is_live":true}"""))

        val response = client.execute<StreamUrlResponse> { getStreamUrl("stream-id-1") }
        assertEquals("https://edge.example.com/live/stream1/index.m3u8", response.url)
        assertEquals("My Stream", response.title)
        assertTrue(response.isLive)
    }

    @Test(expected = SFAuthException::class)
    fun `throws SFAuthException on 401`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"message":"Invalid API key"}"""))

        client.execute<SdkTenantResponse> { getTenant() }
    }

    @Test(expected = SFAuthException::class)
    fun `throws SFAuthException on 403`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(403)
            .setBody("""{"message":"Forbidden"}"""))

        client.execute<SdkTenantResponse> { getTenant() }
    }

    @Test(expected = SFApiException::class)
    fun `throws SFApiException on 404`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(404)
            .setBody("""{"message":"Not found"}"""))

        client.execute<StreamUrlResponse> { getStreamUrl("nonexistent") }
    }

    @Test(expected = SFApiException::class)
    fun `throws SFApiException on 500`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("""{"message":"Internal server error"}"""))

        client.execute<SdkTenantResponse> { getTenant() }
    }

    @Test
    fun `SFAuthException contains status code`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"message":"Bad key"}"""))

        try {
            client.execute<SdkTenantResponse> { getTenant() }
            fail("Expected SFAuthException")
        } catch (e: SFAuthException) {
            assertEquals(401, e.statusCode)
        }
    }

    @Test
    fun `SFApiException contains status code and body`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(422)
            .setBody("""{"message":"Validation failed"}"""))

        try {
            client.execute<SdkTenantResponse> { getTenant() }
            fail("Expected SFApiException")
        } catch (e: SFApiException) {
            assertEquals(422, e.statusCode)
        }
    }
}
