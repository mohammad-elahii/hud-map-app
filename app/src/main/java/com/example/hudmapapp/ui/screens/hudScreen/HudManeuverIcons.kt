package com.example.hudmapapp.ui.screens.hudScreen

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.hudmapapp.navigation.ManeuverType

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
