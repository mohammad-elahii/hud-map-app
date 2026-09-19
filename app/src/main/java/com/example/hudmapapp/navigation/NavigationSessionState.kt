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
    data class Rerouting(
        val destination: Destination,
        val route: RoutePreview
    ) : NavigationSessionState
    data class OffRoute(
        val destination: Destination,
        val route: RoutePreview
    ) : NavigationSessionState
    data class Interrupted(
        val destination: Destination,
        val route: RoutePreview,
        val reason: InterruptionReason
    ) : NavigationSessionState
    data object Stopping : NavigationSessionState
    data object Stopped : NavigationSessionState
    data object Arrived : NavigationSessionState
    data class Error(val error: NavigationSessionError) : NavigationSessionState
}

enum class InterruptionReason {
    LOCATION_UNAVAILABLE,
    NETWORK_ERROR,
    GUIDANCE_PAUSED
}

sealed interface NavigationSessionError {
    data object NavigatorNotReady : NavigationSessionError
    data object InvalidRoute : NavigationSessionError
    data class RouteFailed(val statusName: String?) : NavigationSessionError
    data object GuidanceFailed : NavigationSessionError
    data object LocationUnavailable : NavigationSessionError
    data object NetworkError : NavigationSessionError
    data object Unknown : NavigationSessionError
}

enum class SessionRecoveryAction {
    RETRY_START,
    RESUME,
    STOP,
    NONE
}

fun recoveryActionFor(state: NavigationSessionState): SessionRecoveryAction {
    return when (state) {
        is NavigationSessionState.Error -> when (state.error) {
            NavigationSessionError.NavigatorNotReady,
            is NavigationSessionError.RouteFailed,
            NavigationSessionError.NetworkError -> SessionRecoveryAction.RETRY_START
            NavigationSessionError.LocationUnavailable -> SessionRecoveryAction.RESUME
            else -> SessionRecoveryAction.STOP
        }
        is NavigationSessionState.Interrupted -> SessionRecoveryAction.RESUME
        is NavigationSessionState.OffRoute,
        is NavigationSessionState.Rerouting -> SessionRecoveryAction.NONE
        else -> SessionRecoveryAction.NONE
    }
}

fun userMessageFor(state: NavigationSessionState): String? {
    return when (state) {
        is NavigationSessionState.Error -> when (state.error) {
            NavigationSessionError.NavigatorNotReady ->
                "Navigation is not ready yet. Please try again."
            NavigationSessionError.InvalidRoute ->
                "Could not plan a route for this destination."
            is NavigationSessionError.RouteFailed ->
                "No route found. Try a different destination."
            NavigationSessionError.GuidanceFailed ->
                "Could not start guidance. Please try again."
            NavigationSessionError.LocationUnavailable ->
                "Waiting for your location. Make sure location is enabled."
            NavigationSessionError.NetworkError ->
                "No connection. Navigation needs internet to start."
            NavigationSessionError.Unknown ->
                "Something went wrong. Please try again."
        }
        is NavigationSessionState.Interrupted -> when (state.reason) {
            InterruptionReason.LOCATION_UNAVAILABLE ->
                "Location lost. Waiting for GPS signal."
            InterruptionReason.NETWORK_ERROR ->
                "Connection lost. Reconnecting."
            InterruptionReason.GUIDANCE_PAUSED ->
                "Navigation paused. Waiting to resume."
        }
        is NavigationSessionState.OffRoute -> "Off route. Finding a new route."
        is NavigationSessionState.Rerouting -> "Finding a better route."
        NavigationSessionState.Arrived -> "You have arrived."
        else -> null
    }
}
