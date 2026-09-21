package com.example.hudmapapp.location

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedLocationProvider(
    private val source: LocationUpdateSource
) : LocationProvider {

    private val lock = Any()
    private val mutableLocationUpdates = MutableSharedFlow<AppLocation>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val locationUpdates: SharedFlow<AppLocation> =
        mutableLocationUpdates.asSharedFlow()

    private val mutableLocationState =
        MutableStateFlow<LocationState>(LocationState.WaitingForFix)
    override val locationState: StateFlow<LocationState> =
        mutableLocationState.asStateFlow()

    private val mutableIsTracking = MutableStateFlow(false)
    override val isTracking: StateFlow<Boolean> = mutableIsTracking.asStateFlow()

    private var registration: LocationUpdateRegistration? = null
    private var generation = 0L
    private var latestLocation: AppLocation? = null

    override suspend fun getLastLocation(): AppLocation? {
        when (source.readiness()) {
            LocationSourceReadiness.PERMISSION_REQUIRED -> {
                mutableLocationState.value = LocationState.PermissionRequired
                return null
            }
            LocationSourceReadiness.SERVICES_DISABLED -> {
                mutableLocationState.value = LocationState.ServicesDisabled
                return null
            }
            LocationSourceReadiness.READY -> Unit
        }

        return try {
            val location = source.getLastLocation()
            if (location != null) {
                publishLocation(location)
            } else {
                synchronized(lock) {
                    if (latestLocation == null) {
                        mutableLocationState.value = LocationState.Unavailable
                    }
                }
            }
            location
        } catch (error: Exception) {
            synchronized(lock) {
                mutableLocationState.value = LocationState.Error(error.locationMessage())
            }
            null
        }
    }

    override fun startUpdates() {
        synchronized(lock) {
            if (registration != null || mutableIsTracking.value) return

            when (source.readiness()) {
                LocationSourceReadiness.PERMISSION_REQUIRED -> {
                    mutableLocationState.value = LocationState.PermissionRequired
                    return
                }
                LocationSourceReadiness.SERVICES_DISABLED -> {
                    mutableLocationState.value = LocationState.ServicesDisabled
                    return
                }
                LocationSourceReadiness.READY -> Unit
            }

            val activeGeneration = ++generation
            mutableLocationState.value = LocationState.WaitingForFix
            mutableIsTracking.value = true

            val observer = object : LocationUpdateObserver {
                override fun onLocation(location: AppLocation) {
                    publishLocation(activeGeneration, location)
                }

                override fun onUnavailable() {
                    synchronized(lock) {
                        if (isActiveLocked(activeGeneration)) {
                            mutableLocationState.value = LocationState.Unavailable
                        }
                    }
                }

                override fun onError(error: Throwable) {
                    handleError(activeGeneration, error)
                }
            }

            try {
                registration = source.register(observer)
            } catch (error: Exception) {
                generation++
                mutableIsTracking.value = false
                mutableLocationState.value = LocationState.Error(error.locationMessage())
            }
        }
    }

    override fun stopUpdates() {
        val activeRegistration = synchronized(lock) {
            val current = registration ?: run {
                mutableIsTracking.value = false
                return
            }
            registration = null
            generation++
            mutableIsTracking.value = false
            current
        }
        runCatching { activeRegistration.unregister() }
    }

    private fun publishLocation(activeGeneration: Long, location: AppLocation) {
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            acceptLocationLocked(location)
        }
    }

    private fun publishLocation(location: AppLocation) {
        synchronized(lock) {
            acceptLocationLocked(location)
        }
    }

    private fun acceptLocationLocked(location: AppLocation) {
        val current = latestLocation
        if (current != null && location.time < current.time) return
        latestLocation = location
        mutableLocationState.value = LocationState.Available(location)
        mutableLocationUpdates.tryEmit(location)
    }

    private fun handleError(activeGeneration: Long, error: Throwable) {
        val activeRegistration = synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            val current = registration
            registration = null
            generation++
            mutableIsTracking.value = false
            mutableLocationState.value = LocationState.Error(error.locationMessage())
            current
        }
        activeRegistration?.let { runCatching { it.unregister() } }
    }

    private fun isActive(activeGeneration: Long): Boolean = synchronized(lock) {
        isActiveLocked(activeGeneration)
    }

    private fun isActiveLocked(activeGeneration: Long): Boolean =
        generation == activeGeneration && mutableIsTracking.value

    private fun Throwable.locationMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Location updates are unavailable."
}
