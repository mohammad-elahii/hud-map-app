package com.example.hudmapapp.data.model

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class RoutePreview(
    val id: String,
    val polylinePoints: List<RouteCoordinate>,
    val encodedPolyline: String,
    val distanceMeters: Int,
    val durationSeconds: Long,
    val routeToken: String? = null,
    val label: String? = null
)

sealed interface RoutePreviewError {
    data object Network : RoutePreviewError
    data object Timeout : RoutePreviewError
    data object Authentication : RoutePreviewError
    data object QuotaExceeded : RoutePreviewError
    data object InvalidRequest : RoutePreviewError
    data object NoResults : RoutePreviewError
    data object Cancelled : RoutePreviewError
    data object Unknown : RoutePreviewError
}

sealed interface RoutePreviewState {
    data object Idle : RoutePreviewState
    data object Loading : RoutePreviewState
    data class Available(val routes: List<RoutePreview>) : RoutePreviewState
    data object Empty : RoutePreviewState
    data object Cancelled : RoutePreviewState
    data class Error(val error: RoutePreviewError) : RoutePreviewState
}
