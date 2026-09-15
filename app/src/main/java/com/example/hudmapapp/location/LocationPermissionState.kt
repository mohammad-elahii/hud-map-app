package com.example.hudmapapp.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Represents the comprehensive state of location acquisition,
 * including success, waiting, and error conditions.
 */
sealed class LocationState {

    /** Location permission has not been granted yet. */
    data object PermissionRequired : LocationState()

    /** Permission granted but device location services are disabled. */
    data object ServicesDisabled : LocationState()

    /** Permission granted and services on, but waiting for the first fix. */
    data object WaitingForFix : LocationState()

    /** A valid location is available. */
    data class Available(val location: AppLocation) : LocationState()

    /** Location fix was lost (e.g. moved indoors, signal lost). */
    data object Unavailable : LocationState()

    /** The location provider encountered an error. */
    data class Error(val message: String) : LocationState()

    /** No network connection — map tiles cannot load. */
    data object NetworkUnavailable : LocationState()
}

/**
 * Classifies the reason the user cannot use location features right now.
 * Used to drive the overlay UI.
 */
sealed class LocationBlockReason {

    /** Permission not granted. */
    data object PermissionDenied : LocationBlockReason()

    /** Permission permanently denied. */
    data object PermissionPermanentlyDenied : LocationBlockReason()

    /** Device location services are off. */
    data object GpsDisabled : LocationBlockReason()
}

/**
 * Checks if location permission is granted.
 */
fun hasLocationPermission(context: android.content.Context): Boolean {
    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

/**
 * Checks if GPS / location providers are enabled on the device.
 */
fun isLocationEnabled(context: android.content.Context): Boolean {
    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
}

/**
 * Checks if the device has an active network connection.
 */
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * The location permissions to request, in order of preference.
 */
val LOCATION_PERMISSIONS = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
)
