package com.example.hudmapapp.data.repository

import android.content.Context
import com.example.hudmapapp.data.model.Destination
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

class DestinationRepository(
    private val placesClient: PlacesClient
) {
    private var sessionToken = AutocompleteSessionToken.newInstance()

    companion object {
        fun create(context: Context, apiKey: String): DestinationRepository {
            if (!Places.isInitialized()) {
                @Suppress("DEPRECATION")
                Places.initialize(context.applicationContext, apiKey)
            }
            val client = Places.createClient(context.applicationContext)
            return DestinationRepository(client)
        }
    }

    suspend fun searchDestinations(
        query: String,
        biasLatitude: Double? = null,
        biasLongitude: Double? = null
    ): Result<List<Destination>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }

        return try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .apply {
                    if (biasLatitude != null && biasLongitude != null) {
                        setLocationBias(
                            RectangularBounds.newInstance(
                                com.google.android.gms.maps.model.LatLng(
                                    biasLatitude - 0.5,
                                    biasLongitude - 0.5
                                ),
                                com.google.android.gms.maps.model.LatLng(
                                    biasLatitude + 0.5,
                                    biasLongitude + 0.5
                                )
                            )
                        )
                    }
                }
                .build()

            val response = placesClient.findAutocompletePredictions(request).await()
            val predictions = response.autocompletePredictions

            if (predictions.isEmpty()) {
                return Result.success(emptyList())
            }

            val destinations = predictions.map { prediction ->
                Destination(
                    placeId = prediction.placeId,
                    name = prediction.getPrimaryText(null).toString(),
                    address = prediction.getSecondaryText(null).toString(),
                    latitude = 0.0,
                    longitude = 0.0
                )
            }

            sessionToken = AutocompleteSessionToken.newInstance()
            Result.success(destinations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
