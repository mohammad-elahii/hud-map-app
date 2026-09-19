package com.example.hudmapapp

import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewError
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.data.repository.RoutePlanningDataSource
import com.example.hudmapapp.data.repository.RoutePlanningRepository
import com.example.hudmapapp.data.repository.RoutePlanningResult
import com.example.hudmapapp.data.repository.RoutesApiDataSource
import com.example.hudmapapp.navigation.RoutePreviewCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val noopLogger = RouteLogger.noop()

private fun preview(id: String) = RoutePreview(
    id = id,
    polylinePoints = listOf(RouteCoordinate(36.3, 59.6)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 772,
    durationSeconds = 165L
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoutePreviewTest {

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
    fun `valid origin and destination produce candidate routes`() = runTest(testDispatcher) {
        val repository = RoutePlanningRepository(
            RoutePlanningDataSource { RoutePlanningResult.Success(listOf(preview("r1"), preview("r2"))) }
        )
        val coordinator = RoutePreviewCoordinator(repository, noopLogger)

        coordinator.requestRoutePreview(36.29, 59.59, 36.30, 59.60)
        advanceUntilIdle()

        val state = coordinator.routePreviewState.value
        assertTrue(state is RoutePreviewState.Available)
        assertEquals(2, (state as RoutePreviewState.Available).routes.size)
        assertEquals("r1", coordinator.selectedRoute.value?.id)
    }

    @Test
    fun `empty results are represented explicitly`() = runTest(testDispatcher) {
        val repository = RoutePlanningRepository(
            RoutePlanningDataSource { RoutePlanningResult.Empty }
        )
        val coordinator = RoutePreviewCoordinator(repository, noopLogger)

        coordinator.requestRoutePreview(36.29, 59.59, 36.30, 59.60)
        advanceUntilIdle()

        assertEquals(RoutePreviewState.Empty, coordinator.routePreviewState.value)
    }

    @Test
    fun `failures map to stable error categories`() = runTest(testDispatcher) {
        val errors = listOf(
            RoutePreviewError.Network,
            RoutePreviewError.Timeout,
            RoutePreviewError.Authentication,
            RoutePreviewError.QuotaExceeded,
            RoutePreviewError.InvalidRequest,
            RoutePreviewError.Unknown
        )
        for (error in errors) {
            val repository = RoutePlanningRepository(
                RoutePlanningDataSource { RoutePlanningResult.Failure(error) }
            )
            val coordinator = RoutePreviewCoordinator(repository, noopLogger)
            coordinator.requestRoutePreview(36.29, 59.59, 36.30, 59.60)
            advanceUntilIdle()
            assertEquals(RoutePreviewState.Error(error), coordinator.routePreviewState.value)
        }
    }

    @Test
    fun `invalid coordinates fail without calling provider`() = runTest(testDispatcher) {
        var providerCalls = 0
        val repository = RoutePlanningRepository(
            RoutePlanningDataSource {
                providerCalls++
                RoutePlanningResult.Empty
            }
        )

        val invalidInputs = listOf(
            arrayOf(null, 59.59, 36.30, 59.60),
            arrayOf(36.29, 59.59, 0.0, 0.0),
            arrayOf(91.0, 59.59, 36.30, 59.60),
            arrayOf(36.29, 200.0, 36.30, 59.60)
        )
        for (input in invalidInputs) {
            val result = repository.requestRoutes(
                input[0] as Double?, input[1] as Double?,
                input[2] as Double?, input[3] as Double?
            )
            assertEquals(
                RoutePlanningResult.Failure(RoutePreviewError.InvalidRequest),
                result
            )
        }
        assertEquals(0, providerCalls)
    }

    @Test
    fun `stale responses cannot overwrite newer state`() = runTest(testDispatcher) {
        val repository = RoutePlanningRepository(
            RoutePlanningDataSource { request ->
                if (request.destination.latitude > 40.0) {
                    delay(1_000)
                    RoutePlanningResult.Success(listOf(preview("stale")))
                } else {
                    RoutePlanningResult.Success(listOf(preview("fresh")))
                }
            }
        )
        val coordinator = RoutePreviewCoordinator(repository, noopLogger)

        coordinator.requestRoutePreview(36.29, 59.59, 41.0, 59.60)
        advanceTimeBy(100)
        coordinator.requestRoutePreview(36.29, 59.59, 36.30, 59.60)
        advanceUntilIdle()

        val state = coordinator.routePreviewState.value
        assertTrue(state is RoutePreviewState.Available)
        assertEquals("fresh", (state as RoutePreviewState.Available).routes.first().id)
    }

    @Test
    fun `routes api response maps to app models`() {
        val dataSource = RoutesApiDataSource("test-key")
        val response = "{" +
            "\"routes\":[{" +
            "\"distanceMeters\":772," +
            "\"duration\":\"165s\"," +
            "\"staticDuration\":\"150s\"," +
            "\"polyline\":{\"encodedPolyline\":\"_p~iF~ps|U_ulLnnqC\"}," +
            "\"routeToken\":\"token-1\"," +
            "\"routeLabels\":[\"DEFAULT_ROUTE\"]" +
            "}]" +
            "}"

        val result = dataSource.parseRoutes(response)

        assertTrue(result is RoutePlanningResult.Success)
        val route = (result as RoutePlanningResult.Success).routes.first()
        assertEquals(772, route.distanceMeters)
        assertEquals(165L, route.durationSeconds)
        assertEquals("token-1", route.routeToken)
        assertEquals("DEFAULT_ROUTE", route.id)
        assertTrue(route.polylinePoints.isNotEmpty())
    }

    @Test
    fun `polyline decoding produces expected coordinates`() {
        val points = RoutesApiDataSource.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.0001)
        assertEquals(-120.2, points[0].longitude, 0.0001)
    }
}
