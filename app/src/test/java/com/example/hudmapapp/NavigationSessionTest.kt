package com.example.hudmapapp

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.navigation.NavigationSessionCoordinator
import com.example.hudmapapp.navigation.NavigationSessionError
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigatorAdapter
import com.google.android.libraries.navigation.ArrivalEvent
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

private val noopLogger = RouteLogger.noop()

private fun destination() = Destination(
    placeId = "place-1",
    name = "Test Place",
    address = "Test Address",
    latitude = 36.30,
    longitude = 59.60
)

private fun route() = RoutePreview(
    id = "route-1",
    polylinePoints = listOf(RouteCoordinate(36.29, 59.59)),
    encodedPolyline = "_p~iF~ps|U",
    distanceMeters = 772,
    durationSeconds = 165L,
    routeToken = "token-1"
)

private class FakeNavigatorAdapter(
    var routeStatus: Navigator.RouteStatus = Navigator.RouteStatus.OK,
    var guidanceStarts: Boolean = true
) : NavigatorAdapter {
    var setDestinationsCalls = 0
    var startGuidanceCalls = 0
    var stopGuidanceCalls = 0
    var clearDestinationsCalls = 0
    var guidanceRunning = false
    private val arrivalListeners = mutableListOf<Navigator.ArrivalListener>()
    private val routeChangedListeners = mutableListOf<Navigator.RouteChangedListener>()

    override fun setDestinations(
        destination: Destination,
        route: RoutePreview,
        onResult: (Navigator.RouteStatus) -> Unit
    ) {
        setDestinationsCalls++
        onResult(routeStatus)
    }

    override fun startGuidance(): Boolean {
        startGuidanceCalls++
        guidanceRunning = guidanceStarts
        return guidanceStarts
    }

    override fun stopGuidance() {
        stopGuidanceCalls++
        guidanceRunning = false
    }

    override fun clearDestinations() {
        clearDestinationsCalls++
    }

    override fun addArrivalListener(listener: Navigator.ArrivalListener) {
        arrivalListeners.add(listener)
    }

    override fun removeArrivalListener(listener: Navigator.ArrivalListener) {
        arrivalListeners.remove(listener)
    }

    override fun addRouteChangedListener(listener: Navigator.RouteChangedListener) {
        routeChangedListeners.add(listener)
    }

    override fun removeRouteChangedListener(listener: Navigator.RouteChangedListener) {
        routeChangedListeners.remove(listener)
    }

    override fun addRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
    }

    override fun removeRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
    }

    override fun addReroutingListener(listener: Navigator.ReroutingListener) {
    }

    override fun removeReroutingListener(listener: Navigator.ReroutingListener) {
    }

    override fun readGuidance(): com.example.hudmapapp.navigation.GuidanceSnapshot? = null

    override fun isGuidanceRunning(): Boolean = guidanceRunning

    fun arrivalListenerCount() = arrivalListeners.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionTest {

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
    fun `selected route starts guidance and becomes active`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        val state = coordinator.sessionState.value
        assertTrue(state is NavigationSessionState.Active)
        assertEquals(1, fake.setDestinationsCalls)
        assertEquals(1, fake.startGuidanceCalls)
    }

    @Test
    fun `starting twice does not create duplicate sessions`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()
        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        assertEquals(1, fake.setDestinationsCalls)
        assertEquals(1, fake.startGuidanceCalls)
    }

    @Test
    fun `stop removes listeners and releases resources`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()
        assertEquals(1, fake.arrivalListenerCount())

        coordinator.stopNavigation()
        advanceUntilIdle()

        assertEquals(NavigationSessionState.Stopped, coordinator.sessionState.value)
        assertEquals(0, fake.arrivalListenerCount())
        assertEquals(1, fake.stopGuidanceCalls)
        assertEquals(1, fake.clearDestinationsCalls)
    }

    @Test
    fun `stale results cannot overwrite newer session`() = runTest(testDispatcher) {
        var pendingCallback: ((Navigator.RouteStatus) -> Unit)? = null
        val blocking = object : NavigatorAdapter by FakeNavigatorAdapter() {
            override fun setDestinations(
                destination: Destination,
                route: RoutePreview,
                onResult: (Navigator.RouteStatus) -> Unit
            ) {
                pendingCallback = onResult
            }
        }
        val coordinator = NavigationSessionCoordinator({ blocking }, noopLogger)

        coordinator.startNavigation(destination(), route())
        coordinator.stopNavigation()
        pendingCallback?.invoke(Navigator.RouteStatus.OK)
        advanceUntilIdle()

        assertEquals(NavigationSessionState.Stopped, coordinator.sessionState.value)
    }

    @Test
    fun `missing navigator surfaces typed error`() = runTest(testDispatcher) {
        val coordinator = NavigationSessionCoordinator({ null }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        assertEquals(
            NavigationSessionState.Error(NavigationSessionError.NavigatorNotReady),
            coordinator.sessionState.value
        )
    }

    @Test
    fun `route failure surfaces typed error`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter(routeStatus = Navigator.RouteStatus.NO_ROUTE_FOUND)
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        val state = coordinator.sessionState.value
        assertTrue(state is NavigationSessionState.Error)
        assertTrue(
            (state as NavigationSessionState.Error).error is NavigationSessionError.RouteFailed
        )
    }

    @Test
    fun `invalid destination is rejected without sdk call`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination().copy(latitude = 0.0, longitude = 0.0), route())
        advanceUntilIdle()

        assertEquals(
            NavigationSessionState.Error(NavigationSessionError.InvalidRoute),
            coordinator.sessionState.value
        )
        assertEquals(0, fake.setDestinationsCalls)
    }

    @Test
    fun `guidance failure surfaces typed error`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter(guidanceStarts = false)
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        val state = coordinator.sessionState.value
        assertTrue(state is NavigationSessionState.Error)
        assertEquals(
            NavigationSessionError.GuidanceFailed,
            (state as NavigationSessionState.Error).error
        )
    }

    @Test
    fun `arrival moves session to arrived`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
    }
}
