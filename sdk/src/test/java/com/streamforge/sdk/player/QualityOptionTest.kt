package com.streamforge.sdk.player

import org.junit.Assert.*
import org.junit.Test

class QualityOptionTest {

    @Test
    fun `AUTO quality has correct defaults`() {
        val auto = QualityOption.AUTO
        assertEquals(0, auto.width)
        assertEquals(0, auto.height)
        assertEquals(0, auto.bitrate)
        assertEquals("Auto", auto.label)
        assertTrue(auto.isAuto)
    }

    @Test
    fun `fromResolution labels 1080p correctly`() {
        val q = QualityOption.fromResolution(1920, 1080, 5000000)
        assertEquals("1080p", q.label)
        assertEquals(1920, q.width)
        assertEquals(1080, q.height)
        assertEquals(5000000, q.bitrate)
        assertFalse(q.isAuto)
    }

    @Test
    fun `fromResolution labels 720p correctly`() {
        val q = QualityOption.fromResolution(1280, 720, 2500000)
        assertEquals("720p", q.label)
    }

    @Test
    fun `fromResolution labels 4K correctly`() {
        val q = QualityOption.fromResolution(3840, 2160, 15000000)
        assertEquals("4K", q.label)
    }

    @Test
    fun `fromResolution labels 1440p correctly`() {
        val q = QualityOption.fromResolution(2560, 1440, 8000000)
        assertEquals("1440p", q.label)
    }

    @Test
    fun `fromResolution labels 480p correctly`() {
        val q = QualityOption.fromResolution(854, 480, 1000000)
        assertEquals("480p", q.label)
    }

    @Test
    fun `fromResolution labels 360p correctly`() {
        val q = QualityOption.fromResolution(640, 360, 500000)
        assertEquals("360p", q.label)
    }

    @Test
    fun `fromResolution labels 240p correctly`() {
        val q = QualityOption.fromResolution(426, 240, 250000)
        assertEquals("240p", q.label)
    }

    @Test
    fun `fromResolution labels non-standard resolution`() {
        val q = QualityOption.fromResolution(320, 180, 100000)
        assertEquals("180p", q.label)
    }

    @Test
    fun `data class equality works`() {
        val q1 = QualityOption(1920, 1080, 5000000, "1080p")
        val q2 = QualityOption(1920, 1080, 5000000, "1080p")
        assertEquals(q1, q2)
    }

    @Test
    fun `data class inequality works`() {
        val q1 = QualityOption(1920, 1080, 5000000, "1080p")
        val q2 = QualityOption(1280, 720, 2500000, "720p")
        assertNotEquals(q1, q2)
    }
}
