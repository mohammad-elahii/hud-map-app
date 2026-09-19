package com.example.hudmapapp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewError
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.data.repository.RoutePlanningRepository
import com.example.hudmapapp.data.repository.RoutePlanningResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutePreviewCoordinator(
    private val routePlanningRepository: RoutePlanningRepository
) : ViewModel() {

    private val _routePreviewState =
        MutableStateFlow<RoutePreviewState>(RoutePreviewState.Idle)
    val routePreviewState: StateFlow<RoutePreviewState> = _routePreviewState.asStateFlow()

    private val _selectedRoute = MutableStateFlow<RoutePreview?>(null)
    val selectedRoute: StateFlow<RoutePreview?> = _selectedRoute.asStateFlow()

    private val requestSequence = AtomicLong(0)
    private var previewJob: Job? = null

    fun requestRoutePreview(
        originLatitude: Double?,
        originLongitude: Double?,
        destinationLatitude: Double?,
        destinationLongitude: Double?
    ) {
        val sequence = requestSequence.incrementAndGet()
        previewJob?.cancel()
        _selectedRoute.value = null

        if (!RoutePlanningRepository.isValidCoordinate(originLatitude, originLongitude) ||
            !RoutePlanningRepository.isValidCoordinate(destinationLatitude, destinationLongitude)
        ) {
            _routePreviewState.value =
                RoutePreviewState.Error(RoutePreviewError.InvalidRequest)
            return
        }

        _routePreviewState.value = RoutePreviewState.Loading
        previewJob = viewModelScope.launch {
            try {
                val result = routePlanningRepository.requestRoutes(
                    originLatitude = originLatitude,
                    originLongitude = originLongitude,
                    destinationLatitude = destinationLatitude,
                    destinationLongitude = destinationLongitude
                )
                if (sequence != requestSequence.get()) return@launch
                when (result) {
                    is RoutePlanningResult.Success -> {
                        _routePreviewState.value =
                            RoutePreviewState.Available(result.routes)
                        _selectedRoute.value = result.routes.firstOrNull()
                    }
                    RoutePlanningResult.Empty ->
                        _routePreviewState.value = RoutePreviewState.Empty
                    is RoutePlanningResult.Failure ->
                        _routePreviewState.value = RoutePreviewState.Error(result.error)
                }
            } catch (e: CancellationException) {
                if (sequence == requestSequence.get()) {
                    _routePreviewState.value = RoutePreviewState.Cancelled
                }
                throw e
            }
        }
    }

    fun selectRoute(routeId: String) {
        val current = _routePreviewState.value
        if (current !is RoutePreviewState.Available) return
        _selectedRoute.value = current.routes.firstOrNull { it.id == routeId }
    }

    fun clearRoutePreview() {
        requestSequence.incrementAndGet()
        previewJob?.cancel()
        previewJob = null
        _selectedRoute.value = null
        _routePreviewState.value = RoutePreviewState.Idle
    }
}
