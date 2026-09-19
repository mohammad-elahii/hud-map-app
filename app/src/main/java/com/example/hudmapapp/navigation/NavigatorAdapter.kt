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

    override fun isGuidanceRunning(): Boolean = navigator.isGuidanceRunning()

    companion object {
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
