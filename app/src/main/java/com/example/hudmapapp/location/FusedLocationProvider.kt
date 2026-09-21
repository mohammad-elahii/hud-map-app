package com.example.hudmapapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.tasks.await

private const val UPDATE_INTERVAL_MS = 5_000L
private const val FASTEST_INTERVAL_MS = 2_000L

class FusedLocationUpdateSource(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) : LocationUpdateSource {

    override fun readiness(): LocationSourceReadiness = when {
        !hasLocationPermission(context) -> LocationSourceReadiness.PERMISSION_REQUIRED
        !isLocationEnabled(context) -> LocationSourceReadiness.SERVICES_DISABLED
        else -> LocationSourceReadiness.READY
    }

    @SuppressLint("MissingPermission")
    override fun register(observer: LocationUpdateObserver): LocationUpdateRegistration {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toAppLocation()?.let(observer::onLocation)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    observer.onUnavailable()
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener(observer::onError)

        val unregistered = AtomicBoolean(false)
        return LocationUpdateRegistration {
            if (unregistered.compareAndSet(false, true)) {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): AppLocation? {
        return fusedClient.lastLocation.await()?.toAppLocation()
    }

    private fun Location.toAppLocation(): AppLocation = appLocationFromValues(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        hasBearing = hasBearing(),
        bearing = bearing,
        hasSpeed = hasSpeed(),
        speed = speed,
        time = time
    )
}
