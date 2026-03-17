package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.maplibre.spatialk.geojson.Position
import org.travelplanner.app.domain.Event

@Composable
fun ItineraryMapView(
    events: List<Event>,
    selectedEventId: Long?,
    onDetailsClick: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val centerEvent =
        events.find { it.id == selectedEventId }

    val initialLat = centerEvent?.latitude ?: 50.0
    val initialLon = centerEvent?.longitude ?: 10.0

    val cameraState =
        rememberCameraState(
            firstPosition =
                CameraPosition(
                    target = Position(longitude = initialLon, latitude = initialLat),
                    zoom = if (centerEvent != null) 13.0 else 5.0,
                ),
        )
    val styleState = rememberStyleState()

    LaunchedEffect(selectedEventId) {
        val selected = events.find { it.id == selectedEventId }
        if (selected?.latitude != null) {
            cameraState.position =
                CameraPosition(
                    target = Position(longitude = selected.longitude, latitude = selected.latitude),
                    zoom = 14.0,
                )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE5E7EB))) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            styleState = styleState,
            cameraState = cameraState,
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright"),
            options = MapOptions(ornamentOptions = OrnamentOptions.OnlyLogo),
        ) {
            val markersSource =
                rememberGeoJsonSource(
                    data = GeoJsonData.JsonString(events.toGeoJson()),
                )

            CircleLayer(
                id = "events-shadow",
                source = markersSource,
                radius = const(14.dp),
                color = const(Color(0x33000000)),
                blur = const(1f),
                translate = const(DpOffset(x = 0.dp, y = 3.dp)),
            )

            CircleLayer(
                id = "events-circle-normal",
                source = markersSource,
                filter =
                    feature["id"].asString() neq
                        const(
                            selectedEventId?.toString() ?: "__none__",
                        ),
                radius = const(11.dp),
                color = const(Color(0xFF155DFC)),
                strokeWidth = const(2.dp),
                strokeColor = const(Color.White),
                onClick = { features ->
                    features
                        .firstOrNull()
                        ?.getStringProperty("id")
                        ?.toLongOrNull()
                        ?.let { onSelect(it) }
                    ClickResult.Consume
                },
            )

            CircleLayer(
                id = "events-circle-selected",
                source = markersSource,
                filter =
                    feature["id"].asString() eq
                        const(
                            selectedEventId?.toString() ?: "__none__",
                        ),
                radius = const(13.dp),
                color = const(Color(0xFFFFB300)),
                strokeWidth = const(3.dp),
                strokeColor = const(Color.White),
                onClick = { features ->
                    features
                        .firstOrNull()
                        ?.getStringProperty("id")
                        ?.toLongOrNull()
                        ?.let { onSelect(it) }
                    ClickResult.Consume
                },
            )

            SymbolLayer(
                id = "events-label",
                source = markersSource,
                textField = format(span(feature["title"].asString())),
                textFont = const(listOf("Noto Sans Regular")),
                textSize = const(1.em),
                textColor = const(Color(0xFF212121)),
                textHaloColor = const(Color.White),
                textHaloWidth = const(1.5.dp),
                textOffset = offset(0f.em, 1.8f.em),
                textAnchor = const(SymbolAnchor.Top),
                textAllowOverlap = const(false),
            )
        }

        if (selectedEventId != null) {
            val event = events.find { it.id == selectedEventId }
            if (event != null) {
                Card(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 0.dp)
                            .clickable(enabled = false) {},
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(10.dp),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(48.dp)
                                .height(4.dp)
                                .background(Color(0xFFD1D5DC), CircleShape),
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Text(getCategoryEmoji(event.category), fontSize = 30.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(event.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        null,
                                        tint = Color(0xFF6A7282),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        event.subtitle,
                                        fontSize = 14.sp,
                                        color = Color(0xFF6A7282),
                                    )
                                }
                                if (!event.description.isNullOrBlank()) {
                                    Text(
                                        event.description ?: "",
                                        fontSize = 14.sp,
                                        color = Color(0xFF4A5565),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }

                                Row(
                                    Modifier.padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {},
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF155DFC),
                                            ),
                                    ) {
                                        Text("Маршрут")
                                    }
                                    Button(
                                        onClick = { onDetailsClick(event.id) },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFF3F4F6),
                                                contentColor = Color.Black,
                                            ),
                                    ) {
                                        Text("Подробнее")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<Event>.toGeoJson(): String {
    val features =
        mapNotNull { event ->
            val lat = event.latitude.takeIf { it != 0.0 } ?: return@mapNotNull null
            val lon = event.longitude.takeIf { it != 0.0 } ?: return@mapNotNull null

            val safeTitle = event.title.replace("\"", "\\\"")

            """
            {
              "type": "Feature",
              "id": "${event.id}",
              "geometry": { "type": "Point", "coordinates": [$lon, $lat] },
              "properties": {
                "id": "${event.id}",
                "title": "$safeTitle"
              }
            }
            """.trimIndent()
        }.joinToString(",\n")

    return """{ "type": "FeatureCollection", "features": [$features] }"""
}
