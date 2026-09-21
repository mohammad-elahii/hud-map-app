package com.example.hudmapapp.domain.driving

enum class HeadingSource {
    NONE,
    LOCATION,
    SENSOR
}

enum class DataReliability {
    UNKNOWN,
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH
}

enum class DataFreshness {
    UNKNOWN,
    FRESH,
    STALE
}

enum class MovementState {
    UNKNOWN,
    STATIONARY,
    MOVING
}

@JvmInline
value class HeadingDegrees private constructor(val value: Float) {
    companion object {
        fun from(value: Float?): HeadingDegrees? {
            if (value == null || !value.isFinite()) return null
            val normalized = ((value % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) %
                FULL_ROTATION_DEGREES
            return HeadingDegrees(if (normalized == -0f) 0f else normalized)
        }

        private const val FULL_ROTATION_DEGREES = 360f
    }
}

@JvmInline
value class SpeedMetersPerSecond private constructor(val value: Float) {
    companion object {
        fun from(value: Float?): SpeedMetersPerSecond? {
            if (value == null || !value.isFinite() || value < 0f) return null
            return SpeedMetersPerSecond(if (value == -0f) 0f else value)
        }
    }
}

data class HeadingReading(
    val value: HeadingDegrees? = null,
    val source: HeadingSource = HeadingSource.NONE,
    val reliability: DataReliability = DataReliability.UNKNOWN,
    val freshness: DataFreshness = DataFreshness.UNKNOWN,
    val timestampMillis: Long? = null
)

data class OrientationReading(
    val azimuth: HeadingDegrees? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null,
    val reliability: DataReliability = DataReliability.UNKNOWN,
    val freshness: DataFreshness = DataFreshness.UNKNOWN,
    val timestampMillis: Long? = null
)

data class SpeedReading(
    val value: SpeedMetersPerSecond? = null,
    val reliability: DataReliability = DataReliability.UNKNOWN,
    val freshness: DataFreshness = DataFreshness.UNKNOWN,
    val timestampMillis: Long? = null
)

data class MovementReading(
    val state: MovementState = MovementState.UNKNOWN,
    val reliability: DataReliability = DataReliability.UNKNOWN,
    val freshness: DataFreshness = DataFreshness.UNKNOWN,
    val timestampMillis: Long? = null
)

data class DrivingContextState(
    val heading: HeadingReading = HeadingReading(),
    val orientation: OrientationReading = OrientationReading(),
    val speed: SpeedReading = SpeedReading(),
    val movement: MovementReading = MovementReading()
)
