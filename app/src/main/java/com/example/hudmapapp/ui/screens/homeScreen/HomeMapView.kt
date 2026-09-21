package com.example.hudmapapp.ui.screens.homeScreen

import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.SelectedDestinationState
import com.example.hudmapapp.location.AppLocation
import com.example.hudmapapp.location.hasLocationPermission
import com.example.hudmapapp.ui.theme.components.HudButton
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

private const val TAG = "HudMapMapView"

private val DefaultMapCenter = LatLng(36.2949894, 59.5928662)
private const val DefaultMapZoom = 14f
private const val UserLocationZoom = 17f
private const val DestinationZoom = 16f
private const val RouteBoundsPaddingPx = 120
private const val SelectedRouteWidth = 14f
private const val AlternativeRouteWidth = 9f
private const val SelectedRouteZIndex = 2f
private const val AlternativeRouteZIndex = 1f
private val SelectedRouteColor = 0xFF7C4DFF.toInt()
private val AlternativeRouteColor = 0xFF9E9E9E.toInt()
private const val CameraAnimationDurationMs = 600

/**
 * Possible map failure modes.
 */
sealed class MapError {
    data object None : MapError()
    data class InitializationFailed(val message: String = "Map failed to load") : MapError()
    data class NetworkUnavailable(val message: String = "No internet connection. Map data cannot be loaded.") : MapError()
}

/**
 * The Navigation SDK ships its own copy of the Maps SDK classes and replaces
 * the standalone Maps SDK entirely, so the home screen renders through the
 * bundled MapView, which behaves exactly like a normal Google map. The
 * turn-by-turn map (NavigationView with full navigation UI) lives in the HUD
 * screen instead; see HudNavMapView.
 */
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routePolylines by remember { mutableStateOf<List<Polyline>>(emptyList()) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<MapError>(MapError.None) }

    val hasUserLocation = currentLocation != null

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

    val selectedRoute = routePreviews.firstOrNull { it.id == selectedRouteId }
        ?: routePreviews.firstOrNull()
    val orderedRoutes = buildList {
        routePreviews.filter { it.id != selectedRoute?.id }.forEach { add(it) }
        selectedRoute?.let { add(it) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            MapView(
                viewContext,
                GoogleMapOptions().camera(
                    CameraPosition.fromLatLngZoom(DefaultMapCenter, DefaultMapZoom)
                )
            ).apply {
                onCreate(null as Bundle?)
                mapView = this
                // Do NOT forward onStart/onResume here: registering the
                // lifecycle observer below makes LifecycleRegistry dispatch
                // ON_START/ON_RESUME to sync it with the current activity
                // state, and NavView enforces strict call ordering.
                getMapAsync(
                    OnMapReadyCallback { readyMap ->
                        Log.d(TAG, "GoogleMap ready")
                        readyMap.isBuildingsEnabled = true
                        readyMap.isIndoorEnabled = true
                        readyMap.isTrafficEnabled = false
                        readyMap.uiSettings.apply {
                            isCompassEnabled = false
                            isIndoorLevelPickerEnabled = true
                            isMyLocationButtonEnabled = false
                            isRotateGesturesEnabled = true
                            isScrollGesturesEnabled = true
                            isTiltGesturesEnabled = true
                            isZoomControlsEnabled = true
                            isZoomGesturesEnabled = true
                        }
                        readyMap.setOnPolylineClickListener { polyline ->
                            (polyline.tag as? String)?.let(onRouteSelected)
                        }
                        readyMap.setOnMapLoadedCallback {
                            Log.d(TAG, "Map tiles loaded")
                            mapError = MapError.None
                        }
                        googleMap = readyMap
                    }
                )
            }
        },
        onRelease = { view ->
            Log.d(TAG, "MapView destroyed")
            googleMap = null
            routePolylines = emptyList()
            destinationMarker = null
            view.onDestroy()
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasUserLocation, googleMap) {
        val map = googleMap ?: return@LaunchedEffect
        val enabled = hasUserLocation && hasLocationPermission(context)
        Log.d(TAG, "setMyLocationEnabled=$enabled")
        map.isMyLocationEnabled = enabled
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0 && hasUserLocation) {
            val userLatLng = LatLng(currentLocation!!.latitude, currentLocation.longitude)
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(userLatLng, UserLocationZoom),
                CameraAnimationDurationMs,
                null
            )
        }
    }

    LaunchedEffect(destinationLatLng, googleMap) {
        if (destinationLatLng != null) {
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(destinationLatLng, DestinationZoom),
                CameraAnimationDurationMs,
                null
            )
        }
    }

    // Center the camera on the user's position once, when the first fix
    // arrives (or when the map is (re)created after a fix is already known).
    // Later fixes leave the camera alone; the recenter button handles that.
    LaunchedEffect(hasUserLocation, googleMap) {
        val map = googleMap ?: return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect
        if (hasUserLocation && !hasCenteredOnUser) {
            hasCenteredOnUser = true
            Log.d(TAG, "Centering camera on first user fix")
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    UserLocationZoom
                ),
                CameraAnimationDurationMs,
                null
            )
        }
    }

    LaunchedEffect(googleMap, routePreviews, selectedRoute?.id) {
        val map = googleMap ?: return@LaunchedEffect
        routePolylines.forEach { it.remove() }
        routePolylines = orderedRoutes.mapNotNull { route ->
            val points = route.polylinePoints.map { LatLng(it.latitude, it.longitude) }
            if (points.size < 2) return@mapNotNull null
            val isSelected = route.id == selectedRoute?.id
            Log.d(TAG, "Adding polyline for route ${route.id} (selected=$isSelected, ${points.size} points)")
            map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .clickable(true)
                    .color(if (isSelected) SelectedRouteColor else AlternativeRouteColor)
                    .jointType(JointType.ROUND)
                    .width(if (isSelected) SelectedRouteWidth else AlternativeRouteWidth)
                    .zIndex(if (isSelected) SelectedRouteZIndex else AlternativeRouteZIndex)
            ).apply { tag = route.id }
        }
    }

    LaunchedEffect(googleMap, destinationLatLng, selectedDestination) {
        destinationMarker?.remove()
        destinationMarker = destinationLatLng?.let { latLng ->
            googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(
                        when (selectedDestination) {
                            is SelectedDestinationState.Selected -> selectedDestination.destination.name
                            is SelectedDestinationState.Confirmed -> selectedDestination.destination.name
                            SelectedDestinationState.None -> ""
                        }
                    )
                    .snippet(
                        when (selectedDestination) {
                            is SelectedDestinationState.Selected -> selectedDestination.destination.address
                            is SelectedDestinationState.Confirmed -> selectedDestination.destination.address
                            SelectedDestinationState.None -> ""
                        }
                    )
            )
        }
    }

    LaunchedEffect(selectedRoute?.id, googleMap) {
        val route = selectedRoute ?: return@LaunchedEffect
        val points = route.polylinePoints.map { LatLng(it.latitude, it.longitude) }
        if (points.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(it) }
        }.build()
        try {
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, RouteBoundsPaddingPx),
                CameraAnimationDurationMs,
                null
            )
        } catch (_: IllegalStateException) {
        }
    }

    Box(modifier = modifier) {
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
