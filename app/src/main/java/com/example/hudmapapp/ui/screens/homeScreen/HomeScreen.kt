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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.location.AppLocation
import com.example.hudmapapp.HudMapApplication
import com.example.hudmapapp.navigation.NavigationInitState
import com.example.hudmapapp.navigation.NavigationSessionCoordinator
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.SdkNavigatorAdapter
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.DestinationSearchState
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.data.model.SelectedDestinationState
import com.example.hudmapapp.data.repository.DestinationRepository
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
import com.example.hudmapapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            LocalContext.current.applicationContext,
            BuildConfig.MAPS_API_KEY
        )
    ),
    map: @Composable (
        AppLocation?,
        Int,
        SelectedDestinationState,
        List<RoutePreview>,
        String?,
        (String) -> Unit
    ) -> Unit = { loc, trigger, selected, routes, selectedRouteId, onRouteSelected ->
        HomeMapView(
            currentLocation = loc,
            recenterTrigger = trigger,
            selectedDestination = selected,
            routePreviews = routes,
            selectedRouteId = selectedRouteId,
            onRouteSelected = onRouteSelected
        )
    },
    onStartNavigation: (RoutePreview) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedDestination by viewModel.selectedDestination.collectAsState()
    val routePreviewState by viewModel.routePreviewState.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()

    var overlayReason by remember { mutableStateOf<LocationBlockReason?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var dismissBanner by remember { mutableStateOf(false) }
    var networkAvailable by remember { mutableStateOf(isNetworkAvailable(context)) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<DestinationSearchState>(DestinationSearchState.Idle) }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val locationProvider: LocationProvider = remember {
        FusedLocationProvider(context.applicationContext)
    }

    val destinationRepository: DestinationRepository = remember {
        DestinationRepository.create(context.applicationContext, BuildConfig.MAPS_API_KEY)
    }

    val navigationManager = remember {
        (context.applicationContext as HudMapApplication).navigationManager
    }

    val navigationInitState by navigationManager.initState.collectAsState()

    val sessionCoordinator = remember {
        NavigationSessionCoordinator(
            adapterProvider = {
                val state = navigationManager.initState.value
                if (state is NavigationInitState.Ready) {
                    SdkNavigatorAdapter(state.navigator)
                } else {
                    null
                }
            }
        )
    }
    val sessionState by sessionCoordinator.sessionState.collectAsState()

    LaunchedEffect(Unit) {
        navigationManager.initialize(context.applicationContext as HudMapApplication)
    }

    DisposableEffect(Unit) {
        onDispose {
            sessionCoordinator.stopNavigation()
            navigationManager.shutdown()
        }
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

    fun searchDestinations(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchState = DestinationSearchState.Idle
            return
        }

        searchState = DestinationSearchState.Searching
        searchJob = coroutineScope.launch {
            delay(300) // Debounce

            val biasLat = currentLocation?.latitude
            val biasLng = currentLocation?.longitude

            val result = destinationRepository.searchDestinations(
                query = query,
                biasLatitude = biasLat,
                biasLongitude = biasLng
            )

            result.fold(
                onSuccess = { destinations ->
                    searchState = if (destinations.isEmpty()) {
                        DestinationSearchState.Empty
                    } else {
                        DestinationSearchState.Results(destinations)
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    searchState = DestinationSearchState.Error(
                        e.message ?: "Search failed"
                    )
                }
            )
        }
    }

    fun onDestinationSelected(destination: Destination) {
        viewModel.clearRoutePreview()
        viewModel.selectDestination(destination)
        searchQuery = destination.name
        searchState = DestinationSearchState.Idle

        coroutineScope.launch {
            val result = destinationRepository.fetchPlaceDetails(destination.placeId)
            result.onSuccess { detailed ->
                viewModel.changeDestination(detailed)
            }
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

                val previewRoutes = when (routePreviewState) {
                    is RoutePreviewState.Available ->
                        (routePreviewState as RoutePreviewState.Available).routes
                    else -> emptyList()
                }

                map(
                    currentLocation,
                    recenterTrigger,
                    selectedDestination,
                    previewRoutes,
                    selectedRoute?.id,
                    { routeId -> viewModel.selectRoute(routeId) }
                )

                HomeTopBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    onSettingsClick = {
                        navController.navigate(AppRoute.HUD)
                    }
                )

                DestinationSearchBar(
                    query = searchQuery,
                    onQueryChange = { query ->
                        searchQuery = query
                        searchDestinations(query)
                    },
                    onClear = {
                        searchQuery = ""
                        searchState = DestinationSearchState.Idle
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                )

                DestinationSearchResults(
                    searchState = searchState,
                    onDestinationClick = { destination ->
                        onDestinationSelected(destination)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 160.dp)
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

            if (selectedDestination is SelectedDestinationState.Selected) {
                val dest = (selectedDestination as SelectedDestinationState.Selected).destination
                DestinationBottomSheet(
                    destination = dest,
                    onConfirm = {
                        viewModel.confirmDestination()
                        viewModel.requestRoutePreview(
                            originLatitude = currentLocation?.latitude,
                            originLongitude = currentLocation?.longitude
                        )
                    },
                    onClear = {
                        viewModel.clearDestination()
                        searchQuery = ""
                    },
                    onDismiss = { viewModel.clearDestination() }
                )
            }

            if (selectedDestination is SelectedDestinationState.Confirmed &&
                sessionState !is NavigationSessionState.Active &&
                sessionState !is NavigationSessionState.Starting &&
                sessionState !is NavigationSessionState.Rerouting &&
                sessionState !is NavigationSessionState.OffRoute &&
                sessionState !is NavigationSessionState.Interrupted
            ) {
                val dest = (selectedDestination as SelectedDestinationState.Confirmed).destination
                RoutePreviewSheet(
                    destination = dest,
                    routePreviewState = routePreviewState,
                    selectedRoute = selectedRoute,
                    onRouteSelected = { routeId -> viewModel.selectRoute(routeId) },
                    onStartNavigation = { route ->
                        sessionCoordinator.startNavigation(dest, route)
                        onStartNavigation(route)
                    },
                    onDismiss = {
                        viewModel.clearDestination()
                        searchQuery = ""
                    }
                )
            }

            val activeSession = when (val session = sessionState) {
                is NavigationSessionState.Active -> session
                is NavigationSessionState.Starting -> session
                is NavigationSessionState.Rerouting -> session
                is NavigationSessionState.OffRoute -> session
                is NavigationSessionState.Interrupted -> session
                else -> null
            }
            if (activeSession != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    NavigationSessionBanner(
                        sessionState = activeSession,
                        onStop = {
                            sessionCoordinator.stopNavigation()
                            viewModel.clearDestination()
                            searchQuery = ""
                        },
                        onRetry = { sessionCoordinator.retryStart() },
                        onResume = { sessionCoordinator.resume() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            val sessionError = sessionState as? NavigationSessionState.Error
            if (sessionError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    NavigationSessionBanner(
                        sessionState = sessionError,
                        onStop = {
                            sessionCoordinator.stopNavigation()
                            viewModel.clearDestination()
                            searchQuery = ""
                        },
                        onRetry = { sessionCoordinator.retryStart() },
                        onResume = { sessionCoordinator.resume() },
                        modifier = Modifier.fillMaxWidth()
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
        navController = rememberNavController(),
        map = { _, _, _, _, _, _ -> }
    )
}
