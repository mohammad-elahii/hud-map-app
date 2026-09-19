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
    private val progressListeners =
        mutableListOf<Navigator.RemainingTimeOrDistanceChangedListener>()
    private val reroutingListeners = mutableListOf<Navigator.ReroutingListener>()

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
        progressListeners.add(listener)
    }

    override fun removeRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        progressListeners.remove(listener)
    }

    override fun addReroutingListener(listener: Navigator.ReroutingListener) {
        reroutingListeners.add(listener)
    }

    override fun removeReroutingListener(listener: Navigator.ReroutingListener) {
        reroutingListeners.remove(listener)
    }

    override fun readGuidance(): com.example.hudmapapp.navigation.GuidanceSnapshot? = null

    override fun isGuidanceRunning(): Boolean = guidanceRunning

    var feedStartCalls = 0
    var feedStopCalls = 0

    override fun startGuidanceFeed(onUpdate: () -> Unit): Boolean {
        feedStartCalls++
        return true
    }

    override fun stopGuidanceFeed() {
        feedStopCalls++
    }

    override fun startSimulator(speedMultiplier: Float): Boolean = true

    fun arrivalListenerCount() = arrivalListeners.size

    fun progressListenerCount() = progressListeners.size

    fun reroutingListenerCount() = reroutingListeners.size

    fun fireRerouting() {
        reroutingListeners.toList().forEach { it.onReroutingRequestedByOffRoute() }
    }

    fun fireRouteChanged() {
        routeChangedListeners.toList().forEach { it.onRouteChanged() }
    }

    fun fireProgress() {
        progressListeners.toList().forEach { it.onRemainingTimeOrDistanceChanged() }
    }
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

    @Test
    fun `off-route then route change recovers to active`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        fake.fireRerouting()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.OffRoute)
        assertEquals(
            com.example.hudmapapp.navigation.GuidanceStatus.OFF_ROUTE,
            coordinator.navigationState.value.status
        )

        fake.fireRouteChanged()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
        assertEquals(
            com.example.hudmapapp.navigation.GuidanceStatus.ACTIVE,
            coordinator.navigationState.value.status
        )
    }

    @Test
    fun `network failure during start maps to network error`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter(routeStatus = Navigator.RouteStatus.NETWORK_ERROR)
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        assertEquals(
            NavigationSessionState.Error(NavigationSessionError.NetworkError),
            coordinator.sessionState.value
        )
    }

    @Test
    fun `location interruption recovers with resume`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        coordinator.notifyLocationUnavailable()
        advanceUntilIdle()
        val interrupted = coordinator.sessionState.value
        assertTrue(
            interrupted is NavigationSessionState.Interrupted &&
                interrupted.reason ==
                com.example.hudmapapp.navigation.InterruptionReason.LOCATION_UNAVAILABLE
        )
        assertEquals(
            com.example.hudmapapp.navigation.GuidanceStatus.INTERRUPTED,
            coordinator.navigationState.value.status
        )

        coordinator.resume()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
    }

    @Test
    fun `network interruption recovers with resume`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        coordinator.notifyNetworkLost()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Interrupted)

        coordinator.resume()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
        assertEquals(1, fake.setDestinationsCalls)
    }

    @Test
    fun `retry after error restarts without duplicate listeners`() =
        runTest(testDispatcher) {
            val fake = FakeNavigatorAdapter(
                routeStatus = Navigator.RouteStatus.NETWORK_ERROR
            )
            val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

            coordinator.startNavigation(destination(), route())
            advanceUntilIdle()
            assertTrue(coordinator.sessionState.value is NavigationSessionState.Error)

            fake.routeStatus = Navigator.RouteStatus.OK
            coordinator.retryStart()
            advanceUntilIdle()

            assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
            assertEquals(2, fake.setDestinationsCalls)
            assertEquals(1, fake.arrivalListenerCount())
            assertEquals(1, fake.progressListenerCount())
            assertEquals(1, fake.reroutingListenerCount())
        }

    @Test
    fun `arrived state blocks further guidance updates`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()
        coordinator.stopNavigation()
        advanceUntilIdle()

        fake.fireRerouting()
        fake.fireRouteChanged()
        fake.fireProgress()
        advanceUntilIdle()

        assertEquals(NavigationSessionState.Stopped, coordinator.sessionState.value)
        assertEquals(
            com.example.hudmapapp.navigation.GuidanceStatus.STOPPED,
            coordinator.navigationState.value.status
        )
    }

    @Test
    fun `user messages never expose raw sdk internals`() = runTest(testDispatcher) {
        val states = listOf(
            NavigationSessionState.Error(NavigationSessionError.NavigatorNotReady),
            NavigationSessionState.Error(
                NavigationSessionError.RouteFailed("SOME_RAW_STATUS")
            ),
            NavigationSessionState.Error(NavigationSessionError.NetworkError),
            NavigationSessionState.Error(NavigationSessionError.LocationUnavailable),
            NavigationSessionState.Error(NavigationSessionError.Unknown),
            NavigationSessionState.OffRoute(destination(), route()),
            NavigationSessionState.Rerouting(destination(), route()),
            NavigationSessionState.Interrupted(
                destination(), route(),
                com.example.hudmapapp.navigation.InterruptionReason.LOCATION_UNAVAILABLE
            ),
            NavigationSessionState.Arrived
        )
        for (state in states) {
            val message = com.example.hudmapapp.navigation.userMessageFor(state)
            assertTrue(!message.isNullOrBlank())
            assertTrue(!message!!.contains("SOME_RAW_STATUS"))
            assertTrue(!message.contains("Navigator"))
        }
    }

    @Test
    fun `background then quick foreground keeps session active`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        coordinator.onAppBackgrounded()
        advanceTimeBy(5_000)
        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
    }

    @Test
    fun `background past grace period pauses with resume`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        coordinator.onAppBackgrounded()
        advanceTimeBy(31_000)
        advanceUntilIdle()

        val interrupted = coordinator.sessionState.value
        assertTrue(
            interrupted is NavigationSessionState.Interrupted &&
                interrupted.reason ==
                com.example.hudmapapp.navigation.InterruptionReason.GUIDANCE_PAUSED
        )

        coordinator.onAppForegrounded()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value is NavigationSessionState.Active)
        assertEquals(1, fake.setDestinationsCalls)
    }

    @Test
    fun `background with no session does nothing`() = runTest(testDispatcher) {
        val fake = FakeNavigatorAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.onAppBackgrounded()
        advanceTimeBy(31_000)
        advanceUntilIdle()

        assertEquals(NavigationSessionState.Idle, coordinator.sessionState.value)
    }

    @Test
    fun `live banner formatters produce driver-safe lines`() {
        assertEquals(
            "300 m",
            com.example.hudmapapp.ui.screens.homeScreen.formatLiveDistance(300)
        )
        assertEquals(
            "1.2 km",
            com.example.hudmapapp.ui.screens.homeScreen.formatLiveDistance(1200)
        )
        assertEquals(
            "13.9 km · 33 min left",
            com.example.hudmapapp.ui.screens.homeScreen.liveEtaLine(13904, 2038L)
        )
        assertEquals(
            null,
            com.example.hudmapapp.ui.screens.homeScreen.liveEtaLine(null, null)
        )
    }
}
