package com.example.hudmapapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
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
import com.example.hudmapapp.ui.screens.hudScreen.HUDScreen
import com.example.hudmapapp.ui.screens.hudScreen.MirroredHUDScreen
import com.example.hudmapapp.ui.theme.HudMapAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun hudDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun hudRoute() = RoutePreview(
    id = "route-1",
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59), RouteCoordinate(36.30, 59.60)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 1200,
    durationSeconds = 420L
)

private fun hudLiveState() = NavigationState(
    status = GuidanceStatus.ACTIVE,
    currentManeuver = ManeuverInfo(
        type = ManeuverType.LEFT,
        instruction = "Turn left onto Main St",
        roadName = "Main St"
    ),
    nextManeuver = ManeuverInfo(
        type = ManeuverType.STRAIGHT,
        instruction = "Continue on Main St",
        roadName = "Main St"
    ),
    progress = NavigationProgress(
        distanceToManeuverMeters = 300,
        remainingDistanceMeters = 1200,
        remainingDurationSeconds = 420L,
        etaMillis = null
    )
)

class HudScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun active_renders_instruction_distance_and_footer() {
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Active(
                        hudDestination(), hudRoute()
                    ),
                    navigationState = hudLiveState()
                )
            }
        }

        composeTestRule.onNodeWithText("Turn left onto Main St").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Then Continue on Main St").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.2 km · 7 min").assertIsDisplayed()
    }

    @Test
    fun rerouting_shows_status_with_frozen_guidance() {
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Rerouting(
                        hudDestination(), hudRoute()
                    ),
                    navigationState = hudLiveState()
                )
            }
        }

        composeTestRule.onNodeWithText("Finding a better route.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Turn left onto Main St").assertIsDisplayed()
    }

    @Test
    fun interrupted_shows_message_and_resume_fires_callback() {
        var resumed = false
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Interrupted(
                        hudDestination(), hudRoute(),
                        InterruptionReason.LOCATION_UNAVAILABLE
                    ),
                    navigationState = hudLiveState(),
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
    fun error_shows_message_and_retry_fires_callback() {
        var retried = false
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Error(
                        NavigationSessionError.NetworkError
                    ),
                    onRetry = { retried = true },
                    onStop = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No connection. Navigation needs internet to start.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun arrived_shows_terminal_state() {
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Arrived,
                    navigationState = hudLiveState()
                )
            }
        }

        composeTestRule.onNodeWithText("You have arrived.").assertIsDisplayed()
    }

    @Test
    fun idle_shows_fallback_with_back_to_map() {
        composeTestRule.setContent {
            HudMapAppTheme {
                HUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Idle
                )
            }
        }

        composeTestRule.onNodeWithText("No active navigation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back to map").assertIsDisplayed()
    }

    @Test
    fun mirrored_renders_same_guidance_state() {
        composeTestRule.setContent {
            HudMapAppTheme {
                MirroredHUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Active(
                        hudDestination(), hudRoute()
                    ),
                    navigationState = hudLiveState()
                )
            }
        }

        composeTestRule.onNodeWithText("Turn left onto Main St").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Then Continue on Main St").assertIsDisplayed()
    }

    @Test
    fun mirrored_interrupted_offers_resume() {
        var resumed = false
        composeTestRule.setContent {
            HudMapAppTheme {
                MirroredHUDScreen(
                    navController = rememberNavController(),
                    sessionState = NavigationSessionState.Interrupted(
                        hudDestination(), hudRoute(),
                        InterruptionReason.NETWORK_ERROR
                    ),
                    navigationState = hudLiveState(),
                    onResume = { resumed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Connection lost. Reconnecting.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume").performClick()
        assertEquals(true, resumed)
    }
}
