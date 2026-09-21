package com.example.hudmapapp.location

/**
 * App-owned geographic fix. Bearing is course over ground in degrees and speed
 * is meters per second. A null value means the platform fix did not report that
 * measurement; zero remains a valid north or stationary reading.
 */
data class AppLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float? = null,
    val speed: Float? = null,
    val time: Long = System.currentTimeMillis()
)

internal fun appLocationFromValues(
    latitude: Double,
    longitude: Double,
    accuracy: Float,
    hasBearing: Boolean,
    bearing: Float,
    hasSpeed: Boolean,
    speed: Float,
    time: Long
): AppLocation = AppLocation(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    bearing = bearing.takeIf { hasBearing },
    speed = speed.takeIf { hasSpeed },
    time = time
)
