package com.example.hudmapapp.navigation

internal fun mapNavInfo(navInfo: Any): GuidanceSnapshot? {
    return try {
        val navInfoClass = navInfo.javaClass
        val currentStep = navInfoClass.getMethod("getCurrentStep").invoke(navInfo)
        val remainingSteps =
            navInfoClass.getMethod("getRemainingSteps").invoke(navInfo) as? Array<*>
        val distanceToStep =
            navInfoClass.getMethod("getDistanceToCurrentStepMeters").invoke(navInfo) as? Int
        val timeToStep =
            navInfoClass.getMethod("getTimeToCurrentStepSeconds").invoke(navInfo) as? Int
        val remainingDistance =
            navInfoClass.getMethod("getDistanceToFinalDestinationMeters").invoke(navInfo) as? Int
        val remainingDuration =
            navInfoClass.getMethod("getTimeToFinalDestinationSeconds").invoke(navInfo) as? Int
        val routeChanged =
            (navInfoClass.getMethod("getRouteChanged").invoke(navInfo) as? Boolean) ?: false
        GuidanceSnapshot(
            currentStep = mapTurnStep(currentStep),
            nextStep = (remainingSteps?.firstOrNull())?.let { mapTurnStep(it) },
            distanceToManeuverMeters = distanceToStep,
            timeToManeuverSeconds = timeToStep?.toLong(),
            remainingDistanceMeters = remainingDistance,
            remainingDurationSeconds = remainingDuration?.toLong(),
            routeChanged = routeChanged
        )
    } catch (_: Exception) {
        null
    }
}

private fun mapTurnStep(step: Any?): StepSnapshot? {
    if (step == null) return null
    return try {
        val stepClass = step.javaClass
        val maneuver = stepClass.getMethod("getManeuver").invoke(step) as? Int ?: -1
        val instruction = (
            stepClass.getMethod("getFullInstructionText").invoke(step) as? String
            ).orEmpty()
        val road = (stepClass.getMethod("getFullRoadName").invoke(step) as? String).orEmpty()
        val exit = runCatching {
            stepClass.getMethod("getExitNumber").invoke(step) as? String
        }.getOrNull()
        val roundabout = runCatching {
            stepClass.getMethod("getRoundaboutTurnNumber").invoke(step) as? Int
        }.getOrNull()
        StepSnapshot(
            maneuverCode = maneuver,
            instruction = instruction,
            roadName = road,
            exitNumber = exit,
            roundaboutExit = roundabout
        )
    } catch (_: Exception) {
        null
    }
}
