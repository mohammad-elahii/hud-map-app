package com.example.hudmapapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.hudmapapp.ui.screens.homeScreen.NavigationSessionBanner
import com.example.hudmapapp.ui.theme.HudMapAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun bannerDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun bannerRoute() = RoutePreview(
    id = "route-1",
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59), RouteCoordinate(36.30, 59.60)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 13904,
    durationSeconds = 2038L
)

private fun liveState() = NavigationState(
    status = GuidanceStatus.ACTIVE,
    currentManeuver = ManeuverInfo(
        type = ManeuverType.LEFT,
        instruction = "Turn left onto Main St",
        roadName = "Main St"
    ),
    progress = NavigationProgress(
        distanceToManeuverMeters = 300,
        remainingDistanceMeters = 13904,
        remainingDurationSeconds = 2038L,
        etaMillis = null
    )
)

class NavigationSessionBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun active_shows_maneuver_distance_and_eta() {
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.Active(
                        bannerDestination(), bannerRoute()
                    ),
                    navigationState = liveState(),
                    onStop = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Navigating to Test Place").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Turn left onto Main St · 300 m · 13.9 km · 33 min left"
        ).assertIsDisplayed()
    }

    @Test
    fun rerouting_shows_finding_better_route() {
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.Rerouting(
                        bannerDestination(), bannerRoute()
                    ),
                    navigationState = liveState(),
                    onStop = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Finding a better route").assertIsDisplayed()
    }

    @Test
    fun offroute_shows_recalculating() {
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.OffRoute(
                        bannerDestination(), bannerRoute()
                    ),
                    navigationState = liveState(),
                    onStop = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Off route — recalculating").assertIsDisplayed()
    }

    @Test
    fun interrupted_shows_resume_and_fires_callback() {
        var resumed = false
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.Interrupted(
                        bannerDestination(), bannerRoute(),
                        InterruptionReason.LOCATION_UNAVAILABLE
                    ),
                    navigationState = liveState(),
                    onStop = {},
                    onResume = { resumed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Location lost. Waiting for GPS signal.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume").performClick()
        assertEquals(true, resumed)
    }

    @Test
    fun error_shows_retry_and_fires_callback() {
        var retried = false
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.Error(
                        NavigationSessionError.NetworkError
                    ),
                    onStop = {},
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithText("No connection. Navigation needs internet to start.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun stop_fires_callback() {
        var stopped = false
        composeTestRule.setContent {
            HudMapAppTheme {
                NavigationSessionBanner(
                    sessionState = NavigationSessionState.Active(
                        bannerDestination(), bannerRoute()
                    ),
                    navigationState = liveState(),
                    onStop = { stopped = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Stop navigation").performClick()
        assertEquals(true, stopped)
    }
}
