package org.travelplanner.app.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GeocodedPlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

class ReverseGeocoder(
    private val json: Json,
) {
    suspend fun reverseGeocode(
        lat: Double,
        lng: Double,
    ): String? {
        val client = HttpClient()
        return try {
            val url =
                "https://nominatim.openstreetmap.org/reverse" +
                    "?format=json&lat=$lat&lon=$lng&accept-language=ru&zoom=18"
            val body =
                client
                    .get(url) {
                        header("User-Agent", "TravelPlannerApp/1.0")
                    }.bodyAsText()
            json
                .parseToJsonElement(body)
                .jsonObject["display_name"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }
}

class ForwardGeocoder(
    private val json: Json,
) {
    suspend fun search(query: String, limit: Int = 8): List<GeocodedPlace> {
        if (query.isBlank()) return emptyList()
        val client = HttpClient()
        return try {
            val encoded = query.encodeURLParameter()
            val url =
                "https://nominatim.openstreetmap.org/search" +
                    "?format=json&q=$encoded&accept-language=ru&limit=$limit"
            val body =
                client
                    .get(url) {
                        header("User-Agent", "TravelPlannerApp/1.0")
                    }.bodyAsText()
            json
                .parseToJsonElement(body)
                .jsonArray
                .mapNotNull { element ->
                    val obj = element.jsonObject
                    val displayName =
                        obj["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    val lat =
                        obj["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            ?: obj["lat"]?.jsonPrimitive?.doubleOrNull
                            ?: return@mapNotNull null
                    val lon =
                        obj["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            ?: obj["lon"]?.jsonPrimitive?.doubleOrNull
                            ?: return@mapNotNull null
                    GeocodedPlace(displayName, lat, lon)
                }
        } catch (_: Exception) {
            emptyList()
        } finally {
            client.close()
        }
    }
}
