package com.example.hudmapapp

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.data.model.SelectedDestinationState
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.data.repository.RoutePlanningDataSource
import com.example.hudmapapp.data.repository.RoutePlanningRepository
import com.example.hudmapapp.data.repository.RoutePlanningResult
import com.example.hudmapapp.navigation.RoutePreviewCoordinator
import com.example.hudmapapp.ui.screens.homeScreen.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val viewModelLogger = RouteLogger.noop()

private fun viewModelDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun viewModelRoutes() = listOf(
    RoutePreview(
        id = "route-1",
        polylinePoints = listOf(RouteCoordinate(36.29, 59.59)),
        encodedPolyline = "_p~iF~ps|U",
        distanceMeters = 772,
        durationSeconds = 165L
    ),
    RoutePreview(
        id = "route-2",
        polylinePoints = listOf(RouteCoordinate(36.29, 59.59)),
        encodedPolyline = "_p~iF~ps|U",
        distanceMeters = 900,
        durationSeconds = 200L
    )
)

private fun previewViewModel(
    result: RoutePlanningResult = RoutePlanningResult.Success(viewModelRoutes())
) = HomeViewModel(
    RoutePreviewCoordinator(
        RoutePlanningRepository(RoutePlanningDataSource { result }),
        viewModelLogger
    ),
    viewModelLogger
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirming destination transitions selection state`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.selectDestination(viewModelDestination())
        assertTrue(viewModel.selectedDestination.value is SelectedDestinationState.Selected)

        viewModel.confirmDestination()
        val confirmed = viewModel.selectedDestination.value
        assertTrue(confirmed is SelectedDestinationState.Confirmed)
        assertEquals(
            viewModelDestination().placeId,
            (confirmed as SelectedDestinationState.Confirmed).destination.placeId
        )
    }

    @Test
    fun `confirm then origin produces available route previews`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.selectDestination(viewModelDestination())
        viewModel.confirmDestination()
        viewModel.requestRoutePreview(36.29, 59.59)
        advanceUntilIdle()

        val state = viewModel.routePreviewState.value
        assertTrue(state is RoutePreviewState.Available)
        assertEquals(2, (state as RoutePreviewState.Available).routes.size)
        assertEquals("route-1", viewModel.selectedRoute.value?.id)
    }

    @Test
    fun `preview request before confirm is ignored`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.requestRoutePreview(36.29, 59.59)
        advanceUntilIdle()

        assertEquals(RoutePreviewState.Idle, viewModel.routePreviewState.value)
        assertNull(viewModel.selectedRoute.value)
    }

    @Test
    fun `selecting a route replaces the selected route`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.selectDestination(viewModelDestination())
        viewModel.confirmDestination()
        viewModel.requestRoutePreview(36.29, 59.59)
        advanceUntilIdle()

        viewModel.selectRoute("route-2")
        advanceUntilIdle()

        assertEquals("route-2", viewModel.selectedRoute.value?.id)
        val state = viewModel.routePreviewState.value
        assertTrue(state is RoutePreviewState.Available)
        assertEquals(2, (state as RoutePreviewState.Available).routes.size)
    }

    @Test
    fun `clearing destination resets previews to idle`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.selectDestination(viewModelDestination())
        viewModel.confirmDestination()
        viewModel.requestRoutePreview(36.29, 59.59)
        advanceUntilIdle()
        assertTrue(viewModel.routePreviewState.value is RoutePreviewState.Available)

        viewModel.clearDestination()
        advanceUntilIdle()

        assertEquals(SelectedDestinationState.None, viewModel.selectedDestination.value)
        assertEquals(RoutePreviewState.Idle, viewModel.routePreviewState.value)
        assertNull(viewModel.selectedRoute.value)
    }

    @Test
    fun `changing destination clears previous confirmation`() = runTest(testDispatcher) {
        val viewModel = previewViewModel()

        viewModel.selectDestination(viewModelDestination())
        viewModel.confirmDestination()
        viewModel.changeDestination(viewModelDestination().copy(placeId = "place-2"))

        val state = viewModel.selectedDestination.value
        assertTrue(state is SelectedDestinationState.Selected)
        assertEquals("place-2", (state as SelectedDestinationState.Selected).destination.placeId)
    }
}
