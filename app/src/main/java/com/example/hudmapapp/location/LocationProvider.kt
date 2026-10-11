package com.example.hudmapapp.location

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface LocationProvider {

    val locationUpdates: SharedFlow<AppLocation>

    val locationState: StateFlow<LocationState>

    suspend fun getLastLocation(): AppLocation?

    fun startUpdates()

    fun stopUpdates()

    val isTracking: StateFlow<Boolean>
}
