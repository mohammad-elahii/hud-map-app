package com.example.hudmapapp.data.repository

import com.example.hudmapapp.data.model.RouteCoordinate
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewError
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RoutePlanningRequest(
    val origin: RouteCoordinate,
    val destination: RouteCoordinate
)

sealed interface RoutePlanningResult {
    data class Success(val routes: List<RoutePreview>) : RoutePlanningResult
    data object Empty : RoutePlanningResult
    data class Failure(val error: RoutePreviewError) : RoutePlanningResult
}

fun interface RoutePlanningDataSource {
    suspend fun computeRoutes(request: RoutePlanningRequest): RoutePlanningResult
}

class RoutePlanningRepository(
    private val dataSource: RoutePlanningDataSource
) {
    suspend fun requestRoutes(
        originLatitude: Double?,
        originLongitude: Double?,
        destinationLatitude: Double?,
        destinationLongitude: Double?
    ): RoutePlanningResult {
        if (!isValidCoordinate(originLatitude, originLongitude) ||
            !isValidCoordinate(destinationLatitude, destinationLongitude)
        ) {
            return RoutePlanningResult.Failure(RoutePreviewError.InvalidRequest)
        }

        return try {
            dataSource.computeRoutes(
                RoutePlanningRequest(
                    origin = RouteCoordinate(originLatitude!!, originLongitude!!),
                    destination = RouteCoordinate(destinationLatitude!!, destinationLongitude!!)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RoutePlanningResult.Failure(RoutePreviewError.Unknown)
        }
    }

    companion object {
        fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
            if (latitude == null || longitude == null) return false
            if (!latitude.isFinite() || !longitude.isFinite()) return false
            if (latitude == 0.0 && longitude == 0.0) return false
            if (latitude < -90.0 || latitude > 90.0) return false
            if (longitude < -180.0 || longitude > 180.0) return false
            return true
        }

        fun create(apiKey: String): RoutePlanningRepository {
            return RoutePlanningRepository(RoutesApiDataSource(apiKey))
        }
    }
}

class RoutesApiDataSource(
    private val apiKey: String,
    private val timeoutMillis: Int = 15_000
) : RoutePlanningDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun computeRoutes(request: RoutePlanningRequest): RoutePlanningResult {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val body = buildRequestBody(request)
                connection = (URL(ROUTES_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMillis
                    readTimeout = timeoutMillis
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Goog-Api-Key", apiKey)
                    setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val statusCode = connection.responseCode
                if (statusCode == 429) {
                    return@withContext RoutePlanningResult.Failure(RoutePreviewError.QuotaExceeded)
                }
                if (statusCode == 401 || statusCode == 403) {
                    return@withContext RoutePlanningResult.Failure(RoutePreviewError.Authentication)
                }
                if (statusCode == 400) {
                    return@withContext RoutePlanningResult.Failure(RoutePreviewError.InvalidRequest)
                }
                if (statusCode !in 200..299) {
                    return@withContext RoutePlanningResult.Failure(RoutePreviewError.Unknown)
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                parseRoutes(responseBody)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                RoutePlanningResult.Failure(RoutePreviewError.Timeout)
            } catch (_: UnknownHostException) {
                RoutePlanningResult.Failure(RoutePreviewError.Network)
            } catch (_: IOException) {
                RoutePlanningResult.Failure(RoutePreviewError.Network)
            } catch (_: Exception) {
                RoutePlanningResult.Failure(RoutePreviewError.Unknown)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun buildRequestBody(request: RoutePlanningRequest): String {
        return buildString {
            append("{\"origin\":{\"location\":{\"latLng\":{")
            append("\"latitude\":").append(request.origin.latitude).append(',')
            append("\"longitude\":").append(request.origin.longitude)
            append("}}},\"destination\":{\"location\":{\"latLng\":{")
            append("\"latitude\":").append(request.destination.latitude).append(',')
            append("\"longitude\":").append(request.destination.longitude)
            append("}}},\"travelMode\":\"DRIVE\",")
            append("\"routingPreference\":\"TRAFFIC_AWARE\",")
            append("\"computeAlternativeRoutes\":true,")
            append("\"languageCode\":\"en-US\",\"units\":\"METRIC\"}")
        }
    }

    internal fun parseRoutes(responseBody: String): RoutePlanningResult {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val routesArray = root["routes"]?.jsonArray ?: return RoutePlanningResult.Empty
        if (routesArray.isEmpty()) return RoutePlanningResult.Empty

        val routes = routesArray.mapIndexedNotNull { index, element ->
            val routeObject = element.jsonObject
            val encodedPolyline = routeObject["polyline"]
                ?.jsonObject?.get("encodedPolyline")?.jsonPrimitive?.contentOrNull
                .orEmpty()
            val distanceMeters = routeObject["distanceMeters"]
                ?.jsonPrimitive?.intOrNull ?: 0
            val durationSeconds = parseDurationSeconds(
                routeObject["duration"]?.jsonPrimitive?.contentOrNull
            )
            val routeToken = routeObject["routeToken"]?.jsonPrimitive?.contentOrNull
            val staticDuration = routeObject["staticDuration"]
                ?.jsonPrimitive?.contentOrNull
            if (encodedPolyline.isBlank()) return@mapIndexedNotNull null
            RoutePreview(
                id = routeObject["routeLabels"]?.jsonArray?.firstOrNull()
                    ?.jsonPrimitive?.contentOrNull ?: "route-$index",
                polylinePoints = decodePolyline(encodedPolyline),
                encodedPolyline = encodedPolyline,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                routeToken = routeToken,
                label = staticDuration
            )
        }

        if (routes.isEmpty()) return RoutePlanningResult.Empty
        return RoutePlanningResult.Success(routes)
    }

    internal fun parseDurationSeconds(duration: String?): Long {
        if (duration.isNullOrBlank()) return 0L
        val numeric = duration.trim().removeSuffix("s")
        return numeric.toLongOrNull() ?: 0L
    }

    companion object {
        internal const val ROUTES_ENDPOINT =
            "https://routes.googleapis.com/directions/v2:computeRoutes"
        internal const val FIELD_MASK =
            "routes.duration,routes.staticDuration,routes.distanceMeters," +
                "routes.polyline.encodedPolyline,routes.routeToken,routes.routeLabels"

        fun decodePolyline(encoded: String): List<RouteCoordinate> {
            val points = mutableListOf<RouteCoordinate>()
            var index = 0
            var latitude = 0
            var longitude = 0
            while (index < encoded.length) {
                var shift = 0
                var result = 0
                var byte: Int
                do {
                    byte = encoded[index++].code - 63
                    result = result or ((byte and 0x1F) shl shift)
                    shift += 5
                } while (byte >= 0x20)
                val deltaLatitude = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                latitude += deltaLatitude

                shift = 0
                result = 0
                do {
                    byte = encoded[index++].code - 63
                    result = result or ((byte and 0x1F) shl shift)
                    shift += 5
                } while (byte >= 0x20)
                val deltaLongitude = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                longitude += deltaLongitude

                points.add(RouteCoordinate(latitude / 1E5, longitude / 1E5))
            }
            return points
        }
    }
}
