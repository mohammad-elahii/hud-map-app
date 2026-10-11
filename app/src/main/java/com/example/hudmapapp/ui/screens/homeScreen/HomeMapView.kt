package com.example.hudmapapp.ui.screens.homeScreen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.hudmapapp.ui.theme.MainGradient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.example.hudmapapp.R

private const val TAG = "HudMapMapView"

private val DefaultMapCenter = LatLng(36.2949894, 59.5928662)
private const val DefaultMapZoom = 14f
private const val UserLocationZoom = 17f
private const val DestinationZoom = 16f
private const val RouteBoundsPaddingPx = 120
private const val SelectedRouteWidth = 14f
private const val AlternativeRouteWidth = 9f
private const val SelectedRouteZIndex = 100f
private const val AlternativeRouteBaseZIndex = 10f
private const val AlternativeRouteZIndexStep = 10f

private const val RouteGradientChunks = 24

private fun gradientArgbAt(t: Float): Int {
    val colors = MainGradient
    val clamped = t.coerceIn(0f, 1f)
    val scaled = clamped * (colors.size - 1)
    val index = scaled.toInt().coerceAtMost(colors.size - 2)
    val fraction = scaled - index
    val from = colors[index]
    val to = colors[index + 1]
    val red = from.red + (to.red - from.red) * fraction
    val green = from.green + (to.green - from.green) * fraction
    val blue = from.blue + (to.blue - from.blue) * fraction
    return android.graphics.Color.argb(
        255,
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

private const val PolylineClickWindowMs = 60L

private class PolylineClickGate {
    private val handler = Handler(Looper.getMainLooper())
    private var clicked = mutableListOf<Polyline>()
    private var decision: Runnable? = null

    fun onPolylineClicked(polyline: Polyline, decide: (List<Polyline>) -> Unit) {
        decision?.let(handler::removeCallbacks)
        if (clicked.none { it === polyline }) clicked.add(polyline)
        val run = Runnable {
            val candidates = clicked
            clicked = mutableListOf()
            decision = null
            decide(candidates)
        }
        decision = run
        handler.postDelayed(run, PolylineClickWindowMs)
    }
}
private val AlternativeRouteColor = 0xFF9E9E9E.toInt()
private const val CameraAnimationDurationMs = 600

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
    onPoiClick: (placeId: String, name: String, latitude: Double, longitude: Double) -> Unit = { _, _, _, _ -> },
    satelliteEnabled: Boolean = false,
    darkStyleEnabled: Boolean = false,
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

    val polylineClickGate = remember { PolylineClickGate() }
    val effectiveSelectedRouteId = remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(selectedRoute?.id) {
        effectiveSelectedRouteId.value = selectedRoute?.id
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
                            polylineClickGate.onPolylineClicked(polyline) { candidates ->
                                val chosen = candidates
                                    .filter { (it.tag as? String) != effectiveSelectedRouteId.value }
                                    .maxByOrNull { it.zIndex }
                                    ?: candidates.maxByOrNull { it.zIndex }
                                    ?: return@onPolylineClicked
                                val chosenId = chosen.tag as? String
                                if (chosenId != null && chosenId != effectiveSelectedRouteId.value) {
                                    Log.d(TAG, "Polyline tap selected route $chosenId (${candidates.size} overlapped)")
                                    onRouteSelected(chosenId)
                                }
                            }
                        }
                        readyMap.setOnPoiClickListener { poi ->
                            Log.d(TAG, "POI clicked: ${poi.name} (${poi.placeId})")
                            onPoiClick(
                                poi.placeId,
                                poi.name,
                                poi.latLng.latitude,
                                poi.latLng.longitude
                            )
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
                else -> {  }
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

    val darkStyleJson = remember {
        context.resources.openRawResource(R.raw.map_style_dark).readBytes().decodeToString()
    }

    LaunchedEffect(satelliteEnabled, darkStyleEnabled, googleMap) {
        val map = googleMap ?: return@LaunchedEffect
        Log.d(TAG, "mapType satellite=$satelliteEnabled darkStyle=$darkStyleEnabled")
        map.mapType = if (satelliteEnabled) {
            GoogleMap.MAP_TYPE_SATELLITE
        } else {
            GoogleMap.MAP_TYPE_NORMAL
        }
        map.setMapStyle(
            if (darkStyleEnabled && !satelliteEnabled) {
                MapStyleOptions(darkStyleJson)
            } else {
                null
            }
        )
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
        val built = mutableListOf<Polyline>()
        var alternativeIndex = 0
        orderedRoutes.forEach { route ->
            val points = route.polylinePoints.map { LatLng(it.latitude, it.longitude) }
            if (points.size < 2) return@forEach
            val isSelected = route.id == selectedRoute?.id
            if (isSelected) {

                val chunkSize = maxOf(1, (points.size + RouteGradientChunks - 1) / RouteGradientChunks)
                var start = 0
                while (start < points.size - 1) {
                    val end = minOf(start + chunkSize + 1, points.size)
                    val t = start.toFloat() / (points.size - 1).coerceAtLeast(1)
                    built += map.addPolyline(
                        PolylineOptions()
                            .addAll(points.subList(start, end))
                            .clickable(true)
                            .color(gradientArgbAt(t))
                            .jointType(JointType.ROUND)
                            .width(SelectedRouteWidth)
                            .zIndex(SelectedRouteZIndex)
                    ).apply { tag = route.id }
                    start += chunkSize
                }
            } else {

                val zIndex =
                    AlternativeRouteBaseZIndex + alternativeIndex++ * AlternativeRouteZIndexStep
                Log.d(TAG, "Adding polyline for route ${route.id} (selected=false, zIndex=$zIndex, ${points.size} points)")
                built += map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .clickable(true)
                        .color(AlternativeRouteColor)
                        .jointType(JointType.ROUND)
                        .width(AlternativeRouteWidth)
                        .zIndex(zIndex)
                ).apply { tag = route.id }
            }
        }
        routePolylines = built
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
