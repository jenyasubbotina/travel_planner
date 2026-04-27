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
import org.travelplanner.app.theme.DSButton

@Composable
expect fun LocationPickerMapCanvas(
    initialLatitude: Double?,
    initialLongitude: Double?,
    selectedLat: Double,
    selectedLon: Double,
    onTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun LocationPickerMap(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val startLat = initialLatitude ?: 52.52
    val startLon = initialLongitude ?: 13.40

    var selectedLat by remember { mutableStateOf(startLat) }
    var selectedLon by remember { mutableStateOf(startLon) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LocationPickerMapCanvas(
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            selectedLat = selectedLat,
            selectedLon = selectedLon,
            onTap = { lat, lon ->
                selectedLat = lat
                selectedLon = lon
            },
            modifier = Modifier.fillMaxSize(),
        )

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
                "Выбрано: ${selectedLat.toString().take(7)}, ${selectedLon.toString().take(7)}",
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
                    onClick = { onConfirm(selectedLat, selectedLon) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
