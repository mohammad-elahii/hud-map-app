package com.example.hudmapapp.location

/**
 * Represents a geographic location with latitude, longitude, and accuracy.
 */
data class AppLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val time: Long = System.currentTimeMillis()
)
