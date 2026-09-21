package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hudmapapp.location.AppLocation
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.SelectedDestinationState
import com.example.hudmapapp.ui.theme.components.HudButton
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

private val DefaultMapCenter = LatLng(36.2949894, 59.5928662)
private const val DefaultMapZoom = 14f
private const val UserLocationZoom = 17f
private const val DestinationZoom = 16f
private const val RouteBoundsPaddingPx = 120
private const val SelectedRouteWidth = 14f
private const val AlternativeRouteWidth = 9f
private const val SelectedRouteZIndex = 2f
private const val AlternativeRouteZIndex = 1f
private val SelectedRouteColor = androidx.compose.ui.graphics.Color(0xFF7C4DFF)
private val AlternativeRouteColor = androidx.compose.ui.graphics.Color(0xFF9E9E9E)

/**
 * Possible map failure modes.
 */
sealed class MapError {
    data object None : MapError()
    data class InitializationFailed(val message: String = "Map failed to load") : MapError()
    data class NetworkUnavailable(val message: String = "No internet connection. Map data cannot be loaded.") : MapError()
}

@Composable
internal fun HomeMapView(
    currentLocation: AppLocation?,
    recenterTrigger: Int,
    selectedDestination: SelectedDestinationState = SelectedDestinationState.None,
    routePreviews: List<RoutePreview> = emptyList(),
    selectedRouteId: String? = null,
    onRouteSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultMapCenter, DefaultMapZoom)
    }

    val hasUserLocation = currentLocation != null

    var mapError by remember { mutableStateOf<MapError>(MapError.None) }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0 && hasUserLocation) {
            val userLatLng = LatLng(currentLocation!!.latitude, currentLocation.longitude)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(userLatLng, UserLocationZoom),
                durationMs = 600
            )
        }
    }

    val destinationLatLng = when (selectedDestination) {
        is SelectedDestinationState.Selected -> {
            val d = selectedDestination.destination
            if (d.latitude != 0.0 || d.longitude != 0.0) {
                LatLng(d.latitude, d.longitude)
            } else null
        }
        is SelectedDestinationState.Confirmed -> {
            val d = selectedDestination.destination
            if (d.latitude != 0.0 || d.longitude != 0.0) {
                LatLng(d.latitude, d.longitude)
            } else null
        }
        SelectedDestinationState.None -> null
    }

    LaunchedEffect(destinationLatLng) {
        if (destinationLatLng != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(destinationLatLng, DestinationZoom),
                durationMs = 600
            )
        }
    }

    val selectedRoute = routePreviews.firstOrNull { it.id == selectedRouteId }
        ?: routePreviews.firstOrNull()
    val orderedRoutes = buildList {
        routePreviews.filter { it.id != selectedRoute?.id }.forEach { add(it) }
        selectedRoute?.let { add(it) }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
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
                zoomControlsEnabled = true,
                zoomGesturesEnabled = true
            ),
            onMapLoaded = {
                mapError = MapError.None
            }
        ) {
            orderedRoutes.forEach { route ->
                val points = route.polylinePoints.map { LatLng(it.latitude, it.longitude) }
                if (points.size >= 2) {
                    val isSelected = route.id == selectedRoute?.id
                    Polyline(
                        points = points,
                        clickable = true,
                        color = if (isSelected) SelectedRouteColor else AlternativeRouteColor,
                        jointType = JointType.ROUND,
                        width = if (isSelected) SelectedRouteWidth else AlternativeRouteWidth,
                        zIndex = if (isSelected) SelectedRouteZIndex else AlternativeRouteZIndex,
                        onClick = { onRouteSelected(route.id) }
                    )
                }
            }

            if (destinationLatLng != null) {
                val markerState = remember(destinationLatLng) {
                    MarkerState(position = destinationLatLng)
                }
                Marker(
                    state = markerState,
                    title = when (selectedDestination) {
                        is SelectedDestinationState.Selected -> selectedDestination.destination.name
                        is SelectedDestinationState.Confirmed -> selectedDestination.destination.name
                        SelectedDestinationState.None -> ""
                    },
                    snippet = when (selectedDestination) {
                        is SelectedDestinationState.Selected -> selectedDestination.destination.address
                        is SelectedDestinationState.Confirmed -> selectedDestination.destination.address
                        SelectedDestinationState.None -> ""
                    }
                )
            }
        }

        LaunchedEffect(selectedRoute?.id) {
            val route = selectedRoute ?: return@LaunchedEffect
            val points = route.polylinePoints.map { LatLng(it.latitude, it.longitude) }
            if (points.size < 2) return@LaunchedEffect
            val bounds = LatLngBounds.builder().apply {
                points.forEach { include(it) }
            }.build()
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, RouteBoundsPaddingPx),
                    durationMs = 600
                )
            } catch (_: IllegalStateException) {
            }
        }

        // Show error overlay if map failed to load
        if (mapError is MapError.InitializationFailed) {
            MapErrorOverlay(
                message = (mapError as MapError.InitializationFailed).message,
                onRetry = { mapError = MapError.None }
            )
        }
    }
}

/**
 * Lightweight overlay shown when the Google Map fails to load.
 */
@Composable
private fun MapErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Map unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            HudButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Filled.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
