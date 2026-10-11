package com.example.hudmapapp.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

sealed class LocationState {

    data object PermissionRequired : LocationState()

    data object ServicesDisabled : LocationState()

    data object WaitingForFix : LocationState()

    data class Available(val location: AppLocation) : LocationState()

    data object Unavailable : LocationState()

    data class Error(val message: String) : LocationState()

    data object NetworkUnavailable : LocationState()
}

sealed class LocationBlockReason {

    data object PermissionDenied : LocationBlockReason()

    data object PermissionPermanentlyDenied : LocationBlockReason()

    data object GpsDisabled : LocationBlockReason()
}

fun hasLocationPermission(context: android.content.Context): Boolean {
    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

fun isLocationEnabled(context: android.content.Context): Boolean {
    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
}

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

val LOCATION_PERMISSIONS = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
)
