package com.example.hudmapapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewError
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.ui.screens.homeScreen.RoutePreviewSheet
import com.example.hudmapapp.ui.theme.HudMapAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun sheetDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun sheetRoute(id: String, distanceMeters: Int = 772) = RoutePreview(
    id = id,
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59), RouteCoordinate(36.30, 59.60)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = distanceMeters,
    durationSeconds = 165L
)

class RoutePreviewSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeTestRule.setContent {
            HudMapAppTheme {
                RoutePreviewSheet(
                    destination = sheetDestination(),
                    routePreviewState = RoutePreviewState.Loading,
                    selectedRoute = null,
                    onRouteSelected = {},
                    onStartNavigation = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Finding routes…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose a route").assertIsDisplayed()
    }

    @Test
    fun empty_state_shows_no_routes_message() {
        composeTestRule.setContent {
            HudMapAppTheme {
                RoutePreviewSheet(
                    destination = sheetDestination(),
                    routePreviewState = RoutePreviewState.Empty,
                    selectedRoute = null,
                    onRouteSelected = {},
                    onStartNavigation = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No routes found").assertIsDisplayed()
    }

    @Test
    fun error_state_shows_driver_safe_message() {
        composeTestRule.setContent {
            HudMapAppTheme {
                RoutePreviewSheet(
                    destination = sheetDestination(),
                    routePreviewState = RoutePreviewState.Error(RoutePreviewError.Network),
                    selectedRoute = null,
                    onRouteSelected = {},
                    onStartNavigation = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Routes unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "No internet connection. Check your connection and retry."
        ).assertIsDisplayed()
    }

    @Test
    fun available_routes_can_be_selected() {
        var selected: String? = null
        val routes = listOf(sheetRoute("route-1"), sheetRoute("route-2", 900))

        composeTestRule.setContent {
            HudMapAppTheme {
                RoutePreviewSheet(
                    destination = sheetDestination(),
                    routePreviewState = RoutePreviewState.Available(routes),
                    selectedRoute = routes.first(),
                    onRouteSelected = { selected = it },
                    onStartNavigation = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Fastest route").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alternative 1").assertIsDisplayed()

        composeTestRule.onNodeWithText("Alternative 1").performClick()
        assertEquals("route-2", selected)
    }

    @Test
    fun start_navigation_confirms_selected_route() {
        var started: RoutePreview? = null
        val routes = listOf(sheetRoute("route-1"), sheetRoute("route-2", 900))

        composeTestRule.setContent {
            HudMapAppTheme {
                RoutePreviewSheet(
                    destination = sheetDestination(),
                    routePreviewState = RoutePreviewState.Available(routes),
                    selectedRoute = routes[1],
                    onRouteSelected = {},
                    onStartNavigation = { started = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Start navigation").performClick()
        assertEquals("route-2", started?.id)
    }
}
