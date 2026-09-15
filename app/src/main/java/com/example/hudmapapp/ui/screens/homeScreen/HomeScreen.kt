package com.example.hudmapapp.ui.screens.homeScreen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.location.LocationBlockReason
import com.example.hudmapapp.location.LocationPermissionHandler
import com.example.hudmapapp.location.LocationPermissionOverlay
import com.example.hudmapapp.location.isLocationEnabled
import com.example.hudmapapp.ui.navigation.AppRoute
import com.example.hudmapapp.ui.theme.DeepPurple30

@Composable
fun HomeScreen(
    navController: NavController,
    map: @Composable (Modifier) -> Unit = { HomeMapView(modifier = it) }
) {
    val context = LocalContext.current
    var overlayReason by remember { mutableStateOf<LocationBlockReason?>(null) }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }

    LocationPermissionHandler(
        onPermissionGranted = {
            if (!isLocationEnabled(context)) {
                overlayReason = LocationBlockReason.GpsDisabled
            }
        }
    ) { hasPermission, permissionActions ->
        Scaffold { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                map(Modifier.fillMaxSize())

                HomeTopBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    onSettingsClick = {
                        navController.navigate(AppRoute.HUD)
                    }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            end = 20.dp,
                            bottom = 160.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MapControlButton(
                        icon = Icons.Filled.Layers,
                        contentDescription = "Map layers"
                    )

                    MapControlButton(
                        icon = Icons.Filled.MyLocation,
                        contentDescription = "Recenter on my location",
                        onClick = {
                            // Two-step check: permission → GPS
                            if (hasPermission) {
                                // Step 1 passed — check GPS
                                if (isLocationEnabled(context)) {
                                    // Both checks passed — do nothing for now
                                    // (Phase 2.2+ will center map on user location)
                                } else {
                                    overlayReason = LocationBlockReason.GpsDisabled
                                }
                            } else {
                                // Step 1 failed — request permission
                                overlayReason = LocationBlockReason.PermissionDenied
                                permissionActions.request()
                            }
                        }
                    )

                    MapControlButton(
                        icon = Icons.Filled.DarkMode,
                        contentDescription = "Dark mode toggle"
                    )
                }

                if (overlayReason != null) {
                    LocationPermissionOverlay(
                        reason = overlayReason!!,
                        onRequestPermission = {
                            overlayReason = null
                            permissionActions.request()
                        },
                        onOpenSettings = {
                            overlayReason = null
                            permissionActions.openSettings()
                        },
                        onOpenLocationSettings = {
                            overlayReason = null
                            openLocationSettings()
                        },
                        onDismiss = {
                            overlayReason = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            )
            .background(
                color = DeepPurple30,
                shape = RoundedCornerShape(50)
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Good morning",
                style = typography.labelMedium,
                color = colors.onSurfaceVariant
            )

            Text(
                text = "Where to?",
                style = typography.labelLarge,
                color = colors.onSurface
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colors.primary)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = colors.onSurface
            )
        }
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.tertiary)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.surface
        )
    }
}

