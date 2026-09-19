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
            return
        }
        val current = _sessionState.value
        if (current is NavigationSessionState.Starting ||
            current is NavigationSessionState.Active
        ) {
            logger.debug("startNavigation ignored: session already in progress")
            return
        }

        val provided = adapterProvider()
        if (provided == null) {
            logger.warn("startNavigation failed: navigator not ready")
            _sessionState.value =
                NavigationSessionState.Error(NavigationSessionError.NavigatorNotReady)
            return
        }

        adapter = provided
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
                _sessionState.value = NavigationSessionState.Error(
                    NavigationSessionError.RouteFailed(status.name)
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
                return@setDestinations
            }
            logger.debug("navigation session #$sequence active")
            _sessionState.value = NavigationSessionState.Active(destination, route)
            _navigationState.value = applyGuidance(
                NavigationState(status = GuidanceStatus.ACTIVE),
                provided.readGuidance()
            )
        }
    }

    fun stopNavigation() {
        val current = _sessionState.value
        if (current is NavigationSessionState.Idle ||
            current is NavigationSessionState.Stopped
        ) {
            return
        }
        _sessionState.value = NavigationSessionState.Stopping
        sessionSequence++
        try {
            adapter?.stopGuidance()
            adapter?.clearDestinations()
        } catch (_: Exception) {
        }
        unregisterListeners()
        adapter = null
        _sessionState.value = NavigationSessionState.Stopped
        _navigationState.value = NavigationState(status = GuidanceStatus.STOPPED)
        logger.debug("navigation session stopped")
    }

    fun onClearedSession() {
        sessionSequence++
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

    private fun onArrival(event: ArrivalEvent) {
        val current = _sessionState.value
        if (current !is NavigationSessionState.Active) return
        if (!event.isFinal()) return
        logger.debug("navigation session arrived")
        unregisterListeners()
        _sessionState.value = NavigationSessionState.Arrived
        _navigationState.value = _navigationState.value.copy(status = GuidanceStatus.ARRIVED)
    }

    private fun onRouteChanged() {
        if (_sessionState.value !is NavigationSessionState.Active) return
        logger.debug("navigation route changed")
        _navigationState.value = applyGuidance(
            _navigationState.value.copy(isRerouting = false),
            adapter?.readGuidance()
        )
    }

    private fun onProgressChanged() {
        if (_sessionState.value !is NavigationSessionState.Active) return
        _navigationState.value = applyGuidance(
            _navigationState.value,
            adapter?.readGuidance()
        )
    }

    private fun onRerouting() {
        if (_sessionState.value !is NavigationSessionState.Active) return
        logger.debug("navigation rerouting requested")
        _navigationState.value = _navigationState.value.copy(
            status = GuidanceStatus.REROUTING,
            isRerouting = true
        )
    }
}
