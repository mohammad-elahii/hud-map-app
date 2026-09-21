package com.example.hudmapapp

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.navigation.GuidanceStatus
import com.example.hudmapapp.navigation.InterruptionReason
import com.example.hudmapapp.navigation.ManeuverInfo
import com.example.hudmapapp.navigation.ManeuverType
import com.example.hudmapapp.navigation.NavigationProgress
import com.example.hudmapapp.navigation.NavigationSessionError
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.ui.screens.hudScreen.HudContent
import com.example.hudmapapp.ui.screens.hudScreen.hudContentFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun contentDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun contentRoute() = RoutePreview(
    id = "route-1",
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59), RouteCoordinate(36.30, 59.60)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 1200,
    durationSeconds = 420L
)

private fun liveNav() = NavigationState(
    status = GuidanceStatus.ACTIVE,
    currentManeuver = ManeuverInfo(
        type = ManeuverType.LEFT,
        instruction = "Turn left onto Main St",
        roadName = "Main St"
    ),
    progress = NavigationProgress(300, 1200, 420L, null)
)

class HudSessionContentTest {

    @Test
    fun `idle and stopped map to idle fallback`() {
        assertEquals(
            HudContent.Idle,
            hudContentFor(NavigationSessionState.Idle, liveNav())
        )
        assertEquals(
            HudContent.Idle,
            hudContentFor(NavigationSessionState.Stopped, liveNav())
        )
    }

    @Test
    fun `starting maps to starting variant`() {
        assertEquals(
            HudContent.Starting,
            hudContentFor(NavigationSessionState.Starting, NavigationState())
        )
    }

    @Test
    fun `active maps to live guidance without status line`() {
        val content = hudContentFor(
            NavigationSessionState.Active(contentDestination(), contentRoute()),
            liveNav()
        )
        assertEquals(HudContent.Guidance(frozen = false, statusLine = null), content)
    }

    @Test
    fun `rerouting and offroute map to frozen guidance with status`() {
        val rerouting = hudContentFor(
            NavigationSessionState.Rerouting(contentDestination(), contentRoute()),
            liveNav()
        ) as HudContent.Guidance
        assertTrue(rerouting.frozen)
        assertEquals("Finding a better route.", rerouting.statusLine)

        val offRoute = hudContentFor(
            NavigationSessionState.OffRoute(contentDestination(), contentRoute()),
            liveNav()
        ) as HudContent.Guidance
        assertTrue(offRoute.frozen)
        assertEquals("Off route. Finding a new route.", offRoute.statusLine)
    }

    @Test
    fun `stopping maps to frozen guidance without status`() {
        val content = hudContentFor(
            NavigationSessionState.Stopping,
            liveNav()
        )
        assertEquals(HudContent.Guidance(frozen = true, statusLine = null), content)
    }

    @Test
    fun `interrupted maps to message with resume`() {
        val content = hudContentFor(
            NavigationSessionState.Interrupted(
                contentDestination(), contentRoute(),
                InterruptionReason.LOCATION_UNAVAILABLE
            ),
            liveNav()
        ) as HudContent.Interrupted
        assertEquals("Location lost. Waiting for GPS signal.", content.message)
        assertTrue(content.canResume)
    }

    @Test
    fun `arrived maps to terminal variant`() {
        assertEquals(
            HudContent.Arrived,
            hudContentFor(NavigationSessionState.Arrived, liveNav())
        )
    }

    @Test
    fun `retryable error maps to message with retry`() {
        val content = hudContentFor(
            NavigationSessionState.Error(NavigationSessionError.NetworkError),
            NavigationState()
        ) as HudContent.Error
        assertEquals("No connection. Navigation needs internet to start.", content.message)
        assertTrue(content.canRetry)
    }

    @Test
    fun `non-retryable error maps to message without retry`() {
        val content = hudContentFor(
            NavigationSessionState.Error(NavigationSessionError.GuidanceFailed),
            NavigationState()
        ) as HudContent.Error
        assertFalse(content.canRetry)
    }

    @Test
    fun `null guidance values never break mapping`() {
        val empty = NavigationState()
        assertEquals(
            HudContent.Guidance(frozen = false, statusLine = null),
            hudContentFor(
                NavigationSessionState.Active(contentDestination(), contentRoute()),
                empty
            )
        )
        val interrupted = hudContentFor(
            NavigationSessionState.Interrupted(
                contentDestination(), contentRoute(),
                InterruptionReason.GUIDANCE_PAUSED
            ),
            empty
        ) as HudContent.Interrupted
        assertEquals("Navigation paused. Waiting to resume.", interrupted.message)
        assertTrue(interrupted.canResume)
    }
}
