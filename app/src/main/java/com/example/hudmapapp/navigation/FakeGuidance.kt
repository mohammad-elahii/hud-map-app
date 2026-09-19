package com.example.hudmapapp.navigation

fun fakeStep(
    maneuverCode: Int = 0,
    instruction: String = "Turn left onto Main St",
    roadName: String = "Main St"
): StepSnapshot = StepSnapshot(
    maneuverCode = maneuverCode,
    instruction = instruction,
    roadName = roadName
)

fun fakeGuidance(
    instruction: String = "Turn left onto Main St",
    roadName: String = "Main St",
    nextInstruction: String = "Continue on Main St",
    nextRoadName: String = "Main St",
    distanceToManeuverMeters: Int? = 300,
    remainingDistanceMeters: Int? = 1200,
    remainingDurationSeconds: Long? = 420L,
    routeChanged: Boolean = false
): GuidanceSnapshot = GuidanceSnapshot(
    currentStep = StepSnapshot(
        maneuverCode = 0,
        instruction = instruction,
        roadName = roadName
    ),
    nextStep = StepSnapshot(
        maneuverCode = 0,
        instruction = nextInstruction,
        roadName = nextRoadName
    ),
    distanceToManeuverMeters = distanceToManeuverMeters,
    timeToManeuverSeconds = null,
    remainingDistanceMeters = remainingDistanceMeters,
    remainingDurationSeconds = remainingDurationSeconds,
    routeChanged = routeChanged
)

fun fakeNavigationState(
    status: GuidanceStatus = GuidanceStatus.ACTIVE,
    instruction: String = "Turn left onto Main St",
    distanceToManeuverMeters: Int? = 300
): NavigationState = applyGuidance(
    NavigationState(status = status),
    fakeGuidance(
        instruction = instruction,
        distanceToManeuverMeters = distanceToManeuverMeters
    ),
    status = status
)
