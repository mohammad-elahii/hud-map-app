package com.example.hudmapapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Default update interval in milliseconds.
 */
private const val UPDATE_INTERVAL_MS = 5_000L

/**
 * Fastest update interval — the app will never receive updates faster than this.
 */
private const val FASTEST_INTERVAL_MS = 2_000L

/**
 * Maximum acceptable age (in ms) for the last-known location before we
 * treat it as stale and rely on fresh updates instead.
 */
private const val MAX_LAST_KNOWN_AGE_MS = 10_000L

/**
 * Concrete [LocationProvider] backed by Google Play Services'
 * [FusedLocationProviderClient].
 *
 * This class is **not** a singleton — create one per scope that needs it
 * (e.g. per Activity or ViewModel) and call [startUpdates] / [stopUpdates]
 * to manage the lifecycle.
 *
 * @param context Application or Activity context.
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    private val _isTracking = MutableStateFlow(false)
    override val isTracking: Boolean get() = _isTracking.value

    // ------------------------------------------------------------------
    // Last-known location
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): AppLocation? {
        if (!hasLocationPermission(context)) return null

        return try {
            val loc = fusedClient.lastLocation.await()
            loc?.toAppLocation()
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Continuous updates via callbackFlow
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override val locationUpdates: Flow<AppLocation> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toAppLocation()?.let { trySend(it) }
            }
        }

        callback = cb
        fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(cb)
            callback = null
        }
    }

    // ------------------------------------------------------------------
    // Start / stop
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override fun startUpdates() {
        if (_isTracking.value) return
        if (callback != null) return          // already listening via flow

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toAppLocation()
            }
        }

        callback = cb
        fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
        _isTracking.value = true
    }

    override fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
        _isTracking.value = false
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun android.location.Location.toAppLocation() = AppLocation(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        bearing = bearing,
        speed = speed,
        time = time
    )
}
