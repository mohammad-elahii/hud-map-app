package com.example.hudmapapp

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.navigation.GuidanceSnapshot
import com.example.hudmapapp.navigation.NavigationSessionCoordinator
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigatorAdapter
import com.google.android.libraries.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val initLogger = RouteLogger.noop()

private fun initDestination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun initRoute() = RoutePreview(
    id = "route-1",
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 772,
    durationSeconds = 165L,
    routeToken = "token-1"
)

private class InitFakeAdapter(
    var routeStatus: Navigator.RouteStatus = Navigator.RouteStatus.OK,
    var failStart: Boolean = false
) : NavigatorAdapter {
    var destinationsSet = 0
    var guidanceStarted = 0
    var guidanceStopped = 0
    var destinationsCleared = 0
    private val arrivals = mutableListOf<Navigator.ArrivalListener>()
    private val routeChanges = mutableListOf<Navigator.RouteChangedListener>()
    private val progress = mutableListOf<Navigator.RemainingTimeOrDistanceChangedListener>()
    private val reroutes = mutableListOf<Navigator.ReroutingListener>()
    var running = false

    override fun setDestinations(
        destination: Destination,
        route: RoutePreview,
        onResult: (Navigator.RouteStatus) -> Unit
    ) {
        destinationsSet++
        onResult(routeStatus)
    }

    override fun startGuidance(): Boolean {
        guidanceStarted++
        running = !failStart
        return !failStart
    }

    override fun stopGuidance() {
        guidanceStopped++
        running = false
    }

    override fun clearDestinations() {
        destinationsCleared++
    }

    override fun addArrivalListener(listener: Navigator.ArrivalListener) {
        arrivals.add(listener)
    }

    override fun removeArrivalListener(listener: Navigator.ArrivalListener) {
        arrivals.remove(listener)
    }

    override fun addRouteChangedListener(listener: Navigator.RouteChangedListener) {
        routeChanges.add(listener)
    }

    override fun removeRouteChangedListener(listener: Navigator.RouteChangedListener) {
        routeChanges.remove(listener)
    }

    override fun addRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        progress.add(listener)
    }

    override fun removeRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        progress.remove(listener)
    }

    override fun addReroutingListener(listener: Navigator.ReroutingListener) {
        reroutes.add(listener)
    }

    override fun removeReroutingListener(listener: Navigator.ReroutingListener) {
        reroutes.remove(listener)
    }

    override fun readGuidance(): GuidanceSnapshot? = null

    override fun isGuidanceRunning(): Boolean = running

    override fun startGuidanceFeed(onUpdate: () -> Unit): Boolean = true

    override fun stopGuidanceFeed() {}

    override fun startSimulator(speedMultiplier: Float): Boolean = true

    fun listenerCount() = arrivals.size + routeChanges.size + progress.size + reroutes.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationInitTest {

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
    fun `initialization through adapter starts a real session`() = runTest(testDispatcher) {
        val fake = InitFakeAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, initLogger)

        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
        assertEquals(1, fake.destinationsSet)
        assertEquals(1, fake.guidanceStarted)
        assertEquals(4, fake.listenerCount())
    }

    @Test
    fun `failed init status reports route failed without guidance`() =
        runTest(testDispatcher) {
            val fake = InitFakeAdapter(routeStatus = Navigator.RouteStatus.NO_ROUTE_FOUND)
            val coordinator = NavigationSessionCoordinator({ fake }, initLogger)

            coordinator.startNavigation(initDestination(), initRoute())
            advanceUntilIdle()

            val state = coordinator.sessionState.value
            assertTrue(state is NavigationSessionState.Error)
            assertEquals(0, fake.guidanceStarted)
            assertEquals(0, fake.listenerCount())
        }

    @Test
    fun `duplicate start is ignored with single listener set`() = runTest(testDispatcher) {
        val fake = InitFakeAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, initLogger)

        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()
        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()

        assertEquals(1, fake.destinationsSet)
        assertEquals(4, fake.listenerCount())
    }

    @Test
    fun `stop then start creates exactly one listener set`() = runTest(testDispatcher) {
        val fake = InitFakeAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, initLogger)

        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()
        coordinator.stopNavigation()
        advanceUntilIdle()
        assertEquals(0, fake.listenerCount())
        assertEquals(1, fake.guidanceStopped)
        assertEquals(1, fake.destinationsCleared)

        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
        assertEquals(2, fake.destinationsSet)
        assertEquals(4, fake.listenerCount())
    }

    @Test
    fun `cleanup after stop removes every listener`() = runTest(testDispatcher) {
        val fake = InitFakeAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, initLogger)

        coordinator.startNavigation(initDestination(), initRoute())
        advanceUntilIdle()
        coordinator.stopNavigation()
        advanceUntilIdle()

        assertEquals(NavigationSessionState.Stopped, coordinator.sessionState.value)
        assertEquals(0, fake.listenerCount())
    }

    @Test
    fun `cancellation during starting leaves no stale active state`() =
        runTest(testDispatcher) {
            var pending: ((Navigator.RouteStatus) -> Unit)? = null
            val blocking = object : NavigatorAdapter by InitFakeAdapter() {
                override fun setDestinations(
                    destination: Destination,
                    route: RoutePreview,
                    onResult: (Navigator.RouteStatus) -> Unit
                ) {
                    pending = onResult
                }
            }
            val coordinator = NavigationSessionCoordinator({ blocking }, initLogger)

            coordinator.startNavigation(initDestination(), initRoute())
            coordinator.stopNavigation()
            pending?.invoke(Navigator.RouteStatus.OK)
            advanceUntilIdle()

            assertEquals(NavigationSessionState.Stopped, coordinator.sessionState.value)
        }
}
