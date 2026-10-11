package com.example.hudmapapp.location

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLocationProviderTest {

    @Test
    fun `repeated start creates one registration`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)

        provider.startUpdates()
        provider.startUpdates()
        provider.startUpdates()

        assertEquals(1, source.registerCalls)
        assertEquals(1, source.activeRegistrationCount)
        assertTrue(provider.isTracking.value)
        assertEquals(LocationState.WaitingForFix, provider.locationState.value)
    }

    @Test
    fun `repeated stop unregisters active registration exactly once`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()

        provider.stopUpdates()
        provider.stopUpdates()

        assertEquals(1, source.unregisterCalls)
        assertEquals(0, source.activeRegistrationCount)
        assertFalse(provider.isTracking.value)
    }

    @Test
    fun `stop then start creates one fresh registration`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)

        provider.startUpdates()
        provider.stopUpdates()
        provider.startUpdates()

        assertEquals(2, source.registerCalls)
        assertEquals(1, source.unregisterCalls)
        assertEquals(1, source.activeRegistrationCount)
        assertTrue(provider.isTracking.value)
    }

    @Test
    fun `collecting updates never starts source registration`() = runTest {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            provider.locationUpdates.collect {}
        }

        assertEquals(0, source.registerCalls)
        assertFalse(provider.isTracking.value)

        collector.cancel()
        assertEquals(0, source.unregisterCalls)
    }

    @Test
    fun `multiple collectors share one registration and receive same update`() = runTest {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        val first = async(start = CoroutineStart.UNDISPATCHED) { provider.locationUpdates.first() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { provider.locationUpdates.first() }
        provider.startUpdates()
        val location = location(time = 100L)

        source.emitLocation(location)

        assertEquals(location, first.await())
        assertEquals(location, second.await())
        assertEquals(1, source.registerCalls)
        assertEquals(0, source.unregisterCalls)
        assertTrue(provider.isTracking.value)
    }

    @Test
    fun `new collector receives replayed location after stop`() = runTest {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()
        val location = location(time = 100L)
        source.emitLocation(location)
        provider.stopUpdates()

        assertEquals(location, provider.locationUpdates.first())
        assertEquals(LocationState.Available(location), provider.locationState.value)
        assertFalse(provider.isTracking.value)
    }

    @Test
    fun `readiness failures preserve permission and services states`() {
        val permissionSource = FakeLocationUpdateSource(
            readiness = LocationSourceReadiness.PERMISSION_REQUIRED
        )
        val permissionProvider = SharedLocationProvider(permissionSource)
        permissionProvider.startUpdates()

        assertEquals(LocationState.PermissionRequired, permissionProvider.locationState.value)
        assertEquals(0, permissionSource.registerCalls)
        assertFalse(permissionProvider.isTracking.value)

        val servicesSource = FakeLocationUpdateSource(
            readiness = LocationSourceReadiness.SERVICES_DISABLED
        )
        val servicesProvider = SharedLocationProvider(servicesSource)
        servicesProvider.startUpdates()

        assertEquals(LocationState.ServicesDisabled, servicesProvider.locationState.value)
        assertEquals(0, servicesSource.registerCalls)
        assertFalse(servicesProvider.isTracking.value)
    }

    @Test
    fun `unavailable and location callbacks update location state`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()

        source.emitUnavailable()
        assertEquals(LocationState.Unavailable, provider.locationState.value)

        val location = location(time = 100L)
        source.emitLocation(location)
        assertEquals(LocationState.Available(location), provider.locationState.value)
    }

    @Test
    fun `registration failure reports error and remains restartable`() {
        val source = FakeLocationUpdateSource(registerError = IllegalStateException("register failed"))
        val provider = SharedLocationProvider(source)

        provider.startUpdates()

        assertEquals(LocationState.Error("register failed"), provider.locationState.value)
        assertFalse(provider.isTracking.value)
        assertEquals(0, source.activeRegistrationCount)

        source.registerError = null
        provider.startUpdates()

        assertEquals(2, source.registerCalls)
        assertTrue(provider.isTracking.value)
    }

    @Test
    fun `asynchronous error cleans active registration and allows retry`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()

        source.emitError(IllegalStateException("updates failed"))

        assertEquals(LocationState.Error("updates failed"), provider.locationState.value)
        assertFalse(provider.isTracking.value)
        assertEquals(1, source.unregisterCalls)
        assertEquals(0, source.activeRegistrationCount)

        provider.startUpdates()
        assertEquals(2, source.registerCalls)
        assertTrue(provider.isTracking.value)
    }

    @Test
    fun `blank errors use stable fallback message`() {
        val source = FakeLocationUpdateSource(registerError = IllegalStateException(""))
        val provider = SharedLocationProvider(source)

        provider.startUpdates()

        assertEquals(
            LocationState.Error("Location updates are unavailable."),
            provider.locationState.value
        )
    }

    @Test
    fun `late callbacks after stop are ignored`() = runTest {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()
        val staleObserver = source.latestObserver
        provider.stopUpdates()

        staleObserver?.onLocation(location(time = 100L))
        staleObserver?.onUnavailable()
        staleObserver?.onError(IllegalStateException("late"))

        assertNull(provider.locationUpdates.replayCache.firstOrNull())
        assertEquals(LocationState.WaitingForFix, provider.locationState.value)
        assertFalse(provider.isTracking.value)
    }

    @Test
    fun `callbacks from old generation cannot overwrite restarted provider`() {
        val source = FakeLocationUpdateSource()
        val provider = SharedLocationProvider(source)
        provider.startUpdates()
        val oldObserver = source.latestObserver
        provider.stopUpdates()
        provider.startUpdates()
        val current = location(latitude = 40.0, time = 200L)
        source.emitLocation(current)

        oldObserver?.onLocation(location(latitude = 10.0, time = 300L))
        oldObserver?.onError(IllegalStateException("old"))

        assertEquals(current, provider.locationUpdates.replayCache.single())
        assertEquals(LocationState.Available(current), provider.locationState.value)
        assertTrue(provider.isTracking.value)
    }

    @Test
    fun `last location preserves permission and services readiness`() = runTest {
        val permissionSource = FakeLocationUpdateSource(
            readiness = LocationSourceReadiness.PERMISSION_REQUIRED
        )
        val permissionProvider = SharedLocationProvider(permissionSource)
        assertNull(permissionProvider.getLastLocation())
        assertEquals(LocationState.PermissionRequired, permissionProvider.locationState.value)
        assertEquals(0, permissionSource.lastLocationCalls)

        val servicesSource = FakeLocationUpdateSource(
            readiness = LocationSourceReadiness.SERVICES_DISABLED
        )
        val servicesProvider = SharedLocationProvider(servicesSource)
        assertNull(servicesProvider.getLastLocation())
        assertEquals(LocationState.ServicesDisabled, servicesProvider.locationState.value)
        assertEquals(0, servicesSource.lastLocationCalls)
    }

    @Test
    fun `last location publishes without starting tracking`() = runTest {
        val expected = location(time = 100L)
        val source = FakeLocationUpdateSource(lastLocation = expected)
        val provider = SharedLocationProvider(source)

        val result = provider.getLastLocation()

        assertEquals(expected, result)
        assertEquals(expected, provider.locationUpdates.replayCache.single())
        assertEquals(LocationState.Available(expected), provider.locationState.value)
        assertEquals(0, source.registerCalls)
        assertFalse(provider.isTracking.value)
    }

    @Test
    fun `older last location cannot replace newer live fix`() = runTest {
        val source = FakeLocationUpdateSource(lastLocation = location(latitude = 10.0, time = 100L))
        val provider = SharedLocationProvider(source)
        provider.startUpdates()
        val live = location(latitude = 40.0, time = 200L)
        source.emitLocation(live)

        val result = provider.getLastLocation()

        assertEquals(10.0, result?.latitude ?: 0.0, 0.0)
        assertEquals(live, provider.locationUpdates.replayCache.single())
        assertEquals(LocationState.Available(live), provider.locationState.value)
    }

    @Test
    fun `null last location reports unavailable only without retained fix`() = runTest {
        val source = FakeLocationUpdateSource(lastLocation = null)
        val emptyProvider = SharedLocationProvider(source)

        assertNull(emptyProvider.getLastLocation())
        assertEquals(LocationState.Unavailable, emptyProvider.locationState.value)

        val retainedProvider = SharedLocationProvider(source)
        retainedProvider.startUpdates()
        val retained = location(time = 100L)
        source.emitLocation(retained)
        source.lastLocation = null

        assertNull(retainedProvider.getLastLocation())
        assertEquals(LocationState.Available(retained), retainedProvider.locationState.value)
    }

    private fun location(
        latitude: Double = 36.2972,
        time: Long
    ): AppLocation = AppLocation(
        latitude = latitude,
        longitude = 59.6067,
        accuracy = 4.5f,
        bearing = 123.25f,
        speed = 8.75f,
        time = time
    )
}

