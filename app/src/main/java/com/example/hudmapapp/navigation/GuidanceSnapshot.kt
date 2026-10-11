package com.example.hudmapapp.navigation

data class StepSnapshot(
    val maneuverCode: Int,
    val instruction: String,
    val roadName: String,
    val exitNumber: String? = null,
    val roundaboutExit: Int? = null
)

data class GuidanceSnapshot(
    val currentStep: StepSnapshot?,
    val nextStep: StepSnapshot?,
    val distanceToManeuverMeters: Int?,
    val timeToManeuverSeconds: Long?,
    val remainingDistanceMeters: Int?,
    val remainingDurationSeconds: Long?,
    val routeChanged: Boolean = false
)
