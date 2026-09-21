package com.example.hudmapapp.location

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-owned location state. Observation is hot and passive; only
 * [startUpdates] and [stopUpdates] control the platform subscription.
 */
interface LocationProvider {

    val locationUpdates: SharedFlow<AppLocation>

    val locationState: StateFlow<LocationState>

    suspend fun getLastLocation(): AppLocation?

    fun startUpdates()

    fun stopUpdates()

    val isTracking: StateFlow<Boolean>
}
