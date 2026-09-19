package com.example.hudmapapp.navigation

enum class ManeuverType {
    DEPART,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    RAMP,
    FORK,
    MERGE,
    ROUNDABOUT,
    FERRY,
    NAME_CHANGE,
    DESTINATION,
    UNKNOWN
}

data class ManeuverInfo(
    val type: ManeuverType,
    val instruction: String,
    val roadName: String,
    val exitNumber: String? = null,
    val roundaboutExit: Int? = null
)

data class NavigationProgress(
    val distanceToManeuverMeters: Int?,
    val remainingDistanceMeters: Int?,
    val remainingDurationSeconds: Long?,
    val etaMillis: Long?
)

enum class GuidanceStatus {
    IDLE,
    ACTIVE,
    REROUTING,
    OFF_ROUTE,
    INTERRUPTED,
    ARRIVED,
    STOPPED,
    ERROR
}

data class NavigationState(
    val status: GuidanceStatus = GuidanceStatus.IDLE,
    val currentManeuver: ManeuverInfo? = null,
    val nextManeuver: ManeuverInfo? = null,
    val progress: NavigationProgress = NavigationProgress(null, null, null, null),
    val isRerouting: Boolean = false,
    val error: NavigationDataError? = null
)

sealed interface NavigationDataError {
    data object GuidanceUnavailable : NavigationDataError
    data object ListenerFailed : NavigationDataError
    data object Unknown : NavigationDataError
}
