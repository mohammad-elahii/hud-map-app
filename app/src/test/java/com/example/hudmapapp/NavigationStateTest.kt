package com.example.hudmapapp

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.navigation.GuidanceSnapshot
import com.example.hudmapapp.navigation.GuidanceStatus
import com.example.hudmapapp.navigation.ManeuverType
import com.example.hudmapapp.navigation.NavigationDataError
import com.example.hudmapapp.navigation.NavigationSessionCoordinator
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.navigation.NavigatorAdapter
import com.example.hudmapapp.navigation.StepSnapshot
import com.example.hudmapapp.navigation.applyGuidance
import com.example.hudmapapp.navigation.fakeGuidance
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

private class GuidanceFakeAdapter(
    var snapshot: GuidanceSnapshot? = fakeGuidance()
) : NavigatorAdapter {
    var remainingListenerCount = 0
    var reroutingListenerCount = 0
    val arrivalListeners = mutableListOf<Navigator.ArrivalListener>()
    val routeChangedListeners = mutableListOf<Navigator.RouteChangedListener>()
    var progressListeners = 0

    override fun setDestinations(
        destination: Destination,
        route: RoutePreview,
        onResult: (Navigator.RouteStatus) -> Unit
    ) {
        onResult(Navigator.RouteStatus.OK)
    }

    override fun startGuidance(): Boolean = true

    override fun stopGuidance() {}

    override fun clearDestinations() {}

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
        progressListeners++
    }

    override fun removeRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        progressListeners = maxOf(0, progressListeners - 1)
    }

    override fun addReroutingListener(listener: Navigator.ReroutingListener) {
        reroutingListenerCount++
    }

    override fun removeReroutingListener(listener: Navigator.ReroutingListener) {
        reroutingListenerCount = maxOf(0, reroutingListenerCount - 1)
    }

    override fun readGuidance(): GuidanceSnapshot? = snapshot

    override fun isGuidanceRunning(): Boolean = true

    var feedTicks = 0
    var feedRunning = false
    var lastFeedCallback: (() -> Unit)? = null
    private var script: List<GuidanceSnapshot?> = emptyList()
    private var scriptIndex = 0

    fun scriptSnapshots(vararg snapshots: GuidanceSnapshot?) {
        script = snapshots.toList()
        scriptIndex = 0
    }

    override fun startGuidanceFeed(onUpdate: () -> Unit): Boolean {
        feedRunning = true
        lastFeedCallback = onUpdate
        return true
    }

    override fun stopGuidanceFeed() {
        feedRunning = false
        lastFeedCallback = null
    }

    override fun startSimulator(speedMultiplier: Float): Boolean = true

    fun fireFeedTick() {
        if (!feedRunning) return
        feedTicks++
        if (scriptIndex < script.size) {
            snapshot = script[scriptIndex]
            scriptIndex++
        }
        lastFeedCallback?.invoke()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationStateTest {

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
    fun `normal progress maps to HUD state`() = runTest(testDispatcher) {
        val fake = GuidanceFakeAdapter(fakeGuidance())
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        val state = coordinator.navigationState.value
        assertEquals(GuidanceStatus.ACTIVE, state.status)
        assertNotNull(state.currentManeuver)
        assertEquals("Turn left onto Main St", state.currentManeuver?.instruction)
        assertEquals("Main St", state.currentManeuver?.roadName)
        assertEquals(300, state.progress.distanceToManeuverMeters)
        assertEquals(1200, state.progress.remainingDistanceMeters)
        assertEquals(420L, state.progress.remainingDurationSeconds)
        assertNotNull(state.progress.etaMillis)
        assertNull(state.error)
    }

    @Test
    fun `maneuver change updates current and next`() {
        val first = applyGuidance(
            NavigationState(),
            fakeGuidance(instruction = "Turn left", nextInstruction = "Turn right")
        )
        assertEquals("Turn left", first.currentManeuver?.instruction)
        assertEquals("Turn right", first.nextManeuver?.instruction)

        val second = applyGuidance(
            first,
            fakeGuidance(instruction = "Turn right", nextInstruction = "Arrive")
        )
        assertEquals("Turn right", second.currentManeuver?.instruction)
        assertEquals("Arrive", second.nextManeuver?.instruction)
    }

    @Test
    fun `missing guidance values have defined fallbacks`() {
        val state = applyGuidance(
            NavigationState(),
            GuidanceSnapshot(
                currentStep = null,
                nextStep = null,
                distanceToManeuverMeters = null,
                timeToManeuverSeconds = null,
                remainingDistanceMeters = null,
                remainingDurationSeconds = null
            )
        )

        assertNull(state.currentManeuver)
        assertNull(state.nextManeuver)
        assertNull(state.progress.distanceToManeuverMeters)
        assertNull(state.progress.remainingDistanceMeters)
        assertNull(state.progress.etaMillis)
        assertNull(state.error)
    }

    @Test
    fun `blank instruction falls back to continue`() {
        val state = applyGuidance(
            NavigationState(),
            fakeGuidance(instruction = "  ", roadName = "")
        )

        assertEquals("Continue", state.currentManeuver?.instruction)
    }

    @Test
    fun `null snapshot surfaces guidance unavailable`() {
        val state = applyGuidance(NavigationState(status = GuidanceStatus.ACTIVE), null)

        assertEquals(GuidanceStatus.ACTIVE, state.status)
        assertEquals(NavigationDataError.GuidanceUnavailable, state.error)
    }

    @Test
    fun `rerouting and progress listeners registered and removed`() =
        runTest(testDispatcher) {
            val fake = GuidanceFakeAdapter()
            val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

            coordinator.startNavigation(destination(), route())
            advanceUntilIdle()
            assertEquals(1, fake.progressListeners)
            assertEquals(1, fake.reroutingListenerCount)

            coordinator.stopNavigation()
            advanceUntilIdle()
            assertEquals(0, fake.progressListeners)
            assertEquals(0, fake.reroutingListenerCount)
            assertEquals(GuidanceStatus.STOPPED, coordinator.navigationState.value.status)
        }

    @Test
    fun `feed ticks advance maneuver progression without stale data`() =
        runTest(testDispatcher) {
            val fake = GuidanceFakeAdapter(fakeGuidance(instruction = "Turn left"))
            val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

            coordinator.startNavigation(destination(), route())
            advanceUntilIdle()
            assertTrue(fake.feedRunning)
            assertEquals("Turn left", coordinator.navigationState.value.currentManeuver?.instruction)

            fake.scriptSnapshots(
                fakeGuidance(instruction = "Turn right", distanceToManeuverMeters = 150),
                fakeGuidance(instruction = "Arrive", distanceToManeuverMeters = 20)
            )
            fake.fireFeedTick()
            advanceUntilIdle()
            assertEquals("Turn right", coordinator.navigationState.value.currentManeuver?.instruction)
            assertEquals(150, coordinator.navigationState.value.progress.distanceToManeuverMeters)

            fake.fireFeedTick()
            advanceUntilIdle()
            assertEquals("Arrive", coordinator.navigationState.value.currentManeuver?.instruction)
            assertEquals(20, coordinator.navigationState.value.progress.distanceToManeuverMeters)
            assertEquals(2, fake.feedTicks)
        }

    @Test
    fun `feed stops on session stop and ignores late ticks`() = runTest(testDispatcher) {
        val fake = GuidanceFakeAdapter(fakeGuidance(instruction = "Turn left"))
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()
        coordinator.stopNavigation()
        advanceUntilIdle()

        assertFalse(fake.feedRunning)
        fake.fireFeedTick()
        advanceUntilIdle()
        assertEquals(GuidanceStatus.STOPPED, coordinator.navigationState.value.status)
    }

    @Test
    fun `null feed snapshot keeps last maneuver with unavailable flag`() =
        runTest(testDispatcher) {
            val fake = GuidanceFakeAdapter(fakeGuidance(instruction = "Turn left"))
            val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

            coordinator.startNavigation(destination(), route())
            advanceUntilIdle()

            fake.scriptSnapshots(null)
            fake.fireFeedTick()
            advanceUntilIdle()

            val state = coordinator.navigationState.value
            assertEquals("Turn left", state.currentManeuver?.instruction)
            assertEquals(NavigationDataError.GuidanceUnavailable, state.error)
        }

    @Test
    fun `simulator start delegates to adapter`() = runTest(testDispatcher) {
        val fake = GuidanceFakeAdapter()
        val coordinator = NavigationSessionCoordinator({ fake }, noopLogger)

        coordinator.startNavigation(destination(), route())
        advanceUntilIdle()

        assertTrue(coordinator.startSimulator(5f))
    }

    @Test
    fun `maneuver name mapping covers key turns`() {
        assertEquals(
            ManeuverType.LEFT,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("TURN_LEFT")
        )
        assertEquals(
            ManeuverType.RIGHT,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("TURN_RIGHT")
        )
        assertEquals(
            ManeuverType.SLIGHT_LEFT,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("TURN_SLIGHT_LEFT")
        )
        assertEquals(
            ManeuverType.ROUNDABOUT,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("ROUNDABOUT_RIGHT")
        )
        assertEquals(
            ManeuverType.DESTINATION,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("DESTINATION")
        )
        assertEquals(
            ManeuverType.UNKNOWN,
            com.example.hudmapapp.navigation.mapManeuverNameForTest("BOGUS_CODE")
        )
        assertEquals(
            ManeuverType.UNKNOWN,
            com.example.hudmapapp.navigation.mapManeuverNameForTest(null)
        )
    }
}
