package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import org.travelplanner.app.theme.DSButton

@Composable
fun LocationPickerMap(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val startLat = initialLatitude ?: 52.52
    val startLon = initialLongitude ?: 13.40
    val startZoom = if (initialLatitude != null) 14.0 else 4.0

    var selectedPosition by remember {
        mutableStateOf(Position(startLon, startLat))
    }

    val cameraState =
        rememberCameraState(
            firstPosition =
                CameraPosition(
                    target = Position(startLon, startLat),
                    zoom = startZoom,
                ),
        )

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            styleState = rememberStyleState(),
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright"),
            onMapClick = { position, screenPoint ->
                selectedPosition = position
                ClickResult.Consume
            },
        ) {
            val markerSource =
                rememberGeoJsonSource(
                    data =
                        GeoJsonData.JsonString(
                            """
                            {
                                "type": "FeatureCollection", 
                                "features": [{
                                    "type": "Feature",
                                    "geometry": { 
                                        "type": "Point", 
                                        "coordinates": [${selectedPosition.longitude}, ${selectedPosition.latitude}] 
                                    }
                                }]
                            }
                            """.trimIndent(),
                        ),
                )

            CircleLayer(
                id = "picker-marker",
                source = markerSource,
                radius = const(12.dp),
                color = const(Color(0xFFE53935)),
                strokeColor = const(Color.White),
                strokeWidth = const(3.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(16.dp),
        ) {
            Text(
                "Нажмите на карту, чтобы выбрать место",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Выбрано: ${
                    selectedPosition.latitude.toString().take(7)
                }, ${selectedPosition.longitude.toString().take(7)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DSButton(
                    text = "Отмена",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFF3F4F6),
                    contentColor = Color.Black,
                )

                DSButton(
                    text = "Подтвердить",
                    onClick = {
                        onConfirm(selectedPosition.latitude, selectedPosition.longitude)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
