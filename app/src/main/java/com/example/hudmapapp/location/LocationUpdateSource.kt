package com.example.hudmapapp.location

enum class LocationSourceReadiness {
    READY,
    PERMISSION_REQUIRED,
    SERVICES_DISABLED
}

interface LocationUpdateObserver {
    fun onLocation(location: AppLocation)

    fun onUnavailable()

    fun onError(error: Throwable)
}

fun interface LocationUpdateRegistration {
    fun unregister()
}

interface LocationUpdateSource {
    fun readiness(): LocationSourceReadiness

    fun register(observer: LocationUpdateObserver): LocationUpdateRegistration

    suspend fun getLastLocation(): AppLocation?
}
