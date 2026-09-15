package com.example.hudmapapp.location

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for obtaining device location.
 *
 * Implementations are responsible for communicating with Android location APIs
 * and exposing location data without coupling to UI or Maps.
 */
interface LocationProvider {

    /**
     * A cold [Flow] that emits the device's current location whenever a new
     * fix is available. The flow completes with an error if location
     * acquisition fails permanently.
     */
    val locationUpdates: Flow<AppLocation>

    /**
     * Returns the last known location, or `null` if no fix is available.
     */
    suspend fun getLastLocation(): AppLocation?

    /**
     * Starts continuous location updates.
     * Must only be called when location permission has been granted.
     */
    fun startUpdates()

    /**
     * Stops continuous location updates.
     */
    fun stopUpdates()

    /**
     * `true` while continuous updates are active.
     */
    val isTracking: Boolean
}
