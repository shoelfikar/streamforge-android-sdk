package com.streamforge.sdk

import org.junit.Assert.*
import org.junit.Test

class StreamForgeConfigTest {

    @Test
    fun `builder creates config with defaults`() {
        val config = StreamForgeConfig.builder().build()
        assertEquals(StreamForgeConfig.BASE_URL, config.baseUrl)
        assertFalse(config.enableLogging)
        assertFalse(config.trustAllCertificates)
        assertNull(config.minBitrateKbps)
        assertNull(config.maxBitrateKbps)
        assertNull(config.maxVideoWidth)
        assertNull(config.maxVideoHeight)
        assertEquals(2500, config.bufferForPlaybackMs)
        assertEquals(5000, config.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `builder applies all values`() {
        val config = StreamForgeConfig.builder()
            .enableLogging(true)
            .trustAllCertificates(true)
            .minBitrateKbps(500)
            .maxBitrateKbps(8000)
            .maxVideoWidth(1920)
            .maxVideoHeight(1080)
            .bufferForPlaybackMs(3000)
            .bufferForPlaybackAfterRebufferMs(6000)
            .build()

        assertTrue(config.enableLogging)
        assertTrue(config.trustAllCertificates)
        assertEquals(500, config.minBitrateKbps)
        assertEquals(8000, config.maxBitrateKbps)
        assertEquals(1920, config.maxVideoWidth)
        assertEquals(1080, config.maxVideoHeight)
        assertEquals(3000, config.bufferForPlaybackMs)
        assertEquals(6000, config.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `baseUrl has default value`() {
        val config = StreamForgeConfig.builder().build()
        assertTrue(config.baseUrl.isNotBlank())
        assertTrue(config.baseUrl.startsWith("https://"))
    }

    @Test
    fun `baseUrl can be overridden`() {
        val config = StreamForgeConfig.builder()
            .baseUrl("https://custom.example.com")
            .build()
        assertEquals("https://custom.example.com", config.baseUrl)
    }
}
