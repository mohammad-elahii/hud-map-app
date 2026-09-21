package com.example.hudmapapp.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocationTest {

    @Test
    fun `optional metadata defaults to unavailable`() {
        val location = AppLocation(36.2, 59.6, 4f, time = 100L)

        assertNull(location.bearing)
        assertNull(location.speed)
    }

    @Test
    fun `platform availability flags preserve missing metadata`() {
        val location = appLocationFromValues(
            latitude = 36.2,
            longitude = 59.6,
            accuracy = 4f,
            hasBearing = false,
            bearing = 180f,
            hasSpeed = false,
            speed = 12f,
            time = 100L
        )

        assertNull(location.bearing)
        assertNull(location.speed)
    }

    @Test
    fun `valid zero bearing and speed remain available`() {
        val location = appLocationFromValues(
            latitude = 36.2,
            longitude = 59.6,
            accuracy = 4f,
            hasBearing = true,
            bearing = 0f,
            hasSpeed = true,
            speed = 0f,
            time = 100L
        )

        assertEquals(0f, location.bearing)
        assertEquals(0f, location.speed)
    }

    @Test
    fun `mapping preserves complete fix metadata`() {
        val location = appLocationFromValues(
            latitude = 36.2972,
            longitude = 59.6067,
            accuracy = 4.5f,
            hasBearing = true,
            bearing = 123.25f,
            hasSpeed = true,
            speed = 8.75f,
            time = 1_700_000_000_000L
        )

        assertEquals(36.2972, location.latitude, 0.0)
        assertEquals(59.6067, location.longitude, 0.0)
        assertEquals(4.5f, location.accuracy)
        assertEquals(123.25f, location.bearing)
        assertEquals(8.75f, location.speed)
        assertEquals(1_700_000_000_000L, location.time)
    }

    @Test
    fun `data class equality hash and copy include optional metadata`() {
        val location = AppLocation(36.2, 59.6, 4f, 90f, 5f, 100L)
        val equal = location.copy()
        val different = location.copy(speed = null)

        assertEquals(location, equal)
        assertEquals(location.hashCode(), equal.hashCode())
        assertNotEquals(location, different)
    }
}
