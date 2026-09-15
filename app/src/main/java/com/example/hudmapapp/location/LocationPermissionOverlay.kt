package com.example.hudmapapp.location

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hudmapapp.ui.theme.components.HudButton


enum class LocationBlockReason {
    PermissionDenied,
    PermissionPermanentlyDenied,
    GpsDisabled
}

@Composable
fun LocationPermissionOverlay(
    reason: LocationBlockReason,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
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
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (reason) {
                    LocationBlockReason.PermissionDenied -> "Location Permission Required"
                    LocationBlockReason.PermissionPermanentlyDenied -> "Permission Denied"
                    LocationBlockReason.GpsDisabled -> "GPS Disabled"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (reason) {
                    LocationBlockReason.PermissionDenied ->
                        "This app needs location permission to show your position on the map."
                    LocationBlockReason.PermissionPermanentlyDenied ->
                        "Location permission has been permanently denied. Please enable it in app settings."
                    LocationBlockReason.GpsDisabled ->
                        "Location services are turned off. Please enable GPS to use location features."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (reason) {
                LocationBlockReason.PermissionDenied -> {
                    HudButton(
                        text = "Enable Location",
                        onClick = onRequestPermission,
                        leadingIcon = Icons.Filled.LocationOn,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LocationBlockReason.PermissionPermanentlyDenied -> {
                    HudButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        leadingIcon = Icons.Filled.Settings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LocationBlockReason.GpsDisabled -> {
                    HudButton(
                        text = "Enable GPS",
                        onClick = onOpenLocationSettings,
                        leadingIcon = Icons.Filled.LocationOn,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
