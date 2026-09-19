package com.example.hudmapapp.navigation

import androidx.lifecycle.ViewModel
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.repository.RouteLogger
import com.example.hudmapapp.data.repository.debug
import com.example.hudmapapp.data.repository.warn
import com.google.android.libraries.navigation.ArrivalEvent
import com.google.android.libraries.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationSessionCoordinator(
    private val adapterProvider: () -> NavigatorAdapter?,
    private val logger: RouteLogger = RouteLogger.android()
) : ViewModel() {

    private val _sessionState =
        MutableStateFlow<NavigationSessionState>(NavigationSessionState.Idle)
    val sessionState: StateFlow<NavigationSessionState> = _sessionState.asStateFlow()

    private var adapter: NavigatorAdapter? = null
    private var sessionSequence = 0L

    private val _navigationState =
        MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val arrivalListener = Navigator.ArrivalListener { event ->
        onArrival(event)
    }
    private val routeChangedListener = Navigator.RouteChangedListener {
        onRouteChanged()
    }
    private val progressListener = Navigator.RemainingTimeOrDistanceChangedListener {
        onProgressChanged()
    }
    private val reroutingListener = Navigator.ReroutingListener {
        onRerouting()
    }
    private var listenersRegistered = false

    fun startNavigation(destination: Destination, route: RoutePreview) {
        if (destination.latitude == 0.0 && destination.longitude == 0.0) {
            logger.warn("startNavigation rejected: invalid destination coordinates")
            _sessionState.value =
                NavigationSessionState.Error(NavigationSessionError.InvalidRoute)
            _navigationState.value = NavigationState(
                status = GuidanceStatus.ERROR,
                error = NavigationDataError.GuidanceUnavailable
            )
            return
        }
        val current = _sessionState.value
        if (current is NavigationSessionState.Starting ||
            current is NavigationSessionState.Active ||
            current is NavigationSessionState.Rerouting ||
            current is NavigationSessionState.OffRoute ||
            current is NavigationSessionState.Interrupted
        ) {
            logger.debug("startNavigation ignored: session already in progress")
            return
        }

        val provided = adapterProvider()
        if (provided == null) {
            logger.warn("startNavigation failed: navigator not ready")
            _sessionState.value =
                NavigationSessionState.Error(NavigationSessionError.NavigatorNotReady)
            _navigationState.value = NavigationState(
                status = GuidanceStatus.ERROR,
                error = NavigationDataError.GuidanceUnavailable
            )
            return
        }

        adapter = provided
        lastDestination = destination
        lastRoute = route
        val sequence = ++sessionSequence
        _sessionState.value = NavigationSessionState.Starting
        logger.debug("navigation session #$sequence starting route=${route.id}")

        registerListeners(provided)
        provided.setDestinations(destination, route) { status ->
            if (sequence != sessionSequence) {
                logger.debug("navigation session #$sequence stale result ignored")
                return@setDestinations
            }
            if (status != Navigator.RouteStatus.OK) {
                logger.warn("setDestinations failed status=$status")
                unregisterListeners()
                val error = when (status) {
                    Navigator.RouteStatus.NETWORK_ERROR -> NavigationSessionError.NetworkError
                    Navigator.RouteStatus.ROUTE_CANCELED -> NavigationSessionError.GuidanceFailed
                    else -> NavigationSessionError.RouteFailed(status.name)
                }
                _sessionState.value = NavigationSessionState.Error(error)
                _navigationState.value = NavigationState(
                    status = GuidanceStatus.ERROR,
                    error = NavigationDataError.GuidanceUnavailable
                )
                return@setDestinations
            }
            val started = try {
                provided.startGuidance()
            } catch (_: Exception) {
                false
            }
            if (!started || !provided.isGuidanceRunning()) {
                logger.warn("startGuidance failed")
                unregisterListeners()
                _sessionState.value = NavigationSessionState.Error(
                    NavigationSessionError.GuidanceFailed
                )
                _navigationState.value = NavigationState(
                    status = GuidanceStatus.ERROR,
                    error = NavigationDataError.GuidanceUnavailable
                )
                return@setDestinations
            }
            logger.debug("navigation session #$sequence active")
            _sessionState.value = NavigationSessionState.Active(destination, route)
            _navigationState.value = applyGuidance(
                NavigationState(status = GuidanceStatus.ACTIVE),
                provided.readGuidance()
            )
            provided.startGuidanceFeed {
                onFeedTick(sequence)
            }
        }
    }

    fun startSimulator(speedMultiplier: Float = 5f): Boolean {
        val target = adapter ?: return false
        return target.startSimulator(speedMultiplier)
    }

    private fun onFeedTick(sequence: Long) {
        if (sequence != sessionSequence) return
        val current = _sessionState.value
        if (current !is NavigationSessionState.Active &&
            current !is NavigationSessionState.Rerouting &&
            current !is NavigationSessionState.OffRoute
        ) {
            return
        }
        _navigationState.value = applyGuidance(
            _navigationState.value,
            adapter?.readGuidance(),
            status = _navigationState.value.status
        )
    }

    fun stopNavigation() {
        val current = _sessionState.value
        if (current is NavigationSessionState.Idle ||
            current is NavigationSessionState.Stopped ||
            current is NavigationSessionState.Arrived
        ) {
            return
        }
        _sessionState.value = NavigationSessionState.Stopping
        sessionSequence++
        try {
            adapter?.stopGuidanceFeed()
        } catch (_: Exception) {
        }
        try {
            adapter?.stopGuidance()
            adapter?.clearDestinations()
        } catch (_: Exception) {
        }
        unregisterListeners()
        adapter = null
        lastDestination = null
        lastRoute = null
        _sessionState.value = NavigationSessionState.Stopped
        _navigationState.value = NavigationState(status = GuidanceStatus.STOPPED)
        logger.debug("navigation session stopped")
    }

    fun onClearedSession() {
        sessionSequence++
        try {
            adapter?.stopGuidanceFeed()
        } catch (_: Exception) {
        }
        unregisterListeners()
        adapter = null
    }

    override fun onCleared() {
        onClearedSession()
        super.onCleared()
    }

    private fun registerListeners(target: NavigatorAdapter) {
        if (listenersRegistered) return
        target.addArrivalListener(arrivalListener)
        target.addRouteChangedListener(routeChangedListener)
        target.addRemainingTimeOrDistanceChangedListener(progressListener)
        target.addReroutingListener(reroutingListener)
        listenersRegistered = true
    }

    private fun unregisterListeners() {
        if (!listenersRegistered) return
        try {
            adapter?.removeArrivalListener(arrivalListener)
        } catch (_: Exception) {
        }
        try {
            adapter?.removeRouteChangedListener(routeChangedListener)
        } catch (_: Exception) {
        }
        try {
            adapter?.removeRemainingTimeOrDistanceChangedListener(progressListener)
        } catch (_: Exception) {
        }
        try {
            adapter?.removeReroutingListener(reroutingListener)
        } catch (_: Exception) {
        }
        listenersRegistered = false
    }

    fun retryStart() {
        val current = _sessionState.value
        if (current !is NavigationSessionState.Error &&
            current !is NavigationSessionState.Interrupted
        ) {
            return
        }
        val destination = lastDestination ?: return
        val route = lastRoute ?: return
        _sessionState.value = NavigationSessionState.Idle
        startNavigation(destination, route)
    }

    fun resume() {
        val current = _sessionState.value
        if (current !is NavigationSessionState.Interrupted) return
        _sessionState.value = NavigationSessionState.Active(current.destination, current.route)
        _navigationState.value = applyGuidance(
            _navigationState.value.copy(status = GuidanceStatus.ACTIVE),
            adapter?.readGuidance(),
            status = GuidanceStatus.ACTIVE
        )
        logger.debug("navigation session resumed")
    }

    fun notifyLocationUnavailable() {
        val current = activeDestination() ?: return
        _sessionState.value = NavigationSessionState.Interrupted(
            destination = current.first,
            route = current.second,
            reason = InterruptionReason.LOCATION_UNAVAILABLE
        )
        _navigationState.value = _navigationState.value.copy(status = GuidanceStatus.INTERRUPTED)
        logger.debug("navigation interrupted: location unavailable")
    }

    fun notifyNetworkLost() {
        val current = activeDestination() ?: return
        _sessionState.value = NavigationSessionState.Interrupted(
            destination = current.first,
            route = current.second,
            reason = InterruptionReason.NETWORK_ERROR
        )
        _navigationState.value = _navigationState.value.copy(status = GuidanceStatus.INTERRUPTED)
        logger.debug("navigation interrupted: network lost")
    }

    private var lastDestination: Destination? = null
    private var lastRoute: RoutePreview? = null

    private fun activeDestination(): Pair<Destination, RoutePreview>? {
        return when (val current = _sessionState.value) {
            is NavigationSessionState.Active -> current.destination to current.route
            is NavigationSessionState.Rerouting -> current.destination to current.route
            is NavigationSessionState.OffRoute -> current.destination to current.route
            is NavigationSessionState.Interrupted -> current.destination to current.route
            else -> null
        }
    }

    private fun onArrival(event: ArrivalEvent) {
        val current = activeDestination() ?: return
        if (!event.isFinal()) return
        logger.debug("navigation session arrived")
        unregisterListeners()
        adapter = null
        _sessionState.value = NavigationSessionState.Arrived
        _navigationState.value = _navigationState.value.copy(status = GuidanceStatus.ARRIVED)
    }

    private fun onRouteChanged() {
        val current = activeDestination() ?: return
        logger.debug("navigation route changed")
        _sessionState.value = NavigationSessionState.Active(current.first, current.second)
        _navigationState.value = applyGuidance(
            _navigationState.value.copy(status = GuidanceStatus.ACTIVE, isRerouting = false),
            adapter?.readGuidance(),
            status = GuidanceStatus.ACTIVE
        )
    }

    private fun onProgressChanged() {
        val current = _sessionState.value
        if (current !is NavigationSessionState.Active &&
            current !is NavigationSessionState.Rerouting &&
            current !is NavigationSessionState.OffRoute
        ) {
            return
        }
        _navigationState.value = applyGuidance(
            _navigationState.value,
            adapter?.readGuidance(),
            status = _navigationState.value.status
        )
    }

    private fun onRerouting() {
        val current = activeDestination() ?: return
        logger.debug("navigation rerouting requested")
        _sessionState.value = NavigationSessionState.OffRoute(current.first, current.second)
        _navigationState.value = _navigationState.value.copy(
            status = GuidanceStatus.OFF_ROUTE,
            isRerouting = true
        )
    }

    internal fun onRerouteResolved() {
        val current = activeDestination() ?: return
        _sessionState.value = NavigationSessionState.Rerouting(current.first, current.second)
        _navigationState.value = _navigationState.value.copy(
            status = GuidanceStatus.REROUTING,
            isRerouting = true
        )
        logger.debug("navigation rerouting in progress")
    }
}
