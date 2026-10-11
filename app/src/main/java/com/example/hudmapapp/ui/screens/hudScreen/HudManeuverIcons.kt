package com.example.hudmapapp.ui.screens.hudScreen

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.hudmapapp.navigation.ManeuverType

/*
 * Custom maneuver icons.
 *
 * Drop one vector drawable (SVG imported via Android Studio) per maneuver
 * into `app/src/main/res/drawable/` using exactly these file names and it
 * is picked up automatically — no code changes. Missing files fall back to
 * the built-in rotating arrow / roundabout glyph.
 *
 * Expected files:
 *   ic_maneuver_depart
 *   ic_maneuver_straight
 *   ic_maneuver_slight_left
 *   ic_maneuver_left
 *   ic_maneuver_sharp_left
 *   ic_maneuver_slight_right
 *   ic_maneuver_right
 *   ic_maneuver_sharp_right
 *   ic_maneuver_u_turn
 *   ic_maneuver_ramp
 *   ic_maneuver_fork
 *   ic_maneuver_merge
 *   ic_maneuver_roundabout
 *   ic_maneuver_roundabout_exit
 *   ic_maneuver_ferry
 *   ic_maneuver_name_change
 *   ic_maneuver_destination
 *   ic_maneuver_unknown
 */

internal fun iconResourceName(type: ManeuverType): String =
    "ic_maneuver_" + type.name.lowercase()

@Composable
internal fun rememberManeuverIconResId(type: ManeuverType): Int? {
    val context = LocalContext.current
    return remember(type, context) {
        val id = context.resources.getIdentifier(
            iconResourceName(type),
            "drawable",
            context.packageName
        )
        if (id != 0) id else null
    }
}
