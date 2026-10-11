package com.example.hudmapapp.navigation

internal fun mapManeuverType(maneuverCode: Int): ManeuverType {
    return try {
        val maneuverClass =
            Class.forName("com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver")
        val fields = maneuverClass.fields
        val nameByValue = fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }
        mapManeuverName(nameByValue[maneuverCode])
    } catch (_: Exception) {
        ManeuverType.UNKNOWN
    }
}

fun mapManeuverNameForTest(name: String?): ManeuverType = mapManeuverName(name)

internal fun mapManeuverName(name: String?): ManeuverType {
    return when (name) {
        "DEPART" -> ManeuverType.DEPART
        "STRAIGHT" -> ManeuverType.STRAIGHT
        "TURN_SLIGHT_LEFT", "ON_RAMP_SLIGHT_LEFT", "OFF_RAMP_SLIGHT_LEFT",
        "FORK_LEFT" -> ManeuverType.SLIGHT_LEFT
        "TURN_LEFT", "TURN_KEEP_LEFT", "ON_RAMP_LEFT", "ON_RAMP_KEEP_LEFT",
        "OFF_RAMP_LEFT", "OFF_RAMP_KEEP_LEFT" -> ManeuverType.LEFT
        "TURN_SHARP_LEFT", "ON_RAMP_SHARP_LEFT", "OFF_RAMP_SHARP_LEFT" -> ManeuverType.SHARP_LEFT
        "TURN_SLIGHT_RIGHT", "ON_RAMP_SLIGHT_RIGHT", "OFF_RAMP_SLIGHT_RIGHT",
        "FORK_RIGHT" -> ManeuverType.SLIGHT_RIGHT
        "TURN_RIGHT", "TURN_KEEP_RIGHT", "ON_RAMP_RIGHT", "ON_RAMP_KEEP_RIGHT",
        "OFF_RAMP_RIGHT", "OFF_RAMP_KEEP_RIGHT" -> ManeuverType.RIGHT
        "TURN_SHARP_RIGHT", "ON_RAMP_SHARP_RIGHT", "OFF_RAMP_SHARP_RIGHT" -> ManeuverType.SHARP_RIGHT
        "TURN_U_TURN_CLOCKWISE", "TURN_U_TURN_COUNTERCLOCKWISE",
        "ON_RAMP_U_TURN_CLOCKWISE", "ON_RAMP_U_TURN_COUNTERCLOCKWISE",
        "OFF_RAMP_U_TURN_CLOCKWISE", "OFF_RAMP_U_TURN_COUNTERCLOCKWISE" -> ManeuverType.U_TURN
        "ON_RAMP_UNSPECIFIED", "OFF_RAMP_UNSPECIFIED" -> ManeuverType.RAMP
        "MERGE_LEFT", "MERGE_RIGHT", "MERGE_UNSPECIFIED" -> ManeuverType.MERGE
        "ROUNDABOUT_CLOCKWISE", "ROUNDABOUT_COUNTERCLOCKWISE",
        "ROUNDABOUT_STRAIGHT_CLOCKWISE", "ROUNDABOUT_STRAIGHT_COUNTERCLOCKWISE",
        "ROUNDABOUT_LEFT_CLOCKWISE", "ROUNDABOUT_LEFT_COUNTERCLOCKWISE",
        "ROUNDABOUT_RIGHT_CLOCKWISE", "ROUNDABOUT_RIGHT_COUNTERCLOCKWISE",
        "ROUNDABOUT_SLIGHT_LEFT_CLOCKWISE", "ROUNDABOUT_SLIGHT_LEFT_COUNTERCLOCKWISE",
        "ROUNDABOUT_SLIGHT_RIGHT_CLOCKWISE", "ROUNDABOUT_SLIGHT_RIGHT_COUNTERCLOCKWISE",
        "ROUNDABOUT_SHARP_LEFT_CLOCKWISE", "ROUNDABOUT_SHARP_LEFT_COUNTERCLOCKWISE",
        "ROUNDABOUT_SHARP_RIGHT_CLOCKWISE", "ROUNDABOUT_SHARP_RIGHT_COUNTERCLOCKWISE",
        "ROUNDABOUT_U_TURN_CLOCKWISE", "ROUNDABOUT_U_TURN_COUNTERCLOCKWISE" -> ManeuverType.ROUNDABOUT
        "ROUNDABOUT_EXIT_CLOCKWISE", "ROUNDABOUT_EXIT_COUNTERCLOCKWISE" -> ManeuverType.ROUNDABOUT_EXIT
        "FERRY_BOAT", "FERRY_TRAIN" -> ManeuverType.FERRY
        "NAME_CHANGE" -> ManeuverType.NAME_CHANGE
        "DESTINATION", "DESTINATION_LEFT", "DESTINATION_RIGHT" -> ManeuverType.DESTINATION
        else -> ManeuverType.UNKNOWN
    }
}

internal fun mapStep(step: StepSnapshot?): ManeuverInfo? {
    if (step == null) return null
    return ManeuverInfo(
        type = mapManeuverType(step.maneuverCode),
        instruction = step.instruction.ifBlank { "Continue" },
        roadName = step.roadName,
        exitNumber = step.exitNumber?.takeIf { it.isNotBlank() },
        roundaboutExit = step.roundaboutExit
    )
}

internal fun applyGuidance(
    current: NavigationState,
    snapshot: GuidanceSnapshot?,
    status: GuidanceStatus = GuidanceStatus.ACTIVE
): NavigationState {
    if (snapshot == null) {
        return current.copy(status = status, error = NavigationDataError.GuidanceUnavailable)
    }
    val etaMillis = snapshot.remainingDurationSeconds?.let {
        if (it < 0) null else System.currentTimeMillis() + it * 1000L
    }
    return current.copy(
        status = status,
        currentManeuver = mapStep(snapshot.currentStep) ?: current.currentManeuver,
        nextManeuver = mapStep(snapshot.nextStep),
        progress = NavigationProgress(
            distanceToManeuverMeters = snapshot.distanceToManeuverMeters,
            remainingDistanceMeters = snapshot.remainingDistanceMeters,
            remainingDurationSeconds = snapshot.remainingDurationSeconds,
            etaMillis = etaMillis
        ),
        isRerouting = snapshot.routeChanged || current.isRerouting,
        error = null
    )
}
