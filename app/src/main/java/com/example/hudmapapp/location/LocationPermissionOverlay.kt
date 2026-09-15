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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Shows a non-blocking status banner when location is in a waiting or error state.
 * Displayed at the bottom of the map so the user can still interact with the map.
 *
 * @param state The current location state.
 * @param onRetry Called when the user taps retry.
 * @param onOpenSettings Called when the user taps "Open Settings".
 * @param onDismiss Called when the user dismisses the banner.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun LocationStatusBanner(
    state: LocationState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, subtitle, icon, showRetry, showSettings) = when (state) {
        is LocationState.WaitingForFix -> BannerConfig(
            title = "Looking for your location",
            subtitle = "Waiting for a GPS signal...",
            icon = Icons.Filled.LocationOn,
            showRetry = false,
            showSettings = false
        )
        is LocationState.Unavailable -> BannerConfig(
            title = "Location unavailable",
            subtitle = "We couldn't determine your current position.",
            icon = Icons.Filled.LocationOff,
            showRetry = true,
            showSettings = false
        )
        is LocationState.Error -> BannerConfig(
            title = "Location error",
            subtitle = state.message.ifEmpty { "Something went wrong." },
            icon = Icons.Filled.LocationOff,
            showRetry = true,
            showSettings = false
        )
        is LocationState.ServicesDisabled -> BannerConfig(
            title = "Location services are off",
            subtitle = "Turn on location services to show your position on the map.",
            icon = Icons.Filled.LocationOff,
            showRetry = false,
            showSettings = true
        )
        is LocationState.NetworkUnavailable -> BannerConfig(
            title = "No internet connection",
            subtitle = "Map tiles cannot load without a network connection.",
            icon = Icons.Filled.WifiOff,
            showRetry = true,
            showSettings = false
        )
        is LocationState.PermissionRequired -> return // handled by overlay, not banner
        is LocationState.Available -> return       // nothing to show
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    if (state is LocationState.WaitingForFix) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showRetry || showSettings) {
                if (showRetry) {
                    HudButton(
                        text = "Retry",
                        onClick = onRetry,
                        leadingIcon = Icons.Filled.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showSettings) {
                    HudButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        leadingIcon = Icons.Filled.Settings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private data class BannerConfig(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val showRetry: Boolean,
    val showSettings: Boolean
)

/**
 * Full-screen overlay for permission-blocked states.
 * Shown on top of everything when location cannot proceed at all.
 */
@Composable
fun LocationBlockOverlay(
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
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
                    LocationBlockReason.GpsDisabled -> "Location Services Off"
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
