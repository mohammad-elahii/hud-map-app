package com.example.hudmapapp

import com.example.hudmapapp.navigation.GuidanceStatus
import com.example.hudmapapp.navigation.ManeuverInfo
import com.example.hudmapapp.navigation.ManeuverType
import com.example.hudmapapp.navigation.NavigationProgress
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.ui.screens.hudScreen.HUD_MISSING_VALUE
import com.example.hudmapapp.ui.screens.hudScreen.footerFor
import com.example.hudmapapp.ui.screens.hudScreen.formatHudDistance
import com.example.hudmapapp.ui.screens.hudScreen.formatHudDuration
import com.example.hudmapapp.ui.screens.hudScreen.headlineFor
import com.example.hudmapapp.ui.screens.hudScreen.nextLineFor
import com.example.hudmapapp.ui.screens.hudScreen.rotationForManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudGuidancePresentationTest {

    @Test
    fun `every maneuver type has a defined rotation`() {
        ManeuverType.entries.forEach { type ->
            rotationForManeuver(type)
        }
    }

    @Test
    fun `turn rotations match driver expectation`() {
        assertEquals(-90f, rotationForManeuver(ManeuverType.LEFT), 0.001f)
        assertEquals(90f, rotationForManeuver(ManeuverType.RIGHT), 0.001f)
        assertEquals(0f, rotationForManeuver(ManeuverType.STRAIGHT), 0.001f)
        assertEquals(0f, rotationForManeuver(ManeuverType.UNKNOWN), 0.001f)
        assertEquals(180f, rotationForManeuver(ManeuverType.U_TURN), 0.001f)
    }

    @Test
    fun `distance formats metric with fallback`() {
        assertEquals("300 m", formatHudDistance(300))
        assertEquals("1.2 km", formatHudDistance(1200))
        assertEquals(HUD_MISSING_VALUE, formatHudDistance(null))
        assertEquals(HUD_MISSING_VALUE, formatHudDistance(-5))
    }

    @Test
    fun `blank instruction falls back to Continue`() {
        assertEquals("Continue", headlineFor(state(instruction = "")))
        assertEquals("Turn left", headlineFor(state(instruction = "Turn left")))
    }

    @Test
    fun `null next maneuver hides preview line`() {
        assertNull(nextLineFor(state(nextInstruction = null)))
        assertEquals(
            "Then Continue on Main St",
            nextLineFor(state(nextInstruction = "Continue on Main St"))
        )
    }

    @Test
    fun `roundabout preview carries exit number`() {
        val line = nextLineFor(roundaboutState(exit = 2))
        assertTrue(line!!.contains("exit 2"))
    }

    @Test
    fun `missing progress hides footer values instead of zeroing`() {
        assertNull(footerFor(state(remaining = null, duration = null, eta = null)))
        val footer = footerFor(state(remaining = 1200, duration = 420L, eta = null))
        assertTrue(footer!!.contains("1.2 km"))
        assertTrue(footer.contains("7 min"))
    }

    @Test
    fun `duration formats hours and minutes`() {
        assertEquals("7 min", formatHudDuration(420L))
        assertEquals("1 h", formatHudDuration(3600L))
        assertEquals("1 h 30 min", formatHudDuration(5400L))
        assertNull(formatHudDuration(null))
        assertNull(formatHudDuration(-1L))
    }

    private fun state(
        instruction: String = "Turn left",
        nextInstruction: String? = "Continue on Main St",
        distance: Int? = 300,
        remaining: Int? = 1200,
        duration: Long? = 420L,
        eta: Long? = null
    ) = NavigationState(
        status = GuidanceStatus.ACTIVE,
        currentManeuver = ManeuverInfo(
            type = ManeuverType.LEFT,
            instruction = instruction,
            roadName = "Main St"
        ),
        nextManeuver = nextInstruction?.let {
            ManeuverInfo(
                type = ManeuverType.STRAIGHT,
                instruction = it,
                roadName = "Main St"
            )
        },
        progress = NavigationProgress(
            distanceToManeuverMeters = distance,
            remainingDistanceMeters = remaining,
            remainingDurationSeconds = duration,
            etaMillis = eta
        )
    )

    private fun roundaboutState(exit: Int) = NavigationState(
        status = GuidanceStatus.ACTIVE,
        currentManeuver = ManeuverInfo(
            type = ManeuverType.ROUNDABOUT,
            instruction = "At the roundabout, take the 2nd exit",
            roadName = "Ring Road",
            roundaboutExit = exit
        ),
        nextManeuver = ManeuverInfo(
            type = ManeuverType.ROUNDABOUT,
            instruction = "Continue on Ring Road",
            roadName = "Ring Road",
            roundaboutExit = exit
        ),
        progress = NavigationProgress(150, 2600, 300L, null)
    )
}
