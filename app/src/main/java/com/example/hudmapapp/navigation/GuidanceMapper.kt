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
        "TURN_SLIGHT_LEFT", "OFF_RAMP_SLIGHT_LEFT", "FORK_LEFT" -> ManeuverType.SLIGHT_LEFT
        "TURN_LEFT", "OFF_RAMP_LEFT", "OFF_RAMP_KEEP_LEFT" -> ManeuverType.LEFT
        "TURN_SHARP_LEFT", "OFF_RAMP_SHARP_LEFT" -> ManeuverType.SHARP_LEFT
        "TURN_SLIGHT_RIGHT", "OFF_RAMP_SLIGHT_RIGHT", "FORK_RIGHT" -> ManeuverType.SLIGHT_RIGHT
        "TURN_RIGHT", "OFF_RAMP_RIGHT", "OFF_RAMP_KEEP_RIGHT" -> ManeuverType.RIGHT
        "TURN_SHARP_RIGHT", "OFF_RAMP_SHARP_RIGHT" -> ManeuverType.SHARP_RIGHT
        "UTURN_LEFT", "UTURN_RIGHT",
        "OFF_RAMP_U_TURN_CLOCKWISE", "OFF_RAMP_U_TURN_COUNTERCLOCKWISE" -> ManeuverType.U_TURN
        "RAMP_LEFT", "RAMP_RIGHT", "OFF_RAMP_UNSPECIFIED" -> ManeuverType.RAMP
        "MERGE_LEFT", "MERGE_RIGHT", "MERGE_UNSPECIFIED" -> ManeuverType.MERGE
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> ManeuverType.ROUNDABOUT
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
