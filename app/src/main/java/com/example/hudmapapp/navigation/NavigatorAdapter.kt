package com.example.hudmapapp.navigation

import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview
import com.google.android.libraries.navigation.ArrivalEvent
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint

interface NavigatorAdapter {
    fun setDestinations(
        destination: Destination,
        route: RoutePreview,
        onResult: (Navigator.RouteStatus) -> Unit
    )

    fun startGuidance(): Boolean

    fun stopGuidance()

    fun clearDestinations()

    fun addArrivalListener(listener: Navigator.ArrivalListener)

    fun removeArrivalListener(listener: Navigator.ArrivalListener)

    fun addRouteChangedListener(listener: Navigator.RouteChangedListener)

    fun removeRouteChangedListener(listener: Navigator.RouteChangedListener)

    fun addRemainingTimeOrDistanceChangedListener(listener: Navigator.RemainingTimeOrDistanceChangedListener)

    fun removeRemainingTimeOrDistanceChangedListener(listener: Navigator.RemainingTimeOrDistanceChangedListener)

    fun addReroutingListener(listener: Navigator.ReroutingListener)

    fun removeReroutingListener(listener: Navigator.ReroutingListener)

    fun startGuidanceFeed(onUpdate: () -> Unit): Boolean

    fun stopGuidanceFeed()

    fun startSimulator(speedMultiplier: Float = 5f): Boolean

    fun readGuidance(): GuidanceSnapshot?

    fun isGuidanceRunning(): Boolean
}

class SdkNavigatorAdapter(
    private val navigator: Navigator
) : NavigatorAdapter {

    override fun setDestinations(
        destination: Destination,
        route: RoutePreview,
        onResult: (Navigator.RouteStatus) -> Unit
    ) {
        val waypoint = try {
            waypointFor(destination)
        } catch (_: Waypoint.UnsupportedPlaceIdException) {
            onResult(Navigator.RouteStatus.NO_ROUTE_FOUND)
            return
        }
        val future: ListenableResultFuture<Navigator.RouteStatus> =
            if (!route.routeToken.isNullOrBlank()) {
                val options = CustomRoutesOptions.builder()
                    .setRouteToken(route.routeToken)
                    .setTravelMode(CustomRoutesOptions.TravelMode.DRIVING)
                    .build()
                navigator.setDestinations(listOf(waypoint), options)
            } else {
                navigator.setDestinations(listOf(waypoint))
            }
        future.setOnResultListener { onResult(it) }
    }

    override fun startGuidance(): Boolean {
        return try {
            navigator.startGuidance()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun stopGuidance() = navigator.stopGuidance()

    override fun clearDestinations() = navigator.clearDestinations()

    override fun addArrivalListener(listener: Navigator.ArrivalListener) {
        navigator.addArrivalListener(listener)
    }

    override fun removeArrivalListener(listener: Navigator.ArrivalListener) {
        navigator.removeArrivalListener(listener)
    }

    override fun addRouteChangedListener(listener: Navigator.RouteChangedListener) {
        navigator.addRouteChangedListener(listener)
    }

    override fun removeRouteChangedListener(listener: Navigator.RouteChangedListener) {
        navigator.removeRouteChangedListener(listener)
    }

    override fun addRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        navigator.addRemainingTimeOrDistanceChangedListener(60, 100, listener)
    }

    override fun removeRemainingTimeOrDistanceChangedListener(
        listener: Navigator.RemainingTimeOrDistanceChangedListener
    ) {
        navigator.removeRemainingTimeOrDistanceChangedListener(listener)
    }

    override fun addReroutingListener(listener: Navigator.ReroutingListener) {
        navigator.addReroutingListener(listener)
    }

    override fun removeReroutingListener(listener: Navigator.ReroutingListener) {
        navigator.removeReroutingListener(listener)
    }

    override fun startGuidanceFeed(onUpdate: () -> Unit): Boolean {
        stopGuidanceFeed()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val task = object : Runnable {
            override fun run() {
                try {
                    onUpdate()
                } finally {
                    feedHandler?.postDelayed(this, FEED_INTERVAL_MILLIS)
                }
            }
        }
        feedHandler = handler
        feedTask = task
        handler.postDelayed(task, FEED_INITIAL_DELAY_MILLIS)
        return true
    }

    override fun stopGuidanceFeed() {
        feedTask?.let { feedHandler?.removeCallbacks(it) }
        feedTask = null
        feedHandler = null
        stopTurnByTurnService()
    }

    override fun startSimulator(speedMultiplier: Float): Boolean {
        return try {
            val options = com.google.android.libraries.navigation.SimulationOptions()
                .speedMultiplier(speedMultiplier)
            navigator.simulator.simulateLocationsAlongExistingRoute(options)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopSimulator() {
        try {
            val simulator = navigator.simulator
            simulator.javaClass.methods
                .firstOrNull { it.name == "stopSimulation" }
                ?.invoke(simulator)
        } catch (_: Exception) {
        }
    }

    override fun readGuidance(): GuidanceSnapshot? {
        readTurnByTurn()?.let { return it }
        return try {
            val times = navigator.getTimeAndDistanceList()
            val currentTime = times.firstOrNull()
            if (currentTime == null) return null
            GuidanceSnapshot(
                currentStep = null,
                nextStep = null,
                distanceToManeuverMeters = null,
                timeToManeuverSeconds = null,
                remainingDistanceMeters = currentTime.meters,
                remainingDurationSeconds = currentTime.seconds.toLong(),
                routeChanged = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private var turnService: Any? = null

    fun startTurnByTurnService(packageName: String, serviceName: String): Boolean {
        return try {
            val optionsClass = Class.forName(
                "com.google.android.libraries.navigation.NavigationUpdatesOptions"
            )
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val builderClass = builder.javaClass
            builderClass.getMethod("setNumNextStepsToPreview", Int::class.javaPrimitiveType)
                .invoke(builder, 1)
            val options = builderClass.getMethod("build").invoke(builder)
            val method = navigator.javaClass.getMethod(
                "registerServiceForNavUpdates",
                String::class.java,
                String::class.java,
                optionsClass
            )
            turnServiceRegistered = method.invoke(navigator, packageName, serviceName, options) as? Boolean
                ?: false
            turnServiceRegistered
        } catch (_: Exception) {
            false
        }
    }

    private var turnServiceRegistered = false

    fun stopTurnByTurnService() {
        if (!turnServiceRegistered) return
        try {
            navigator.javaClass.getMethod("unregisterServiceForNavUpdates").invoke(navigator)
        } catch (_: Exception) {
        }
        turnServiceRegistered = false
    }

    private fun readTurnByTurn(): GuidanceSnapshot? {
        return turnService?.let { mapNavInfo(it) }
    }

    private var feedHandler: android.os.Handler? = null
    private var feedTask: Runnable? = null

    override fun isGuidanceRunning(): Boolean = navigator.isGuidanceRunning()

    companion object {
        internal const val FEED_INTERVAL_MILLIS = 2_000L
        internal const val FEED_INITIAL_DELAY_MILLIS = 1_000L

        internal fun waypointFor(destination: Destination): Waypoint {
            val builder = Waypoint.builder().setTitle(destination.name)
            if (destination.placeId.isNotBlank()) {
                try {
                    builder.setPlaceIdString(destination.placeId)
                } catch (_: Waypoint.UnsupportedPlaceIdException) {
                    builder.setLatLng(destination.latitude, destination.longitude)
                }
            } else {
                builder.setLatLng(destination.latitude, destination.longitude)
            }
            return builder.build()
        }
    }
}

internal fun ArrivalEvent.isFinal(): Boolean = isFinalDestination
