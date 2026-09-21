package com.example.hudmapapp.domain.driving

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingContextStateTest {

    @Test
    fun `default context is entirely unknown and unavailable`() {
        val state = DrivingContextState()

        assertNull(state.heading.value)
        assertEquals(HeadingSource.NONE, state.heading.source)
        assertEquals(DataReliability.UNKNOWN, state.heading.reliability)
        assertEquals(DataFreshness.UNKNOWN, state.heading.freshness)
        assertNull(state.heading.timestampMillis)
        assertNull(state.orientation.azimuth)
        assertNull(state.orientation.pitchDegrees)
        assertNull(state.orientation.rollDegrees)
        assertNull(state.speed.value)
        assertEquals(MovementState.UNKNOWN, state.movement.state)
        assertNull(state.movement.timestampMillis)
    }

    @Test
    fun `heading normalizes finite values into one rotation`() {
        assertEquals(0f, HeadingDegrees.from(0f)?.value)
        assertEquals(0f, HeadingDegrees.from(360f)?.value)
        assertEquals(90f, HeadingDegrees.from(450f)?.value)
        assertEquals(270f, HeadingDegrees.from(-90f)?.value)
        assertEquals(1f, HeadingDegrees.from(721f)?.value)
    }

    @Test
    fun `heading rejects unavailable and non finite values`() {
        assertNull(HeadingDegrees.from(null))
        assertNull(HeadingDegrees.from(Float.NaN))
        assertNull(HeadingDegrees.from(Float.POSITIVE_INFINITY))
        assertNull(HeadingDegrees.from(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `speed accepts finite non negative values including zero`() {
        assertEquals(0f, SpeedMetersPerSecond.from(0f)?.value)
        assertEquals(12.5f, SpeedMetersPerSecond.from(12.5f)?.value)
    }

    @Test
    fun `speed rejects unavailable negative and non finite values`() {
        assertNull(SpeedMetersPerSecond.from(null))
        assertNull(SpeedMetersPerSecond.from(-0.1f))
        assertNull(SpeedMetersPerSecond.from(Float.NaN))
        assertNull(SpeedMetersPerSecond.from(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `readings preserve source reliability freshness orientation and movement`() {
        val state = DrivingContextState(
            heading = HeadingReading(
                value = HeadingDegrees.from(90f),
                source = HeadingSource.LOCATION,
                reliability = DataReliability.HIGH,
                freshness = DataFreshness.FRESH,
                timestampMillis = 100L
            ),
            orientation = OrientationReading(
                azimuth = HeadingDegrees.from(180f),
                pitchDegrees = 10f,
                rollDegrees = -5f,
                reliability = DataReliability.MEDIUM,
                freshness = DataFreshness.STALE,
                timestampMillis = 90L
            ),
            speed = SpeedReading(
                value = SpeedMetersPerSecond.from(8f),
                reliability = DataReliability.HIGH,
                freshness = DataFreshness.FRESH,
                timestampMillis = 100L
            ),
            movement = MovementReading(
                state = MovementState.MOVING,
                reliability = DataReliability.MEDIUM,
                freshness = DataFreshness.FRESH,
                timestampMillis = 100L
            )
        )

        assertEquals(HeadingSource.LOCATION, state.heading.source)
        assertEquals(DataReliability.MEDIUM, state.orientation.reliability)
        assertEquals(DataFreshness.STALE, state.orientation.freshness)
        assertEquals(10f, state.orientation.pitchDegrees)
        assertEquals(-5f, state.orientation.rollDegrees)
        assertEquals(MovementState.MOVING, state.movement.state)
    }

    @Test
    fun `context data classes have deterministic equality and copy behavior`() {
        val state = DrivingContextState(
            heading = HeadingReading(
                value = HeadingDegrees.from(45f),
                source = HeadingSource.SENSOR,
                reliability = DataReliability.LOW,
                freshness = DataFreshness.FRESH,
                timestampMillis = 100L
            )
        )
        val equal = state.copy()
        val different = state.copy(
            movement = MovementReading(MovementState.STATIONARY)
        )

        assertEquals(state, equal)
        assertEquals(state.hashCode(), equal.hashCode())
        assertNotEquals(state, different)
    }
}
