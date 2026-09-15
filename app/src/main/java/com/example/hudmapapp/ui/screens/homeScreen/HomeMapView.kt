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
import com.example.hudmapapp.ui.theme.components.HudButton
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

private val DefaultMapCenter = LatLng(36.2949894, 59.5928662)
private const val DefaultMapZoom = 14f
private const val UserLocationZoom = 17f

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
                zoomControlsEnabled = false,
                zoomGesturesEnabled = true
            ),
            onMapLoaded = {
                mapError = MapError.None
            }
        )

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
