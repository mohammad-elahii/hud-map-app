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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.location.AppLocation
import com.example.hudmapapp.location.FusedLocationProvider
import com.example.hudmapapp.location.LocationBlockReason
import com.example.hudmapapp.location.LocationPermissionHandler
import com.example.hudmapapp.location.LocationState
import com.example.hudmapapp.location.LocationStatusBanner
import com.example.hudmapapp.location.LocationBlockOverlay
import com.example.hudmapapp.location.LocationProvider
import com.example.hudmapapp.location.hasLocationPermission
import com.example.hudmapapp.location.isLocationEnabled
import com.example.hudmapapp.location.isNetworkAvailable
import com.example.hudmapapp.ui.navigation.AppRoute
import com.example.hudmapapp.ui.theme.DeepPurple30

@Composable
fun HomeScreen(
    navController: NavController,
    map: @Composable (AppLocation?, Int) -> Unit = { loc, trigger ->
        HomeMapView(currentLocation = loc, recenterTrigger = trigger)
    }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayReason by remember { mutableStateOf<LocationBlockReason?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var dismissBanner by remember { mutableStateOf(false) }
    var networkAvailable by remember { mutableStateOf(isNetworkAvailable(context)) }

    val locationProvider: LocationProvider = remember {
        FusedLocationProvider(context.applicationContext)
    }

    val currentLocation by locationProvider.locationUpdates
        .collectAsState(initial = null)

    val locationState by locationProvider.locationState.collectAsState()

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }

    // ---------------------------------------------------------------
    // Lifecycle-aware start/stop
    //
    // START  → check permission + GPS → start updates
    // STOP   → stop updates
    // ---------------------------------------------------------------
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    hasPermission = hasLocationPermission(context)
                    if (hasPermission && isLocationEnabled(context)) {
                        locationProvider.startUpdates()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    locationProvider.stopUpdates()
                }
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationProvider.stopUpdates()
        }
    }

    // Also re-check on permission change (e.g. user grants in-dialog)
    LaunchedEffect(hasPermission) {
        if (hasPermission && isLocationEnabled(context)) {
            locationProvider.startUpdates()
        }
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }

    fun retryLocation() {
        dismissBanner = false
        networkAvailable = isNetworkAvailable(context)
        if (hasPermission && isLocationEnabled(context) && networkAvailable) {
            locationProvider.startUpdates()
        }
    }

    LocationPermissionHandler(
        onPermissionGranted = {
            hasPermission = true
            if (!isLocationEnabled(context)) {
                overlayReason = LocationBlockReason.GpsDisabled
            } else {
                locationProvider.startUpdates()
            }
        }
    ) { permGranted, permissionActions ->

        LaunchedEffect(permGranted) { hasPermission = permGranted }

        Scaffold { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                map(currentLocation, recenterTrigger)

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
                            if (permGranted) {
                                if (isLocationEnabled(context)) {
                                    recenterTrigger++
                                } else {
                                    overlayReason = LocationBlockReason.GpsDisabled
                                }
                            } else {
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

                if (!dismissBanner && permGranted && isLocationEnabled(context)) {
                    val bannerState = if (!networkAvailable) {
                        LocationState.NetworkUnavailable
                    } else {
                        locationState
                    }

                    when (bannerState) {
                        is LocationState.WaitingForFix,
                        is LocationState.Unavailable,
                        is LocationState.Error,
                        is LocationState.NetworkUnavailable -> {
                            LocationStatusBanner(
                                state = bannerState,
                                onRetry = { retryLocation() },
                                onOpenSettings = { openLocationSettings() },
                                onDismiss = { dismissBanner = true },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            )
                        }
                        else -> { /* no banner needed */ }
                    }
                }

                if (overlayReason != null) {
                    LocationBlockOverlay(
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        navController = rememberNavController()
    )
}
