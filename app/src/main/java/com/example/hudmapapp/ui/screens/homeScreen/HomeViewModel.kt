package com.example.hudmapapp.ui.screens.homeScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.data.model.SelectedDestinationState
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.data.repository.RoutePlanningRepository
import com.example.hudmapapp.data.repository.debug
import com.example.hudmapapp.data.repository.error
import com.example.hudmapapp.data.repository.warn
import com.example.hudmapapp.navigation.RoutePreviewCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val routePreviewCoordinator: RoutePreviewCoordinator? = null,
    private val logger: RouteLogger = RouteLogger.android()
) : ViewModel() {

    private val _selectedDestination = MutableStateFlow<SelectedDestinationState>(
        SelectedDestinationState.None
    )
    val selectedDestination: StateFlow<SelectedDestinationState> =
        _selectedDestination.asStateFlow()

    val routePreviewState: StateFlow<RoutePreviewState> =
        routePreviewCoordinator?.routePreviewState
            ?: MutableStateFlow<RoutePreviewState>(RoutePreviewState.Idle).asStateFlow()

    val selectedRoute: StateFlow<RoutePreview?> =
        routePreviewCoordinator?.selectedRoute
            ?: MutableStateFlow<RoutePreview?>(null).asStateFlow()

    fun selectDestination(destination: Destination) {
        _selectedDestination.value = SelectedDestinationState.Selected(destination)
    }

    fun confirmDestination() {
        val current = _selectedDestination.value
        if (current is SelectedDestinationState.Selected) {
            _selectedDestination.value = SelectedDestinationState.Confirmed(current.destination)
        }
    }

    fun changeDestination(destination: Destination) {
        _selectedDestination.value = SelectedDestinationState.Selected(destination)
    }

    fun clearDestination() {
        _selectedDestination.value = SelectedDestinationState.None
        routePreviewCoordinator?.clearRoutePreview()
    }

    fun requestRoutePreview(originLatitude: Double?, originLongitude: Double?) {
        val confirmed = _selectedDestination.value as? SelectedDestinationState.Confirmed
            ?: run {
                logger.warn("requestRoutePreview skipped: destination not confirmed")
                return
            }
        if (routePreviewCoordinator == null) {
            logger.error("requestRoutePreview dropped: RoutePreviewCoordinator is null")
            return
        }
        logger.debug(
            "requestRoutePreview origin=($originLatitude,$originLongitude) " +
                "destination=(${confirmed.destination.latitude},${confirmed.destination.longitude})"
        )
        routePreviewCoordinator.requestRoutePreview(
            originLatitude = originLatitude,
            originLongitude = originLongitude,
            destinationLatitude = confirmed.destination.latitude,
            destinationLongitude = confirmed.destination.longitude
        )
    }

    fun selectRoute(routeId: String) {
        routePreviewCoordinator?.selectRoute(routeId)
    }

    fun clearRoutePreview() {
        routePreviewCoordinator?.clearRoutePreview()
    }

    class Factory(
        private val context: android.content.Context,
        private val apiKey: String,
        private val logger: RouteLogger = RouteLogger.android()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = RoutePlanningRepository.create(
                context.applicationContext,
                apiKey,
                logger
            )
            return HomeViewModel(RoutePreviewCoordinator(repository, logger), logger) as T
        }
    }
}