private class FakeLocationUpdateSource(
    var readiness: LocationSourceReadiness = LocationSourceReadiness.READY,
    var lastLocation: AppLocation? = null,
    var registerError: RuntimeException? = null
) : LocationUpdateSource {

    var registerCalls = 0
        private set
    var unregisterCalls = 0
        private set
    var lastLocationCalls = 0
        private set
    val activeRegistrationCount: Int
        get() = registrations.count { !it.unregistered }
    val latestObserver: LocationUpdateObserver?
        get() = registrations.lastOrNull()?.observer

    private val registrations = mutableListOf<FakeRegistration>()

    override fun readiness(): LocationSourceReadiness = readiness

    override fun register(observer: LocationUpdateObserver): LocationUpdateRegistration {
        registerCalls++
        registerError?.let { throw it }
        return FakeRegistration(observer).also(registrations::add)
    }

    override suspend fun getLastLocation(): AppLocation? {
        lastLocationCalls++
        return lastLocation
    }

    fun emitLocation(location: AppLocation) {
        registrations.lastOrNull { !it.unregistered }?.observer?.onLocation(location)
    }

    fun emitUnavailable() {
        registrations.lastOrNull { !it.unregistered }?.observer?.onUnavailable()
    }

    fun emitError(error: Throwable) {
        registrations.lastOrNull { !it.unregistered }?.observer?.onError(error)
    }

    private inner class FakeRegistration(
        val observer: LocationUpdateObserver
    ) : LocationUpdateRegistration {
        var unregistered = false
            private set

        override fun unregister() {
            if (unregistered) return
            unregistered = true
            unregisterCalls++
        }
    }
}
