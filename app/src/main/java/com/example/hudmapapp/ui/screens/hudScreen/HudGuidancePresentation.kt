package com.example.hudmapapp.ui.screens.hudScreen

import com.example.hudmapapp.navigation.ManeuverType
import com.example.hudmapapp.navigation.NavigationState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val HUD_MISSING_VALUE = "—"

fun rotationForManeuver(type: ManeuverType): Float {
    return when (type) {
        ManeuverType.SLIGHT_LEFT -> -30f
        ManeuverType.LEFT -> -90f
        ManeuverType.SHARP_LEFT -> -135f
        ManeuverType.SLIGHT_RIGHT -> 30f
        ManeuverType.RIGHT -> 90f
        ManeuverType.SHARP_RIGHT -> 135f
        ManeuverType.U_TURN -> 180f
        ManeuverType.STRAIGHT,
        ManeuverType.DEPART,
        ManeuverType.RAMP,
        ManeuverType.FORK,
        ManeuverType.MERGE,
        ManeuverType.ROUNDABOUT,
        ManeuverType.FERRY,
        ManeuverType.NAME_CHANGE,
        ManeuverType.DESTINATION,
        ManeuverType.UNKNOWN -> 0f
    }
}

fun formatHudDistance(meters: Int?): String {
    if (meters == null || meters < 0) return HUD_MISSING_VALUE
    if (meters < 1000) return "$meters m"
    return String.format(Locale.US, "%.1f km", meters / 1000f)
}

fun headlineFor(state: NavigationState): String {
    val instruction = state.currentManeuver?.instruction
    return if (instruction.isNullOrBlank()) "Continue" else instruction
}

fun nextLineFor(state: NavigationState): String? {
    val next = state.nextManeuver ?: return null
    val base = if (next.instruction.isBlank()) "Continue" else next.instruction
    return if (next.type == ManeuverType.ROUNDABOUT && next.roundaboutExit != null) {
        "$base · exit ${next.roundaboutExit}"
    } else {
        "Then $base"
    }
}

fun footerFor(state: NavigationState): String? {
    val parts = listOfNotNull(
        state.progress.remainingDistanceMeters
            ?.takeIf { it >= 0 }
            ?.let { formatHudDistance(it) },
        formatHudDuration(state.progress.remainingDurationSeconds),
        state.progress.etaMillis
            ?.takeIf { it > 0 }
            ?.let { "ETA ${formatHudEta(it)}" }
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

fun formatHudDuration(seconds: Long?): String? {
    if (seconds == null || seconds < 0) return null
    if (seconds < 3600) return "${maxOf(1L, seconds / 60)} min"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (minutes == 0L) "$hours h" else "$hours h $minutes min"
}

fun formatHudEta(etaMillis: Long): String {
    return Instant.ofEpochMilli(etaMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
