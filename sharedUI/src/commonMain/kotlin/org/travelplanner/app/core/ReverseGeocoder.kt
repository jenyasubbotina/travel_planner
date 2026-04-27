package org.travelplanner.app.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
