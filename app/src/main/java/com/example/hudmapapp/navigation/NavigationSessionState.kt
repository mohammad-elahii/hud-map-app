package com.example.hudmapapp.navigation

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview

sealed interface NavigationSessionState {
    data object Idle : NavigationSessionState
    data object Starting : NavigationSessionState
    data class Active(
        val destination: Destination,
        val route: RoutePreview
    ) : NavigationSessionState
    data object Stopping : NavigationSessionState
    data object Stopped : NavigationSessionState
    data object Arrived : NavigationSessionState
    data class Error(val error: NavigationSessionError) : NavigationSessionState
}

sealed interface NavigationSessionError {
    data object NavigatorNotReady : NavigationSessionError
    data object InvalidRoute : NavigationSessionError
    data class RouteFailed(val statusName: String?) : NavigationSessionError
    data object GuidanceFailed : NavigationSessionError
    data object Unknown : NavigationSessionError
}
