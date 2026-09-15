package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.hudmapapp.location.AppLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

private val DefaultMapCenter = LatLng(36.2949894, 59.5928662)
private const val DefaultMapZoom = 14f
private const val UserLocationZoom = 17f


@Composable
internal fun HomeMapView(
    currentLocation: AppLocation?,
    recenterTrigger: Int,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultMapCenter, DefaultMapZoom)
    }

    val hasUserLocation = currentLocation != null

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0 && hasUserLocation) {
            val userLatLng = LatLng(currentLocation!!.latitude, currentLocation.longitude)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(userLatLng, UserLocationZoom),
                durationMs = 600
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isBuildingEnabled = true,
            isIndoorEnabled = true,
            isTrafficEnabled = false,
            isMyLocationEnabled = hasUserLocation
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = true,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = true
        )
    )
}
